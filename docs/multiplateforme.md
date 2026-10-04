# DiaSmart multiplateforme (Kotlin Multiplatform)

But : une seule base de code Kotlin pour Android, PC (Windows/Mac/Linux) puis iPhone.

## Etapes
1. **Module commun `shared/`** (fait) : la logique en Kotlin pur, utilisee par l'app Android.
2. Version PC (Compose Desktop) qui reutilise `shared/`.
3. Version iPhone (Compose Multiplatform iOS), quand le compte Apple developpeur est pris.

## Regles du module `shared/`
- `shared/src/commonMain` : Kotlin pur. Pas d'import `android.*`, `androidx.*`, `java.*`
  ni Firebase Android. Les maths passent par `kotlin.math`.
- Les classes gardent leur package d'origine (`com.diabeto...`) : l'app Android
  n'a pas eu a changer ses imports.
- Tests : `shared/src/commonTest`, lances par la CI (`gradle :shared:desktopTest`).

## Deja dans `shared/`
- `domain/ReglesGlycemie` : seuils glycemiques, formule ADAG HbA1c <-> glycemie moyenne,
  interpretation de l'HbA1c.
- `domain/prediction/` : prediction glycemique et conseils.
- `util/UrgencyDetector` : detection des messages d'urgence.
- `data/model/Etablissement` : modeles de l'espace etablissement (sauf `SuiviPatient`,
  qui utilise encore `java.time`).
- `data/entity/HbA1cInterpretation`.

## Prochaines pieces a deplacer
- Dates : passer de `java.time` a `kotlinx-datetime`, puis deplacer `SuiviPatient` et
  les regles de priorite (`EtablissementRepository.evaluer`).
- Firebase : `dev.gitlive:firebase-*` (Firestore/Auth multiplateforme).
- Base locale : Room 2.7+ (multiplateforme). Injection : Koin a la place de Hilt.
- Restent propres a chaque appareil : alarmes, notifications, podometre, batterie, appels.
