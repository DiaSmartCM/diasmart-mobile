# DiaSmart v2.1.45 — UI link/unlink + Suppression compte RGPD

Suite de v2.1.44, on finit la chaine de bout en bout.

## 🔗 (UI) Unlink + Reactivate cote medecin

`DataSharingScreen` (vue MEDECIN) a maintenant 2 sections :

### Section "Patients actifs"
- Carte avec avatar (initiales) + nom + badge vert "Acces actif"
- Icone `PersonRemove` a droite → clic → **AlertDialog confirmation** :
  > « Se desabonner ? »  
  > Vous ne verrez plus les donnees de ${nom}. Le patient devra reactiver le partage ou vous pourrez reactiver le lien depuis "Acces revoques". Aucune donnee n'est supprimee.
  
  Bouton rouge "Se desabonner" + "Annuler"

### Section "Acces revoques"
- Apparait uniquement si au moins 1 lien revoque
- Carte grise (visuellement secondaire) avec nom du patient
- Bouton text "Reactiver" → **AlertDialog confirmation** :
  > « Reactiver l'acces ? »  
  > Reactiver le lien avec ${nom} ? Vous pourrez de nouveau consulter ses donnees. Le patient peut a tout moment revoquer ce lien.
  
  Bouton "Reactiver" + "Annuler"

**Wiring** : `DataSharingViewModel.unlinkPatient()` + `reactivateLink()` (deja livres en v2.1.44).

## 🇪🇺 Suppression compte RGPD — cascade complete

### Probleme corrige

L'ancienne `AuthRepository.deleteAccount()` :
```kotlin
// Avant : seuls data_sharing + users + Auth supprimes
// → glucose, repas, medicaments, journal, conversations, doctor_reviews,
//   validations, fcm_tokens, backups, reports... TOUS ORPHELINS
```

Non conforme RGPD/CNPDCP.

### Solution

`AuthRepository.deleteAccount()` appelle maintenant le nouveau endpoint **`POST /api/delete-account`** (livre en v2.1.44) qui cascade :

1. Top-level data (`glucose`, `repas`, `medicaments`, `rendezvous`, `journal`, `hba1c_lectures`, `patients`, `community_messages`)
2. Conversations + messages
3. Data sharing (2 cotes)
4. Doctor reviews (patient + medecin)
5. Validations
6. Calls VoIP
7. FCM tokens
8. User-owned (`users/{uid}`, `rolly_history/{uid}`, `quota/{uid}`, `backups/{uid}`, `reports/{uid}`)
9. Rate limits
10. Firebase Auth (DERNIER)
11. Recu RGPD signe HMAC-SHA256

### UI du dialog (Settings)

L'AlertDialog liste maintenant explicitement TOUT ce qui sera supprime :
- Profil utilisateur
- Toutes vos lectures glycemiques + HbA1c
- Tous vos repas, medicaments, RDV, journal
- Conversations + messages (patient/medecin)
- Avis donnes ou recus
- Liens de partage (patients/medecins)
- Notifications FCM
- Backup cloud + rapports PDF
- Compte Firebase Auth

Plus mention : « Un recu RGPD signe vous sera fourni comme preuve legale. »

## ⏭️ Reste a faire (v2.1.46)

- **Affichage du recu RGPD** apres delete + telechargement en .json
- UI revoke cote PATIENT (le patient peut revoquer un medecin avec confirmation)
- **D etendu** : onboarding bulles sur Glucose, ROLLY, Messagerie
- **E** Mode famille V1
- **H** Refactor 5 ViewModels qui bypassent les Repos
- **I** Tests E2E Maestro
- **J** i18n complet FR/EN/AR/PCM
- Supabase storage cascade dans delete-account

## 📦 Empreinte

- APK : 58 Mo
- Aucune nouvelle dependance
- Aucun nouvel endpoint serveur (utilise celui de v2.1.44)
