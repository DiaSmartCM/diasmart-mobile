// POST /api/verify-email-otp
// Body: { code: "123456" }
// Auth: Bearer <Firebase ID token>
// Validates the OTP and marks the Firebase Auth user as emailVerified.
const crypto = require("node:crypto");
const { initFirebase, requireAuth } = require("./_firebase.js");

const OTP_MAX_ATTEMPTS = 5;

function sha256(s) {
  return crypto.createHash("sha256").update(s).digest("hex");
}

async function readJsonBody(req) {
  if (req.body && typeof req.body === "object") return req.body;
  if (typeof req.body === "string") {
    try { return JSON.parse(req.body); } catch { return {}; }
  }
  return await new Promise((resolve) => {
    let raw = "";
    req.on("data", (c) => (raw += c));
    req.on("end", () => {
      try { resolve(JSON.parse(raw || "{}")); }
      catch { resolve({}); }
    });
    req.on("error", () => resolve({}));
  });
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const body = await readJsonBody(req);
  const rawCode = (body && body.code) || "";
  const code = String(rawCode).replace(/\s+/g, "");
  if (!/^[0-9]{6}$/.test(code)) {
    return res.status(400).json({ error: "invalid_code_format", message: "Code invalide (6 chiffres attendus)." });
  }

  const uid = decoded.uid;
  const adm = initFirebase();
  const db = adm.firestore();
  const ref = db.collection("email_otp").doc(uid);
  const snap = await ref.get();
  if (!snap.exists) {
    return res.status(404).json({ error: "no_pending_otp", message: "Aucun code en cours. Demandez un nouveau code." });
  }
  const data = snap.data();
  if (data.used) {
    return res.status(409).json({ error: "already_used", message: "Code deja utilise. Demandez un nouveau code." });
  }
  if (Date.now() > (data.expiresAtMs || 0)) {
    return res.status(410).json({ error: "expired", message: "Code expire. Demandez un nouveau code." });
  }
  if ((data.attempts || 0) >= OTP_MAX_ATTEMPTS) {
    return res.status(429).json({ error: "too_many_attempts", message: "Trop de tentatives. Demandez un nouveau code." });
  }

  if (sha256(code) !== data.codeHash) {
    await ref.update({ attempts: (data.attempts || 0) + 1 });
    return res.status(403).json({ error: "wrong_code", message: "Code incorrect." });
  }

  await adm.auth().updateUser(uid, { emailVerified: true });
  await ref.update({ used: true, verifiedAtMs: Date.now() });
  try {
    await db.collection("users").doc(uid).set(
      { emailVerified: true, emailVerifiedAtMs: Date.now() },
      { merge: true }
    );
  } catch (_) { /* non-fatal */ }

  return res.status(200).json({ ok: true });
};
