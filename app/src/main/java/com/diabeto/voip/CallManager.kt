package com.diabeto.voip

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-grade call orchestrator.
 * Bridges FirestoreSignaling ↔ WebRTCManager.
 *
 * Critical call flows:
 *
 * CALLER (outgoing):
 *   1. setupWebRTCCallbacks()
 *   2. createPeerConnection(isVideo)
 *   3. createOffer → initiateCall(offer) → Firestore doc created
 *   4. listenToIceCandidates() (caller's candidates are sent as they arrive)
 *   5. Wait for callee's answer via listenToCall()
 *   6. setRemoteDescription(answer) → drainPendingIceCandidates()
 *   7. ICE connects → CONNECTED
 *
 * CALLEE (incoming):
 *   1. Incoming call detected via listenForIncomingCalls() → RINGING
 *   2. User taps Accept:
 *      a. setupWebRTCCallbacks()
 *      b. createPeerConnection(isVideo)
 *      c. acceptCall() → starts listenToCall() + listenToIceCandidates()
 *      d. listenToCall fires with offer → setRemoteDescription(offer) → createAnswer → sendAnswer
 *      e. ICE connects → CONNECTED
 */
@Singleton
class CallManager @Inject constructor(
    private val context: Context,
    private val webRTCManager: WebRTCManager,
    private val doctorReviewRepository: com.diabeto.data.repository.DoctorReviewRepository
) {
    companion object {
        private const val TAG = "CallManager"
        private const val MAX_ICE_RESTART_ATTEMPTS = 3
        private const val ICE_RESTART_DELAY_MS = 2000L
        private const val DISCONNECTED_TIMEOUT_MS = 8000L
        private const val CALL_TIMEOUT_MS = 45000L
    }

    enum class CallState {
        IDLE, CALLING, RINGING, CONNECTING, CONNECTED, RECONNECTING, ENDED
    }

    data class CallInfo(
        val callId: String = "",
        val remoteUid: String = "",
        val remoteNom: String = "",
        val isVideo: Boolean = true,
        val isOutgoing: Boolean = true,
        val state: CallState = CallState.IDLE,
        val isMuted: Boolean = false,
        val isCameraOff: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val durationSeconds: Int = 0
    )

    private val _callState = MutableStateFlow(CallInfo())
    val callState: StateFlow<CallInfo> = _callState.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val signaling = FirestoreSignaling()
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + scopeJob)

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var pendingIceCandidates = mutableListOf<IceCandidate>()
    @Volatile private var remoteDescriptionSet = false
    @Volatile private var everConnected = false
    private var timeoutJob: Job? = null
    private var durationJob: Job? = null
    private var disconnectedJob: Job? = null
    private var iceRestartCount = 0

    // ═══════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    fun initialize(authToken: String) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        webRTCManager.initialize()
        setupSignalingCallbacks()
        signaling.listenForIncomingCalls()
        Log.d(TAG, "CallManager initialized, uid=${signaling.currentUid}")
    }

    // ═══════════════════════════════════════════════════════════
    // SIGNALING CALLBACKS
    // ═══════════════════════════════════════════════════════════

    private fun setupSignalingCallbacks() {

        // ── Incoming call (callee side) ──
        signaling.onCallIncoming = { callDoc ->
            Log.d(TAG, "Incoming call from ${callDoc.callerNom} (${callDoc.callId})")
            _callState.value = CallInfo(
                callId = callDoc.callId,
                remoteUid = callDoc.callerUid,
                remoteNom = callDoc.callerNom,
                isVideo = callDoc.type == "video",
                isOutgoing = false,
                state = CallState.RINGING
            )
            startRingtone()
            showIncomingCallScreen(callDoc)
        }

        // ── Callee accepted (caller sees this) ──
        signaling.onCallAccepted = { callId ->
            Log.d(TAG, "Call accepted by callee: $callId")
            if (_callState.value.isOutgoing && _callState.value.state == CallState.CALLING) {
                _callState.value = _callState.value.copy(state = CallState.CONNECTING)
            }
        }

        // ── Call ended ──
        signaling.onCallEnded = { callId, reason ->
            Log.d(TAG, "Call ended: $callId reason=$reason")
            cleanupCallResources()
        }

        // ── Remote SDP offer (callee side) ──
        // This fires when the callee's listener sees the offer in the call document.
        // At this point, PeerConnection is already created (in acceptCall).
        signaling.onRemoteOffer = { callId, sdp ->
            Log.d(TAG, "Remote offer received for $callId (${sdp.length} chars)")
            val sessionDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
            webRTCManager.setRemoteDescription(sessionDesc) {
                Log.d(TAG, "Remote offer set successfully, creating answer...")
                remoteDescriptionSet = true
                drainPendingIceCandidates()

                webRTCManager.createAnswer { answer ->
                    Log.d(TAG, "Answer created, sending via Firestore")
                    scope.launch {
                        signaling.sendAnswer(callId, answer.description)
                    }
                }
            }
        }

        // ── Remote SDP answer (caller side) ──
        signaling.onRemoteAnswer = { callId, sdp ->
            Log.d(TAG, "Remote answer received for $callId (${sdp.length} chars)")
            val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            webRTCManager.setRemoteDescription(sessionDesc) {
                Log.d(TAG, "Remote answer set successfully")
                remoteDescriptionSet = true
                drainPendingIceCandidates()
            }
        }

        // ── Remote ICE candidate ──
        signaling.onRemoteIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
            if (remoteDescriptionSet) {
                val added = webRTCManager.addIceCandidate(iceCandidate)
                Log.d(TAG, "ICE candidate added: $added (sdpMid=$sdpMid)")
            } else {
                Log.d(TAG, "Buffering ICE candidate (remote desc not set yet) sdpMid=$sdpMid")
                pendingIceCandidates.add(iceCandidate)
            }
        }

        // ── ICE restart offer/answer from remote ──
        signaling.onIceRestartOffer = { callId, sdp ->
            Log.d(TAG, "ICE restart offer received")
            val sessionDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
            webRTCManager.setRemoteDescription(sessionDesc) {
                webRTCManager.createAnswer { answer ->
                    scope.launch {
                        signaling.sendIceRestartAnswer(callId, answer.description)
                    }
                }
            }
        }

        signaling.onIceRestartAnswer = { callId, sdp ->
            Log.d(TAG, "ICE restart answer received")
            val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            webRTCManager.setRemoteDescription(sessionDesc) {
                Log.d(TAG, "ICE restart complete")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // WEBRTC CALLBACKS
    // ═══════════════════════════════════════════════════════════

    private fun setupWebRTCCallbacks() {

        webRTCManager.onIceCandidate = { candidate ->
            val callId = _callState.value.callId
            if (callId.isNotEmpty()) {
                scope.launch {
                    signaling.sendIceCandidate(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                }
            } else {
                Log.w(TAG, "ICE candidate generated but no callId yet — dropping")
            }
        }

        webRTCManager.onRemoteVideoTrack = { track ->
            Log.d(TAG, "Remote video track received")
            _remoteVideoTrack.value = track
        }

        webRTCManager.onRemoteAudioTrack = { track ->
            Log.d(TAG, "Remote audio track received, enabled=${track.enabled()}")
            track.setEnabled(true)
        }

        webRTCManager.onLocalVideoTrack = { track ->
            _localVideoTrack.value = track
        }

        webRTCManager.onConnectionStateChange = { state ->
            Log.d(TAG, "ICE: $state (callState=${_callState.value.state})")
            handleIceConnectionState(state)
        }

        webRTCManager.onPeerConnectionStateChange = { state ->
            Log.d(TAG, "PeerConnection: $state")
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> onCallConnected()
                PeerConnection.PeerConnectionState.FAILED -> handleConnectionFailed()
                else -> {}
            }
        }

        webRTCManager.onNetworkChanged = {
            Log.d(TAG, "Network changed during call")
            if (_callState.value.state == CallState.CONNECTED ||
                _callState.value.state == CallState.RECONNECTING) {
                performIceRestart()
            }
        }

        webRTCManager.onHeartbeatTimeout = {
            Log.e(TAG, "Heartbeat timeout — remote peer unreachable")
            if (_callState.value.state == CallState.CONNECTED ||
                _callState.value.state == CallState.RECONNECTING) {
                if (iceRestartCount < MAX_ICE_RESTART_ATTEMPTS) {
                    performIceRestart()
                } else {
                    endCall()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ICE STATE MACHINE
    // ═══════════════════════════════════════════════════════════

    private fun handleIceConnectionState(state: PeerConnection.IceConnectionState) {
        when (state) {
            PeerConnection.IceConnectionState.CHECKING -> {
                Log.d(TAG, "ICE checking candidates...")
            }

            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                onCallConnected()
            }

            PeerConnection.IceConnectionState.DISCONNECTED -> {
                Log.w(TAG, "ICE DISCONNECTED — starting recovery timer")
                if (_callState.value.state == CallState.CONNECTED) {
                    _callState.value = _callState.value.copy(state = CallState.RECONNECTING)
                }
                disconnectedJob?.cancel()
                disconnectedJob = scope.launch {
                    delay(DISCONNECTED_TIMEOUT_MS)
                    if (_callState.value.state == CallState.RECONNECTING) {
                        Log.w(TAG, "Disconnected timeout — attempting ICE restart")
                        performIceRestart()
                    }
                }
            }

            PeerConnection.IceConnectionState.FAILED -> {
                handleConnectionFailed()
            }

            PeerConnection.IceConnectionState.CLOSED -> {
                Log.d(TAG, "ICE connection closed")
            }

            else -> {}
        }
    }

    private fun onCallConnected() {
        if (_callState.value.state == CallState.CONNECTED) return

        Log.d(TAG, "CALL CONNECTED!")
        disconnectedJob?.cancel()
        iceRestartCount = 0
        everConnected = true
        _callState.value = _callState.value.copy(state = CallState.CONNECTED)
        stopRingtone()
        requestAudioFocus()
        timeoutJob?.cancel()
        startDurationTimer()
        CallService.start(context, _callState.value.remoteNom, _callState.value.isVideo)
    }

    private fun handleConnectionFailed() {
        Log.e(TAG, "Connection FAILED (restart attempt ${iceRestartCount + 1}/$MAX_ICE_RESTART_ATTEMPTS)")
        if (iceRestartCount < MAX_ICE_RESTART_ATTEMPTS) {
            performIceRestart()
        } else {
            Log.e(TAG, "All ICE restart attempts exhausted — ending call")
            endCall()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ICE RESTART
    // ═══════════════════════════════════════════════════════════

    private fun performIceRestart() {
        iceRestartCount++
        _callState.value = _callState.value.copy(state = CallState.RECONNECTING)
        Log.d(TAG, "ICE restart #$iceRestartCount/$MAX_ICE_RESTART_ATTEMPTS")

        scope.launch {
            delay(ICE_RESTART_DELAY_MS)

            val callId = _callState.value.callId
            if (callId.isEmpty()) return@launch

            webRTCManager.restartIce { sdp ->
                scope.launch {
                    signaling.sendIceRestartOffer(callId, sdp.description)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CALL DURATION TIMER
    // ═══════════════════════════════════════════════════════════

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                _callState.value = _callState.value.copy(durationSeconds = seconds)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════

    /**
     * Start an outgoing call.
     * Flow: setupCallbacks → createPeerConnection → createOffer → Firestore doc → wait for answer
     */
    fun startCall(calleeUid: String, callerNom: String, calleeNom: String, isVideo: Boolean) {
        Log.d(TAG, "Starting ${if (isVideo) "VIDEO" else "AUDIO"} call to $calleeNom ($calleeUid)")

        _callState.value = CallInfo(
            remoteUid = calleeUid,
            remoteNom = calleeNom,
            isVideo = isVideo,
            isOutgoing = true,
            state = CallState.CALLING
        )

        remoteDescriptionSet = false
        pendingIceCandidates.clear()
        iceRestartCount = 0

        // 1. Setup WebRTC callbacks FIRST
        setupWebRTCCallbacks()

        // 2. Create PeerConnection (adds local audio + video tracks)
        webRTCManager.createPeerConnection(isVideo)

        // 3. Create offer → initiateCall with offer in Firestore
        webRTCManager.createOffer { sdp ->
            Log.d(TAG, "Offer created, initiating Firestore call")
            scope.launch {
                try {
                    val callId = signaling.initiateCall(
                        calleeUid = calleeUid,
                        callerNom = callerNom,
                        calleeNom = calleeNom,
                        type = if (isVideo) "video" else "audio",
                        offer = sdp.description
                    )
                    _callState.value = _callState.value.copy(callId = callId)
                    Log.d(TAG, "Call created: $callId — waiting for answer...")

                    // Note: ICE candidates listener already started in initiateCall()

                    // Timeout for unanswered calls
                    timeoutJob = scope.launch {
                        delay(CALL_TIMEOUT_MS)
                        if (_callState.value.state == CallState.CALLING) {
                            Log.w(TAG, "Call timeout — no answer after ${CALL_TIMEOUT_MS / 1000}s")
                            endCall()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initiate call", e)
                    cleanupCallResources()
                }
            }
        }

        // Set audio mode for outgoing ring
        requestAudioFocus()
    }

    /**
     * Accept an incoming call (callee side).
     * Flow: setupCallbacks → createPeerConnection → acceptCall → offer arrives → answer created
     *
     * CRITICAL: We setup WebRTC and create PeerConnection BEFORE calling signaling.acceptCall().
     * This ensures the PeerConnection is ready when the offer is processed.
     */
    fun acceptCall() {
        val call = _callState.value
        Log.d(TAG, "Accepting call ${call.callId}")

        stopRingtone()
        remoteDescriptionSet = false
        pendingIceCandidates.clear()
        iceRestartCount = 0
        _callState.value = call.copy(state = CallState.CONNECTING)

        // 1. Setup WebRTC callbacks FIRST
        setupWebRTCCallbacks()

        // 2. Create PeerConnection (adds local audio + video tracks)
        webRTCManager.createPeerConnection(call.isVideo)

        // 3. Accept call — this starts Firestore listeners which will deliver the offer
        // The offer callback (onRemoteOffer) will fire, set remote desc, and create answer
        scope.launch {
            try {
                signaling.acceptCall(call.callId)
                Log.d(TAG, "Call acceptance signaled — waiting for offer delivery...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept call", e)
                cleanupCallResources()
            }
        }

        // Request audio focus
        requestAudioFocus()
    }

    fun rejectCall() {
        val call = _callState.value
        Log.d(TAG, "Rejecting call ${call.callId}")
        stopRingtone()
        scope.launch { signaling.rejectCall(call.callId) }
        _callState.value = CallInfo()
    }

    fun endCall() {
        val call = _callState.value
        Log.d(TAG, "Ending call ${call.callId}")
        val wasConnected = everConnected
        everConnected = false
        if (call.callId.isNotEmpty()) {
            scope.launch { signaling.endCall(call.callId) }
            // Si la teleconsultation a effectivement abouti, increment du compteur
            // cote medecin. Best-effort, fire-and-forget.
            if (wasConnected) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { doctorReviewRepository.incrementConsultationForCall(call.callId) }
                }
            }
        }
        cleanupCallResources()
    }

    // ═══════════════════════════════════════════════════════════
    // CONTROLS
    // ═══════════════════════════════════════════════════════════

    fun toggleMute() {
        val newMuted = !_callState.value.isMuted
        webRTCManager.toggleMicrophone(!newMuted)
        _callState.value = _callState.value.copy(isMuted = newMuted)
    }

    fun toggleCamera() {
        val newOff = !_callState.value.isCameraOff
        webRTCManager.toggleCamera(!newOff)
        _callState.value = _callState.value.copy(isCameraOff = newOff)
    }

    fun switchCamera() {
        webRTCManager.switchCamera()
    }

    fun toggleSpeaker() {
        val newSpeaker = !_callState.value.isSpeakerOn
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = newSpeaker
        _callState.value = _callState.value.copy(isSpeakerOn = newSpeaker)
    }

    fun getEglContext() = webRTCManager.getEglContext()

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun drainPendingIceCandidates() {
        if (pendingIceCandidates.isNotEmpty()) {
            Log.d(TAG, "Draining ${pendingIceCandidates.size} buffered ICE candidates")
            pendingIceCandidates.forEach {
                val added = webRTCManager.addIceCandidate(it)
                Log.d(TAG, "  Buffered ICE added: $added (sdpMid=${it.sdpMid})")
            }
            pendingIceCandidates.clear()
        }
    }

    private fun cleanupCallResources() {
        Log.d(TAG, "Cleaning up call resources")
        timeoutJob?.cancel()
        timeoutJob = null
        durationJob?.cancel()
        durationJob = null
        disconnectedJob?.cancel()
        disconnectedJob = null
        iceRestartCount = 0
        stopRingtone()
        releaseAudioFocus()
        webRTCManager.dispose()
        signaling.cleanup()
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _callState.value = CallInfo()
        remoteDescriptionSet = false
        everConnected = false
        pendingIceCandidates.clear()
        // Reset scope to isolate next call from any leaked coroutines
        scopeJob.cancel()
        scopeJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.Main + scopeJob)
        CallService.stop(context)
    }

    // ── Incoming Call Screen ──

    private fun showIncomingCallScreen(callDoc: FirestoreSignaling.CallDoc) {
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callDoc.callId)
            putExtra("callerNom", callDoc.callerNom)
            putExtra("callerUid", callDoc.callerUid)
            putExtra("callType", callDoc.type)
        }
        context.startActivity(intent)
    }

    // ── Audio Focus ──

    private fun requestAudioFocus() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        audioManager?.requestAudioFocus(audioFocusRequest!!)
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = _callState.value.isVideo
        Log.d(TAG, "Audio focus acquired, mode=IN_COMMUNICATION, speaker=${_callState.value.isVideo}")
    }

    private fun releaseAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioManager?.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = false
    }

    // ── Ringtone ──

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            val pattern = longArrayOf(0, 1000, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone error", e)
        }
    }

    private fun stopRingtone() {
        try {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        } catch (e: Exception) { /* ignore */ }
        ringtonePlayer = null
        vibrator?.cancel()
    }

    fun cleanup() {
        endCall()
        signaling.stopListening()
        webRTCManager.release()
        scopeJob.cancel()
    }
}
