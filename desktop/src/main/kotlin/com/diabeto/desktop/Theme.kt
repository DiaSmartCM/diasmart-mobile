package com.diabeto.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.PlatformLocalization
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
//  Style DiaSmart PC : angles droits, bandeaux de titre indigo, tableaux
// ═══════════════════════════════════════════════════════════════════════

val IndigoFonce = Color(0xFF4C55C4)
val Fond = Color(0xFFF3F4F8)
val Bordure = Color(0xFFDADDE8)
val EnteteTableau = Color(0xFFE9EBF5)

private val carre = RoundedCornerShape(0.dp)

/** Toutes les formes Material a angle droit. */
val FormesCarrees = Shapes(extraSmall = carre, small = carre, medium = carre, large = carre, extraLarge = carre)

val CouleursDiaSmart = lightColorScheme(primary = Indigo, secondary = Indigo, background = Fond, surface = Color.White,
    surfaceContainerHighest = Color.White, surfaceContainerHigh = Color.White, surfaceContainer = Color.White)

// Boutons Material a angle droit (leur forme par defaut est arrondie et ne suit pas le theme).

@Composable
fun Button(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.Button(onClick, modifier, enabled, shape = RectangleShape, colors = colors,
    contentPadding = contentPadding, content = content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.OutlinedButton(onClick, modifier, enabled, shape = RectangleShape, colors = colors,
    border = BorderStroke(1.dp, if (enabled) Indigo else Bordure), content = content)

@Composable
fun TextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.TextButton(onClick, modifier, enabled, shape = RectangleShape, content = content)

/** Panneau blanc avec bandeau de titre indigo (titre en majuscules) et actions a droite. */
@Composable
fun Panneau(
    titre: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    contenu: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.background(Color.White).border(1.dp, Bordure)) {
        Row(Modifier.fillMaxWidth().background(Indigo).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titre.uppercase(), Modifier.weight(1f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp)
            actions()
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = contenu)
    }
}

/** Badge rectangulaire plein (compteurs du haut de page). */
@Composable
fun Badge(texte: String, couleur: Color = Indigo) {
    Text(texte, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(couleur).padding(horizontal = 12.dp, vertical = 6.dp))
}

/** Onglets texte souligne (comme « Vue generale · Carnet · Courbes »). */
@Composable
fun <T> Onglets(valeurs: List<T>, choisi: T, libelle: (T) -> String, onChoix: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).border(1.dp, Bordure)) {
        valeurs.forEach { v ->
            val actif = v == choisi
            Column(Modifier.width(IntrinsicSize.Max).clickable { onChoix(v) }) {
                Text(libelle(v), Modifier.padding(horizontal = 16.dp, vertical = 11.dp), fontSize = 13.sp,
                    color = if (actif) Indigo else Color.DarkGray, fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.height(3.dp).fillMaxWidth().background(if (actif) Indigo else Color.Transparent))
            }
        }
    }
}

/** Choix segmente rectangulaire (7 j / 30 j / 90 j...). */
@Composable
fun <T> Segments(valeurs: List<T>, choisi: T, libelle: (T) -> String, onChoix: (T) -> Unit) {
    Row(Modifier.border(1.dp, Indigo)) {
        valeurs.forEach { v ->
            val actif = v == choisi
            Text(libelle(v), Modifier.background(if (actif) Indigo else Color.White).clickable { onChoix(v) }
                .padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, color = if (actif) Color.White else Indigo)
        }
    }
}

/** Ligne d'en-tete de tableau. */
@Composable
fun EnteteLigne(cellules: List<Pair<String, Float>>) {
    Row(Modifier.fillMaxWidth().background(EnteteTableau).padding(horizontal = 14.dp, vertical = 9.dp)) {
        cellules.forEach { (t, poids) ->
            Text(t, Modifier.weight(poids), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF4A4F63))
        }
    }
}

