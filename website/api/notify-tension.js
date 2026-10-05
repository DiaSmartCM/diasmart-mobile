// POST /api/notify-tension
//
// Appele par l'application (Android ou site) quand un patient enregistre une
// tension tres elevee (>= 180/110). Envoie un push FCM a ses soignants :
//   - les medecins lies (data_sharing actif, patientUid == appelant) ;
//   - les soignants de l'etablissement ou il est inscrit.
//
// Auth : Firebase ID token (Bearer) du patient.
// Body : { systolique: int, diastolique: int, dateHeure: string (ISO local) }
// Reponse : { prevenus: number, sent: number } ou { skipped: "..." }

const { initFirebase, requireAuth } = require("./_firebase.js");

const MAX_PAR_JOUR = 20; // par patient (anti-spam)
const ORIGINES = [
  "https://diasmart-mobile.vercel.app",
  "https://website-omega-umber-20.vercel.app",
];

async function limiteAtteinte(db, uid) {
  const ref = db.collection("rate_limits").doc(`notify_tension_${uid}`);
  const now = Date.now();
  try {
    const snap = await ref.get();
    const d = snap.exists ? snap.data() : null;
    if (!d || now - (d.windowStart || 0) > 24 * 3600 * 1000) {
      await ref.set({ windowStart: now, count: 1 });
      return false;
    }
    if ((d.count || 0) >= MAX_PAR_JOUR) return true;
    await ref.update({ count: (d.count || 0) + 1 });
    return false;
  } catch (_) {
    return false;
  }
}

async function soignantsDu(db, patientUid) {
  const uids = new Set();
  try {
    const liens = await db
      .collection("data_sharing")
      .where("patientUid", "==", patientUid)
      .where("isActive", "==", true)
      .get();
    liens.forEach((d) => d.data().medecinUid && uids.add(d.data().medecinUid));
  } catch (_) {}
  try {
    const aff = await db.collection("affiliations").doc(patientUid).get();
    const a = aff.exists ? aff.data() : null;
    if (a && a.role === "PATIENT" && a.etablissementId) {
      const etab = db.collection("etablissements").doc(a.etablissementId);
      const inscrit = await etab.collection("patients").doc(patientUid).get();
      if (inscrit.exists) {
        const membres = await etab.collection("membres").get();
        membres.forEach((m) => uids.add(m.id));
      }
    }
  } catch (_) {}
  uids.delete(patientUid);
  return [...uids];
}

async function tokensDe(db, uid) {
  const tokens = new Set();
  try {
    const snap = await db.collection("fcm_tokens").where("uid", "==", uid).get();
    snap.forEach((d) => d.data().token && tokens.add(d.data().token));
  } catch (_) {}
  if (tokens.size === 0) {
    try {
      const u = await db.collection("users").doc(uid).get();
      const t = u.exists && u.data() && u.data().fcmToken;
      if (t) tokens.add(t);
    } catch (_) {}
  }
  return [...tokens];
}

module.exports = async (req, res) => {
  const origine = req.headers.origin;
  if (origine && ORIGINES.includes(origine)) {
    res.setHeader("Access-Control-Allow-Origin", origine);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  }
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const { systolique, diastolique, dateHeure } = req.body || {};
  const s = Number(systolique), d = Number(diastolique);
  if (!Number.isInteger(s) || !Number.isInteger(d) || s < 50 || s > 300 || d < 30 || d > 200 || s <= d) {
    return res.status(400).json({ error: "valeurs_invalides" });
  }
  if (s < 180 && d < 110) {
    return res.status(200).json({ skipped: "pas_une_alerte", prevenus: 0 });
  }

  const adm = initFirebase();
  const db = adm.firestore();
  if (await limiteAtteinte(db, decoded.uid)) {
    return res.status(429).json({ error: "rate_limit_exceeded" });
  }

  const destinataires = await soignantsDu(db, decoded.uid);
  if (destinataires.length === 0) {
    return res.status(200).json({ prevenus: 0, sent: 0, reason: "aucun_soignant" });
  }

  let nom = "Un patient";
  try {
    const u = await db.collection("users").doc(decoded.uid).get();
    const p = u.exists ? u.data() : {};
    const complet = `${p.prenom || ""} ${p.nom || ""}`.trim();
    if (complet) nom = complet.substring(0, 60);
  } catch (_) {}

  let quand = "";
  const m = typeof dateHeure === "string" && dateHeure.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (m) quand = ` le ${m[3]}/${m[2]} à ${m[4]}h${m[5]}`;

  const messaging = adm.messaging();
  let sent = 0;
  let prevenus = 0;
  for (const uid of destinataires) {
    const tokens = await tokensDe(db, uid);
    let ok = false;
    for (const token of tokens) {
      try {
        await messaging.send({
          token,
          data: {
            type: "tension_alerte",
            patientUid: decoded.uid,
            title: `Tension très élevée : ${nom}`,
            body: `${s}/${d} mmHg${quand}. Pensez à contacter le patient.`,
          },
          android: { priority: "high", ttl: 6 * 3600 * 1000 },
        });
        sent++;
        ok = true;
      } catch (_) {}
    }
    if (ok) prevenus++;
  }

  return res.status(200).json({ prevenus, sent });
};
