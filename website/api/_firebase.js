// Shared Firebase Admin + nodemailer init for /api routes.
// All secrets come from Vercel env vars (see _setup-env.mjs).
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

function loadPrivateKey() {
  const b64 = process.env.FIREBASE_PRIVATE_KEY_B64;
  if (b64) return Buffer.from(b64, "base64").toString("utf8");
  const raw = process.env.FIREBASE_PRIVATE_KEY || "";
  return raw.replace(/\\n/g, "\n");
}

function initFirebase() {
  if (admin.apps.length) return admin;
  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      privateKey: loadPrivateKey(),
    }),
  });
  return admin;
}

function buildTransporter() {
  return nodemailer.createTransport({
    service: "gmail",
    auth: {
      user: process.env.GMAIL_USER,
      pass: process.env.GMAIL_APP_PASSWORD,
    },
  });
}

/**
 * Verifies the `Authorization: Bearer <idToken>` header and returns
 * the Firebase-decoded token on success. Sends an error response and
 * returns null on failure.
 */
async function requireAuth(req, res) {
  const header = req.headers.authorization || req.headers.Authorization || "";
  const m = header.match(/^Bearer\s+(.+)$/i);
  if (!m) {
    res.status(401).json({ error: "missing_authorization" });
    return null;
  }
  try {
    const adm = initFirebase();
    return await adm.auth().verifyIdToken(m[1]);
  } catch (e) {
    res.status(401).json({ error: "invalid_token", message: e.message });
    return null;
  }
}

module.exports = {
  admin,
  initFirebase,
  buildTransporter,
  requireAuth,
};
