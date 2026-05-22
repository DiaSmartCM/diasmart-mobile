# DiaSmart v2.1.44 — Scaling + RGPD + Migrations + Link API

Ship lourd cote infra. 4 chantiers Tier 2 livres.

## ⚡ (B) BatchSyncWorker delta-only

**Probleme** : sync periodique etait O(P × N) — chaque heure on re-uploadait toutes les lectures de tous les patients. Pour 1000 patients × 100 lectures = 100k writes/sync. Sur Spark : quota explose des 5000 patients.

**Solution v2.1.44** :
- Track `lastSyncAt` dans DataStore (epoch ms).
- DAO queries `getXxxModifiedSince(since: Long)` ajoutes a chaque entite (Patient, Glucose, HbA1c, Medicament, RendezVous, Journal).
- `CloudBackupRepository.performIncrementalBackup(since)` : O(delta) au lieu de O(total).
- WriteBatch Firestore par paquets de **500** (limite API).
- Si delta vide → return immediat (0 writes).
- Si delta > 5000 docs → bascule auto sur full backup (premier sync ou retard de plusieurs jours).
- **Periode 1h → 6h** (`ExistingPeriodicWorkPolicy.UPDATE` applique le changement aux installations existantes).

**Impact estime** :
- 1000 patients actifs : ~50 writes/sync au lieu de 100k → **2000× moins**
- Tient sur Spark gratuit jusqu'a ~20k patients actifs (vs ~1k avant)

**Fichiers** : `PreferencesRepository` (+ `lastSyncAt`), 6 DAOs (+ `getXxxModifiedSince`), `CloudBackupRepository` (+ `performIncrementalBackup`), `BatchSyncWorker` (delta-only + 6h).

## 🇪🇺 (F) Suppression compte RGPD-compliant

**Probleme** : l'ancien delete supprimait Firebase Auth mais laissait :
- Firestore : users/{uid}, glucose, repas, medicaments, RDV, journal, conversations, doctor_reviews, validations, data_sharing, community_messages, fcm_tokens, calls...
- Backups + reports
- Rate limits

→ **non-conforme RGPD/CNPDCP** : droit a l'oubli pas respecte.

**Solution** : nouveau endpoint Vercel `POST /api/delete-account` qui cascade tout :

1. Top-level data : `glucose`, `repas`, `medicaments`, `rendezvous`, `journal`, `hba1c_lectures`, `patients`, `community_messages` (where `userId == uid`)
2. Conversations + messages (where `patientId == uid` OR `medecinId == uid`)
3. Data sharing (2 cotes : patientUid + medecinUid)
4. Doctor reviews (cote patient OU cote medecin)
5. Validations (cote medecin OU cote patient)
6. Calls VoIP (caller OU callee)
7. FCM tokens (where `uid == auth.uid`)
8. User-owned top-level docs : `users/{uid}` + sous-coll, `rolly_history/{uid}`, `quota/{uid}`, `backups/{uid}`, `reports/{uid}` + sous-coll
9. Rate limits (rolly_, notify_msg_, notify_comm_)
10. Firebase Auth account (en DERNIER)
11. **Recu RGPD signe** (HMAC-SHA256) renvoye en Base64 — preuve legale

**Auth + securite** :
- Bearer Firebase ID token (uid extrait du token, pas du body — pas d'injection possible)
- Body doit contenir `confirm: "DELETE_<uid>"` exactement — evite les appels accidentels

**Cote client** : `NotificationApi.deleteAccount()` qui prepare le `confirm` automatiquement.

**A faire au prochain ship** : 
- TODO Supabase storage cascade (necessite cle `service_role` cote Vercel)
- TODO UI : ecran "Suppression compte" avec triple-confirm + telechargement recu

## 🔗 (UI prep) Unlink + reactivate API

3 nouvelles methodes dans `DataSharingRepository` + ViewModel handlers prets :

```kotlin
DataSharingRepository.unlinkAsDoctor(patientUid)   // medecin se desabonne
DataSharingRepository.reactivateConsent(otherUid)  // reactiver lien revoque

DataSharingViewModel.unlinkPatient(patientUid)
DataSharingViewModel.reactivateLink(otherUid)
```

**Boutons UI dans DataSharingScreen** : prepares cote VM. Wiring visuel reporte a v2.1.45 (faut etoffer la carte patient cote medecin avec un menu kebab + AlertDialog de confirmation).

## 🗃️ (G) Room migrations v1-v5 (stubs no-op)

**Probleme** : la base Room avait des migrations 6→7, 7→8, 8→9 mais aucune migration v1-v5. Un user avec v2.1.20 ou plus ancien qui mettait a jour vers v2.1.44 → fallback destructive → perte de donnees locale.

**Solution** : 5 stubs no-op ajoutes :
```kotlin
MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
```

Chaque stub fait juste un log et passe. La chaine v1 → v9 est maintenant continue → plus de fallback destructive.

**Filet de securite supplementaire** (deja en place dans `DiabetoDatabase.getInstance`) : si la base est corrompue malgre tout, on tente de la reconstruire + `CloudBackupRepository.performFullRestore()` recupere les donnees depuis Firestore.

## 🧪 Tests

`93 tests, 0 failures` toujours OK (tests unitaires logique medicale inchanges).

## 📦 Empreinte

- APK : 58 Mo (inchange)
- Nouveau endpoint serveur : `/api/delete-account`
- Aucune nouvelle permission Android
- DataStore : +1 cle (`last_sync_at`)
- Room : versions 1-9 toutes couvertes en migration explicite

## ⏭️ Reste a faire (v2.1.45)

- UI unlink/reactivate buttons dans DataSharingScreen + AlertDialog
- Onboarding etendu (autres ecrans : Glucose, ROLLY, Messagerie)
- Mode famille V1
- Refactor 5 ViewModels qui bypassent les Repos
- Tests E2E Maestro
- i18n complet FR/EN/AR/PCM
- Supabase storage cascade dans delete-account
- UI ecran "Suppression compte" avec triple-confirm
