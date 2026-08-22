# DiaSmart v2.1.77

## Corriger le nom d'un plat corrige enfin les chiffres

Jusqu'ici, modifier le nom du repas après l'analyse ne changeait que l'étiquette
enregistrée. Les glucides, l'index glycémique et les calories restaient ceux du
plat mal reconnu. On croyait avoir corrigé son repas alors que les valeurs
décrivaient toujours autre chose — et ce sont ces valeurs qui servent à ajuster
le traitement.

- Un bouton **« Recalculer avec ce nom »** apparaît dès que le nom est modifié,
  accompagné d'un avertissement expliquant que les valeurs affichées décrivent
  encore le plat proposé par ROLLY. L'estimation nutritionnelle est refaite à
  partir du nom corrigé, qui n'est plus réécrit par le modèle.
- Si le plat est décrit **avant** de prendre la photo, ce nom est transmis comme
  information certaine : le modèle le reprend tel quel et n'utilise l'image que
  pour estimer les portions et les valeurs nutritionnelles. Le patient
  identifie, le modèle compte.

## Le cache ne masque plus les corrections

L'application conservait pendant six heures la réponse associée à un nom de
plat. Une analyse erronée restait donc servie telle quelle après une correction
côté serveur : on relançait l'analyse et on relisait l'ancienne réponse, en
concluant que le correctif n'avait rien changé. La version du prompt entre
désormais dans la clé du cache, ce qui purge les entrées périmées à chaque
révision.

## Catalogue de plats structuré

Le lexique en prose est remplacé par un catalogue où chaque plat répond aux
mêmes quatre questions : couleur, forme, texture, accompagnements. Chaque entrée
porte aussi les plats avec lesquels on la confond et le signe unique qui
tranche.

Descriptions corrigées à partir d'observations de terrain :

- **Couscous de tapioca** : jaune, allongé, légèrement granulé.
- **Couscous de maïs** : jaune ou blanc laiteux, en boule.
- **Couscous de manioc** : blanc cassé, en boule.
- **Fufu** : allongé comme le tapioca, mais lisse et brillant.
- **Water fufu** : blanc à blanc laiteux, en cylindre lisse.
- **Bâton de manioc** : long, avec des nœuds visibles sur la longueur. Pas de
  nœud, pas de bâton de manioc — c'est ce qui le sépare du water fufu.
- **Eru et ndolè** : lanières longues et surface brillante d'huile de palme pour
  l'eru, morceaux courts et surface mate épaissie par l'arachide pour le ndolè.
  L'eru est presque toujours servi avec du water fufu ou du fufu, ce qui vaut
  indice.

Le catalogue couvre également l'Afrique de l'Ouest, du Nord et de l'Est ainsi
que les plats occidentaux et internationaux courants. Ces corrections sont côté
serveur et s'appliquent immédiatement, sans attendre la mise à jour.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
