// POST /api/rolly-chat
//
// Proxy securise pour Gemini (ROLLY assistant clinique DiaSmart).
// La cle API Gemini est stockee SERVER-SIDE uniquement (env GEMINI_API_KEY)
// pour ne JAMAIS apparaitre dans l'APK.
//
// Auth : Firebase ID token (Bearer header).
// Body : {
//   mode: "chat" | "chat_context" | "meal_json" | "meal_image" |
//         "glucose_analysis" | "nutrition_advice" | "risk_prediction" |
//         "predictive_7days",
//   message: string,
//   context?: string,           // contexte patient (pour modes context)
//   history?: string,           // historique chat (pour modes chat)
//   imageBase64?: string,       // pour mode meal_image
//   stream?: boolean,           // SSE streaming (defaut false)
//   useFallback?: boolean       // utilise gemini-2.0-flash au lieu de 2.5
// }
//
// Reponse :
//   - stream=false : { text: "..." } ou { json: {...} }
//   - stream=true  : SSE events `data: <chunk>\n\n`, fin avec `data: [DONE]\n\n`

const { GoogleGenerativeAI } = require("@google/generative-ai");
const { initFirebase, requireAuth } = require("./_firebase.js");
const {
  ROLLY_PRIMARY_PROMPT,
  ROLLY_FALLBACK_PROMPT,
  MEAL_JSON_PROMPT,
  MEAL_IMAGE_PROMPT,
  GLUCOSE_ANALYSIS_PROMPT,
  NUTRITION_ADVICE_PROMPT,
  RISK_PREDICTION_PROMPT,
  PREDICTIVE_7DAYS_PROMPT,
} = require("./_rolly-prompts.js");

const MAX_INPUT_LENGTH = 16000;
const MAX_REQUESTS_PER_DAY = 200; // par UID

function pickSystemPrompt(mode, useFallback) {
  if (useFallback) return ROLLY_FALLBACK_PROMPT;
  switch (mode) {
    case "meal_json": return MEAL_JSON_PROMPT;
    case "meal_image": return MEAL_IMAGE_PROMPT;
    case "glucose_analysis": return GLUCOSE_ANALYSIS_PROMPT;
    case "nutrition_advice": return NUTRITION_ADVICE_PROMPT;
    case "risk_prediction": return RISK_PREDICTION_PROMPT;
    case "predictive_7days": return PREDICTIVE_7DAYS_PROMPT;
    default: return ROLLY_PRIMARY_PROMPT; // chat, chat_context
  }
}

// v2.1.61 : preambule de langue prioritaire. Le client envoie le code locale
// de l'app (ex "fr", "ar", "pcm", "dua", "bas", "ful", "dii") et on l'injecte
// en haut du systemInstruction pour forcer Gemini a repondre dans cette langue
// MEME si le message utilisateur est en francais (ex le user a configure son
// app en pidgin mais ecrit en francais pour aller plus vite — il veut quand
// meme une reponse en pidgin).
const LANGUAGE_NAMES = {
  fr: "francais",
  en: "anglais",
  ar: "arabe",
  pcm: "Pidgin English camerounais (Kamtok)",
  dua: "duala",
  bas: "bassa",
  ful: "fulfulde (Adamaoua/Nord Cameroun)",
  dii: "dii (dourou)",
};

