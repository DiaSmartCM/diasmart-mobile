package com.diabeto.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.diabeto.ui.theme.OnSurfaceVariant
import com.diabeto.ui.theme.Primary

/**
 * Rendu des reponses de ROLLY.
 *
 * Les modeles renvoient du Markdown meme quand on leur demande de s'en passer :
 * l'utilisateur voyait donc "**Tendance generale :**" avec les asterisques a
 * l'ecran. Plutot que de compter sur le prompt seul, on nettoie et on met en
 * forme ici — c'est le seul endroit ou l'on est sur du resultat.
 *
 * Trois formes reconnues : titre de section, puce, paragraphe. Le gras interne
 * est converti en style, jamais affiche tel quel.
 */

private sealed interface Bloc {
    data class Titre(val texte: String) : Bloc
    data class Puce(val texte: String) : Bloc
    data class Para(val texte: String) : Bloc
}

/** Supprime les marques Markdown que Compose n'interprete pas. */
private fun degrossir(ligne: String): String =
    ligne
        .replace(Regex("`{1,3}"), "")
        .replace(Regex("~~"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .trim()

/** Convertit **gras** en style et retire les asterisques orphelins. */
private fun enrichir(source: String, couleurGras: Color): AnnotatedString = buildAnnotatedString {
    val motif = Regex("\\*\\*(.+?)\\*\\*")
    var curseur = 0
    for (m in motif.findAll(source)) {
        append(source.substring(curseur, m.range.first).replace("*", ""))
        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = couleurGras))
        append(m.groupValues[1].trim())
        pop()
        curseur = m.range.last + 1
    }
    append(source.substring(curseur).replace("*", ""))
}

private fun decouper(brut: String): List<Bloc> {
    val blocs = mutableListOf<Bloc>()

    for (ligneBrute in brut.lines()) {
        var l = degrossir(ligneBrute)
        if (l.isEmpty()) continue

        // Titre Markdown (### Titre)
        if (l.startsWith("#")) {
            blocs += Bloc.Titre(l.trimStart('#').trim().replace("*", "").trimEnd(':', '：'))
            continue
        }

        // Puce : -, *, • ou "1." en debut de ligne
        val puce = Regex("^([-*•·]|\\d+[.)])\\s+")
        val correspondance = puce.find(l)
        if (correspondance != null) {
            val contenu = l.removeRange(correspondance.range).trim()
            // "1. **Tendance generale :**" est un titre deguise en puce :
            // la ligne entiere est en gras et se termine par deux-points.
            val entierementGras = Regex("^\\*\\*(.+?)\\*\\*[:：]?$").find(contenu)
            if (entierementGras != null) {
                blocs += Bloc.Titre(entierementGras.groupValues[1].trim().trimEnd(':', '：'))
            } else {
                blocs += Bloc.Puce(contenu)
            }
            continue
        }

        // Ligne courte entierement en gras, ou finissant par ":" => titre
        val grasSeul = Regex("^\\*\\*(.+?)\\*\\*[:：]?$").find(l)
        if (grasSeul != null) {
            blocs += Bloc.Titre(grasSeul.groupValues[1].trim().trimEnd(':', '：'))
            continue
        }
        if (l.length <= 40 && (l.endsWith(":") || l.endsWith("：")) && !l.contains(". ")) {
            blocs += Bloc.Titre(l.trimEnd(':', '：').replace("*", ""))
            continue
        }

        blocs += Bloc.Para(l)
    }
    return blocs
}

@Composable
fun AiRichText(
    texte: String,
    modifier: Modifier = Modifier,
    couleurAccent: Color = Primary,
) {
    val blocs = remember(texte) { decouper(texte) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocs.forEachIndexed { i, bloc ->
            when (bloc) {
                is Bloc.Titre -> {
                    if (i > 0) Spacer(Modifier.height(4.dp))
                    Text(
                        bloc.texte,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = couleurAccent,
                    )
                }

                is Bloc.Puce -> Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 7.dp, end = 10.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(couleurAccent.copy(alpha = 0.55f))
                    )
                    Text(
                        enrichir(bloc.texte, couleurAccent),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                    )
                }

                is Bloc.Para -> Text(
                    enrichir(bloc.texte, couleurAccent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
