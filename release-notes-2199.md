# DiaSmart v2.1.99

## Analyse de repas : une photo ne reprend plus le plat précédent

Après une première analyse, l'application recopiait la phrase de ROLLY dans le
champ de description. À la photo suivante, cette phrase partait comme si le
patient l'avait écrite, et ROLLY la tenait pour certaine. Une sauce d'arachide
photographiée après un okok ressortait donc en « eru et water fufu », et ROLLY
allait jusqu'à signaler un eru « non visible » à côté de prunes.

- Le texte de ROLLY ne remplace plus ce que le patient a écrit.
- La description saisie sert à une seule photo. Choisir une autre photo
  l'efface, même si l'analyse précédente a échoué.
- Choisir une nouvelle photo efface le résultat de la précédente.

## ROLLY ne tombe plus en panne quand le quota Google est atteint

Le 13 septembre au soir, toutes les analyses échouaient avec « Le service est
momentanément indisponible ». Les journaux du serveur ont donné deux causes :

- La version gratuite de Gemini n'accorde que 20 requêtes par jour sur le
  modèle principal, pour l'ensemble des patients.
- Le modèle de secours, gemini-2.0-flash, a été retiré par Google. Une fois
  le quota atteint, il n'y avait plus rien derrière.

Le serveur essaie maintenant trois modèles l'un après l'autre. Google compte
le quota modèle par modèle : chacun ajoute le sien, sans aucun coût. Au
premier test, le troisième modèle a répondu en 14 secondes alors que les
deux premiers étaient épuisés ou surchargés.

Un défaut du compteur de requêtes par patient est aussi corrigé : pour un
nouvel utilisateur, il ne démarrait jamais.

## Plats camerounais mieux reconnus en photo

Ces corrections sont faites sur le serveur. Elles valent aussi pour les
versions déjà installées.

- **Nkui** : sauce brune, jamais noire, gluante, sans huile en surface, servie
  avec une boule de couscous de maïs. Il était pris pour du mbongo tchobi.
- **Okok** : même feuille que l'eru, mais brun-vert, lié à l'arachide et servi
  avec du manioc. Il était pris pour de l'eru.
- **Manioc bouilli** : nouvelle entrée. Des morceaux de racine fibreux ne sont
  plus confondus avec le couscous ou le water fufu.
- **Banane malaxée**, **safou**, **légumes sautés** : ajoutés au catalogue. La
  banane malaxée était lue comme une simple sauce d'arachide, sans les
  glucides du plantain.
- **Lipides** : l'eru, l'okok ou le nkui affichaient 0 g de lipides, parce que
  l'huile de palme et l'arachide ne se voient pas sur une photo. Les valeurs
  suivent maintenant la recette du plat reconnu.

Tests sur quatre photos de patients, avec le nom du plat écrit avant la photo :
nkui, okok, safou et banane malaxée sont tous reconnus. Sans description,
l'okok est encore parfois lu comme de l'eru : écrire le nom du plat reste le
moyen le plus sûr.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
