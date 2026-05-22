# DiaSmart v2.1.41 — UX fixes : Dashboard race + Recipient visibility + Real-time recipients

Quatre corrections UX visibles + 2 documents strategiques.

## 🐛 Bug fixes

### (C) Dashboard race condition — fini le flash patient chez les medecins

**Avant** : a l'ouverture de l'app, le medecin voyait brievement le dashboard PATIENT par defaut, puis ca basculait sur le dashboard MEDECIN une fois le role charge depuis Firestore. Confusion + look « casse ».

**Maintenant** : un loader (CircularProgressIndicator) s'affiche pendant que le role se charge. Une fois le role connu (~200-400ms), le BON dashboard apparait directement.

**Fichiers** : `DashboardViewModel.kt` (ajout `roleLoaded: Boolean`), `DashboardScreen.kt` (early-return avec loader si `!roleLoaded`).

### (A) Nom du destinataire MAINTENANT visible dans send report

**Avant** : le champ destinataire etait un `OutlinedTextField` `readOnly` — texte fade gris, difficile a lire, surtout sur petits ecrans.

**Maintenant** : le destinataire selectionne s'affiche dans une **Card prominent** en haut de la section, avec :
- Avatar circulaire (initiale du nom)
- Nom en gros, gras
- Email en sous-titre
- Label « Medecin destinataire » ou « Patient destinataire » en italique en dessous

Si plusieurs destinataires disponibles, un selecteur « Changer (N disponibles) » apparait en-dessous.

**Fichier** : `ReportsScreen.kt` — section `RecipientSection` redesignee.

### (B) Liste destinataires TEMPS REEL (plus de delai de minutes)

**Avant** : `ReportRepository.getLinkedDoctors()` / `getLinkedPatients()` faisaient des `getDocs` one-shot. Quand un medecin etait autorise via DataSharingScreen, il fallait sortir/rentrer dans ReportsScreen pour le voir.

**Maintenant** : deux nouvelles methodes `getLinkedDoctorsFlow()` et `getLinkedPatientsFlow()` utilisent `addSnapshotListener` sur la collection `data_sharing`. La liste se met a jour **instantanement** quand un nouveau medecin/patient est lie.

Bonus : la selection courante est preservee si elle est toujours dans la liste (sinon le 1er est selectionne automatiquement).

**Fichiers** : `ReportRepository.kt` (+2 methodes Flow), `ReportViewModel.kt` (`.collect` au lieu de one-shot).

### (K) Empty state avec CTA actionnable

**Avant** : quand aucun medecin/patient lie, simple texte rouge « Aucun medecin lie. Utilisez l'onglet... »

**Maintenant** : Surface avec :
- Icone PersonOff rouge
- Titre « Aucun medecin lie » en gras
- Message explicatif
- **Bouton « Lier un medecin »** qui navigue directement vers `data_sharing?tab=1`

L'utilisateur n'a plus a chercher l'onglet, le CTA l'amene la-bas en 1 clic.

**Fichiers** : `ReportsScreen.kt` (empty state redesigne + nouveau param `onNavigateToDataSharing`), `Navigation.kt` (wire le callback vers data_sharing tab=1).

## 📋 Documents

### `docs/tier2-followup.md` (mis a jour)

Backlog technique complet avec priorisation :
- A. Tests unitaires logique medicale (P1)
- B. BatchSyncWorker incremental (P2)
- C. Monitoring production (P3)
- D. Tests E2E (P4)
- E. i18n FR/EN/AR/PCM complet (P5)
- F. RGPD-compliant cascade delete (P6)
- G. Migrations Room v1-v5 (P7)
- H. Refactor 5 ViewModels qui bypassent les Repos
- I. Onboarding contextuel + mode Famille (UX v2.1.42)
- J. Empty states + CTAs (UX — partiellement fait en v2.1.41)

### `docs/cnpdcp-guide.md` (NOUVEAU)

Guide complet de declaration aupres de la CNPDCP Cameroun :
- Contexte legal (lois 2010/012 + 2024/017)
- Audit actuel DiaSmart vs CNPDCP (12 criteres)
- Procedure de declaration (documents, modele, depot)
- Designation DPO (NGOS comme auto-DPO acceptable V1)
- Actions techniques prealables (10 items)
- Couts estimes : 800-2000 € minimum
- Plan d'execution 3 mois

**Verdict** : non-conforme actuellement pour V1 payant, conforme pour R&D / beta privee.

## ⏭️ Reporte a v2.1.42 (prochain ship)

- **B etendu** : real-time aussi pour DataSharingScreen, MessagerieScreen list patients
- **D** : onboarding bulles contextuelles au 1er usage
- **E** : mode famille V1 (1 aidant gratuit)
- **J** : i18n complet — completer values-en + values-ar des 95 → 218 strings + ajouter values-pcm

## 📦 Empreinte

- APK : 58 Mo (inchange)
- Aucune nouvelle permission
- Aucun nouveau endpoint serveur — tout cote client + flux Firestore existants
