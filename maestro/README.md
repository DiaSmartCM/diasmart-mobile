# DiaSmart — Tests E2E Maestro

5 scenarios de test end-to-end pour DiaSmart, ecrits en Maestro YAML.

## Installation Maestro CLI

### macOS / Linux
```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

### Windows
```powershell
# Via Chocolatey (recommande)
choco install maestro

# OU via Scoop
scoop install maestro

# OU via npm (cross-plateforme)
npm install -g @mobile.dev/maestro-cli
```

Verifier :
```bash
maestro --version
```

## Pre-requis

1. **Device ou emulator Android branche** (`adb devices` doit lister au moins 1 entree)
2. **APK DiaSmart v2.1.49+ installe** (versions anterieures n'ont pas l'i18n complet ni les tests-friendly content descriptions)
3. **Compte test** avec :
   - Email valide
   - OTP reproductible (un compte avec un code de test fixe — sinon Maestro doit lire l'OTP)
4. **Au moins 1 medecin lie** pour le test 04 (data_sharing actif)

## Variables d'environnement

```bash
export MAESTRO_EMAIL=test@diasmart.cm
export MAESTRO_OTP=123456  # OU configure Firebase test phone numbers
```

## Lancer un seul test

```bash
maestro test maestro/tests/01_login_dashboard.yaml
```

## Lancer toute la suite (5 tests)

```bash
maestro test maestro/tests/
```

## Lancer dans Maestro Cloud (Mobile Dev)

Service hosted SaaS, gratuit jusqu'a 100 runs/mois :
```bash
maestro cloud --apiKey YOUR_API_KEY maestro/tests/ ./app/build/outputs/apk/release/app-release.apk
```

## Les 5 scenarios

| # | Fichier | Scope | Duree estimee |
|---|---|---|---|
| 1 | `01_login_dashboard.yaml` | Login email + OTP → Dashboard charge | 30-60s |
| 2 | `02_add_glucose.yaml` | Ajout glycemie via dialog | 15-30s |
| 3 | `03_rolly_chat.yaml` | Pose question a ROLLY, reponse non vide | 30-60s (Gemini latency) |
| 4 | `04_report_generation.yaml` | Generation + envoi rapport PDF | 60-90s |
| 5 | `05_offline_sync.yaml` | Saisie hors-ligne + re-sync | 30s + manuel reseau toggle |

## Adapter selon ta langue d'app

Les YAML utilisent des **regex multi-langues** sur les `text:` (ex `"Glycémie|Blood Sugar|Sukre i makia"`). Si tu deploies en pidgin/duala/bassa/fulfulde, les tests passent quand meme grace au pattern OR.

Pour forcer une langue avant un test : `adb shell am start ... --es lang fr` ou Settings > Langue > FR avant.

## Debug

```bash
maestro test --debug-output maestro-debug/ maestro/tests/01_login_dashboard.yaml
```

Genere screenshots a chaque etape + logs detailles dans `maestro-debug/`.

## CI / GitHub Actions

Exemple workflow dans `.github/workflows/maestro.yml` (a creer) :
```yaml
name: E2E Maestro
on: [pull_request]
jobs:
  e2e:
    runs-on: macos-latest  # Linux fonctionne aussi
    steps:
      - uses: actions/checkout@v4
      - uses: mobile-dev-inc/action-maestro-cloud@v1
        with:
          api-key: ${{ secrets.MAESTRO_CLOUD_API_KEY }}
          app-file: app-release.apk
          android-api-level: 33
```

## Limitations connues

- Test 04 (rapport PDF) necessite Gemini Vercel proxy en ligne — pas testable totalement offline
- Test 05 (offline sync) requires manual airplane mode toggle ou ADB
- Les tests assume `clearState: true` au demarrage du test 1, puis `false` pour les suivants (chaining session)
- Les OTP Firebase reels ne sont pas mockables sans Firebase Test Lab integration
