package com.diabeto.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Un point de la courbe : instant (ms) et glycemie (mg/dL). */
data class PointCourbe(val t: Long, val valeur: Double)

private val fuseau = TimeZone.currentSystemDefault()

fun LocalDateTime.ms(): Long = toInstant(fuseau).toEpochMilliseconds()

/**
 * Courbe de glycemie entre [debut] et [fin] (ms), avec la zone cible
 * 70-180 mg/dL en vert pale. [relier] = trait entre les points.
 */
@Composable
fun CourbeGlycemie(points: List<PointCourbe>, debut: Long, fin: Long, modifier: Modifier = Modifier, relier: Boolean = true) {
    val mesureur = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = Color.Gray)
    Canvas(modifier) {
        val gauche = 44f
        val bas = 22f
        val largeur = size.width - gauche - 8f
        val hauteur = size.height - bas - 8f
        if (largeur <= 0f || hauteur <= 0f) return@Canvas
        val yMin = minOf(40.0, points.minOfOrNull { it.valeur } ?: 40.0)
        val yMax = maxOf(300.0, (points.maxOfOrNull { it.valeur } ?: 300.0) + 20)
        val duree = (fin - debut).coerceAtLeast(1L)
        fun x(t: Long) = gauche + largeur * ((t - debut).toFloat() / duree)
        fun y(v: Double) = 8f + hauteur * (1f - ((v - yMin) / (yMax - yMin)).toFloat())

        // Zone cible
        drawRect(Color(0x1A2E7D32), Offset(gauche, y(180.0)), Size(largeur, y(70.0) - y(180.0)))
        val tirets = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        listOf(70.0, 180.0).forEach { v ->
            drawLine(Vert.copy(alpha = 0.5f), Offset(gauche, y(v)), Offset(gauche + largeur, y(v)), 1f, pathEffect = tirets)
        }
        // Axe vertical : graduations
        listOf(70.0, 180.0, 250.0).plus(if (yMax > 350) listOf(350.0) else emptyList()).forEach { v ->
            val r = mesureur.measure(v.toInt().toString(), style)
            drawText(r, topLeft = Offset(gauche - r.size.width - 6f, y(v) - r.size.height / 2f))
        }
        drawLine(Color.LightGray, Offset(gauche, 8f), Offset(gauche, 8f + hauteur))
        drawLine(Color.LightGray, Offset(gauche, 8f + hauteur), Offset(gauche + largeur, 8f + hauteur))
        // Axe horizontal : debut, milieu, fin
        listOf(debut, debut + duree / 2, fin).forEachIndexed { i, t ->
            val d = Instant.fromEpochMilliseconds(t).toLocalDateTime(fuseau)
            val r = mesureur.measure("%02d/%02d".format(d.dayOfMonth, d.monthNumber), style)
            val px = when (i) { 0 -> gauche; 2 -> gauche + largeur - r.size.width; else -> x(t) - r.size.width / 2f }
            drawText(r, topLeft = Offset(px, 8f + hauteur + 4f))
        }

        val visibles = points.filter { it.t in debut..fin }.sortedBy { it.t }
        if (relier && visibles.size > 1) {
            val chemin = Path()
            visibles.forEachIndexed { i, p -> if (i == 0) chemin.moveTo(x(p.t), y(p.valeur)) else chemin.lineTo(x(p.t), y(p.valeur)) }
            drawPath(chemin, Indigo.copy(alpha = 0.7f), style = Stroke(width = 2f))
        }
        visibles.forEach { p -> drawCircle(couleurGlycemie(p.valeur), radius = 3.5f, center = Offset(x(p.t), y(p.valeur))) }
    }
}

