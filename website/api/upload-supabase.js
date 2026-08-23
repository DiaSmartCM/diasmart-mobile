// POST /api/upload-supabase
//
// Uploads un fichier vers Supabase Storage (bucket public `diasmart-files`)
// puis retourne son URL publique. Pensé pour les rapports PDF (taille ~50 KB)
// mais compatible avec n'importe quel binaire jusqu'à 4 MB (limite Vercel
// Hobby pour le body JSON après le decode base64).
//
// Auth : Firebase ID token (Bearer). Le path est forcé sur le UID du caller.
// Body : { folder: "reports"|"profile_photos"|..., fileName, contentType, base64 }
//
// Réponse : { url, path }

const crypto = require("node:crypto");
const { requireAuth } = require("./_firebase.js");

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_ROLE = process.env.SUPABASE_SERVICE_ROLE;

// Duree de validite des liens de telechargement : 90 jours.
//
// Compromis assume. Trop court, un medecin qui rouvre le rapport d'un patient
// deux mois plus tard tombe sur un lien mort et croit a une panne. Trop long,
// on retombe sur l'acces perpetuel qu'on cherche justement a eviter. Un
// trimestre couvre l'intervalle habituel entre deux consultations de suivi
// pour un diabetique.
const SIGNED_URL_TTL_SECONDS = 90 * 24 * 60 * 60;
const BUCKET = "diasmart-files";

const ALLOWED_FOLDERS = new Set([
  "reports",
  "profile_photos",
  "shared",
]);

// Magic numbers acceptes pour pdf/png/jpg : on bloque les fichiers
// uploades qui pretendent etre des PDFs mais sont du JS/exe/etc.
const MAGIC_BYTES = {
  "application/pdf": [0x25, 0x50, 0x44, 0x46], // "%PDF"
  "image/png": [0x89, 0x50, 0x4E, 0x47],
  "image/jpeg": [0xFF, 0xD8, 0xFF],
  "image/webp": [0x52, 0x49, 0x46, 0x46], // RIFF
};

function bufferStartsWith(buf, prefix) {
  if (buf.length < prefix.length) return false;
  for (let i = 0; i < prefix.length; i++) {
    if (buf[i] !== prefix[i]) return false;
  }
  return true;
}

function sanitizeFileName(name) {
  return String(name || "file")
    .replace(/[^a-zA-Z0-9._-]/g, "_")
    .substring(0, 120);
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE) {
    return res.status(500).json({ error: "supabase_not_configured" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { folder, fileName, contentType, base64 } = req.body || {};
  if (!folder || !ALLOWED_FOLDERS.has(folder)) {
    return res.status(400).json({ error: "invalid_folder" });
  }
  if (!fileName || typeof fileName !== "string") {
    return res.status(400).json({ error: "missing_fileName" });
  }
  if (!base64 || typeof base64 !== "string") {
    return res.status(400).json({ error: "missing_base64" });
  }
  const ct = contentType || "application/octet-stream";

  let buffer;
  try {
    buffer = Buffer.from(base64, "base64");
  } catch (e) {
    return res.status(400).json({ error: "invalid_base64" });
  }
  if (buffer.length === 0 || buffer.length > 10 * 1024 * 1024) {
    return res.status(400).json({ error: "invalid_size" });
  }

  // Verification magic bytes : empeche d'uploader un .exe renomme en .pdf
  // pour s'en servir comme cdn de malware via DiaSmart.
  const expectedMagic = MAGIC_BYTES[ct];
  if (expectedMagic && !bufferStartsWith(buffer, expectedMagic)) {
    return res.status(400).json({
      error: "content_type_mismatch",
      message: `Le fichier ne correspond pas au type declare (${ct}).`,
    });
  }

  // Path durcis v2.1.35 :
  //   {folder}/{uid}/{16 bytes hex aleatoires}_{nom sanitize}
  // Les 16 octets aleatoires (128 bits) rendent l'URL imprevisible :
  // meme connaissant uid + nom de fichier, un attaquant ne peut pas
  // deviner le path. C'est une "capability URL" — a defendre comme
  // un secret (ce que fait deja le client en l'envoyant uniquement aux
  // destinataires legitimes via la messagerie).
  const safeName = sanitizeFileName(fileName);
  const randomSuffix = crypto.randomBytes(16).toString("hex");
  const path = `${folder}/${decoded.uid}/${randomSuffix}_${safeName}`;

  // Upload via API REST Storage
  const uploadUrl = `${SUPABASE_URL}/storage/v1/object/${BUCKET}/${encodeURIComponent(path).replace(/%2F/g, "/")}`;
  let uploadResp;
  try {
    uploadResp = await fetch(uploadUrl, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE}`,
        "apikey": SUPABASE_SERVICE_ROLE,
        "Content-Type": ct,
        "x-upsert": "true",
      },
      body: buffer,
    });
  } catch (e) {
    console.error("Supabase upload network error:", e.message);
    return res.status(502).json({ error: "supabase_unreachable" });
  }

  if (!uploadResp.ok) {
    const text = await uploadResp.text().catch(() => "");
    console.error("Supabase upload failed:", uploadResp.status, text);
    return res.status(502).json({
      error: "supabase_upload_failed",
      status: uploadResp.status,
      detail: text.substring(0, 200),
    });
  }

  // ── Lien signe, a duree limitee ────────────────────────────────────
  //
  // Le bucket reste PRIVE. Auparavant on renvoyait une URL publique dont la
  // seule protection etait l'imprevisibilite du chemin. Cela suffit tant que
  // le lien ne circule pas — mais un rapport medical nominatif transfere sur
  // WhatsApp, capture en photo ou retrouve dans un historique restait lisible
  // pour toujours, sans aucun moyen de revoquer l'acces.
  //
  // Un lien signe expire de lui-meme. Le medecin ouvre le rapport pendant la
  // duree prevue, ensuite le lien meurt. C'est la difference entre un document
  // confie et un document publie.
  const encodedPath = encodeURIComponent(path).replace(/%2F/g, "/");
  let signedUrl;
  try {
    const signResp = await fetch(
      `${SUPABASE_URL}/storage/v1/object/sign/${BUCKET}/${encodedPath}`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE}`,
          "apikey": SUPABASE_SERVICE_ROLE,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ expiresIn: SIGNED_URL_TTL_SECONDS }),
      }
    );
    if (!signResp.ok) {
      const detail = await signResp.text().catch(() => "");
      console.error("Supabase sign failed:", signResp.status, detail);
      return res.status(502).json({
        error: "supabase_sign_failed",
        status: signResp.status,
        detail: detail.substring(0, 200),
      });
    }
    const signed = await signResp.json();
    // Supabase renvoie un chemin relatif ("/object/sign/...?token=…").
    signedUrl = `${SUPABASE_URL}/storage/v1${signed.signedURL || signed.signedUrl}`;
  } catch (e) {
    console.error("Supabase sign network error:", e.message);
    return res.status(502).json({ error: "supabase_sign_unreachable" });
  }

  return res.status(200).json({
    url: signedUrl,
    path,
    sizeBytes: buffer.length,
    expiresInSeconds: SIGNED_URL_TTL_SECONDS,
  });
};
