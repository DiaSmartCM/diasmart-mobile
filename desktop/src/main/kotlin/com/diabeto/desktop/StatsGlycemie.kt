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
        TRES_HAUT("> 250", 0xFFD32F2F),
        HAUT("181-250", 0xFFF57C00),
        CIBLE("70-180", 0xFF2E7D32),
        BAS("54-69", 0xFF1E88E5),
        TRES_BAS("< 54", 0xFF6A1B9A);

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

    /** Indice ADRR (Kovatchev) : moyenne par jour du risque bas maximal + risque haut maximal. */
    fun adrr(mesures: List<Mesure>): Double? {
        val parJour = mesures.groupBy { it.date.date }.values
        if (parJour.isEmpty()) return null
        return parJour.map { jour ->
            val r = jour.map { risque(it.valeur) }
            r.maxOf { it.first } + r.maxOf { it.second }
        }.average()
    }

    /** (risque bas, risque haut) d'une glycemie, echelle de Kovatchev. */
    private fun risque(g: Double): Pair<Double, Double> {
        val f = 1.509 * (ln(g.coerceAtLeast(20.0)).pow(1.084) - 5.381)
        val r = 10 * f * f
        return if (f < 0) r to 0.0 else 0.0 to r
    }

    // ── Carnet : colonnes par moment de la journee ─────────────────────

    /**
     * Reperes de couleur par moment (ADA 2025, reperes generaux) :
     * < 54 et 54-69 = hypoglycemie ; cible jusqu'a [cibleMax] ; [hautMax] = au-dessus.
     */
    enum class Categorie(val libelle: String, val cibleMax: Int, val hautMax: Int) {
        NUIT_REVEIL("Nuit / au réveil", 130, 180),
        AVANT_REPAS("Avant repas", 130, 180),
        APRES_REPAS("Après repas", 180, 250),
        COUCHER("Au coucher", 150, 200),
        AUTRE("Autres moments", 180, 250);

        /** Libelles des 5 niveaux, du plus bas au plus haut. */
        val seuils: List<Pair<String, Plage>> get() = listOf(
            "< 54" to Plage.TRES_BAS, "54-69" to Plage.BAS, "70-$cibleMax" to Plage.CIBLE,
            "${cibleMax + 1}-$hautMax" to Plage.HAUT, "> $hautMax" to Plage.TRES_HAUT
        )

        fun niveau(v: Double): Plage = when {
            v < 54 -> Plage.TRES_BAS
            v < 70 -> Plage.BAS
            v <= cibleMax -> Plage.CIBLE
            v <= hautMax -> Plage.HAUT
            else -> Plage.TRES_HAUT
        }
    }

    enum class Moment(val libelle: String, val categorie: Categorie, val repas: Boolean = false) {
        NUIT("Nuit", Categorie.NUIT_REVEIL),
        PETIT_DEJ("Petit-déjeuner", Categorie.AVANT_REPAS, repas = true),
        MATINEE("Matinée · après repas", Categorie.APRES_REPAS),
        DEJEUNER("Déjeuner", Categorie.AVANT_REPAS, repas = true),
        APRES_MIDI("Après-midi · après repas", Categorie.APRES_REPAS),
        DINER("Dîner", Categorie.AVANT_REPAS, repas = true),
        SOIREE("Soirée · après repas", Categorie.APRES_REPAS),
        COUCHER("Coucher", Categorie.COUCHER),
        AUTRE("Autre", Categorie.AUTRE)
    }

    /** Range une mesure dans une colonne du carnet (contexte saisi par le patient + heure). */
    fun moment(m: Mesure): Moment {
        val h = m.date.hour
        return when (m.contexte) {
            "REVEIL", "A_JEUN" -> Moment.PETIT_DEJ
            "AVANT_REPAS" -> repasA(h)
            "APRES_REPAS_1H", "APRES_REPAS_2H" -> when {
                h < 12 -> Moment.MATINEE
                h < 18 -> Moment.APRES_MIDI
                else -> Moment.SOIREE
            }
            "AU_LIT" -> Moment.COUCHER
            else -> if (h < 5) Moment.NUIT else Moment.AUTRE
        }
    }

    /** Repas le plus proche d'une heure : petit-dejeuner, dejeuner ou diner. */
    fun repasA(heure: Int): Moment = when {
        heure < 11 -> Moment.PETIT_DEJ
        heure < 17 -> Moment.DEJEUNER
        else -> Moment.DINER
    }

    /** Couleur d'une case du carnet selon les reperes du moment. */
    fun couleurCase(v: Double, moment: Moment): Long = moment.categorie.niveau(v).couleur

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
