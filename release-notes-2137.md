# DiaSmart v2.1.37 — Proxy ROLLY : la cle Gemini quitte l'APK

## 🔐 Securite (changement majeur)

**`GEMINI_API_KEY` n'est plus dans l'APK.**

Avant : la cle Gemini etait embarquee en `BuildConfig` ; quiconque decompile l'APK peut l'extraire et brûler ton quota / facturer ton compte Google AI.

Maintenant :
- Toutes les requetes ROLLY (chat, analyses, vision, predictions) passent par `/api/rolly-chat` sur Vercel.
- La cle Gemini vit cote serveur (variable d'environnement Vercel `GEMINI_API_KEY`).
- Authentification : Firebase ID token (Bearer header) → seul un utilisateur connecte peut invoquer Rolly.
- Rate limit cote serveur : 200 requetes / 24h / UID (anti-abus).
- Tailles maximales serveur : message 16 ko, image ~4 Mo (anti-DoS).
- Verification APK : `strings app-release.apk | grep -i gemini` → **0 matche**.

## 🏗️ Refactor

- **`website/api/rolly-chat.js`** (nouveau) — endpoint Vercel qui proxy Gemini avec 8 modes (`chat`, `chat_context`, `meal_json`, `meal_image`, `glucose_analysis`, `nutrition_advice`, `risk_prediction`, `predictive_7days`). Streaming SSE pour la conversation.
- **`website/api/_rolly-prompts.js`** (nouveau) — prompts systeme ROLLY centralises serveur (~200 lignes extraites de `FirebaseModule.kt`). Modification d'un prompt → redeploy Vercel, plus besoin de rebuild APK.
- **`app/.../data/repository/RollyChatClient.kt`** (nouveau) — client OkHttp pour le proxy, parser SSE, `sendText()` / `sendJson()` / `streamText()`.
- **`ChatbotRepository.kt`** refactor complet : 11 methodes Gemini migrees vers le proxy. Cache local (HMAC), detection d'urgence, fallback transitoire conserves.
- **`FirebaseModule.kt`** : `provideGeminiModel()` et `provideGeminiFallbackModel()` supprimes.
- **`app/build.gradle.kts`** : `BuildConfig.GEMINI_API_KEY` supprime, SDK `com.google.ai.client.generativeai:generativeai` retire des dependances.

## 📦 Empreinte

- APK release : 58 Mo (legere baisse, SDK Gemini retire).
- Aucune nouvelle permission Android.
- Latence ajoutee : ~50-150 ms (overhead du proxy + Firebase token), invisible pour l'utilisateur grace au streaming.

## 🌐 Compatibilite

- Si Vercel est down → ROLLY affiche un message d'erreur reseau ; les donnees patient (glycemies, repas, journal) continuent de s'enregistrer localement et de se synchroniser quand le service revient.
- Mode hors ligne : message « Pas de connexion internet » (comportement inchange depuis v2.1.31).

## 🔄 Migration

Aucune action utilisateur requise. La premiere ouverture apres mise a jour repointe automatiquement vers le proxy.

## Verdict

La derniere fuite critique de cle API dans l'APK est colmatee. Si quelqu'un decompile l'APK il ne trouvera **rien** d'exploitable cote Gemini.
