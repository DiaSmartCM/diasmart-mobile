// POST /api/notify-message
//
// Called by the Android client immediately after a message is written to
// `conversations/{conversationId}/messages/...`. Sends an FCM push to the
// OTHER participant of the conversation (patient ↔ medecin).
//
// Auth : Firebase ID token (Bearer) — caller must be patientId or medecinId
// of the conversation.
//
// Body : {
//   conversationId: string,
//   preview: string,           // texte du message (tronque cote serveur)
//   attachmentName?: string,   // si message porte une piece jointe (PDF, etc.)
// }
//
// Reponse : { sent: number, dead: number } ou { skipped: "..." }

const { initFirebase, requireAuth } = require("./_firebase.js");

const MAX_PREVIEW_LEN = 120;
const MAX_REQUESTS_PER_DAY = 500; // par UID (anti-spam)

async function checkRateLimit(db, uid) {
  const ref = db.collection("rate_limits").doc(`notify_msg_${uid}`);
  const now = Date.now();
  const windowMs = 24 * 60 * 60 * 1000;
  try {
    const snap = await ref.get();
    const data = snap.exists ? snap.data() : { windowStart: now, count: 0 };
    if (now - (data.windowStart || 0) > windowMs) {
      await ref.set({ windowStart: now, count: 1 });
      return { ok: true };
    }
    if ((data.count || 0) >= MAX_REQUESTS_PER_DAY) {
      return { ok: false };
    }
    await ref.update({ count: (data.count || 0) + 1 });
    return { ok: true };
  } catch (_) {
    return { ok: true }; // best-effort
  }
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { conversationId, preview, attachmentName } = req.body || {};
  if (!conversationId || typeof conversationId !== "string") {
    return res.status(400).json({ error: "missing_conversationId" });
  }

  const adm = initFirebase();
  const db = adm.firestore();

  // Rate limit
  const rl = await checkRateLimit(db, decoded.uid);
  if (!rl.ok) {
    return res.status(429).json({ error: "rate_limit_exceeded" });
  }

  // Charge la conversation et verifie l'appartenance
  let convData;
  try {
    const convSnap = await db
      .collection("conversations")
      .doc(conversationId)
      .get();
    if (!convSnap.exists) {
      return res.status(404).json({ error: "conversation_not_found" });
    }
    convData = convSnap.data() || {};
  } catch (e) {
    return res.status(500).json({ error: "firestore_read_failed", message: e.message });
  }

  const patientId = convData.patientId;
  const medecinId = convData.medecinId;
  const patientNom = convData.patientNom || "Patient";
  const medecinNom = convData.medecinNom || "Médecin";

  if (decoded.uid !== patientId && decoded.uid !== medecinId) {
    return res.status(403).json({ error: "not_a_participant" });
  }

  const recipientUid = decoded.uid === patientId ? medecinId : patientId;
  const senderNom = decoded.uid === patientId ? patientNom : medecinNom;
  if (!recipientUid) {
    return res.status(400).json({ error: "no_recipient_in_conversation" });
  }

  // Collecte les tokens FCM du destinataire
  const tokens = new Set();
  try {
    const snap = await db
      .collection("fcm_tokens")
      .where("uid", "==", recipientUid)
      .get();
    snap.forEach((d) => {
      const t = d.data().token;
      if (t) tokens.add(t);
    });
  } catch (_) {}
  if (tokens.size === 0) {
    try {
      const userDoc = await db.collection("users").doc(recipientUid).get();
      const t = userDoc.exists && userDoc.data() && userDoc.data().fcmToken;
      if (t) tokens.add(t);
    } catch (_) {}
  }
  if (tokens.size === 0) {
    return res.status(200).json({ sent: 0, reason: "no_tokens" });
  }

  // Construit la notification
  let bodyText = (preview || "").toString().trim();
  if (attachmentName && bodyText.length === 0) {
    bodyText = `📎 ${attachmentName}`;
  } else if (attachmentName) {
    bodyText = `📎 ${attachmentName} — ${bodyText}`;
  }
  if (bodyText.length > MAX_PREVIEW_LEN) {
    bodyText = bodyText.substring(0, MAX_PREVIEW_LEN - 1) + "…";
  }
  if (bodyText.length === 0) bodyText = "Nouveau message";

  const title = senderNom.substring(0, 50);
  const messaging = adm.messaging();

  let sent = 0;
  const dead = [];
  for (const token of tokens) {
    try {
      await messaging.send({
        token,
        notification: { title, body: bodyText },
        data: {
          type: "new_message",
          conversationId,
          senderUid: decoded.uid,
          senderNom: senderNom.substring(0, 60),
        },
        android: {
          priority: "high",
          notification: { channelId: "diasmart_messages" },
        },
      });
      sent++;
    } catch (e) {
      if (
        e.code === "messaging/invalid-registration-token" ||
        e.code === "messaging/registration-token-not-registered"
      ) {
        dead.push(token);
      }
    }
  }

  // Cleanup tokens morts (best-effort)
  if (dead.length) {
    try {
      for (const t of dead) {
        const snap = await db
          .collection("fcm_tokens")
          .where("token", "==", t)
          .get();
        const batch = db.batch();
        snap.forEach((d) => batch.delete(d.ref));
        await batch.commit();
      }
    } catch (_) {}
  }

  return res.status(200).json({ sent, dead: dead.length });
};
