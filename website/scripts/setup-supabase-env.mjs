#!/usr/bin/env node
/**
 * setup-supabase-env.mjs
 *
 * One-shot installer pour les 3 env vars Supabase sur Vercel + redeploy.
 * Evite d'avoir a naviguer dans le dashboard Vercel manuellement.
 *
 * Usage :
 *   cd website
 *   node scripts/setup-supabase-env.mjs
 *
 * Le script te demande 4 choses :
 *   1. Ton VERCEL_TOKEN (genere ici : https://vercel.com/account/tokens)
 *   2. Ton SUPABASE_SERVICE_ROLE_KEY
 *      (genere ici : https://supabase.com/dashboard/project/avcskcqzxwbkiskvlvxx/settings/api-keys
 *       onglet "Legacy anon, service_role API keys")
 *   3. (defaut OK) SUPABASE_URL = https://avcskcqzxwbkiskvlvxx.supabase.co
 *   4. (defaut OK) SUPABASE_BUCKET = diasmart-files
 *
 * Puis il :
 *   - upserte les 3 env vars sur les 3 environnements (prod + preview + dev)
 *   - declenche un redeploy de la derniere prod
 *   - affiche l'URL du nouveau deploy
 *
 * Aucune valeur n'est ecrite sur disque (pas de .env.local cree).
 */

import { createInterface } from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";

const VERCEL_API = "https://api.vercel.com";
const DEFAULT_URL = "https://avcskcqzxwbkiskvlvxx.supabase.co";
const DEFAULT_BUCKET = "diasmart-files";
const PROJECT_NAME = process.env.VERCEL_PROJECT_NAME || "website"; // override si besoin
const ENV_TARGETS = ["production", "preview", "development"];

const rl = createInterface({ input, output });

async function ask(q, def) {
  const a = await rl.question(def ? `${q} [${def}] : ` : `${q} : `);
  return (a || def || "").trim();
}

async function askSecret(q) {
  // Pas de masquage natif en Node sans dep externe — on previent l'utilisateur.
  console.log(`  (la valeur va etre visible a l'ecran le temps de la taper)`);
  return ask(q);
}

async function vercelFetch(path, init = {}, token, teamSlug) {
  const url = new URL(VERCEL_API + path);
  if (teamSlug) url.searchParams.set("slug", teamSlug);
  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    ...(init.headers || {}),
  };
  const resp = await fetch(url, { ...init, headers });
  const body = await resp.text();
  let parsed;
  try { parsed = body ? JSON.parse(body) : {}; } catch { parsed = { raw: body }; }
  if (!resp.ok) {
    const err = new Error(`Vercel API ${resp.status} ${resp.statusText} : ${body.slice(0, 300)}`);
    err.status = resp.status;
    err.body = parsed;
    throw err;
  }
  return parsed;
}

async function findProject(token, projectName) {
  // Liste tous les projets visibles avec ce token (perso + teams)
  const me = await vercelFetch("/v2/user", {}, token).catch(() => null);
  const teams = await vercelFetch("/v2/teams", {}, token).catch(() => ({ teams: [] }));

  const contexts = [{ name: "(personnel)", slug: null }];
  for (const t of teams.teams || []) contexts.push({ name: t.name, slug: t.slug });

  for (const ctx of contexts) {
    try {
      const r = await vercelFetch(
        `/v9/projects/${encodeURIComponent(projectName)}`,
        {},
        token,
        ctx.slug
      );
      console.log(`  -> projet trouve : ${r.name} (id ${r.id}) sur ${ctx.name}`);
      return { project: r, teamSlug: ctx.slug };
    } catch (e) {
      if (e.status !== 404) throw e;
    }
  }
  throw new Error(`Projet "${projectName}" introuvable. Verifie le nom ou exporte VERCEL_PROJECT_NAME.`);
}

async function listEnvVars(token, projectId, teamSlug) {
  const r = await vercelFetch(`/v9/projects/${projectId}/env`, {}, token, teamSlug);
  return r.envs || [];
}

async function deleteEnvVar(token, projectId, envId, teamSlug) {
  return vercelFetch(
    `/v9/projects/${projectId}/env/${envId}`,
    { method: "DELETE" },
    token,
    teamSlug
  );
}

async function createEnvVar(token, projectId, teamSlug, name, value, sensitive) {
  // Vercel : les Sensitive env vars ne peuvent PAS cibler 'development'.
  // (cf. erreur API : "You cannot set a Sensitive Environment Variable's target to development.")
  const target = sensitive ? ["production", "preview"] : ENV_TARGETS;
  return vercelFetch(
    `/v10/projects/${projectId}/env?upsert=true`,
    {
      method: "POST",
      body: JSON.stringify({
        key: name,
        value,
        type: sensitive ? "sensitive" : "encrypted",
        target,
      }),
    },
    token,
    teamSlug
  );
}

