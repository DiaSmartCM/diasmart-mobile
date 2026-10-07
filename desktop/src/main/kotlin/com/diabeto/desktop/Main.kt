package com.diabeto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.platform.LocalLocalization
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.MembreEtablissement
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.domain.CategorieTension
import com.diabeto.domain.ReglesTension
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.ReglesGlycemie
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

val Indigo = Color(0xFF6771E4)
val Rouge = Color(0xFFD32F2F)
val Orange = Color(0xFFF57C00)
val Vert = Color(0xFF2E7D32)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DiaSmart",
        icon = painterResource("icone.png"),
        state = rememberWindowState(width = 1280.dp, height = 820.dp)
    ) {
        MaterialTheme(colorScheme = CouleursDiaSmart, shapes = FormesCarrees) {
            CompositionLocalProvider(LocalLocalization provides MenusFrancais) {
                Surface(Modifier.fillMaxSize(), color = Fond) { AppDiaSmart() }
            }
        }
    }
}

@Composable
private fun AppDiaSmart() {
    val fb = remember { FirebaseRest() }
    var profil by remember { mutableStateOf<Profil?>(null) }
    // Apres une connexion par email : choisir le mot de passe de ce PC
    var creerMdpLocal by remember { mutableStateOf(false) }
    // Session gardee sur ce PC : on l'ouvre avec le mot de passe local
    var enregistree by remember { mutableStateOf(SessionPc.lire()) }
    var messageConnexion by remember { mutableStateOf<String?>(null) }
    val p = profil
    val e = enregistree
    Column(Modifier.fillMaxSize()) {
    BandeauMiseAJour()
    Box(Modifier.weight(1f)) {
    when {
        p != null && creerMdpLocal -> EcranCreerMotDePasseLocal(fb, p) { creerMdpLocal = false; enregistree = SessionPc.lire() }
        p != null -> EcranPrincipal(remember(p.uid) { EtatApp(fb, p) }) {
            // Verrouiller : la session reste sur ce PC, le mot de passe local la rouvre
            fb.deconnexion(); profil = null; enregistree = SessionPc.lire()
        }
        e != null -> EcranDeverrouillage(fb, e,
            onOuvert = { profil = it },
            onAutreCompte = { messageConnexion = it; enregistree = null })
        else -> key(messageConnexion) {
            EcranConnexion(fb, messageConnexion) { profil = it; creerMdpLocal = true; messageConnexion = null }
        }
    }
    }
    }
}

