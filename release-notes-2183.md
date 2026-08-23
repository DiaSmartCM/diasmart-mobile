# DiaSmart v2.1.83

## Les rappels de traitement sonnent enfin à l'heure

Les rappels ne se déclenchaient pas au moment prévu, et la cause allait au-delà
d'un simple retard.

Le mécanisme reposait sur une tâche périodique de huit heures qui, à chaque
exécution, notifiait **tous** les médicaments actifs — sans jamais consulter
l'heure de prise enregistrée. Il n'y avait donc pas de rappel à 08 h 00 pour une
prise de 08 h 00, mais des séries de notifications à des moments décidés par le
système. Les rendez-vous suivaient la même logique, avec un contrôle horaire
incapable de tenir un préavis d'une heure.

Un rappel de traitement est une alarme : il se déclenche à l'heure exacte ou il
ne sert à rien. Le mécanisme périodique, volontairement imprécis pour préserver
la batterie, ne pouvait pas remplir ce rôle.

Désormais :

- Une alarme exacte est posée pour chaque prise, à l'heure enregistrée, et
  replanifiée pour le lendemain après chaque déclenchement.
- Les rendez-vous déclenchent une alarme une heure avant, avec le lieu.
- Ces alarmes traversent la veille profonde de l'appareil et s'affichent en
  priorité maximale.
- Elles sont reposées au redémarrage du téléphone. Sans cela, un simple
  redémarrage aurait supprimé tous les rappels sans aucun signe visible.

Si l'autorisation d'alarme exacte est refusée, un rappel approximatif est posé
plutôt que rien.

## Affichage corrigé sur les écrans étroits

Sur les téléphones moins larges, la carte du médecin traitant écrasait le
libellé « Médicaments » jusqu'à empiler ses lettres verticalement : les trois
étiquettes de données partagées réclamaient le double de la place disponible et
ne pouvaient pas passer à la ligne. Elles se répartissent maintenant sur
plusieurs lignes.

Le même défaut rendait la validation impossible : sur la carte de demande
d'accès, un nom de médecin un peu long repoussait les boutons Accepter et
Refuser hors de l'écran. Le nom est désormais tronqué et les boutons conservent
leur place.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
