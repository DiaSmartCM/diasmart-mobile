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
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.MesureTension
import com.diabeto.domain.MesureHbA1c
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
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
        const val JOURS_PERDU_DE_VUE = EvaluationSuivi.JOURS_PERDU_DE_VUE

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

        /**
         * Collage d'un message entier (« ... tapez le code : ABCD EFGH ») dans le champ
         * du code : on ne garde que le code. On prend le premier code bien forme apres
         * le mot « code », sinon le dernier du texte. Saisie courte : inchangee.
         */
        fun codeDepuisCollage(saisie: String): String {
            if (saisie.length <= 12) return saisie
            val texte = saisie.uppercase()
            fun codes(t: String) = Regex("""(?=\b([A-Z0-9]{4}) ?([A-Z0-9]{4})\b)""").findAll(t)
                .map { it.groupValues[1] + it.groupValues[2] }.filter { codeBienForme(it) }.toList()
            val apresMotCode = texte.lastIndexOf("CODE").takeIf { it >= 0 }?.let { codes(texte.substring(it)) }.orEmpty()
            return apresMotCode.firstOrNull() ?: codes(texte).lastOrNull() ?: saisie
        }

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
        val tensions = try {
            backup.collection("tension")
                .orderBy("dateHeure", Query.Direction.DESCENDING)
                .limit(60)
                .get().await().documents
                .mapNotNull { d ->
                    val date = d.getString("dateHeure")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                    val sys = (d.get("systolique") as? Number)?.toInt()
                    val dia = (d.get("diastolique") as? Number)?.toInt()
                    if (date != null && sys != null && dia != null)
                        MesureTension(date.toKotlinLocalDateTime(), sys, dia, (d.get("pouls") as? Number)?.toInt())
                    else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "Tensions illisibles pour ${p.uid} : ${e.message}")
            emptyList()
        }
        return evaluer(p, mesures, hba1c, maintenant, tensions)
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
        maintenant: LocalDateTime,
        tensions: List<MesureTension> = emptyList()
    ): SuiviPatient {
        // Les regles sont dans le module commun (aussi utilisees par la version PC)
        val r = EvaluationSuivi.evaluer(
            inscritAt = p.inscritAt,
            mesures = mesures.map { it.first.toKotlinLocalDateTime() to it.second },
            hba1c = hba1c.map { MesureHbA1c(it.first.toKotlinLocalDate(), it.second, it.third) },
            maintenant = maintenant.toKotlinLocalDateTime(),
            fuseau = TimeZone.currentSystemDefault(),
            tensions = tensions
        )
        return SuiviPatient(
            uid = p.uid,
            nom = p.nom,
            inscritAt = p.inscritAt,
            derniereMesure = r.derniereMesure?.toJavaLocalDateTime(),
            nbMesures30j = r.nbMesures30j,
            moyenne30j = r.moyenne30j,
            nbHypos30j = r.nbHypos30j,
            nbHyposSeveres30j = r.nbHyposSeveres30j,
            derniereHbA1c = r.hba1c?.valeur,
            dateHbA1c = r.hba1c?.date?.toJavaLocalDate(),
            hba1cEstimee = r.hba1c?.estimee == true,
            priorite = r.priorite,
            raisons = r.raisons,
            perduDeVue = r.perduDeVue,
            tensionMoyenne30j = r.tensionMoyenne30j?.let { "${it.first}/${it.second}" },
            derniereTension = r.derniereTension?.let { "${it.systolique}/${it.diastolique}" }
        )
    }
}
