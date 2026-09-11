package com.diabeto.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.diabeto.BuildConfig
import com.diabeto.R
import com.diabeto.data.repository.PreferencesRepository
import com.diabeto.util.AppUpdateChecker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Écran de démarrage DiaSmart.
 *
 * La marque occupe le haut. Une sphère blanche en tombe, rebondit vers la
 * gauche et fait surgir une lettre à chaque impact — « t », « r », « a »,
 * « m », « S », « a », puis « D ». Le nom écrit, elle repart vers la droite
 * et se pose au-dessus de la tige du « i », dont elle devient le point.
 *
 * Tout est dessiné dans un repère de 400 × 780, mis à l'échelle sur la
 * largeur de l'écran. Les constantes de mouvement — restitution, budget des
 * rebonds — sont les mêmes que celles de la maquette de validation et que
 * celles du générateur de la sonorité : si l'une bouge, les trois doivent
 * bouger ensemble.
 */

// ── Repère de dessin ──────────────────────────────────────────────────
private const val L = 400f          // largeur de référence
private const val H = 780f          // hauteur de référence

private const val D_X = 125f
private const val D_Y = 96f
private const val D_L = 150f
private const val D_H = 180f

private const val SOL = 470f        // ligne de base du mot
private const val R_BILLE = 34f     // rayon pendant la chute et les rebonds
private const val R_POINT = 9f      // rayon une fois devenue le point du « i »
private const val CORPS = 46f       // taille du nom
private const val SOL_BILLE = SOL - R_BILLE

// Proportions d'un bas-de-casse en graisse 700.
private const val TIGE_L = 0.115f
private const val HAUT_X = 0.52f
private const val HAUT_POINT = 0.80f

// ── Découpage temporel, en millisecondes ──────────────────────────────
private const val T_MARQUE = 800f
private const val T_CHUTE = 1350f
private const val T_REBONDS_FIN = 2900f
private const val T_POSE = 3700f
private const val T_TASSE = 4020f
private const val TOTAL = 4500f

/** Part de vitesse conservée à chaque impact. Tout le reste en découle. */
private const val REST = 0.74f

private val ORDRE = intArrayOf(7, 6, 5, 4, 3, 2, 0)

private val BLANC = Color(0xFFF2F4FE)
private val BLEU_MOT = Color(0xFF2C86FF)
private val VIOLET_MOT = Color(0xFF7B45F0)

// ── Petites fonctions d'allure ────────────────────────────────────────
private fun bornes(v: Float, a: Float, b: Float) = max(a, min(b, v))
private fun prog(t: Float, a: Float, b: Float) = bornes((t - a) / (b - a), 0f, 1f)
private fun melange(a: Float, b: Float, p: Float) = a + (b - a) * p
private fun sortie(p: Float) = 1f - (1f - p).pow(3)
private fun douce(p: Float) =
    if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface SplashEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}

/**
 * Joue la sonorité d'ouverture, sous trois conditions cumulées.
 *
 * Le réglage de l'utilisateur ne suffit pas. Une application de santé qui
 * sonne pendant une consultation se fait désinstaller : on vérifie donc
 * aussi le mode de sonnerie du téléphone. Et le son part sur le flux
 * multimédia, jamais alarme ni notification — c'est le seul où baisser le
 * volume a l'effet attendu.
 */
private fun jouerOuverture(context: Context) {
    try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val attributs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val lecteur = MediaPlayer.create(
            context, R.raw.son_ouverture, attributs, audio.generateAudioSessionId()
        ) ?: return
        lecteur.setVolume(0.6f, 0.6f)
        lecteur.setOnCompletionListener { it.release() }
        lecteur.start()
    } catch (_: Exception) {
        // Une sonorité qui échoue ne doit jamais retarder l'ouverture.
    }
}

/** Une lettre du nom, avec sa position déjà crénée. */
private data class Lettre(
    val texte: String,
    val couleur: Color,
    val x: Float,
    val largeur: Float,
    val tige: Boolean,
    val mise: TextLayoutResult?,
)

