# DiaSmart v2.1.46 — Onboarding contextuel etendu (4 ecrans)

## 💡 Tooltips contextuels sur les 4 ecrans majeurs

A la **premiere ouverture** de chaque ecran, une bulle d'aide apparait en haut (icone ampoule + titre + 1 phrase). L'utilisateur la ferme avec le X → ne reapparait plus jamais (flag DataStore separe par ecran).

### Glucose (GlucoseScreen)
> 💡 **Suivi de glycemie**  
> Tap sur le bouton + en bas a droite pour saisir une nouvelle lecture. Onglet HbA1c pour ton historique trimestriel. Tu peux exporter en CSV depuis Reglages.

### ROLLY (ChatbotScreen)
> 💡 **Ton assistant ROLLY**  
> Pose tes questions en francais, pidgin, ewondo, duala, bassa, fulfulde ou arabe. En cas d'urgence (tape "malaise", "vertige"...), les numeros SAMU 119 apparaissent immediatement.

### Messagerie (MessagerieScreen)
> 💡 **Messagerie patient-medecin**  
> Echange textuel ou par appel video avec ton medecin attitre. Tape sur le bouton + (en bas) pour demarrer une nouvelle conversation. Tu peux envoyer des PDF (rapports, ordonnances).

### Reports (ReportsScreen)
> 💡 **Envoyer un rapport** (patient)  
> Selectionne ton medecin destinataire, choisis la periode, puis genere ton rapport PDF. 3 canaux d'envoi : messagerie in-app, email, WhatsApp.

> 💡 **Envoyer un rapport** (medecin)  
> Genere une ordonnance ou un compte-rendu personnalise et envoie-le directement au patient.

## 🏗️ Implementation

**Nouveaux fichiers** :
- `app/.../ui/components/ContextualTooltip.kt` — Composable generique (ampoule + titre + message + X)
- `app/.../ui/viewmodel/OnboardingViewModel.kt` — Hilt singleton qui centralise les 4 flags (glucose/rolly/messagerie/reports) + leur dismiss

**Modifications** :
- `PreferencesRepository` : +4 cles DataStore (`onboarding_glucose_seen`, `onboarding_rolly_seen`, `onboarding_messagerie_seen`, `onboarding_reports_seen`) + Flow + suspend marks
- 4 ecrans wired avec `hiltViewModel<OnboardingViewModel>()` + tooltip en haut

## ⏭️ Tier 2 — reste apres v2.1.46

**Memoire mise a jour** : NGOS est sur Firebase Spark gratuit (pas de moyens pour Blaze). Tout reste compatible.

| Priorite | Item | Estimation |
|---|---|---|
| 🟠 | **E** Mode famille V1 | 1 semaine |
| 🟠 | **H** Refactor 5 ViewModels qui bypassent les Repos | 3-4 jours |
| 🟡 | **I** Tests E2E Maestro (5 scenarios) | 1 semaine |
| 🟡 | **J** i18n complet : 95 → 218 strings × 4 langues (FR/EN/AR/PCM) | 2-3 semaines |
| 🟢 | **UI** revoke cote patient (TreatingDoctorCard kebab) | 2h |
| 🟢 | **UI** affichage + telechargement recu RGPD apres delete | 2h |
| 🟢 | Supabase storage cascade dans delete-account | 1 jour |

## 📦 Empreinte

- APK : 58 Mo
- DataStore : +4 cles boolean
- Aucune nouvelle dependance
- Aucun nouvel endpoint serveur
