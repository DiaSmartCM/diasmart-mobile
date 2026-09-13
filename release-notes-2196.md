## DiaSmart v2.1.96

### La reponse rapide part aussi quand l application est fermee

Depuis la v2.1.95, on peut repondre a un message depuis le volet des notifications. Application ouverte ou en arriere-plan, la reponse partait. Application fermee, la notification affichait **Envoi impossible**.

### Pourquoi ca echouait

Application fermee, Android demarre un processus neuf rien que pour traiter la reponse. Firestore n a alors aucune connexion ouverte. L envoi commencait par relire le profil de l expediteur sur le serveur, avec dix secondes au plus : le delai etait depasse, le profil revenait vide, et l envoi s arretait la. Le tout se jouait dans un recepteur de notification, qu Android ne laisse vivre que quelques secondes.

### Ce qui change

- La reponse est confiee a une tache de fond WorkManager. Elle attend que le reseau soit disponible, prend le temps qu il faut et continue meme si Android coupe le processus.
- En cas d echec, elle reessaie deux fois avant d afficher **Envoi impossible**.
- Le profil de l expediteur est lu d abord dans la copie locale de Firestore, sans attendre le serveur. Les messages tapes dans l application en profitent aussi.
- Deux reponses rapides dans la meme conversation partent dans l ordre ou elles ont ete ecrites.
- Si l envoi echoue malgre tout, la cause exacte est enregistree dans Crashlytics. Jusqu ici elle etait perdue.

### Ce qui ne change pas

La notification affiche toujours **Envoi…** tout de suite, puis le texte envoye. Sans reseau, elle reste sur **Envoi…** et la reponse part des que la connexion revient.
