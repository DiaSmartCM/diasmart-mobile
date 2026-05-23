# DiaSmart v2.1.48 — Mode famille V1

Tier 2 item **E** livre. Premiere version d'une feature majeure freemium.

## 👨‍👩‍👧 Concept

Un patient (« owner ») peut inviter un proche (« aidant ») a suivre ses donnees medicales. L'aidant a **acces LECTURE SEULE** — il ne peut RIEN modifier sur les donnees du patient. Aucun risque qu'un proche bien intentionne mais maladroit casse le suivi.

**Modele freemium V1** (Spark gratuit) :
- **1 aidant gratuit** par owner
- Premium V2 (apres seed) : illimite + alertes urgences propagees + tableau de bord aidant

## 🎯 Fonctionnel V1

### Cote OWNER (patient)
1. Settings → section « Mode famille » → ouvre l'ecran FamilyScreen
2. Bouton FAB « Inviter un proche » → dialog email + relation (fils, conjoint...)
3. Verification : l'aidant doit deja avoir un compte DiaSmart (sinon erreur explicite)
4. Carte avec statut : `En attente` / `Actif` / `Revoque`
5. Bouton revoquer + AlertDialog confirmation
6. Verification limite gratuite : refuse l'invitation si deja 1 aidant actif

### Cote AIDANT
1. Settings → Mode famille → section « Patients que j'aide »
2. Voit l'invitation PENDING → boutons « Accepter » / « Refuser »
3. Si accepte : statut « Tu suis ses donnees » + bouton « Se desabonner »
4. Si refuse/revoque : carte grisee + bouton « Reactiver »

## 🔐 Securite Firestore

### Rules deployees

```js
function isFamilyAidantOf(ownerUid) {
  let docId = ownerUid + "_" + request.auth.uid;
  return isSignedIn() &&
         exists(/databases/$(database)/documents/family_links/$(docId)) &&
         get(/databases/$(database)/documents/family_links/$(docId)).data.isActive == true;
}
function canReadPatientData(ownerUid) {
  return isOwner(ownerUid) || isLinkedDoctorOf(ownerUid) || isFamilyAidantOf(ownerUid);
}

// Read sur glucose, repas, medicaments, journal, hba1c_lectures, patients :
allow read: if isResourceOwner() ||
               isLinkedDoctorOf(resource.data.userId) ||
               isFamilyAidantOf(resource.data.userId);

match /family_links/{docId} {
  function isFamilyParty() { ... patientUid OU aidantUid == auth.uid ... }
  allow read: if isFamilyParty();
  allow create: if isSignedIn() && request.resource.data.ownerUid == request.auth.uid;
  allow update: if isFamilyParty();
  allow delete: if false;  // tracabilite preservee
}
```

**1 read supplementaire** par requete protegee (verif consent actif), accepte pour la securite.

### Garanties
- ✅ Aidant peut LIRE glucose, repas, medicaments, journal, hba1c, patients du owner
- ❌ Aidant NE PEUT PAS ecrire/modifier/supprimer
- ❌ Aidant NE PEUT PAS inviter d'autres aidants au nom du owner
- ✅ Owner OU aidant peut revoquer/reactiver le lien

## 🏗️ Implementation

**Nouveaux fichiers** :
- `app/.../data/model/FamilyLink.kt` — model + enum `FamilyLinkStatus` (PENDING/ACCEPTED/REJECTED)
- `app/.../data/repository/FamilyRepository.kt` — invite, accept, reject, revoke, unlink, reactivate, getMyAidantsFlow, getMyOwnersFlow, findUserByEmail
- `app/.../ui/viewmodel/FamilyViewModel.kt` — observe 2 flows + handlers + clearMessages
- `app/.../ui/screens/FamilyScreen.kt` — 2 sections (Mes aidants / Patients que j'aide) + FAB + dialogs

**Modifications** :
- `Navigation.kt` : route `family`
- `SettingsScreen.kt` : nouvelle section « Mode famille » (cote patient seulement)
- `firestore.rules` : helper `isFamilyAidantOf` + reads des collections medicales + rule `family_links/{docId}` + helper combine `canReadPatientData` (preparatif pour le ship V2)

## ⏭️ V2 a venir (apres seed / Blaze upgrade ou alternative)

| Item | Pourquoi |
|---|---|
| **Aidant voit le Dashboard du owner** | Necessite un mode "view-as" qui charge les donnees d'un autre UID. Pas tres complique mais demande de refactor le DashboardViewModel pour accepter un targetUid optionnel. |
| **Notifications urgences propagees** | Quand UrgencyDetector detecte une urgence ou que glycemie < 54 / > 300, FCM push vers tous les aidants actifs avec `canReceiveEmergencyAlerts=true`. Necessite un endpoint `/api/notify-emergency-aidants`. |
| **Invitation par email pur** | Permet d'inviter quelqu'un qui n'a PAS de compte DiaSmart → matching au moment de la creation du compte par l'email. Plus complexe (race condition possible). |
| **Premium : 3+ aidants** | Necessite verification cote rules avec un champ `premiumUntil` dans users/{uid}. Bloque par le modele Spark : pas de cron pour expirer les premium. |

## 📦 Empreinte

- APK : 58 Mo
- Nouvelle collection Firestore : `family_links`
- Rules : +20 lignes, +2 helpers
- Aucun endpoint serveur (tout client-triggered)
- Aucune nouvelle dependance

## 🧪 Tests a faire chez toi

1. **Cree 2 comptes DiaSmart** : patient-A + proche-B (sur 2 telephones distincts)
2. Connecte-toi en patient-A → Settings → Mode famille → invite proche-B par email
3. Verifie que A voit son aidant en « En attente »
4. Connecte-toi en proche-B → Settings → Mode famille → Accepte
5. Verifie que A voit l'aidant en « Actif »
6. **Test acces aidant** : depuis proche-B, fais un `getDocs` sur `glucose where userId == patientA_uid` (via console Firebase ou via app si tu ajoutes la fonctionnalite "voir owner" plus tard) → doit retourner les docs
7. **Test impossibilite ecriture** : depuis proche-B, tente un `set()` sur `glucose/{docId}` du patientA → doit echouer (permission denied)
8. **Test limite gratuit** : depuis patient-A, invite un 2eme aidant → erreur « Limite atteinte »
9. **Test revocation** : patient-A revoque l'aidant → proche-B ne voit plus rien (acces Firestore refuse)
10. **Test reactivation** : proche-B reactive depuis sa liste « Patients que j'aide » → relance le suivi