async function upsertEnvVar(token, projectId, teamSlug, name, value, sensitive) {
  // Strategie : supprimer toutes les versions existantes de cette cle puis recreer
  // (evite les conflits multi-env quand certains envs sont deja set et pas d'autres)
  const existing = (await listEnvVars(token, projectId, teamSlug)).filter((e) => e.key === name);
  for (const e of existing) {
    await deleteEnvVar(token, projectId, e.id, teamSlug);
  }
  await createEnvVar(token, projectId, teamSlug, name, value, sensitive);
  const appliedTo = sensitive ? "production, preview" : ENV_TARGETS.join(", ");
  console.log(`  ${name} : ${existing.length > 0 ? "remplace" : "cree"} sur ${appliedTo}`);
}

async function triggerRedeploy(token, projectName, teamSlug) {
  // Recupere le dernier deploy prod et le re-deploye
  const list = await vercelFetch(
    `/v6/deployments?projectId=${encodeURIComponent(projectName)}&target=production&limit=1&state=READY`,
    {},
    token,
    teamSlug
  );
  if (!list.deployments?.length) {
    console.log("  Aucun deploy prod existant trouve — pas de redeploy automatique.");
    console.log("  Pousse un commit sur master pour declencher un build avec les nouvelles env vars.");
    return null;
  }
  const last = list.deployments[0];
  console.log(`  Dernier deploy prod : ${last.uid} (${new Date(last.created).toISOString()})`);
  // POST /v13/deployments avec deploymentId pour redeployer
  const newDep = await vercelFetch(
    "/v13/deployments",
    {
      method: "POST",
      body: JSON.stringify({
        name: projectName,
        deploymentId: last.uid,
        target: "production",
      }),
    },
    token,
    teamSlug
  );
  console.log(`  Nouveau deploy declenche : https://${newDep.url}`);
  console.log(`  Suivi : https://vercel.com/${teamSlug ? teamSlug + "/" : ""}${projectName}/${newDep.id}`);
  return newDep;
}

async function main() {
  console.log("\n=== Setup env vars Supabase pour Vercel ===\n");

  const token = await askSecret("Vercel API token (https://vercel.com/account/tokens)");
  if (!token) throw new Error("VERCEL_TOKEN obligatoire");

  const projectName = await ask("Nom du projet Vercel", PROJECT_NAME);

  console.log("\nRecherche du projet...");
  const { project, teamSlug } = await findProject(token, projectName);

  console.log("\nValeurs Supabase :");
  const supaUrl = await ask("SUPABASE_URL", DEFAULT_URL);
  const supaBucket = await ask("SUPABASE_BUCKET", DEFAULT_BUCKET);
  console.log("\nSUPABASE_SERVICE_ROLE_KEY :");
  console.log("  Recupere-la ici (onglet 'Legacy anon, service_role API keys') :");
  console.log(`  https://supabase.com/dashboard/project/${new URL(supaUrl).hostname.split('.')[0]}/settings/api-keys`);
  const supaKey = await askSecret("SUPABASE_SERVICE_ROLE_KEY");
  if (!supaKey) throw new Error("Cle service_role obligatoire");
  if (supaKey.startsWith("eyJ") === false && supaKey.startsWith("sb_secret_") === false) {
    console.warn("  Attention : cette cle ne commence ni par 'eyJ' (JWT legacy) ni par 'sb_secret_'.");
    const ok = await ask("Continuer quand meme ? (y/N)", "n");
    if (ok.toLowerCase() !== "y") {
      console.log("Annule.");
      process.exit(1);
    }
  }

  console.log("\nUpsert des 3 env vars...");
  await upsertEnvVar(token, project.id, teamSlug, "SUPABASE_URL", supaUrl, false);
  await upsertEnvVar(token, project.id, teamSlug, "SUPABASE_BUCKET", supaBucket, false);
  await upsertEnvVar(token, project.id, teamSlug, "SUPABASE_SERVICE_ROLE_KEY", supaKey, true);

  console.log("\nRedeploy de la prod pour appliquer les nouvelles env vars...");
  await triggerRedeploy(token, project.name, teamSlug);

  console.log("\nFini. Une fois le deploy READY, teste avec :");
  console.log(`  curl -H "Authorization: Bearer <firebase-id-token>" \\`);
  console.log(`    https://${project.name}-<hash>.vercel.app/api/test-supabase-cascade`);
  console.log("\nOu depuis l'app Android (cf. release notes v2.1.60).\n");

  rl.close();
}

main().catch((e) => {
  console.error("\nERREUR :", e.message);
  if (e.body) console.error("  detail :", JSON.stringify(e.body, null, 2));
  rl.close();
  process.exit(1);
});
