package com.diabeto.desktop

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistiques de glycemie pour la fiche patient (onglets Vue generale,
 * Profil glycemique, Carnet). Calculees sur des mesures ponctuelles
 * (glycemies capillaires) : les pourcentages sont des % de mesures,
 * pas un temps mesure par capteur continu.
 */
object StatsGlycemie {

    /** Plages du consensus international (temps dans la cible). */
    enum class Plage(val libelle: String, val couleur: Long) {
        TRES_HAUT("> 250", 0xFFFB8C00),
        HAUT("181-250", 0xFFF9A825),
        CIBLE("70-180", 0xFF43A047),
        BAS("54-69", 0xFFE53935),
        TRES_BAS("< 54", 0xFF8E0000);

        companion object {
            fun de(v: Double) = when {
                v < 54 -> TRES_BAS
                v < 70 -> BAS
                v <= 180 -> CIBLE
                v <= 250 -> HAUT
                else -> TRES_HAUT
            }
        }
    }

    data class Resume(
        val n: Int,
        val moyenne: Double,
        val ecartType: Double,
        val cv: Double,
        val gmi: Double,
        val hba1cEstimee: Double,
        val min: Double,
        val max: Double,
        val lbgi: Double,
        val hbgi: Double,
        val parPlage: Map<Plage, Double>,
        val mesuresParJour: Double
    )

    fun resume(valeurs: List<Double>, jours: Int): Resume? {
        if (valeurs.isEmpty()) return null
        val n = valeurs.size
        val moy = valeurs.average()
        val sd = if (n > 1) sqrt(valeurs.sumOf { (it - moy).pow(2) } / (n - 1)) else 0.0
        // Indices de risque (Kovatchev) : f(g) = 1,509 x (ln(g)^1,084 - 5,381)
        val risques = valeurs.map { g ->
            val f = 1.509 * (ln(g.coerceAtLeast(20.0)).pow(1.084) - 5.381)
            val r = 10 * f * f
            if (f < 0) r to 0.0 else 0.0 to r
        }
        return Resume(
            n = n,
            moyenne = moy,
            ecartType = sd,
            cv = if (moy > 0) sd / moy * 100 else 0.0,
            gmi = 3.31 + 0.02392 * moy,
            hba1cEstimee = (moy + 46.7) / 28.7,
            min = valeurs.min(),
            max = valeurs.max(),
            lbgi = risques.map { it.first }.average(),
            hbgi = risques.map { it.second }.average(),
            parPlage = Plage.entries.associateWith { p -> valeurs.count { Plage.de(it) == p } * 100.0 / n },
            mesuresParJour = n.toDouble() / jours.coerceAtLeast(1)
        )
    }

    /** Percentile (0-100) d'une liste triee. */
    fun percentile(tries: List<Double>, p: Double): Double {
        if (tries.isEmpty()) return 0.0
        val pos = (p / 100.0) * (tries.size - 1)
        val bas = pos.toInt()
        val haut = (bas + 1).coerceAtMost(tries.size - 1)
        return tries[bas] + (tries[haut] - tries[bas]) * (pos - bas)
    }

    /** Profil par tranche de 2 h : (heure de debut, p10, p25, mediane, p75, p90, nombre). */
    data class Tranche(val heure: Int, val p10: Double, val p25: Double, val mediane: Double, val p75: Double, val p90: Double, val n: Int)

    fun profilHoraire(mesures: List<Mesure>): List<Tranche> =
        (0 until 24 step 2).mapNotNull { h ->
            val v = mesures.filter { it.date.hour in h until h + 2 }.map { it.valeur }.sorted()
            if (v.isEmpty()) null
            else Tranche(h, percentile(v, 10.0), percentile(v, 25.0), percentile(v, 50.0), percentile(v, 75.0), percentile(v, 90.0), v.size)
        }

    // ── Carnet : colonnes par moment de la journee ─────────────────────

    enum class Moment(val groupe: String, val libelle: String, val avantRepas: Boolean?) {
        NUIT("Nuit", "", null),
        REVEIL("Réveil", "À jeun", true),
        PD_AVANT("Petit-déjeuner", "Avant", true),
        PD_APRES("Petit-déjeuner", "Après", false),
        DEJ_AVANT("Déjeuner", "Avant", true),
        DEJ_APRES("Déjeuner", "Après", false),
        DIN_AVANT("Dîner", "Avant", true),
        DIN_APRES("Dîner", "Après", false),
        COUCHER("Coucher", "", null),
        AUTRE("Autre", "Activité / autre", null)
    }

    /** Range une mesure dans une colonne du carnet (contexte saisi + heure). */
    fun moment(m: Mesure): Moment {
        val h = m.date.hour
        fun repas(avant: Boolean) = when {
            h < 11 -> if (avant) Moment.PD_AVANT else Moment.PD_APRES
            h < 16 -> if (avant) Moment.DEJ_AVANT else Moment.DEJ_APRES
            else -> if (avant) Moment.DIN_AVANT else Moment.DIN_APRES
        }
        return when (m.contexte) {
            "REVEIL", "A_JEUN" -> Moment.REVEIL
            "AVANT_REPAS" -> repas(true)
            "APRES_REPAS_1H", "APRES_REPAS_2H" -> repas(false)
            "AU_LIT" -> Moment.COUCHER
            else -> if (h < 5) Moment.NUIT else Moment.AUTRE
        }
    }

    /**
     * Couleur d'une case du carnet : rouge fonce < 54, rouge < 70,
     * vert dans l'objectif (a jeun / avant repas 80-130, apres repas < 180,
     * autres 70-180), jaune au-dessus, orange > 250.
     */
    fun couleurCase(v: Double, moment: Moment): Long = when {
        v < 54 -> Plage.TRES_BAS.couleur
        v < 70 -> Plage.BAS.couleur
        v > 250 -> Plage.TRES_HAUT.couleur
        moment.avantRepas == true -> if (v <= 130) Plage.CIBLE.couleur else Plage.HAUT.couleur
        else -> if (v <= 180) Plage.CIBLE.couleur else Plage.HAUT.couleur
    }

    fun libelleContexte(code: String) = when (code) {
        "A_JEUN" -> "À jeun"
        "AVANT_REPAS" -> "Avant repas"
        "APRES_REPAS_1H" -> "Après repas (1 h)"
        "APRES_REPAS_2H" -> "Après repas (2 h)"
        "AVANT_EXERCICE" -> "Avant exercice"
        "APRES_EXERCICE" -> "Après exercice"
        "AU_LIT" -> "Au coucher"
        "REVEIL" -> "Au réveil"
        "" -> "—"
        else -> "Autre"
    }
}
