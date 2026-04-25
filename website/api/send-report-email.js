// POST /api/send-report-email
//
// Auth : Firebase ID token (Bearer).
// Body : { toEmail, subject, bodyHtml, downloadUrl, fileName }
//
// Envoie un email avec un lien de telechargement vers le PDF stocke
// dans Firebase Storage. On n'attache pas le fichier en piece jointe pour
// rester sous la limite de Gmail (et eviter de retelecharger le PDF
// cote serveur) : le destinataire clique sur le lien.
const { buildTransporter, requireAuth } = require("./_firebase.js");

function escapeHtml(s) {
  return String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderEmail({ subject, bodyHtml, downloadUrl, fileName }) {
  const safeUrl = escapeHtml(downloadUrl);
  const safeName = escapeHtml(fileName || "rapport.pdf");
  const safeBody = bodyHtml || "";
  return `<!DOCTYPE html><html><body style="margin:0;padding:0;background:#F5F7FA;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#1A2B4B;">
<div style="max-width:600px;margin:0 auto;padding:32px 24px;">
  <div style="background:linear-gradient(135deg,#00D2FF 0%,#6771E4 100%);border-radius:24px;padding:24px;text-align:center;">
    <div style="color:#FFFFFF;font-size:22px;font-weight:700;letter-spacing:0.5px;">DiaSmart.AI</div>
    <div style="color:rgba(255,255,255,0.85);font-size:13px;margin-top:4px;">${escapeHtml(subject || "Nouveau rapport")}</div>
  </div>
  <div style="background:#FFFFFF;border-radius:24px;padding:28px;margin-top:16px;box-shadow:0 2px 8px rgba(0,0,0,0.04);font-size:15px;line-height:1.55;color:#2C2A5E;">
    ${safeBody}
    <div style="text-align:center;margin:28px 0 12px;">
      <a href="${safeUrl}" style="display:inline-block;background:#6771E4;color:#FFFFFF;text-decoration:none;padding:14px 24px;border-radius:14px;font-weight:600;">📄 Telecharger ${safeName}</a>
    </div>
    <p style="font-size:12px;color:#8492A6;margin-top:18px;">Lien direct : <a href="${safeUrl}" style="color:#6771E4;">${safeUrl}</a></p>
  </div>
  <div style="text-align:center;margin-top:18px;font-size:12px;color:#8492A6;">&copy; DiaSmart.AI — Donnees confidentielles. Ne pas partager.</div>
</div></body></html>`;
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { toEmail, subject, bodyHtml, downloadUrl, fileName } = req.body || {};
  if (!toEmail || typeof toEmail !== "string" || !toEmail.includes("@")) {
    return res.status(400).json({ error: "invalid_email" });
  }
  if (!downloadUrl || typeof downloadUrl !== "string") {
    return res.status(400).json({ error: "missing_downloadUrl" });
  }
  // Anti-spam basique : refus si l'URL ne pointe pas vers Firebase Storage.
  if (!/firebasestorage\.googleapis\.com/.test(downloadUrl)) {
    return res.status(400).json({ error: "invalid_downloadUrl_host" });
  }

  const safeSubject = String(subject || "Rapport DiaSmart").substring(0, 120);
  const html = renderEmail({
    subject: safeSubject,
    bodyHtml,
    downloadUrl,
    fileName,
  });

  try {
    const transporter = buildTransporter();
    await transporter.sendMail({
      from: `"DiaSmart.AI" <${process.env.GMAIL_USER}>`,
      to: toEmail,
      subject: safeSubject,
      text: `Vous avez recu un rapport DiaSmart. Telechargement : ${downloadUrl}`,
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
