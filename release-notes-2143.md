# DiaSmart v2.1.43 — Firestore rules durcies + unlink/relink

**Changement de securite majeur.** Les regles Firestore etaient ouvertes : tout utilisateur authentifie pouvait techniquement modifier les donnees d'un autre. Corrige.

## 🔒 (1) Firestore rules : modele de permission strict

### Avant (faille critique)

```
match /{document=**} {
  allow read, write: if request.auth != null;  // OPEN BAR
}
```

N'importe quel patient/medecin authentifie pouvait **modifier les donnees medicales d'un autre patient** s'il connaissait l'ID du document. Faille majeure.

### Maintenant

**Modele de permission** :
- Le **proprietaire** d'une donnee est identifie via le champ `userId` ou via l'ID du doc (`users/{userId}`).
- **Ecriture** : seulement le proprietaire. Personne d'autre.
- **Lecture** : proprietaire OU **medecin lie via un consent actif** (`data_sharing/{patientUid}_{medecinUid}` avec `isActive: true`).

Concretement :

| Collection | Lecture | Ecriture |
|---|---|---|
| `users/{uid}` | tout authentifie | proprietaire `uid` seulement |
| `users/{uid}/repas` (sous-coll) | proprietaire OU medecin lie | proprietaire |
| `glucose` (top-level) | proprietaire OU medecin lie | proprietaire |
| `repas` (top-level) | proprietaire OU medecin lie | proprietaire |
| `medicaments` | proprietaire OU medecin lie | proprietaire |
| `rendezvous` | proprietaire OU medecin lie OU medecin par medecinUid | proprietaire OU medecin (le medecin peut valider) |
| `journal` | proprietaire OU medecin lie | proprietaire |
| `hba1c_lectures` | proprietaire OU medecin lie | proprietaire |
| `patients` | proprietaire OU medecin lie | proprietaire |
| `conversations` + messages | participants (patientId/medecinId) | participants |
| `data_sharing/{patientUid_medecinUid}` | 2 parties impliquees | 2 parties impliquees |
| `community_messages` | tout authentifie | auteur (`userId == auth.uid`) |
| `doctor_reviews` | tout authentifie | patient auteur (suppression : patient OU medecin concerne) |
| `validations` | medecin OU patient cite | medecin createur |
| `calls/{callId}` | callerUid OU calleeUid | callerUid pour create, parties pour update |
| `fcm_tokens` | `uid == auth.uid` | `uid == auth.uid` |
| `quota/{uid}` | proprietaire | proprietaire |
| `rate_limits` | lecture authentifie | **ecriture serveur uniquement** (Admin SDK) |
| `app_config` | publique | aucune ecriture cote client |
| `reports/{ownerUid}` | proprietaire | proprietaire |
| `backups/{userId}` | proprietaire | proprietaire (max 100 ko) |
| **Catch-all** | **REFUSE** | **REFUSE** |

**Helper rule clef** : `isLinkedDoctorOf(patientUid)` verifie l'existence + `isActive` du doc `data_sharing/{patientUid}_{medecinUid}`. Cout : 1 read supplementaire par requete protegee. Acceptable pour la securite.

### Impact utilisateur visible

- **Doctor cannot edit patient data** : techniquement impossible meme s'il essaye via console
- **Patient cannot edit doctor profile** : techniquement impossible
- **Bidirectionnel temps reel** : les modifications du patient s'affichent chez le medecin (lecture), les modifications du profil medecin s'affichent chez le patient (lecture)
- **Catch-all DENY** : tout endpoint Firestore non liste est refuse par defaut → securise toute future regression

## 🔗 (2) Unlink + relink bidirectionnel

### Nouvelles methodes dans `DataSharingRepository`

```kotlin
suspend fun unlinkAsDoctor(patientUid: String): Result<Unit>
suspend fun reactivateConsent(otherUid: String): Result<Unit>
```

Avant : seul le patient pouvait revoquer (`revokeConsent`). Maintenant le medecin aussi peut se desabonner d'un patient (par exemple s'il quitte la relation therapeutique).

**Reactivation** : si un lien est revoque (isActive=false), n'importe quelle des 2 parties peut le reactiver via `reactivateConsent(otherUid)`. Le doc est reutilise (pas de nouveau doc cree) → traçabilite preservee.

### Champs ajoutes

- `revokedBy: "patient" | "medecin"` — qui a revoque
- `reactivatedAt: Timestamp` — derniere reactivation

## ⏭️ Reste a faire (UI v2.1.44)

- Ajouter un bouton « Se desabonner de ce patient » sur la carte patient cote medecin
- Ajouter un bouton « Reactiver l'acces » dans la liste des liens revoques
- Confirmer par dialog avant unlink (eviter clic accidentel)

Actuellement disponible via API uniquement — les boutons UI viennent au prochain ship.

## 🧪 Tests a faire chez toi

1. **Connecte un compte PATIENT-A** + un compte PATIENT-B
2. Tente de modifier les donnees de B depuis A (via app ou Firebase console) → **doit echouer** avec « permission denied »
3. **Connecte un MEDECIN** + lie avec PATIENT-A via DataSharingScreen
4. Tente d'editer le profil du PATIENT-A depuis le compte MEDECIN → doit echouer
5. **Le PATIENT-A modifie sa glycemie** → le MEDECIN voit la mise a jour instantanement (Firestore listener)
6. **Le MEDECIN modifie son profil** (specialite, photo) → le PATIENT voit la mise a jour instantanement
7. **PATIENT revoque le lien** via DataSharingScreen → le MEDECIN ne voit plus les donnees
8. **MEDECIN ou PATIENT reactive** via `reactivateConsent()` (API pour l'instant, UI au prochain ship) → la liaison reprend

## 📦 Empreinte

- APK : 58 Mo
- Aucune nouvelle permission
- Cote serveur : 1 read supplementaire par requete protegee (verification consent)
- Catch-all DENY : aucune regression possible sur futures collections sans mise a jour explicite des rules