/** Courbe de tension : systolique (indigo) et diastolique (vert-bleu), reperes objectif 130 et 80 mmHg. */
@Composable
fun CourbeTension(points: List<Triple<Long, Int, Int>>, debut: Long, fin: Long, modifier: Modifier = Modifier) {
    val mesureur = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = Color.Gray)
    val couleurDia = Color(0xFF26A69A)
    Canvas(modifier) {
        val gauche = 44f
        val bas = 22f
        val largeur = size.width - gauche - 8f
        val hauteur = size.height - bas - 8f
        if (largeur <= 0f || hauteur <= 0f) return@Canvas
        val visibles = points.filter { it.first in debut..fin }.sortedBy { it.first }
        val yMin = minOf(50, (visibles.minOfOrNull { it.third } ?: 60) - 10).toFloat()
        val yMax = maxOf(180, (visibles.maxOfOrNull { it.second } ?: 160) + 10).toFloat()
        val duree = (fin - debut).coerceAtLeast(1L)
        fun x(t: Long) = gauche + largeur * ((t - debut).toFloat() / duree)
        fun y(v: Int) = 8f + hauteur * (1f - (v - yMin) / (yMax - yMin))

        val tirets = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        listOf(130, 80).forEach { v ->
            drawLine(Orange.copy(alpha = 0.6f), Offset(gauche, y(v)), Offset(gauche + largeur, y(v)), 1f, pathEffect = tirets)
            val r = mesureur.measure(v.toString(), style)
            drawText(r, topLeft = Offset(gauche - r.size.width - 6f, y(v) - r.size.height / 2f))
        }
        drawLine(Color.LightGray, Offset(gauche, 8f), Offset(gauche, 8f + hauteur))
        drawLine(Color.LightGray, Offset(gauche, 8f + hauteur), Offset(gauche + largeur, 8f + hauteur))
        listOf(debut, debut + duree / 2, fin).forEachIndexed { i, t ->
            val d = Instant.fromEpochMilliseconds(t).toLocalDateTime(fuseau)
            val r = mesureur.measure("%02d/%02d".format(d.dayOfMonth, d.monthNumber), style)
            val px = when (i) { 0 -> gauche; 2 -> gauche + largeur - r.size.width; else -> x(t) - r.size.width / 2f }
            drawText(r, topLeft = Offset(px, 8f + hauteur + 4f))
        }
        fun serie(valeur: (Triple<Long, Int, Int>) -> Int, couleur: Color) {
            if (visibles.size > 1) {
                val chemin = Path()
                visibles.forEachIndexed { i, p -> if (i == 0) chemin.moveTo(x(p.first), y(valeur(p))) else chemin.lineTo(x(p.first), y(valeur(p))) }
                drawPath(chemin, couleur.copy(alpha = 0.8f), style = Stroke(width = 2f))
            }
            visibles.forEach { p -> drawCircle(couleur, radius = 3.5f, center = Offset(x(p.first), y(valeur(p)))) }
        }
        serie({ it.second }, Indigo)
        serie({ it.third }, couleurDia)
    }
}

/** Une serie sur 24 h : (minute du jour 0-1439, glycemie). */
data class SerieJour(val points: List<Pair<Int, Double>>, val couleur: Color, val epaisseur: Float = 2f)

/** Axe 00:00 - 24:00 commun aux courbes de la journee et au profil horaire. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.axeJournee(
    mesureur: androidx.compose.ui.text.TextMeasurer, style: TextStyle,
    gauche: Float, haut: Float, largeur: Float, hauteur: Float, y: (Double) -> Float
) {
    drawRect(Color(0x1A2E7D32), Offset(gauche, y(180.0)), Size(largeur, y(70.0) - y(180.0)))
    val tirets = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    listOf(70.0, 180.0).forEach { v ->
        drawLine(Vert.copy(alpha = 0.6f), Offset(gauche, y(v)), Offset(gauche + largeur, y(v)), 1f, pathEffect = tirets)
    }
    listOf(54.0, 70.0, 180.0, 250.0, 350.0).forEach { v ->
        val py = y(v)
        if (py < haut - 1 || py > haut + hauteur + 1) return@forEach
        val r = mesureur.measure(v.toInt().toString(), style)
        drawText(r, topLeft = Offset(gauche - r.size.width - 6f, py - r.size.height / 2f))
    }
    for (h in 0..24 step 3) {
        val px = gauche + largeur * h / 24f
        drawLine(Color(0xFFEDEEF3), Offset(px, haut), Offset(px, haut + hauteur))
        val r = mesureur.measure("%02d:00".format(h), style)
        drawText(r, topLeft = Offset((px - r.size.width / 2f).coerceIn(gauche - 4f, gauche + largeur - r.size.width), haut + hauteur + 4f))
    }
    drawLine(Color.LightGray, Offset(gauche, haut), Offset(gauche, haut + hauteur))
    drawLine(Color.LightGray, Offset(gauche, haut + hauteur), Offset(gauche + largeur, haut + hauteur))
}

/** Courbes de journees superposees sur 24 h (la derniere serie est dessinee au-dessus). */
@Composable
fun CourbeJournee(series: List<SerieJour>, modifier: Modifier = Modifier) {
    val mesureur = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = Color.Gray)
    Canvas(modifier) {
        val gauche = 44f; val haut = 8f
        val largeur = size.width - gauche - 12f
        val hauteur = size.height - 26f - haut
        if (largeur <= 0f || hauteur <= 0f) return@Canvas
        val tous = series.flatMap { s -> s.points.map { it.second } }
        val yMin = minOf(40.0, tous.minOrNull() ?: 40.0)
        val yMax = maxOf(300.0, (tous.maxOrNull() ?: 300.0) + 20)
        fun y(v: Double) = haut + hauteur * (1f - ((v - yMin) / (yMax - yMin)).toFloat())
        fun x(minute: Int) = gauche + largeur * (minute / 1440f)
        axeJournee(mesureur, style, gauche, haut, largeur, hauteur, ::y)
        series.forEach { s ->
            val pts = s.points.sortedBy { it.first }
            if (pts.size > 1) {
                val chemin = Path()
                pts.forEachIndexed { i, p -> if (i == 0) chemin.moveTo(x(p.first), y(p.second)) else chemin.lineTo(x(p.first), y(p.second)) }
                drawPath(chemin, s.couleur, style = Stroke(width = s.epaisseur))
            }
            pts.forEach { p ->
                drawCircle(if (s.epaisseur >= 2f) couleurGlycemie(p.second) else s.couleur, radius = if (s.epaisseur >= 2f) 4.5f else 2.5f,
                    center = Offset(x(p.first), y(p.second)))
            }
        }
    }
}

