# Espace etablissement (offre B2B2C)

Un hopital, une clinique ou un centre de sante suit ses patients diabetiques
dans DiaSmart, a plusieurs soignants.

## Qui fait quoi

| Compte | Ce qu'il peut faire |
|---|---|
| Administrateur (le soignant qui cree l'espace) | tout ce que fait un soignant, plus : voir le code soignant, creer de nouveaux codes, retirer un soignant ou un patient, generer le rapport pour le payeur |
| Soignant (compte medecin, entre avec le code soignant) | tableau de bord, liste de l'equipe, code patient a partager, quitter l'equipe |
| Patient (entre avec le code patient) | rejoindre le centre (c'est son accord), le quitter a tout moment |

Dans l'appli : **Parametres > Etablissement de sante**.

## Tableau de bord

Calcule sur le telephone du soignant a partir des sauvegardes du patient
(`backups/{uid}/glucose` et `backups/{uid}/hba1c`), sans Cloud Functions.

- **A revoir en priorite** : HbA1c >= 9 %, une glycemie < 54 mg/dL en 30 jours,
  3 glycemies < 70 mg/dL ou plus, ou moyenne sur 30 jours >= 250 mg/dL.
- **A surveiller** : HbA1c >= 7 %, moyenne >= 180 mg/dL, 1 ou 2 glycemies
  < 70 mg/dL, ou pas d'HbA1c depuis 6 mois.
- **Perdu de vue** : aucune mesure depuis 60 jours (ou aucune mesure, inscrit
  depuis plus de 30 jours).

C'est une aide pour savoir qui recontacter en premier, pas un diagnostic.

Cout Firestore : au plus 160 lectures par patient a chaque ouverture du
tableau de bord (150 dernieres glycemies + 10 HbA1c). Le plan gratuit donne
50 000 lectures par jour pour toute l'appli : un centre de 50 patients peut
ouvrir le tableau de bord environ 5 fois par jour. A surveiller quand les
premiers centres arrivent.

## Rapport pour le payeur

PDF d'une page, que des chiffres agreges : patients inscrits, actifs,
perdus de vue, HbA1c de laboratoire des 6 derniers mois (moyenne, < 7 %,
>= 9 %), glycemies basses, repartition des priorites. Aucun nom ni
identifiant. Un effectif de 1 a 4 s'affiche "< 5" et les pourcentages ne
sont pas donnes sous 5 patients.

## Firestore

Collections : `etablissements/{id}` (+ `membres`, `patients`),
`codes_invitation/{code}`, `affiliations/{uid}`. Les regles sont dans
`website/firestore.rules`. **Elles doivent etre deployees** (console Firebase
> Firestore > Regles, ou `firebase deploy --only firestore:rules`) avant que
l'espace etablissement marche : sans elles, tout est refuse.
