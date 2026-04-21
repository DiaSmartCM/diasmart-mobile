// POST /api/send-email-otp
// Auth: Bearer <Firebase ID token>
// Generates a 6-digit OTP, stores SHA-256 hash in Firestore (email_otp/{uid}),
// sends the code by email via Gmail SMTP.
const crypto = require("node:crypto");
const { initFirebase, buildTransporter, requireAuth } = require("./_firebase.js");

const OTP_TTL_MINUTES = 10;
const RESEND_COOLDOWN_SECONDS = 60;

function sha256(s) {
  return crypto.createHash("sha256").update(s).digest("hex");
}

function generateOtp() {
  return crypto.randomInt(0, 1_000_000).toString().padStart(6, "0");
}

function renderEmailHtml(code, firstName) {
  const hello = firstName ? `Bonjour ${firstName},` : "Bonjour,";
  return `<!DOCTYPE html><html><body style="margin:0;padding:0;background:#F5F7FA;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#1A2B4B;">
<div style="max-width:560px;margin:0 auto;padding:32px 24px;">
  <div style="background:linear-gradient(135deg,#00D2FF 0%,#6771E4 100%);border-radius:24px;padding:28px;text-align:center;">
    <div style="color:#FFFFFF;font-size:22px;font-weight:700;letter-spacing:0.5px;">DiaSmart.AI</div>
    <div style="color:rgba(255,255,255,0.85);font-size:13px;margin-top:4px;">Votre compagnon diabete</div>
  </div>
  <div style="background:#FFFFFF;border-radius:24px;padding:32px;margin-top:16px;box-shadow:0 2px 8px rgba(0,0,0,0.04);">
    <p style="margin:0 0 12px;font-size:16px;">${hello}</p>
    <p style="margin:0 0 20px;font-size:15px;line-height:1.55;color:#4A5A78;">Voici votre code de verification pour securiser votre compte :</p>
    <div style="text-align:center;margin:24px 0;">
      <div style="display:inline-block;background:#F3F0FF;color:#6771E4;font-size:34px;font-weight:700;letter-spacing:8px;padding:18px 28px;border-radius:16px;font-family:'SF Mono','Monaco','Courier New',monospace;">${code}</div>
    </div>
    <p style="margin:0 0 8px;font-size:14px;color:#4A5A78;">Ce code expire dans <strong>${OTP_TTL_MINUTES} minutes</strong>.</p>
    <p style="margin:0;font-size:13px;color:#8492A6;">Si vous n'etes pas a l'origine de cette demande, ignorez ce message.</p>
  </div>
  <div style="text-align:center;margin-top:18px;font-size:12px;color:#8492A6;">&copy; DiaSmart.AI</div>
</div></body></html>`;
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const uid = decoded.uid;
  const email = decoded.email;
  if (!email) {
    return res.status(400).json({ error: "no_email_on_account" });
  }

  const adm = initFirebase();
  const db = adm.firestore();
  const ref = db.collection("email_otp").doc(uid);
  const existing = await ref.get();
  const now = Date.now();
  if (existing.exists) {
    const lastSentMs = existing.data().lastSentMs || 0;
    if (now - lastSentMs < RESEND_COOLDOWN_SECONDS * 1000) {
      const waitSec = Math.ceil((RESEND_COOLDOWN_SECONDS * 1000 - (now - lastSentMs)) / 1000);
      return res.status(429).json({
        error: "cooldown",
        message: `Patientez ${waitSec}s avant de redemander un code.`,
      });
    }
  }

  const code = generateOtp();
  const codeHash = sha256(code);
  const expiresAtMs = now + OTP_TTL_MINUTES * 60 * 1000;

  await ref.set({
    codeHash,
    expiresAtMs,
    lastSentMs: now,
    attempts: 0,
    used: false,
    email,
  });

  // Best-effort first-name lookup for greeting
  let firstName = "";
  try {
    const userDoc = await db.collection("users").doc(uid).get();
    firstName = (userDoc.data() || {}).prenom || "";
  } catch (_) { /* ignore */ }

  try {
    const transporter = buildTransporter();
    await transporter.sendMail({
      from: `"DiaSmart.AI" <${process.env.GMAIL_USER}>`,
      to: email,
      subject: `Code de verification DiaSmart : ${code}`,
      text: `Votre code de verification DiaSmart : ${code}\nCe code expire dans ${OTP_TTL_MINUTES} minutes.`,
      html: renderEmailHtml(code, firstName),
    });
  } catch (e) {
    console.error("SMTP send failed:", e.message);
    return res.status(502).json({
      error: "email_send_failed",
      message: "L'envoi de l'email a echoue. Reessayez dans un instant.",
    });
  }

  return res.status(200).json({ ok: true, expiresAtMs });
};
