# Construction automatique de l'APK (GitHub Actions)

Le workflow `.github/workflows/build-release-apk.yml` construit l'APK release
signe a chaque push. On le recupere dans l'onglet **Actions** du depot :
ouvrir la derniere execution "APK release signe", puis telecharger le fichier
dans la section **Artifacts** (garde 30 jours).

L'APK n'est **jamais publie** automatiquement : pas de GitHub Release, pas de
copie dans `website/`, pas d'ecriture dans Firestore `app_config/latest_version`.
Les telephones des utilisateurs ne le recoivent donc pas par la mise a jour
automatique (`AppUpdateChecker.kt` ne suit que `app_config/latest_version`).

## Secrets a renseigner

Depot GitHub > **Settings** > **Secrets and variables** > **Actions** >
**New repository secret**. Un secret par ligne :

| Nom | Valeur |
|---|---|
| `KEYSTORE_BASE64` | le fichier `.jks` encode en base64 (une seule ligne) |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `diasmart` |
| `KEY_PASSWORD` | mot de passe de la cle (le meme que le keystore, format PKCS12) |
| `GOOGLE_SERVICES_JSON` | tout le contenu de `google-services.json` (console Firebase > Parametres du projet > appli Android `com.diabeto`) |
| `TURN_USERNAME` | identifiant TURN Metered.ca (optionnel) |
| `TURN_PASSWORD` | mot de passe TURN Metered.ca (optionnel) |

Ne jamais mettre le `.jks`, les mots de passe ou `google-services.json` dans le
depot : il est public.

Sans `KEYSTORE_BASE64` ou `GOOGLE_SERVICES_JSON` (par exemple une PR venant
d'un fork), le workflow fait une construction de **test** avec une cle jetable
et un faux `google-services.json` : il verifie seulement que le code compile
et ne depose aucun APK.

## Nouvelle cle de signature (octobre 2026)

L'ancienne cle `diasmart-release.jks` a ete perdue. La nouvelle cle a ete
creee le 2026-10-03 (RSA 4096, valable jusqu'en 2059, alias `diasmart`).
Empreintes publiques du certificat (a declarer dans la console Firebase >
Parametres du projet > appli Android > "Ajouter une empreinte", sinon la
connexion Google ne marche pas avec les APK signes par cette cle) :

- SHA-1 : `5A:B0:32:BE:CF:6A:32:FF:73:26:0F:70:0E:EF:2D:B4:0F:A8:0B:11`
- SHA-256 : `73:07:93:DE:21:72:13:DD:BC:D4:C5:37:95:5B:12:D6:F6:2B:C8:9B:87:CB:AB:38:8E:9D:F0:0E:CC:AD:28:6B`

Un APK signe avec cette nouvelle cle **ne s'installe pas par-dessus** une
version signee avec l'ancienne : chaque utilisateur doit desinstaller puis
reinstaller une fois (ses donnees Firebase sont conservees, ses donnees
locales non synchronisees sont perdues). Le plan de migration doit etre decide
avant de toucher a `app_config/latest_version`.

## Construire en local

Le depot n'a pas de `gradlew` Unix ni de `gradle-wrapper.jar`. En local, il
faut Gradle 9.2.1, un `local.properties` (cles `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`, `TURN_USERNAME`, `TURN_PASSWORD`), le keystore
copie en `diasmart-release.jks` a la racine, et `app/google-services.json`.
