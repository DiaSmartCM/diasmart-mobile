# DiaSmart v2.1.49 — Voice mode + i18n 7 langues + lexique ROLLY enrichi

Tier 2 item **J** demarre + bonus voice mode.

## 🎙️ Voice mode ROLLY (TTS local Android, gratuit)

### Texte-vers-parole sur les reponses ROLLY
Chaque bulle de reponse de ROLLY a maintenant un petit bouton **« 🔊 Ecouter »** (devient « ⏹ Stop » pendant la lecture).

- **100% local** : utilise `TextToSpeech` Android natif. Pas d'API cloud.
- **Gratuit** : zero cout, compatible Firebase Spark.
- **Langues supportees TTS** : FR (par defaut), EN, AR. Pour les autres (Pidgin, Duala, Bassa, Fulfulde), fallback FR.
- **Nettoyage avant lecture** : retire markdown (asterisques, headers, emojis) pour eviter "asterisque asterisque gras asterisque asterisque".

### Saisie vocale (deja en place)
Le bouton micro existant dans la zone de saisie utilise `SpeechRecognizer` (deja `RECORD_AUDIO` declaree). Pas de change.

### Fichiers
- `app/.../util/VoiceManager.kt` (nouveau) — singleton TTS + STT helpers
- `app/.../ui/screens/ChatbotScreen.kt` — bouton Ecouter sur bulles ROLLY

## 🌍 i18n — passage de 3 a 7 langues

### Langues supportees

| Code | Langue | Statut |
|---|---|---|
| `fr` | Francais | ✅ complet (206 strings) |
| `en` | English | 🟠 partiel (95/206) |
| `ar` | العربية | 🟠 partiel (95/206) |
| `pcm` | **Pidgin Camerounais (Kamtok)** | 🟡 stub initial (12 strings) |
| `dua` | **Duala** | 🟡 stub initial (~10 strings) |
| `bas` | **Bassa** | 🟡 stub initial (~10 strings) |
| `ful` | **Fulfulde Camerounais** | 🟡 stub initial (~10 strings) |

Les strings non-traduites tombent automatiquement sur `values/` (FR) par fallback Android.

### Mecanisme de bascule (fix critique)
**Avant** : la preference de langue etait stockee dans DataStore mais **jamais appliquee** au runtime → bouton inutile.

**Maintenant** :
- `SettingsViewModel.setLanguage()` appelle `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))` apres la sauvegarde DataStore
- Android recree automatiquement toutes les Activity dans la nouvelle langue
- Per-app language Android 13+ via `android:localeConfig="@xml/locales_config"` dans le manifest
- Selecteur Settings montre maintenant les 7 langues

### Nouveaux fichiers
- `app/.../res/values-b+pcm/strings.xml` — Pidgin Camerounais
- `app/.../res/values-b+dua/strings.xml` — Duala
- `app/.../res/values-b+bas/strings.xml` — Bassa
- `app/.../res/values-b+ful/strings.xml` — Fulfulde
- `app/.../res/xml/locales_config.xml` — declaration locales pour Android 13+
- Dependance ajoutee : `androidx.appcompat:appcompat:1.7.0`

## 🗣️ Lexique ROLLY enrichi (Vercel deploye)

Le system prompt `ROLLY_PRIMARY_PROMPT` dans `website/api/_rolly-prompts.js` a maintenant un **glossaire medical par langue locale** :

- **Pidgin** : sugar sickness, sugar level, sugar dey high/low, eat small small, fit go hospital sharp sharp, etc.
- **Duala** : sukoli o makila, musima, mauti, na bwele, na maha, etc.
- **Bassa** : sukre i makia, ngen, bika be, nlema, hola, ndap likalo, etc.
- **Fulfulde** : nyaw'el ngarwol, suukre ƴiiƴam, naawki, lekki, mi yahi, noddu jaha jaha, etc.
- **Ewondo** : amalan (sucre), ma kone, mvon, ndo a vakor, etc.

ROLLY garde les termes medicaux techniques en francais/scientifique (insuline, HbA1c, mg/dL) mais utilise le vocabulaire courant local pour le ressenti patient (fatigue, douleur, urgence).

## ⏭️ Reste a faire (prochains ships)

### Quand les dictionnaires Drive arrivent
Le user a 4 dictionnaires (Bassa, Duala, Fulfulde + grammaire Bassa) sur son Google Drive mais non syncs localement. Une fois telecharges, je peux enrichir massivement :

| Ship | Contenu |
|---|---|
| v2.1.50 | EN + AR completion 95 → 206 (123 traductions chaque) |
| v2.1.51 | Pidgin 12 → 206 strings (avec dictionnaire) |
| v2.1.52 | Duala 10 → 206 strings (dico Duala) |
| v2.1.53 | Bassa 10 → 206 strings (dico Bassa + grammaire) |
| v2.1.54 | Fulfulde 10 → 206 strings (dico Fulfulde) |

### Voice mode evolutions
- Auto-play optionnel (ROLLY lit sa reponse des reception) — toggle Settings
- Voix arabe avec vraies voix natives (necessite installation pack TTS Arabic OS)
- TTS langues camerounaises : impossible avec Android natif. Alternative seriouse = serveur Coqui TTS open-source, mais necessite du calcul (Vercel functions OK mais lourd). A explorer V2.

## 📦 Empreinte

- APK : 58 Mo
- +1 dependance : `androidx.appcompat:appcompat:1.7.0` (~200 Ko, deja indirect a travers d'autres libs)
- +4 dossiers `res/values-b+xxx/` (4 fichiers ~1 Ko chacun)
- +1 fichier `res/xml/locales_config.xml`
- Aucun nouveau permission
- Aucun endpoint serveur ajoute (juste mise a jour du prompt existant)
