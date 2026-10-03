package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.MembreEtablissement
import com.diabeto.data.model.PatientInscrit
import com.diabeto.data.model.PrioriteSuivi
import com.diabeto.data.model.RoleEtablissement
import com.diabeto.data.model.SuiviPatient
import com.diabeto.data.model.TypeCode
import com.diabeto.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Espace etablissement (B2B2C). Voir data/model/Etablissement.kt pour le
 * schema Firestore et website/firestore.rules pour les droits.
 *
 * Tout tourne sur le plan Firebase gratuit : pas de Cloud Functions. Les
 * indicateurs du tableau de bord sont calcules sur le telephone du soignant
 * a partir des sauvegardes backups/{uid}/glucose et backups/{uid}/hba1c.
 */
@Singleton
class EtablissementRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "Etablissement"
        private const val ETABLISSEMENTS = "etablissements"
        private const val CODES = "codes_invitation"
        private const val AFFILIATIONS = "affiliations"

        /** Sans mesure depuis ce nombre de jours : patient "perdu de vue". */
        const val JOURS_PERDU_DE_VUE = 60L

        /** Mesures lues par patient pour le tableau de bord (limite le cout Firestore). */
        private const val MAX_MESURES = 150L

        // Alphabet sans 0/O ni 1/I/L pour eviter les erreurs de saisie
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun genererCode(): String =
            (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        fun normaliserCode(saisie: String): String =
            saisie.uppercase().filter { it in ALPHABET || it in "01ILO" }.take(8)

        /** Code bien forme (8 caracteres de l'alphabet) : meme regle que firestore.rules. */
        fun codeBienForme(code: String): Boolean = code.length == 8 && code.all { it in ALPHABET }

        // Limites identiques a website/firestore.rules
        const val NOM_MIN = 2
        const val NOM_MAX = 80
        const val VILLE_MAX = 60
        const val NOM_PERSONNE_MAX = 100

        /** Retire les caracteres invisibles et les espaces en trop. */
        fun nettoyerTexte(texte: String, max: Int): String =
            texte.filter { !it.isISOControl() }
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(max)
    }

    /** Code tape inconnu ou desactive (compte comme un essai rate). */
    class CodeInconnuException : Exception("Code inconnu ou expire")

    private fun etabRef(id: String) = firestore.collection(ETABLISSEMENTS).document(id)

    // ═══════════════════════════════════════════════════════════════
    //  AFFILIATION DU COMPTE CONNECTE
    // ═══════════════════════════════════════════════════════════════

    suspend fun getMonAffiliation(): Affiliation? {
        val uid = authRepository.currentUserId ?: return null
        val doc = firestore.collection(AFFILIATIONS).document(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val data = doc.data as? Map<String, Any?> ?: return null
        return Affiliation.fromMap(data).takeIf { it.etablissementId.isNotBlank() }
    }

    suspend fun getEtablissement(id: String): Etablissement? {
        val doc = etabRef(id).get().await()
        @Suppress("UNCHECKED_CAST")
        val data = doc.data as? Map<String, Any?> ?: return null
        return Etablissement.fromMap(doc.id, data)
    }

    /** Vrai si l'inscription du compte (soignant ou patient) existe encore. */
    suspend fun inscriptionToujoursValide(aff: Affiliation): Boolean {
        val uid = authRepository.currentUserId ?: return false
        val sous = if (aff.role == RoleEtablissement.PATIENT) "patients" else "membres"
        return try {
            etabRef(aff.etablissementId).collection(sous).document(uid).get().await().exists()
        } catch (e: Exception) {
            // Un soignant retire n'a plus le droit de lire : on le traite comme retire
            Log.w(TAG, "Verification inscription : ${e.message}")
            false
        }
    }

    suspend fun effacerAffiliation() {
        val uid = authRepository.currentUserId ?: return
        firestore.collection(AFFILIATIONS).document(uid).delete().await()
    }

    // ═══════════════════════════════════════════════════════════════
    //  CREER UN ETABLISSEMENT (le createur devient administrateur)
    // ═══════════════════════════════════════════════════════════════

    suspend fun creerEtablissement(nom: String, ville: String): Result<Etablissement> = runCatching {
        val uid = authRepository.currentUserId ?: error("Non connecte")
        val profil = authRepository.getCurrentUserProfile() ?: error("Profil introuvable")
        require(profil.role == UserRole.MEDECIN) { "Seul un compte soignant peut creer un etablissement" }
        require(getMonAffiliation() == null) { "Ce compte est deja rattache a un etablissement" }

        val nomPropre = nettoyerTexte(nom, NOM_MAX)
        val villePropre = nettoyerTexte(ville, VILLE_MAX)
        require(nomPropre.length >= NOM_MIN) { "Le nom de l'etablissement est trop court" }
        val ref = firestore.collection(ETABLISSEMENTS).document()
        val now = System.currentTimeMillis()
        val etab = Etablissement(
            id = ref.id,
            nom = nomPropre,
            ville = villePropre,
            adminUid = uid,
            codePatient = genererCode(),
            codeSoignant = genererCode(),
            createdAt = now
        )
        firestore.batch().apply {
            set(ref, mapOf(
                "nom" to etab.nom,
                "ville" to etab.ville,
                "adminUid" to uid,
                "codePatient" to etab.codePatient,
                "codeSoignant" to etab.codeSoignant,
                "createdAt" to now
            ))
            set(ref.collection("membres").document(uid), mapOf(
                "uid" to uid,
                "nom" to nettoyerTexte(profil.nomComplet, NOM_PERSONNE_MAX),
                "role" to RoleEtablissement.ADMIN.name,
                "joinedAt" to now
            ))
            set(firestore.collection(CODES).document(etab.codePatient), codeMap(etab, TypeCode.PATIENT))
            set(firestore.collection(CODES).document(etab.codeSoignant), codeMap(etab, TypeCode.SOIGNANT))
            set(firestore.collection(AFFILIATIONS).document(uid), mapOf(
                "etablissementId" to ref.id,
                "etablissementNom" to etab.nom,
                "role" to RoleEtablissement.ADMIN.name
            ))
        }.commit().await()
        etab
    }

    private fun codeMap(etab: Etablissement, type: TypeCode) = mapOf(
        "etablissementId" to etab.id,
        "etablissementNom" to etab.nom,
        "type" to type.name,
        "actif" to true,
        "createdAt" to System.currentTimeMillis()
    )

    /** Administrateur : remplace un code (l'ancien ne marche plus). */
    suspend fun regenererCode(etab: Etablissement, type: TypeCode): Result<Etablissement> = runCatching {
        val nouveau = genererCode()
        val ancien = if (type == TypeCode.PATIENT) etab.codePatient else etab.codeSoignant
        val maj = if (type == TypeCode.PATIENT) etab.copy(codePatient = nouveau) else etab.copy(codeSoignant = nouveau)
        firestore.batch().apply {
            if (ancien.isNotBlank()) update(firestore.collection(CODES).document(ancien), "actif", false)
            set(firestore.collection(CODES).document(nouveau), codeMap(maj, type))
            update(etabRef(etab.id), if (type == TypeCode.PATIENT) "codePatient" else "codeSoignant", nouveau)
        }.commit().await()
        maj
    }

    // ═══════════════════════════════════════════════════════════════
    //  REJOINDRE AVEC UN CODE (patient ou soignant)
    // ═══════════════════════════════════════════════════════════════

    data class InfoCode(val code: String, val etablissementId: String, val etablissementNom: String, val type: TypeCode)

    /** Lit un code (sans rien ecrire) pour afficher le nom du centre avant d'accepter. */
    suspend fun verifierCode(saisie: String): Result<InfoCode> = runCatching {
        val code = normaliserCode(saisie)
        require(codeBienForme(code)) { "Le code fait 8 caracteres (lettres et chiffres)" }
        val doc = firestore.collection(CODES).document(code).get().await()
        if (!doc.exists() || doc.getBoolean("actif") != true) throw CodeInconnuException()
        InfoCode(
            code = code,
            etablissementId = doc.getString("etablissementId") ?: error("Code invalide"),
            etablissementNom = doc.getString("etablissementNom") ?: "",
            type = runCatching { TypeCode.valueOf(doc.getString("type") ?: "") }.getOrElse { error("Code invalide") }
        )
    }

    suspend fun rejoindre(info: InfoCode): Result<Unit> = runCatching {
        val uid = authRepository.currentUserId ?: error("Non connecte")
        val profil = authRepository.getCurrentUserProfile() ?: error("Profil introuvable")
        require(getMonAffiliation() == null) { "Quitte d'abord ton etablissement actuel" }
        val estSoignant = profil.role == UserRole.MEDECIN
        when (info.type) {
            TypeCode.SOIGNANT -> require(estSoignant) { "Ce code est reserve aux soignants. Demande le code patient." }
            TypeCode.PATIENT -> require(!estSoignant) { "Ce code est pour les patients. Demande le code soignant a l'administrateur." }
        }
        val now = System.currentTimeMillis()
        val ref = etabRef(info.etablissementId)
        firestore.batch().apply {
            if (info.type == TypeCode.PATIENT) {
                set(ref.collection("patients").document(uid), mapOf(
                    "uid" to uid,
                    "nom" to nettoyerTexte(profil.nomComplet, NOM_PERSONNE_MAX),
                    "code" to info.code,
                    "inscritAt" to now
                ))
            } else {
                set(ref.collection("membres").document(uid), mapOf(
                    "uid" to uid,
                    "nom" to nettoyerTexte(profil.nomComplet, NOM_PERSONNE_MAX),
                    "role" to RoleEtablissement.SOIGNANT.name,
                    "code" to info.code,
                    "joinedAt" to now
                ))
            }
            set(firestore.collection(AFFILIATIONS).document(uid), mapOf(
                "etablissementId" to info.etablissementId,
                "etablissementNom" to info.etablissementNom,
                "role" to (if (info.type == TypeCode.PATIENT) RoleEtablissement.PATIENT else RoleEtablissement.SOIGNANT).name
            ))
        }.commit().await()
    }

    /** Patient ou soignant : quitte l'etablissement (l'administrateur ne peut pas). */
    suspend fun quitter(aff: Affiliation): Result<Unit> = runCatching {
        val uid = authRepository.currentUserId ?: error("Non connecte")
        require(aff.role != RoleEtablissement.ADMIN) { "L'administrateur ne peut pas quitter son etablissement" }
        val sous = if (aff.role == RoleEtablissement.PATIENT) "patients" else "membres"
        firestore.batch().apply {
            delete(etabRef(aff.etablissementId).collection(sous).document(uid))
            delete(firestore.collection(AFFILIATIONS).document(uid))
        }.commit().await()
    }

    // ═══════════════════════════════════════════════════════════════
    //  EQUIPE ET PATIENTS (soignants du centre)
    // ═══════════════════════════════════════════════════════════════

    suspend fun getMembres(etabId: String): List<MembreEtablissement> =
        etabRef(etabId).collection("membres").get().await().documents
            .mapNotNull { d ->
                @Suppress("UNCHECKED_CAST")
                (d.data as? Map<String, Any?>)?.let { MembreEtablissement.fromMap(it) }
            }
            .sortedWith(compareBy<MembreEtablissement>({ it.role != RoleEtablissement.ADMIN }, { it.nom }))

    suspend fun getPatients(etabId: String): List<PatientInscrit> =
        etabRef(etabId).collection("patients").get().await().documents
            .mapNotNull { d ->
                @Suppress("UNCHECKED_CAST")
                (d.data as? Map<String, Any?>)?.let { PatientInscrit.fromMap(it) }
            }

    /** Administrateur : retire un soignant ou un patient du centre. */
    suspend fun retirerMembre(etabId: String, uid: String): Result<Unit> = runCatching {
        etabRef(etabId).collection("membres").document(uid).delete().await()
    }

    suspend fun retirerPatient(etabId: String, uid: String): Result<Unit> = runCatching {
        etabRef(etabId).collection("patients").document(uid).delete().await()
    }

    // ═══════════════════════════════════════════════════════════════
    //  TABLEAU DE BORD : indicateurs par patient
    // ═══════════════════════════════════════════════════════════════

    suspend fun calculerSuivi(patients: List<PatientInscrit>): List<SuiviPatient> = coroutineScope {
        val limite = Semaphore(4)
        patients.map { p ->
            async { limite.withPermit { suiviPatient(p) } }
        }.awaitAll()
    }

    private suspend fun suiviPatient(p: PatientInscrit): SuiviPatient {
        val backup = firestore.collection("backups").document(p.uid)
        val maintenant = LocalDateTime.now()
        val mesures = try {
            backup.collection("glucose")
                .orderBy("dateHeure", Query.Direction.DESCENDING)
                .limit(MAX_MESURES)
                .get().await().documents
                .mapNotNull { d ->
                    val date = (d.getString("dateHeure"))?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                    val valeur = (d.get("valeur") as? Number)?.toDouble()
                    if (date != null && valeur != null) date to valeur else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "Glycemies illisibles pour ${p.uid} : ${e.message}")
            emptyList()
        }
        val hba1c = try {
            backup.collection("hba1c")
                .orderBy("dateMesure", Query.Direction.DESCENDING)
                .limit(10)
                .get().await().documents
                .mapNotNull { d ->
                    val date = d.getString("dateMesure")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val valeur = (d.get("valeur") as? Number)?.toDouble()
                    if (date != null && valeur != null) Triple(date, valeur, d.getBoolean("estEstimation") == true) else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "HbA1c illisibles pour ${p.uid} : ${e.message}")
            emptyList()
        }
        return evaluer(p, mesures, hba1c, maintenant)
    }

    /**
     * Regles de priorite (aide au suivi, pas un diagnostic) :
     *  - HAUTE : HbA1c >= 9 %, ou une glycemie < 54 mg/dL sur 30 jours,
     *            ou 3 glycemies < 70 mg/dL ou plus, ou moyenne 30 j >= 250 mg/dL
     *  - MOYENNE : HbA1c >= 7 %, moyenne 30 j >= 180 mg/dL, 1 ou 2 glycemies
     *              < 70 mg/dL, ou aucune HbA1c depuis 6 mois
     *  - BASSE : rien de tout cela, avec des donnees recentes
     *  - Perdu de vue : aucune mesure depuis JOURS_PERDU_DE_VUE jours
     *    (ou aucune mesure du tout, inscrit depuis plus de 30 jours)
     */
    internal fun evaluer(
        p: PatientInscrit,
        mesures: List<Pair<LocalDateTime, Double>>,
        hba1c: List<Triple<LocalDate, Double, Boolean>>,
        maintenant: LocalDateTime
    ): SuiviPatient {
        val depuis30 = maintenant.minusDays(30)
        val recentes = mesures.filter { it.first.isAfter(depuis30) }.map { it.second }
        val moyenne = recentes.takeIf { it.isNotEmpty() }?.average()
        val hypos = recentes.count { it < 70 }
        val hyposSeveres = recentes.count { it < 54 }
        val derniere = mesures.maxOfOrNull { it.first }
        // Une vraie HbA1c de labo passe avant une estimation
        val hReelle = hba1c.filter { !it.third }.maxByOrNull { it.first }
        val h = hReelle ?: hba1c.maxByOrNull { it.first }
        val inscritDepuisJours = if (p.inscritAt > 0)
            ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(p.inscritAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                maintenant
            ) else 0L
        val perdu = if (derniere != null)
            ChronoUnit.DAYS.between(derniere, maintenant) >= JOURS_PERDU_DE_VUE
        else inscritDepuisJours >= 30

        val haute = mutableListOf<String>()
        val moyenneR = mutableListOf<String>()
        if (h != null && h.second >= 9.0) haute += "HbA1c ${fmt(h.second)} %"
        else if (h != null && h.second >= 7.0) moyenneR += "HbA1c ${fmt(h.second)} %"
        if (hyposSeveres > 0) haute += "$hyposSeveres glycemie(s) < 54 mg/dL en 30 j"
        if (hypos >= 3) haute += "$hypos glycemies < 70 mg/dL en 30 j"
        else if (hypos > 0 && hyposSeveres == 0) moyenneR += "$hypos glycemie(s) < 70 mg/dL en 30 j"
        if (moyenne != null && moyenne >= 250) haute += "moyenne 30 j ${moyenne.toInt()} mg/dL"
        else if (moyenne != null && moyenne >= 180) moyenneR += "moyenne 30 j ${moyenne.toInt()} mg/dL"
        if (h == null || h.first.isBefore(maintenant.toLocalDate().minusMonths(6))) moyenneR += "pas d'HbA1c depuis 6 mois"

        val priorite = when {
            haute.isNotEmpty() -> PrioriteSuivi.HAUTE
            recentes.isEmpty() && h == null -> PrioriteSuivi.INCONNUE
            moyenneR.isNotEmpty() -> PrioriteSuivi.MOYENNE
            else -> PrioriteSuivi.BASSE
        }
        val raisons = buildList {
            if (perdu) add(if (derniere == null) "aucune mesure partagee" else "aucune mesure depuis ${ChronoUnit.DAYS.between(derniere, maintenant)} jours")
            addAll(haute)
            if (priorite != PrioriteSuivi.INCONNUE) addAll(moyenneR)
        }
        return SuiviPatient(
            uid = p.uid,
            nom = p.nom,
            inscritAt = p.inscritAt,
            derniereMesure = derniere,
            nbMesures30j = recentes.size,
            moyenne30j = moyenne,
            nbHypos30j = hypos,
            nbHyposSeveres30j = hyposSeveres,
            derniereHbA1c = h?.second,
            dateHbA1c = h?.first,
            hba1cEstimee = h?.third == true,
            priorite = priorite,
            raisons = raisons,
            perduDeVue = perdu
        )
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.FRANCE, "%.1f", v)
}
