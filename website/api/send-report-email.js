// POST /api/send-report-email
//
// Auth : Firebase ID token (Bearer).
// Body : { toEmail, subject, bodyText, downloadUrl, fileName, recipientUid? }
//
// Durcis v2.1.35 :
//  - bodyText au lieu de bodyHtml (HTML arbitraire forbidden → anti-phishing)
//  - downloadUrl restreint au bucket Supabase officiel
//  - toEmail doit correspondre soit a l'email du caller, soit a l'email
//    d'un medecin/patient LIE via data_sharing (anti-spam)
//  - rate limit : max 10 emails/24h par UID

const { initFirebase, buildTransporter, requireAuth } = require("./_firebase.js");

const SUPABASE_PROJECT_REF = "avcskcqzxwbkiskvlvxx";
const MAX_EMAILS_PER_24H = 10;

function escapeHtml(s) {
  return String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderEmail({ subject, bodyText, downloadUrl, fileName }) {
  // bodyText est echappe puis converti en paragraphes <p> (jamais d'HTML brut).
  const safeUrl = escapeHtml(downloadUrl);
  const safeName = escapeHtml(fileName || "rapport.pdf");
  const safeBodyHtml = escapeHtml(bodyText || "")
    .split(/\n\s*\n/) // paragraphes
    .map((p) => `<p>${p.replace(/\n/g, "<br>")}</p>`)
    .join("");
  return `<!DOCTYPE html><html><body style="margin:0;padding:0;background:#F5F7FA;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#1A2B4B;">
<div style="max-width:600px;margin:0 auto;padding:32px 24px;">
  <div style="background:linear-gradient(135deg,#00D2FF 0%,#6771E4 100%);border-radius:24px;padding:24px;text-align:center;">
    <div style="color:#FFFFFF;font-size:22px;font-weight:700;letter-spacing:0.5px;">DiaSmart.AI</div>
    <div style="color:rgba(255,255,255,0.85);font-size:13px;margin-top:4px;">${escapeHtml(subject || "Nouveau rapport")}</div>
  </div>
  <div style="background:#FFFFFF;border-radius:24px;padding:28px;margin-top:16px;box-shadow:0 2px 8px rgba(0,0,0,0.04);font-size:15px;line-height:1.55;color:#2C2A5E;">
    ${safeBodyHtml}
    <div style="text-align:center;margin:28px 0 12px;">
      <a href="${safeUrl}" style="display:inline-block;background:#6771E4;color:#FFFFFF;text-decoration:none;padding:14px 24px;border-radius:14px;font-weight:600;">Telecharger ${safeName}</a>
    </div>
    <p style="font-size:12px;color:#8492A6;margin-top:18px;">Lien direct : <a href="${safeUrl}" style="color:#6771E4;">${safeUrl}</a></p>
  </div>
  <div style="text-align:center;margin-top:18px;font-size:12px;color:#8492A6;">&copy; DiaSmart.AI &mdash; Donnees confidentielles. Ne pas partager.</div>
</div></body></html>`;
}

/**
 * Verifie que le caller a le droit d'envoyer un email a `toEmail` :
 *  - soit toEmail == son propre email (auto-envoi)
 *  - soit toEmail correspond a un MEDECIN ou un PATIENT lie via
 *    data_sharing.isActive = true.
 */
async function isRecipientAllowed(db, callerUid, callerEmail, toEmail) {
  const targetEmail = toEmail.toLowerCase().trim();
  if (callerEmail && callerEmail.toLowerCase().trim() === targetEmail) {
    return true;
  }
  // Cherche tous les data_sharing du caller (en tant que patient OU medecin)
  // et verifie qu'un des contreparts a cet email.
  const [asPatient, asMedecin] = await Promise.all([
    db.collection("data_sharing")
      .where("patientUid", "==", callerUid)
      .where("isActive", "==", true)
      .get(),
    db.collection("data_sharing")
      .where("medecinUid", "==", callerUid)
      .where("isActive", "==", true)
      .get(),
  ]);
  const linkedUids = new Set();
  asPatient.forEach((d) => linkedUids.add(d.data().medecinUid));
  asMedecin.forEach((d) => linkedUids.add(d.data().patientUid));
  if (linkedUids.size === 0) return false;
  // Verifie l'email des users lies (max 10 reads).
  const uidsArr = Array.from(linkedUids).slice(0, 10);
  const docs = await Promise.all(
    uidsArr.map((uid) => db.collection("users").doc(uid).get())
  );
  return docs.some((doc) => {
    const data = doc.data();
    return data && data.email && data.email.toLowerCase().trim() === targetEmail;
  });
}

async function checkRateLimit(db, uid) {
  const ref = db.collection("rate_limits").doc(`email_${uid}`);
  const now = Date.now();
  const windowMs = 24 * 60 * 60 * 1000;
  const snap = await ref.get();
  const data = snap.exists ? snap.data() : { windowStart: now, count: 0 };
  if (now - (data.windowStart || 0) > windowMs) {
    await ref.set({ windowStart: now, count: 1 });
    return { ok: true, remaining: MAX_EMAILS_PER_24H - 1 };
  }
  if ((data.count || 0) >= MAX_EMAILS_PER_24H) {
    return { ok: false, retryAfter: windowMs - (now - data.windowStart) };
  }
  await ref.update({ count: (data.count || 0) + 1 });
  return { ok: true, remaining: MAX_EMAILS_PER_24H - (data.count + 1) };
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { toEmail, subject, bodyText, bodyHtml, downloadUrl, fileName } = req.body || {};
  if (!toEmail || typeof toEmail !== "string" || !toEmail.includes("@")) {
    return res.status(400).json({ error: "invalid_email" });
  }
  if (!downloadUrl || typeof downloadUrl !== "string") {
    return res.status(400).json({ error: "missing_downloadUrl" });
  }
  // Restriction stricte : uniquement notre bucket Supabase officiel.
  // Firebase Storage est conserve en regex pour compatibilite avec
  // d'anciens uploads, mais notre upload-supabase utilise SUPABASE_URL.
  const supabaseHostRe = new RegExp(
    `^https://${SUPABASE_PROJECT_REF}\\.supabase\\.co/storage/`
  );
  const firebaseHostRe = /^https:\/\/firebasestorage\.googleapis\.com\//;
  if (!supabaseHostRe.test(downloadUrl) && !firebaseHostRe.test(downloadUrl)) {
    return res.status(400).json({ error: "invalid_downloadUrl_host" });
  }

  // Anti-phishing : on accepte uniquement bodyText (string plain), pas
  // de HTML. Si un caller envoie encore bodyHtml (anciens clients), on
  // l'ignore et on prefere bodyText, ou on convertit bodyHtml en text.
  const rawBody = (typeof bodyText === "string" && bodyText.length > 0)
    ? bodyText
    : (typeof bodyHtml === "string" ? bodyHtml.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim() : "");
  if (rawBody.length > 5000) {
    return res.status(400).json({ error: "body_too_long" });
  }

  // Verification du destinataire (anti-phishing externe).
  const adm = initFirebase();
  const db = adm.firestore();
  let allowed = false;
  try {
    allowed = await isRecipientAllowed(db, decoded.uid, decoded.email, toEmail);
  } catch (e) {
    console.error("recipient check failed:", e.message);
    return res.status(500).json({ error: "recipient_check_failed" });
  }
  if (!allowed) {
    return res.status(403).json({
      error: "recipient_not_linked",
      message: "Vous ne pouvez envoyer des rapports qu'a vos contacts lies (medecin/patient).",
    });
  }

  // Rate limit (10 emails / 24h).
  try {
    const rl = await checkRateLimit(db, decoded.uid);
    if (!rl.ok) {
      return res.status(429).json({
        error: "rate_limit_exceeded",
        retryAfterMs: rl.retryAfter,
      });
    }
  } catch (e) {
    console.error("rate limit check failed:", e.message);
    // Best-effort : on continue si Firestore est down (sinon on bloque tout).
  }

  const safeSubject = String(subject || "Rapport DiaSmart").substring(0, 120);
  const html = renderEmail({
    subject: safeSubject,
    bodyText: rawBody,
    downloadUrl,
    fileName,
  });

  try {
    const transporter = buildTransporter();
    await transporter.sendMail({
      from: `"DiaSmart.AI" <${process.env.GMAIL_USER}>`,
      to: toEmail,
      subject: safeSubject,
      text: `${rawBody}\n\nTelechargement : ${downloadUrl}`,
      html,
    });
  } catch (e) {
    console.error("Report email send failed:", e.message);
    return res.status(502).json({
      error: "email_send_failed",
      message: "L'envoi de l'email a echoue.",
    });
  }

  return res.status(200).json({ ok: true });
};