@Composable
fun SplashScreen(
    onSplashFinished: (isLoggedIn: Boolean) -> Unit,
    isUserLoggedIn: Boolean,
) {
    val context = LocalContext.current
    val horloge = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val entree = EntryPointAccessors.fromApplication(
            context.applicationContext, SplashEntryPoint::class.java
        )
        val prefs = entree.preferencesRepository()

        if (prefs.sonDemarrage.first()) jouerOuverture(context)

        // Vérification de mise à jour en arrière-plan : jamais bloquante.
        launch {
            try {
                val info = AppUpdateChecker(context).checkForUpdate()
                if (info != null && info.apkUrl.isNotBlank()) {
                    prefs.setPendingUpdate(
                        version = info.versionName,
                        url = info.apkUrl,
                        changelog = info.changelog,
                        force = info.forceUpdate,
                    )
                }
            } catch (_: Exception) {
            }
        }

        launch {
            horloge.animateTo(TOTAL, tween(TOTAL.toInt(), easing = LinearEasing))
        }
        delay(TOTAL.toLong())
        onSplashFinished(isUserLoggedIn)
    }

    val marque = ImageBitmap.imageResource(R.drawable.logo_d)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeurPx = constraints.maxWidth.toFloat()
        val hauteurPx = constraints.maxHeight.toFloat()
        val e = largeurPx / L
        val decalageY = (hauteurPx - H * e) / 2f

        val mesureur = rememberTextMeasurer()
        val densite = LocalDensity.current

        /* Les lettres sont mesurées DANS le mot, par préfixes successifs.
           Mesurer chaque glyphe isolément puis cumuler les chasses perdrait
           le crénage, et les lettres se décaleraient les unes par rapport
           aux autres — d'autant plus qu'on avance dans le mot. */
        val lettres = remember(e) {
            val style = TextStyle(
                fontSize = with(densite) { (CORPS * e).toSp() },
                fontWeight = FontWeight.Bold,
            )
            val mot = "DiaSmart"
            // « Dia » en blanc, « Smart » en dégradé du bleu au violet.
            // Une lettre ne peut porter qu'une couleur : on échantillonne le
            // dégradé lettre par lettre plutôt que de le couper en deux blocs.
            val couleurs = listOf(BLANC, BLANC, BLANC) +
                (0..4).map { k ->
                    androidx.compose.ui.graphics.lerp(BLEU_MOT, VIOLET_MOT, k / 4f)
                }
            val total = mesureur.measure(mot, style).size.width.toFloat()
            var cumul = 0f
            var x = L / 2f * e - total / 2f
            mot.mapIndexed { i, c ->
                val jusquIci = mesureur.measure(mot.substring(0, i + 1), style).size.width.toFloat()
                val larg = jusquIci - cumul
                cumul = jusquIci
                val tige = i == 1
                val l = Lettre(
                    texte = c.toString(),
                    couleur = couleurs[i],
                    x = x,
                    largeur = larg,
                    tige = tige,
                    // Le « i » n'est pas composé : le glyphe porte son propre
                    // point, précisément là où la bille doit se poser.
                    mise = if (tige) null
                    else mesureur.measure(
                        c.toString(), style.copy(color = couleurs[i])
                    ),
                )
                x += larg
                l
            }
        }

        // Points de contact et arcs, dérivés de la restitution.
        val arcs = remember(lettres) { construireArcs(lettres) }

        Canvas(Modifier.fillMaxSize()) {
            val t = horloge.value
            fun px(v: Float) = v * e
            fun py(v: Float) = v * e + decalageY

            dessinerFond(e, decalageY, largeurPx, hauteurPx)

            // ── La marque ──
            val entreeD = sortie(prog(t, 60f, 760f))
            if (entreeD > 0f) {
                val ech = melange(0.86f, 1f, entreeD)
                val cx = px(D_X + D_L / 2f)
                val cy = py(D_Y + D_H / 2f)
                scale(ech, ech, Offset(cx, cy)) {
                    drawImage(
                        image = marque,
                        dstOffset = IntOffset(px(D_X).roundToInt(), py(D_Y).roundToInt()),
                        dstSize = IntSize(px(D_L).roundToInt(), px(D_H).roundToInt()),
                        alpha = entreeD,
                    )
                }
            }

            // ── La bille ──
            val etat = positionBille(t, arcs, lettres)
            if (etat.opacite > 0f) {
                val rr = px(etat.rayon)
                // `etat.x` est deja en pixels ecran : les arcs sont bâtis sur
                // les lettres mesurees. Le repasser par px() le multipliait
                // une seconde fois par l'echelle, et la bille partait loin a
                // droite du mot. Seule la verticale est en unites du repere.
                translate(etat.x, py(etat.y)) {
                    scale(2f - etat.ecrase, etat.ecrase, Offset.Zero) {
                        rotate(etat.angle, Offset.Zero) {
                            // Intérieur bleu, révélé par l'ouverture de la coque.
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFF3E7FF0), Color(0xFF1249BE), Color(0xFF021C63)),
                                    center = Offset(-rr * 0.16f, -rr * 0.22f),
                                    radius = rr * 1.7f,
                                ),
                                radius = rr,
                                center = Offset.Zero,
                                alpha = etat.opacite,
                            )
                            val revele = prog(t, T_CHUTE + 450f, T_REBONDS_FIN - 400f)
                            if (revele > 0f) {
                                drawPath(
                                    cheminGoutte(rr * 0.98f),
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFFFF5E73), Color(0xFFE01E32), Color(0xFF9E0A20)),
                                        start = Offset(-rr, -rr), end = Offset(rr, rr),
                                    ),
                                    alpha = etat.opacite * revele,
                                )
                            }
                            val ouverture = douce(prog(t, T_CHUTE, T_REBONDS_FIN))
                            drawPath(
                                cheminCoque(rr, melange(-rr, rr * 0.44f, ouverture)),
                                brush = Brush.linearGradient(
                                    listOf(Color.White, Color(0xFFE4EAF6), Color(0xFFA6BADD)),
                                    start = Offset(-rr, -rr), end = Offset(rr, rr),
                                ),
                                alpha = etat.opacite,
                            )
                        }
                    }
                }
            }

            // ── Les lettres, une par impact ──
            val avancee = etat.avancee
            lettres.forEachIndexed { i, lettre ->
                /* La tige du « i » ne figure pas dans ORDRE : cette table ne
                   liste que les lettres qu'un rebond fait surgir, et la bille
                   ne rebondit pas sur le « i », elle s'y pose. Tirée de cette
                   table, la tige recevait le rang -1 et n'était jamais
                   dessinée — le nom s'affichait « D aSmart ». Elle suit donc
                   son propre calendrier, calé sur la pose de la bille. */
                if (lettre.tige) {
                    val venue = bornes(prog(t, T_REBONDS_FIN + 350f, T_POSE), 0f, 1f)
                    if (venue <= 0f) return@forEachIndexed
                    val lt = px(CORPS * TIGE_L)
                    val hx = px(CORPS * HAUT_X)
                    drawRoundRect(
                        color = BLANC.copy(alpha = melange(0.2f, 1f, venue)),
                        topLeft = Offset(
                            lettre.x + lettre.largeur / 2f - lt / 2f,
                            py(SOL) - hx + px(melange(9f, 0f, sortie(venue))),
                        ),
                        size = Size(lt, hx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(lt / 2f),
                    )
                    return@forEachIndexed
                }

                val rang = ORDRE.indexOf(i)
                if (rang < 0 || etat.contacts <= rang) return@forEachIndexed
                val jeune = bornes((avancee - rang) / 0.32f, 0f, 1f)
                val mise = lettre.mise ?: return@forEachIndexed
                drawText(
                    textLayoutResult = mise,
                    topLeft = Offset(
                        lettre.x,
                        py(SOL) - mise.firstBaseline + px(melange(11f, 0f, sortie(jeune))),
                    ),
                    alpha = melange(0.2f, 1f, jeune),
                )
            }

            // ── Slogan, barre de chargement, version ──
            dessinerPied(t, e, decalageY, largeurPx, mesureur, densite)
        }
    }
}