/** Bandeau en haut : une nouvelle version est disponible -> installation en un clic. */
@Composable
private fun BandeauMiseAJour() {
    var dispo by remember { mutableStateOf<MiseAJour.Disponible?>(null) }
    var progres by remember { mutableStateOf<Float?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { dispo = MiseAJour.verifier() }
    val d = dispo ?: return
    Row(Modifier.fillMaxWidth().background(Indigo).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val texte = when {
            erreur != null -> erreur!!
            progres != null -> "Téléchargement de la version ${d.version}… ${((progres ?: 0f) * 100).toInt()} %. DiaSmart va se fermer puis s'installer."
            else -> "Nouvelle version ${d.version} disponible."
        }
        Text(texte, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (progres == null) {
            Button(onClick = {
                erreur = null; progres = 0f
                scope.launch {
                    try { MiseAJour.installer(d) { progres = it } ; progres = null }
                    catch (ex: Exception) { progres = null; erreur = ex.message ?: "Mise à jour impossible." }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Indigo)) {
                Text(if (erreur != null) "Réessayer" else "Mettre à jour")
            }
        }
    }
}

// ── Donnees partagees par tous les ecrans ────────────────────────────────

class EtatApp(val fb: FirebaseRest, val profil: Profil) {
    val etabService = ServiceEtablissement(fb)
    val patientsService = ServicePatients(fb)
    val rdvService = ServiceRdv(fb)
    val messagerieService = ServiceMessagerie(fb)

    var chargement by mutableStateOf(false)
    var erreur by mutableStateOf<String?>(null)
    var affiliation by mutableStateOf<Affiliation?>(null)
    var etablissement by mutableStateOf<Etablissement?>(null)
    var membres by mutableStateOf<List<MembreEtablissement>>(emptyList())
    var patients by mutableStateOf<List<PatientSuivi>>(emptyList())
    var rdv by mutableStateOf<List<DemandeRdv>>(emptyList())
    var dejaCharge by mutableStateOf(false)
    /** Patients que ce soignant a ranges dans « Archivés ». */
    var archives by mutableStateOf<Set<String>>(emptySet())
    var erreurArchive by mutableStateOf<String?>(null)
    var conversations by mutableStateOf<List<Conversation>>(emptyList())

    /** Archive ou desarchive un patient (enregistre dans le compte du soignant). */
    suspend fun basculerArchive(uid: String) {
        val avant = archives
        val apres = if (uid in avant) avant - uid else avant + uid
        archives = apres
        try {
            patientsService.enregistrerArchives(profil.uid, apres)
        } catch (e: Exception) {
            archives = avant
            erreurArchive = e.message ?: "Enregistrement impossible."
        }
    }

    /** Membre de l'equipe d'un etablissement (admin ou soignant), sinon null. */
    val equipe: Affiliation? get() = affiliation?.takeIf { it.role != RoleEtablissement.PATIENT }

    suspend fun charger() {
        if (chargement) return
        chargement = true; erreur = null
        try {
            val aff = etabService.monAffiliation(profil.uid)
            affiliation = aff
            val etabId = aff?.takeIf { it.role != RoleEtablissement.PATIENT }?.etablissementId
            val inscrits = if (etabId != null) {
                etablissement = etabService.etablissement(etabId)
                membres = runCatching { etabService.membres(etabId) }.getOrDefault(emptyList())
                runCatching { etabService.patients(etabId) }.getOrDefault(emptyList())
            } else {
                etablissement = null; membres = emptyList(); emptyList()
            }
            rdv = runCatching { rdvService.demandes(profil.uid) }.getOrDefault(rdv)
            archives = patientsService.archives(profil.uid)
            conversations = runCatching { messagerieService.conversations(profil.uid) }.getOrDefault(conversations)
            patients = patientsService.patientsSuivis(profil.uid, inscrits)
            runCatching { SessionPc.retenirEtablissement(etablissement?.nom) }
            dejaCharge = true
        } catch (e: Exception) {
            erreur = e.message ?: "Chargement impossible"
        } finally {
            chargement = false
        }
    }
}

// ── Ecran principal ──────────────────────────────────────────────────────

enum class Onglet(val libelle: String) {
    ACCUEIL("Accueil"), TABLEAU("Tableau de bord"), PATIENTS("Mes patients"), MESSAGES("Messagerie"),
    RDV("Rendez-vous"), ETABLISSEMENT("Établissement"), OUTILS("Outils")
}

@Composable
private fun EcranPrincipal(etat: EtatApp, onDeconnexion: () -> Unit) {
    var onglet by remember { mutableStateOf(Onglet.ACCUEIL) }
    var patientOuvert by remember { mutableStateOf<String?>(null) }
    var ongletFiche by remember { mutableStateOf(OngletFiche.VUE) }
    var ecrireA by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(etat) { etat.charger() }
    fun ouvrirPatient(uid: String?, o: OngletFiche = OngletFiche.VUE) { patientOuvert = uid; ongletFiche = o; onglet = Onglet.PATIENTS }
    fun ecrire(uid: String) { ecrireA = uid; onglet = Onglet.MESSAGES }

    Row(Modifier.fillMaxSize()) {
        // Barre laterale indigo (couleurs DiaSmart), elements a angle droit
        Column(Modifier.width(150.dp).fillMaxHeight().background(Indigo)) {
            Text("DiaSmart", Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
            Onglet.entries.forEach { o ->
                val pastille = if (o == Onglet.MESSAGES) etat.conversations.sumOf { it.nonLusMedecin } else 0
                ElementMenu(o.libelle, o.icone(), onglet == o, pastille) { onglet = o; if (o == Onglet.PATIENTS) patientOuvert = null }
            }
            Spacer(Modifier.weight(1f))
            ElementMenu("Verrouiller", Icons.Default.Lock, false, onClic = onDeconnexion)
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when (onglet) {
                Onglet.ACCUEIL -> EcranAccueil(etat) { onglet = it; if (it == Onglet.PATIENTS) patientOuvert = null }
                Onglet.TABLEAU -> EcranTableauDeBord(etat,
                    onOuvrirPatient = { ouvrirPatient(it) },
                    onAller = { onglet = it })
                Onglet.PATIENTS -> EcranPatients(etat, patientOuvert, ongletFiche, { uid, o -> ouvrirPatient(uid, o) }, ::ecrire)
                Onglet.MESSAGES -> EcranMessagerie(etat, ecrireA, onOuvert = { ecrireA = null }, onDossier = { ouvrirPatient(it) })
                Onglet.RDV -> EcranRendezVous(etat)
                Onglet.ETABLISSEMENT -> EcranEtablissement(etat)
                Onglet.OUTILS -> EcranOutils()
            }
        }
    }
}

@Composable
private fun ElementMenu(libelle: String, icone: ImageVector, actif: Boolean, pastille: Int = 0, onClic: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (actif) IndigoFonce else Color.Transparent).clickable(onClick = onClic),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(44.dp).background(if (actif) Color.White else Color.Transparent))
        Icon(icone, null, Modifier.padding(start = 10.dp).size(20.dp), tint = Color.White)
        Text(libelle, Modifier.weight(1f).padding(start = 8.dp, end = 4.dp), color = Color.White, fontSize = 12.sp,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal)
        if (pastille > 0) Text(pastille.toString(), Modifier.padding(end = 8.dp).background(Rouge).padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Onglet.icone() = when (this) {
    Onglet.ACCUEIL -> Icons.Default.Home
    Onglet.TABLEAU -> Icons.AutoMirrored.Filled.List
    Onglet.PATIENTS -> Icons.Default.Person
    Onglet.MESSAGES -> Icons.Default.Email
    Onglet.RDV -> Icons.Default.DateRange
    Onglet.ETABLISSEMENT -> Icons.Default.Place
    Onglet.OUTILS -> Icons.Default.Build
}

// ── Petits elements communs ──────────────────────────────────────────────

@Composable
fun Chargement(texte: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(texte)
        }
    }
}

@Composable
fun Message(texte: String, onReessayer: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(texte, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onReessayer) { Text("Réessayer") }
        }
    }
}

