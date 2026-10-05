package com.diabeto.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diabeto.data.entity.TensionEntity
import com.diabeto.domain.CategorieTension
import com.diabeto.domain.ReglesTension
import com.diabeto.domain.MesureTension
import com.diabeto.ui.theme.OnSurfaceVariant
import com.diabeto.ui.theme.Primary
import com.diabeto.ui.viewmodel.TensionViewModel
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun CategorieTension.couleur(): Color = when (this) {
    CategorieTension.BASSE -> Color(0xFF1E88E5)
    CategorieTension.OBJECTIF, CategorieTension.OBJECTIF_AGE -> Color(0xFF2E7D32)
    CategorieTension.AU_DESSUS_OBJECTIF -> Color(0xFFF9A825)
    CategorieTension.HTA_DOMICILE -> Color(0xFFF57C00)
    CategorieTension.URGENCE -> Color(0xFFC62828)
}

private val formatDate = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** Onglet « Tension » de l'ecran Glycemie : saisie, courbe, historique. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TensionContent(viewModel: TensionViewModel = hiltViewModel()) {
    val tensions by viewModel.tensions.collectAsStateWithLifecycle()
    val saisie by viewModel.saisie.collectAsStateWithLifecycle()
    val age by viewModel.age.collectAsStateWithLifecycle()
    var aSupprimer by remember { mutableStateOf<TensionEntity?>(null) }
    val context = LocalContext.current
    fun choisirDate() {
        val base = saisie.dateHeure ?: LocalDateTime.now()
        android.app.DatePickerDialog(context, { _, a, m, j ->
            android.app.TimePickerDialog(context, { _, h, min ->
                viewModel.onDateHeure(LocalDateTime.of(a, m + 1, j, h, min))
            }, base.hour, base.minute, true).show()
        }, base.year, base.monthValue - 1, base.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        tensions.firstOrNull()?.let { t -> item { DerniereTensionCard(t, age) } }
        alertesTension(tensions).takeIf { it.isNotEmpty() }?.let { alertes -> item { AlertesCard(alertes) } }

        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nouvelle mesure", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Mesurez assis, au repos depuis 5 minutes, le bras posé à hauteur du cœur.",
                        fontSize = 12.sp, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(saisie.systolique, viewModel::onSystolique, label = { Text("PAS (haut)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(saisie.diastolique, viewModel::onDiastolique, label = { Text("PAD (bas)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(saisie.pouls, viewModel::onPouls, label = { Text("FC (pouls)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f))
                    }
                    Text("Position", fontSize = 13.sp, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ReglesTension.ASSIS to "Assis", ReglesTension.COUCHE to "Couché").forEach { (code, nom) ->
                            FilterChip(saisie.position == code, { viewModel.onPosition(code) }, label = { Text(nom) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ReglesTension.DEBOUT_1MIN to "Debout 1 min", ReglesTension.DEBOUT_3MIN to "Debout 3 min").forEach { (code, nom) ->
                            FilterChip(saisie.position == code, { viewModel.onPosition(code) }, label = { Text(nom) })
                        }
                    }
                    if (saisie.position != ReglesTension.ASSIS) Text(
                        "Test couché-debout : mesurez couché après 5 min de repos, puis debout à 1 min et à 3 min.",
                        fontSize = 12.sp, color = OnSurfaceVariant)
                    Text("Bras", fontSize = 13.sp, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(saisie.bras == ReglesTension.GAUCHE, { viewModel.onBras(ReglesTension.GAUCHE) }, label = { Text("Gauche") })
                        FilterChip(saisie.bras == ReglesTension.DROIT, { viewModel.onBras(ReglesTension.DROIT) }, label = { Text("Droit") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(saisie.traitement, viewModel::onTraitement)
                        Text("Traitement contre la tension en cours", fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Date : " + (saisie.dateHeure?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "maintenant"),
                            fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { choisirDate() }) { Text("Changer") }
                        if (saisie.dateHeure != null) TextButton(onClick = { viewModel.onDateHeure(null) }) { Text("Maintenant") }
                    }
                    saisie.systolique.toIntOrNull()?.let { s ->
                        saisie.diastolique.toIntOrNull()?.takeIf { ReglesTension.valide(s, it) }?.let { d ->
                            val c = ReglesTension.categorie(s, d, age)
                            Text("PP ${ReglesTension.pressionPulsee(s, d)} · PAM ${ReglesTension.pam(s, d)} mmHg · ${c.libelle}",
                                fontSize = 13.sp, color = c.couleur(), fontWeight = FontWeight.Medium)
                        }
                    }
                    saisie.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    saisie.message?.let { Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp) }
                    Button(onClick = viewModel::ajouter, modifier = Modifier.fillMaxWidth(),
                        enabled = saisie.systolique.isNotBlank() && saisie.diastolique.isNotBlank()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter (mmHg)")
                    }
                }
            }
        }

        if (tensions.size >= 2) item { CourbeTensionCard(tensions) }

        if (tensions.isNotEmpty()) {
            item { Text("Historique", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            items(tensions.take(60), key = { it.id }) { t -> LigneTension(t, age) { aSupprimer = t } }
        }
        item {
            Text(ReglesTension.AVERTISSEMENT + " Indication pour vous aider à suivre votre tension, pas un diagnostic. " +
                "Vos mesures sont visibles par vos soignants si vous les partagez.",
                fontSize = 11.sp, color = OnSurfaceVariant)
        }
    }

    aSupprimer?.let { t ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer cette mesure ?") },
            text = { Text("${ReglesTension.texte(t.systolique, t.diastolique)} du ${t.dateHeure.format(formatDate)}") },
            confirmButton = { TextButton(onClick = { viewModel.supprimer(t); aSupprimer = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun DerniereTensionCard(t: TensionEntity, age: Int?) {
    val c = t.categorie(age)
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = c.couleur().copy(alpha = 0.08f))) {
        Column(Modifier.padding(16.dp)) {
            Text("Dernière mesure · ${t.dateHeure.format(formatDate)}", fontSize = 12.sp, color = OnSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.systolique}/${t.diastolique}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = c.couleur())
                Spacer(Modifier.width(6.dp))
                Text("mmHg" + (t.pouls?.let { "  ·  FC $it" } ?: ""), fontSize = 13.sp, color = OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Text("Pression pulsée ${t.pressionPulsee()} · PAM ${t.pam()} mmHg · ${t.positionTexte()} · bras ${t.brasTexte()}" +
                (if (t.traitement) " · sous traitement" else ""), fontSize = 12.sp, color = OnSurfaceVariant)
            Text(c.libelle, fontWeight = FontWeight.SemiBold, color = c.couleur())
            Text(c.conseil, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CourbeTensionCard(tensions: List<TensionEntity>) {
    val depuis = LocalDateTime.now().minusDays(30)
    val periode = tensions.filter { it.dateHeure.isAfter(depuis) }.sortedBy { it.dateHeure }
    val moyenne = ReglesTension.moyenne(periode.map {
        MesureTension(it.dateHeure.toKotlinLocalDateTime(), it.systolique, it.diastolique)
    })
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Évolution (30 jours)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            moyenne?.let { (s, d) ->
                Text("Moyenne : $s/$d mmHg sur ${periode.size} mesures · PP ${s - d} · PAM ${ReglesTension.pam(s, d)} mmHg",
                    fontSize = 13.sp, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (periode.size < 2) Text("Pas assez de mesures ces 30 derniers jours.", fontSize = 13.sp, color = OnSurfaceVariant)
            else CourbeTension(periode, Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legende(Primary, "Haut (systolique)")
                Legende(Color(0xFF26A69A), "Bas (diastolique)")
            }
            Text("Lignes pointillées : 140 et 90 mmHg", fontSize = 11.sp, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun CourbeTension(mesures: List<TensionEntity>, modifier: Modifier) {
    val couleurDia = Color(0xFF26A69A)
    Canvas(modifier) {
        val p = 16f
        val w = size.width - 2 * p
        val h = size.height - 2 * p
        val yMin = minOf(50, mesures.minOf { it.diastolique } - 10).toFloat()
        val yMax = maxOf(180, mesures.maxOf { it.systolique } + 10).toFloat()
        fun y(v: Int) = p + h * (1f - (v - yMin) / (yMax - yMin))
        fun x(i: Int) = p + w * i / (mesures.size - 1).toFloat()
        val tirets = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        listOf(140, 90).forEach { v ->
            drawLine(Color(0xFFF57C00).copy(alpha = 0.6f), Offset(p, y(v)), Offset(p + w, y(v)), 2f, pathEffect = tirets)
        }
        fun trace(valeur: (TensionEntity) -> Int, couleur: Color) {
            val chemin = Path()
            mesures.forEachIndexed { i, m -> if (i == 0) chemin.moveTo(x(i), y(valeur(m))) else chemin.lineTo(x(i), y(valeur(m))) }
            drawPath(chemin, couleur, style = Stroke(width = 4f))
            mesures.forEachIndexed { i, m -> drawCircle(couleur, 6f, Offset(x(i), y(valeur(m)))) }
        }
        trace({ it.systolique }, Primary)
        trace({ it.diastolique }, couleurDia)
    }
}

@Composable
private fun Legende(couleur: Color, texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(couleur, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(texte, fontSize = 12.sp)
    }
}

@Composable
private fun LigneTension(t: TensionEntity, age: Int?, onSupprimer: () -> Unit) {
    val c = t.categorie(age)
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(c.couleur(), CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${t.systolique}/${t.diastolique} mmHg" + (t.pouls?.let { " · FC $it" } ?: ""), fontWeight = FontWeight.SemiBold)
                Text("${c.libelle} · PP ${t.pressionPulsee()} · ${t.positionTexte()}, bras ${t.brasTexte()}", fontSize = 12.sp, color = c.couleur())
                Text(t.dateHeure.format(formatDate) + (if (t.traitement) " · sous traitement" else ""), fontSize = 12.sp, color = OnSurfaceVariant)
            }
            IconButton(onClick = onSupprimer) { Icon(Icons.Outlined.Delete, "Supprimer", tint = OnSurfaceVariant) }
        }
    }
}

/** Signaux a montrer au patient (a discuter avec le medecin), calcules sur 30 jours. */
private fun alertesTension(tensions: List<TensionEntity>): List<String> {
    val depuis = LocalDateTime.now().minusDays(30)
    val mesures = tensions.filter { it.dateHeure.isAfter(depuis) }.map {
        MesureTension(it.dateHeure.toKotlinLocalDateTime(), it.systolique, it.diastolique, it.pouls, it.position, it.bras, it.traitement)
    }
    return buildList {
        ReglesTension.testsOrthostatiques(mesures).firstOrNull()?.let { o ->
            add(if (o.positif) "Test couché-debout : baisse de ${o.baissePas} mmHg (PAS) et ${o.baissePad} mmHg (PAD). " +
                "C'est une hypotension orthostatique (baisse ≥ 20 / ≥ 10) : parlez-en à votre médecin."
            else "Test couché-debout : baisse de ${o.baissePas}/${o.baissePad} mmHg, pas d'hypotension orthostatique.")
        }
        if (ReglesTension.tachycardiePersistante(mesures))
            add("Votre pouls au repos est souvent ≥ 100/min. Parlez-en à votre médecin.")
        val pp = mesures.map { it.pressionPulsee }
        if (pp.size >= 3 && pp.average() > 60)
            add("Pression pulsée moyenne ${pp.average().toInt()} mmHg (> 60) : à signaler à votre médecin.")
    }
}

@Composable
private fun AlertesCard(alertes: List<String>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF57C00).copy(alpha = 0.08f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("À signaler à votre médecin", fontWeight = FontWeight.SemiBold, color = Color(0xFFE65100))
            alertes.forEach { Text("• $it", fontSize = 13.sp) }
        }
    }
}