/** Profil glycemique par tranche de 2 h : bandes 10-90 % et 25-75 %, mediane. */
@Composable
fun CourbeProfil(tranches: List<StatsGlycemie.Tranche>, modifier: Modifier = Modifier) {
    val mesureur = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = Color.Gray)
    Canvas(modifier) {
        val gauche = 44f; val haut = 8f
        val largeur = size.width - gauche - 12f
        val hauteur = size.height - 26f - haut
        if (largeur <= 0f || hauteur <= 0f) return@Canvas
        val yMin = minOf(40.0, tranches.minOfOrNull { it.p10 } ?: 40.0)
        val yMax = maxOf(300.0, (tranches.maxOfOrNull { it.p90 } ?: 300.0) + 20)
        fun y(v: Double) = haut + hauteur * (1f - ((v - yMin) / (yMax - yMin)).toFloat())
        fun x(heure: Double) = gauche + largeur * (heure / 24.0).toFloat()
        axeJournee(mesureur, style, gauche, haut, largeur, hauteur, ::y)
        if (tranches.isEmpty()) return@Canvas
        fun bande(bas: (StatsGlycemie.Tranche) -> Double, hautV: (StatsGlycemie.Tranche) -> Double, couleur: Color) {
            if (tranches.size == 1) {
                val t = tranches[0]
                drawRect(couleur, Offset(x(t.heure.toDouble()), y(hautV(t))), Size(largeur / 12f, y(bas(t)) - y(hautV(t))))
                return
            }
            val chemin = Path()
            tranches.forEachIndexed { i, t -> val px = x(t.heure + 1.0); if (i == 0) chemin.moveTo(px, y(hautV(t))) else chemin.lineTo(px, y(hautV(t))) }
            tranches.reversed().forEach { t -> chemin.lineTo(x(t.heure + 1.0), y(bas(t))) }
            chemin.close()
            drawPath(chemin, couleur)
        }
        bande({ it.p10 }, { it.p90 }, Indigo.copy(alpha = 0.15f))
        bande({ it.p25 }, { it.p75 }, Indigo.copy(alpha = 0.30f))
        val mediane = Path()
        tranches.forEachIndexed { i, t -> val px = x(t.heure + 1.0); if (i == 0) mediane.moveTo(px, y(t.mediane)) else mediane.lineTo(px, y(t.mediane)) }
        drawPath(mediane, IndigoFonce, style = Stroke(width = 2.5f))
        tranches.forEach { t -> drawCircle(IndigoFonce, radius = 3.5f, center = Offset(x(t.heure + 1.0), y(t.mediane))) }
    }
}

/** Barre verticale empilee du « temps dans la cible » (% de mesures par plage). */
@Composable
fun BarreCible(parPlage: Map<StatsGlycemie.Plage, Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        var yCourant = 0f
        StatsGlycemie.Plage.entries.forEach { p ->
            val h = size.height * ((parPlage[p] ?: 0.0) / 100.0).toFloat()
            if (h > 0f) drawRect(Color(p.couleur), Offset(0f, yCourant), Size(size.width, h))
            yCourant += h
        }
        if (yCourant == 0f) drawRect(Color(0xFFEDEEF3), Offset.Zero, size)
    }
}

