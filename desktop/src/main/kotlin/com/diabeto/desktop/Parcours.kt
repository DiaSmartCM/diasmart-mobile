package com.diabeto.desktop

/**
 * Parcours d'education du patient (page d'accueil de la version PC).
 * Fiches courtes, en langage simple, que le soignant peut lire avec le
 * patient ou lui envoyer par la messagerie. Reperes generaux (ADA 2025,
 * OMS) : les objectifs de chaque patient sont fixes par son medecin.
 */
data class Fiche(val titre: String, val paragraphes: List<String>) {
    /** Texte envoye au patient par la messagerie. */
    val texteMessage: String get() = (listOf("📘 $titre") + paragraphes + AVERTISSEMENT_PARCOURS).joinToString("\n\n")
}

data class Parcours(val titre: String, val fiches: List<Fiche>)

const val AVERTISSEMENT_PARCOURS =
    "Information générale pour vous aider au quotidien : elle ne remplace pas l'avis de votre médecin, qui fixe vos objectifs."

val PARCOURS: List<Parcours> = listOf(
    Parcours("Comprendre le diabète", listOf(
        Fiche("Qu'est-ce que le diabète ?", listOf(
            "Le diabète, c'est trop de sucre (glucose) dans le sang pendant longtemps. Le glucose vient surtout de ce que l'on mange et sert d'énergie au corps.",
            "Pour faire entrer le glucose dans les cellules, le corps a besoin d'une hormone fabriquée par le pancréas : l'insuline. Quand elle manque ou agit mal, le sucre reste dans le sang.",
            "On parle de diabète quand la glycémie à jeun est d'au moins 126 mg/dL (contrôlée deux fois), ou quand l'HbA1c est d'au moins 6,5 %.",
            "Bien suivi, le diabète permet une vie normale. Sans suivi, l'excès de sucre abîme peu à peu les yeux, les reins, les nerfs, le cœur et les pieds."
        )),
        Fiche("Les différents types de diabète", listOf(
            "Type 1 : le pancréas ne fabrique plus d'insuline. Il apparaît souvent chez l'enfant ou le jeune adulte. L'insuline est indispensable chaque jour.",
            "Type 2 : le plus fréquent. Le corps utilise mal son insuline et finit par en manquer. Il est favorisé par le surpoids, la sédentarité et l'hérédité. Il se traite par l'alimentation, l'activité physique, des comprimés et parfois de l'insuline.",
            "Diabète gestationnel : il apparaît pendant la grossesse (dépisté vers 24 à 28 semaines). Il disparaît souvent après l'accouchement, mais augmente le risque de diabète de type 2 plus tard : un contrôle est conseillé après la naissance.",
            "Prédiabète : glycémie à jeun entre 100 et 125 mg/dL ou HbA1c entre 5,7 et 6,4 %. Ce n'est pas encore un diabète, et perdre un peu de poids et bouger plus permet souvent de l'éviter."
        )),
        Fiche("L'HbA1c : le résultat des 3 derniers mois", listOf(
            "L'HbA1c (hémoglobine glyquée) est une prise de sang qui reflète la moyenne de la glycémie des 2 à 3 derniers mois.",
            "Pour beaucoup d'adultes, l'objectif est une HbA1c inférieure à 7 %. Votre médecin peut fixer un objectif différent selon votre âge et votre santé.",
            "Elle se contrôle tous les 3 mois quand l'objectif n'est pas atteint ou que le traitement change, et au moins 2 fois par an quand tout est stable.",
            "Repère : une HbA1c de 7 % correspond à une glycémie moyenne d'environ 154 mg/dL."
        ))
    )),
    Parcours("Surveiller sa glycémie", listOf(
        Fiche("Quand mesurer sa glycémie ?", listOf(
            "Le bon rythme dépend de votre traitement : votre médecin vous le précise. Avec de l'insuline, on mesure en général avant les repas et au coucher.",
            "Mesurez aussi en cas de malaise, avant de conduire si vous prenez de l'insuline ou certains comprimés, et quand vous êtes malade (fièvre, diarrhée, vomissements).",
            "Pour une mesure fiable : mains lavées à l'eau et au savon puis bien séchées, piqûre sur le côté du bout du doigt, bandelettes non périmées.",
            "Notez chaque mesure dans DiaSmart avec le moment (à jeun, avant ou après le repas, coucher) : votre soignant voit ainsi votre carnet."
        )),
        Fiche("Les objectifs de glycémie", listOf(
            "Repères généraux pour un adulte : avant les repas entre 80 et 130 mg/dL ; 1 à 2 heures après le début du repas, moins de 180 mg/dL.",
            "En dessous de 70 mg/dL, c'est une hypoglycémie ; en dessous de 54 mg/dL, elle est importante et doit être corrigée tout de suite.",
            "Au-dessus de 250 mg/dL, contrôlez à nouveau et suivez les conseils de la fiche « Hyperglycémie ».",
            "Ces repères peuvent être assouplis (personne âgée, fragile) ou plus stricts (grossesse) : suivez les objectifs fixés par votre médecin."
        )),
        Fiche("Bien utiliser le carnet DiaSmart", listOf(
            "Dans l'application, ajoutez chaque glycémie avec son moment. Le carnet range les valeurs par repas et les colore : vert dans l'objectif, orange au-dessus, rouge très au-dessus, bleu et violet en dessous.",
            "Ajoutez aussi vos mesures de tension, votre HbA1c et votre journal (sommeil, humeur, activité) : votre soignant a ainsi une vue complète.",
            "Partagez vos données avec votre médecin ou votre centre de santé depuis l'application : vous pouvez arrêter le partage quand vous voulez."
        ))
    )),
    Parcours("Hypoglycémie et hyperglycémie", listOf(
        Fiche("Reconnaître une hypoglycémie", listOf(
            "L'hypoglycémie, c'est une glycémie inférieure à 70 mg/dL. Elle concerne surtout les personnes sous insuline ou sous certains comprimés (sulfamides).",
            "Signes fréquents : tremblements, sueurs, faim soudaine, cœur qui bat vite, pâleur, vertiges, fatigue, troubles de la vue, nervosité, difficulté à se concentrer.",
            "Causes habituelles : repas sauté ou trop léger, effort physique inhabituel, dose de traitement trop forte, alcool à jeun.",
            "Au moindre doute, mesurez votre glycémie. Si vous ne pouvez pas mesurer, resucrez-vous quand même."
        )),
        Fiche("Se resucrer : la règle des 15", listOf(
            "Prenez 15 g de sucre rapide : 3 morceaux de sucre, ou 1 cuillère à soupe de miel, ou 150 mL de jus de fruit ou de soda (non « light »).",
            "Attendez 15 minutes au repos, puis mesurez de nouveau. Si la glycémie est encore sous 70 mg/dL, reprenez 15 g de sucre.",
            "Quand elle est remontée, prenez une collation (pain, fruit) si le prochain repas est loin.",
            "Si la personne est inconsciente ou ne peut pas avaler : ne rien lui donner par la bouche, la mettre sur le côté (position latérale de sécurité), faire du glucagon si un proche est formé, et appeler les secours."
        )),
        Fiche("Hyperglycémie : que faire ?", listOf(
            "L'hyperglycémie, c'est trop de sucre dans le sang. Signes possibles : soif intense, bouche sèche, urines fréquentes, fatigue, vue trouble.",
            "Au-dessus de 250 mg/dL : buvez de l'eau, contrôlez de nouveau quelques heures plus tard et cherchez la cause (repas, oubli de traitement, infection, stress).",
            "Avec un diabète de type 1, recherchez l'acétone (bandelettes urinaires ou capteur de cétones) si la glycémie dépasse 250 mg/dL.",
            "Consultez en urgence en cas de vomissements, douleurs au ventre, respiration rapide, somnolence, ou si la glycémie reste très élevée malgré le traitement."
        ))
    )),
    Parcours("Bien manger avec le diabète", listOf(
        Fiche("Les glucides", listOf(
            "Les glucides sont les aliments qui font monter la glycémie : riz, pain, pâtes, manioc, plantain, igname, macabo, maïs, pommes de terre, fruits, sucre et boissons sucrées.",
            "Ils ne sont pas interdits : le corps en a besoin. L'important est la quantité, et de les répartir sur les repas de la journée.",
            "Préférez les féculents complets et riches en fibres, et accompagnez-les de légumes : la glycémie monte moins vite.",
            "Avec l'application, prenez votre repas en photo pour estimer ses glucides."
        )),
        Fiche("L'assiette équilibrée", listOf(
            "Une moitié de l'assiette en légumes (feuilles, gombo, aubergine, haricots verts, tomates…).",
            "Un quart en protéines : poisson, poulet sans peau, œufs, haricots, arachides en petite quantité.",
            "Un quart en féculents : riz, plantain, manioc, igname, pain…",
            "Mangez à heures régulières, sans sauter de repas, et limitez la friture et l'huile."
        )),
        Fiche("Boissons et sucres cachés", listOf(
            "L'eau est la meilleure boisson. Les sodas, jus de fruits et boissons sucrées font monter très vite la glycémie : gardez-les pour corriger une hypoglycémie.",
            "Attention aux sucres cachés : biscuits, beignets, confiture, lait concentré sucré, sauces industrielles.",
            "L'alcool peut provoquer une hypoglycémie plusieurs heures après, surtout à jeun : si vous en buvez, toujours pendant un repas et avec modération."
        ))
    )),
    Parcours("Bouger au quotidien", listOf(
        Fiche("Pourquoi l'activité physique aide", listOf(
            "Bouger fait baisser la glycémie, aide l'insuline à mieux agir, fait baisser la tension et aide à garder un poids sain.",
            "Objectif pour un adulte : au moins 150 minutes par semaine d'activité modérée (marche rapide, vélo, danse), par exemple 30 minutes 5 jours par semaine.",
            "Évitez de rester assis longtemps : levez-vous et marchez quelques minutes toutes les 30 minutes.",
            "Le podomètre de l'application compte vos pas : votre soignant voit votre activité dans votre journal."
        )),
        Fiche("Activité et hypoglycémie", listOf(
            "Avec de l'insuline ou des sulfamides, l'effort peut faire baisser la glycémie pendant et jusqu'à plusieurs heures après.",
            "Mesurez votre glycémie avant l'effort et gardez toujours du sucre sur vous.",
            "Si la glycémie est sous 100 mg/dL avant l'effort, prenez une collation. Évitez l'effort intense si elle dépasse 250 mg/dL avec de l'acétone.",
            "Demandez à votre médecin s'il faut adapter vos doses les jours d'activité."
        ))
    )),
    Parcours("Tension artérielle et cœur", listOf(
        Fiche("Mesurer sa tension à la maison", listOf(
            "La règle des 3 : 3 mesures le matin, 3 mesures le soir, 3 jours de suite.",
            "Assis, au calme, après 5 minutes de repos, le dos appuyé, le bras posé sur la table au niveau du cœur, sans parler.",
            "Pas de café, de tabac ni d'effort dans les 30 minutes avant. Notez chaque mesure dans DiaSmart.",
            "À la maison, une tension moyenne de 135/85 mmHg ou plus est trop élevée : parlez-en à votre médecin."
        )),
        Fiche("Les objectifs de tension", listOf(
            "Avec un diabète, l'objectif est le plus souvent une tension inférieure à 130/80 mmHg, si elle est bien supportée.",
            "Votre médecin peut fixer un objectif personnel, que vous voyez dans l'application.",
            "Une tension de 180/110 mmHg ou plus, surtout avec mal de tête, douleur dans la poitrine, essoufflement ou trouble de la parole, demande un avis médical en urgence."
        )),
        Fiche("Moins de sel", listOf(
            "Le sel fait monter la tension. L'OMS conseille moins de 5 g de sel par jour, soit environ une cuillère à café.",
            "Le sel se cache dans les cubes d'assaisonnement, le poisson fumé ou salé, les conserves, la charcuterie et le pain.",
            "Goûtez avant de saler, et remplacez une partie du sel par des épices, de l'ail, de l'oignon ou des herbes."
        ))
    )),
    Parcours("Mes traitements", listOf(
        Fiche("Prendre ses médicaments chaque jour", listOf(
            "Prenez vos médicaments à heure fixe, même quand vous vous sentez bien : le diabète et la tension ne se sentent souvent pas.",
            "Utilisez les rappels de l'application pour ne pas oublier.",
            "N'arrêtez jamais un traitement et ne changez pas les doses sans en parler à votre médecin. En cas d'effet gênant, signalez-le.",
            "Gardez la liste de vos médicaments à jour dans l'application : votre soignant la voit."
        )),
        Fiche("L'insuline : conservation et injection", listOf(
            "Les stylos et flacons non entamés se gardent au réfrigérateur, entre 2 et 8 °C, jamais au congélateur.",
            "Le stylo ou flacon en cours se garde à température ambiante, à l'abri de la chaleur et du soleil, et s'utilise en général dans les 4 semaines (voir la notice). Sans réfrigérateur, un canari ou un pot en terre humide aide à le garder au frais.",
            "Changez de point d'injection à chaque fois (ventre, cuisses, bras, fesses) et laissez quelques centimètres entre deux piqûres : cela évite les boules sous la peau (lipodystrophies) qui gênent l'action de l'insuline.",
            "Utilisez une aiguille neuve à chaque injection et jetez-la dans une boîte fermée."
        ))
    )),
    Parcours("Prévenir les complications", listOf(
        Fiche("Prendre soin de ses pieds", listOf(
            "Le diabète peut diminuer la sensibilité des pieds : une petite blessure peut passer inaperçue et s'infecter.",
            "Regardez vos pieds chaque jour (avec un miroir si besoin), lavez-les à l'eau tiède et séchez bien entre les orteils.",
            "Ne marchez pas pieds nus, vérifiez l'intérieur des chaussures avant de les mettre, coupez les ongles droits et ne retirez pas les cors vous-même.",
            "Montrez rapidement toute plaie, ampoule, rougeur ou changement de couleur à un soignant."
        )),
        Fiche("Les examens à faire chaque année", listOf(
            "Fond d'œil : il repère tôt les atteintes de la rétine.",
            "Prise de sang et urines : fonction des reins (créatinine) et albumine dans les urines, cholestérol.",
            "Examen des pieds par un soignant, contrôle chez le dentiste.",
            "HbA1c tous les 3 à 6 mois et tension à chaque consultation. Votre médecin vous dira si d'autres examens sont utiles (cœur, par exemple)."
        )),
        Fiche("Tabac et alcool", listOf(
            "Le tabac abîme les vaisseaux sanguins et augmente beaucoup le risque d'infarctus, d'AVC et de problèmes aux pieds chez les personnes diabétiques.",
            "Arrêter de fumer est l'un des gestes les plus utiles : demandez de l'aide à votre médecin.",
            "L'alcool fait varier la glycémie et peut provoquer des hypoglycémies : à limiter, et jamais à jeun."
        ))
    ))
)