function buildLanguagePreamble(userLanguage, mode) {
  const tag = (userLanguage || "").toLowerCase().split("-")[0];
  const name = LANGUAGE_NAMES[tag] || null;

  // Modes JSON structures (meal_json, meal_image) : le preambule complet
  // perturberait le format, mais les VALEURS du JSON (nom du plat,
  // description, recommandations) sont lues par le patient et doivent donc
  // suivre la langue de l'app. On envoie une directive ciblee qui distingue
  // les cles (contrat technique, jamais traduites) des valeurs (texte libre).
  if (mode === "meal_json" || mode === "meal_image") {
    if (!name) return null; // langue inconnue -> laisse Gemini decider
    return `═══ LANGUE DU CONTENU ═══
Les NOMS DE CLES du JSON restent EXACTEMENT ceux du schema demande (minuscules
avec underscores, en francais). Ne les traduis JAMAIS, ne les renomme pas.
En revanche, le TEXTE contenu dans les valeurs — "nom_repas", "description",
"impact_glycemique", "recommandations", "alternatives_saines" — doit etre
redige en ${name} (code ${tag}), car il est lu directement par le patient.
"categorie_ig" garde ses valeurs techniques : "bas", "moyen" ou "eleve".
Ne produis rien d'autre que l'objet JSON.`;
  }

  if (!name) return null; // langue inconnue / non transmise -> comportement par defaut (detection auto)

  return `═══ LANGUE DE REPONSE ═══
Configuration utilisateur : application DiaSmart en ${name} (code ${tag}).

REGLE DE DECISION (dans l'ordre) :
1. SI le message utilisateur est clairement ecrit dans une langue donnee (au moins 5 mots significatifs detectables), reponds DANS LA LANGUE DU MESSAGE.
2. SINON (message tres court, mot isole, ambiguite, salutation, "oui"/"non"), reponds dans la langue configuree de l'app : ${name}.
3. Code-switching naturel : si l'utilisateur melange (ex francais + pidgin), melange aussi dans ta reponse — c'est le comportement naturel au Cameroun.

VOCABULAIRE :
- Termes medicaux techniques (insuline, HbA1c, mg/dL, TIR, glycemie, IMC) restent toujours en francais ou anglais selon la langue principale.
- Numeros d'urgence (SAMU 119, Police 117, Pompiers 118) restent en chiffres + francais.
- Ne demande JAMAIS la permission de changer de langue — applique directement.

`;
}

function pickModelName(useFallback) {
  return useFallback ? "gemini-2.0-flash" : "gemini-2.5-flash";
}

function buildUserPrompt(mode, message, context, history) {
  const parts = [];
  if (history && history.trim().length > 0) {
    parts.push(history.trim());
    parts.push("");
  }
  if (context && context.trim().length > 0 && (mode === "chat_context" || mode === "glucose_analysis" || mode === "nutrition_advice" || mode === "risk_prediction" || mode === "predictive_7days")) {
    parts.push("Données patient :");
    parts.push(context.trim());
    parts.push("");
  }
  parts.push(mode === "chat" || mode === "chat_context" ? `Question : ${message}` : message);
  return parts.join("\n").trim();
}

async function checkRateLimit(db, uid) {
  const ref = db.collection("rate_limits").doc(`rolly_${uid}`);
  const now = Date.now();
  const windowMs = 24 * 60 * 60 * 1000;
  const snap = await ref.get();
  const data = snap.exists ? snap.data() : { windowStart: now, count: 0 };
  if (now - (data.windowStart || 0) > windowMs) {
    await ref.set({ windowStart: now, count: 1 });
    return { ok: true, remaining: MAX_REQUESTS_PER_DAY - 1 };
  }
  if ((data.count || 0) >= MAX_REQUESTS_PER_DAY) {
    return { ok: false, retryAfter: windowMs - (now - data.windowStart) };
  }
  await ref.update({ count: (data.count || 0) + 1 });
  return { ok: true, remaining: MAX_REQUESTS_PER_DAY - (data.count + 1) };
}

