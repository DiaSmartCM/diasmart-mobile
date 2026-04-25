package com.diabeto.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.model.ReportRecord
import com.diabeto.data.model.UserProfile
import com.diabeto.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReportUiState(
    val isGenerating: Boolean = false,
    val isSending: Boolean = false,
    val lastFile: File? = null,
    val lastFileUrl: String = "",
    val error: String? = null,
    val info: String? = null,
    val period: String = ReportRepository.PERIOD_30D,
    val recipients: List<UserProfile> = emptyList(),
    val selectedRecipient: UserProfile? = null,
    val emailOverride: String = "",
    val sendByEmail: Boolean = true,
    val sendByMessagerie: Boolean = true,
    val ordonnance: String = "",
    val compteRendu: String = "",
    val recommandations: String = "",
    val history: List<ReportRecord> = emptyList()
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository
) : ViewModel() {

    companion object { private const val TAG = "ReportVM" }

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun setPeriod(period: String) = _uiState.update { it.copy(period = period) }
    fun setOrdonnance(v: String) = _uiState.update { it.copy(ordonnance = v) }
    fun setCompteRendu(v: String) = _uiState.update { it.copy(compteRendu = v) }
    fun setRecommandations(v: String) = _uiState.update { it.copy(recommandations = v) }
    fun setEmailOverride(v: String) = _uiState.update { it.copy(emailOverride = v) }
    fun setSendByEmail(v: Boolean) = _uiState.update { it.copy(sendByEmail = v) }
    fun setSendByMessagerie(v: Boolean) = _uiState.update { it.copy(sendByMessagerie = v) }
    fun selectRecipient(p: UserProfile?) = _uiState.update {
        it.copy(selectedRecipient = p, emailOverride = p?.email ?: it.emailOverride)
    }
    fun clearMessages() = _uiState.update { it.copy(error = null, info = null) }

    fun loadAsPatient() {
        viewModelScope.launch {
            val recipients = runCatching { reportRepository.getLinkedDoctors() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    recipients = recipients,
                    selectedRecipient = recipients.firstOrNull(),
                    emailOverride = recipients.firstOrNull()?.email ?: ""
                )
            }
            loadHistory()
        }
    }

    fun loadAsDoctor() {
        viewModelScope.launch {
            val recipients = runCatching { reportRepository.getLinkedPatients() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    recipients = recipients,
                    selectedRecipient = recipients.firstOrNull(),
                    emailOverride = recipients.firstOrNull()?.email ?: ""
                )
            }
            loadHistory()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            val history = reportRepository.listMyReports()
            _uiState.update { it.copy(history = history) }
        }
    }

    /** Genere + envoie un rapport patient (auto-export). */
    fun generateAndSendPatient() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, info = null) }
            val fileResult = reportRepository.buildPatientReport(state.period)
            val file = fileResult.getOrElse { e ->
                _uiState.update { it.copy(isGenerating = false, error = "Generation echouee: ${e.message}") }
                return@launch
            }
            _uiState.update { it.copy(isGenerating = false, lastFile = file, isSending = true) }
            sendReport(file, state, ReportRecord.TYPE_PATIENT, state.period)
        }
    }

    /** Genere + envoie un rapport medecin (ordonnance + compte-rendu). */
    fun generateAndSendDoctor() {
        val state = _uiState.value
        val recipient = state.selectedRecipient
        if (recipient == null) {
            _uiState.update { it.copy(error = "Selectionnez un patient destinataire.") }
            return
        }
        if (state.ordonnance.isBlank() && state.compteRendu.isBlank()) {
            _uiState.update { it.copy(error = "Saisissez au moins l'ordonnance ou le compte-rendu.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, info = null) }
            val fileResult = reportRepository.buildDoctorReport(
                patientUid = recipient.uid,
                ordonnance = state.ordonnance,
                compteRendu = state.compteRendu,
                recommandations = state.recommandations
            )
            val file = fileResult.getOrElse { e ->
                _uiState.update { it.copy(isGenerating = false, error = "Generation echouee: ${e.message}") }
                return@launch
            }
            _uiState.update { it.copy(isGenerating = false, lastFile = file, isSending = true) }
            sendReport(file, state, ReportRecord.TYPE_MEDECIN, "consultation")
        }
    }

    private suspend fun sendReport(file: File, state: ReportUiState, type: String, periodLabel: String) {
        val uploadResult = reportRepository.uploadReport(file)
        val (_, downloadUrl) = uploadResult.getOrElse { e ->
            _uiState.update { it.copy(isSending = false, error = "Upload echoue: ${e.message}") }
            return
        }
        _uiState.update { it.copy(lastFileUrl = downloadUrl) }

        val channels = mutableListOf<String>()
        val recipient = state.selectedRecipient
        val subject = if (type == ReportRecord.TYPE_PATIENT)
            "Rapport DiaSmart — donnees patient"
        else
            "Compte-rendu de consultation — DiaSmart"
        val bodyHtml = if (type == ReportRecord.TYPE_PATIENT)
            "<p>Bonjour,</p><p>Vous trouverez ci-joint le rapport de donnees DiaSmart de votre patient.</p>" +
                "<p><a href=\"$downloadUrl\">Telecharger le PDF</a></p>"
        else
            "<p>Bonjour,</p><p>Vous trouverez ci-joint votre compte-rendu de consultation et votre ordonnance.</p>" +
                "<p><a href=\"$downloadUrl\">Telecharger le PDF</a></p>"

        if (state.sendByMessagerie && recipient != null) {
            val msg = if (type == ReportRecord.TYPE_PATIENT)
                "Rapport DiaSmart pour la periode : $periodLabel"
            else
                "Compte-rendu et ordonnance"
            val r = reportRepository.sendByMessagerie(
                recipientUid = recipient.uid,
                downloadUrl = downloadUrl,
                fileName = file.name,
                contenu = msg
            )
            r.fold(
                onSuccess = { channels.add(ReportRecord.CHANNEL_MESSAGERIE) },
                onFailure = { Log.w(TAG, "messagerie failed: ${it.message}") }
            )
        }
        if (state.sendByEmail && state.emailOverride.isNotBlank()) {
            val r = reportRepository.sendByEmail(
                toEmail = state.emailOverride.trim(),
                subject = subject,
                bodyHtml = bodyHtml,
                downloadUrl = downloadUrl,
                fileName = file.name
            )
            r.fold(
                onSuccess = { channels.add(ReportRecord.CHANNEL_EMAIL) },
                onFailure = { Log.w(TAG, "email failed: ${it.message}") }
            )
        }

        // Historique
        val record = ReportRecord(
            ownerUid = "",
            ownerNom = "",
            recipientUid = recipient?.uid.orEmpty(),
            recipientNom = recipient?.nomComplet.orEmpty(),
            recipientEmail = state.emailOverride.trim(),
            type = type,
            periodLabel = periodLabel,
            title = if (type == ReportRecord.TYPE_PATIENT) "Rapport patient" else "Compte-rendu medecin",
            fileUrl = downloadUrl,
            fileName = file.name,
            sizeBytes = file.length(),
            channels = channels
        )
        runCatching { reportRepository.recordReport(record) }

        val info = when {
            channels.isEmpty() -> "Rapport genere mais aucun canal d'envoi reussi."
            channels.size == 2 -> "Rapport envoye par messagerie et email."
            channels.contains(ReportRecord.CHANNEL_EMAIL) -> "Rapport envoye par email."
            else -> "Rapport envoye par messagerie."
        }
        _uiState.update { it.copy(isSending = false, info = info) }
        loadHistory()
    }
}
