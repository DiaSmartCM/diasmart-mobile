# DiaSmart v2.1.80

## Les médecins redeviennent visibles partout

Un patient inscrit sur la plateforme web ne voyait aucun médecin : la liste
affichait « Aucun médecin disponible ». La cause tenait à un détail d'écriture.

L'application enregistre le rôle sous le nom de son énumération interne, en
majuscules (`MEDECIN`), tandis que la plateforme web l'enregistre en minuscules
(`medecin`). Chacune interrogeait ensuite dans sa propre casse. Les médecins
étaient donc répartis en deux moitiés invisibles l'une à l'autre, et comme la
plupart s'inscrivent depuis l'application, la liste de la plateforme restait
vide.

Le même écart produisait une erreur plus discrète et plus gênante : dans
l'application, la conversion du rôle échouait sur la forme minuscule et
retombait silencieusement sur « patient ». **Tout médecin inscrit depuis la
plateforme devenait donc un patient dans l'application** — privé de ses écrans,
absent des listes, sans aucun message d'erreur.

Les deux formes sont désormais acceptées de part et d'autre : à la lecture du
profil, dans les cinq requêtes de l'application qui filtrent par rôle (liste des
médecins, communauté, partage de données, rendez-vous partagés, validations) et
dans les sept comparaisons de la plateforme. Les comptes existants continuent de
fonctionner sans modification.

## Le bouton « Prédire » donne enfin une prévision

Il ouvrait un texte rédigé par l'assistant : des conseils, jamais une
prédiction. Il ouvre maintenant un bulletin chiffré, présenté comme une
prévision météo.

- La glycémie du moment et le pic attendu, avec son heure.
- Six échéances horaires, chacune avec sa valeur et une pastille de couleur
  lisible d'un coup d'œil.
- Une barre de fiabilité qui pâlit à mesure que l'horizon s'éloigne : une
  prévision à six heures vaut moins qu'une prévision à une heure, et cela se
  voit.

Les valeurs proviennent du modèle d'excursion calculé sur l'appareil, donc sans
connexion. Le commentaire de ROLLY subsiste, mais sous les chiffres.

## Numéro WhatsApp

Un numéro noté avec un zéro initial, forme la plus courante dans un carnet
d'adresses, conservait ce zéro et n'était reconnu par aucun cas de la
normalisation. Il partait tel quel vers WhatsApp, qui n'identifiait alors aucun
destinataire.

La normalisation gère maintenant le zéro d'acheminement, le préfixe
international sous ses trois formes, ainsi que les espaces et la ponctuation.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données. Les
corrections de la plateforme web sont déjà actives et ne nécessitent aucune
mise à jour.
