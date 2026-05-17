# Tier 2 — Travaux reportes (audit complet du 16 mai 2026)

Ces chantiers sortent du scope « patch securite express ». A planifier comme sprints dedies.

## Securite

### 1. Proxy Gemini complet (`/api/rolly-chat`)
**Status** : reporte — la restriction SHA-1 + package dans Google Cloud Console couvre 80 % du risque sans toucher au code.

**Si vraiment necessaire** :
- Nouveau endpoint Vercel `/api/rolly-chat.js` qui prend `{ message, context, history }`, verifie le Firebase ID token, appelle Gemini avec la cle server-side, retourne la reponse.
- Streaming via SSE (`text/event-stream`) — attention timeout Vercel 30s (Hobby) / 60s (Pro).
- `ChatbotRepository.kt` : remplacer `geminiModel.startChat()` par OkHttp + parser SSE.
- Retirer `BuildConfig.GEMINI_API_KEY` de `app/build.gradle.kts`.
- Le `systemInstruction` (200+ lignes dans `FirebaseModule.kt`) migre cote serveur ou en Remote Config.

**Effort estime** : 2-3 jours.

### 2. Activer App Check Play Integrity
Console only — voir release notes v2.1.35.

## Architecture (priorite haute)

### 3. Tests unitaires logique medicale
**Effort** : 1 semaine pour 30 % coverage.

Cibles prioritaires (logique pure, sans Firebase) :
- `GlucoseRepository.estimateHbA1c()` (formule ADAG)
- `GlucoseRepository.getGlucoseStatus()` + `getGlucoseColor()`
- `PatientEntity.imc`, `categorieImc`, `risqueTourDeTaille`
- `CloudBackupRepository` converters `mapToGlucose`/`mapToRepas`
- `UrgencyDetector.detectUrgency()`

Outils : JUnit5 + MockK + Turbine pour Flow.

### 4. Decoupage ChatbotRepository (871 lignes)
Casser en :
- `AiClient` (interface, impl Gemini / proxy)
- `AiCache` (HMAC + Room cache, dedie)
- `PromptBuilder` (avec prompts externalises en assets ou Remote Config)
- `MealAnalyzer` / `GlucoseAnalyzer` / `UrgencyDetector` (deja existant)

**Effort** : 1-2 semaines.

### 5. Migrations Room v1→v6 manquantes
Actuellement seules `6→7`, `7→8`, `8→9` sont definies. Toute upgrade depuis v1-v5 → catch global qui wipe la base + clear passphrase = PERTE DE DONNEES PATIENT.

Action :
- Ajouter migrations no-op pour v1-v5 (meme vides).
- Supprimer `fallbackToDestructiveMigrationOnDowngrade()`.
- Tests `MigrationTestHelper` pour chaque chemin.
- Avant destruction : tentative d'export Firestore.

**Effort** : 3-5 jours.

### 6. BatchSyncWorker incremental
Actuellement O(P × N) sequentiel avec resync complet toutes les heures = explosion couts Firestore.

Action :
- `WriteBatch` par paquets de 500.
- Filtre delta : `lastModified > lastSyncAt` (DataStore).
- Aligner periodicite a 4h (vs 1h actuel).

**Effort** : 1 semaine.

### 7. 5 ViewModels qui accedent direct a Firestore
`RendezVousViewModel`, `ProfileSyncViewModel`, `CommunityViewModel`, `MessagerieViewModel`, `PatientViewModel` court-circuitent leurs repositories. Refactor : creer `RdvSharedRepository`, faire passer tous les acces par les repos.

**Effort** : 3-4 jours.

## UX

### 8. i18n complete (218 strings hardcodees)
- Extraction → `values/strings.xml` (FR par defaut).
- Traduction → `values-en/strings.xml`, `values-ar/strings.xml`, `values-pcm/strings.xml` (pidgin camerounais).
- Locale switcher dans Settings.

**Effort** : 2-3 semaines (traduction inclus).

### 9. Empty states actionnables + skeletons shimmer + erreur sticky
Quand une liste est vide, afficher une CTA pertinente ("Ajouter votre premier RDV"). Pendant chargement : skeleton shimmer (pas spinner). En erreur : banniere persistante en haut + bouton "Reessayer".

**Effort** : 1 semaine.

### 10. Suppression compte RGPD-compliant
Actuellement `DELETE_ACCOUNT` efface Firebase Auth mais laisse Firestore (`/users/{uid}`, `/patients`, `/glucose`, etc.) et Supabase intacts.

Action : Cloud Function (ou endpoint Vercel) qui supprime cascade :
- Toutes les collections Firestore ou `userId == uid`
- Tous les blobs Supabase ou `path` commence par `reports/{uid}/`, `profile_photos/{uid}/`
- FCM tokens
- Auth account (en dernier)

**Effort** : 3-5 jours.

## Couplage

### 11. Interface RemoteDataSource par domaine
Pour permettre migration Firebase → Supabase ou hybride sans toucher tous les repos.

```kotlin
interface UserRemoteDataSource {
    suspend fun get(uid: String): UserProfile?
    suspend fun update(uid: String, fields: Map<String, Any?>)
    fun observe(uid: String): Flow<UserProfile?>
}

class FirestoreUserRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : UserRemoteDataSource { /* ... */ }
```

**Effort** : XL (refactor fondamental, 4-6 semaines).

## Verdict scaling

- **1 000 patients** : v2.1.35 + Tier 1 quick wins (v2.1.36) + restriction Gemini + App Check = OK
- **5 000 patients** : ajouter #3, #5, #6, #10 (tests, migrations Room, sync incremental, suppression RGPD)
- **10 000 patients** : tout le Tier 2 + monitoring (Firebase Performance, alerts cout Firestore)
