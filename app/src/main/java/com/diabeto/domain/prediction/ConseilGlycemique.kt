package com.diabeto.domain.prediction

/**
 * Conseils rattaches a un niveau de glycemie, calcules sur le telephone.
 *
 * Deux raisons de ne pas confier cela au modele de langage :
 *
 *  1. Ces messages doivent partir meme sans reseau — c'est la situation
 *     habituelle, pas l'exception. Une notification qui echoue faute de 4G ne
 *     sert a rien.
 *  2. Un seuil clinique ne se reformule pas a chaque appel. En dessous de
 *     54 mg/dL la conduite a tenir est fixe ; elle ne doit pas dependre de la
 *     temperature d'echantillonnage d'un modele.
 *
 * ROLLY garde son role : commenter, nuancer, repondre aux questions. Les
 * seuils, eux, sont ici.
 *
 * LIMITE ASSUMEE : aucun de ces conseils ne touche aux doses. Activite,
 * hydratation, composition du repas, moment de mesure, quand consulter — oui.
 * Ajuster un traitement releve du medecin.
 */
object ConseilGlycemique {

    enum class Niveau { HYPO_SEVERE, HYPO, CIBLE, ELEVEE, TRES_ELEVEE }

    data class Conseil(
        val niveau: Niveau,
        val titre: String,
        val message: String,
        val urgent: Boolean = false,
    )

    /** Seuils ADA, en mg/dL. */
    const val HYPO_SEVERE = 54.0
    const val HYPO = 70.0
    const val CIBLE_HAUTE_POST = 180.0
    const val TRES_ELEVEE = 250.0

    fun niveauDe(glycemie: Double): Niveau = when {
        glycemie < HYPO_SEVERE -> Niveau.HYPO_SEVERE
        glycemie < HYPO -> Niveau.HYPO
        glycemie <= CIBLE_HAUTE_POST -> Niveau.CIBLE
        glycemie <= TRES_ELEVEE -> Niveau.ELEVEE
        else -> Niveau.TRES_ELEVEE
    }

    /**
     * Conseil rattache a une glycemie constatee.
     */
    fun pour(glycemie: Double): Conseil = when (niveauDe(glycemie)) {
        Niveau.HYPO_SEVERE -> Conseil(
            Niveau.HYPO_SEVERE,
            "Hypoglycemie severe",
            "Prends 15 a 20 g de sucre rapide tout de suite, puis remesure dans " +
                "15 minutes. Si tu te sens partir ou si quelqu'un perd connaissance, " +
                "appelle le 119.",
            urgent = true,
        )
        Niveau.HYPO -> Conseil(
            Niveau.HYPO,
            "Glycemie basse",
            "Prends 15 g de sucre rapide — trois morceaux de sucre, un demi-verre " +
                "de jus — et remesure dans 15 minutes avant toute activite.",
            urgent = true,
        )
        Niveau.CIBLE -> Conseil(
            Niveau.CIBLE,
            "Dans la cible",
            "Rien a signaler. Continue comme tu fais.",
        )
        Niveau.ELEVEE -> Conseil(
            Niveau.ELEVEE,
            "Glycemie elevee",
            "Bois de l'eau et marche 15 a 20 minutes si tu le peux : le muscle " +
                "capte le glucose sans passer par l'insuline. Remesure dans deux heures.",
        )
        Niveau.TRES_ELEVEE -> Conseil(
            Niveau.TRES_ELEVEE,
            "Glycemie tres elevee",
            "Bois de l'eau et remesure dans une heure. Si le chiffre ne baisse pas, " +
                "ou si tu as des nausees, des vomissements ou une respiration rapide, " +
                "consulte sans attendre.",
            urgent = true,
        )
    }

    /**
     * Conseil rattache a une excursion PREVUE, avant qu'elle ne survienne.
     * C'est la seule fenetre ou une action change encore quelque chose.
     */
    fun pourExcursionPrevue(excursion: GlucosePrediction.Excursion): Conseil? {
        val pic = excursion.valeurPic
        val montee = excursion.monteePic

        // Une montee modeste vers une valeur correcte ne merite pas d'alerte.
        if (pic <= CIBLE_HAUTE_POST && montee < 40) return null

        return when {
            pic > TRES_ELEVEE -> Conseil(
                Niveau.TRES_ELEVEE,
                "Forte montee attendue",
                "Ce repas devrait faire monter ta glycemie vers " +
                    "${GlucosePrediction.arrondiAffichage(pic)} mg/dL. Une marche de " +
                    "20 minutes dans l'heure qui vient reduirait nettement ce pic.",
            )
            pic > CIBLE_HAUTE_POST -> Conseil(
                Niveau.ELEVEE,
                "Depassement attendu",
                "Ta glycemie devrait passer au-dessus de 180 mg/dL " +
                    "(environ ${GlucosePrediction.arrondiAffichage(pic)}). Bouger un peu " +
                    "apres le repas aide a aplatir la montee.",
            )
            else -> Conseil(
                Niveau.CIBLE,
                "Montee notable",
                "Montee attendue d'environ ${GlucosePrediction.arrondiAffichage(montee)} " +
                    "mg/dL, sans depasser la cible. Rien d'inquietant.",
            )
        }
    }
}
