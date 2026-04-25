package com.diabeto.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.diabeto.data.entity.JournalEntity
import com.diabeto.data.entity.LectureGlucoseEntity
import com.diabeto.data.entity.MedicamentEntity
import com.diabeto.data.model.RepasDocument
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generateur de PDF pour les rapports patient et medecin.
 *
 * Implementation 100% native via android.graphics.pdf.PdfDocument — aucune
 * dependance tierce. Format A4 (595 x 842 pts a 72 dpi).
 *
 * On reste sur du texte tabulaire pour rester leger et lisible : la donnee
 * importe plus que la mise en page sophistiquee. Une evolution future pourra
 * ajouter un graphique de glycemie via Canvas.
 */
class PdfReportGenerator(private val context: Context) {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 32f
    private val contentWidth = pageWidth - 2 * margin
    private val lineHeight = 14f

    private val titlePaint = Paint().apply {
        color = Color.parseColor("#3F3D8A")
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    private val sectionPaint = Paint().apply {
        color = Color.parseColor("#2C2A5E")
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        color = Color.BLACK
        textSize = 10.5f
        isAntiAlias = true
    }
    private val mutedPaint = Paint().apply {
        color = Color.parseColor("#6A6A82")
        textSize = 9.5f
        isAntiAlias = true
    }
    private val rulePaint = Paint().apply {
        color = Color.parseColor("#D4D4DC")
        strokeWidth = 0.6f
    }

    data class PatientReportData(
        val patientNom: String,
        val patientEmail: String,
        val periodLabel: String,
        val glucoseLectures: List<LectureGlucoseEntity>,
        val medicaments: List<MedicamentEntity>,
        val repas: List<RepasDocument>,
        val journal: List<JournalEntity>
    )

    data class DoctorReportData(
        val medecinNom: String,
        val patientNom: String,
        val patientEmail: String,
        val ordonnance: String,
        val compteRendu: String,
        val recommandations: String
    )

    // ════════════════════════════════════════════════════════════════════
    //  PATIENT REPORT
    // ════════════════════════════════════════════════════════════════════

    fun generatePatientReport(data: PatientReportData, fileName: String): File {
        val pdf = PdfDocument()
        val cursor = PageCursor(pdf)
        cursor.startPage()

        // En-tete
        cursor.drawText("Rapport patient — DiaSmart", titlePaint)
        cursor.advance(6f)
        cursor.drawText("Patient : ${data.patientNom}", bodyPaint)
        if (data.patientEmail.isNotBlank()) {
            cursor.drawText("Email : ${data.patientEmail}", mutedPaint)
        }
        cursor.drawText("Periode : ${data.periodLabel}", mutedPaint)
        cursor.drawText("Genere le : ${nowIso()}", mutedPaint)
        cursor.advance(8f)
        cursor.drawRule()
        cursor.advance(10f)

        // Glycemies
        cursor.section("Glycemies (${data.glucoseLectures.size} lectures)")
        if (data.glucoseLectures.isEmpty()) {
            cursor.drawText("Aucune lecture enregistree sur la periode.", mutedPaint)
        } else {
            val sum = data.glucoseLectures.sumOf { it.valeur }
            val avg = sum / data.glucoseLectures.size
            val min = data.glucoseLectures.minOf { it.valeur }
            val max = data.glucoseLectures.maxOf { it.valeur }
            cursor.drawText(
                "Moyenne : ${"%.1f".format(Locale.FRANCE, avg)} mg/dL  |  Min : ${"%.0f".format(Locale.FRANCE, min)}  |  Max : ${"%.0f".format(Locale.FRANCE, max)}",
                bodyPaint
            )
            cursor.advance(6f)
            // Tableau : Date | Heure | Valeur | Contexte
            val cols = listOf(
                Col("Date/heure", 0.32f),
                Col("Valeur (mg/dL)", 0.22f),
                Col("Contexte", 0.30f),
                Col("Notes", 0.16f)
            )
            cursor.tableHeader(cols)
            data.glucoseLectures.take(60).forEach { l ->
                cursor.tableRow(
                    cols, listOf(
                        l.dateHeure.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                        "%.0f".format(Locale.FRANCE, l.valeur),
                        l.contexte.name.lowercase(Locale.FRANCE).replace('_', ' '),
                        l.notes.take(20)
                    )
                )
            }
            if (data.glucoseLectures.size > 60) {
                cursor.advance(2f)
                cursor.drawText("(${data.glucoseLectures.size - 60} lectures supplementaires non listees)", mutedPaint)
            }
        }
        cursor.advance(10f)

        // Medicaments
        cursor.section("Medicaments (${data.medicaments.size})")
        if (data.medicaments.isEmpty()) {
            cursor.drawText("Aucun medicament enregistre.", mutedPaint)
        } else {
            val cols = listOf(
                Col("Nom", 0.36f),
                Col("Dosage", 0.20f),
                Col("Frequence", 0.20f),
                Col("Heure", 0.12f),
                Col("Statut", 0.12f)
            )
            cursor.tableHeader(cols)
            data.medicaments.forEach { m ->
                cursor.tableRow(
                    cols, listOf(
                        m.nom,
                        m.dosage,
                        m.frequence.name.lowercase(Locale.FRANCE).replace('_', ' '),
                        m.heurePrise.format(DateTimeFormatter.ofPattern("HH:mm")),
                        if (m.estActif) "actif" else "arrete"
                    )
                )
            }
        }
        cursor.advance(10f)

        // Repas analyses
        cursor.section("Repas analyses par Rolly IA (${data.repas.size})")
        if (data.repas.isEmpty()) {
            cursor.drawText("Aucun repas analyse sur la periode.", mutedPaint)
        } else {
            val cols = listOf(
                Col("Repas", 0.40f),
                Col("Glucides (g)", 0.16f),
                Col("IG", 0.10f),
                Col("CG", 0.10f),
                Col("Calories", 0.14f),
                Col("Charge", 0.10f)
            )
            cursor.tableHeader(cols)
            data.repas.take(40).forEach { r ->
                cursor.tableRow(
                    cols, listOf(
                        r.nomRepas.ifBlank { "Repas" },
                        "%.1f".format(Locale.FRANCE, r.glucidesEstimes),
                        r.indexGlycemique.toString(),
                        "%.1f".format(Locale.FRANCE, r.chargeGlycemique),
                        r.caloriesEstimees.toString(),
                        ratingFromCG(r.chargeGlycemique)
                    )
                )
            }
        }
        cursor.advance(10f)

        // Journal humeur / sommeil / pas
        cursor.section("Journal humeur, sommeil et pas (${data.journal.size} entrees)")
        if (data.journal.isEmpty()) {
            cursor.drawText("Aucune entree dans le journal.", mutedPaint)
        } else {
            val cols = listOf(
                Col("Date", 0.18f),
                Col("Humeur", 0.16f),
                Col("Stress", 0.14f),
                Col("Sommeil", 0.16f),
                Col("Heures", 0.12f),
                Col("Pas", 0.12f),
                Col("Activite", 0.12f)
            )
            cursor.tableHeader(cols)
            data.journal.forEach { j ->
                cursor.tableRow(
                    cols, listOf(
                        j.date.format(DateTimeFormatter.ofPattern("dd/MM/yy")),
                        j.humeur.name.lowercase(Locale.FRANCE),
                        j.niveauStress.name.lowercase(Locale.FRANCE),
                        j.qualiteSommeil.name.lowercase(Locale.FRANCE),
                        "%.1f h".format(Locale.FRANCE, j.heuresSommeil),
                        j.pas.toString(),
                        if (j.activitePhysique) "${j.minutesActivite} min" else "-"
                    )
                )
            }
            // Total pas / moyenne
            val totalPas = data.journal.sumOf { it.pas }
            val avgPas = if (data.journal.isNotEmpty()) totalPas / data.journal.size else 0
            cursor.advance(4f)
            cursor.drawText("Total pas : $totalPas  |  Moyenne quotidienne : $avgPas", bodyPaint)
        }
        cursor.advance(14f)

        cursor.drawRule()
        cursor.advance(6f)
        cursor.drawText(
            "Document genere automatiquement par DiaSmart. Donnees confidentielles destinees au professionnel de sante.",
            mutedPaint
        )

        cursor.finish()
        return writePdf(pdf, fileName)
    }

    // ════════════════════════════════════════════════════════════════════
    //  DOCTOR REPORT (ordonnance + compte-rendu)
    // ════════════════════════════════════════════════════════════════════

    fun generateDoctorReport(data: DoctorReportData, fileName: String): File {
        val pdf = PdfDocument()
        val cursor = PageCursor(pdf)
        cursor.startPage()

        cursor.drawText("Compte-rendu medical & ordonnance", titlePaint)
        cursor.advance(6f)
        cursor.drawText("Medecin : ${data.medecinNom}", bodyPaint)
        cursor.drawText("Patient : ${data.patientNom}", bodyPaint)
        if (data.patientEmail.isNotBlank()) {
            cursor.drawText("Email patient : ${data.patientEmail}", mutedPaint)
        }
        cursor.drawText("Date : ${nowIso()}", mutedPaint)
        cursor.advance(8f)
        cursor.drawRule()
        cursor.advance(10f)

        cursor.section("Compte-rendu de consultation")
        if (data.compteRendu.isBlank()) {
            cursor.drawText("(Aucun compte-rendu saisi)", mutedPaint)
        } else {
            cursor.drawParagraph(data.compteRendu, bodyPaint)
        }
        cursor.advance(10f)

        cursor.section("Ordonnance")
        if (data.ordonnance.isBlank()) {
            cursor.drawText("(Aucune ordonnance saisie)", mutedPaint)
        } else {
            cursor.drawParagraph(data.ordonnance, bodyPaint)
        }
        cursor.advance(10f)

        if (data.recommandations.isNotBlank()) {
            cursor.section("Recommandations / suivi")
            cursor.drawParagraph(data.recommandations, bodyPaint)
            cursor.advance(10f)
        }

        cursor.advance(20f)
        cursor.drawRule()
        cursor.advance(6f)
        cursor.drawText(
            "Signature electronique : ${data.medecinNom}  —  Genere via DiaSmart le ${nowIso()}",
            mutedPaint
        )

        cursor.finish()
        return writePdf(pdf, fileName)
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private fun writePdf(pdf: PdfDocument, fileName: String): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        return file
    }

    private fun nowIso(): String = java.time.LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

    private fun ratingFromCG(cg: Double): String = when {
        cg < 10 -> "faible"
        cg < 20 -> "moyenne"
        else -> "elevee"
    }

    private data class Col(val title: String, val ratio: Float)

    private inner class PageCursor(val pdf: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var y = margin
        private var pageNum = 0

        fun startPage() {
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = pdf.startPage(info)
            y = margin
        }

        fun finish() {
            page?.let { pdf.finishPage(it) }
            page = null
        }

        private fun ensureRoom(needed: Float) {
            if (y + needed > pageHeight - margin) {
                finish()
                startPage()
            }
        }

        fun advance(extra: Float) {
            y += extra
        }

        fun drawText(text: String, paint: Paint) {
            ensureRoom(lineHeight)
            page?.canvas?.drawText(text, margin, y + lineHeight, paint)
            y += lineHeight + 2f
        }

        fun drawParagraph(text: String, paint: Paint) {
            // Wrap simple a la largeur disponible
            val words = text.replace("\r", "").split('\n')
            words.forEach { line ->
                if (line.isEmpty()) {
                    advance(lineHeight)
                    return@forEach
                }
                val tokens = line.split(' ')
                var current = StringBuilder()
                tokens.forEach { token ->
                    val tentative = if (current.isEmpty()) token else "$current $token"
                    if (paint.measureText(tentative) > contentWidth) {
                        if (current.isNotEmpty()) drawText(current.toString(), paint)
                        current = StringBuilder(token)
                    } else {
                        current.clear()
                        current.append(tentative)
                    }
                }
                if (current.isNotEmpty()) drawText(current.toString(), paint)
            }
        }

        fun drawRule() {
            ensureRoom(4f)
            page?.canvas?.drawLine(margin, y, pageWidth - margin, y, rulePaint)
            y += 4f
        }

        fun section(title: String) {
            advance(4f)
            drawText(title, sectionPaint)
            drawRule()
            advance(2f)
        }

        fun tableHeader(cols: List<Col>) {
            ensureRoom(lineHeight + 4f)
            var x = margin
            val headerPaint = Paint(bodyPaint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            cols.forEach { c ->
                page?.canvas?.drawText(c.title, x, y + lineHeight, headerPaint)
                x += c.ratio * contentWidth
            }
            y += lineHeight + 1f
            drawRule()
        }

        fun tableRow(cols: List<Col>, values: List<String>) {
            ensureRoom(lineHeight + 2f)
            var x = margin
            cols.forEachIndexed { i, c ->
                val raw = values.getOrNull(i) ?: ""
                val w = c.ratio * contentWidth - 4f
                val text = ellipsize(raw, w, bodyPaint)
                page?.canvas?.drawText(text, x, y + lineHeight, bodyPaint)
                x += c.ratio * contentWidth
            }
            y += lineHeight + 1.5f
        }

        private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            val ell = "…"
            var lo = 0
            var hi = text.length
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (paint.measureText(text.substring(0, mid) + ell) <= maxWidth) lo = mid else hi = mid - 1
            }
            return if (lo <= 0) ell else text.substring(0, lo) + ell
        }
    }
}
