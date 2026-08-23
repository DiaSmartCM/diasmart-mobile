# DiaSmart v2.1.85

## Récupérer les dossiers masqués par la mise à jour précédente

La version 2.1.84 a séparé les données par compte. Les dossiers créés avant
cette séparation n'appartenaient à aucun compte : ils ont été conservés mais
masqués, volontairement — attribuer automatiquement des données de santé au
compte connecté au moment d'une mise à jour aurait été plus risqué encore.

Conséquence non anticipée : les rendez-vous et les traitements rattachés à ces
dossiers ont disparu eux aussi, et **les rappels correspondants ont cessé de
sonner**, la liste qui les alimente ne renvoyant plus rien.

Une carte apparaît maintenant dans les Paramètres lorsque de tels dossiers
existent. Elle indique combien sont en attente et permet de les rattacher au
compte connecté. Les rappels sont reprogrammés dans la foulée, sans avoir à
relancer l'application.

L'opération reste manuelle : seul l'utilisateur sait si ces dossiers sont les
siens. Sur un téléphone où l'application a servi à plusieurs personnes, un
compte médecin récupérerait sinon des données saisies par un patient.

## Le médecin voit les analyses de repas

Lorsqu'un patient faisait analyser un repas, son médecin recevait un simple
message de notification. Il n'avait aucun moyen de vérifier l'estimation ni de
signaler une erreur.

C'est pourtant là que cela compte le plus : le patient ajuste son alimentation
sur une quantité de glucides estimée par un modèle, sans savoir si elle est
juste.

Chaque analyse enregistrée est désormais transmise au médecin traitant pour
avis, avec le détail complet — plat identifié, glucides, index et charge
glycémiques, calories, macronutriments, score et impact annoncé. Le médecin
peut l'approuver ou signaler une erreur par commentaire.

L'analyse transmise est celle que le patient a validée, corrections comprises,
et non la proposition initiale du modèle. Les valeurs enregistrées ne sont pas
modifiées par l'avis du médecin : elles restent celles que le patient a
confirmées, et le commentaire les accompagne.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
