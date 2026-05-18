# DiaSmart v2.1.39 — Tap-to-open + "Mes avis" coté médecin

## 🎯 Comportement attendu

Quand l'utilisateur tape sur une notification, l'app s'ouvre directement sur le bon ecran (au lieu du dashboard) :

| Notification | Destination |
|---|---|
| Nouveau message (`new_message`) | la conversation precise |
| Nouveau post communaute (`new_community`) | l'ecran Communaute |
| Nouvel avis patient (`new_review`, médecin) | l'ecran **Mes avis** (lecture seule) |
| Appel entrant (`incoming_call`) | inchangé (IncomingCallActivity full-screen) |
| Mise a jour (`app_update`) | inchangé (dashboard) |

Si l'utilisateur n'est pas encore authentifie quand la notif arrive (cas froid : app deja killee), le deep-link attend la fin du splash + login pendant max 12 s avant de naviguer ; sinon il est rejeté pour ne pas court-circuiter le login.

## 👨‍⚕️ Nouvelle vue "Mes avis" (cote médecin)

Le médecin a maintenant un ecran dedie pour voir sa reputation :

- **Note moyenne globale** affichee en gros (`4.3 / 5`) avec affichage étoile.
- **Nombre total d'avis** reçus.
- **Liste complete** des avis (nom du patient, note 1-5, commentaire, date relative).
- **Lecture seule** : un bandeau avec icône 🔒 rappelle que les avis ne peuvent pas être modifies. Coherent avec le contrat patient/médecin — c'est le patient qui evalue.
- Accessible depuis : tap sur notification "nouvel avis" ET nouvelle carte "Mes avis" sur le dashboard médecin (à côté de "Compte-rendu / Ordonnance").

## 🏗️ Implementation

**Nouveaux fichiers** :
- `app/.../notifications/DeepLinkBus.kt` — singleton `MutableSharedFlow<DeepLinkEvent>` avec `replay=1` (l'event survit si emis avant que la nav soit prete).
- `app/.../ui/screens/MesAvisScreen.kt` — Compose screen + `MesAvisViewModel` (lit `users/{uid}.ratingSum / reviewCount` + appelle `DoctorReviewRepository.getReviewsForDoctor(uid)`).

**Fichiers modifies** :
- `MainActivity` : `onCreate` lit l'intent + `onNewIntent` recupere l'intent quand l'app etait en arriere-plan, parse `navigate_to` + `conversation_id`, emet sur le bus. Les extras sont supprimes apres lecture pour eviter de re-emettre apres rotation.
- `Navigation.kt` : nouvelle route `MES_AVIS = "mes_avis"`, composable correspondant, et un `LaunchedEffect` qui collecte `DeepLinkBus.events` et navigue (en attendant que la route courante ne soit plus splash/login/onboarding).
- `DiaSmartFCMService.afficherNotificationAvis()` : `navigate_to` passe de `dashboard` à `mes_avis`.
- `DashboardScreen` : nouvelle FeatureCard "Mes avis" cote médecin + parametre `onNavigateToMesAvis`.

## ⚙️ Migration

Aucune action utilisateur. Les anciennes notifications encore en file d'attente continuent de fonctionner (elles ouvrent l'app sans deep-link, l'utilisateur navigue à la main).

## 📦 Empreinte

- APK : 58 Mo (inchangé).
- Aucune nouvelle permission.
- Aucun nouvel endpoint serveur (tout est cote client).
