# DiaSmart v2.1.42 — Tests medicaux + Monitoring + Onboarding

Premier ship du Tier 2 backlog. 3 livrables concrets :

## 🧪 (A) 93 tests unitaires sur la logique medicale critique

Premier filet de securite contre les regressions sur les calculs medicaux. **0 failure, 93 passing.**

### Repartition

| Fichier | Tests | Couverture |
|---|---|---|
| `UrgencyDetectorTest` | 37 | FR/Pidgin/Ewondo/Duala/Fulfulde, normalisation accents, faux positifs, warnings, reponses formatees |
| `HbA1cFormulaTest` | 17 | Formule ADAG aller-retour (5/6/7/8/10 %), interpretation clinique 6 niveaux, edge cases |
| `PatientEntityTest` | 23 | IMC + 6 categories OMS + frontieres exactes, tour de taille IDF (homme/femme), edge cases |
| `GlucoseStatusTest` | 16 | Seuils 54/70/180/250 mg/dL, frontieres exactes, sweep complet 30-500 |

### Bugs detectes par les tests (Tier 2 backlog)

3 phrasings naturels que l'UrgencyDetector MANQUE actuellement :
- « Ma vision **est** floue » (keyword exige adjacence « vision floue »)
- « J'ai **vomi** 3 fois » (keyword « vomis » trop long)
- « Mon haleine **sent le** fruit » (keyword exige adjacence)

A ameliorer en v2.1.43 : enrichir les keywords avec fuzzy matching ou regex (`\bvomi\w*\b`, etc.).

### Comment lancer les tests

```bash
./gradlew.bat :app:testDebugUnitTest
```

Resultats dans `app/build/test-results/testDebugUnitTest/`. Pas de dependance Firebase ni Android — tests purs JVM, rapides (~5 sec).

## 📊 (C) Monitoring Crashlytics — CrashlyticsLogger helper

Nouveau singleton `CrashlyticsLogger` centralise tous les acces a Firebase Crashlytics avec :

- `setScreen(name)` — pose un breadcrumb sur chaque ecran majeur
- `setCustomKey(k, v)` — surcharges String/Int/Boolean
- `setUserId(uid)` / `setUserRole(role)` — pour filtrer les crashs
- `log(message)` — breadcrumb arbitraire
- `logException(throwable, screen, action, metadata)` — exception non-fatale (apparait dans Crashlytics sans arreter l'app)
- `setCollectionEnabled(boolean)` — pour desactiver en sessions de dev

**Wiring initial** dans `MainActivity.onCreate` :
- `setUserId(uid)` + log breadcrumb `app_start`
- `setCustomKey("app_version", VERSION_NAME)` + `app_versionCode`

**Pour propager** dans les autres ViewModels/Screens (sprints futurs) :
- `LaunchedEffect(Unit) { CrashlyticsLogger.setScreen("GlucoseTracking") }`
- `try { ... } catch (e) { CrashlyticsLogger.logException(e, screen = "...", action = "...") }`

### A configurer cote Console Firebase
1. **Dashboard alerts** : Project Settings → Integrations → Slack/Email pour ping quand crash > 0.5%
2. **Velocity alerts** : nouvelle issue critique
3. **Custom dashboards** : grouper par `role` (PATIENT vs MEDECIN), par `screen`, par `app_version`

Pas de SDK supplementaire — Firebase Crashlytics etait deja dans `build.gradle.kts`, juste sous-exploite.

## 🎓 (D-light) Onboarding 1ere ouverture du Dashboard

Tutoriel 4 pages (patient) / 3 pages (medecin) qui apparait **une seule fois** apres le 1er login. Vu/skipped → ne reapparait jamais (sauf reinstall).

### Contenu Patient (4 ecrans)
1. **Suis ta glycemie** — MonitorHeart icon, conseil 4-6 mesures/jour
2. **Parle a ROLLY** — Chat icon, mention urgence + langues locales (Pidgin, Ewondo, Duala, Fulfulde, Arabe)
3. **Partage avec ton medecin** — Share icon, controle revocable
4. **Rejoins la communaute** — Groups icon, anonymat possible

### Contenu Medecin (3 ecrans)
1. **Tes patients en un coup d'oeil** — comment activer le partage
2. **Genere ordonnances et comptes-rendus** — workflow PDF + canaux
3. **Teleconsultation integree** — WebRTC natif

### Implementation
- `app/.../ui/components/OnboardingDashboardOverlay.kt` (nouveau) — Compose HorizontalPager avec dots + skip + suivant + final CTA
- `PreferencesRepository.onboardingDashboardSeen` (Flow) + `markOnboardingDashboardSeen()`
- `DashboardViewModel` : `observeOnboardingState()` + `markOnboardingSeen()` + `showOnboarding` dans le state
- `DashboardScreen` : early-return de l'overlay quand `roleLoaded && showOnboarding`

### Pour reset l'onboarding en dev
```kotlin
preferencesRepository.dataStore.edit { it.remove(Keys.ONBOARDING_DASHBOARD_SEEN) }
```

Ou simplement reinstaller l'app.

## ⏭️ Encore en backlog Tier 2

- **B** : BatchSyncWorker delta-only (P2, 5-7 jours)
- **D etendu** : bulles contextuelles sur chaque ecran majeur (vs juste Dashboard) (P3)
- **E** : mode famille V1 (P4, 1 semaine)
- **I** : Tests E2E Maestro (P5, 1 semaine)
- **J** : i18n complet (P5, 2-3 semaines)
- **F** : RGPD cascade delete (P6, 3-5 jours)
- **G** : Migrations Room v1-v5 (P7, 3-5 jours)
- **H** : Refactor 5 ViewModels qui bypassent les Repos (P8)

## 📦 Empreinte

- APK : 58 Mo (inchange)
- Aucune nouvelle permission
- Aucun nouvel endpoint serveur
- Tests JVM purs (pas androidTest) — rapides + pas de device requis
