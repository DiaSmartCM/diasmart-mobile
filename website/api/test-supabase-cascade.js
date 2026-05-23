// GET /api/test-supabase-cascade
//
// Endpoint READ-ONLY (aucune suppression) pour valider la config Supabase
// avant le premier vrai delete-account. Verifie :
//   1. Les 3 env vars sont presentes (SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
//      SUPABASE_BUCKET).
//   2. La cle service_role peut effectivement appeler l'API Storage.
//   3. Combien de blobs existent sous chaque prefixe pour l'utilisateur courant.
//
// Auth : Firebase ID token (Bearer), meme pattern que delete-account.
// Pas de body. La methode est GET pour eviter tout effet de bord.
//
// Reponse OK :
//   200 {
//     env: { has_url: true, has_key: true, has_bucket: true, bucket: "..." },
//     listings: {
//       "reports/<uid>": { ok: true, count: N, sample: [...] },
//       "profile_photos/<uid>": { ok: true, count: 0, sample: [] },
//       "chat_attachments/<uid>": { ok: true, count: M, sample: [...] }
//     },
//     totalBlobs: N+M,
//     wouldDelete: [ ... liste des paths qui seraient supprimes par
//                    delete-account si l'utilisateur supprimait son compte ... ],
//     dryRun: true
//   }
//
// Reponse KO :
//   400 si une env var manque (avec liste de ce qui manque)
//   401 si non authentifie
//   502 si l'API Supabase repond une erreur (cle invalide, bucket inexistant)

const { requireAuth } = require("./_firebase.js");

async function listBlobs(url, key, bucket, prefix) {
  const out = { ok: false, count: 0, sample: [], paths: [], error: null };
  try {
    let offset = 0;
    while (true) {
      const resp = await fetch(`${url}/storage/v1/object/list/${bucket}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          apikey: key,
          Authorization: `Bearer ${key}`,
        },
        body: JSON.stringify({
          prefix,
          limit: 1000,
          offset,
          sortBy: { column: "name", order: "asc" },
        }),
      });
      if (!resp.ok) {
        out.error = `HTTP ${resp.status} ${await resp.text().catch(() => "")}`.slice(0, 200);
        return out;
      }
      const items = await resp.json();
      if (!Array.isArray(items)) {
        out.error = "response is not an array";
        return out;
      }
      for (const it of items) {
        if (it && it.name) out.paths.push(`${prefix}/${it.name}`);
      }
      if (items.length < 1000) break;
      offset += items.length;
    }
    out.ok = true;
    out.count = out.paths.length;
    out.sample = out.paths.slice(0, 5);
    return out;
  } catch (e) {
    out.error = e.message;
    return out;
  }
}

module.exports = async (req, res) => {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;
  const uid = decoded.uid;

  // 1. Verification env
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const bucket = process.env.SUPABASE_BUCKET || "diasmart-files";
  const env = {
    has_url: !!url,
    has_key: !!key,
    has_bucket: !!process.env.SUPABASE_BUCKET, // defaut applique mais on indique la presence explicite
    bucket,
  };

  if (!url || !key) {
    return res.status(400).json({
      ok: false,
      env,
      error: "missing_env",
      message:
        "Variables manquantes : " +
        [!url && "SUPABASE_URL", !key && "SUPABASE_SERVICE_ROLE_KEY"]
          .filter(Boolean)
          .join(", ") +
        ". Configure-les sur Vercel puis redeploie.",
    });
  }

  // 2. Listing des 3 prefixes
  const prefixes = [
    `reports/${uid}`,
    `profile_photos/${uid}`,
    `chat_attachments/${uid}`,
  ];

  const listings = {};
  const wouldDelete = [];
  let anyError = false;
  for (const p of prefixes) {
    const r = await listBlobs(url, key, bucket, p);
    listings[p] = {
      ok: r.ok,
      count: r.count,
      sample: r.sample,
      error: r.error,
    };
    if (!r.ok) anyError = true;
    wouldDelete.push(...r.paths);
  }

  // 3. Verdict
  if (anyError) {
    return res.status(502).json({
      ok: false,
      env,
      listings,
      message:
        "Au moins un prefixe a echoue. Verifie que la cle est bien service_role " +
        "(pas anon), que le bucket existe, et que les RLS policies autorisent " +
        "le service_role a lister les objets.",
    });
  }

  return res.status(200).json({
    ok: true,
    env,
    listings,
    totalBlobs: wouldDelete.length,
    wouldDelete: wouldDelete.slice(0, 50), // tronque pour eviter une reponse enorme
    truncated: wouldDelete.length > 50,
    dryRun: true,
    message:
      wouldDelete.length === 0
        ? "Config OK. Tu n'as aucun blob Supabase actuellement — la cascade sera no-op a la suppression."
        : `Config OK. ${wouldDelete.length} blob(s) seraient supprimes par delete-account.`,
  });
};
