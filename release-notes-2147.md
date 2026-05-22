# DiaSmart v2.1.47 — Refactor architectural : 5 ViewModels via Repositories

Tier 2 item **H** livre. Aucun changement UI visible — c'est de l'architecture interne pour preparer les futures evolutions.

## 🏗️ Probleme corrige

5 ViewModels accedaient directement a `FirebaseFirestore.getInstance()` au lieu de passer par des repositories :

| VM | Calls Firestore directs | Operations |
|---|---|---|
| CommunityViewModel | 6 | listener community_messages + count users PATIENT + add message |
| RendezVousViewModel | 7 | rdv_shared CRUD + users where role=MEDECIN |
| ProfileSyncViewModel | 3 | users/{uid}.set merge (geolocalisation) |
| PatientViewModel | 3 | users/{uid}.set merge (morpho) |
| MessagerieViewModel | 1 | conversations/{id}.get (lookup interlocuteur) |

**Consequences** :
- Code Firestore duplique entre VM et repos
- Impossible de migrer vers un autre backend sans refactor massif
- Tests difficiles (les VMs ne peuvent etre mockes correctement)
- Logique metier dispersee dans la couche presentation

## ✅ Solution

### Nouveaux repositories
- **`CommunityRepository`** : encapsule `community_messages` (observe + post + count members)
- **`RdvSharedRepository`** : encapsule `rdv_shared/{patientUid}/rendezvous/{rdvId}` (get/set/update/delete) + `users where role=MEDECIN`

### Methodes ajoutees a des repos existants
- **`AuthRepository.mergeUserFields(fields: Map)`** : merge generique sur `users/{uid}` (utilise par ProfileSync pour la geoloc + PatientViewModel pour la morpho)
- **`MessagerieRepository.getInterlocuteurUid(conversationId)`** : lookup du UID de l'interlocuteur dans une conversation

### ViewModels refactores
Les 5 VMs n'importent plus du tout `FirebaseFirestore` :
```diff
- import com.google.firebase.firestore.FirebaseFirestore
- private val firestore = FirebaseFirestore.getInstance()
- db.collection("xxx").document(id).set(data).await()
+ private val xxxRepository: XxxRepository (inject Hilt)
+ xxxRepository.setXxx(id, data)
```

## 📊 Verification

```bash
grep -nE "firestore\.|FirebaseFirestore" app/src/main/java/com/diabeto/ui/viewmodel/*.kt
```

Plus de calls directs Firestore depuis les 5 VMs cibles. Seuls les commentaires (`// passe par xxxRepository au lieu de FirebaseFirestore direct`) subsistent.

## 🧪 Tests

Compile + tests existants OK. Le refactor est purement structurel — meme comportement runtime.

## ⏭️ Reste a faire (v2.1.48+)

| Priorite | Item | Ship |
|---|---|---|
| 🟠 | **E** Mode famille V1 (1 aidant gratuit) | v2.1.48 |
| 🟡 | **J** i18n complet 6 langues : FR/EN/AR + Pidgin + Douala + Bassa + Fulfulde | v2.1.49+ (par batches) |
| 🟢 | UI revoke cote patient + receipts RGPD | v2.1.49 |
| 🟢 | Supabase storage cascade dans delete-account | v2.1.49 |
| 🟡 | **I** Tests E2E Maestro (5 scenarios) | v2.1.50 |

## 📦 Empreinte

- APK : 58 Mo (inchange)
- +2 fichiers (CommunityRepository, RdvSharedRepository)
- 5 VMs alleges (650+ lignes → 550 lignes pour RDV par exemple)
- Aucune nouvelle dependance
