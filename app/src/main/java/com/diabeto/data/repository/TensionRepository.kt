package com.diabeto.data.repository

import com.diabeto.data.dao.TensionDao
import com.diabeto.data.entity.TensionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Mesures de tension : stockage local + copie dans le cloud (backups/{uid}/tension). */
@Singleton
class TensionRepository @Inject constructor(
    private val tensionDao: TensionDao,
    private val cloudBackup: CloudBackupRepository
) {
    fun getTensions(patientId: Long): Flow<List<TensionEntity>> = tensionDao.getTensionsByPatient(patientId)

    suspend fun derniere(patientId: Long): TensionEntity? =
        tensionDao.getTensionsByPatientList(patientId).firstOrNull()

    suspend fun ajouter(tension: TensionEntity): TensionEntity =
        tension.copy(id = tensionDao.insertTension(tension))

    /** Copie tout de suite dans le cloud pour le soignant ; sinon la synchro reguliere s'en charge. */
    suspend fun envoyer(tension: TensionEntity) {
        runCatching { cloudBackup.backupTension(tension) }
    }

    suspend fun supprimer(tension: TensionEntity) {
        tensionDao.deleteTension(tension)
        runCatching { cloudBackup.deleteBackupDoc("tension", tension.id.toString()) }
    }
}
