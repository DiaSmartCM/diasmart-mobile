# DiaSmart v2.1.56 — Dii (Dourou) + cloture i18n batch

Batch 6/6 livre. Fin de la serie i18n entamee en v2.1.49.

## 🌍 Etat final i18n — 8 langues supportees

| Langue | Code | Couverture | Source |
|---|---|---|---|
| 🇫🇷 Francais | `fr` | **206/206** | langue source |
| 🇬🇧 English | `en` | **206/206** ✅ | manuel |
| 🇸🇦 العربية | `ar` | **206/206** ✅ | manuel MSA |
| Pidgin Cmr (Kamtok) | `pcm` | **206/206** ✅ | Wikipedia + connaissances |
| Bassa (Basaá) | `bas` | 64/206 ⚠️ | Bellnoun Momha (L'Harmattan 2007) |
| Duala | `dua` | 56/206 ⚠️ | Helmlinger (Klincksieck 1972, archive.org) |
| Fulfulde Adamawa | `ful` | 88/206 ⚠️ | Dico medical Maroua/Meskine |
| **Dii / Dourou** | `dii` | **15/206** ⚠️ | Dico 2014 (extraction limitee) |

Pour les langues camerounaises, les keys non traduites tombent automatiquement sur **values/ (FR)** — l'app reste fonctionnelle dans toutes les langues, juste partiellement traduite.

## 🛑 Langues abandonnees

- **Ewondo** : pas de source verifiable trouvee (utilisateur a confirme l'abandon)
- **Tupuri** : aucune source online utilisable (Wikipedia phonologie seulement, Google Books couverture, Glosbe vide)

## 🆕 Cette release

### Dii / Dourou (v2.1.56)
- ~52 000 locuteurs Adamaoua / Nord Cameroun
- Famille Niger-Congo, Adamawa-Ubangi
- Source : Dictionnaire Dii de 2014 (PDF 328 pages)
- **Extraction limitee** : le dico utilise une notation linguistique academique avec diacritiques unicode complexes (tons hauts/bas, implosives 6/0, etc.) qui rend le parsing automatique difficile
- 15 strings traduites (identite, navigation, actions de base, auth, salutation)
- Le reste tombe en fallback FR

### Mises a jour transverses
- `AppLanguage` enum etendu (8 langues)
- `xml/locales_config.xml` declare `dii` pour Android 13+ per-app language
- `SettingsViewModel.setLanguage()` mapping ajoute

## ⚠️ Pour aller plus loin sur les langues camerounaises

**TOUTES les traductions camerounaises sont des MEILLEURS EFFORTS extraites de dictionnaires academiques.** Avant un deploiement grande echelle (V1 payant, partenariat hospitalier), il faut :

1. **Review native speaker** pour chaque langue (Bassa, Duala, Fulfulde, Dii, Pidgin)
2. **Validation medicale** des termes diabete par un soignant qui parle la langue
3. **Test utilisateur** dans la region cible (Adamaoua pour Fulfulde/Dii, Littoral pour Duala/Bassa, NW/SW pour Pidgin)

Ces reviews ameliorant la couverture progressivement (40 → 100 → 150 → 206 strings par langue).

## 📦 Empreinte

- APK : 58 Mo
- +1 dossier `values-b+dii/` (~1 Ko)
- +1 entree dans `locales_config.xml`
- +1 valeur dans enum `AppLanguage`

## 🧪 Test rapide

1. Settings > Choisir la langue > **Dii / Dourou**
2. L'app affiche le nom + quelques navigations en Dii, le reste en FR
3. Settings > Francais pour revenir

Idem pour les autres langues : Bassa, Duala, Fulfulde — la couverture varie de 56 a 88 strings selon la richesse du dico.

## 📊 Synthese effort i18n (v2.1.49 → v2.1.56)

| Effort | Realise |
|---|---|
| Langues ajoutees | +5 (PCM, BAS, DUA, FUL, DII) |
| Strings traduites | ~700 (EN 111 + AR 111 + PCM 206 + BAS 64 + DUA 56 + FUL 88 + DII 15) |
| Verifications passees | 4 langues a 100%, 4 langues partielles |
| Sources consultees | Wikipedia, archive.org, 5 PDFs dictionnaires locaux |
| Ships | 8 versions (v2.1.49 voice+stubs, v2.1.50 EN, v2.1.51 AR, v2.1.52 PCM, v2.1.53 BAS, v2.1.54 DUA, v2.1.55 FUL, v2.1.56 DII) |
