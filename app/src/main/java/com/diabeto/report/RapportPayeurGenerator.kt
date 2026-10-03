package com.diabeto.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.SuiviPatient
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rapport de resultats ANONYMISE pour le payeur (assureur, mutuelle,
 * programme, employeur) d'un etablissement.
 *
 * Que des chiffres agreges : aucun nom, aucun identifiant, aucune date
 * individuelle. Un effectif de 1 a 4 patients s'affiche "< 5", et un
 * pourcentage n'est donne que si le groupe compte au moins 5 patients, pour
 * qu'on ne puisse pas reconnaitre une personne dans un petit centre.
 */
class RapportPayeurGenerator(private val context: Context) {

    data class Indicateurs(
        val inscrits: Int,
        val actifs30j: Int,
        val perdusDeVue: Int,
        val avecHbA1c6mois: Int,
        val hba1cMoyenne: Double?,
        val hba1cSous7: Int,
        val hba1cAuDessus9: Int,
        val avecHypoSevere30j: Int,
        val glycemieMoyenne30j: Double?,
        val prioriteHaute: Int,
        val prioriteMoyenne: Int,
        val prioriteBasse: Int,
        val prioriteInconnue: Int
    )

    companion object {
        const val SEUIL_PETIT_EFFECTIF = 5

        fun calculer(suivis: List<SuiviPatient>, aujourdHui: LocalDate = LocalDate.now()): Indicateurs {
            val il6mois = aujourdHui.minusMonths(6)
            // Seules les HbA1c de laboratoire comptent pour le payeur
            val hba1cRecentes = suivis.filter {
                it.derniereHbA1c != null && !it.hba1cEstimee && it.dateHbA1c != null && !it.dateHbA1c.isBefore(il6mois)
            }
            val valeurs = hba1cRecentes.mapNotNull { it.derniereHbA1c }
            val moyennes = suivis.mapNotNull { it.moyenne30j }
            return Indicateurs(
                inscrits = suivis.size,
                actifs30j = suivis.count { it.nbMesures30j > 0 },
                perdusDeVue = suivis.count { it.perduDeVue },
                avecHbA1c6mois = hba1cRecentes.size,
                hba1cMoyenne = valeurs.takeIf { it.size >= SEUIL_PETIT_EFFECTIF }?.average(),
                hba1cSous7 = valeurs.count { it < 7.0 },
                hba1cAuDessus9 = valeurs.count { it >= 9.0 },
                avecHypoSevere30j = suivis.count { it.nbHyposSeveres30j > 0 },
                glycemieMoyenne30j = moyennes.takeIf { it.size >= SEUIL_PETIT_EFFECTIF }?.average(),
                prioriteHaute = suivis.count { it.priorite == PrioriteSuivi.HAUTE },
                prioriteMoyenne = suivis.count { it.priorite == PrioriteSuivi.MOYENNE },
                prioriteBasse = suivis.count { it.priorite == PrioriteSuivi.BASSE },
                prioriteInconnue = suivis.count { it.priorite == PrioriteSuivi.INCONNUE }
            )
        }

        /** Effectif publie : "< 5" pour 1 a 4, pour eviter de reconnaitre quelqu'un. */
        fun effectif(n: Int): String = if (n in 1 until SEUIL_PETIT_EFFECTIF) "< $SEUIL_PETIT_EFFECTIF" else n.toString()

        fun pourcentage(n: Int, total: Int): String =
            if (total < SEUIL_PETIT_EFFECTIF) "n.d." else "${(n * 100.0 / total).toInt()} %"
    }

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f

