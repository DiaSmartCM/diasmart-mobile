# DiaSmart v2.1.79

## Les prédictions deviennent des prédictions

Jusqu'ici, la courbe des six prochaines heures était une droite de régression
tracée sur les dernières mesures, puis ramenée doucement vers la moyenne. Aucune
variable ne représentait le repas : un patient qui venait de manger 68 g de
glucides voyait la même courbe plate qu'à jeun. Ce n'était pas une prédiction
physiologique, c'était le prolongement d'une tendance — d'où les valeurs
identiques répétées d'heure en heure.

La glycémie est désormais projetée à partir de ce qui la fait réellement monter.

- **Amplitude du pic** proportionnelle à la charge glycémique du repas — les
  glucides multipliés par l'index glycémique — et non aux glucides seuls.
- **Moment du pic** déduit de l'index : environ une heure pour un index élevé,
  1 h 20 pour un moyen, 1 h 45 pour un index bas. Un plat à index bas monte
  moins haut et plus tard.
- **Retour à la ligne de base** selon une courbe d'absorption, sur trois à cinq
  heures, et non en ligne droite.

Une nouvelle carte annonce le chiffre et l'heure : pic attendu, fourchette,
montée depuis la dernière mesure, et le repas qui en est à l'origine.

## Une prédiction calibrée sur vous

Le coefficient qui relie la charge glycémique à la montée réelle varie d'une
personne à l'autre, selon l'insulinorésistance et les traitements. Chaque repas
où les champs « avant repas » et « après repas » sont renseignés devient une
observation, et le coefficient personnel s'ajuste par moindres carrés.

La mesure « après repas » n'étant pas prise au sommet mais environ deux heures
plus tard, le modèle compare la montée observée à ce qu'il prédit à cet instant
précis. Sans cette correction, le coefficient serait systématiquement
sous-estimé.

En dessous de quatre repas mesurés, le coefficient de population est conservé et
l'écran l'indique clairement plutôt que de présenter une estimation générique
comme une valeur personnalisée.

## Notifications de pic glycémique

Deux alertes sont programmées à l'enregistrement d'un repas. Elles sont
calculées et déclenchées par le téléphone, donc **fonctionnent sans connexion**.

- **45 minutes après le repas** : le pic attendu et sa fourchette, avec le
  conseil correspondant. C'est la fenêtre où une marche change encore le
  résultat.
- **2 heures après le repas** : le moment où la mesure post-prandiale a un sens.
  C'est aussi elle qui affine les prédictions suivantes.

Ces deux alertes ignorent volontairement les contraintes d'économie de batterie
qui s'appliquent aux autres rappels : une notification de pic glycémique arrivée
en retard n'a plus d'intérêt. Le canal de notification est distinct, ce qui
permet de désactiver les prédictions sans perdre les rappels de traitement.

## Conseils selon le niveau

Les seuils cliniques sont calculés sur l'appareil et ne dépendent plus de la
disponibilité du réseau : hypoglycémie sévère, hypoglycémie, cible, glycémie
élevée, glycémie très élevée. Chacun porte une conduite à tenir concrète.

Aucun de ces conseils ne touche aux doses de traitement. Activité, hydratation,
composition du repas, moment de mesure et signes devant conduire à consulter —
oui. L'ajustement thérapeutique reste du ressort du médecin.

## Fiche santé du patient rétablie

L'écran de dossier médical n'avait pas disparu du code, mais plus aucun chemin
n'y menait côté patient depuis la réorganisation du tableau de bord. Il est de
nouveau accessible par une carte « Ma fiche santé ».

C'est aussi par là que se saisissent les données personnelles — date de
naissance, sexe, type de diabète, date de diagnostic, coordonnées, poids,
taille, tour de taille, masse grasse — via le crayon en haut de la fiche.

Le médecin et le patient consultent désormais le même écran, ce qui garantit
qu'ils voient les mêmes informations. L'action de suppression reste réservée au
médecin sur les dossiers qu'il gère.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