// ── Arcs de rebond ────────────────────────────────────────────────────

private class Arcs(
    val depart: FloatArray,
    val arrivee: FloatArray,
    val hauteurs: FloatArray,
    val bornesArc: FloatArray,
)

private fun construireArcs(lettres: List<Lettre>): Arcs {
    val n = ORDRE.size - 1
    val durees = FloatArray(n) { REST.pow(it) }
    val somme = durees.sum()
    for (k in 0 until n) durees[k] /= somme

    // Vitesse d'arrivée de la chute : pour une chute à accélération
    // constante partant du repos, elle vaut deux fois la vitesse moyenne.
    val hauteurChute = SOL_BILLE - (D_Y + D_H - 24f)
    val vImpact = 2f * hauteurChute / (T_CHUTE - T_MARQUE)
    val t0 = durees[0] * (T_REBONDS_FIN - T_CHUTE)
    // Pour l'arc y = sol − 4h·u(1−u), la vitesse de départ vaut 4h/T.
    val h0 = min(REST * vImpact * t0 / 4f, 74f)

    val depart = FloatArray(n)
    val arrivee = FloatArray(n)
    val hauteurs = FloatArray(n)
    val bornesArc = FloatArray(n + 1)
    var borne = 0f
    for (k in 0 until n) {
        depart[k] = centre(lettres, ORDRE[k])
        arrivee[k] = centre(lettres, ORDRE[k + 1])
        hauteurs[k] = h0 * REST.pow(2 * k)
        bornesArc[k] = borne
        borne += durees[k]
    }
    bornesArc[n] = 1f
    return Arcs(depart, arrivee, hauteurs, bornesArc)
}

