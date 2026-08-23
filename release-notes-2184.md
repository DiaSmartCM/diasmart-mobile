# DiaSmart v2.1.84

## Un patient ne voit plus les rendez-vous des autres

Sur un appareil ayant servi à plusieurs comptes, la liste des prochains
rendez-vous affichait tout ce que contenait le téléphone — y compris les
rendez-vous pris par d'autres patients auprès du même médecin.

La version précédente avait cloisonné les dossiers patients, mais elle s'était
arrêtée là : les rendez-vous, glycémies, traitements, HbA1c et entrées du
carnet étaient restés communs à l'appareil. Le cloisonnement couvre désormais
l'ensemble.

Une donnée médicale appartient au compte qui possède le dossier patient auquel
elle se rattache. Aucune table n'a été modifiée : le filtrage s'appuie sur la
propriété déjà établie, ce qui évite d'autres migrations de la base chiffrée.

### Deux fuites découvertes en achevant le travail

**Les alarmes de traitement.** La requête qui alimente les rappels ne
distinguait pas les comptes. Sur un téléphone partagé, l'application aurait
sonné pour le traitement d'un autre utilisateur, en affichant le nom du
médicament sur l'écran verrouillé.

**La sauvegarde cloud.** La collecte envoyait vers le cloud toutes les données
présentes sur l'appareil, sans distinction de compte. Les données d'un
utilisateur pouvaient donc se retrouver dans la sauvegarde d'un autre. C'est la
seule des trois qui sortait du téléphone.

## Le médecin peut retirer une demande d'accès

Une demande envoyée à un patient et restée sans réponse se rangeait parmi les
accès révoqués, où le seul bouton proposé était « réactiver ». Le médecin ne
pouvait pas se rétracter.

Les demandes en attente ont maintenant leur propre section, avec un bouton
**Annuler** : la demande disparaît et le patient cesse de la voir.

Un accès déjà accordé ne peut pas être supprimé par ce chemin. Il passe par
« Retirer l'accès », qui conserve une trace horodatée de la révocation — une
suppression effacerait la preuve du consentement et de son retrait.

## Mise à jour

L'APK s'installe par-dessus la version précédente et conserve les données.