@Composable
fun Espace(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun EspaceL(l: Int = 8) = Spacer(Modifier.width(l.dp))

// ═══════════════════════════════════════════════════════════════════════
//  Defilement au clavier : fleches, Page haut/bas, Debut/Fin, Espace
// ═══════════════════════════════════════════════════════════════════════

private const val PAS_FLECHE = 70f

private fun pas(key: Key, page: Float): Float? = when (key) {
    Key.DirectionDown -> PAS_FLECHE
    Key.DirectionUp -> -PAS_FLECHE
    Key.PageDown -> page
    Key.PageUp -> -page
    else -> null
}

/**
 * Zone qui defile a la souris ET au clavier. Elle prend le focus quand on
 * clique dedans (et a l'ouverture), sans gener les champs de saisie.
 */
@Composable
fun Modifier.defilementClavier(etat: ScrollState, focusAuDepart: Boolean = true): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    if (focusAuDepart) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press && !e.buttons.isSecondaryPressed) runCatching { focus.requestFocus() }
                }
            }
        }
        .focusRequester(focus)
        .onKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
            val page = (etat.viewportSize * 0.9f).coerceAtLeast(PAS_FLECHE)
            when (ev.key) {
                Key.MoveHome -> { scope.launch { etat.animateScrollTo(0) }; true }
                Key.MoveEnd -> { scope.launch { etat.animateScrollTo(etat.maxValue) }; true }
                else -> pas(ev.key, page)?.let { d -> scope.launch { etat.animateScrollBy(d) }; true } ?: false
            }
        }
        .focusable()
        .verticalScroll(etat)
}

/** Meme chose pour une liste (LazyColumn) : a mettre sur la LazyColumn avec son etat. */
@Composable
fun Modifier.defilementClavier(etat: LazyListState, focusAuDepart: Boolean = true): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    if (focusAuDepart) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press && !e.buttons.isSecondaryPressed) runCatching { focus.requestFocus() }
                }
            }
        }
        .focusRequester(focus)
        .onKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
            val page = (etat.layoutInfo.viewportSize.height * 0.9f).coerceAtLeast(PAS_FLECHE)
            when (ev.key) {
                Key.MoveHome -> { scope.launch { etat.animateScrollToItem(0) }; true }
                Key.MoveEnd -> { scope.launch { etat.animateScrollToItem((etat.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }; true }
                else -> pas(ev.key, page)?.let { d -> scope.launch { etat.animateScrollBy(d) }; true } ?: false
            }
        }
        .focusable()
}

/**
 * Textes copiables : on selectionne a la souris (glisser, ou double-clic sur un mot)
 * puis Ctrl+C, ou clic droit > Copier. A placer a l'interieur de la colonne qui
 * defile, pour garder le defilement au clavier.
 */
@Composable
fun Copiable(contenu: @Composable () -> Unit) = SelectionContainer(content = contenu)

/** Code bien visible (selectionnable) + bouton « Copier » vers le presse-papiers. */
@Composable
fun CodeACopier(code: String, modifier: Modifier = Modifier) {
    val pressePapiers = LocalClipboardManager.current
    var copie by remember(code) { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Copiable {
            Text(code, Modifier.background(EnteteTableau).padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Indigo)
        }
        OutlinedButton(onClick = { pressePapiers.setText(AnnotatedString(code)); copie = true }) {
            Text(if (copie) "Copié ✓" else "Copier")
        }
    }
}

/** Menus clic droit (textes et champs de saisie) en francais. */
object MenusFrancais : PlatformLocalization {
    override val copy = "Copier"
    override val cut = "Couper"
    override val paste = "Coller"
    override val selectAll = "Tout sélectionner"
}

val PaddingBouton = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/** Lien blanc dans un bandeau de titre indigo. */
@Composable
fun LienBandeau(texte: String, onClic: () -> Unit) {
    Text(texte, Modifier.border(1.dp, Color.White.copy(alpha = 0.7f)).clickable(onClick = onClic)
        .padding(horizontal = 10.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp)
}

/** Petite bulle d'aide qui apparait au survol de la souris. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Infobulle(texte: String, contenu: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Text(texte, Modifier.background(Color(0xFF2B2F42)).padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White, fontSize = 12.sp)
        },
        delayMillis = 300,
        content = contenu
    )
}

/** Petit bouton texte encadre, pour les tableaux (« Dossier », « Carnet »). */
@Composable
fun MiniBouton(texte: String, couleur: Color = Indigo, onClic: () -> Unit) {
    Text(texte, Modifier.border(1.dp, couleur).clickable(onClick = onClic).padding(horizontal = 8.dp, vertical = 3.dp),
        color = couleur, fontSize = 12.sp, maxLines = 1, softWrap = false)
}