private fun centre(lettres: List<Lettre>, i: Int) = lettres[i].x + lettres[i].largeur / 2f

private class EtatBille(
    val x: Float, val y: Float, val rayon: Float, val angle: Float,
    val ecrase: Float, val opacite: Float, val contacts: Int, val avancee: Float,
)

/**
 * Position de la bille à l'instant [t].
 *
 * `x` est en pixels écran (les arcs viennent des lettres déjà mesurées) ;
 * `y` et `rayon` sont dans le repère de 400 × 780.
 */
private fun DrawScope.positionBille(t: Float, arcs: Arcs, lettres: List<Lettre>): EtatBille {
    val e = size.width / L
    val xMarque = (L / 2f + 26f) * e
    val xPremier = arcs.depart[0]
    val iCible = centre(lettres, 1)

    var x: Float
    var y: Float
    var rayon = R_BILLE
    var ecrase = 1f
    var opacite = 0f
    var contacts = 0
    var avancee = 0f

    if (t < T_MARQUE) {
        x = xMarque; y = D_Y + D_H - 24f
    } else {
        opacite = bornes(prog(t, T_MARQUE, 950f), 0f, 1f)
        val pc = prog(t, T_MARQUE, T_CHUTE)
        // Horizontale à vitesse constante, verticale quadratique : un
        // projectile ne freine pas latéralement, et tombe en accélérant.
        x = melange(xMarque, xPremier, pc)
        y = melange(D_Y + D_H - 24f, SOL_BILLE, pc * pc)
        // Le dégagement de la marque est bref : étalé sur la chute, il se
        // lirait comme une apparition et non comme une sortie.
        rayon = R_BILLE * melange(0.62f, 1f, sortie(prog(t, T_MARQUE, 980f)))
    }

    val pRb = prog(t, T_CHUTE, T_REBONDS_FIN)
    if (t >= T_CHUTE) {
        var k = arcs.hauteurs.size - 1
        for (j in arcs.hauteurs.indices) {
            if (pRb >= arcs.bornesArc[j] && pRb < arcs.bornesArc[j + 1]) { k = j; break }
        }
        val u = bornes(
            (pRb - arcs.bornesArc[k]) / (arcs.bornesArc[k + 1] - arcs.bornesArc[k]), 0f, 1f
        )
        x = melange(arcs.depart[k], arcs.arrivee[k], u)
        y = SOL_BILLE - 4f * arcs.hauteurs[k] * u * (1f - u)
        contacts = k + 1
        avancee = k + u
        // Écrasement au contact, proportionnel à l'énergie du bond.
        val pres = max(0f, 1f - min(u, 1f - u) * 9f)
        ecrase = 1f - pres * (arcs.hauteurs[k] / max(arcs.hauteurs[0], 1f)) * 0.16f
        rayon = R_BILLE
    }
    if (t >= T_CHUTE) contacts = max(contacts, 1)
    if (t >= T_REBONDS_FIN) contacts = ORDRE.size
    avancee += prog(t, T_REBONDS_FIN, 3300f) * 1.1f

    val cibleI = SOL - CORPS * HAUT_POINT
    if (t >= T_REBONDS_FIN) {
        val pM = prog(t, T_REBONDS_FIN, T_POSE)
        // Même formule que les rebonds : `u` reste linéaire, c'est le temps.
        y = melange(SOL_BILLE, cibleI, pM) - 4f * 42f * pM * (1f - pM)
        val ax = if (pM < 0.8f) pM else 0.8f + 0.2f * (1f - (1f - (pM - 0.8f) / 0.2f).pow(2))
        x = melange(arcs.arrivee.last(), iCible, ax)
        rayon = melange(R_BILLE, R_POINT, douce(bornes(pM / 0.72f, 0f, 1f)))
        ecrase = 1f
    }
    if (t >= T_POSE) {
        val ps = prog(t, T_POSE, T_TASSE)
        x = iCible
        y = cibleI - abs(sin(ps * Math.PI.toFloat() * 2.4f)) * 4.5f * exp(-5.2f * ps)
        rayon = R_POINT
    }

    // La rotation ne s'accumule qu'après le premier contact : la calculer
    // pendant la chute ferait rouler la bille en l'air.
    var angle = if (t < T_CHUTE) prog(t, T_MARQUE, T_CHUTE) * 22f
    else 22f + (x - xPremier) / (R_BILLE * e) * (180f / Math.PI.toFloat())
    val redresse = douce(prog(t, T_REBONDS_FIN, T_POSE))
    angle -= (angle - (angle / 360f).roundToInt() * 360f) * redresse

    return EtatBille(x, y, rayon, angle, ecrase, opacite, contacts, avancee)
}

