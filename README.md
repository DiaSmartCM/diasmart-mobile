<div align="center">

# DiaSmart — Diabétologie Intelligente 🇨🇲

**Application mobile gratuite de gestion du diabète, conçue pour le Cameroun et l'Afrique Sub-Saharienne.**

[![Latest release](https://img.shields.io/github/v/release/DiaSmartCM/diasmart-mobile?label=Derni%C3%A8re%20version&color=6771E4)](https://github.com/DiaSmartCM/diasmart-mobile/releases/latest)
[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://github.com/DiaSmartCM/diasmart-mobile/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2025.02-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/Licence-Propri%C3%A9taire-EF4444)](#-licence)

[📲 Télécharger l'APK](https://github.com/DiaSmartCM/diasmart-mobile/releases/latest) · [🌐 Web App](https://diasmart-cm.vercel.app) · [🐛 Signaler un bug](https://github.com/DiaSmartCM/diasmart-mobile/issues) · [📧 Contact](mailto:ngostheo30@gmail.com)

</div>

---

## 📱 Présentation

**DiaSmart** aide les patients diabétiques et leurs médecins à suivre la maladie au quotidien, même avec une connexion limitée. Pensée pour le terrain africain : **offline-first**, **multilingue**, et adaptée aux réalités locales (aliments, prix en FCFA, langues camerounaises).

> 100 % gratuite · Aucune publicité · Aucune revente de données

---

## ✨ Fonctionnalités

### 🩸 Suivi médical
- **Glycémie + HbA1c** : enregistrement, graphiques, statistiques (moyenne, min/max, temps dans la cible), classification automatique hypo/hyperglycémie
- **Médicaments** : posologie, rappels de prise, activation/désactivation
- **Rendez-vous** : planification, demandes patient↔médecin, rappels 1 h avant
- **Carnet de bord** : humeur, sommeil, activité physique, podomètre

### 🤖 ROLLY — Assistant IA
- Spécialisé **exclusivement en diabétologie** (Gemini 2.5 Flash via proxy sécurisé)
- **Ton camerounais** + connaissance des **aliments locaux** (ndolè, plantain, eru, manioc, igname…) avec leur index glycémique
- Analyse de repas **par photo**
- Adaptation automatique à la **langue de l'utilisateur**
- Voix (synthèse vocale locale, hors-ligne)

### 👨‍⚕️ Côté médecin
- Tableau de bord patients liés
- Partage de données **avec consentement explicite** du patient (révocable à tout moment)
- Génération d'**ordonnances** et de **comptes-rendus** PDF
- Envoi via messagerie in-app, e-mail ou **WhatsApp**
- Validations des réponses de ROLLY

### 👨‍👩‍👧 Mode famille
- Inviter un proche en lecture seule pour suivre un patient (1 aidant gratuit)

### 🌍 Accessibilité
- **8 langues** : Français, English, العربية, Pidgin Kamtok, Duala, Bassa, Fulfulde, Dii
- **Offline-first** : tout fonctionne sans réseau, synchronisation automatique au retour
- **Dark mode**, Material Design 3
- **App Lock** : empreinte / PIN / mot de passe

### 🔒 Confidentialité & conformité
- Écran de **consentement RGPD** au premier lancement (Article 9 — données de santé)
- **Suppression complète** du compte avec reçu RGPD signé
- Conforme **RGPD + loi camerounaise 2024** sur les données personnelles
- Base locale **chiffrée** (SQLCipher)

---

## 🏗️ Architecture

Application **MVVM** moderne, 100 % Kotlin + Jetpack Compose.

```
com.diabeto/
├── data/
│   ├── database/       # Room + SQLCipher (chiffré)
│   ├── dao/ entity/    # Persistance locale
│   ├── model/          # Modèles métier (Firestore)
│   └── repository/     # Source unique de vérité
├── di/                 # Hilt (injection de dépendances)
├── monitoring/         # Crashlytics
├── notifications/      # FCM + rappels (WorkManager)
├── security/           # App Lock (biométrie / PIN)
├── sync/               # Synchronisation Firestore offline-first
├── ui/
│   ├── components/     # Composants réutilisables
│   ├── navigation/     # Navigation Compose
│   ├── screens/        # Écrans (Dashboard, Glucose, ROLLY…)
│   ├── theme/          # Material 3 + thèmes
│   └── viewmodel/      # ViewModels
├── util/               # VoiceManager, WhatsAppShare…
└── voip/               # Appels audio/vidéo (WebRTC)
```

**Backend serverless** (`/website/api`) : proxy Vercel pour ROLLY (Gemini), OTP e-mail, rapports, suppression RGPD — sans clé API dans l'APK.

---

## 🛠️ Stack technique

| Domaine | Technologies |
|---|---|
| **Langage / UI** | Kotlin 2.0.21 · Jetpack Compose (BOM 2025.02) · Material 3 |
| **Architecture** | MVVM · Hilt 2.52 · Coroutines & Flow · Navigation Compose |
| **Données locales** | Room 2.6 + SQLCipher · DataStore |
| **Cloud (Free tier)** | Firebase Spark (Auth, Firestore, FCM, Crashlytics) · Vercel · Supabase Storage |
| **IA** | Google Gemini 2.5 Flash (via proxy Vercel) |
| **Temps réel** | WebRTC (appels audio/vidéo) |
| **Tests** | JUnit 4 · kotlinx-coroutines-test · Maestro (E2E) |

> 💡 Architecture pensée pour rester **100 % gratuite à héberger** (Firebase Spark + Vercel + Supabase Free).

---

## 🚀 Build depuis les sources

### Prérequis
- Android Studio Ladybug (2024.2) ou supérieur
- JDK 17 · Android SDK 35

### Étapes
```bash
git clone https://github.com/DiaSmartCM/diasmart-mobile.git
cd diasmart-mobile
```

Crée un `local.properties` avec tes secrets (jamais commité) :
```properties
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
TURN_USERNAME=...
TURN_PASSWORD=...
```

Puis :
```bash
# Build debug
./gradlew assembleDebug

# Build release signé
./gradlew assembleRelease

# Tests unitaires
./gradlew test
```

L'APK release est généré dans `app/build/outputs/apk/release/`.

---

## 📲 Installation (utilisateurs)

1. Télécharge la [**dernière APK**](https://github.com/DiaSmartCM/diasmart-mobile/releases/latest)
2. Autorise « Installer depuis des sources inconnues » sur ton Android
3. Ouvre le fichier `.apk` téléchargé
4. Accepte le consentement RGPD au premier lancement

---

## 📝 Licence

Projet sous **licence propriétaire** © NGOS THEODORE. Voir [LICENSE](LICENSE).

## 🔒 Confidentialité

[Politique de confidentialité](https://public-one-omega-88.vercel.app/privacy.html) · [CGU](https://public-one-omega-88.vercel.app/terms.html)

---

<div align="center">

**NGOS THEODORE** — Développeur & fondateur
📧 ngostheo30@gmail.com · 📍 Yaoundé, Cameroun 🇨🇲

Développé avec ❤️ pour améliorer la gestion du diabète en Afrique.

<sub>DiaSmart · Diabétologie Intelligente · Cameroun · Diabetes management · mHealth Africa · Glucose · HbA1c · ROLLY AI</sub>

</div>
