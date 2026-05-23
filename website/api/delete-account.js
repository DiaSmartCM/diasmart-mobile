// POST /api/delete-account
//
// Suppression RGPD-compliant : cascade complete de toutes les donnees de
// l'utilisateur sur Firestore + Supabase + FCM tokens, puis suppression
// du compte Firebase Auth.
//
// Auth : Firebase ID token (Bearer). L'appelant doit etre le proprietaire
// du compte a supprimer (uid extrait du token).
//
// Body : { confirm: "DELETE_<uid>" } pour eviter les appels accidentels.
//
// Reponse :
//   200 { deleted: { firestore: N, supabase: M, fcm: K, auth: true }, receipt: "..." }
//   400 si confirm manquant ou faux
//   401 si pas authentifie
//   500 si erreur

const { initFirebase, requireAuth } = require("./_firebase.js");
const crypto = require("crypto");

// Collections top-level qui ont un champ userId
const USER_DATA_COLLECTIONS_BY_USERID = [
  "glucose",
  "repas",
  "medicaments",
  "rendezvous",
  "journal",
  "hba1c_lectures",
  "patients",
  "community_messages",
];

// Collections "owned" par l'uid lui-meme (doc ID = uid ou sous-collection /uid/...)
const USER_OWNED_TOP_LEVEL = [
  "users",       // /users/{uid}
  "rolly_history", // /rolly_history/{uid}
  "quota",       // /quota/{uid}
  "backups",     // /backups/{uid}
  "reports",     // /reports/{uid}
  "fcm_tokens",  // /fcm_tokens (where uid==me)
  "rate_limits", // /rate_limits/{prefix}_{uid}
];

async function deleteAllDocsWhere(db, collection, field, value) {
  let totalDeleted = 0;
  const batchSize = 400;
  while (true) {
    const snap = await db.collection(collection).where(field, "==", value).limit(batchSize).get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    totalDeleted += snap.size;
    if (snap.size < batchSize) break;
  }
  return totalDeleted;
}

async function deleteSubcollection(db, parentPath, sub, batchSize = 400) {
  let totalDeleted = 0;
  while (true) {
    const snap = await db.collection(`${parentPath}/${sub}`).limit(batchSize).get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    totalDeleted += snap.size;
    if (snap.size < batchSize) break;
  }
  return totalDeleted;
}

async function deleteUserDoc(db, collection, uid) {
  let deleted = 0;
  // Delete known sub-collections
  if (collection === "users") {
    deleted += await deleteSubcollection(db, `users/${uid}`, "repas");
  }
  if (collection === "rolly_history") {
    deleted += await deleteSubcollection(db, `rolly_history/${uid}`, "messages");
    // sous-conversations
    try {
      const convs = await db.collection(`rolly_history/${uid}/conversations`).limit(1000).get();
      for (const conv of convs.docs) {
        deleted += await deleteSubcollection(db, conv.ref.path, "messages");
        await conv.ref.delete();
        deleted++;
      }
    } catch (_) {}
  }
  if (collection === "reports") {
    deleted += await deleteSubcollection(db, `reports/${uid}`, "items");
  }
  // Delete the user doc itself
  try {
    await db.collection(collection).doc(uid).delete();
    deleted++;
  } catch (_) {}
  return deleted;
}

async function deleteConversationsInvolvingUser(db, uid) {
  let deleted = 0;
  // patientId == uid
  let snap = await db.collection("conversations").where("patientId", "==", uid).get();
  for (const conv of snap.docs) {
    deleted += await deleteSubcollection(db, conv.ref.path, "messages");
    await conv.ref.delete();
    deleted++;
  }
  // medecinId == uid
  snap = await db.collection("conversations").where("medecinId", "==", uid).get();
  for (const conv of snap.docs) {
    deleted += await deleteSubcollection(db, conv.ref.path, "messages");
    await conv.ref.delete();
    deleted++;
  }
  return deleted;
}

async function deleteFcmTokens(db, uid) {
  return deleteAllDocsWhere(db, "fcm_tokens", "uid", uid);
}