/**
 * Journee detaillee (comme un releve de capteur) : courbe coloree selon la
 * plage, valeur ecrite sur chaque mesure, echelle de couleur sur les cotes,
 * et une ligne « Glucides » sous la courbe avec les repas du jour.
 * [fond] : autres jours superposes (gris clair).
 */
@Composable
fun CourbeJourDetaillee(
    points: List<Pair<Int, Double>>,
    repas: List<Pair<Int, Double>>,
    modifier: Modifier = Modifier,
    fond: List<SerieJour> = emptyList()
) {
    val mesureur = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, color = Color.Gray)
    val styleValeur = TextStyle(fontSize = 10.sp, color = Color.White)
    Canvas(modifier) {
        val gauche = 56f; val droite = 22f; val haut = 8f
        val voieGlucides = if (repas.isEmpty()) 0f else 30f
        val largeur = size.width - gauche - droite
        val hauteur = size.height - 26f - haut - voieGlucides
        if (largeur <= 0f || hauteur <= 0f) return@Canvas
        val tous = points.map { it.second } + fond.flatMap { s -> s.points.map { it.second } }
        val yMin = minOf(40.0, tous.minOrNull() ?: 40.0)
        val yMax = maxOf(300.0, (tous.maxOrNull() ?: 300.0) + 30)
        fun y(v: Double) = haut + hauteur * (1f - ((v - yMin) / (yMax - yMin)).toFloat())
        fun x(minute: Int) = gauche + largeur * (minute / 1440f)
        axeJournee(mesureur, style, gauche, haut, largeur, hauteur, ::y)

        // Echelle de couleur a gauche et a droite (comme la legende du carnet)
        val bornes = listOf(yMin to 54.0, 54.0 to 70.0, 70.0 to 180.0, 180.0 to 250.0, 250.0 to yMax)
        val plages = listOf(StatsGlycemie.Plage.TRES_BAS, StatsGlycemie.Plage.BAS, StatsGlycemie.Plage.CIBLE,
            StatsGlycemie.Plage.HAUT, StatsGlycemie.Plage.TRES_HAUT)
        bornes.zip(plages).forEach { (b, pl) ->
            val y1 = y(b.second); val y2 = y(b.first)
            if (y2 > y1) {
                drawRect(Color(pl.couleur), Offset(gauche - 50f, y1), Size(5f, y2 - y1))
                drawRect(Color(pl.couleur), Offset(gauche + largeur + 8f, y1), Size(5f, y2 - y1))
            }
        }

        fond.forEach { s ->
            val pts = s.points.sortedBy { it.first }
            if (pts.size > 1) {
                val chemin = Path()
                pts.forEachIndexed { i, p -> if (i == 0) chemin.moveTo(x(p.first), y(p.second)) else chemin.lineTo(x(p.first), y(p.second)) }
                drawPath(chemin, s.couleur, style = Stroke(width = s.epaisseur))
            }
        }

        // Segments colores : couleur de la plage atteinte au milieu du segment
        val pts = points.sortedBy { it.first }
        pts.zipWithNext().forEach { (a, b) ->
            val milieu = (a.second + b.second) / 2
            drawLine(Color(StatsGlycemie.Plage.de(milieu).couleur), Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 3f)
        }
        pts.forEach { p ->
            val c = Color(StatsGlycemie.Plage.de(p.second).couleur)
            val centre = Offset(x(p.first), y(p.second))
            val r = mesureur.measure(p.second.toInt().toString(), styleValeur)
            val rayon = maxOf(r.size.width, r.size.height) / 2f + 3f
            drawCircle(c, rayon, centre)
            drawCircle(Color.White, rayon, centre, style = Stroke(1.5f))
            drawText(r, topLeft = Offset(centre.x - r.size.width / 2f, centre.y - r.size.height / 2f))
        }

        if (repas.isNotEmpty()) {
            val yVoie = haut + hauteur + 26f
            val etiquette = mesureur.measure("Glucides", style)
            drawText(etiquette, topLeft = Offset(0f, yVoie + 2f))
            repas.forEach { (minute, g) ->
                val t = mesureur.measure("${g.toInt()} g", styleValeur)
                val px = (x(minute) - t.size.width / 2f - 4f).coerceIn(gauche, gauche + largeur - t.size.width - 8f)
                drawRect(Rouge.copy(alpha = 0.85f), Offset(px, yVoie), Size(t.size.width + 8f, t.size.height + 4f))
                drawText(t, topLeft = Offset(px + 4f, yVoie + 2f))
            }
        }
    }
}