// ── Tracés ────────────────────────────────────────────────────────────

/**
 * Coque blanche. [k] va de −r (sphère pleine) à +0,44 r (fin croissant à
 * droite). L'arc de retour bombe à gauche tant que k est négatif et recouvre
 * tout ; il bascule à droite ensuite. C'est ce basculement qui donne la
 * lecture d'une coque qui s'ouvre, et non d'une couleur qui change.
 */
private fun cheminCoque(r: Float, k: Float): Path {
    val rx = max(abs(k), 0.001f)
    return Path().apply {
        arcTo(Rect(-r, -r, r, r), -90f, 180f, true)
        arcTo(Rect(-rx, -r, rx, r), 90f, if (k < 0f) 180f else -180f, false)
        close()
    }
}

/** Goutte : pointe en haut, ventre rond en bas. */
private fun cheminGoutte(h: Float): Path {
    val l = h * 0.62f
    val r = l * 0.52f
    val cy = h * 0.18f
    return Path().apply {
        moveTo(0f, -h / 2f)
        cubicTo(l * 0.42f, -h * 0.10f, r, h * 0.06f, r, cy)
        arcTo(Rect(-r, cy - r, r, cy + r), 0f, 180f, false)
        cubicTo(-r, h * 0.06f, -l * 0.42f, -h * 0.10f, 0f, -h / 2f)
        close()
    }
}

