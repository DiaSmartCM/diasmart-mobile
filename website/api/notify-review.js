// POST /api/notify-review
//
// Called by the Android client immediately after a patient successfully
// writes a review to doctor_reviews/{doctorUid}_{patientUid}.
// Sends an FCM push to the reviewed doctor so they see the new rating in real-time.
//
// Auth : Firebase ID token (Bearer) — caller must be the patient author of the review.
// Body : { doctorUid: string, rating: number, comment?: string, patientNom?: string, isUpdate?: boolean }

const { initFirebase, requireAuth } = require("./_firebase.js");

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "method_not_allowed" });
    return;
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { doctorUid, rating, comment, patientNom, isUpdate } = req.body || {};
  if (!doctorUid || typeof doctorUid !== "string") {
    res.status(400).json({ error: "missing_doctorUid" });
    return;
  }
  const r = Math.max(1, Math.min(5, parseInt(rating, 10) || 0));
  if (doctorUid === decoded.uid) {
    // A user cannot review themselves — but we still 200 so the client doesn't spin
    res.status(200).json({ skipped: "self_review" });
    return;
  }

  const adm = initFirebase();
  const db = adm.firestore();
  const messaging = adm.messaging();

  // Collect FCM tokens for this doctor (fcm_tokens + fallback users/{uid}.fcmToken)
  const tokens = new Set();
  try {
    const snap = await db
      .collection("fcm_tokens")
      .where("uid", "==", doctorUid)
      .get();
    snap.forEach((d) => {
      const t = d.data().token;
      if (t) tokens.add(t);
    });
  } catch (_) {}
  if (tokens.size === 0) {
    try {
      const userDoc = await db.collection("users").doc(doctorUid).get();
      const t = userDoc.data() && userDoc.data().fcmToken;
      if (t) tokens.add(t);
    } catch (_) {}
  }
  if (tokens.size === 0) {
    res.status(200).json({ sent: 0, reason: "no_tokens" });
    return;
  }

  const cleanPatient = (patientNom || "Un patient").toString().substring(0, 40);
  const stars = "⭐".repeat(r);
  const title = isUpdate
    ? `${cleanPatient} a modifié son avis`
    : `${cleanPatient} vous a laissé un avis`;
  const body =
    `${stars} ${r}/5` +
    (comment ? ` — « ${String(comment).substring(0, 80)} »` : "");

  let sent = 0;
  const dead = [];
  for (const token of tokens) {
    try {
      await messaging.send({
        token,
        notification: { title, body },
        data: {
          type: "new_review",
          doctorUid,
          patientUid: decoded.uid,
          rating: String(r),
        },
        android: {
          priority: "high",
          notification: { channelId: "diasmart_reviews" },
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

  // Clean up dead tokens best-effort
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

  res.status(200).json({ sent, dead: dead.length });
};
