# Tier 2 — Backlog technique DiaSmart (mise a jour 2026-05-22)

Travaux qui demandent un sprint dedie (1-2 semaines chacun). A planifier dans l'ordre de priorite ci-dessous.

> v2.1.37 (proxy ROLLY), v2.1.38 (notifs FCM), v2.1.39 (deep-link + Mes avis), v2.1.40 (PWA parite + index Firestore), v2.1.41 (UX dashboard race + recipient visibility + real-time recipients) sont shipped.

---

## A. Tests unitaires logique medicale (PRIORITE 1)

**Pourquoi** : aucune couverture de tests sur les calculs medicaux critiques. Une regression silencieuse peut :
- Mal evaluer une HbA1c estimee → mauvais conseil au patient
- Rater une urgence (UrgencyDetector) → patient non alerte
- Calculer un IMC errone → mauvaise stratification du risque
- Mal categoriser une glycemie → couleur/alerte fausses

**Cibles** :
- `GlucoseRepository.estimateHbA1c()` — formule ADAG : `eAG = 28.7 × HbA1c − 46.7`
- `GlucoseRepository.getGlucoseStatus()` + `getGlucoseColor()` — seuils 70/130/180/250/300/54
- `PatientEntity.imc`, `categorieImc`, `risqueTourDeTaille`
- `CloudBackupRepository.mapToGlucose` / `mapToRepas` — converters Firestore ↔ Room
- `UrgencyDetector.detectUrgency()` + `detectWarning()` — keywords + heuristiques

**Outils** :
- JUnit 5 (`testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")`)
- MockK (`testImplementation("io.mockk:mockk:1.13.7")`)
- Turbine pour les Flow (`testImplementation("app.cash.turbine:turbine:1.0.0")`)
- Robolectric pour Android-bound logic

**Setup** : creer `app/src/test/java/com/diabeto/` + classes par repository.

**Cible coverage** : 60% sur la logique medicale.

**Effort** : 1 semaine pour 30%, 2 semaines pour 60%.

---

## B. BatchSyncWorker incremental (PRIORITE 2)

**Probleme actuel** : `BatchSyncWorker` est en O(P × N) — pour chaque patient (P), il itere sur toutes ses lectures (N). Toutes les 4h, full resync :
- 1000 patients × 100 lectures = 100k reads + 100k writes
- A 10 000 patients : 10M operations / sync = explose le quota Spark (50k reads/jour)
- Cout estime Blaze : ~30 $/mois a 10k patients

**Solution delta-only** :
- Stocker `lastSyncAt` par utilisateur dans DataStore
- Filtrer en SQL : `WHERE lastModified > lastSyncAt`
- WriteBatch Firestore par paquets de 500 (limite API)
- Aligner periode a 6h (vs 4h actuel)

**Code a modifier** : `app/src/main/java/com/diabeto/sync/BatchSyncWorker.kt`

**Effort** : 5-7 jours.

---

## C. Monitoring en production (PRIORITE 3)

**Probleme** : on ne sait pas si l'app crashe chez les utilisateurs. Crashlytics est configure mais peu exploite.

**Actions** :
1. **Crashlytics custom keys** sur chaque ecran critique
2. **Logs non-fatals** sur les chemins critiques (`recordException(e)`)
3. **Firebase Performance Monitoring** : activer le SDK + traces sur cold start, dashboard, generation PDF, stream ROLLY
4. **Alerting Crashlytics** : configurer dans la console pour ping Slack/email quand crash > 0.5%
5. **Logs structures Vercel** : JSON-formatted dans `/api/*`

**Effort** : 3-4 jours.

---

## D. Tests E2E (PRIORITE 4)

**Options** :
- **Maestro** (recommande) : YAML-based, multi-plateforme, gratuit
- **Espresso** : natif Android, plus verbeux

**Scenarios prioritaires** :
1. Login email + OTP → Dashboard charge
2. Ajout d'une glycemie → apparait + chart
3. Chat ROLLY → reponse recue
4. Generation + envoi d'un rapport PDF → arrive cote medecin
5. Mode hors-ligne → saisie locale → re-sync

