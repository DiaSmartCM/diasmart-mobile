package com.diabeto.desktop

import com.diabeto.data.model.Affiliation
import com.diabeto.data.model.Etablissement
import com.diabeto.data.model.PatientInscrit
import com.diabeto.domain.EvaluationSuivi
import com.diabeto.domain.MesureHbA1c
import com.diabeto.domain.ResultatSuivi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Un patient inscrit et son evaluation (memes regles que l'app mobile). */
data class LigneSuivi(val patient: PatientInscrit, val resultat: ResultatSuivi)

/**
 * Lecture de l'espace etablissement depuis le PC, avec les memes chemins
 * Firestore que l'app Android (EtablissementRepository).
 */
class DepotEtablissement(private val fb: FirebaseRest) {

    suspend fun monAffiliation(uid: String): Affiliation? =
        fb.document("affiliations/$uid")?.let(Affiliation::fromMap)

    suspend fun etablissement(id: String): Etablissement? =
        fb.document("etablissements/$id")?.let { Etablissement.fromMap(id, it) }

    suspend fun suivi(etablissementId: String): List<LigneSuivi> = coroutineScope {
        val patients = fb.collection("etablissements/$etablissementId/patients").map(PatientInscrit::fromMap)
        val fuseau = TimeZone.currentSystemDefault()
        val maintenant = Clock.System.now().toLocalDateTime(fuseau)
        val limite = Semaphore(4)
        patients.map { p ->
            async { limite.withPermit { LigneSuivi(p, evaluer(p, maintenant, fuseau)) } }
        }.awaitAll()
            .sortedWith(compareBy<LigneSuivi>({ it.resultat.priorite.ordinal }, { !it.resultat.perduDeVue }, { it.patient.nom }))
    }

    private suspend fun evaluer(p: PatientInscrit, maintenant: LocalDateTime, fuseau: TimeZone): ResultatSuivi {
        val mesures = runCatching {
            fb.derniers("backups/${p.uid}", "glucose", "dateHeure", 150).mapNotNull { m ->
                val date = (m["dateHeure"] as? String)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                val valeur = (m["valeur"] as? Number)?.toDouble()
                if (date != null && valeur != null) date to valeur else null
            }
        }.getOrDefault(emptyList())
        val hba1c = runCatching {
            fb.derniers("backups/${p.uid}", "hba1c", "dateMesure", 10).mapNotNull { m ->
                val date = (m["dateMesure"] as? String)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                val valeur = (m["valeur"] as? Number)?.toDouble()
                if (date != null && valeur != null) MesureHbA1c(date, valeur, m["estEstimation"] == true) else null
            }
        }.getOrDefault(emptyList())
        return EvaluationSuivi.evaluer(p.inscritAt, mesures, hba1c, maintenant, fuseau)
    }
}
