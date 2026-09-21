// POST /api/rolly-tts
//
// Synthese vocale naturelle pour ROLLY (option "voix naturelle" du patient).
//
// Pourquoi un endpoint a part
// ---------------------------
// La voix par defaut de l'app reste le TextToSpeech d'Android : gratuite,
// hors-ligne, sans quota. Elle sonne robotique sur les telephones dont le
// moteur est celui du constructeur. Cet endpoint offre l'alternative, mais
// il consomme le quota gratuit Gemini (partage par TOUS les patients) et
// fait passer de l'audio sur le reseau mobile. Il n'est donc appele que si
// le patient a active l'option lui-meme dans les reglages.
//
// Auth : Firebase ID token (Bearer header), comme rolly-chat.
// Body : { text: string, voice?: string, style?: string }
// Reponse : {
//   audioBase64: "...",          // PCM brut, 16 bits, mono
//   mimeType: "audio/L16",
//   sampleRate: 24000,
//   model: "gemini-3.1-flash-tts-preview"
// }
//
// Le client joue ce PCM avec AudioTrack. En cas d'erreur (quota, reseau,
// modele indisponible) il retombe sur le TextToSpeech local : la voix est
// moins belle, mais le patient entend quand meme sa reponse.

const { initFirebase, requireAuth } = require("./_firebase.js");

// Les modeles TTS sont distincts des modeles de dialogue : ils ne prennent
// que du texte et ne rendent que de l'audio (24 kHz, 16 bits, mono).
// Seul gemini-3.1-flash-tts-preview sait streamer ; on ne streame pas ici,
// mais il reste le plus rapide, donc il passe en premier.
// Chaque modele apporte son propre quota gratuit, d'ou la chaine.
const MODEL_CHAIN = [
  "gemini-3.1-flash-tts-preview",
  "gemini-2.5-flash-preview-tts",
];

const ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";

// Voix par defaut. "Kore" est posee et claire — la plus proche du ton
// clinique de ROLLY parmi les voix proposees par Google.
const DEFAULT_VOICE = "Kore";
const VOICES_AUTORISEES = new Set([
  "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
  "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
  "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
  "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
  "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
]);

// Une reponse de ROLLY tient en quelques phrases. Au-dela, la synthese coute
// cher en quota et la qualite derive (limite documentee par Google).
const MAX_TEXT_LENGTH = 1200;

// Plafond par patient et par jour. Volontairement bas : le quota gratuit est
// partage par tout le monde, et l'option reste un confort, pas un soin.
const MAX_REQUESTS_PER_DAY = 30;

const SAMPLE_RATE = 24000;

/** Vrai si l'erreur tient au modele (quota, retrait, surcharge) et non a la requete. */
function erreurDeModele(status, message) {
  if ([429, 404, 500, 503].includes(status)) return true;
  return /RESOURCE_EXHAUSTED|no longer available|overloaded|UNAVAILABLE|high demand/i.test(message || "");
}

async function checkRateLimit(db, uid) {
  const ref = db.collection("rate_limits").doc(`rolly_tts_${uid}`);
  const now = Date.now();
  const windowMs = 24 * 60 * 60 * 1000;
  const snap = await ref.get();
  if (!snap.exists) {
    await ref.set({ windowStart: now, count: 1 });
    return { ok: true, remaining: MAX_REQUESTS_PER_DAY - 1 };
  }
  const data = snap.data();
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

/**
 * Retrouve l'audio dans la reponse.
 *
 * Le champ documente est `output_audio`. Selon les versions il arrive soit
 * comme une chaine base64 directe, soit comme un objet portant `data` ou
 * `audio_data`, soit encore dans une liste de blocs `output`. On accepte les
 * trois plutot que de casser au premier changement de forme cote Google.
 */
function extraireAudio(json) {
  const direct = json.output_audio ?? json.outputAudio;
  if (typeof direct === "string" && direct.length > 0) return direct;
  if (direct && typeof direct === "object") {
    const d = direct.data || direct.audio_data || direct.audioData || direct.b64_json;
    if (typeof d === "string" && d.length > 0) return d;
  }
  const blocs = json.output || json.outputs || json.content || [];
  for (const bloc of Array.isArray(blocs) ? blocs : [blocs]) {
    if (!bloc || typeof bloc !== "object") continue;
    const candidat =
      bloc.audio?.data || bloc.audio_data || bloc.data ||
      bloc.inline_data?.data || bloc.inlineData?.data;
    if (typeof candidat === "string" && candidat.length > 0) return candidat;
  }
  return null;
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

  const { text = "", voice = DEFAULT_VOICE, style = "" } = req.body || {};

  if (typeof text !== "string" || text.trim().length === 0) {
    return res.status(400).json({ error: "missing_text" });
  }
  if (text.length > MAX_TEXT_LENGTH) {
    return res.status(400).json({ error: "text_too_long", max: MAX_TEXT_LENGTH });
  }
  const voixChoisie = VOICES_AUTORISEES.has(voice) ? voice : DEFAULT_VOICE;

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
    console.warn("tts_rate_limit_check_failed:", e.message);
  }

  // La consigne de ton voyage avec le texte : c'est ainsi que ces modeles
  // prennent une intention (chaleur, lenteur) sans champ dedie.
  const consigne = style && style.trim().length > 0
    ? style.trim()
    : "Lis ce message d'un ton calme, chaleureux et pose, comme un soignant qui s'adresse a son patient";
  const input = `${consigne} : ${text.trim()}`;

  let derniereErreur = null;
  for (const model of MODEL_CHAIN) {
    try {
      const reponse = await fetch(ENDPOINT, {
        method: "POST",
        headers: {
          "x-goog-api-key": apiKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model,
          input,
          response_format: { type: "audio" },
          generation_config: { speech_config: [{ voice: voixChoisie }] },
        }),
      });

      const json = await reponse.json().catch(() => ({}));

      if (!reponse.ok) {
        const message = json?.error?.message || `HTTP ${reponse.status}`;
        derniereErreur = message;
        console.error(`rolly-tts erreur (${model}):`, message);
        if (!erreurDeModele(reponse.status, message)) break;
        continue;
      }

      const audioBase64 = extraireAudio(json);
      if (!audioBase64) {
        derniereErreur = "reponse sans audio";
        console.error(`rolly-tts (${model}): reponse sans audio`, Object.keys(json));
        continue;
      }

      console.log(`rolly-tts model=${model} voice=${voixChoisie} chars=${text.length}`);
      res.setHeader("X-Rolly-Tts-Model", model);
      return res.status(200).json({
        audioBase64,
        mimeType: "audio/L16",
        sampleRate: SAMPLE_RATE,
        model,
        voice: voixChoisie,
      });
    } catch (e) {
      derniereErreur = e.message;
      console.error(`rolly-tts exception (${model}):`, e.message);
    }
  }

  // Le client sait quoi faire de cet echec : repasser au TextToSpeech local.
  return res.status(502).json({
    error: "tts_failed",
    message: derniereErreur || "Synthese vocale indisponible.",
  });
};
