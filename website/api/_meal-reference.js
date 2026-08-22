// Catalogue des plats servant a l'identification visuelle (ROLLY vision).
//
// Pourquoi un fichier a part
// --------------------------
// Le lexique vivait en prose au milieu du prompt. Chaque correction le
// reecrivait en entier, et rien ne garantissait que deux plats voisins soient
// decrits avec la meme grille — on finissait par comparer une couleur a une
// texture. Ici chaque plat repond aux memes quatre questions : de quelle
// couleur, de quelle forme, quelle texture, servi avec quoi. Ce sont les
// criteres qu'un cuisinier utilise reellement pour reconnaitre un plat.
//
// `confusions` est la partie la plus utile : elle nomme le plat avec lequel
// celui-ci est confondu ET le signe unique qui tranche. C'est ce que le modele
// doit verifier avant de se decider.
//
// `source: "terrain"` marque les descriptions verifiees par un utilisateur
// camerounais. Elles priment sur toute description generique : plusieurs
// entrees ont ete corrigees ainsi apres des erreurs constatees dans l'app.
//
// Pour enrichir le catalogue : ajouter une entree ci-dessous. Le prompt se
// reconstruit tout seul, aucune autre modification n'est necessaire.

const PLATS = [
  // ── Feculents : c'est la FORME qui tranche en premier, pas la couleur ──
  {
    nom: "Couscous de tapioca",
    famille: "Feculents",
    couleur: "jaune",
    forme: "servi en forme allongee, pas en boule",
    texture: "legerement granule, on distingue le grain",
    accompagnements: "sauces en general, souvent avec du poisson",
    confusions: [
      { avec: "baton de manioc", signe: "le tapioca est jaune et granule ; le baton de manioc est blanc et parfaitement lisse, sans aucun grain" },
      { avec: "couscous de mais", signe: "le tapioca est allonge ; le couscous de mais est une boule" },
      { avec: "fufu", signe: "meme allure generale, mais le fufu est lisse et brillant alors que le tapioca est granule et mat" },
    ],
    source: "terrain",
  },
  {
    nom: "Fufu",
    famille: "Feculents",
    couleur: "clair, blanc a jaune pale",
    forme: "allongee, proche du couscous de tapioca",
    texture: "LISSE et BRILLANTE, aucun grain visible",
    accompagnements: "eru, okok, sauces de feuilles",
    confusions: [
      { avec: "couscous de tapioca", signe: "la surface : le fufu brille et reste lisse, le tapioca est mat et granule" },
    ],
    source: "terrain",
  },
  {
    nom: "Couscous de mais",
    famille: "Feculents",
    couleur: "jaune, ou blanc laiteux selon la preparation — les deux existent",
    forme: "EN BOULE",
    texture: "compacte",
    accompagnements: "sauces de legumes, sauces de feuilles, gombo",
    confusions: [
      { avec: "couscous de tapioca", signe: "la forme : le mais est une boule, le tapioca est allonge" },
      { avec: "couscous de manioc", signe: "meme boule ; le manioc est blanc casse, le mais est jaune ou blanc laiteux" },
    ],
    source: "terrain",
  },
  {
    nom: "Couscous de manioc",
    famille: "Feculents",
    couleur: "blanc casse",
    forme: "EN BOULE",
    texture: "compacte",
    accompagnements: "sauces diverses",
    confusions: [
      { avec: "couscous de mais", signe: "meme forme en boule ; le manioc est blanc casse, le mais tire vers le jaune ou le blanc laiteux" },
    ],
    source: "terrain",
  },
  {
    nom: "Baton de manioc (bobolo, miondo)",
    famille: "Feculents",
    couleur: "blanc",
    forme: "LONG, avec des NOEUDS visibles le long du baton — les ligatures marquent la pate et la decoupent en segments. C'est le signe le plus sur",
    texture: "compacte et lisse, aucun grain",
    accompagnements: "poisson braise, ndole, sauce d'arachide, soya",
    confusions: [
      { avec: "couscous de tapioca", signe: "s'il y a des grains visibles ou une teinte jaune, ce n'est PAS du baton de manioc" },
      { avec: "water fufu", signe: "les deux sont blancs et allonges ; seul le baton de manioc porte des noeuds le long de sa longueur. Pas de noeud, pas de baton de manioc" },
    ],
    source: "terrain",
  },
  {
    nom: "Water fufu",
    famille: "Feculents",
    couleur: "blanc, parfois blanc laiteux",
    forme: "EN CYLINDRE, lisse et regulier, SANS noeud",
    texture: "molle et lisse",
    accompagnements: "eru presque toujours — l'association est un indice tres fort",
    confusions: [
      { avec: "baton de manioc", signe: "le baton de manioc porte des noeuds, le water fufu est un cylindre lisse sans ligature" },
    ],
    source: "terrain",
  },
  { nom: "Achu", famille: "Feculents", couleur: "pate blanche", forme: "tas lisse creuse au centre", texture: "lisse et elastique", accompagnements: "sauce jaune tres huileuse, la couleur jaune de la sauce est caracteristique" },
  { nom: "Plantain mur frit (alloco)", famille: "Feculents", couleur: "dore a brun", forme: "tranches ovales ou biseautees", texture: "bords caramelises, moelleux", accompagnements: "haricots, poisson, soya" },
  { nom: "Plantain vert bouilli", famille: "Feculents", couleur: "jaune pale mat", forme: "gros morceaux", texture: "ferme", accompagnements: "ndole, sauces" },
  { nom: "Plantain braise", famille: "Feculents", couleur: "jaune avec stries noires de grill", forme: "moities allongees", texture: "ferme", accompagnements: "soya, poisson braise" },
  { nom: "Taro", famille: "Feculents", couleur: "blanc a violace", forme: "morceaux ou pile", texture: "ferme ou pateuse", accompagnements: "sauce jaune" },
  { nom: "Macabo, igname, patate douce", famille: "Feculents", couleur: "blanc a jaune pale", forme: "gros morceaux", texture: "ferme", accompagnements: "sauces, huile de palme" },
  { nom: "Riz blanc", famille: "Feculents", couleur: "blanc", forme: "grains separes", texture: "grains distincts", accompagnements: "toutes sauces" },
  { nom: "Gari, attieke", famille: "Feculents", couleur: "blanc a jaune pale", forme: "granules fins et secs", texture: "sableuse, grains tres fins", accompagnements: "poisson, sauce claire" },

  // ── Plats a feuilles : tous VERTS, la coupe des feuilles les separe ──
  {
    nom: "Ndole",
    famille: "Plats a feuilles",
    couleur: "vert fonce mat",
    forme: "masse epaisse dans l'assiette",
    texture: "granuleuse, feuilles hachees en PETITS MORCEAUX courts, liees par la pate d'arachide qui l'epaissit et la rend mate",
    accompagnements: "plantain, baton de manioc, riz ; viande, poisson fume ou crevettes",
    confusions: [
      { avec: "eru", signe: "la coupe des feuilles et l'aspect de surface : le ndole a des morceaux courts et une surface MATE epaissie par l'arachide ; l'eru a de longues lanieres fines et une surface LUISANTE d'huile de palme rouge. En cas de doute, regarder l'accompagnement : l'eru va presque toujours avec du water fufu ou du fufu" },
    ],
    source: "terrain",
  },
  {
    nom: "Eru",
    famille: "Plats a feuilles",
    couleur: "vert, plus vif que le ndole, avec des reflets rouges d'huile de palme",
    forme: "masse souple",
    texture: "feuilles en LONGUES LANIERES FINES melangees au waterleaf, surface LUISANTE et huileuse, pas de liant epais",
    accompagnements: "water fufu ou fufu presque systematiquement — c'est un indice fort",
    confusions: [
      { avec: "ndole", signe: "lanieres longues et surface brillante pour l'eru ; morceaux courts et surface mate pour le ndole. L'huile de palme rouge visible penche pour l'eru" },
    ],
    source: "terrain",
  },
  { nom: "Okok (mfumbua)", famille: "Plats a feuilles", couleur: "vert", forme: "masse souple", texture: "lanieres encore plus fines que l'eru, souvent avec des graines de courge", accompagnements: "baton de manioc" },
  { nom: "Kpem (mbem, feuilles de manioc pilees)", famille: "Plats a feuilles", couleur: "vert olive", forme: "puree homogene", texture: "puree sans morceaux distincts — c'est ce qui la separe du ndole et de l'eru", accompagnements: "riz, baton de manioc" },
  { nom: "Sanga", famille: "Plats a feuilles", couleur: "vert avec des grains jaunes bien visibles", forme: "melange", texture: "grains de mais entiers melanges aux feuilles", accompagnements: "plat complet" },
  { nom: "Feuilles de patate, folon, zom", famille: "Plats a feuilles", couleur: "vert", forme: "sauce legere", texture: "feuilles entieres ou peu hachees, sauce peu epaisse", accompagnements: "riz, feculents" },
  { nom: "Koki", famille: "Plats a feuilles", couleur: "orange-ocre", forme: "bloc ferme portant l'empreinte de la feuille de bananier", texture: "ferme, compacte, pate de haricots", accompagnements: "plantain" },
  { nom: "Mets de pistache", famille: "Plats a feuilles", couleur: "brun-orange", forme: "cuit et servi en feuille", texture: "pateuse et grasse", accompagnements: "baton de manioc, plantain" },

  // ── Sauces : la COULEUR DE FOND tranche ──
  {
    nom: "Mbongo tchobi (mbongo)",
    famille: "Sauces",
    couleur: "NOIRE ou brun tres fonce, presque encre",
    forme: "sauce liquide a nappante",
    texture: "surface huileuse et lisse, AUCUN morceau de feuille visible",
    accompagnements: "poisson, viande ou pattes de boeuf ; plantain, baton de manioc",
    confusions: [
      { avec: "ndole et eru", signe: "la couleur : le mbongo est noir, le ndole et l'eru sont verts. Une sauce noire n'est jamais un plat de feuilles" },
    ],
    source: "terrain",
  },
  { nom: "Sauce d'arachide (nnam owondo, mafe)", famille: "Sauces", couleur: "brun-orange", forme: "sauce nappante", texture: "onctueuse et epaisse, jamais noire", accompagnements: "riz, baton de manioc, viande" },
  { nom: "Nkui", famille: "Sauces", couleur: "brun clair", forme: "sauce", texture: "tres visqueuse et filante, aspect gluant", accompagnements: "plat de fete, viande" },
  { nom: "Sauce gombo (okra)", famille: "Sauces", couleur: "verte", forme: "sauce", texture: "visqueuse et filante", accompagnements: "fufu, couscous" },
  { nom: "Sauce tomate", famille: "Sauces", couleur: "rouge-orange", forme: "sauce", texture: "lisse ou avec des morceaux de tomate", accompagnements: "riz, pates, viande" },
  { nom: "Sauce jaune", famille: "Sauces", couleur: "jaune vif et tres huileuse", forme: "sauce", texture: "huileuse, se separe", accompagnements: "achu, taro" },

  // ── Viandes et poissons ──
  { nom: "Poisson braise", famille: "Viandes et poissons", couleur: "brun dore avec marques de grill", forme: "poisson entier ouvert en deux", texture: "peau striee et croustillante", accompagnements: "plantain, baton de manioc, piment" },
  { nom: "Soya (brochettes)", famille: "Viandes et poissons", couleur: "brun-rouge d'epices", forme: "brochettes ou lanieres", texture: "grillee, seche en surface", accompagnements: "plantain, oignons" },
  { nom: "Poulet DG", famille: "Viandes et poissons", couleur: "dore et colore", forme: "melange en assiette", texture: "morceaux de poulet, plantain frit dore et legumes en des", accompagnements: "plat complet" },
  { nom: "Poulet ou viande en sauce", famille: "Viandes et poissons", couleur: "selon la sauce", forme: "morceaux en sauce", texture: "variable", accompagnements: "riz, feculents" },

  // ── Afrique de l'Ouest, du Nord et de l'Est ──
  { nom: "Jollof rice", famille: "Afrique", couleur: "orange-rouge uniforme", forme: "riz en grains separes", texture: "grains colores dans la masse par la tomate", accompagnements: "poulet, plantain" },
  { nom: "Thieboudienne, riz gras", famille: "Afrique", couleur: "brun-orange", forme: "riz avec gros legumes poses dessus", texture: "riz gras, legumes entiers (chou, carotte, manioc)", accompagnements: "poisson" },
  { nom: "Yassa", famille: "Afrique", couleur: "blond dore", forme: "abondance d'oignons fondus", texture: "oignons fondants, sauce citronnee", accompagnements: "riz, poulet ou poisson" },
  { nom: "Egusi", famille: "Afrique", couleur: "jaune-vert", forme: "sauce granuleuse", texture: "granuleuse, graines de courge moulues", accompagnements: "fufu, eba" },
  { nom: "Amala", famille: "Afrique", couleur: "brun tres fonce", forme: "boule lisse", texture: "lisse, elastique", accompagnements: "ewedu, gbegiri" },
  { nom: "Eba", famille: "Afrique", couleur: "jaune pale", forme: "boule", texture: "granuleuse (gari)", accompagnements: "soupes nigerianes" },
  { nom: "Akara, beignets de haricots", famille: "Afrique", couleur: "dore", forme: "boulettes rondes", texture: "frite, croustillante dehors", accompagnements: "bouillie, haricots" },
  { nom: "Couscous marocain", famille: "Afrique", couleur: "jaune pale", forme: "semoule fine en dome", texture: "grains tres fins et separes", accompagnements: "legumes en quartiers, bouillon, viande" },
  { nom: "Tajine", famille: "Afrique", couleur: "variable", forme: "plat conique en terre", texture: "viande et legumes fondus", accompagnements: "olives, citron confit, pain" },
  { nom: "Injera", famille: "Afrique", couleur: "gris-beige", forme: "grande galette plate", texture: "spongieuse et alveolee", accompagnements: "tas de sauces colorees poses dessus (wat, misir, doro)" },
  { nom: "Ugali", famille: "Afrique", couleur: "blanc", forme: "bloc compact", texture: "lisse et ferme", accompagnements: "sukuma wiki, viande" },

  // ── Occidental et international ──
  { nom: "Pates (spaghetti, penne, tagliatelles)", famille: "Occidental", couleur: "jaune pale, sauce variable", forme: "longs fils ou tubes", texture: "selon la sauce : bolognaise rouge-brun avec viande hachee, carbonara creme pale avec lardons, napolitaine rouge lisse", accompagnements: "fromage rape" },
  { nom: "Pizza", famille: "Occidental", couleur: "rouge et dore", forme: "disque plat", texture: "pate cuite, fromage fondu, garnitures visibles", accompagnements: "aucun" },
  { nom: "Frites", famille: "Occidental", couleur: "dore", forme: "batonnets reguliers", texture: "croustillante", accompagnements: "viande, poisson, sauce" },
  { nom: "Puree de pommes de terre", famille: "Occidental", couleur: "blanc creme", forme: "tas lisse", texture: "lisse et onctueuse", accompagnements: "viande en sauce" },
  { nom: "Gratin", famille: "Occidental", couleur: "surface doree", forme: "plat rectangulaire", texture: "surface gratinee, interieur fondant", accompagnements: "salade" },
  { nom: "Escalope panee", famille: "Occidental", couleur: "brun dore", forme: "tranche plate", texture: "croute de chapelure", accompagnements: "frites, riz, salade" },
  { nom: "Steak, roti, poulet roti", famille: "Occidental", couleur: "brun exterieur", forme: "piece de viande", texture: "grillee ou rotie", accompagnements: "feculent, legumes" },
  { nom: "Omelette, oeufs", famille: "Occidental", couleur: "jaune pale", forme: "disque plat ou brouilles", texture: "moelleuse", accompagnements: "pain, spaghetti" },
  { nom: "Sandwich, burger, pain", famille: "Occidental", couleur: "brun dore", forme: "baguette, pain rond, pain de mie", texture: "mie et croute", accompagnements: "frites, garnitures" },
  { nom: "Salade composee", famille: "Occidental", couleur: "vert et multicolore", forme: "melange cru en saladier", texture: "feuilles crues, tomate, concombre, mais, thon", accompagnements: "vinaigrette" },
  { nom: "Legumes cuits", famille: "Occidental", couleur: "variable", forme: "morceaux", texture: "haricots verts, petits pois, carottes, ratatouille rouge fondue", accompagnements: "viande, feculent" },
  { nom: "Lentilles, haricots en sauce", famille: "Occidental", couleur: "brun, corail ou rouge", forme: "graines en sauce", texture: "graines distinctes fondantes", accompagnements: "riz, pain" },
  { nom: "Quinoa, boulgour, semoule", famille: "Occidental", couleur: "beige clair", forme: "petites billes ou grains", texture: "le quinoa montre un germe en spirale visible", accompagnements: "legumes, viande" },
  { nom: "Riz saute, nouilles sautees", famille: "Occidental", couleur: "brun dore", forme: "melange saute", texture: "grains ou nouilles avec des des de legumes", accompagnements: "sauce soja, poulet" },
  { nom: "Bouillie de mais ou de mil", famille: "Occidental", couleur: "beige clair", forme: "liquide epais en bol", texture: "lisse et fluide", accompagnements: "beignets, pain" },
  { nom: "Fruits frais", famille: "Occidental", couleur: "variable", forme: "morceaux ou entiers", texture: "banane, mangue, papaye, ananas, orange, pasteque, avocat", accompagnements: "aucun" },
  { nom: "Gateau, tarte, crepes, yaourt", famille: "Occidental", couleur: "variable", forme: "part ou portion", texture: "pate cuite, creme, laitage", accompagnements: "aucun" },
];

/** Rend une entree du catalogue en une ligne lisible par le modele. */
function ligne(p) {
  const bouts = [
    `couleur ${p.couleur}`,
    `forme ${p.forme}`,
    `texture ${p.texture}`,
  ];
  if (p.accompagnements) bouts.push(`sert avec ${p.accompagnements}`);
  let txt = `- ${p.nom} : ${bouts.join(" ; ")}.`;
  if (p.confusions) {
    for (const c of p.confusions) {
      txt += `\n    A NE PAS CONFONDRE AVEC ${c.avec} — ${c.signe}.`;
    }
  }
  return txt;
}

/** Assemble le catalogue complet, groupe par famille. */
function buildCatalogue() {
  const familles = [];
  for (const p of PLATS) {
    let f = familles.find((x) => x.nom === p.famille);
    if (!f) { f = { nom: p.famille, plats: [] }; familles.push(f); }
    f.plats.push(p);
  }
  return familles
    .map((f) => `── ${f.nom} ──\n${f.plats.map(ligne).join("\n")}`)
    .join("\n\n");
}

module.exports = { PLATS, buildCatalogue };