    private val titlePaint = Paint().apply {
        color = Color.parseColor("#3F3D8A"); textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val sectionPaint = Paint().apply {
        color = Color.parseColor("#2C2A5E"); textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10.5f; isAntiAlias = true }
    private val boldPaint = Paint(bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val mutedPaint = Paint().apply { color = Color.parseColor("#6A6A82"); textSize = 9f; isAntiAlias = true }
    private val rulePaint = Paint().apply { color = Color.parseColor("#D4D4DC"); strokeWidth = 0.6f }

    fun generer(etab: Etablissement, nbSoignants: Int, suivis: List<SuiviPatient>): File {
        val ind = calculer(suivis)
        val aujourdHui = LocalDate.now()
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val c = page.canvas
        var y = margin + 10f

        c.drawText("DiaSmart : rapport de resultats", margin, y, titlePaint); y += 22f
        c.drawText("${etab.nom}${if (etab.ville.isNotBlank()) " (${etab.ville})" else ""}", margin, y, sectionPaint); y += 16f
        c.drawText(
            "Edite le ${aujourdHui.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE))}. " +
                "Donnees anonymisees et agregees : aucun nom ni identifiant de patient.",
            margin, y, mutedPaint
        ); y += 10f
        c.drawLine(margin, y, pageWidth - margin, y, rulePaint); y += 22f

        fun ligne(libelle: String, valeur: String) {
            c.drawText(libelle, margin, y, bodyPaint)
            c.drawText(valeur, pageWidth - margin - boldPaint.measureText(valeur), y, boldPaint)
            y += 17f
        }
        fun section(titre: String) { y += 6f; c.drawText(titre, margin, y, sectionPaint); y += 18f }

        val n = ind.inscrits
        section("Patients suivis")
        ligne("Patients inscrits", effectif(n))
        ligne("Soignants dans l'equipe", nbSoignants.toString())
        ligne("Patients actifs (au moins une mesure en 30 jours)", "${effectif(ind.actifs30j)}   ${pourcentage(ind.actifs30j, n)}")
        ligne("Patients perdus de vue (aucune mesure depuis 60 jours)", "${effectif(ind.perdusDeVue)}   ${pourcentage(ind.perdusDeVue, n)}")

        section("Equilibre du diabete (HbA1c de laboratoire, 6 derniers mois)")
        val nh = ind.avecHbA1c6mois
        ligne("Patients avec une HbA1c recente", "${effectif(nh)}   ${pourcentage(nh, n)}")
        ligne("HbA1c moyenne", ind.hba1cMoyenne?.let { String.format(Locale.FRANCE, "%.1f %%", it) } ?: "n.d.")
        ligne("HbA1c inferieure a 7 %", "${effectif(ind.hba1cSous7)}   ${pourcentage(ind.hba1cSous7, nh)}")
        ligne("HbA1c de 9 % ou plus", "${effectif(ind.hba1cAuDessus9)}   ${pourcentage(ind.hba1cAuDessus9, nh)}")

        section("Glycemies partagees (30 derniers jours)")
        ligne("Glycemie moyenne des patients actifs", ind.glycemieMoyenne30j?.let { "${it.toInt()} mg/dL" } ?: "n.d.")
        ligne("Patients avec au moins une glycemie < 54 mg/dL", "${effectif(ind.avecHypoSevere30j)}   ${pourcentage(ind.avecHypoSevere30j, n)}")

        section("Priorite de suivi (aide a l'organisation, pas un diagnostic)")
        ligne("A revoir en priorite", "${effectif(ind.prioriteHaute)}   ${pourcentage(ind.prioriteHaute, n)}")
        ligne("A surveiller", "${effectif(ind.prioriteMoyenne)}   ${pourcentage(ind.prioriteMoyenne, n)}")
        ligne("Stables", "${effectif(ind.prioriteBasse)}   ${pourcentage(ind.prioriteBasse, n)}")
        ligne("Pas assez de donnees", "${effectif(ind.prioriteInconnue)}   ${pourcentage(ind.prioriteInconnue, n)}")

        y += 14f
        c.drawLine(margin, y, pageWidth - margin, y, rulePaint); y += 16f
        listOf(
            "Methode : chiffres calcules a partir des mesures que les patients partagent avec l'etablissement",
            "dans DiaSmart (inscription volontaire avec le code de l'etablissement, retrait possible a tout moment).",
            "Effectifs de 1 a 4 affiches \"< 5\" et pourcentages non donnes (n.d.) sous 5 patients, pour",
            "proteger l'anonymat. Les HbA1c estimees par l'application ne sont pas comptees.",
            "La priorite de suivi est une aide a l'organisation des soins ; elle ne remplace pas l'avis du soignant."
        ).forEach { c.drawText(it, margin, y, mutedPaint); y += 12f }

        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "DiaSmart_rapport_resultats_${aujourdHui}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
