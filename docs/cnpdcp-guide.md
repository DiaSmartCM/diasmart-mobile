# Guide CNPDCP Cameroun — Declaration DiaSmart

Document de reference pour la **declaration obligatoire** des donnees a caractere personnel (DCP) sensibles (donnees de sante) au regulateur camerounais avant lancement V1 grand public.

> **Auteur** : NGOS THEODORE — solo dev, Master 2 Sciences Biomedicales, U. Ngaoundere.
> **Date** : 2026-05-22.
> **Statut** : a executer avant V1 payant.

---

## 1. Contexte legal

La **loi n° 2010/012 du 21 decembre 2010** relative a la cybersecurite et a la cybercriminalite au Cameroun + la **loi n° 2024/017** sur la protection des donnees personnelles **(en cours d'adoption / promulguee selon date verification)** imposent :

- **Declaration prealable** a la **CNPDCP** (Commission Nationale pour la Protection des Donnees a Caractere Personnel) pour tout traitement de DCP.
- Pour les **donnees de sante** : autorisation specifique (regime renforce — categorie « sensibles »).
- **Designation d'un DPO** (Delegue a la Protection des Donnees) si traitement « regulier et systematique » > 250 personnes.
- **Consentement explicite, libre, eclaire et specifique** du patient.
- **Droit d'acces, rectification, suppression, portabilite**.
- **Notification de violation** sous 72h aux autorites + utilisateurs concernes.

> ⚠️ Verifier la date exacte d'entree en vigueur de la loi 2024/017 — verifier au MINPOSTEL ou avec un avocat.

---

## 2. Etat actuel DiaSmart (audit interne)

| Critere CNPDCP | Statut DiaSmart |
|---|---|
| Declaration CNPDCP | ❌ Pas faite |
| Consentement patient documente | ⚠️ Implicite via inscription, pas de checkbox dedie « donnees de sante » |
| Politique de confidentialite publique | ⚠️ Pas trouvee sur site/app |
| CGU specifiques traitement DCP | ❌ Pas faites |
| DPO designe | ❌ Pas designe |
| Mecanisme droit d'acces | ✅ Partiel (profil + export CSV) |
| Mecanisme droit a la suppression | ⚠️ Bouton existe mais ne cascade pas Firestore/Supabase (cf. Tier 2 F) |
| Notification violation 72h | ❌ Aucune procedure |
| Chiffrement transit | ✅ TLS partout |
| Chiffrement repos | ✅ Room/SQLCipher + Firestore + Supabase chiffrent |
| Logs d'acces (qui a vu quoi) | ❌ Pas de log audit |
| Conservation limitee | ❌ Pas de purge automatique |

**Verdict** : **non conforme** pour V1 payant. Conforme pour R&D / beta privee si declaration "test" faite.

---

## 3. Procedure de declaration CNPDCP — etapes

### 3.1 Documents a preparer

1. **Identite du responsable de traitement** :
   - NGOS THEODORE — adresse complete, telephone, email, CNI
   - Statut juridique : actuellement micro-entreprise / particulier → ideal de creer une **SAS ou SARL Cameroun** avant declaration (limite responsabilite, credibilite vis-a-vis CNPDCP)

2. **Fiche de declaration de traitement** (CNPDCP formulaire officiel) :
   - **Finalite** : « Outil d'aide a la gestion du diabete pour patients et professionnels de sante (suivi glycemie, telemedecine, conseil IA) »
   - **Categories de donnees** : etat civil, contact, donnees de sante (glycemies, HbA1c, IMC, repas, medicaments, journal symptomes), donnees techniques (IP, FCM token)
   - **Categories de personnes concernees** : patients diabetiques, professionnels de sante (medecins endocrinologues, nutritionnistes)
   - **Destinataires** : le patient lui-meme, son medecin attitre, le DPO, sous-traitants techniques (Firebase, Vercel, Supabase, Google Gemini via proxy)
   - **Duree de conservation** : 5 ans apres dernier acces, suppression au-dela
   - **Transferts hors Cameroun** : oui (US/EU — Firebase, Vercel, Supabase, Google). Mentionner les **clauses contractuelles types** ou **decision d'adequation**.
   - **Mesures de securite** : TLS, chiffrement repos, App Lock biometrique, App Check, Firestore rules

3. **Modele de consentement** :
   - Checkbox a l'inscription : « J'accepte que mes donnees de sante soient traitees pour les finalites decrites dans la [Politique de confidentialite] (lien). Je peux retirer mon consentement a tout moment. »
   - Granularite : consentement separe pour (a) suivi medical, (b) partage avec medecin attitre, (c) communaute (anonymise), (d) IA ROLLY (donnees envoyees a Gemini via proxy)

4. **Politique de confidentialite** (publier sur https://project-d-r1997t.web.app/privacy) :
   - Quelles donnees collectees
   - Pourquoi
   - Combien de temps
   - Avec qui partagees
   - Vos droits + comment les exercer
   - DPO contact

5. **CGU / Termes du service** :
   - Distinction claire : DiaSmart **n'est pas un dispositif medical**, c'est un outil d'aide. Pas de diagnostic, pas de prescription.
   - Limitation de responsabilite
   - Juridiction camerounaise

---

### 3.2 Depot du dossier

**Adresse CNPDCP** :
- Yaounde — coordonnees a confirmer (verifier sur site officiel ou MINPOSTEL)
- Egalement par email a confirmer

**Cout** : la declaration en elle-meme est gratuite. Coute potentiellement un avocat pour rediger les CGU + Politique de confidentialite (compter 200-500 €).

**Delai** : ~3 mois entre depot et accuse de reception / autorisation.

---

### 3.3 Designation du DPO

Pour DiaSmart au stade actuel (solo dev, < 250 patients) — pas obligatoire mais **recommande pour la credibilite**.

Options :
1. **Auto-designation** : NGOS THEODORE = DPO de DiaSmart (acceptable pour V1)
2. **DPO externe partage** : services qui mutualisent un DPO pour plusieurs petites entites (~50-100 €/mois)

Email DPO public : `dpo@diasmart.cm` (a creer si tu prends un nom de domaine .cm).

---

## 4. Actions techniques a faire AVANT declaration

Sans ces actions, la declaration sera incomplete ou rejetee :

1. ✅ **Cle Gemini retiree de l'APK** (fait v2.1.37) — base de la securite
2. ✅ **Firestore rules durcies** (fait v2.1.35)
3. ✅ **TLS partout** (fait)
4. ❌ **Politique de confidentialite publique** (a rediger + publier)
5. ❌ **CGU specifiques** (a rediger)
6. ❌ **Checkbox consentement explicite a l'inscription** (a coder, ~2h)
7. ❌ **Cascade delete RGPD** (cf. Tier 2 F)
8. ❌ **Log audit acces patient** (qui a vu quoi quand) (cf. monitoring Tier 2 C)
9. ❌ **Procedure notification violation 72h** (proces interne + endpoint d'alerte)
10. ❌ **Email DPO + page contact** sur le site

---

## 5. Couts estimes (synthese)

| Poste | Estimation |
|---|---|
| Avocat pour CGU + Politique de confidentialite | 200-500 € |
| Creation SAS/SARL Cameroun | ~300-800 € (greffe + frais) |
| Domaine .cm (`diasmart.cm`) | 100-200 € / an |
| Email professionnel + DPO | gratuit (Gmail Workspace ou Zoho) |
| Avocat optionnel pour suivi du dossier CNPDCP | 200-400 € |
| **Total minimum** | **800-2000 €** |

Sans l'option avocat, on peut faire le minimum a ~500 € (creation entite + domaine).

---

## 6. Plan d'execution recommande (2-3 mois)

### Mois 1
1. Creer statut juridique (SAS Cameroun de preference)
2. Acheter `diasmart.cm`
3. Rediger Politique de confidentialite + CGU (template + ajustement, avec ou sans avocat)
4. Coder la checkbox de consentement explicite a l'inscription
5. Publier la Politique de confidentialite sur le site

### Mois 2
6. Designer formellement le DPO (auto ou externe)
7. Implementer le cascade delete RGPD (Tier 2 F)
8. Implementer le log audit acces (Tier 2 C partiel)
9. Preparer le dossier CNPDCP complet

### Mois 3
10. Deposer la declaration CNPDCP
11. Attendre accuse de reception (~3 mois)
12. En parallele : implementer la procedure « notification violation 72h »

---

## 7. Ressources utiles

- **CNPDCP** : verifier le site officiel via MINPOSTEL (Ministere des Postes et Telecommunications)
- **Loi n° 2010/012** : disponible sur Legicam ou site presidence
- **Template Politique de confidentialite RGPD-like** : la CNIL francaise (cnil.fr) publie des modeles adaptables au Cameroun
- **Avocat specialiste e-sante Yaounde** : demander a la chambre des avocats du Cameroun

---

## 8. Risque si on ne fait pas la declaration

- **Amendes** : selon la loi 2010/012 + 2024/017, amendes jusqu'a 50M XAF + emprisonnement (jusqu'a 5 ans) en cas d'utilisation malveillante.
- **Plainte d'un patient** : risque legal personnel sur le solo dev tant que pas de SAS.
- **Refus partenariat hospitalier** : aucun hopital serieux (Hopital de Reference de Ngaoundere, Hopital Central de Yaounde, Centre Pasteur) ne signera de partenariat sans declaration CNPDCP a jour.
- **Refus investisseur** : tout fonds healthtech demandera la conformite avant signature seed.

**Verdict** : a faire **avant** :
1. Lancement V1 payant (obligatoire legal)
2. Partenariat hospitalier (credibilite)
3. Levee de fonds seed (due diligence)

Pour la R&D / beta privee actuelle, on est dans une zone grise mais low-risk (les patients sont volontaires, donnees pseudonymisees, < 250 personnes).
