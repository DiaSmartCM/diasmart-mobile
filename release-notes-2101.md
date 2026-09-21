# DiaSmart v2.1.101

## La voix de ROLLY

Le chat vocal parlait comme un robot. Trois causes, corrigees.

L'application laissait Android choisir le moteur de synthese. Sur beaucoup de
telephones vendus ici, ce moteur est celui du constructeur, avec ses vieilles
voix a formants. DiaSmart demande maintenant le moteur Google quand il est
installe.

Ensuite, la selection de la voix classait par qualite annoncee avant de
regarder si la voix etait reellement presente sur le telephone. Une voix
listee mais non telechargee pouvait donc gagner : l'appareil l'acceptait, puis
la lecture retombait sur la voix par defaut, la plus pauvre. Ces voix absentes
sont desormais ecartees.

Enfin, les voix neuronales de Google, les seules qui sonnent vraiment humaines,
demandent une connexion. Elles passent maintenant en premier. Si le reseau
tombe pendant la lecture, l'application rebascule sur la meilleure voix
presente sur le telephone et rejoue la phrase.

## Voix naturelle, en option

Un nouvel interrupteur dans les reglages, sous le son de demarrage : la voix
naturelle. Activee, les reponses de ROLLY sont dites par une voix de synthese
generee en ligne, nettement plus proche d'une voix humaine.

Elle est desactivee par defaut, et cela se justifie. Elle consomme des donnees
mobiles, demande du reseau, et puise dans un quota partage par tous les
patients. La voix du telephone reste donc le comportement normal : gratuite,
hors connexion, disponible partout. Quand la voix naturelle echoue, pour
quelque raison que ce soit, la voix du telephone prend le relais dans la
seconde. Un quota epuise ne doit jamais se traduire par du silence.

## Reconnaissance des plats

Le couscous de tapioca est de la semoule de manioc. Sa couleur jaune vient de
l'huile de palme, pas du grain : prepare sans huile, il est blanc. La couleur
ne suffit donc pas a le reconnaitre, et deux rouleaux allonges a cote d'un plat
de feuilles vertes sont du couscous de tapioca, pas du water fufu.

Le fufu et le water fufu sont le meme aliment a la preparation pres, tous deux
a base de manioc. Les confondre ne change rien a l'estimation des glucides :
ROLLY ne perd plus de temps a trancher entre les deux.
