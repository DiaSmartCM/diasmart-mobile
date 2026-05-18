# DiaSmart PWA v2.1.40 — Parite securite avec l'APK

Ce ship met la **version web** (PWA) au meme niveau de securite que l'APK Android v2.1.37+. Aucun changement Android dans cette release.

## 🔐 Securite (changement majeur)

**`GEMINI_API_KEY` n'est plus dans le client web.**

Avant : la cle Gemini etait dans `app.html` (deux endroits, lignes 1442 et 1490). Quiconque inspecte le code source de la PWA pouvait la copier et bruler le quota du compte Google AI.

Maintenant :
- `callGemini()` et `callGeminiVision()` envoient leur requete a `/api/rolly-chat` (Vercel) avec Firebase ID token (Bearer header). La cle Gemini vit cote serveur.
- Verification : `curl -s https://project-d-r1997t.web.app/app.html | grep -c "generativelanguage.googleapis.com"` retourne `0`. Seul subsiste la cle PUBLIQUE de configuration Firebase Web (par design — c'est un identifiant projet, pas un secret).
- Rate limit serveur : 200 req/24h/UID (anti-abus, partage avec l'APK).
- Fallback automatique vers `gemini-2.0-flash` si `gemini-2.5-flash` est surcharge (gere serveur-side).

## 🔔 Notifications in-app

Premier pas vers la parite notifs avec l'APK. La PWA ne supporte pas encore les push FCM (necessite une VAPID key + `firebase-messaging-sw.js` — a faire dans le prochain ship si tu veux).

Pour l'instant : **toast in-app + Notification API du navigateur** quand l'app est ouverte (meme dans un onglet d'arriere-plan).

- Demande la permission Notification au 1er login.
- Firestore `onSnapshot` sur :
  - `conversations` ou je suis participant → notif au 1er message d'un autre user
  - `community_messages` → notif sur chaque nouveau post (sauf le mien)
- Toast en bas a droite (cliquable, redirige vers messagerie ou community).
- Notification systeme via `new Notification(...)` si permission accordee.
- Filtrage : l'auteur du message ne se notifie pas lui-meme.
- Ignore les messages anterieurs au moment ou le listener s'est attache (sinon on bombarderait avec tout l'historique au reload).

## 🏗️ Implementation

**Fichiers modifies** :
- `website/public/app.html` :
  - 2 fonctions `callGemini` + `callGeminiVision` rewritees → `_rollyCall()` helper qui POST `/api/rolly-chat` avec Bearer ID token, gestion fallback + 401/429/503 user-friendly.
  - Bloc `initInAppNotifs()` + `_showNotif()` + `_showInAppToast()` (+ helper `escapeHtml`).
  - Import `onSnapshot` ajoute aux Firebase Firestore imports.
  - `initInAppNotifs()` appele depuis le auth state listener (apres login).
- `website/public/sw.js` : `diasmart-v2.1.33` → `diasmart-v2.1.40` (force le refresh du cache PWA chez l'utilisateur a la prochaine ouverture).
- `website/package.json` : version `2.1.37` → `2.1.40`.

## 🌐 Deploiement

- Firebase Hosting : https://project-d-r1997t.web.app ✅
- Vercel : https://website-omega-umber-20.vercel.app ✅

Les deux deployements sont synchronises.

## 🗺️ Pas dans ce ship (a faire plus tard si necessaire)

- **FCM Web Push** (notifications quand la PWA est completement fermee) → necessite une VAPID key dans Firebase Console (Project Settings → Cloud Messaging → Web Push certificates) + un `firebase-messaging-sw.js` dedie. Donne-moi la VAPID key et je le branche.
- **Vue "Mes avis"** cote medecin (parite avec l'APK v2.1.39) → la PWA n'a pas encore d'interface dediee.
- **Tap-to-open conversation** sur les notifs in-app → deja fonctionnel pour le toast ET la Notification systeme (les deux redirigent vers la bonne conv ou la community via `window.showScreen`).
- **App lock** (PIN/biometric a l'ouverture) → moins critique sur web (gere par le navigateur + session timeout Firebase Auth).

## ⚙️ Migration utilisateur

Aucune action requise. A la prochaine ouverture de la PWA :
- Le service worker telecharge le nouveau cache (`diasmart-v2.1.40`).
- Un bandeau "Nouvelle version disponible — Mettre a jour" apparait, cliquer force le reload propre.
- Au login : permission Notification demandee (refusable, l'app continue de fonctionner avec juste les toasts in-app).
