# DiaSmart v2.1.81

## Le médecin ne voit plus les données d'un patient sans son accord

Le tableau de bord du médecin affichait une glycémie moyenne et une fiche
patient dans « Patients récents », alors qu'aucun patient ne lui avait accordé
de partage. Une incohérence trahissait le défaut : le compteur indiquait
« 0 patients » pendant qu'une fiche s'affichait juste en dessous.

Les deux valeurs ne lisaient pas la même source. Le compteur interrogeait les
partages actifs, où le consentement conditionne l'accès. La moyenne glycémique
et la liste des patients récents, elles, lisaient la base locale de l'appareil,
qui ne connaît rien aux autorisations.

Ce qui change :

- **Moyenne glycémique.** Plus aucune donnée glycémique n'est agrégée sur le
  tableau de bord du médecin. La carte affiche désormais le nombre de patients
  liés. Côté patient, rien ne change : il consulte sa propre glycémie.
- **Patients récents.** La liste provient des partages consentis. Un patient
  n'y figure qu'après avoir explicitement autorisé ce médecin à accéder à ses
  données. En l'absence de partage : « Aucun patient ne partage ses données
  avec vous ».
- **Ouverture d'une fiche** depuis cette liste : elle passe par l'écran de
  données partagées, où l'autorisation est vérifiée à la lecture.

## Limite connue, à traiter séparément

Les dossiers patients enregistrés sur l'appareil ne portent aucune marque de
propriétaire : la base locale appartient au téléphone, pas au compte connecté.
Sur un appareil où l'application a d'abord servi à un patient, un médecin qui
s'y connecte ensuite retrouve ces dossiers dans l'onglet « Patients ».

Cette version ferme l'accès sur le tableau de bord. Le cloisonnement de la base
locale par compte demande une modification du schéma de données et sera traité
dans une version dédiée.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
