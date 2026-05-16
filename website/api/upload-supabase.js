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

  // Bucket public ⇒ on construit l'URL publique directement.
  const publicUrl = `${SUPABASE_URL}/storage/v1/object/public/${BUCKET}/${encodeURIComponent(path).replace(/%2F/g, "/")}`;

  return res.status(200).json({
    url: publicUrl,
    path,
    sizeBytes: buffer.length,
  });
};
