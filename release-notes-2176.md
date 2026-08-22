# DiaSmart v2.1.76

## Analyse de repas : fini les plats mal identifiés

L'analyse par photo nommait des plats qu'elle n'avait pas vraiment reconnus. La
cause venait des consignes envoyées au modèle : elles lui demandaient
explicitement de proposer sa « meilleure hypothèse » quand il hésitait. Une
hésitation devenait donc une affirmation.

Ce qui change :

- **Observer avant de nommer.** Le modèle décrit d'abord ce qu'il voit —
  couleurs, textures, cuisson, accompagnements — et ne cherche le nom du plat
  qu'ensuite, à partir de ces indices.
- **Plus de devinette.** Quand plusieurs plats restent possibles, il nomme le
  composant principal et son accompagnement plutôt que d'inventer un nom
  précis. Une photo floue, sombre ou incomplète donne désormais « Plat non
  identifiable » au lieu d'une estimation hasardeuse.
- **Justification obligatoire.** Le modèle renvoie la liste des aliments qu'il
  a réellement observés et son niveau de confiance. Le nom du plat doit
  s'appuyer sur cette liste.
- **Cuisine locale mieux décrite.** Ndolè, eru, koki, achu, taro, bobolo, kpem,
  sanga, poulet DG, alloco et une vingtaine d'autres plats sont maintenant
  accompagnés de leur aspect visuel caractéristique, pas seulement de leur nom.
- **Réglage plus strict.** La température du modèle passe de 0,4 à 0,15 pour
  l'analyse d'image : identifier une assiette est un travail d'observation, pas
  de création.

Ces corrections sont côté serveur : elles s'appliquent même aux versions
précédentes de l'application.

## Page de prédiction : texte plus court et plus lisible

Les réponses de ROLLY s'affichaient avec les astérisques du Markdown en clair
(`**Tendance générale :**`), précédées d'une salutation et étalées sur des
paragraphes trop longs pour une boîte de dialogue.

- Les analyses de risque et de tendance sont limitées à 120 mots, réparties en
  trois sections fixes, et entrent directement dans le sujet.
- Un nouveau composant de rendu transforme les titres en intitulés colorés et
  les listes en vraies puces. Les marques de Markdown ne peuvent plus
  apparaître à l'écran, quelle que soit la réponse du modèle.
- Le dialogue adopte des coins arrondis cohérents avec le reste de
  l'application.

## Mise à jour

Désinstallation inutile : l'APK s'installe par-dessus la version précédente et
conserve les données.
