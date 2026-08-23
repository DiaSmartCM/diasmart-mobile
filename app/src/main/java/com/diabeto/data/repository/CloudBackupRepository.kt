package com.diabeto.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.diabeto.data.dao.*
import com.diabeto.data.database.DiabetoDatabase
import com.diabeto.data.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud backup/restore of local Room database to Firestore.
 *
 * Firestore structure:
 *   backups/{uid}/patients/{id}
 *   backups/{uid}/glucose/{id}
 *   backups/{uid}/hba1c/{id}
 *   backups/{uid}/medicaments/{id}
 *   backups/{uid}/rendezvous/{id}
 *   backups/{uid}/journal/{id}
 *   backups/{uid}/metadata/info  → { lastBackupAt, counts... }
 *
 * Auto-backup: called after each write operation.
 * Restore: called on login if local DB is empty.
 */
@Singleton
class CloudBackupRepository @Inject constructor(
    private val database: DiabetoDatabase,
    private val patientDao: PatientDao,
    private val glucoseDao: GlucoseDao,
    private val hbA1cDao: HbA1cDao,
    private val medicamentDao: MedicamentDao,
    private val rendezVousDao: RendezVousDao,
    private val journalDao: JournalDao,
    private val pendingOperationDao: PendingOperationDao
) {
    companion object {
        private const val TAG = "CloudBackup"
        private const val BACKUPS = "backups"
    }

    private val syncMutex = Mutex()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val uid: String? get() = auth.currentUser?.uid

    // ══════════════════════════════════════════════════════════════
    // INCREMENTAL BACKUP (v2.1.44+) — uploads only docs modified since
    // `since` timestamp. Used by BatchSyncWorker (delta-only sync).
    //
    // Avantage : O(delta) au lieu de O(P × N).
    // - WriteBatch Firestore par paquets de 500 (limite API).
    // - Si delta vide -> retourne immediatement.
    // - Si > 5000 docs en attente -> on bascule en full backup (premier sync,
    //   ou retard de plusieurs jours).
    //
    // Retourne le nombre de docs effectivement pushes.
    // ══════════════════════════════════════════════════════════════

    private val BATCH_LIMIT = 500
    private val FULL_BACKUP_THRESHOLD = 5000

    suspend fun performIncrementalBackup(since: Long): Result<Int> = withContext(Dispatchers.IO) {
        val userId = uid ?: return@withContext Result.failure(Exception("Non connecte"))
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "Sync already in progress — skipping incremental backup")
            return@withContext Result.success(0)
        }
        try {
            val userRef = db.collection(BACKUPS).document(userId)

            // Collecte delta
            // v2.1.84 : la collecte delta ne portait aucun filtre de compte.
            // Sur un appareil ayant servi a plusieurs comptes, la sauvegarde
            // televersait les lignes d'AUTRUI dans le cloud du compte courant —
            // une fuite qui franchissait les frontieres de l'appareil.
            val moi = uid ?: "__aucun__"
            val patients = patientDao.getPatientsModifiedSince(since, moi)
            val glucose = glucoseDao.getLecturesModifiedSince(since, moi)
            val hba1cs = hbA1cDao.getHbA1cModifiedSince(since, moi)
            val meds = medicamentDao.getMedicamentsModifiedSince(since, moi)
            val rdvs = rendezVousDao.getRendezVousModifiedSince(since, moi)
            val journals = journalDao.getEntriesModifiedSince(since, moi)
            val total = patients.size + glucose.size + hba1cs.size + meds.size + rdvs.size + journals.size

            if (total == 0) {
                Log.d(TAG, "Incremental backup : aucun delta depuis $since")
                return@withContext Result.success(0)
            }

            // Trop de docs -> full backup plus efficace (1 seul lot Firestore)
            if (total > FULL_BACKUP_THRESHOLD) {
                Log.d(TAG, "Incremental backup : $total docs > threshold $FULL_BACKUP_THRESHOLD, bascule sur full backup")
                syncMutex.unlock()
                return@withContext performFullBackup()
            }

            // Push par batches de BATCH_LIMIT
            data class Item(val coll: String, val id: String, val map: Map<String, Any?>)
            val items = buildList<Item> {
                patients.forEach { add(Item("patients", it.id.toString(), patientToMap(it))) }
                glucose.forEach { add(Item("glucose", it.id.toString(), glucoseToMap(it))) }
                hba1cs.forEach { add(Item("hba1c", it.id.toString(), hba1cToMap(it))) }
                meds.forEach { add(Item("medicaments", it.id.toString(), medicamentToMap(it))) }
                rdvs.forEach { add(Item("rendezvous", it.id.toString(), rendezVousToMap(it))) }
                journals.forEach { add(Item("journal", it.id.toString(), journalToMap(it))) }
            }

            items.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { item ->
                    val ref = userRef.collection(item.coll).document(item.id)
                    batch.set(ref, item.map, SetOptions.merge())
                }
                batch.commit().await()
            }

            // Metadata (toujours pousse pour suivre lastBackupAt)
            userRef.collection("metadata").document("info").set(
                mapOf(
                    "lastBackupAt" to LocalDateTime.now().toString(),
                    "lastBackupKind" to "incremental",
                    "lastIncrementalDelta" to total
                ),
                SetOptions.merge()
            ).await()

            Log.d(TAG, "Incremental backup: $total docs uploaded (delta since $since)")
            Result.success(total)
        } catch (e: Exception) {
            Log.e(TAG, "Incremental backup failed", e)
            Result.failure(e)
        } finally {
            if (syncMutex.isLocked) syncMutex.unlock()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FULL BACKUP — uploads all local data to Firestore
    // ══════════════════════════════════════════════════════════════

    suspend fun performFullBackup(): Result<Int> = withContext(Dispatchers.IO) {
        val userId = uid ?: return@withContext Result.failure(Exception("Non connecte"))
        if (!syncMutex.tryLock()) {
            Log.d(TAG, "Sync already in progress — skipping backup")
            return@withContext Result.success(0)
        }
        try {
            var totalDocs = 0
            val userRef = db.collection(BACKUPS).document(userId)

            // 1. Patients
            val patients = patientDao.getAllPatientsList(uid ?: "__aucun__")
            for (p in patients) {
                userRef.collection("patients").document(p.id.toString())
                    .set(patientToMap(p), SetOptions.merge()).await()
                totalDocs++
            }

            // 2. Glucose readings (batch per patient)
            for (p in patients) {
                val readings = glucoseDao.getLecturesByPatientList(p.id, 100000)
                for (r in readings) {
                    userRef.collection("glucose").document(r.id.toString())
                        .set(glucoseToMap(r), SetOptions.merge()).await()
                    totalDocs++
                }
            }

            // 3. HbA1c
            for (p in patients) {
                val hba1cs = hbA1cDao.getHbA1cByPatientList(p.id)
                for (h in hba1cs) {
                    userRef.collection("hba1c").document(h.id.toString())
                        .set(hba1cToMap(h), SetOptions.merge()).await()
                    totalDocs++
                }
            }

            // 4. Medicaments
            for (p in patients) {
                val meds = medicamentDao.getMedicamentsByPatientList(p.id)
                for (m in meds) {
                    userRef.collection("medicaments").document(m.id.toString())
                        .set(medicamentToMap(m), SetOptions.merge()).await()
                    totalDocs++
                }
            }

            // 5. Rendez-vous
            for (p in patients) {
                val rdvs = rendezVousDao.getRendezVousByPatientList(p.id)
                for (r in rdvs) {
                    userRef.collection("rendezvous").document(r.id.toString())
                        .set(rendezVousToMap(r), SetOptions.merge()).await()
                    totalDocs++
                }
            }

            // 6. Journal
            for (p in patients) {
                val entries = journalDao.getEntriesByPatientList(p.id)
                for (j in entries) {
                    userRef.collection("journal").document(j.id.toString())
                        .set(journalToMap(j), SetOptions.merge()).await()
                    totalDocs++
                }
            }

            // 7. Metadata
            userRef.collection("metadata").document("info").set(
                mapOf(
                    "lastBackupAt" to LocalDateTime.now().toString(),
                    "patientCount" to patients.size,
                    "totalDocuments" to totalDocs
                ),
                SetOptions.merge()
            ).await()

            Log.d(TAG, "Full backup: $totalDocs documents uploaded")
            Result.success(totalDocs)
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.failure(e)
        } finally {
            syncMutex.unlock()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FULL RESTORE — downloads cloud data to local Room DB
    // ══════════════════════════════════════════════════════════════

    suspend fun performFullRestore(): Result<Int> = withContext(Dispatchers.IO) {
        val userId = uid ?: return@withContext Result.failure(Exception("Non connecte"))
        syncMutex.withLock {
            try {
                val userRef = db.collection(BACKUPS).document(userId)

                // Check if backup exists
                val metadata = userRef.collection("metadata").document("info").get().await()
                if (!metadata.exists()) {
                    Log.d(TAG, "No cloud backup found for $userId")
                    return@withContext Result.success(0)
                }

                // Fetch all cloud data BEFORE writing to local DB
                val patientDocs = userRef.collection("patients").get().await()
                val glucoseDocs = userRef.collection("glucose").get().await()
                val hba1cDocs = userRef.collection("hba1c").get().await()
                val medDocs = userRef.collection("medicaments").get().await()
                val rdvDocs = userRef.collection("rendezvous").get().await()
                val journalDocs = userRef.collection("journal").get().await()

                // Insert all data in a single Room transaction — atomic: all or nothing
                var totalDocs = 0
                database.withTransaction {
                    val patientIdMap = mutableMapOf<Long, Long>()

                    for (doc in patientDocs.documents) {
                        val patient = mapToPatient(doc.data ?: continue)
                        val oldId = patient.id
                        val newId = patientDao.insertPatient(patient.copy(id = 0))
                        patientIdMap[oldId] = newId
                        totalDocs++
                    }

                    for (doc in glucoseDocs.documents) {
                        val reading = mapToGlucose(doc.data ?: continue)
                        val newPatientId = patientIdMap[reading.patientId] ?: continue
                        glucoseDao.insertLecture(reading.copy(id = 0, patientId = newPatientId))
                        totalDocs++
                    }

                    for (doc in hba1cDocs.documents) {
                        val hba1c = mapToHbA1c(doc.data ?: continue)
                        val newPatientId = patientIdMap[hba1c.patientId] ?: continue
                        hbA1cDao.insertHbA1c(hba1c.copy(id = 0, patientId = newPatientId))
                        totalDocs++
                    }

                    for (doc in medDocs.documents) {
                        val med = mapToMedicament(doc.data ?: continue)
                        val newPatientId = patientIdMap[med.patientId] ?: continue
                        medicamentDao.insertMedicament(med.copy(id = 0, patientId = newPatientId))
                        totalDocs++
                    }

                    for (doc in rdvDocs.documents) {
                        val rdv = mapToRendezVous(doc.data ?: continue)
                        val newPatientId = patientIdMap[rdv.patientId] ?: continue
                        rendezVousDao.insertRendezVous(rdv.copy(id = 0, patientId = newPatientId))
                        totalDocs++
                    }

                    for (doc in journalDocs.documents) {
                        val entry = mapToJournal(doc.data ?: continue)
                        val newPatientId = patientIdMap[entry.patientId] ?: continue
                        journalDao.insertEntry(entry.copy(id = 0, patientId = newPatientId))
                        totalDocs++
                    }
                }

                Log.d(TAG, "Full restore: $totalDocs documents restored (atomic transaction)")
                Result.success(totalDocs)
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed — transaction rolled back", e)
                Result.failure(e)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INCREMENTAL BACKUP — single entity sync after write
    // ══════════════════════════════════════════════════════════════

    suspend fun backupPatient(patient: PatientEntity) = backupDoc("patients", patient.id.toString(), patientToMap(patient))
    suspend fun backupGlucose(reading: LectureGlucoseEntity) = backupDoc("glucose", reading.id.toString(), glucoseToMap(reading))
    suspend fun backupHbA1c(hba1c: HbA1cEntity) = backupDoc("hba1c", hba1c.id.toString(), hba1cToMap(hba1c))
    suspend fun backupMedicament(med: MedicamentEntity) = backupDoc("medicaments", med.id.toString(), medicamentToMap(med))
    suspend fun backupRendezVous(rdv: RendezVousEntity) = backupDoc("rendezvous", rdv.id.toString(), rendezVousToMap(rdv))
    suspend fun backupJournal(entry: JournalEntity) = backupDoc("journal", entry.id.toString(), journalToMap(entry))

    suspend fun deleteBackupDoc(collection: String, docId: String) {
        val userId = uid ?: return
        try {
            db.collection(BACKUPS).document(userId)
                .collection(collection).document(docId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Delete backup doc failed", e)
        }
    }

    /**
     * Backup a single document with timestamp-based conflict resolution.
     * Only overwrites cloud data if local is newer.
     */
    private suspend fun backupDoc(collection: String, docId: String, data: Map<String, Any?>) {
        val userId = uid ?: return
        try {
            val docRef = db.collection(BACKUPS).document(userId)
                .collection(collection).document(docId)
            val localModified = (data["lastModified"] as? Long) ?: 0L

            // Check if cloud version is newer
            val cloudDoc = docRef.get().await()
            if (cloudDoc.exists()) {
                val cloudModified = cloudDoc.getLong("lastModified") ?: 0L
                if (cloudModified > localModified) {
                    Log.d(TAG, "Skip $collection/$docId — cloud is newer ($cloudModified > $localModified)")
                    return
                }
            }

            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Backup $collection/$docId failed — queuing for retry", e)
            try {
                val jsonPayload = org.json.JSONObject(data.filterValues { it != null }).toString()
                pendingOperationDao.insert(
                    PendingOperationEntity(
                        operationType = "BACKUP_DOC",
                        collection = "$BACKUPS/${uid}/$collection",
                        documentId = docId,
                        payload = jsonPayload
                    )
                )
            } catch (qe: Exception) {
                Log.e(TAG, "Failed to queue pending operation", qe)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CHECK IF RESTORE NEEDED
    // ══════════════════════════════════════════════════════════════

    suspend fun hasCloudBackup(): Boolean {
        val userId = uid ?: return false
        return try {
            val meta = db.collection(BACKUPS).document(userId)
                .collection("metadata").document("info").get().await()
            meta.exists() && (meta.getLong("patientCount") ?: 0) > 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isLocalDbEmpty(): Boolean = withContext(Dispatchers.IO) {
        patientDao.getPatientCount(uid ?: "__aucun__") == 0
    }

    suspend fun getBackupInfo(): Map<String, Any>? {
        val userId = uid ?: return null
        return try {
            val meta = db.collection(BACKUPS).document(userId)
                .collection("metadata").document("info").get().await()
            meta.data
        } catch (e: Exception) {
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ENTITY ↔ MAP CONVERTERS
    // ══════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════

    private fun patientToMap(p: PatientEntity) = mapOf(
        "id" to p.id,
        "nom" to p.nom,
        "prenom" to p.prenom,
        "dateNaissance" to p.dateNaissance.toString(),
        "sexe" to p.sexe.name,
        "telephone" to p.telephone,
        "email" to p.email,
        "adresse" to p.adresse,
        "typeDiabete" to p.typeDiabete.name,
        "dateDiagnostic" to p.dateDiagnostic?.toString(),
        "poids" to p.poids,
        "taille" to p.taille,
        "tourDeTaille" to p.tourDeTaille,
        "masseGrasse" to p.masseGrasse,
        "notes" to p.notes,
        "createdAt" to p.createdAt.toString(),
        "updatedAt" to p.updatedAt.toString(),
        "lastModified" to p.lastModified
    )

    private fun mapToPatient(m: Map<String, Any?>): PatientEntity = PatientEntity(
        id = (m["id"] as? Number)?.toLong() ?: 0,
        nom = m["nom"] as? String ?: "",
        prenom = m["prenom"] as? String ?: "",
        dateNaissance = (m["dateNaissance"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
        sexe = (m["sexe"] as? String)?.let { runCatching { Sexe.valueOf(it) }.getOrNull() } ?: Sexe.HOMME,
        telephone = m["telephone"] as? String ?: "",
        email = m["email"] as? String ?: "",
        adresse = m["adresse"] as? String ?: "",
        typeDiabete = (m["typeDiabete"] as? String)?.let { runCatching { TypeDiabete.valueOf(it) }.getOrNull() } ?: TypeDiabete.TYPE_2,
        dateDiagnostic = (m["dateDiagnostic"] as? String)?.let { LocalDate.parse(it) },
        poids = (m["poids"] as? Number)?.toDouble(),
        taille = (m["taille"] as? Number)?.toDouble(),
        tourDeTaille = (m["tourDeTaille"] as? Number)?.toDouble(),
        masseGrasse = (m["masseGrasse"] as? Number)?.toDouble(),
        notes = m["notes"] as? String ?: "",
        createdAt = (m["createdAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
        updatedAt = (m["updatedAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
    )

    private fun glucoseToMap(r: LectureGlucoseEntity) = mapOf(
        "id" to r.id,
        "patientId" to r.patientId,
        "valeur" to r.valeur,
        "dateHeure" to r.dateHeure.toString(),
        "contexte" to r.contexte.name,
        "notes" to r.notes,
        "createdAt" to r.createdAt.toString(),
        "lastModified" to r.lastModified
    )

    private fun mapToGlucose(m: Map<String, Any?>): LectureGlucoseEntity = LectureGlucoseEntity(
        id = (m["id"] as? Number)?.toLong() ?: 0,
        patientId = (m["patientId"] as? Number)?.toLong() ?: 0,
        valeur = (m["valeur"] as? Number)?.toDouble() ?: 0.0,
        dateHeure = (m["dateHeure"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
        contexte = (m["contexte"] as? String)?.let { runCatching { ContexteGlucose.valueOf(it) }.getOrNull() }
            ?: ContexteGlucose.AUTRE,
        notes = m["notes"] as? String ?: "",
        createdAt = (m["createdAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
    )

    private fun hba1cToMap(h: HbA1cEntity) = mapOf(
        "id" to h.id,
        "patientId" to h.patientId,
        "valeur" to h.valeur,
        "dateMesure" to h.dateMesure.toString(),
        "laboratoire" to h.laboratoire,
        "notes" to h.notes,
        "estEstimation" to h.estEstimation,
        "createdAt" to h.createdAt.toString(),
        "lastModified" to h.lastModified
    )

    private fun mapToHbA1c(m: Map<String, Any?>): HbA1cEntity = HbA1cEntity(
        id = (m["id"] as? Number)?.toLong() ?: 0,
        patientId = (m["patientId"] as? Number)?.toLong() ?: 0,
        valeur = (m["valeur"] as? Number)?.toDouble() ?: 0.0,
        dateMesure = (m["dateMesure"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
        laboratoire = m["laboratoire"] as? String ?: "",
        notes = m["notes"] as? String ?: "",
        estEstimation = m["estEstimation"] as? Boolean ?: false,
        createdAt = (m["createdAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
    )

    private fun medicamentToMap(med: MedicamentEntity) = mapOf(
        "id" to med.id,
        "patientId" to med.patientId,
        "nom" to med.nom,
        "dosage" to med.dosage,
        "frequence" to med.frequence.name,
        "heurePrise" to med.heurePrise.toString(),
        "dateDebut" to med.dateDebut.toString(),
        "dateFin" to med.dateFin?.toString(),
        "estActif" to med.estActif,
        "rappelActive" to med.rappelActive,
        "notes" to med.notes,
        "createdAt" to med.createdAt.toString(),
        "lastModified" to med.lastModified
    )

    private fun mapToMedicament(m: Map<String, Any?>): MedicamentEntity = MedicamentEntity(
        id = (m["id"] as? Number)?.toLong() ?: 0,
        patientId = (m["patientId"] as? Number)?.toLong() ?: 0,
        nom = m["nom"] as? String ?: "",
        dosage = m["dosage"] as? String ?: "",
        frequence = (m["frequence"] as? String)?.let { runCatching { FrequencePrise.valueOf(it) }.getOrNull() }
            ?: FrequencePrise.QUOTIDIEN,
        heurePrise = (m["heurePrise"] as? String)?.let { LocalTime.parse(it) } ?: LocalTime.of(8, 0),
        dateDebut = (m["dateDebut"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
        dateFin = (m["dateFin"] as? String)?.let { LocalDate.parse(it) },
        estActif = m["estActif"] as? Boolean ?: true,
        rappelActive = m["rappelActive"] as? Boolean ?: true,
        notes = m["notes"] as? String ?: "",
        createdAt = (m["createdAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
    )

    private fun rendezVousToMap(r: RendezVousEntity) = mapOf(
        "id" to r.id,
        "patientId" to r.patientId,
        "titre" to r.titre,
        "dateHeure" to r.dateHeure.toString(),
        "dureeMinutes" to r.dureeMinutes,
        "type" to r.type.name,
        "lieu" to r.lieu,
        "notes" to r.notes,
        "estConfirme" to r.estConfirme,
        "rappelEnvoye" to r.rappelEnvoye,
        "createdAt" to r.createdAt.toString(),
        "lastModified" to r.lastModified
    )

    private fun mapToRendezVous(m: Map<String, Any?>): RendezVousEntity = RendezVousEntity(
        id = (m["id"] as? Number)?.toLong() ?: 0,
        patientId = (m["patientId"] as? Number)?.toLong() ?: 0,
        titre = m["titre"] as? String ?: "",
        dateHeure = (m["dateHeure"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
        dureeMinutes = (m["dureeMinutes"] as? Number)?.toInt() ?: 30,
        type = (m["type"] as? String)?.let { runCatching { TypeRendezVous.valueOf(it) }.getOrNull() }
            ?: TypeRendezVous.CONSULTATION,
        lieu = m["lieu"] as? String ?: "",
        notes = m["notes"] as? String ?: "",
        estConfirme = m["estConfirme"] as? Boolean ?: false,
        rappelEnvoye = m["rappelEnvoye"] as? Boolean ?: false,
        createdAt = (m["createdAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
    )

    private fun journalToMap(j: JournalEntity) = mapOf(
        "id" to j.id,
        "patientId" to j.patientId,
        "date" to j.date.toString(),
        "humeur" to j.humeur.name,
        "niveauStress" to j.niveauStress.name,
        "qualiteSommeil" to j.qualiteSommeil.name,
        "heuresSommeil" to j.heuresSommeil,
        "activitePhysique" to j.activitePhysique,
        "minutesActivite" to j.minutesActivite,
        "pas" to j.pas,
        "notes" to j.notes,
        "glycemieCorrelation" to j.glycemieCorrelation,
        "createdAt" to j.createdAt.toString(),
        "lastModified" to j.lastModified
    )

    private fun mapToJournal(m: Map<String, Any?>): JournalEntity = JournalEntity(
        id = (m["id"] as? Number)?.toLong() ?: 0,
        patientId = (m["patientId"] as? Number)?.toLong() ?: 0,
        date = (m["date"] as? String)?.let { LocalDate.parse(it) } ?: LocalDate.now(),
        humeur = (m["humeur"] as? String)?.let { runCatching { Humeur.valueOf(it) }.getOrNull() } ?: Humeur.NEUTRE,
        niveauStress = (m["niveauStress"] as? String)?.let { runCatching { NiveauStress.valueOf(it) }.getOrNull() } ?: NiveauStress.AUCUN,
        qualiteSommeil = (m["qualiteSommeil"] as? String)?.let { runCatching { QualiteSommeil.valueOf(it) }.getOrNull() } ?: QualiteSommeil.BONNE,
        heuresSommeil = (m["heuresSommeil"] as? Number)?.toDouble() ?: 7.0,
        activitePhysique = m["activitePhysique"] as? Boolean ?: false,
        minutesActivite = (m["minutesActivite"] as? Number)?.toInt() ?: 0,
        pas = (m["pas"] as? Number)?.toInt() ?: 0,
        notes = m["notes"] as? String ?: "",
        glycemieCorrelation = (m["glycemieCorrelation"] as? Number)?.toDouble(),
        createdAt = (m["createdAt"] as? String)?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
    )
}
