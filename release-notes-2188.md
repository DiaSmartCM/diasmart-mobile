# DiaSmart v2.1.88

## Les avis réapparaissent

Le médecin voyait « Aucun avis publié » alors que ses avis existaient bel et
bien.

La requête filtrait sur un champ et triait sur un autre, ce qui exige une
configuration particulière de la base côté serveur. Faute de cette
configuration, la requête échouait et la liste revenait vide, sans message.

Le tri se fait désormais après réception, comme ailleurs dans l'application. La
liste ne dépend plus d'un réglage à créer à la main.

## Les rendez-vous entrent dans l'agenda du téléphone

Deux obstacles se cumulaient.

Côté patient, seuls les rendez-vous marqués comme confirmés étaient ajoutés à
l'agenda. Or ceux programmés par le médecin arrivaient non confirmés : aucun n'y
parvenait. Un rendez-vous posé par le médecin est désormais confirmé par
lui-même, et tout rendez-vous à venir rejoint l'agenda.

Côté médecin, l'ajout à l'agenda n'avait lieu que lorsqu'il acceptait une
demande envoyée par un patient. Un rendez-vous qu'il créait lui-même n'entrait
dans aucun agenda. C'est corrigé.

## Des messages compréhensibles à la place des erreurs techniques

L'application affichait le détail interne des erreurs. On pouvait lire, en plein
écran :

> Upload echoue: IllegalStateException: HTTP 502 — {"error":"supabase_unreachable"}

Ce message n'apprend rien d'utile et expose le fonctionnement interne du
service.

Les erreurs sont maintenant traduites en phrases actionnables — absence de
connexion, service momentanément indisponible, session expirée, accès refusé —
sur l'ensemble des écrans. Le détail technique reste consigné dans les journaux
de l'application, à l'usage du développeur.

## Tableau de bord du médecin allégé

Le compteur « Rappels » affichait invariablement zéro côté médecin : les rappels
de traitement concernent le patient, qui suit ses propres prises. Cette carte
occupait un tiers de la rangée sans rien apprendre.

Elle disparaît de l'interface du médecin, où seuls les patients suivis et les
rendez-vous restent affichés. Côté patient, elle est inchangée.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