async function deleteDataSharing(db, uid) {
  let deleted = 0;
  deleted += await deleteAllDocsWhere(db, "data_sharing", "patientUid", uid);
  deleted += await deleteAllDocsWhere(db, "data_sharing", "medecinUid", uid);
  return deleted;
}

async function deleteDoctorReviews(db, uid) {
  let deleted = 0;
  deleted += await deleteAllDocsWhere(db, "doctor_reviews", "patientUid", uid);
  // si medecin : suppression de tous les avis sur lui
  deleted += await deleteAllDocsWhere(db, "doctor_reviews", "doctorUid", uid);
  return deleted;
}

async function deleteValidations(db, uid) {
  let deleted = 0;
  deleted += await deleteAllDocsWhere(db, "validations", "medecinUid", uid);
  deleted += await deleteAllDocsWhere(db, "validations", "patientUid", uid);
  return deleted;
}

async function deleteCalls(db, uid) {
  let deleted = 0;
  deleted += await deleteAllDocsWhere(db, "calls", "callerUid", uid);
  deleted += await deleteAllDocsWhere(db, "calls", "calleeUid", uid);
  return deleted;
}

/**
 * v2.1.60 : cascade Supabase Storage. Supprime tous les blobs sous les prefixes
 * appartenant a l'utilisateur (rapports PDF, photos profil, pieces jointes chat).
 *
 * Env requis (a setter sur Vercel) :
 *   - SUPABASE_URL                 (ex: https://avcskcqzxwbkiskvlvxx.supabase.co)
 *   - SUPABASE_SERVICE_ROLE_KEY    (cle service_role, JAMAIS exposee cote client)
 *   - SUPABASE_BUCKET              (ex: diasmart-files — defaut "diasmart-files")
 *
 * Si une variable manque, on warn et on retourne 0 (deploiement continue mais
 * la cascade Supabase est skip — a noter dans les logs Vercel).
 */
