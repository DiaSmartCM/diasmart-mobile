package com.diabeto.desktop

import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.MembreEtablissement
import com.diabeto.data.model.PatientInscrit
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.data.model.TypeCode
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.MesureHbA1c
import com.diabeto.domain.MesureTension
import com.diabeto.domain.ResultatSuivi
import com.diabeto.util.ReglesEssais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Instant
import java.util.prefs.Preferences

// ═══════════════════════════════════════════════════════════════════════
//  Essais rates (memes regles que le mobile, stockees sur ce PC)
// ═══════════════════════════════════════════════════════════════════════

class LimiteurPc(private val cle: String) {
    private val prefs = Preferences.userRoot().node("diasmart/limiteur_essais")

    fun attenteRestanteMs(): Long = (prefs.getLong("$cle.bloqueJusqua", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun echec() {
        val n = prefs.getInt("$cle.echecs", 0) + 1
        val delai = ReglesEssais.delaiApres(n)
        prefs.putInt("$cle.echecs", n)
        prefs.putLong("$cle.bloqueJusqua", if (delai > 0) System.currentTimeMillis() + delai else 0L)
    }

    fun reussite() {
        prefs.remove("$cle.echecs"); prefs.remove("$cle.bloqueJusqua")
    }

    fun essaisRestants(): Int = (ReglesEssais.ESSAIS_LIBRES - prefs.getInt("$cle.echecs", 0)).coerceAtLeast(0)

    fun messageBlocage(): String = "Trop d'essais : réessayez dans ${ReglesEssais.formaterAttente(attenteRestanteMs())}."

    fun messageApresEchec(debut: String): String {
        val attente = attenteRestanteMs()
        return when {
            attente > 0 -> "$debut ${messageBlocage()}"
            essaisRestants() in 1..2 -> "$debut Encore ${essaisRestants()} essai(s) avant un blocage temporaire."
            else -> debut
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Compte soignant : profil, code de verification email (API du site)
// ═══════════════════════════════════════════════════════════════════════

data class Profil(val uid: String, val nom: String, val prenom: String, val role: String, val email: String) {
    val nomComplet get() = "$prenom $nom".trim()
    val estSoignant get() = role == "MEDECIN"
}

class ServiceCompte(private val fb: FirebaseRest) {
    private val http = HttpClient.newHttpClient()
    private val api = "https://website-omega-umber-20.vercel.app/api"

    suspend fun profil(uid: String): Profil? = fb.document("users/$uid")?.let {
        Profil(uid, it["nom"] as? String ?: "", it["prenom"] as? String ?: "", it["role"] as? String ?: "", it["email"] as? String ?: "")
    }

    /** Cree le profil soignant (memes champs que l'app mobile, UserProfile.toMap). */
    suspend fun creerProfilSoignant(uid: String, email: String, nom: String, prenom: String) {
        fb.lot(listOf(FirebaseRest.Ecriture.Poser("users/$uid", mapOf(
            "uid" to uid,
            "email" to email,
            "nom" to nettoyer(nom, 60),
            "prenom" to nettoyer(prenom, 60),
            "role" to "MEDECIN",
            "telephone" to "",
            "createdAt" to FirebaseRest.Horodatage(Instant.now().toString()),
            "ratingSum" to 0.0,
            "reviewCount" to 0,
            "consultationCount" to 0
        ))))
    }

    suspend fun envoyerCodeEmail() = appelApi("send-email-otp", "{}")

    suspend fun verifierCodeEmail(code: String) = appelApi("verify-email-otp", """{"code":"${code.filter { it.isDigit() }.take(6)}"}""")

    private suspend fun appelApi(chemin: String, corps: String) = withContext(Dispatchers.IO) {
        val req = HttpRequest.newBuilder(URI.create("$api/$chemin"))
            .header("Authorization", "Bearer ${fb.jetonValide()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(corps)).build()
        val rep = try { http.send(req, HttpResponse.BodyHandlers.ofString()) }
        catch (e: java.io.IOException) { throw FirebaseRest.ErreurFirebase("Pas de connexion Internet.") }
        if (rep.statusCode() !in 200..299) {
            val msg = runCatching {
                Json.parseToJsonElement(rep.body()).jsonObject["message"]?.jsonPrimitive?.content
            }.getOrNull()
            throw FirebaseRest.ErreurFirebase(msg ?: "Erreur du serveur (${rep.statusCode()}).", rep.statusCode())
        }
    }
}

fun nettoyer(texte: String, max: Int): String =
    texte.filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim().take(max)

// ═══════════════════════════════════════════════════════════════════════
//  Etablissement : memes chemins et memes ecritures que EtablissementRepository
// ═══════════════════════════════════════════════════════════════════════

class ServiceEtablissement(private val fb: FirebaseRest) {
    companion object {
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val hasard = SecureRandom()
        fun genererCode() = (1..8).map { ALPHABET[hasard.nextInt(ALPHABET.length)] }.joinToString("")
        fun normaliserCode(s: String) = s.uppercase().filter { it in ALPHABET || it in "01ILO" }.take(8)
        fun codeBienForme(c: String) = c.length == 8 && c.all { it in ALPHABET }
        private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        fun nouvelId() = (1..20).map { ID_ALPHABET[hasard.nextInt(ID_ALPHABET.length)] }.joinToString("")
    }

    data class InfoCode(val code: String, val etablissementId: String, val etablissementNom: String, val type: TypeCode)

    suspend fun monAffiliation(uid: String): Affiliation? =
        fb.document("affiliations/$uid")?.let(Affiliation::fromMap)?.takeIf { it.etablissementId.isNotBlank() }

    suspend fun etablissement(id: String): Etablissement? =
        fb.document("etablissements/$id")?.let { Etablissement.fromMap(id, it) }

    suspend fun membres(id: String): List<MembreEtablissement> =
        fb.collection("etablissements/$id/membres").map(MembreEtablissement::fromMap)
            .sortedWith(compareBy<MembreEtablissement>({ it.role != RoleEtablissement.ADMIN }, { it.nom }))

    suspend fun patients(id: String): List<PatientInscrit> =
        fb.collection("etablissements/$id/patients").map(PatientInscrit::fromMap)

    suspend fun creer(uid: String, profil: Profil, nom: String, ville: String): Etablissement {
        val nomPropre = nettoyer(nom, 80)
        require(nomPropre.length >= 2) { "Le nom de l'établissement est trop court." }
        val id = nouvelId()
        val now = System.currentTimeMillis()
        val e = Etablissement(id, nomPropre, nettoyer(ville, 60), uid, genererCode(), genererCode(), now)
        fun code(type: TypeCode) = mapOf(
            "etablissementId" to id, "etablissementNom" to e.nom, "type" to type.name, "actif" to true, "createdAt" to now
        )
        fb.lot(listOf(
            FirebaseRest.Ecriture.Poser("etablissements/$id", mapOf(
                "nom" to e.nom, "ville" to e.ville, "adminUid" to uid,
                "codePatient" to e.codePatient, "codeSoignant" to e.codeSoignant, "createdAt" to now
            )),
            FirebaseRest.Ecriture.Poser("etablissements/$id/membres/$uid", mapOf(
                "uid" to uid, "nom" to nettoyer(profil.nomComplet, 100), "role" to "ADMIN", "joinedAt" to now
            )),
            FirebaseRest.Ecriture.Poser("codes_invitation/${e.codePatient}", code(TypeCode.PATIENT)),
            FirebaseRest.Ecriture.Poser("codes_invitation/${e.codeSoignant}", code(TypeCode.SOIGNANT)),
            FirebaseRest.Ecriture.Poser("affiliations/$uid", mapOf(
                "etablissementId" to id, "etablissementNom" to e.nom, "role" to "ADMIN"
            ))
        ))
        return e
    }

    /** Lit un code sans rien ecrire. Null = code inconnu ou desactive (essai rate). */
    suspend fun verifierCode(saisie: String): InfoCode? {
        val code = normaliserCode(saisie)
        require(codeBienForme(code)) { "Le code fait 8 caractères (lettres et chiffres)." }
        val d = fb.document("codes_invitation/$code") ?: return null
        if (d["actif"] != true) return null
        return InfoCode(code, d["etablissementId"] as? String ?: return null, d["etablissementNom"] as? String ?: "",
            runCatching { TypeCode.valueOf(d["type"] as? String ?: "") }.getOrNull() ?: return null)
    }

    suspend fun rejoindreCommeSoignant(uid: String, profil: Profil, info: InfoCode) {
        require(info.type == TypeCode.SOIGNANT) { "Ce code est pour les patients. Demandez le code soignant à l'administrateur." }
        val now = System.currentTimeMillis()
        fb.lot(listOf(
            FirebaseRest.Ecriture.Poser("etablissements/${info.etablissementId}/membres/$uid", mapOf(
                "uid" to uid, "nom" to nettoyer(profil.nomComplet, 100), "role" to "SOIGNANT", "code" to info.code, "joinedAt" to now
            )),
            FirebaseRest.Ecriture.Poser("affiliations/$uid", mapOf(
                "etablissementId" to info.etablissementId, "etablissementNom" to info.etablissementNom, "role" to "SOIGNANT"
            ))
        ))
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Patients suivis : liens directs (data_sharing) + etablissement
// ═══════════════════════════════════════════════════════════════════════

enum class Origine(val libelle: String) { LIEN("Lien direct"), ETABLISSEMENT("Établissement"), LES_DEUX("Lien + établissement") }

data class PatientSuivi(
    val uid: String, val nom: String, val origine: Origine, val inscritAt: Long,
    val resultat: ResultatSuivi, val mesures: List<Mesure>, val tensions: List<MesureTension> = emptyList()
)

data class Mesure(val date: LocalDateTime, val valeur: Double, val contexte: String)

class ServicePatients(private val fb: FirebaseRest) {
    private val fuseau = TimeZone.currentSystemDefault()

    suspend fun patientsSuivis(medecinUid: String, etabPatients: List<PatientInscrit>): List<PatientSuivi> = coroutineScope {
        val liens = runCatching {
            fb.requete(null, "data_sharing", mapOf("medecinUid" to medecinUid, "isActive" to true))
                .map { it.second }
        }.getOrDefault(emptyList())
        val tous = LinkedHashMap<String, Triple<String, Origine, Long>>()
        liens.forEach { l ->
            val uid = l["patientUid"] as? String ?: return@forEach
            tous[uid] = Triple(l["patientNom"] as? String ?: "", Origine.LIEN, 0L)
        }
        etabPatients.forEach { p ->
            val avant = tous[p.uid]
            tous[p.uid] = Triple(p.nom.ifBlank { avant?.first ?: "" }, if (avant != null) Origine.LES_DEUX else Origine.ETABLISSEMENT, p.inscritAt)
        }
        val maintenant = Clock.System.now().toLocalDateTime(fuseau)
        val limite = Semaphore(4)
        tous.map { (uid, t) ->
            async {
                limite.withPermit {
                    val m = mesures(uid, 150)
                    val tn = tensions(uid, 60)
                    PatientSuivi(uid, t.first, t.second, t.third,
                        EvaluationSuivi.evaluer(t.third, m.map { it.date to it.valeur }, hba1c(uid), maintenant, fuseau, tn), m, tn)
                }
            }
        }.awaitAll().sortedWith(compareBy<PatientSuivi>({ it.resultat.priorite.ordinal }, { !it.resultat.perduDeVue }, { it.nom }))
    }

    suspend fun mesures(uid: String, limite: Int): List<Mesure> = runCatching {
        fb.derniers("backups/$uid", "glucose", "dateHeure", limite).mapNotNull { m ->
            val date = (m["dateHeure"] as? String)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val valeur = (m["valeur"] as? Number)?.toDouble()
            if (date != null && valeur != null) Mesure(date, valeur, m["contexte"] as? String ?: "") else null
        }
    }.getOrDefault(emptyList())

    /** Tensions (backups/{uid}/tension), ecrites par le telephone ou le site ; sans doublons. */
    suspend fun tensions(uid: String, limite: Int): List<MesureTension> = runCatching {
        fb.derniers("backups/$uid", "tension", "dateHeure", limite).mapNotNull { m ->
            val date = (m["dateHeure"] as? String)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val sys = (m["systolique"] as? Number)?.toInt()
            val dia = (m["diastolique"] as? Number)?.toInt()
            if (date != null && sys != null && dia != null) MesureTension(
                date, sys, dia, (m["pouls"] as? Number)?.toInt(),
                position = m["position"] as? String ?: "", bras = m["bras"] as? String ?: "",
                traitement = m["traitementAntihypertenseur"] as? Boolean
            ) else null
        }.distinctBy { Triple(it.date, it.systolique, it.diastolique) }
    }.getOrDefault(emptyList())

    /** Objectif de tension personnel (objectifs_tension/{uid}), ou null. */
    suspend fun objectifTension(uid: String): com.diabeto.domain.ObjectifTension? = runCatching {
        fb.document("objectifs_tension/$uid")?.let { m ->
            val s = (m["systolique"] as? Number)?.toInt()
            val d = (m["diastolique"] as? Number)?.toInt()
            if (s != null && d != null) com.diabeto.domain.ObjectifTension(s, d, m["auteurNom"] as? String ?: "") else null
        }
    }.getOrNull()

    suspend fun fixerObjectifTension(uid: String, systolique: Int, diastolique: Int, auteur: Profil) {
        require(com.diabeto.domain.ReglesTension.objectifValide(systolique, diastolique)) {
            "Objectif invalide : PAS entre 100 et 170, PAD entre 60 et 110."
        }
        fb.lot(listOf(FirebaseRest.Ecriture.Poser("objectifs_tension/$uid", mapOf(
            "systolique" to systolique, "diastolique" to diastolique,
            "auteurUid" to auteur.uid, "auteurNom" to auteur.nomComplet.take(100),
            "majAt" to System.currentTimeMillis()
        ))))
    }

    suspend fun retirerObjectifTension(uid: String) {
        fb.lot(listOf(FirebaseRest.Ecriture.Supprimer("objectifs_tension/$uid")))
    }

    suspend fun hba1c(uid: String): List<MesureHbA1c> = runCatching {
        fb.derniers("backups/$uid", "hba1c", "dateMesure", 10).mapNotNull { m ->
            val date = (m["dateMesure"] as? String)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            val valeur = (m["valeur"] as? Number)?.toDouble()
            if (date != null && valeur != null) MesureHbA1c(date, valeur, m["estEstimation"] == true) else null
        }
    }.getOrDefault(emptyList())
}

// ═══════════════════════════════════════════════════════════════════════
//  Rendez-vous : demandes des patients (rdv_requests)
// ═══════════════════════════════════════════════════════════════════════

data class DemandeRdv(
    val id: String, val patientUid: String, val patientNom: String, val medecinNom: String,
    val dateIso: String, val date: LocalDateTime?, val dureeMinutes: Int,
    val motif: String, val type: String, val statut: String, val reponse: String
)

class ServiceRdv(private val fb: FirebaseRest) {
    suspend fun demandes(medecinUid: String): List<DemandeRdv> =
        fb.requete(null, "rdv_requests", mapOf("medecinUid" to medecinUid)).map { (id, m) ->
            val iso = m["dateHeureSouhaitee"] as? String ?: ""
            DemandeRdv(
                id = id,
                patientUid = m["patientUid"] as? String ?: "",
                patientNom = m["patientNom"] as? String ?: "",
                medecinNom = m["medecinNom"] as? String ?: "",
                dateIso = iso,
                date = runCatching { LocalDateTime.parse(iso) }.getOrNull(),
                dureeMinutes = (m["dureeMinutes"] as? Number)?.toInt() ?: 30,
                motif = m["motif"] as? String ?: "",
                type = m["type"] as? String ?: "CONSULTATION",
                statut = (m["status"] as? String ?: "PENDING").uppercase(),
                reponse = m["medecinReponse"] as? String ?: ""
            )
        }.sortedBy { it.date }

    /** Comme l'app mobile : statut ACCEPTED + RDV copie chez le patient (rdv_shared). */
    suspend fun accepter(d: DemandeRdv, medecinUid: String, reponse: String) {
        val maintenant = FirebaseRest.Horodatage(Instant.now().toString())
        fb.lot(listOf(
            FirebaseRest.Ecriture.Changer("rdv_requests/${d.id}", mapOf(
                "status" to "ACCEPTED", "medecinReponse" to nettoyer(reponse, 300), "updatedAt" to maintenant
            )),
            FirebaseRest.Ecriture.Poser("rdv_shared/${d.patientUid}/rendezvous/${d.id}", mapOf(
                "titre" to d.motif.ifBlank { "Consultation" },
                "dateHeure" to d.dateIso,
                "dureeMinutes" to d.dureeMinutes,
                "type" to d.type,
                "lieu" to "",
                "notes" to d.motif,
                "estConfirme" to true,
                "medecinNom" to d.medecinNom,
                "medecinUid" to medecinUid,
                "createdAt" to maintenant
            ))
        ))
    }

    suspend fun refuser(d: DemandeRdv, reponse: String) {
        fb.lot(listOf(FirebaseRest.Ecriture.Changer("rdv_requests/${d.id}", mapOf(
            "status" to "REJECTED", "medecinReponse" to nettoyer(reponse, 300),
            "updatedAt" to FirebaseRest.Horodatage(Instant.now().toString())
        ))))
    }
}
