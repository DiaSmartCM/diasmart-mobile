# DiaSmart v2.1.86

## Les rendez-vous du médecin réapparaissent

La liste des rendez-vous ne chargeait que ceux à venir. Les onglets « Tous » et
« Passés » filtraient donc une liste qui ne contenait déjà que le futur : ils ne
pouvaient rien afficher d'autre. Un rendez-vous programmé plus tôt dans la
journée devenait invisible dans les trois onglets.

Le tableau de bord, lui, comptait tous les rendez-vous du jour. D'où un
compteur annonçant cinq rendez-vous en face d'une liste vide, et l'impression
d'une perte de données.

La liste charge maintenant l'historique complet, du plus récent au plus ancien,
et le compteur ne compte plus que ce que la liste affiche.

## Les rappels sonnent au moment prévu

Les alarmes n'étaient programmées qu'au lancement de l'application et au
redémarrage du téléphone. Créer un médicament ou un rendez-vous ne posait aucune
alarme : elle n'existait qu'après une relance de l'application, et si l'heure
était passée entre-temps, elle basculait au lendemain. Un rappel à courte
échéance ne pouvait donc jamais se déclencher.

L'alarme est désormais posée au moment de l'action : création d'un traitement,
activation ou désactivation de son rappel, création d'un rendez-vous. Elle est
retirée à la suppression du traitement — auparavant, un traitement supprimé
continuait de sonner jusqu'à son échéance.

## Rendez-vous transmis au patient : le médecin sait à quoi s'en tenir

Un rendez-vous n'est transmis au patient que si celui-ci possède un compte lié.
Pour un dossier créé localement par le médecin, rien n'était envoyé et rien ne
le signalait : le rendez-vous s'affichait dans son agenda comme n'importe quel
autre, et il pouvait croire son patient prévenu.

Le médecin est maintenant averti lorsque le patient n'a pas de compte lié. De
même, un échec d'envoi dû au réseau s'affiche à l'écran au lieu de rester
silencieux.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
