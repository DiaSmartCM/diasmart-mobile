package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.MesureHbA1c
import com.diabeto.domain.MesureTension
import com.diabeto.domain.ObjectifTension
import com.diabeto.domain.ReglesTension
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private enum class OngletFiche(val libelle: String) {
    VUE("Vue générale"),
    CARNET("Carnet"),
    JOUR("Courbes quotidiennes"),
    PROFIL("Profil glycémique"),
    TENSION("Tension"),
    CLINIQUE("Contexte clinique & traitement"),
    JOURNAL("Journal de vie")
}

private val PERIODES = listOf(7, 14, 30, 90)

/** Donnees de la fiche, chargees une fois a l'ouverture. */
private class DonneesFiche(p: PatientSuivi) {
    var mesures by mutableStateOf(p.mesures)
    var tensions by mutableStateOf(p.tensions)
    var hba1c by mutableStateOf<List<MesureHbA1c>>(emptyList())
    var objectif by mutableStateOf<ObjectifTension?>(null)
    var profil by mutableStateOf<ProfilClinique?>(null)
    var medicaments by mutableStateOf<List<Medicament>>(emptyList())
    var journal by mutableStateOf<List<EntreeJournal>>(emptyList())
    var chargement by mutableStateOf(true)
}

@Composable
fun FichePatient(etat: EtatApp, p: PatientSuivi, onRetour: () -> Unit) {
    val d = remember(p.uid) { DonneesFiche(p) }
    var onglet by remember(p.uid) { mutableStateOf(OngletFiche.VUE) }
    var jours by remember { mutableIntStateOf(30) }
    var jourChoisi by remember(p.uid) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(p.uid) {
        val s = etat.patientsService
        s.mesures(p.uid, 800).takeIf { it.isNotEmpty() }?.let { d.mesures = it }
        d.hba1c = s.hba1c(p.uid)
        s.tensions(p.uid, 300).takeIf { it.isNotEmpty() }?.let { d.tensions = it }
        d.objectif = s.objectifTension(p.uid)
        d.profil = s.profilClinique(p.uid)
        d.medicaments = s.medicaments(p.uid)
        d.journal = s.journal(p.uid, 90)
        d.chargement = false
    }
    val joursDispo = d.mesures.map { it.date.date }.distinct().sortedDescending()
    val jour = jourChoisi ?: joursDispo.firstOrNull()
    fun changerJour(sens: Int) {
        val i = joursDispo.indexOf(jour ?: return)
        joursDispo.getOrNull(i - sens)?.let { jourChoisi = it }
    }

    Column(
        Modifier.fillMaxSize().onKeyEvent { ev ->
            // Courbes quotidiennes : fleches gauche / droite = jour precedent / suivant
            if (onglet != OngletFiche.JOUR || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (ev.key) {
                Key.DirectionLeft -> { changerJour(-1); true }
                Key.DirectionRight -> { changerJour(1); true }
                else -> false
            }
        }
    ) {
        EnteteFiche(p, d.chargement, onRetour)
        Espace(10)
        Onglets(OngletFiche.entries, onglet, { it.libelle }) { onglet = it }
        Espace(12)
        Box(Modifier.weight(1f)) {
            val defil = remember(onglet) { androidx.compose.foundation.ScrollState(0) }
            Column(Modifier.fillMaxSize().defilementClavier(defil), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (onglet) {
                    OngletFiche.VUE -> VueGenerale(p, d, jours) { jours = it }
                    OngletFiche.CARNET -> Carnet(d.mesures, jours) { jours = it }
                    OngletFiche.JOUR -> CourbesQuotidiennes(d.mesures, jour, joursDispo, ::changerJour)
                    OngletFiche.PROFIL -> ProfilGlycemique(d.mesures, jours) { jours = it }
                    OngletFiche.TENSION -> {
                        CarteObjectifTension(etat, p.uid, d.objectif) { d.objectif = it }
                        CarteTension(d.tensions, jours, d.objectif) { jours = it }
                    }
                    OngletFiche.CLINIQUE -> ContexteClinique(d)
                    OngletFiche.JOURNAL -> JournalDeVie(d.journal, d.chargement)
                }
                Text("Aide au suivi, pas un diagnostic : la décision reste au soignant.", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun EnteteFiche(p: PatientSuivi, chargement: Boolean, onRetour: () -> Unit) {
    val r = p.resultat
    Row(Modifier.fillMaxWidth().background(Color.White).border(1.dp, Bordure).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour à la liste") }
        Column(Modifier.weight(1f)) {
            Text(p.nom.ifBlank { "Patient" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(listOf(p.origine.libelle, r.raisons.joinToString(" · ")).filter { it.isNotBlank() }.joinToString("  ·  "),
                fontSize = 13.sp, color = Color.Gray)
        }
        if (chargement) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Badge("HbA1c " + (r.hba1c?.let { EvaluationSuivi.unChiffre(it.valeur) + " %" } ?: "—"), IndigoFonce)
        Badge("Priorité : " + r.priorite.libelle.lowercase(), r.priorite.couleur())
    }
}

@Composable
private fun ChoixPeriode(jours: Int, onJours: (Int) -> Unit) =
    Segments(PERIODES, jours, { "$it j" }, onJours)

private fun periodeDe(mesures: List<Mesure>, jours: Int): List<Mesure> {
    val debut = System.currentTimeMillis() - jours * 86_400_000L
    return mesures.filter { it.date.ms() >= debut }
}

@Composable
private fun Ligne(libelle: String, valeur: String, couleur: Color = Color.Unspecified, gras: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(libelle, Modifier.weight(1f), fontSize = 13.sp, color = Color.DarkGray)
        Text(valeur, fontSize = 13.sp, color = couleur, fontWeight = if (gras) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun f1(v: Double) = EvaluationSuivi.unChiffre(v)
private fun pct(v: Double) = "${f1(v)} %"

// ── Vue generale ─────────────────────────────────────────────────────────

@Composable
private fun VueGenerale(p: PatientSuivi, d: DonneesFiche, jours: Int, onJours: (Int) -> Unit) {
    val periode = periodeDe(d.mesures, jours)
    val stats = StatsGlycemie.resume(periode.map { it.valeur }, jours)
    val r = p.resultat
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Période : $jours derniers jours", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        ChoixPeriode(jours, onJours)
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panneau("Temps dans la cible", Modifier.weight(1f).fillMaxHeight()) { BlocCible(stats) }
        Panneau("Glycémie", Modifier.weight(1f).fillMaxHeight()) {
            Ligne("Moyenne", stats?.let { "${it.moyenne.toInt()} mg/dL" } ?: "—", gras = true)
            Ligne("GMI (HbA1c estimée)", stats?.let { pct(it.gmi) } ?: "—")
            Ligne("Dernière HbA1c", r.hba1c?.let { pct(it.valeur) + if (it.estimee) " (est.)" else "" } ?: "—")
            Ligne("Mesures", stats?.let { "${it.n} (${f1(it.mesuresParJour)} / jour)" } ?: "0")
            Ligne("Dernière mesure", r.derniereMesure?.let { "${it.jour()} ${it.heure()}" } ?: "—",
                if (r.perduDeVue) Orange else Color.Unspecified)
        }
        Panneau("Variabilité", Modifier.weight(1f).fillMaxHeight()) {
            Ligne("Écart-type", stats?.let { "${it.ecartType.toInt()} mg/dL" } ?: "—")
            Ligne("CV (objectif ≤ 36 %)", stats?.let { pct(it.cv) } ?: "—", stats?.let { if (it.cv > 36) Orange else Vert } ?: Color.Unspecified)
            Ligne("Min / max", stats?.let { "${it.min.toInt()} / ${it.max.toInt()}" } ?: "—")
            Ligne("Hypos < 70 (30 j)", r.nbHypos30j.toString(), if (r.nbHypos30j > 0) Rouge else Color.Unspecified)
            Ligne("Hypos < 54 (30 j)", r.nbHyposSeveres30j.toString(), if (r.nbHyposSeveres30j > 0) Rouge else Color.Unspecified)
        }
        Panneau("Tension", Modifier.weight(1f).fillMaxHeight()) {
            val t = d.tensions.maxByOrNull { it.date }
            if (t == null) Text("Aucune mesure de tension.", fontSize = 13.sp, color = Color.Gray)
            else {
                val c = ReglesTension.categorie(t.systolique, t.diastolique, objectif = d.objectif)
                Ligne("Dernière", "${t.systolique}/${t.diastolique} mmHg", c.couleur(), gras = true)
                Ligne("Le", "${t.date.jour()} ${t.date.heure()}")
                r.tensionMoyenne30j?.let { Ligne("Moyenne 30 j", "${it.first}/${it.second}") }
                Ligne("Objectif", d.objectif?.texte ?: "< 130/80 (général)")
                Text(c.libelle, fontSize = 12.sp, color = c.couleur())
            }
        }
    }
    Panneau("Courbe de glycémie ($jours jours)", Modifier.fillMaxWidth()) {
        val fin = System.currentTimeMillis()
        if (periode.isEmpty()) Text("Aucune mesure sur cette période.", color = Color.Gray, fontSize = 13.sp)
        else CourbeGlycemie(periode.map { PointCourbe(it.date.ms(), it.valeur) }, fin - jours * 86_400_000L, fin,
            Modifier.fillMaxWidth().height(260.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panneau("Alertes et points d'attention", Modifier.weight(1f)) {
            val alertes = buildList {
                addAll(r.raisons)
                if (r.perduDeVue) add("Aucune mesure depuis plus de 60 jours")
                stats?.let { if (it.cv > 36) add("Glycémie instable : CV ${pct(it.cv)} (> 36 %)") }
                stats?.parPlage?.get(StatsGlycemie.Plage.TRES_BAS)?.takeIf { it > 1.0 }?.let { add("${pct(it)} des mesures < 54 mg/dL") }
            }.distinct()
            if (alertes.isEmpty()) Text("Rien de particulier sur la période.", fontSize = 13.sp, color = Vert)
            alertes.forEach { Text("• $it", fontSize = 13.sp) }
        }
        Panneau("Dernières mesures", Modifier.weight(1f)) {
            if (d.mesures.isEmpty()) Text("Aucune mesure.", color = Color.Gray, fontSize = 13.sp)
            d.mesures.sortedByDescending { it.date }.take(10).forEach { m ->
                Row {
                    Text("${m.date.jour()} ${m.date.heure()}", Modifier.width(140.dp), fontSize = 13.sp)
                    Text("${m.valeur.toInt()} mg/dL", Modifier.width(90.dp), fontWeight = FontWeight.Medium, color = couleurGlycemie(m.valeur))
                    Text(StatsGlycemie.libelleContexte(m.contexte), fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun BlocCible(stats: StatsGlycemie.Resume?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BarreCible(stats?.parPlage ?: emptyMap(), Modifier.width(34.dp).height(130.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatsGlycemie.Plage.entries.forEach { pl ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(pl.couleur)))
                    EspaceL(6)
                    Text(pl.libelle, Modifier.width(62.dp), fontSize = 12.sp, color = Color.DarkGray)
                    Text(stats?.parPlage?.get(pl)?.let { pct(it) } ?: "—", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = Color(pl.couleur))
                }
            }
        }
    }
    Text("% des mesures (glycémie capillaire) · objectif : > 70 % entre 70 et 180, < 4 % sous 70, < 1 % sous 54.",
        fontSize = 11.sp, color = Color.Gray)
}

// ── Carnet ───────────────────────────────────────────────────────────────

@Composable
private fun Carnet(mesures: List<Mesure>, jours: Int, onJours: (Int) -> Unit) {
    val periode = periodeDe(mesures, jours)
    val parJour = periode.groupBy { it.date.date }.toSortedMap(compareByDescending { it })
    val colonnes = StatsGlycemie.Moment.entries
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Carnet de glycémie (mg/dL)", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        ChoixPeriode(jours, onJours)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendeCase("< 54", StatsGlycemie.Plage.TRES_BAS.couleur)
        LegendeCase("54-69", StatsGlycemie.Plage.BAS.couleur)
        LegendeCase("Dans l'objectif (à jeun / avant repas 80-130, après < 180)", StatsGlycemie.Plage.CIBLE.couleur)
        LegendeCase("Au-dessus", StatsGlycemie.Plage.HAUT.couleur)
        LegendeCase("> 250", StatsGlycemie.Plage.TRES_HAUT.couleur)
    }
    Column(Modifier.fillMaxWidth().background(Color.White).border(1.dp, Bordure)) {
        // En-tete sur deux lignes : repas, puis avant / apres
        Row(Modifier.fillMaxWidth().background(Indigo)) {
            CelluleEntete("Date", 1.2f)
            val groupes = colonnes.groupBy { it.groupe }
            groupes.forEach { (g, cols) -> CelluleEntete(g, cols.size.toFloat()) }
        }
        Row(Modifier.fillMaxWidth().background(EnteteTableau)) {
            Box(Modifier.weight(1.2f))
            colonnes.forEach { c ->
                Text(c.libelle, Modifier.weight(1f).padding(vertical = 5.dp), fontSize = 11.sp, color = Color(0xFF4A4F63),
                    textAlign = TextAlign.Center)
            }
        }
        if (parJour.isEmpty()) Text("Aucune mesure sur cette période.", Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
        parJour.forEach { (date, liste) ->
            HorizontalDivider(color = Bordure)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Text(date.texte(), Modifier.weight(1.2f).padding(10.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                colonnes.forEach { c ->
                    Column(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xFFEDEEF3)).padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        liste.filter { StatsGlycemie.moment(it) == c }.sortedBy { it.date }.forEach { m ->
                            val fond = Color(StatsGlycemie.couleurCase(m.valeur, c))
                            Text(m.valeur.toInt().toString(), Modifier.background(fond).padding(horizontal = 8.dp, vertical = 2.dp),
                                color = if (fond == Color(StatsGlycemie.Plage.HAUT.couleur)) Color.Black else Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(m.date.heure(), fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
    Text("Le moment est celui saisi par le patient (à jeun, avant / après repas, coucher) ; le repas est déduit de l'heure.",
        fontSize = 12.sp, color = Color.Gray)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CelluleEntete(texte: String, poids: Float) {
    Text(texte, Modifier.weight(poids).border(0.5.dp, IndigoFonce).padding(vertical = 7.dp), color = Color.White,
        fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun LegendeCase(texte: String, couleur: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Color(couleur)))
        EspaceL(5)
        Text(texte, fontSize = 12.sp, color = Color.DarkGray)
    }
}

// ── Courbes quotidiennes ─────────────────────────────────────────────────

@Composable
private fun CourbesQuotidiennes(mesures: List<Mesure>, jour: LocalDate?, joursDispo: List<LocalDate>, changer: (Int) -> Unit) {
    var superposer by remember { mutableStateOf(false) }
    if (jour == null) {
        Panneau("Courbes quotidiennes", Modifier.fillMaxWidth()) { Text("Aucune mesure.", color = Color.Gray, fontSize = 13.sp) }
        return
    }
    val duJour = mesures.filter { it.date.date == jour }.sortedBy { it.date }
    val i = joursDispo.indexOf(jour)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { changer(-1) }, enabled = i < joursDispo.size - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null); Text("Jour précédent")
        }
        Text(jour.texte(), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        OutlinedButton(onClick = { changer(1) }, enabled = i > 0) {
            Text("Jour suivant"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
        Box(Modifier.weight(1f))
        Segments(listOf(false, true), superposer, { if (it) "+ 6 jours précédents" else "Ce jour seul" }) { superposer = it }
    }
    Text("Astuce : flèches gauche / droite du clavier pour changer de jour.", fontSize = 12.sp, color = Color.Gray)
    val series = buildList {
        if (superposer) {
            joursDispo.drop(i + 1).take(6).forEach { j ->
                add(SerieJour(mesures.filter { it.date.date == j }.map { it.date.hour * 60 + it.date.minute to it.valeur },
                    Indigo.copy(alpha = 0.28f), 1.2f))
            }
        }
        add(SerieJour(duJour.map { it.date.hour * 60 + it.date.minute to it.valeur }, Indigo, 2.5f))
    }
    Panneau("Glycémie du ${jour.texte()}", Modifier.fillMaxWidth()) {
        CourbeJournee(series, Modifier.fillMaxWidth().height(300.dp))
        if (superposer) Text("En clair : les 6 jours précédents avec des mesures. En foncé : le jour choisi.", fontSize = 12.sp, color = Color.Gray)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panneau("Mesures du jour", Modifier.weight(2f)) {
            EnteteLigne(listOf("Heure" to 1f, "Glycémie" to 1f, "Moment" to 2f))
            duJour.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Text(m.date.heure(), Modifier.weight(1f), fontSize = 13.sp)
                    Text("${m.valeur.toInt()} mg/dL", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = couleurGlycemie(m.valeur))
                    Text(StatsGlycemie.libelleContexte(m.contexte), Modifier.weight(2f), fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
        Panneau("Résumé du jour", Modifier.weight(1f)) {
            val v = duJour.map { it.valeur }
            Ligne("Mesures", v.size.toString())
            Ligne("Moyenne", if (v.isEmpty()) "—" else "${v.average().toInt()} mg/dL", gras = true)
            Ligne("Min / max", if (v.isEmpty()) "—" else "${v.min().toInt()} / ${v.max().toInt()}")
            Ligne("Dans 70-180", if (v.isEmpty()) "—" else pct(v.count { it in 70.0..180.0 } * 100.0 / v.size))
        }
    }
}

// ── Profil glycemique (statistiques, tendances par heure) ────────────────

@Composable
private fun ProfilGlycemique(mesures: List<Mesure>, jours: Int, onJours: (Int) -> Unit) {
    val periode = periodeDe(mesures, jours)
    val stats = StatsGlycemie.resume(periode.map { it.valeur }, jours)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Statistiques et variabilité glycémique", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        ChoixPeriode(jours, onJours)
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panneau("Temps dans la cible", Modifier.weight(1f).fillMaxHeight()) { BlocCible(stats) }
        Panneau("Statistiques", Modifier.weight(1f).fillMaxHeight()) {
            Ligne("Mesures", stats?.n?.toString() ?: "0")
            Ligne("Mesures par jour", stats?.let { f1(it.mesuresParJour) } ?: "—")
            Ligne("Moyenne", stats?.let { "${it.moyenne.toInt()} mg/dL" } ?: "—", gras = true)
            Ligne("GMI", stats?.let { pct(it.gmi) } ?: "—")
            Ligne("HbA1c estimée (ADAG)", stats?.let { pct(it.hba1cEstimee) } ?: "—")
        }
        Panneau("Variabilité", Modifier.weight(1f).fillMaxHeight()) {
            Ligne("Écart-type (SD)", stats?.let { "${it.ecartType.toInt()} mg/dL" } ?: "—")
            Ligne("CV", stats?.let { pct(it.cv) } ?: "—", stats?.let { if (it.cv > 36) Orange else Vert } ?: Color.Unspecified)
            Ligne("LBGI (risque d'hypo)", stats?.let { f1(it.lbgi) } ?: "—", stats?.let { if (it.lbgi > 2.5) Orange else Color.Unspecified } ?: Color.Unspecified)
            Ligne("HBGI (risque d'hyper)", stats?.let { f1(it.hbgi) } ?: "—", stats?.let { if (it.hbgi > 9) Orange else Color.Unspecified } ?: Color.Unspecified)
            Ligne("Min / max", stats?.let { "${it.min.toInt()} / ${it.max.toInt()}" } ?: "—")
        }
    }
    Panneau("Tendances quotidiennes sur la période", Modifier.fillMaxWidth()) {
        val tranches = StatsGlycemie.profilHoraire(periode)
        if (tranches.isEmpty()) Text("Aucune mesure sur cette période.", color = Color.Gray, fontSize = 13.sp)
        else {
            CourbeProfil(tranches, Modifier.fillMaxWidth().height(300.dp))
            Text("Par tranche de 2 h : trait foncé = médiane ; bande foncée = 25-75 % des mesures ; bande claire = 10-90 %. " +
                "Zone verte : 70-180 mg/dL.", fontSize = 12.sp, color = Color.Gray)
        }
    }
    Panneau("Par moment de la journée", Modifier.fillMaxWidth()) {
        EnteteLigne(listOf("Moment" to 2f, "Mesures" to 1f, "Moyenne" to 1f, "Min" to 1f, "Max" to 1f, "Dans l'objectif" to 1.2f))
        StatsGlycemie.Moment.entries.forEach { m ->
            val v = periode.filter { StatsGlycemie.moment(it) == m }.map { it.valeur }
            if (v.isEmpty()) return@forEach
            val ok = v.count { StatsGlycemie.couleurCase(it, m) == StatsGlycemie.Plage.CIBLE.couleur }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                Text(listOf(m.groupe, m.libelle).filter { it.isNotBlank() }.joinToString(" · "), Modifier.weight(2f), fontSize = 13.sp)
                Text(v.size.toString(), Modifier.weight(1f), fontSize = 13.sp)
                Text("${v.average().toInt()}", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = couleurGlycemie(v.average()))
                Text("${v.min().toInt()}", Modifier.weight(1f), fontSize = 13.sp)
                Text("${v.max().toInt()}", Modifier.weight(1f), fontSize = 13.sp)
                Text(pct(ok * 100.0 / v.size), Modifier.weight(1.2f), fontSize = 13.sp)
            }
        }
    }
    Text("Calculs sur les glycémies capillaires saisies (pas un capteur continu) : à interpréter avec le nombre de mesures.",
        fontSize = 12.sp, color = Color.Gray)
}

// ── Contexte clinique et traitement ─────────────────────────────────────

@Composable
private fun ContexteClinique(d: DonneesFiche) {
    val aujourdhui = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    fun ans(depuis: LocalDate?) = depuis?.let { (aujourdhui.year - it.year) - if (aujourdhui < LocalDate(aujourdhui.year, it.monthNumber, minOf(it.dayOfMonth, 28))) 1 else 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Panneau("Profil", Modifier.weight(1f)) {
            val pr = d.profil
            if (pr == null) Text(if (d.chargement) "Chargement…" else "Profil non partagé par le patient.", fontSize = 13.sp, color = Color.Gray)
            else {
                Ligne("Type de diabète", pr.typeTexte, gras = true)
                Ligne("Âge", ans(pr.dateNaissance)?.takeIf { it in 0..120 }?.let { "$it ans" } ?: "—")
                Ligne("Sexe", pr.sexeTexte)
                Ligne("Diabète depuis", pr.dateDiagnostic?.let { "${it.texte()} (${ans(it)} ans)" } ?: "—")
                Ligne("Poids", pr.poids?.let { "${f1(it)} kg" } ?: "—")
                Ligne("Taille", pr.taille?.let { "${it.toInt()} cm" } ?: "—")
                Ligne("IMC", pr.imc?.let { f1(it) + when { it < 18.5 -> " (maigreur)"; it < 25 -> " (normal)"; it < 30 -> " (surpoids)"; else -> " (obésité)" } } ?: "—",
                    pr.imc?.let { if (it >= 30 || it < 18.5) Orange else Color.Unspecified } ?: Color.Unspecified)
                Ligne("Tour de taille", pr.tourDeTaille?.let { "${it.toInt()} cm" } ?: "—")
            }
        }
        Panneau("Historique HbA1c", Modifier.weight(1f)) {
            if (d.hba1c.isEmpty()) Text(if (d.chargement) "Chargement…" else "Aucune HbA1c enregistrée.", fontSize = 13.sp, color = Color.Gray)
            d.hba1c.sortedByDescending { it.date }.take(12).forEach { h ->
                Ligne(h.date.texte() + if (h.estimee) " (estimée)" else "", pct(h.valeur),
                    when { h.valeur >= 9 -> Rouge; h.valeur >= 7 -> Orange; else -> Vert })
            }
        }
    }
    Panneau("Traitements", Modifier.fillMaxWidth()) {
        if (d.medicaments.isEmpty()) Text(if (d.chargement) "Chargement…" else "Aucun traitement saisi par le patient.", fontSize = 13.sp, color = Color.Gray)
        else {
            EnteteLigne(listOf("Médicament" to 2f, "Dose" to 1.2f, "Fréquence" to 1.4f, "Heure" to 0.8f, "Depuis" to 1f, "Statut" to 0.9f))
            d.medicaments.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                    Text(m.nom, Modifier.weight(2f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(m.dosage.ifBlank { "—" }, Modifier.weight(1.2f), fontSize = 13.sp)
                    Text(m.frequenceTexte, Modifier.weight(1.4f), fontSize = 13.sp)
                    Text(m.heure.ifBlank { "—" }, Modifier.weight(0.8f), fontSize = 13.sp)
                    Text(m.debut.ifBlank { "—" }, Modifier.weight(1f), fontSize = 13.sp)
                    Text(if (m.actif) "En cours" else "Arrêté", Modifier.weight(0.9f), fontSize = 13.sp, color = if (m.actif) Vert else Color.Gray)
                }
            }
        }
    }
    Text("Données saisies par le patient dans son application DiaSmart.", fontSize = 12.sp, color = Color.Gray)
}

// ── Journal de vie (humeur, sommeil, activite) ───────────────────────────

@Composable
private fun JournalDeVie(journal: List<EntreeJournal>, chargement: Boolean) {
    if (journal.isEmpty()) {
        Panneau("Journal de vie", Modifier.fillMaxWidth()) {
            Text(if (chargement) "Chargement…" else "Le patient n'a pas encore rempli son journal.", fontSize = 13.sp, color = Color.Gray)
        }
        return
    }
    val recents = journal.take(30)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Compteur("Sommeil moyen", recents.mapNotNull { it.heuresSommeil }.takeIf { it.isNotEmpty() }?.let { f1(it.average()) + " h" } ?: "—", Modifier.weight(1f))
        Compteur("Pas par jour", recents.mapNotNull { it.pas }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { it.average().toInt().toString() } ?: "—", Modifier.weight(1f))
        Compteur("Jours actifs", "${recents.count { it.activite }} / ${recents.size}", Modifier.weight(1f))
        Compteur("Stress élevé", recents.count { it.stress == "ELEVE" || it.stress == "EXTREME" }.toString(), Modifier.weight(1f),
            if (recents.any { it.stress == "EXTREME" }) Orange else Indigo)
    }
    Panneau("Journal (${journal.size} jours)", Modifier.fillMaxWidth()) {
        EnteteLigne(listOf("Date" to 1f, "Humeur" to 1f, "Stress" to 1f, "Sommeil" to 1.4f, "Activité" to 1f, "Pas" to 0.8f))
        journal.forEach { j ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                Text(j.date.texte(), Modifier.weight(1f), fontSize = 13.sp)
                Text(EntreeJournal.libelle(j.humeur), Modifier.weight(1f), fontSize = 13.sp)
                Text(EntreeJournal.libelle(j.stress), Modifier.weight(1f), fontSize = 13.sp,
                    color = if (j.stress == "ELEVE" || j.stress == "EXTREME") Orange else Color.Unspecified)
                Text(EntreeJournal.libelle(j.sommeil) + (j.heuresSommeil?.let { " · ${f1(it)} h" } ?: ""), Modifier.weight(1.4f), fontSize = 13.sp)
                Text(if (j.activite) "${j.minutesActivite ?: 0} min" else "—", Modifier.weight(1f), fontSize = 13.sp)
                Text(j.pas?.takeIf { it > 0 }?.toString() ?: "—", Modifier.weight(0.8f), fontSize = 13.sp)
            }
        }
    }
}

// ── Tension ──────────────────────────────────────────────────────────────

@Composable
fun CarteObjectifTension(etat: EtatApp, uid: String, objectif: ObjectifTension?, onChange: (ObjectifTension?) -> Unit) {
    var sys by remember(objectif) { mutableStateOf(objectif?.systolique?.toString() ?: "130") }
    var dia by remember(objectif) { mutableStateOf(objectif?.diastolique?.toString() ?: "80") }
    var message by remember(uid) { mutableStateOf<String?>(null) }
    val portee = rememberCoroutineScope()
    Panneau("Objectif de tension du patient", Modifier.fillMaxWidth()) {
        Text(objectif?.let { "Objectif personnel : ${it.texte}" + (if (it.auteurNom.isNotBlank()) " (fixé par ${it.auteurNom})" else "") }
            ?: "Objectif général : < 130/80 mmHg (ADA 2025 / ESC 2024)", fontSize = 13.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, label = { Text("PAS <") }, singleLine = true, modifier = Modifier.width(110.dp))
            OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, label = { Text("PAD <") }, singleLine = true, modifier = Modifier.width(110.dp))
            Button(onClick = {
                val s = sys.toIntOrNull() ?: 0; val d = dia.toIntOrNull() ?: 0
                portee.launch {
                    message = runCatching {
                        etat.patientsService.fixerObjectifTension(uid, s, d, etat.profil)
                        onChange(ObjectifTension(s, d, etat.profil.nomComplet))
                        "Objectif enregistré : le patient le voit dans son suivi."
                    }.getOrElse { it.message ?: "Enregistrement impossible" }
                }
            }) { Text("Enregistrer") }
            if (objectif != null) OutlinedButton(onClick = {
                portee.launch {
                    message = runCatching {
                        etat.patientsService.retirerObjectifTension(uid); onChange(null); "Objectif retiré."
                    }.getOrElse { it.message ?: "Suppression impossible" }
                }
            }) { Text("Revenir au général") }
        }
        message?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
    }
}

@Composable
fun CarteTension(tensions: List<MesureTension>, jours: Int, objectif: ObjectifTension?, onJours: (Int) -> Unit) {
    val fin = System.currentTimeMillis()
    val debut = fin - jours * 86_400_000L
    val periode = tensions.filter { it.date.ms() >= debut }
    Panneau("Tension artérielle ($jours jours)", Modifier.fillMaxWidth(), actions = {
        Segments(PERIODES, jours, { "$it j" }, onJours)
    }) {
        if (tensions.isEmpty()) {
            Text("Aucune mesure de tension partagée.", color = Color.Gray, fontSize = 13.sp)
            return@Panneau
        }
        val derniere = tensions.maxBy { it.date }
        val cat = ReglesTension.categorie(derniere.systolique, derniere.diastolique, objectif = objectif)
        val moy = ReglesTension.moyenne(periode)
        Text(
            "Dernière : ${derniere.systolique}/${derniere.diastolique} mmHg le ${derniere.date.jour()} (${cat.libelle})" +
                (moy?.let { " · moyenne ${it.first}/${it.second} sur ${periode.size} mesures, PP ${it.first - it.second}, PAM ${ReglesTension.pam(it.first, it.second)}" } ?: "") +
                (if (derniere.traitement == true) " · traitement antihypertenseur en cours" else ""),
            fontSize = 13.sp, color = Color.DarkGray
        )
        ReglesTension.testsOrthostatiques(tensions).firstOrNull()?.let { o ->
            Text(
                "Dernier test couché/debout (${o.couche.date.jour()}) : baisse ${o.baissePas}/${o.baissePad} mmHg" +
                    if (o.positif) " · hypotension orthostatique possible (PAS ≥ 20 ou PAD ≥ 10)" else " · pas d'hypotension orthostatique",
                fontSize = 13.sp, color = if (o.positif) Orange else Vert
            )
        }
        if (ReglesTension.tachycardiePersistante(periode))
            Text("Tachycardie de repos persistante (FC ≥ 100) : neuropathie autonome possible", fontSize = 13.sp, color = Orange)
        moy?.takeIf { it.first - it.second > 60 }?.let {
            Text("Pression pulsée moyenne ${it.first - it.second} mmHg (> 60) : rigidité artérielle possible", fontSize = 13.sp, color = Orange)
        }
        if (periode.isEmpty()) Text("Aucune mesure sur cette période.", color = Color.Gray, fontSize = 13.sp)
        else CourbeTension(periode.map { Triple(it.date.ms(), it.systolique, it.diastolique) }, debut, fin,
            Modifier.fillMaxWidth().height(220.dp))
        Text("Indigo : PAS (haut) · vert : PAD (bas) · pointillés : objectif 130/80 mmHg · " + ReglesTension.AVERTISSEMENT,
            fontSize = 12.sp, color = Color.Gray)
        EnteteLigne(listOf("Date" to 1.3f, "TA" to 0.8f, "FC" to 0.6f, "PP / PAM" to 1f, "Position / bras" to 1.4f, "Catégorie" to 2f))
        tensions.sortedByDescending { it.date }.take(15).forEach { t ->
            val c = ReglesTension.categorie(t.systolique, t.diastolique, objectif = objectif)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text("${t.date.jour()} ${t.date.heure()}", Modifier.weight(1.3f), fontSize = 13.sp)
                Text("${t.systolique}/${t.diastolique}", Modifier.weight(0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.couleur())
                Text(t.pouls?.toString() ?: "—", Modifier.weight(0.6f), fontSize = 13.sp)
                Text("${t.pressionPulsee} / ${t.pam}", Modifier.weight(1f), fontSize = 13.sp)
                Text(listOfNotNull(
                    t.position.takeIf { it.isNotBlank() }?.let { ReglesTension.libellePosition(it) },
                    t.bras.takeIf { it.isNotBlank() }?.let { "bras " + ReglesTension.libelleBras(it) }
                ).joinToString(" · ").ifBlank { "—" }, Modifier.weight(1.4f), fontSize = 12.sp, color = Color.Gray)
                Text(c.libelle, Modifier.weight(2f), fontSize = 12.sp, color = c.couleur())
            }
        }
    }
}
