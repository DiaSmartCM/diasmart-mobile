# public-site

Source de la page d'accueil du projet Vercel **public**
(`public-one-omega-88.vercel.app`), l'adresse ou l'application ouvre ses
documents legaux depuis l'ecran de consentement.

Seuls `index.html` et `sw.js` sont versionnes ici. `privacy.html`,
`terms.html`, `license.html`, `app.html`, `manifest.json` et les images vivent
sur le deploiement Vercel et n'ont pas de source dans ce depot : les recuperer
avant tout redeploiement, sinon ils disparaissent.

    mkdir /tmp/public-site && cd /tmp/public-site
    for f in privacy.html terms.html license.html app.html manifest.json \
             sw.js diasmart-logo.png phone-mockup.png; do
      curl -sO "https://public-one-omega-88.vercel.app/$f"
    done
    cp <ce-dossier>/index.html <ce-dossier>/sw.js .
    vercel link --project public --yes && vercel deploy --prod --yes

Un deploiement Vercel est un instantane complet : un fichier absent du dossier
est un fichier supprime du site. C'est ainsi que les vieux binaires
`downloads/DiaSmart.apk` et `.zip` (v1.9.3) ont ete retires le 21/09/2026.

Ne pas supprimer ce projet Vercel : `ConsentScreen.kt` ouvre
`privacy.html` et `terms.html` a cette adresse, en dur.
