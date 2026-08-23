# DiaSmart v2.1.87

## Correction urgente : l'enregistrement fonctionne de nouveau

La version précédente empêchait d'enregistrer un traitement ou un rendez-vous,
côté médecin comme côté patient.

La programmation de l'alarme avait été placée entre l'enregistrement et le
rafraîchissement de l'écran. Lorsqu'elle échouait, l'erreur interrompait la
suite : la fenêtre restait ouverte et la liste ne se mettait pas à jour. La
donnée était pourtant bien enregistrée — simplement invisible.

Chaque programmation d'alarme est désormais isolée. Si elle échoue, l'échec est
consigné et l'enregistrement se termine normalement. Une fonction accessoire ne
peut plus interrompre la fonction principale.

## Savoir pourquoi les rappels ne sonnent pas

Plutôt que de continuer à supposer, l'application le dit maintenant elle-même.

Dans les Paramètres, une carte **« Les rappels ne sonnent pas ? »** propose deux
actions.

**Tester** pose une alarme dans une minute et affiche l'état réel de l'appareil :
version d'Android, marque et modèle, autorisation des alarmes exactes, état des
notifications, état du canal de rappel des traitements, et présence d'une alarme
en attente. Si la programmation échoue, le message indique précisément la nature
de l'erreur.

**Autoriser** ouvre directement le réglage Android des alarmes exactes. Le
chemin vers ce réglage diffère d'un constructeur à l'autre, expliquer où cliquer
ne suffit pas ; si l'écran n'existe pas sur l'appareil, la fiche de
l'application s'ouvre à la place.

Ce test permet de distinguer trois situations : une autorisation refusée, qui se
règle en un appui ; un fonctionnement normal ; ou un blocage des tâches en
arrière-plan par le constructeur, qui demande d'autoriser l'application dans les
réglages de batterie.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
