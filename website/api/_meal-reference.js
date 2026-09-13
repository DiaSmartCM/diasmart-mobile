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
    forme: "EN BOULE, en dome ou en cone faconne a la main",
    texture: "compacte",
    accompagnements: "sauces de legumes, sauces de feuilles, gombo, et surtout le nkui : une boule de couscous de mais blanc a cote d'une sauce brune gluante est le plat de l'Ouest par excellence",
    confusions: [
      { avec: "couscous de tapioca", signe: "la forme : le mais est une boule, le tapioca est allonge" },
      { avec: "couscous de manioc", signe: "meme boule ; le manioc est blanc casse, le mais est jaune ou blanc laiteux" },
      { avec: "fufu", signe: "la forme : une BOULE ronde est un couscous, le fufu est servi allonge" },
      { avec: "manioc bouilli", signe: "le couscous est une pate homogene moulee en boule, sans fibre ; le manioc bouilli est un morceau de racine fibreux, fendu, aux bords anguleux" },
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
    couleur: "BLANC LAITEUX, un peu gris ou translucide, moins blanc que le manioc bouilli",
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
      { avec: "manioc bouilli", signe: "le water fufu est une pate moulee, cylindre regulier et lisse ; le manioc bouilli est un morceau de racine coupe au couteau : forme irreguliere, bords anguleux, fibres et fentes visibles dans la longueur" },
    ],
    source: "terrain",
  },
  { nom: "Achu", famille: "Feculents", couleur: "pate blanche", forme: "tas lisse creuse au centre", texture: "lisse et elastique", accompagnements: "sauce jaune tres huileuse, la couleur jaune de la sauce est caracteristique" },
  { nom: "Plantain mur frit (alloco)", famille: "Feculents", couleur: "dore a brun", forme: "tranches ovales ou biseautees", texture: "bords caramelises, moelleux", accompagnements: "haricots, poisson, soya" },
  { nom: "Plantain vert bouilli", famille: "Feculents", couleur: "jaune pale mat", forme: "gros morceaux", texture: "ferme", accompagnements: "ndole, sauces" },
  {
    nom: "Banane malaxee (plantain malaxe)",
    famille: "Feculents",
    couleur: "brun-orange a caramel avec la pate d'arachide, ou rouge-orange avec l'huile de palme",
    forme: "masse epaisse en tas dans laquelle on distingue des formes ALLONGEES enrobees : ce sont les morceaux de plantain",
    texture: "cremeuse et pateuse, plantain en partie ecrase et melange a la sauce, morceaux de poisson fume dedans",
    accompagnements: "AVOCAT pose a cote tres souvent, c'est un indice fort",
    confusions: [
      { avec: "sauce d'arachide", signe: "les formes allongees enrobees sont du plantain melange au plat : c'est un feculent complet, pas une sauce seule. L'avocat a cote penche pour la banane malaxee" },
    ],
    source: "terrain",
  },
  {
    nom: "Legumes verts sautes",
    famille: "Plats a feuilles",
    couleur: "vert fonce, avec parfois des morceaux orange ou rouges (piment, tomate, oignon)",
    forme: "petit tas d'accompagnement",
    texture: "feuilles coupees GROSSIEREMENT, tiges visibles, luisantes d'huile, SANS liant d'arachide",
    accompagnements: "couscous de mais et nkui",
    confusions: [
      { avec: "ndole", signe: "le ndole est une masse epaisse et mate liee a l'arachide, en petits morceaux ; les legumes sautes sont des feuilles et tiges distinctes, sans liant" },
    ],
    source: "terrain",
  },
  { nom: "Plantain braise", famille: "Feculents", couleur: "jaune avec stries noires de grill", forme: "moities allongees", texture: "ferme", accompagnements: "soya, poisson braise" },
  { nom: "Taro", famille: "Feculents", couleur: "blanc a violace", forme: "morceaux ou pile", texture: "ferme ou pateuse", accompagnements: "sauce jaune" },
  { nom: "Macabo, igname, patate douce", famille: "Feculents", couleur: "blanc a jaune pale", forme: "gros morceaux", texture: "ferme", accompagnements: "sauces, huile de palme" },
  {
    nom: "Manioc bouilli (morceaux de tubercule)",
    famille: "Feculents",
    couleur: "BLANC FRANC et opaque, parfois un peu jaunatre ; plus blanc que le baton de manioc, qui est blanc laiteux",
    forme: "MORCEAUX DE RACINE coupes au couteau : troncons ou quartiers allonges, IRREGULIERS, bords anguleux, souvent fendus dans la longueur",
    texture: "FIBREUSE et farineuse : fibres, fentes et craquelures visibles dans la longueur, parfois la fibre centrale. Ce n'est PAS une pate lisse",
    accompagnements: "okok surtout, ndole, sauces de feuilles, poisson",
    confusions: [
      { avec: "couscous (mais ou manioc)", signe: "le couscous est une pate homogene moulee en boule ; le manioc bouilli garde la forme du tubercule et montre ses fibres" },
      { avec: "water fufu et fufu", signe: "ce sont des pates lisses et regulieres ; le manioc bouilli a des bords anguleux, des fentes et des fibres" },
      { avec: "baton de manioc", signe: "le baton est une pate lisse enveloppee, marquee de noeuds ; le manioc bouilli est un morceau de racine fibreux, sans noeud" },
    ],
    source: "terrain",
  },
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
    couleur: "vert vif, plus vif que le ndole, qui baigne dans BEAUCOUP d'huile de palme rouge-orange : l'huile luit partout et forme souvent une flaque au bord de l'assiette",
    forme: "masse souple",
    texture: "feuilles en LONGUES LANIERES FINES melangees au waterleaf, surface tres LUISANTE et grasse, pas de liant epais (ni arachide)",
    accompagnements: "water fufu ou fufu presque systematiquement — c'est un indice fort",
    confusions: [
      { avec: "ndole", signe: "lanieres longues et surface brillante pour l'eru ; morceaux courts et surface mate pour le ndole. L'huile de palme rouge visible penche pour l'eru" },
      { avec: "okok", signe: "c'est la MEME feuille, seule la preparation change. L'eru reste VERT VIF, on reconnait les feuilles de waterleaf et l'huile de palme luit ; l'okok est VERT TRES FONCE tirant sur le BRUN ou le kaki, en masse compacte liee par l'arachide ou par la pulpe de noix de palme. Brun-vert et compact, ce n'est pas de l'eru" },
    ],
    source: "terrain",
  },
  {
    nom: "Okok (mfumbua)",
    famille: "Plats a feuilles",
    couleur: "VERT TRES FONCE tirant sur le BRUN ou le kaki, jamais vert vif ; BEAUCOUP d'huile de palme rouge qui deborde souvent sur les bords",
    forme: "masse compacte en tas, qui se tient",
    texture: "feuilles hachees EXTREMEMENT fin, presque en fils, collees en grumeaux par la pate d'arachide ou par la pulpe de noix de palme ; aucune feuille entiere visible. Plat TRES RICHE EN LIPIDES : arachide ou pulpe de noix de palme, ET beaucoup d'huile rouge, les lipides d'une assiette sont eleves",
    accompagnements: "MANIOC BOUILLI en morceaux ou baton de manioc, c'est l'association habituelle",
    confusions: [
      { avec: "eru", signe: "meme feuille, preparation differente : l'eru est vert vif et huileux, avec des feuilles de waterleaf reconnaissables, et se mange avec du water fufu ; l'okok est brun-vert fonce, compact, lie par l'arachide ou la pulpe de noix de palme, et se mange avec du manioc" },
      { avec: "ndole", signe: "le ndole est vert fonce MAT en petits morceaux de feuille distincts ; l'okok est plus brun, en fils tres fins colles en masse" },
      { avec: "okok sucre", signe: "l'okok sale contient de la viande ou du poisson visibles ; sans viande ni poisson, c'est un okok sucre" },
    ],
    source: "terrain",
  },
  {
    nom: "Okok sucre",
    famille: "Plats a feuilles",
    couleur: "meme aspect que l'okok : vert tres fonce tirant sur le brun, beaucoup d'huile de palme rouge",
    forme: "masse compacte en tas",
    texture: "feuilles hachees tres fin, liees par l'arachide ou la pulpe de noix de palme, AUCUN morceau de viande ni de poisson : le liant est sucre",
    accompagnements: "manioc bouilli ou baton de manioc",
    confusions: [
      { avec: "okok sale", signe: "le sucre ne se voit pas : c'est l'ABSENCE de viande et de poisson qui signale l'okok sucre. Ses glucides incluent le sucre ajoute, en plus du manioc" },
    ],
    source: "terrain",
  },
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
      { avec: "nkui", signe: "le mbongo est NOIR et HUILEUX, avec du poisson ou de la viande en morceaux ; le nkui est BRUN, sans couche d'huile, et GLUANT (il file). Une boule de couscous de mais a cote indique le nkui" },
    ],
    source: "terrain",
  },
  {
    nom: "Sauce d'arachide (nnam owondo, mafe)",
    famille: "Sauces",
    couleur: "brun-orange a caramel, opaque, jamais noire",
    forme: "sauce nappante qui recouvre les morceaux",
    texture: "onctueuse, cremeuse et epaisse, parfois legerement granuleuse en surface ; aucune feuille visible",
    accompagnements: "poisson fume ou viande dans la sauce ; riz, baton de manioc, manioc bouilli, plantain servi A PART",
    confusions: [
      { avec: "banane malaxee", signe: "dans la banane malaxee, des morceaux ALLONGES de plantain enrobes sont melanges a la masse et l'avocat est souvent pose a cote ; une sauce d'arachide nappe du poisson ou de la viande, le feculent etant servi a part" },
      { avec: "nkui", signe: "la sauce d'arachide est cremeuse et opaque, souvent avec du poisson fume ; le nkui est gluant et filant, servi avec une boule de couscous" },
      { avec: "okok et eru", signe: "la sauce d'arachide ne contient AUCUNE feuille ; l'okok et l'eru sont des masses de feuilles hachees" },
    ],
    source: "terrain",
  },
  {
    nom: "Nkui",
    famille: "Sauces",
    couleur: "BRUN, du brun moyen au brun fonce selon les epices, mais JAMAIS noir d'encre",
    forme: "sauce epaisse servie a cote d'une boule de couscous",
    texture: "tres VISQUEUSE et FILANTE, aspect gluant et nappant, surface lisse SANS couche d'huile, peu ou pas de morceaux visibles",
    accompagnements: "COUSCOUS DE MAIS en boule (blanc ou jaune) presque toujours ; parfois des legumes verts sautes a cote, qui ne sont pas du ndole",
    confusions: [
      { avec: "mbongo tchobi", signe: "le mbongo est NOIR, luisant d'huile, accompagne de poisson ou de viande, servi avec plantain ou baton de manioc ; le nkui est brun, gluant, sans huile en surface, servi avec du couscous de mais. Sur une photo sombre, la boule de couscous et l'aspect filant tranchent pour le nkui" },
    ],
    source: "terrain",
  },
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
  { nom: "Safou (prune du Cameroun)", famille: "Afrique", couleur: "violet fonce a bleu-noir, chair vert pale a l'interieur", forme: "fruit OVALE allonge de 5 a 8 cm, entier ou fendu", texture: "peau lisse et luisante, chair tendre et grasse une fois cuite", accompagnements: "manioc bouilli, mais grille, plantain ou pain. Fruit TRES RICHE EN LIPIDES : chaque fruit cuit apporte une part importante de graisses, a compter pour chaque safou visible", confusions: [ { avec: "aubergine ou taro violet", signe: "le safou est un petit fruit ovale entier a peau luisante, pas un legume coupe" } ], source: "terrain" },
  { nom: "Avocat", famille: "Afrique", couleur: "chair vert-jaune pale, peau verte a violet fonce", forme: "fruit coupe en deux, souvent avec le creux du noyau visible, ou en tranches", texture: "chair lisse, beurree et onctueuse", accompagnements: "banane malaxee, pain, riz, plats de feuilles. Fruit TRES RICHE EN LIPIDES, surtout des graisses mono-insaturees favorables au diabetique ; glucides tres faibles, index glycemique tres bas", source: "terrain" },
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