@Composable
fun Compteur(titre: String, valeur: String, modifier: Modifier = Modifier, couleur: Color = Indigo) {
    // Case blanche avec un liseré de couleur a gauche
    Row(modifier.background(Color.White).border(1.dp, Bordure)) {
        Box(Modifier.width(4.dp).height(72.dp).background(couleur))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(valeur, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = couleur)
            Text(titre.uppercase(), fontSize = 11.sp, color = Color.Gray, letterSpacing = 0.4.sp)
        }
    }
}

@Composable
fun Pastille(texte: String, couleur: Color) {
    Text(texte, fontSize = 12.sp, color = couleur, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(couleur.copy(alpha = 0.12f)).border(1.dp, couleur.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun PointPriorite(p: PrioriteSuivi, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(p.couleur()))
        Spacer(Modifier.width(6.dp))
        Text(p.texte(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Libelle de priorite avec accents, pour l'ecran PC. */
fun PrioriteSuivi.texte() = when (this) {
    PrioriteSuivi.HAUTE -> "À revoir en priorité"
    PrioriteSuivi.MOYENNE -> "À surveiller"
    PrioriteSuivi.BASSE -> "Stable"
    PrioriteSuivi.INCONNUE -> "Pas assez de données"
}

fun PrioriteSuivi.couleur() = when (this) {
    PrioriteSuivi.HAUTE -> Rouge
    PrioriteSuivi.MOYENNE -> Orange
    PrioriteSuivi.BASSE -> Vert
    PrioriteSuivi.INCONNUE -> Color.Gray
}

fun CategorieTension.couleur() = when (this) {
    CategorieTension.BASSE -> Color(0xFF1E88E5)
    CategorieTension.OBJECTIF, CategorieTension.OBJECTIF_AGE, CategorieTension.OBJECTIF_PERSO -> Vert
    CategorieTension.AU_DESSUS_OBJECTIF -> Color(0xFFF9A825)
    CategorieTension.HTA_DOMICILE -> Orange
    CategorieTension.URGENCE -> Color(0xFFC62828)
}

fun couleurGlycemie(v: Double) = when {
    v < 70 -> Rouge
    v > 180 -> Orange
    else -> Vert
}

fun LocalDate.texte() = "%02d/%02d/%d".format(dayOfMonth, monthNumber, year)
fun LocalDateTime.jour() = "%02d/%02d/%d".format(dayOfMonth, monthNumber, year)
fun LocalDateTime.heure() = "%02dh%02d".format(hour, minute)

// ── Outils ───────────────────────────────────────────────────────────────

@Composable
private fun EcranOutils() {
    var hba1c by remember { mutableStateOf("") }
    var glycemie by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 1200.dp).fillMaxHeight().defilementClavier(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Outils", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        // Glycemie : HbA1c et statut cote a cote
        DeuxColonnes {
            Panneau("HbA1c → glycémie moyenne estimée (ADAG)", it) {
                OutlinedTextField(hba1c, { hba1c = it.take(5) }, label = { Text("HbA1c (%)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                hba1c.replace(',', '.').toDoubleOrNull()?.takeIf { it in 3.0..20.0 }?.let { v ->
                    Copiable {
                        Text("≈ ${ReglesGlycemie.glycemieMoyenneDepuisHbA1c(v).toInt()} mg/dL · " +
                            ReglesGlycemie.interpreterHbA1c(v).getDisplayName())
                    }
                }
            }
            Panneau("Statut d'une glycémie", it) {
                OutlinedTextField(glycemie, { glycemie = it.take(5) }, label = { Text("Glycémie (mg/dL)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                glycemie.replace(',', '.').toDoubleOrNull()?.takeIf { it in 10.0..800.0 }?.let { v ->
                    Copiable {
                        Text("${ReglesGlycemie.statutGlycemie(v)} · HbA1c équivalente ≈ ${EvaluationSuivi.unChiffre(ReglesGlycemie.hba1cDepuisGlycemieMoyenne(v))} %")
                    }
                }
            }
        }
        // Tension : mesure et test couche-debout cote a cote
        DeuxColonnes {
            OutilTension(it)
            OutilOrthostatique(it)
        }
    }
}

/** Deux panneaux de meme hauteur, cote a cote (le modifier donne a chacun sa moitie). */
@Composable
private fun DeuxColonnes(contenu: @Composable RowScope.(Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        contenu(Modifier.weight(1f).fillMaxHeight())
    }
}

private fun entier(s: String) = s.trim().toIntOrNull()

@Composable
private fun ChampNombre(valeur: String, onChange: (String) -> Unit, libelle: String, modifier: Modifier = Modifier) =
    OutlinedTextField(valeur, { onChange(it.filter(Char::isDigit).take(3)) }, label = { Text(libelle) }, singleLine = true, modifier = modifier)

/** Tension arterielle : categorie (reperes ADA 2025 / ESC 2024), pression pulsee, PAM. */
@Composable
private fun OutilTension(modifier: Modifier) {
    var pas by remember { mutableStateOf("") }
    var pad by remember { mutableStateOf("") }
    var fc by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    Panneau("Tension artérielle (TA)", modifier) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(pas, { pas = it }, "PAS (mmHg)", Modifier.weight(1f))
            ChampNombre(pad, { pad = it }, "PAD (mmHg)", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(fc, { fc = it }, "FC (bpm, facultatif)", Modifier.weight(1f))
            ChampNombre(age, { age = it }, "Âge (facultatif)", Modifier.weight(1f))
        }
        val s = entier(pas); val d = entier(pad)
        if (s != null && d != null) {
            if (!ReglesTension.valide(s, d)) {
                Text("Valeurs à vérifier (la PAS doit être plus haute que la PAD).", color = Color.Gray, fontSize = 13.sp)
            } else {
                val c = ReglesTension.categorie(s, d, entier(age))
                Pastille(c.libelle, c.couleur())
                val pp = ReglesTension.pressionPulsee(s, d)
                Text("Pression pulsée : $pp mmHg" + if (ReglesTension.pressionPulseeElevee(s, d)) " (> 60 : rigidité artérielle possible)" else "")
                Text("Pression artérielle moyenne (PAM) : ${ReglesTension.pam(s, d)} mmHg")
                entier(fc)?.takeIf { it >= 100 }?.let {
                    Text("FC $it bpm au repos : tachycardie. Si elle persiste, penser à une neuropathie autonome.", color = Orange, fontSize = 13.sp)
                }
            }
        }
        Text("Objectif : moins de 130/80. Urgence : 180/110 ou plus.", fontSize = 12.sp, color = Color.Gray)
    } }
}

/** Hypotension orthostatique : couche puis debout (1 et 3 min). */
@Composable
private fun OutilOrthostatique(modifier: Modifier) {
    var pasC by remember { mutableStateOf("") }
    var padC by remember { mutableStateOf("") }
    var pasD by remember { mutableStateOf("") }
    var padD by remember { mutableStateOf("") }
    Panneau("Test d'hypotension orthostatique", modifier) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mesure couché après 5 min de repos, puis debout à 1 et 3 min : entrez la plus basse.", fontSize = 13.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(pasC, { pasC = it }, "PAS couché", Modifier.weight(1f))
            ChampNombre(padC, { padC = it }, "PAD couché", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampNombre(pasD, { pasD = it }, "PAS debout", Modifier.weight(1f))
            ChampNombre(padD, { padD = it }, "PAD debout", Modifier.weight(1f))
        }
        val sc = entier(pasC); val dc = entier(padC); val sd = entier(pasD); val dd = entier(padD)
        if (sc != null && dc != null && sd != null && dd != null && ReglesTension.valide(sc, dc) && ReglesTension.valide(sd, dd)) {
            val baisseS = sc - sd; val baisseD = dc - dd
            Text("Baisse : PAS $baisseS mmHg, PAD $baisseD mmHg")
            if (baisseS >= 20 || baisseD >= 10) Pastille("Hypotension orthostatique (baisse PAS ≥ 20 ou PAD ≥ 10)", Rouge)
            else Pastille("Pas d'hypotension orthostatique", Vert)
        }
    } }
}
