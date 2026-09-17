package com.diabeto.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.diabeto.data.model.EntreeDossier
import com.diabeto.data.model.SectionDossier
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class FormatExport(val extension: String, val mime: String, val libelle: String) {
    PDF("pdf", "application/pdf", "PDF"),
    WORD(
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Word (.docx)"
    )
}

/**
 * Export du dossier numerique en fichier, pour le sortir de l'application.
 *
 * Aucun des deux formats n'ajoute de dependance : le PDF passe par
 * `android.graphics.pdf.PdfDocument`, et le .docx est ecrit a la main (un
 * fichier Word n'est qu'une archive zip de trois fichiers XML). Les fichiers
 * vont dans `cache/reports/`, deja declare au FileProvider pour le partage.
 */
class DossierExporter(private val context: Context) {

    data class Contenu(
        val patientNom: String,
        val medecinNom: String,
        val entrees: List<EntreeDossier>,
        val rendezVous: List<Map<String, Any?>>,
        val inclurePrivees: Boolean,
        val versionPatient: Boolean
    )

    fun exporter(contenu: Contenu, format: FormatExport): File {
        val nom = nomFichier(contenu.patientNom, format)
        return when (format) {
            FormatExport.PDF -> ecrirePdf(contenu, nom)
            FormatExport.WORD -> ecrireWord(contenu, nom)
        }
    }

    // ── Contenu commun aux deux formats ─────────────────────────────────

