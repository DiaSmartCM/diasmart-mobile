# DiaSmart v2.1.82

## Les dossiers appartiennent désormais à un compte, plus au téléphone

La base locale n'était pas cloisonnée : les dossiers patients appartenaient à
l'appareil, pas à l'utilisateur connecté. Sur un téléphone où l'application
avait d'abord servi à un patient, un médecin qui s'y connectait ensuite
retrouvait ses dossiers et sa glycémie.

Chaque dossier porte maintenant l'identifiant du compte qui l'a créé, et toutes
les lectures filtrent dessus.

**Les dossiers déjà présents restent sans propriétaire.** Ils sont conservés
mais n'apparaissent dans aucune liste tant qu'ils n'ont pas été réattribués. Les
attribuer automatiquement au compte connecté pendant la mise à jour aurait
reproduit exactement le défaut corrigé : on ne devine pas à qui appartient une
donnée de santé.

### Trois fuites découvertes en corrigeant

Le filtrage a révélé des composants qui interrogeaient la base sans passer par
le contrôle d'accès :

- **Rappels de traitement** : ils parcouraient tous les dossiers de l'appareil.
  Sur un téléphone à plusieurs comptes, une notification pouvait afficher le nom
  du médicament d'un autre utilisateur sur l'écran verrouillé.
- **Widget d'écran d'accueil** : il affichait la glycémie du premier dossier
  trouvé, quel que soit le compte connecté.
- **Sauvegarde cloud** : elle envoyait l'ensemble des dossiers locaux, y compris
  ceux d'un autre compte.

Les trois filtrent désormais sur le compte connecté.

## Effacement des données locales à la déconnexion

Les dossiers du compte sortant sont effacés du téléphone lors de la
déconnexion. Ceux des autres comptes et les dossiers sans propriétaire ne sont
pas touchés.

Un avertissement précède l'opération, avec un bouton de sauvegarde **dans le
dialogue** : les données déjà envoyées dans le cloud reviennent à la prochaine
connexion, celles qui ne l'ont jamais été sont perdues.

## Avertissement avant suppression de compte

Le dialogue de suppression rappelle que la sauvegarde cloud disparaît avec le
compte, et oriente vers « Exporter mes données », qui produit un fichier
conservé sur le téléphone. C'est le seul moyen de garder une trace après
suppression.

## Avant la mise à jour

Cette version modifie la structure de la base locale. Une sauvegarde cloud est
recommandée avant l'installation.

L'APK s'installe par-dessus la version précédente.
