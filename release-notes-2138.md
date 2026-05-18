# DiaSmart v2.1.38 — Notifications push : messages + communauté

## 🔔 Nouvelles notifications

Avant : les messages patient ↔ médecin et les posts dans la communauté ne déclenchaient AUCUNE notification — il fallait ouvrir l'app pour les voir.

Maintenant :
- **Patient ↔ Médecin** : à chaque message envoyé, l'autre participant reçoit une notif push avec le nom de l'expéditeur en titre et un aperçu (120 caractères max). Tap → ouvre l'app.
- **Communauté** : à chaque post dans la communauté, tous les membres reçoivent une notif (l'auteur du message est exclu côté client). Tap → ouvre l'app.
- **Pièces jointes** : si le message contient un PDF (rapport, ordonnance), le préfixe « 📎 » apparaît dans l'aperçu.

## 🏗️ Architecture

Pattern « client-triggered » (cohérent avec `notify-review.js` pour les avis médecin) — pas de Cloud Functions Firestore triggers (forfait Spark) :

1. Le client écrit le message dans Firestore (comme avant).
2. Après succès, il appelle un endpoint Vercel `/api/notify-message` ou `/api/notify-community` avec son Firebase ID token.
3. L'endpoint vérifie l'authentification + l'appartenance à la conversation, résout le destinataire, et envoie un push FCM via Firebase Admin SDK.

**Nouveaux fichiers** :
- `website/api/notify-message.js` — push 1-à-1, vérifie patientId/medecinId, rate limit 500/24h/UID.
- `website/api/notify-community.js` — push au topic FCM `community`, rate limit 100/24h/UID.
- `app/.../data/api/NotificationApi.kt` — client OkHttp pour les 2 endpoints.

**Fichiers modifiés** :
- `MessagerieRepository` : appelle `notifyMessage()` après `envoyerMessage()` / `envoyerMessageAvecPieceJointe()` (best-effort, n'échoue pas l'envoi).
- `CommunityViewModel` : appelle `notifyCommunity()` après écriture Firestore.
- `DiaSmartFCMService` :
  - Abonnement automatique au topic FCM `community` au démarrage.
  - Gère 2 nouveaux types `new_message` et `new_community` avec canaux dédiés.
  - Filtrage côté client : l'auteur du post communauté ne reçoit pas son propre push.
  - Canaux : `diasmart_messages` (HIGH, déjà existant) et `diasmart_community` (DEFAULT, nouveau).
  - Notif chat : ID stable par conversation → la nouvelle remplace la précédente (pas d'empilement).

## 🛡️ Sécurité / anti-abus

- Auth Firebase ID token (Bearer) sur les 2 endpoints.
- Vérification d'appartenance : pour `/notify-message`, le serveur charge la conversation Firestore et confirme que l'appelant est `patientId` OU `medecinId`.
- Rate limit Firestore par UID : 500 messages/jour et 100 posts communauté/jour.
- Tokens FCM morts nettoyés automatiquement.
- Aperçu tronqué à 120 caractères côté serveur.

## ⚙️ Migration

Aucune action utilisateur. À la première ouverture après mise à jour, l'app s'abonne automatiquement au topic FCM `community`.

## 📦 Empreinte

- APK : 58 Mo (inchangé).
- Aucune nouvelle permission Android (`POST_NOTIFICATIONS` déjà déclarée depuis v2.1.31).
- Latence ajoutée par message envoyé : ~200-400 ms (appel HTTP best-effort, asynchrone, non bloquant pour l'UX d'envoi).

## 🗺️ Pas dans ce patch

- Deep-link à partir du tap notification (ouvre l'app sur le dashboard et l'utilisateur navigue à la main vers messagerie/community). À polir dans un prochain ship.
- Notifications de rendez-vous validés / consultations programmées (déjà via WorkManager local).