async function deleteSupabaseUserBlobs(uid) {
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const bucket = process.env.SUPABASE_BUCKET || "diasmart-files";
  if (!url || !key) {
    console.warn("SUPABASE_URL ou SUPABASE_SERVICE_ROLE_KEY absent — skip cascade Storage");
    return 0;
  }

  // Les prefixes que DiaSmart utilise. Garder synchro avec le code mobile.
  const prefixes = [`reports/${uid}`, `profile_photos/${uid}`, `chat_attachments/${uid}`];
  let total = 0;
  for (const prefix of prefixes) {
    try {
      // 1) Lister les objets sous le prefixe (1000 max par requete — boucle si necessaire)
      let offset = 0;
      const paths = [];
      while (true) {
        const listResp = await fetch(
          `${url}/storage/v1/object/list/${bucket}`,
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              apikey: key,
              Authorization: `Bearer ${key}`,
            },
            body: JSON.stringify({
              prefix,
              limit: 1000,
              offset,
              sortBy: { column: "name", order: "asc" },
            }),
          }
        );
        if (!listResp.ok) {
          console.warn(`supabase list ${prefix} -> HTTP ${listResp.status}`);
          break;
        }
        const items = await listResp.json();
        if (!Array.isArray(items) || items.length === 0) break;
        for (const it of items) {
          if (it && it.name) paths.push(`${prefix}/${it.name}`);
        }
        if (items.length < 1000) break;
        offset += items.length;
      }

      // 2) Suppression batch (API Supabase Storage : DELETE /object/{bucket}
      //    avec body { prefixes: [...] })
      if (paths.length > 0) {
        const delResp = await fetch(
          `${url}/storage/v1/object/${bucket}`,
          {
            method: "DELETE",
            headers: {
              "Content-Type": "application/json",
              apikey: key,
              Authorization: `Bearer ${key}`,
            },
            body: JSON.stringify({ prefixes: paths }),
          }
        );
        if (delResp.ok) {
          total += paths.length;
        } else {
          console.warn(`supabase delete ${prefix} -> HTTP ${delResp.status}`);
        }
      }
    } catch (e) {
      console.warn(`supabase cascade ${prefix} failed:`, e.message);
    }
  }
  return total;
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const uid = decoded.uid;
  const { confirm } = req.body || {};
  const expectedToken = `DELETE_${uid}`;
  if (confirm !== expectedToken) {
    return res.status(400).json({
      error: "confirm_required",
      message: `Le champ "confirm" doit valoir exactement "${expectedToken}"`,
    });
  }

  const adm = initFirebase();
  const db = adm.firestore();
  const auth = adm.auth();

  const deletedCounts = { firestore: 0, fcm: 0, conversations: 0, sharing: 0, reviews: 0, validations: 0, calls: 0, supabase: 0, auth: false };

  try {
    // 1. Top-level user data by userId
    for (const coll of USER_DATA_COLLECTIONS_BY_USERID) {
      let field = "userId";
      if (coll === "community_messages") field = "userId";
      try {
        deletedCounts.firestore += await deleteAllDocsWhere(db, coll, field, uid);
      } catch (e) {
        console.warn(`delete ${coll} failed:`, e.message);
      }
    }

    // 2. Conversations + messages
    deletedCounts.conversations = await deleteConversationsInvolvingUser(db, uid);

    // 3. Data sharing
    deletedCounts.sharing = await deleteDataSharing(db, uid);

    // 4. Doctor reviews
    deletedCounts.reviews = await deleteDoctorReviews(db, uid);

    // 5. Validations
    deletedCounts.validations = await deleteValidations(db, uid);

    // 6. Calls
    deletedCounts.calls = await deleteCalls(db, uid);

    // 7. FCM tokens
    deletedCounts.fcm = await deleteFcmTokens(db, uid);

    // 8. User-owned top-level docs + sub-collections
    for (const coll of USER_OWNED_TOP_LEVEL) {
      if (coll === "fcm_tokens") continue; // already done above
      try {
        deletedCounts.firestore += await deleteUserDoc(db, coll, uid);
      } catch (e) {
        console.warn(`delete /${coll}/${uid} failed:`, e.message);
      }
    }

    // 9. Rate limits (preferes : rolly_, notify_msg_, notify_comm_)
    for (const prefix of ["rolly", "notify_msg", "notify_comm"]) {
      try {
        await db.collection("rate_limits").doc(`${prefix}_${uid}`).delete();
      } catch (_) {}
    }

    // 10. Supabase storage : reports/{uid}/* + profile_photos/{uid}/* + chat_attachments/{uid}/*
    // v2.1.60 : cascade RGPD complete. Si SUPABASE_SERVICE_ROLE_KEY absent,
    // on log et on skip (deployment ne casse pas, mais conformite reduite).
    deletedCounts.supabase = await deleteSupabaseUserBlobs(uid).catch((e) => {
      console.warn("supabase cascade failed:", e.message);
      return 0;
    });

    // 11. Firebase Auth (en DERNIER — apres ca le token ne marche plus)
    try {
      await auth.deleteUser(uid);
      deletedCounts.auth = true;
    } catch (e) {
      console.warn(`auth.deleteUser failed:`, e.message);
      deletedCounts.auth = false;
    }

    // 12. Recu RGPD signe (preuve legale)
    const receiptData = {
      uid,
      deletedAt: new Date().toISOString(),
      counts: deletedCounts,
    };
    const receiptStr = JSON.stringify(receiptData);
    const hmac = crypto
      .createHmac("sha256", process.env.DELETE_RECEIPT_SECRET || "diasmart-rgpd-2026")
      .update(receiptStr)
      .digest("hex");
    const receipt = Buffer.from(JSON.stringify({ ...receiptData, hmac })).toString("base64");

    return res.status(200).json({
      ok: true,
      deleted: deletedCounts,
      receipt,
      message:
        "Compte supprime. Recu RGPD genere — conservez-le comme preuve legale.",
    });
  } catch (e) {
    console.error("delete-account fatal:", e);
    return res.status(500).json({
      error: "delete_failed",
      message: e.message,
      partialDeletes: deletedCounts,
    });
  }
};
