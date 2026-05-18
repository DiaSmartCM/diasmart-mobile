// POST /api/notify-community
//
// Called after a community message is written to `community_messages/...`.
// Sends an FCM push to the FCM topic "community". The client-side
// DiaSmartFCMService skips display if `data.senderUid` equals the local UID
// (to avoid notifying the sender of their own post).
//
// Auth : Firebase ID token (Bearer).
//
// Body : {
//   preview: string,
//   senderName?: string,   // fallback si on ne peut pas lire users/{uid}.nomComplet
// }
//
// Reponse : { sent: "topic" } ou { error: ... }

const { initFirebase, requireAuth } = require("./_firebase.js");

const MAX_PREVIEW_LEN = 120;
const MAX_REQUESTS_PER_DAY = 100; // par UID (community = + cher, on serre)

async function checkRateLimit(db, uid) {
  const ref = db.collection("rate_limits").doc(`notify_comm_${uid}`);
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
    return { ok: true };
  }
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { preview, senderName } = req.body || {};
  const adm = initFirebase();
  const db = adm.firestore();

  const rl = await checkRateLimit(db, decoded.uid);
  if (!rl.ok) {
    return res.status(429).json({ error: "rate_limit_exceeded" });
  }

  // Resout le nom du sender : d'abord users/{uid}.nomComplet, sinon le param.
  let resolvedName = (senderName || "").toString().trim();
  if (!resolvedName) {
    try {
      const userDoc = await db.collection("users").doc(decoded.uid).get();
      const data = userDoc.exists ? userDoc.data() : null;
      resolvedName =
        (data && (data.nomComplet || data.displayName || data.email)) || "Un membre";
    } catch (_) {
      resolvedName = "Un membre";
    }
  }
  resolvedName = resolvedName.substring(0, 50);

  let bodyText = (preview || "").toString().trim();
  if (bodyText.length > MAX_PREVIEW_LEN) {
    bodyText = bodyText.substring(0, MAX_PREVIEW_LEN - 1) + "…";
  }
  if (bodyText.length === 0) bodyText = "Nouveau message dans la communauté";

  const title = `${resolvedName} — Communauté`;

  try {
    await adm.messaging().send({
      topic: "community",
      notification: { title, body: bodyText },
      data: {
        type: "new_community",
        senderUid: decoded.uid,
        senderNom: resolvedName,
      },
      android: {
        priority: "high",
        notification: { channelId: "diasmart_community" },
      },
    });
    return res.status(200).json({ sent: "topic" });
  } catch (e) {
    return res.status(502).json({ error: "fcm_failed", message: e.message });
  }
};