module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "method_not_allowed" });
  }

  const decoded = await requireAuth(req, res);
  if (!decoded) return;

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(500).json({ error: "gemini_not_configured" });
  }

  const {
    mode = "chat",
    message = "",
    context = "",
    history = "",
    imageBase64 = "",
    stream = false,
    useFallback = false,
    userLanguage = "",  // v2.1.61 : code locale de l'app (fr, en, ar, pcm, dua, bas, ful, dii)
  } = req.body || {};

  if (typeof message !== "string" || message.length === 0) {
    return res.status(400).json({ error: "missing_message" });
  }
  if (message.length > MAX_INPUT_LENGTH) {
    return res.status(400).json({ error: "message_too_long" });
  }
  if (context && context.length > MAX_INPUT_LENGTH) {
    return res.status(400).json({ error: "context_too_long" });
  }
  if (history && history.length > MAX_INPUT_LENGTH) {
    return res.status(400).json({ error: "history_too_long" });
  }
  // Image base64 limite a ~4 MB (avant decode = ~5.4 MB en base64)
  if (imageBase64 && imageBase64.length > 5_400_000) {
    return res.status(400).json({ error: "image_too_large" });
  }

  // Rate limit best-effort (n'echoue pas la requete si Firestore est down).
  try {
    const adm = initFirebase();
    const rl = await checkRateLimit(adm.firestore(), decoded.uid);
    if (!rl.ok) {
      return res.status(429).json({
        error: "rate_limit_exceeded",
        retryAfterMs: rl.retryAfter,
      });
    }
  } catch (e) {
    console.warn("rate_limit_check_failed:", e.message);
  }

  let systemInstruction = pickSystemPrompt(mode, useFallback);
  // v2.1.61 : prepend preambule de langue si le client a transmis sa locale.
  const langPreamble = buildLanguagePreamble(userLanguage, mode);
  if (langPreamble) {
    systemInstruction = langPreamble + systemInstruction;
  }
  const modelName = pickModelName(useFallback);
  const userPrompt = buildUserPrompt(mode, message, context, history);

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({
    model: modelName,
    systemInstruction,
    generationConfig: {
      temperature: 0.4,
      topP: 0.85,
      maxOutputTokens: 8192,
    },
  });

  // Construire le contenu (texte + image eventuelle).
  const parts = [{ text: userPrompt }];
  if (imageBase64 && imageBase64.length > 0) {
    parts.push({
      inlineData: {
        mimeType: "image/jpeg",
        data: imageBase64,
      },
    });
  }

  // ── Mode SSE streaming ──────────────────────────────────────
  if (stream) {
    res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
    res.setHeader("Cache-Control", "no-cache, no-transform");
    res.setHeader("Connection", "keep-alive");
    res.setHeader("X-Accel-Buffering", "no");
    res.flushHeaders?.();

    try {
      const result = await model.generateContentStream({ contents: [{ role: "user", parts }] });
      for await (const chunk of result.stream) {
        const text = chunk.text();
        if (text) {
          // SSE : chaque ligne `data: ...` se termine par `\n\n`.
          // On encode en JSON pour preserver les retours a la ligne.
          res.write(`data: ${JSON.stringify({ text })}\n\n`);
        }
      }
      res.write(`data: [DONE]\n\n`);
      res.end();
    } catch (e) {
      console.error("rolly stream error:", e.message);
      // Si on a deja envoye les headers, on peut juste annoncer l'erreur dans le stream.
      res.write(`data: ${JSON.stringify({ error: e.message || "stream_failed" })}\n\n`);
      res.write(`data: [DONE]\n\n`);
      res.end();
    }
    return;
  }

  // ── Mode one-shot JSON ──────────────────────────────────────
  try {
    const result = await model.generateContent({ contents: [{ role: "user", parts }] });
    const text = result.response.text() || "";
    // Pour les modes structures (meal_*), on essaie de parser le JSON.
    if (mode === "meal_json" || mode === "meal_image") {
      // Tolere les blocs ```json ... ``` ou texte autour.
      const cleaned = text
        .replace(/^```json\s*/i, "")
        .replace(/^```\s*/i, "")
        .replace(/```\s*$/i, "")
        .trim();
      const firstBrace = cleaned.indexOf("{");
      const lastBrace = cleaned.lastIndexOf("}");
      const jsonStr = firstBrace >= 0 && lastBrace > firstBrace
        ? cleaned.slice(firstBrace, lastBrace + 1)
        : cleaned;
      try {
        const parsed = JSON.parse(jsonStr);
        return res.status(200).json({ json: parsed, text });
      } catch (parseErr) {
        // Si le parsing echoue, on retourne le texte brut quand meme.
        return res.status(200).json({ text, parseError: parseErr.message });
      }
    }
    return res.status(200).json({ text });
  } catch (e) {
    console.error("rolly generation error:", e.message);
    return res.status(502).json({
      error: "generation_failed",
      message: e.message || "Gemini API a echoue.",
    });
  }
};