**Effort** : 1 semaine pour 5 scenarios.

---

## E. i18n complet FR/EN/AR/PCM (PRIORITE 5)

**Etat actuel** :
- `values/strings.xml` : 206 strings (FR)
- `values-en/strings.xml` : **95 strings (incomplet)**
- `values-ar/strings.xml` : **95 strings (incomplet)**
- Pidgin Camerounais (PCM) : **non commence**

**Probleme utilisateur** : bascule EN ou AR → ~50% de l'app reste en FR. Frustrant.

**Actions** :
1. Auditer les 218 strings hardcodees encore en dur
2. Extraire vers `values/strings.xml`
3. Traduire EN/AR/PCM
4. Selecteur de langue dans Settings
5. Tester la bascule a chaud

**Effort** : 2-3 semaines (traduction incluse).

---

## F. RGPD-compliant account deletion (PRIORITE 6)

**Probleme** : `DELETE_ACCOUNT` actuel efface Firebase Auth mais laisse Firestore + Supabase + FCM tokens intacts.

**Solution** : endpoint Vercel `/api/delete-account` qui supprime en cascade :
- Toutes les collections Firestore ou `userId == uid`
- Tous les blobs Supabase ou `path` commence par `reports/{uid}/` ou `profile_photos/{uid}/`
- FCM tokens
- Auth account (en DERNIER)

**Effort** : 3-5 jours.

---

## G. Migrations Room v1→v5 manquantes (PRIORITE 7)

**Probleme** : seules migrations `6→7`, `7→8`, `8→9` definies. Upgrade depuis v1-v5 = perte de donnees patient.

**Action** : migrations no-op pour v1-v5 + suppression `fallbackToDestructiveMigrationOnDowngrade()` + tests `MigrationTestHelper`.

**Effort** : 3-5 jours.

---

## H. Refactor 5 ViewModels qui bypassent les Repositories

`RendezVousViewModel`, `ProfileSyncViewModel`, `CommunityViewModel`, `MessagerieViewModel`, `PatientViewModel` accedent directement a Firestore.

**Action** : creer repos partages, faire passer tous les acces via les repos.

**Effort** : 3-4 jours.

---

## I. Onboarding contextuel + mode Famille (PRIORITE — UX v2.1.42)

- Onboarding bulles contextuelles au 1er usage de chaque ecran principal (Dashboard, Glucose, ROLLY, Messagerie, Reports)
- Library : `com.takusemba:spotlight` ou custom Compose overlay
- Mode famille V1 :
  - 1 patient + 1 aidant (conjoint / enfant / proche)
  - L'aidant voit les glycemies + recoit alertes urgence
  - Modele freemium : 1 aidant gratuit, 3+ aidants = premium

**Effort** : 1 semaine pour onboarding, 1 semaine pour mode famille.

---

## J. Empty states + CTAs (PRIORITE — UX)

Quand une liste est vide, afficher une CTA pertinente ("Ajouter votre premier RDV"). Pendant chargement : skeleton shimmer. En erreur : banniere persistante + bouton "Reessayer".

**Effort** : 1 semaine.

> v2.1.41 a fixe le 1er cas critique (recipient section dans ReportsScreen). Reste a propager le pattern aux autres ecrans (RDV, Medicaments, Journal, Repas, Messages).

---

## Hors code

### CNPDCP Cameroun (declaration donnees de sante)

Voir `docs/cnpdcp-guide.md`. Obligatoire legal pour stocker les donnees de sante de patients camerounais. Non bloquant pour la R&D mais obligatoire avant V1 grand public payant.

### Comite scientifique medical

Voir investment_review_2026_05.md (memoire). 2-3 endocrinos + 1 nutritionniste, charte signee, revue prompts ROLLY trimestrielle. Obligatoire avant V1 payant.

---

## Verdict scaling

- **1 000 patients** : OK avec v2.1.41+
- **5 000 patients** : ajouter A, B, C, F (tests, sync, monitoring, RGPD delete)
- **10 000 patients** : tout le Tier 2 + alerting cout Firestore
