package com.diabeto.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.diabeto.data.model.ReportRecord
import com.diabeto.report.PdfReportGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline complet d'export et d'envoi de rapports PDF.
 *
 *  1. Collecte des donnees (glucose, repas, medicaments, journal)
 *  2. Generation du PDF via PdfReportGenerator
 *  3. Upload sur Firebase Storage : reports/{ownerUid}/{reportId}.pdf
 *  4. Diffusion : messagerie in-app et/ou email (via Vercel)
 *  5. Historique : /reports/{ownerUid}/items/{reportId}
 */
@Singleton
class ReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val glucoseRepository: GlucoseRepository,
    private val medicamentRepository: MedicamentRepository,
    private val journalRepository: JournalRepository,
    private val repasRepository: RepasRepository,
    private val messagerieRepository: MessagerieRepository,
    private val dataSharingRepository: DataSharingRepository
) {
    companion object {
        private const val TAG = "ReportRepo"
        private const val BASE_URL = "https://website-omega-umber-20.vercel.app"
        private const val SEND_EMAIL_URL = "$BASE_URL/api/send-report-email"
        private const val UPLOAD_URL = "$BASE_URL/api/upload-supabase"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val PERIOD_7D = "7d"
        const val PERIOD_30D = "30d"
        const val PERIOD_CUSTOM = "custom"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val pdfGenerator = PdfReportGenerator(context)

    // ════════════════════════════════════════════════════════════════
    //  PATIENT REPORT
    // ════════════════════════════════════════════════════════════════

    /**
     * Genere un PDF pour le patient courant et le renvoie sous forme de File.
     */
    suspend fun buildPatientReport(
        period: String,
        from: LocalDate? = null,
        to: LocalDate? = null
    ): Result<File> = runCatching {
        val profile = authRepository.getCurrentUserProfile()
            ?: throw IllegalStateException("Profil introuvable")

        // Determiner la fenetre temporelle
        val (startDate, endDate, periodLabel) = when (period) {
            PERIOD_7D -> Triple(LocalDate.now().minusDays(7), LocalDate.now(), "7 derniers jours")
            PERIOD_30D -> Triple(LocalDate.now().minusDays(30), LocalDate.now(), "30 derniers jours")
            PERIOD_CUSTOM -> {
                val f = from ?: LocalDate.now().minusDays(30)
                val t = to ?: LocalDate.now()
                Triple(f, t, "${f} → ${t}")
            }
            else -> Triple(LocalDate.now().minusDays(30), LocalDate.now(), "30 derniers jours")
        }
        val startDt = startDate.atStartOfDay()
        val daysSpan = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

        // Identifier le patient local : un patient utilisateur n'a generalement
        // qu'une seule entree dans la table patients (lui-meme). On prend la
        // premiere ; pour un medecin qui ne devrait pas appeler ce flux, on
        // tombe sur le 1er patient liste.
        val patient = patientRepository.getAllPatientsList().firstOrNull()
        val patientId: Long = patient?.id ?: 0L

        val glucose = if (patientId == 0L) emptyList() else when (period) {
            PERIOD_7D -> glucoseRepository.getLast7DaysLectures(patientId)
            PERIOD_30D -> glucoseRepository.getLast30DaysLectures(patientId)
            else -> glucoseRepository.getLecturesByPatientList(patientId, 200)
                .filter { it.dateHeure.isAfter(startDt) }
        }
        val medicaments = if (patientId == 0L) emptyList()
            else medicamentRepository.getMedicamentsByPatientList(patientId)
        val journal = if (patientId == 0L) emptyList()
            else journalRepository.getEntriesBetweenDates(patientId, startDate, endDate)
        val repas = repasRepository.getRepasDepuis(daysSpan)

        val data = PdfReportGenerator.PatientReportData(
            patientNom = profile.nomComplet,
            patientEmail = profile.email,
            periodLabel = periodLabel,
            glucoseLectures = glucose,
            medicaments = medicaments,
            repas = repas,
            journal = journal
        )

        val fileName = "rapport_patient_${profile.uid.take(6)}_${LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.pdf"
        pdfGenerator.generatePatientReport(data, fileName)
    }

    // ════════════════════════════════════════════════════════════════
    //  DOCTOR REPORT
    // ════════════════════════════════════════════════════════════════

    suspend fun buildDoctorReport(
        patientUid: String,
        ordonnance: String,
        compteRendu: String,
        recommandations: String
    ): Result<File> = runCatching {
        val medecinProfile = authRepository.getCurrentUserProfile()
            ?: throw IllegalStateException("Profil medecin introuvable")
        val patientProfile = authRepository.getUserProfile(patientUid)
            ?: throw IllegalStateException("Patient introuvable")

        val data = PdfReportGenerator.DoctorReportData(
            medecinNom = medecinProfile.nomComplet,
            patientNom = patientProfile.nomComplet,
            patientEmail = patientProfile.email,
            ordonnance = ordonnance,
            compteRendu = compteRendu,
            recommandations = recommandations
        )

        val fileName = "ordonnance_${patientUid.take(6)}_${LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.pdf"
        pdfGenerator.generateDoctorReport(data, fileName)
    }

    // ════════════════════════════════════════════════════════════════
    //  UPLOAD + ENVOI + HISTORIQUE
    // ════════════════════════════════════════════════════════════════

    /**
     * Upload le PDF vers Supabase Storage via l'endpoint Vercel /api/upload-supabase.
     * On reste sur Spark Firebase ; le binaire vit dans le bucket public Supabase
     * `diasmart-files` (1 GB free, sans carte). Retourne (path, publicUrl).
     */
    /**
     * IMPORTANT : tout le bloc IO (lecture fichier + token + reseau) est
     * deplace sur Dispatchers.IO. Avant on heritait du dispatcher de l'appelant
     * (viewModelScope = Main) ce qui declenchait NetworkOnMainThreadException
     * sur l'execute() bloquant de OkHttp.
     */
    suspend fun uploadReport(file: File): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching { doUploadReport(file) }
        }

    private suspend fun doUploadReport(file: File): Pair<String, String> {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("Fichier PDF invalide (existe=${file.exists()}, taille=${file.length()})")
        }
        Log.d(TAG, "uploadReport: file=${file.name} size=${file.length()} bytes")

        // Etape 1 : token Firebase. On NE force PAS le refresh (forceRefresh=true
        // declenche un round-trip serveur qui peut timeout sur reseau MTN).
        // Le token cache est suffisant ; Firebase auto-refresh si expire bientot.
        val idToken = try {
            auth.currentUser?.getIdToken(false)?.await()?.token
                ?: throw IllegalStateException("Aucun utilisateur connecte")
        } catch (e: Exception) {
            throw IllegalStateException(
                "Echec recuperation token Firebase: ${e::class.java.simpleName}: ${e.message ?: "(reseau)"}",
                e
            )
        }

        // Etape 2 : preparation du payload
        val bytes = file.readBytes()
        if (bytes.size > 3 * 1024 * 1024) {
            throw IllegalStateException("Fichier trop volumineux (${bytes.size} octets, max 3 MB)")
        }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("folder", "reports")
            put("fileName", file.name)
            put("contentType", "application/pdf")
            put("base64", base64)
        }.toString()
        val req = Request.Builder()
            .url(UPLOAD_URL)
            .addHeader("Authorization", "Bearer $idToken")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        // Etape 3 : appel reseau (deja sur Dispatchers.IO grace a withContext)
        val (path, url) = try {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Upload HTTP ${resp.code}: $body")
                    throw IllegalStateException("HTTP ${resp.code} — ${body.take(300)}")
                }
                if (body.isBlank()) {
                    throw IllegalStateException("Reponse Vercel vide (HTTP ${resp.code})")
                }
                val json = JSONObject(body)
                json.optString("path") to json.optString("url")
            }
        } catch (e: java.io.IOException) {
            throw IllegalStateException(
                "Reseau indisponible pour l'upload: ${e::class.java.simpleName}: ${e.message ?: "(connexion interrompue)"}",
                e
            )
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Reponse Vercel non JSON: ${e.message ?: "(parse error)"}", e)
        }

        if (url.isBlank()) throw IllegalStateException("URL Supabase manquante dans la reponse")
        Log.d(TAG, "Uploaded: $path -> $url")
        return path to url
    }

    /**
     * Envoi par email via Vercel /api/send-report-email.
     */
    suspend fun sendByEmail(
        toEmail: String,
        subject: String,
        bodyHtml: String,
        downloadUrl: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                ?: throw IllegalStateException("ID token indisponible")
            val payload = JSONObject().apply {
                put("toEmail", toEmail)
                put("subject", subject)
                put("bodyHtml", bodyHtml)
                put("downloadUrl", downloadUrl)
                put("fileName", fileName)
            }.toString()
            val req = Request.Builder()
                .url(SEND_EMAIL_URL)
                .addHeader("Authorization", "Bearer $idToken")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Email HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
            }
        }
    }

    /**
     * Envoi en messagerie in-app : insere un message avec piece jointe dans
     * la conversation entre owner et recipient. Cree la conversation si
     * elle n'existe pas (recherche/creation idempotente cote MessagerieRepository).
     */
    suspend fun sendByMessagerie(
        recipientUid: String,
        downloadUrl: String,
        fileName: String,
        contenu: String
    ): Result<Unit> = runCatching {
        val current = authRepository.getCurrentUserProfile()
            ?: throw IllegalStateException("Utilisateur non connecte")
        val recipientProfile = authRepository.getUserProfile(recipientUid)
            ?: throw IllegalStateException("Destinataire introuvable")
        val convId = messagerieRepository
            .findOrCreateConversationWith(current, recipientProfile)
            .getOrThrow()
        messagerieRepository.envoyerMessageAvecPieceJointe(
            conversationId = convId,
            contenu = contenu,
            attachmentUrl = downloadUrl,
            attachmentName = fileName,
            attachmentType = "application/pdf"
        ).getOrThrow()
    }

    /**
     * Persiste le rapport dans /reports/{ownerUid}/items/{id} pour l'historique.
     */
    suspend fun recordReport(record: ReportRecord): Result<String> = runCatching {
        val owner = record.ownerUid.ifBlank { auth.currentUser?.uid.orEmpty() }
        require(owner.isNotBlank()) { "ownerUid manquant" }
        val ref = firestore.collection("reports").document(owner)
            .collection("items").document()
        val withId = record.copy(id = ref.id, ownerUid = owner)
        ref.set(withId.toMap()).await()
        ref.id
    }

    /**
     * Liste l'historique des rapports envoyes par l'utilisateur courant.
     */
    suspend fun listMyReports(limit: Long = 50): List<ReportRecord> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching emptyList()
        val snap = firestore.collection("reports").document(uid)
            .collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        snap.documents.mapNotNull { d ->
            @Suppress("UNCHECKED_CAST")
            d.data?.let { ReportRecord.fromMap(d.id, it as Map<String, Any?>) }
        }
    }.getOrElse { e ->
        Log.w(TAG, "listMyReports failed: ${e.message}")
        emptyList()
    }

    /**
     * Liste les medecins lies (cote patient) pour pre-remplir le destinataire.
     */
    suspend fun getLinkedDoctors(): List<com.diabeto.data.model.UserProfile> {
        val consents = runCatching { dataSharingRepository.getMyConsents() }
            .getOrDefault(emptyList())
            .filter { it.isActive }
        return consents.mapNotNull { authRepository.getUserProfile(it.medecinUid) }
    }

    /**
     * Liste les patients lies (cote medecin).
     */
    suspend fun getLinkedPatients(): List<com.diabeto.data.model.UserProfile> {
        val consents = runCatching { dataSharingRepository.getSharedPatients() }
            .getOrDefault(emptyList())
        return consents.mapNotNull { authRepository.getUserProfile(it.patientUid) }
    }
}
