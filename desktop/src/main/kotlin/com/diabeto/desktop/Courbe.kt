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