    private fun horodatage(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"))

    private fun fichesDe(contenu: Contenu, section: SectionDossier) =
        contenu.entrees.filter { it.type.section == section && (contenu.inclurePrivees || it.visiblePatient) }

    /** Lignes « Libelle : valeur » d'une fiche, dans l'ordre des champs du type. */
    private fun champsRemplis(fiche: EntreeDossier): List<Pair<String, String>> =
        fiche.type.champs.mapNotNull { champ ->
            fiche.champs[champ.cle]?.takeIf { it.isNotBlank() }?.let { champ.libelle to it.trim() }
        }

    private fun resumeRendezVous(rdv: Map<String, Any?>): String {
        val titre = (rdv["titre"] as? String).orEmpty().ifBlank { "Rendez-vous" }
        val date = rdv["dateHeure"]?.toString().orEmpty().take(16).replace("T", " ")
        val lieu = (rdv["lieu"] as? String).orEmpty()
        return listOf(titre, date, lieu).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun sousTitre(contenu: Contenu): String? = when {
        contenu.versionPatient -> "Informations partagées par votre médecin"
        contenu.inclurePrivees -> "Copie complète du médecin, notes privées incluses"
        else -> "Version sans les notes privées du médecin"
    }

    private fun nomFichier(patientNom: String, format: FormatExport): String {
        val base = Normalizer.normalize(patientNom.ifBlank { "patient" }, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(40)
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "Dossier_${base}_$date.${format.extension}"
    }

    private fun fichierSortie(nom: String): File {
        val dossier = File(context.cacheDir, "reports").apply { mkdirs() }
        return File(dossier, nom)
    }

    // ══════════════════════════════════════════════════════════════════
    //  PDF
    // ══════════════════════════════════════════════════════════════════

    private val largeurPage = 595
    private val hauteurPage = 842
    private val marge = 40f
    private val largeurTexte = largeurPage - 2 * marge

    private fun paint(taille: Float, couleur: String, gras: Boolean = false, italique: Boolean = false) =
        Paint().apply {
            color = Color.parseColor(couleur)
            textSize = taille
            isAntiAlias = true
            typeface = Typeface.create(
                Typeface.DEFAULT,
                when {
                    gras && italique -> Typeface.BOLD_ITALIC
                    gras -> Typeface.BOLD
                    italique -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
            )
        }

    private val pTitre = paint(20f, "#3F3D8A", gras = true)
    private val pSousTitre = paint(10f, "#6A6A82", italique = true)
    private val pEntete = paint(10.5f, "#1F1F2E")
    private val pSection = paint(13.5f, "#3F3D8A", gras = true)
    private val pType = paint(9f, "#5B5BD6", gras = true)
    private val pDate = paint(9f, "#6A6A82")
    private val pTitreFiche = paint(11.5f, "#1F1F2E", gras = true)
    private val pLibelle = paint(8.5f, "#6A6A82")
    private val pValeur = paint(10.5f, "#1F1F2E")
    private val pPrive = paint(8.5f, "#B45309", italique = true)
    private val pVide = paint(10f, "#8A8AA0", italique = true)
    private val pPied = paint(8f, "#8A8AA0")
    private val pFiletSection = Paint().apply { color = Color.parseColor("#3F3D8A"); strokeWidth = 1.2f }
    private val pFilet = Paint().apply { color = Color.parseColor("#DCDCE6"); strokeWidth = 0.6f }

    private fun ecrirePdf(contenu: Contenu, nom: String): File {
        val pdf = PdfDocument()
        val c = CurseurPdf(pdf, contenu)
        c.nouvellePage()

        c.texte("Dossier médical", pTitre, apres = 4f)
        sousTitre(contenu)?.let { c.texte(it, pSousTitre, apres = 8f) }
        c.texte("Patient : ${contenu.patientNom.ifBlank { "—" }}", pEntete)
        c.texte("Médecin : Dr ${contenu.medecinNom.ifBlank { "—" }}", pEntete)
        c.texte("Exporté le ${horodatage()}", pDate, apres = 10f)

        SectionDossier.entries.forEach { section ->
            val fiches = fichesDe(contenu, section)
            c.section(section.libelle)
            if (fiches.isEmpty()) c.texte("Aucune fiche.", pVide, apres = 6f)
            fiches.forEach { fiche -> c.fiche(fiche) }

            if (section == SectionDossier.SUIVI && contenu.rendezVous.isNotEmpty()) {
                c.texte("Rendez-vous", pType, avant = 4f, apres = 2f)
                contenu.rendezVous.forEach { c.paragraphe("• ${resumeRendezVous(it)}", pValeur, retrait = 6f) }
            }
        }

        c.terminer()
        val fichier = fichierSortie(nom)
        FileOutputStream(fichier).use { pdf.writeTo(it) }
        pdf.close()
        return fichier
    }

    private inner class CurseurPdf(val pdf: PdfDocument, val contenu: Contenu) {
        private var page: PdfDocument.Page? = null
        private var numero = 0
        private var y = marge
        private val basUtile = hauteurPage - marge - 18f

        fun nouvellePage() {
            terminer()
            numero++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(largeurPage, hauteurPage, numero).create())
            y = marge
        }

        fun terminer() {
            val p = page ?: return
            val canvas = p.canvas
            val yPied = hauteurPage - marge + 6f
            canvas.drawLine(marge, yPied - 12f, largeurPage - marge, yPied - 12f, pFilet)
            canvas.drawText("DiaSmart — document médical confidentiel", marge, yPied, pPied)
            val num = "Page $numero"
            canvas.drawText(num, largeurPage - marge - pPied.measureText(num), yPied, pPied)
            pdf.finishPage(p)
            page = null
        }

        private fun place(hauteur: Float) {
            if (page == null || y + hauteur > basUtile) nouvellePage()
        }

        private fun hauteurLigne(p: Paint) = p.textSize * 1.35f

        fun texte(t: String, p: Paint, avant: Float = 0f, apres: Float = 2f) {
            y += avant
            place(hauteurLigne(p))
            page?.canvas?.drawText(t, marge, y + p.textSize, p)
            y += hauteurLigne(p) + apres
        }

        /** Texte avec retour a la ligne automatique et sauts de ligne conserves. */
        fun paragraphe(t: String, p: Paint, retrait: Float = 0f, apres: Float = 2f) {
            val largeur = largeurTexte - retrait
            t.replace("\r", "").split('\n').forEach { ligneSource ->
                if (ligneSource.isBlank()) { y += hauteurLigne(p) * 0.5f; return@forEach }
                var courante = ""
                ligneSource.split(' ').forEach { mot ->
                    val essai = if (courante.isEmpty()) mot else "$courante $mot"
                    if (p.measureText(essai) > largeur && courante.isNotEmpty()) {
                        ecrireLigne(courante, p, retrait)
                        courante = mot
                    } else {
                        courante = essai
                    }
                }
                if (courante.isNotEmpty()) ecrireLigne(courante, p, retrait)
            }
            y += apres
        }

        private fun ecrireLigne(t: String, p: Paint, retrait: Float) {
            place(hauteurLigne(p))
            page?.canvas?.drawText(t, marge + retrait, y + p.textSize, p)
            y += hauteurLigne(p)
        }

        fun section(titre: String) {
            // Un titre de section ne reste jamais seul en bas de page.
            if (page != null && y + 70f > basUtile) nouvellePage()
            y += 10f
            texte(titre, pSection, apres = 1f)
            page?.canvas?.drawLine(marge, y, largeurPage - marge, y, pFiletSection)
            y += 8f
        }

        fun fiche(f: EntreeDossier) {
            if (page != null && y + 60f > basUtile) nouvellePage()
            place(hauteurLigne(pType))
            val canvas = page?.canvas
            canvas?.drawText(f.type.libelle.uppercase(), marge, y + pType.textSize, pType)
            if (f.date.isNotBlank()) {
                canvas?.drawText(f.date, largeurPage - marge - pDate.measureText(f.date), y + pDate.textSize, pDate)
            }
            y += hauteurLigne(pType)
            if (!contenu.versionPatient && !f.visiblePatient) {
                texte("Note privée — non visible par le patient", pPrive, apres = 1f)
            }
            val champs = champsRemplis(f)
            champs.forEachIndexed { i, (libelle, valeur) ->
                if (i == 0) {
                    paragraphe(valeur, pTitreFiche, apres = 2f)
                } else {
                    texte(libelle, pLibelle, apres = 0f)
                    paragraphe(valeur, pValeur, apres = 3f)
                }
            }
            y += 4f
            place(2f)
            page?.canvas?.drawLine(marge, y, largeurPage - marge, y, pFilet)
            y += 8f
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  WORD (.docx)
    // ══════════════════════════════════════════════════════════════════

    private fun ecrireWord(contenu: Contenu, nom: String): File {
        val corps = StringBuilder()

        corps.append(para("Dossier médical", taille = 36, gras = true, couleur = "3F3D8A", apres = 60))
        sousTitre(contenu)?.let { corps.append(para(it, taille = 20, italique = true, couleur = "6A6A82", apres = 160)) }
        corps.append(para("Patient : ${contenu.patientNom.ifBlank { "—" }}", taille = 22, apres = 20))
        corps.append(para("Médecin : Dr ${contenu.medecinNom.ifBlank { "—" }}", taille = 22, apres = 20))
        corps.append(para("Exporté le ${horodatage()}", taille = 18, couleur = "6A6A82", apres = 240))

        SectionDossier.entries.forEach { section ->
            corps.append(titreSection(section.libelle))
            val fiches = fichesDe(contenu, section)
            if (fiches.isEmpty()) corps.append(para("Aucune fiche.", taille = 20, italique = true, couleur = "8A8AA0", apres = 120))
            fiches.forEach { f ->
                val entete = f.type.libelle.uppercase() + if (f.date.isNotBlank()) "   ·   ${f.date}" else ""
                corps.append(para(entete, taille = 17, gras = true, couleur = "5B5BD6", apres = 20, avecSuivant = true))
                if (!contenu.versionPatient && !f.visiblePatient) {
                    corps.append(para("Note privée — non visible par le patient", taille = 17, italique = true, couleur = "B45309", apres = 20, avecSuivant = true))
                }
                champsRemplis(f).forEachIndexed { i, (libelle, valeur) ->
                    if (i == 0) {
                        corps.append(para(valeur, taille = 23, gras = true, apres = 40))
                    } else {
                        corps.append(para(libelle, taille = 16, couleur = "6A6A82", apres = 0, avecSuivant = true))
                        corps.append(para(valeur, taille = 21, apres = 60))
                    }
                }
                corps.append(para("", taille = 8, apres = 120, filetBas = true))
            }
            if (section == SectionDossier.SUIVI && contenu.rendezVous.isNotEmpty()) {
                corps.append(para("Rendez-vous", taille = 17, gras = true, couleur = "5B5BD6", apres = 20, avecSuivant = true))
                contenu.rendezVous.forEach { corps.append(para("• ${resumeRendezVous(it)}", taille = 21, apres = 20)) }
            }
        }
        corps.append(para("DiaSmart — document médical confidentiel", taille = 16, italique = true, couleur = "8A8AA0", avant = 240))

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$corps<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>"""

        val typesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

        val fichier = fichierSortie(nom)
        ZipOutputStream(FileOutputStream(fichier)).use { zip ->
            listOf(
                "[Content_Types].xml" to typesXml,
                "_rels/.rels" to relsXml,
                "word/document.xml" to documentXml
            ).forEach { (chemin, xml) ->
                zip.putNextEntry(ZipEntry(chemin))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return fichier
    }

    private fun titreSection(titre: String): String =
        "<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"280\" w:after=\"120\"/>" +
            "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"2\" w:color=\"3F3D8A\"/></w:pBdr></w:pPr>" +
            run(titre, taille = 27, gras = true, italique = false, couleur = "3F3D8A") + "</w:p>"

    /** Paragraphe Word. `taille` en demi-points, espacements en vingtiemes de point. */
    private fun para(
        texte: String,
        taille: Int,
        gras: Boolean = false,
        italique: Boolean = false,
        couleur: String = "1F1F2E",
        avant: Int = 0,
        apres: Int = 0,
        avecSuivant: Boolean = false,
        filetBas: Boolean = false
    ): String {
        val pPr = StringBuilder("<w:pPr>")
        if (avecSuivant) pPr.append("<w:keepNext/>")
        pPr.append("<w:spacing w:before=\"$avant\" w:after=\"$apres\"/>")
        if (filetBas) pPr.append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"1\" w:color=\"DCDCE6\"/></w:pBdr>")
        pPr.append("</w:pPr>")
        return "<w:p>$pPr${run(texte, taille, gras, italique, couleur)}</w:p>"
    }

    private fun run(texte: String, taille: Int, gras: Boolean, italique: Boolean, couleur: String): String {
        val rPr = buildString {
            append("<w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>")
            if (gras) append("<w:b/>")
            if (italique) append("<w:i/>")
            append("<w:color w:val=\"$couleur\"/><w:sz w:val=\"$taille\"/><w:szCs w:val=\"$taille\"/></w:rPr>")
        }
        // Les retours a la ligne saisis dans l'application deviennent des <w:br/>.
        return texte.replace("\r", "").split('\n').mapIndexed { i, morceau ->
            val saut = if (i > 0) "<w:br/>" else ""
            "<w:r>$rPr$saut<w:t xml:space=\"preserve\">${echapper(morceau)}</w:t></w:r>"
        }.joinToString("")
    }

    private fun echapper(t: String) = t
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .filter { it == '\t' || it.code >= 0x20 }
}
