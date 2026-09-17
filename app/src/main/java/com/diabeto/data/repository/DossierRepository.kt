package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.model.DossierMedical
import com.diabeto.data.model.EntreeDossier
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dossier numerique patient, tenu par le medecin.
 *
 * Qui peut quoi est decide par les regles Firestore (`match /dossiers`) :
 * le medecin lie lit et ecrit ; le patient ne lit que les fiches marquees
 * visibles, et ne peut rien modifier. Ce repository ne fait que s'y conformer,
 * notamment en filtrant `visiblePatient` dans la requete du patient : sans ce
 * filtre, Firestore refuserait la requete entiere.
 */
@Singleton
class DossierRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "DossierRepository"
        private const val COL_DOSSIERS = "dossiers"
        private const val COL_ENTREES = "entrees"
        private val FORMAT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun aujourdhui(): String = LocalDate.now().format(FORMAT_DATE)
    }

    private fun dossierRef(patientUid: String, medecinUid: String) =
        firestore.collection(COL_DOSSIERS).document(DossierMedical.identifiant(patientUid, medecinUid))

    /**
     * Cree le dossier s'il n'existe pas encore, sinon le renvoie tel quel.
     *
     * Appele a chaque etablissement du lien (acceptation, reactivation) et a
     * chaque ouverture par le medecin : l'appel est idempotent, un dossier
     * existant n'est jamais ecrase.
     *
     * @return le dossier, et `true` s'il vient d'etre cree.
     */
    suspend fun assurerDossier(
        patientUid: String,
        medecinUid: String,
        patientNom: String,
        medecinNom: String
    ): Result<Pair<DossierMedical, Boolean>> = try {
        val ref = dossierRef(patientUid, medecinUid)
        val snap = ref.get().await()
        if (snap.exists()) {
            @Suppress("UNCHECKED_CAST")
            Result.success(DossierMedical.fromMap(snap.data as Map<String, Any?>) to false)
        } else {
            val dossier = DossierMedical(
                patientUid = patientUid,
                medecinUid = medecinUid,
                patientNom = patientNom,
                medecinNom = medecinNom
            )
            ref.set(dossier.toMap()).await()
            Log.d(TAG, "Dossier cree : ${dossier.id}")
            Result.success(dossier to true)
        }
    } catch (e: Exception) {
        Log.w(TAG, "assurerDossier a echoue : ${e.message}")
        Result.failure(e)
    }

    /** Fiches du dossier, mises a jour en direct, les plus recentes d'abord. */
    fun observerEntrees(
        patientUid: String,
        medecinUid: String,
        commePatient: Boolean
    ): Flow<List<EntreeDossier>> = callbackFlow {
        var requete: Query = dossierRef(patientUid, medecinUid).collection(COL_ENTREES)
        if (commePatient) requete = requete.whereEqualTo("visiblePatient", true)

        val ecoute = requete.addSnapshotListener { snap, erreur ->
            if (erreur != null) {
                Log.w(TAG, "Lecture des fiches refusee : ${erreur.message}")
                close(erreur)
                return@addSnapshotListener
            }
            val fiches = snap?.documents?.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { EntreeDossier.fromMap(doc.id, it as Map<String, Any?>) }
            }.orEmpty()
            trySend(trier(fiches))
        }
        awaitClose { ecoute.remove() }
    }

    private fun trier(fiches: List<EntreeDossier>): List<EntreeDossier> =
        fiches.sortedWith(
            compareByDescending<EntreeDossier> { lireDate(it.date) }
                .thenByDescending { it.createdAt.seconds }
        )

    private fun lireDate(date: String): LocalDate =
        runCatching { LocalDate.parse(date.trim(), FORMAT_DATE) }.getOrDefault(LocalDate.MIN)

    /** Cree la fiche si elle n'a pas d'id, la met a jour sinon. */
    suspend fun enregistrer(entree: EntreeDossier): Result<Unit> = try {
        val profil = authRepository.getCurrentUserProfileRapide()
        val moi = authRepository.currentUserId ?: throw IllegalStateException("Non connecté")
        val dossier = dossierRef(entree.patientUid, entree.medecinUid)
        val maintenant = Timestamp.now()
        if (entree.id.isBlank()) {
            val nouvelle = entree.copy(
                auteurUid = moi,
                auteurNom = profil?.nomComplet.orEmpty(),
                createdAt = maintenant,
                updatedAt = maintenant
            )
            dossier.collection(COL_ENTREES).add(nouvelle.toMap()).await()
        } else {
            dossier.collection(COL_ENTREES).document(entree.id).update(
                mapOf(
                    "date" to entree.date,
                    "champs" to entree.champs,
                    "visiblePatient" to entree.visiblePatient,
                    "updatedAt" to maintenant
                )
            ).await()
        }
        dossier.update("updatedAt", maintenant).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.w(TAG, "Enregistrement de la fiche refuse : ${e.message}")
        Result.failure(e)
    }

    suspend fun changerVisibilite(entree: EntreeDossier, visible: Boolean): Result<Unit> = try {
        dossierRef(entree.patientUid, entree.medecinUid)
            .collection(COL_ENTREES).document(entree.id)
            .update(mapOf("visiblePatient" to visible, "updatedAt" to Timestamp.now()))
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun supprimer(entree: EntreeDossier): Result<Unit> = try {
        dossierRef(entree.patientUid, entree.medecinUid)
            .collection(COL_ENTREES).document(entree.id)
            .delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Rendez-vous du patient avec ce medecin, pour l'onglet Suivi.
     * Le filtre sur `medecinUid` est indispensable : c'est lui qui permet a
     * Firestore d'autoriser la requete au nom du medecin.
     */
    suspend fun rendezVous(patientUid: String, medecinUid: String): List<Map<String, Any?>> = try {
        firestore.collection("rendezvous")
            .whereEqualTo("userId", patientUid)
            .whereEqualTo("medecinUid", medecinUid)
            .get().await()
            .documents.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it.data as? Map<String, Any?>
            }
            .sortedByDescending { it["dateHeure"]?.toString().orEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "Rendez-vous illisibles : ${e.message}")
        emptyList()
    }
}