// ── Fond et pied d'écran ──────────────────────────────────────────────

/**
 * Fond en verre dépoli.
 *
 * L'effet ne vient pas d'un flou marqué mais de l'inverse : des aplats
 * colorés nets, vus au travers d'un voile translucide bordé d'un liseré.
 */
private fun DrawScope.dessinerFond(e: Float, decalageY: Float, larg: Float, haut: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF0D0B2E), 0.34f to Color(0xFF1A1452),
            0.68f to Color(0xFF120F3D), 1f to Color(0xFF0D0B2E),
        ),
        size = Size(larg, haut),
    )
    fun cercle(x: Float, y: Float, r: Float, c: Long) =
        drawCircle(Color(c), r * e, Offset(x * e, y * e + decalageY), alpha = 0.5f)
    cercle(96f, 206f, 132f, 0xFF4A4FA8)
    cercle(318f, 296f, 94f, 0xFF7A4470)
    cercle(120f, 598f, 80f, 0xFF3E4392)
    cercle(300f, 700f, 110f, 0xFF454AA0)

    // Voile : plus dense en haut et en bas, comme une lumière rasante.
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.085f),
            0.42f to Color(0xFFC9CBFF).copy(alpha = 0.022f),
            1f to Color.White.copy(alpha = 0.055f),
            start = Offset(larg * 0.15f, 0f), end = Offset(larg * 0.7f, haut),
        ),
        size = Size(larg, haut),
    )
    // Liseré : l'arête du panneau. C'est lui qui fait basculer la lecture
    // de « voile » à « plaque de verre ».
    drawRoundRect(
        color = Color.White.copy(alpha = 0.11f),
        topLeft = Offset(6f * e, 6f * e),
        size = Size(larg - 12f * e, haut - 12f * e),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f * e),
        style = Stroke(width = 1f * e),
    )
}

private fun DrawScope.dessinerPied(
    t: Float, e: Float, decalageY: Float, larg: Float,
    mesureur: TextMeasurer, densite: androidx.compose.ui.unit.Density,
) {
    fun py(v: Float) = v * e + decalageY

    val slogan = bornes(prog(t, 3820f, 4260f), 0f, 1f)
    if (slogan > 0f) {
        val mise = mesureur.measure(
            "Diabétologie Intelligente",
            TextStyle(
                fontSize = with(densite) { (15f * e).toSp() },
                letterSpacing = with(densite) { (1.6f * e).toSp() },
                color = Color(0xFFB9C0F5),
            ),
        )
        drawText(
            mise,
            topLeft = Offset(larg / 2f - mise.size.width / 2f, py(508f) - mise.firstBaseline),
            alpha = slogan,
        )
    }

    // La barre avance sur toute la durée : elle couvre l'attente, elle ne
    // la commente pas à la fin.
    val chargement = bornes(prog(t, 150f, 600f), 0f, 1f)
    if (chargement > 0f) {
        val largeurBarre = 120f * e
        val gauche = larg / 2f - largeurBarre / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.15f * chargement),
            topLeft = Offset(gauche, py(686f)),
            size = Size(largeurBarre, 3f * e),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * e),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF6771E4), Color(0xFF4F58C2), Color(0xFF6771E4)),
                startX = gauche, endX = gauche + largeurBarre,
            ),
            topLeft = Offset(gauche, py(686f)),
            size = Size(largeurBarre * douce(t / TOTAL), 3f * e),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * e),
            alpha = chargement,
        )
        val version = mesureur.measure(
            "v${BuildConfig.VERSION_NAME}",
            TextStyle(
                fontSize = with(densite) { (11f * e).toSp() },
                color = Color.White.copy(alpha = 0.32f),
            ),
        )
        drawText(
            version,
            topLeft = Offset(larg / 2f - version.size.width / 2f, py(738f) - version.firstBaseline),
            alpha = chargement,
        )
    }
}
