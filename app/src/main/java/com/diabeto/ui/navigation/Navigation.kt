package com.diabeto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import com.diabeto.ui.screens.*
import com.diabeto.voip.CallManager
import com.diabeto.voip.CallScreen
import com.google.firebase.auth.FirebaseAuth

/**
 * Routes de navigation DiaSmart
 */
object Routes {
    const val SPLASH           = "splash"
    const val ONBOARDING       = "onboarding"
    const val LOGIN            = "login"
    const val DASHBOARD        = "dashboard"
    const val PATIENTS         = "patients"
    const val PATIENTS_SEARCH  = "patients/search"
    const val PATIENT_DETAIL   = "patient/{patientId}"
    const val PATIENT_EDIT     = "patient/edit?patientId={patientId}"
    const val GLUCOSE_TRACKING = "glucose/{patientId}"
    const val MEDICAMENTS      = "medicaments/{patientId}"
    const val RENDEZ_VOUS      = "rendezvous?patientId={patientId}"
    const val RENDEZ_VOUS_EDIT = "rendezvous/edit?rdvId={rdvId}&patientId={patientId}"
    const val CHATBOT          = "chatbot?patientId={patientId}"
    const val REPAS_ANALYSE    = "repas_analyse"
    const val MESSAGERIE       = "messagerie"
    const val CONVERSATION     = "messagerie/{conversationId}?interlocuteur={interlocuteur}"
    const val DATA_SHARING     = "data_sharing?tab={tab}"
    fun dataSharing(tab: Int = 0) = "data_sharing?tab=$tab"
    const val REPORTS          = "reports"
    const val SETTINGS         = "settings"
    const val PROFILE          = "profile"
    const val JOURNAL          = "journal?patientId={patientId}"
    const val PEDOMETER        = "pedometer?patientId={patientId}"
    const val PREDICTIVE       = "predictive?patientId={patientId}"
    const val VALIDATIONS      = "validations"
    const val COMMUNITY        = "community"
    const val VIDEO_CALL       = "videocall/{roomName}?interlocuteur={interlocuteur}&audioOnly={audioOnly}"
    const val VOIP_CALL        = "voip_call"
    const val SHARED_PATIENT   = "shared_patient/{patientUid}?patientNom={patientNom}"

    fun patientDetail(patientId: Long)   = "patient/$patientId"
    fun patientEdit(patientId: Long? = null) =
        if (patientId != null) "patient/edit?patientId=$patientId" else "patient/edit"
    fun glucoseTracking(patientId: Long) = "glucose/$patientId"
    fun medicaments(patientId: Long)     = "medicaments/$patientId"
    fun rendezVous(patientId: Long? = null) =
        if (patientId != null) "rendezvous?patientId=$patientId" else "rendezvous"
    fun rendezVousEdit(rdvId: Long? = null, patientId: Long? = null): String {
        val params = mutableListOf<String>()
        rdvId?.let { params.add("rdvId=$it") }
        patientId?.let { params.add("patientId=$it") }
        return if (params.isEmpty()) "rendezvous/edit"
               else "rendezvous/edit?${params.joinToString("&")}"
    }
    fun chatbot(patientId: Long? = null) =
        if (patientId != null) "chatbot?patientId=$patientId" else "chatbot"
    fun conversation(conversationId: String, interlocuteur: String) =
        "messagerie/$conversationId?interlocuteur=${java.net.URLEncoder.encode(interlocuteur, "UTF-8")}"
    fun journal(patientId: Long? = null) =
        if (patientId != null) "journal?patientId=$patientId" else "journal"
    fun pedometer(patientId: Long? = null) =
        if (patientId != null) "pedometer?patientId=$patientId" else "pedometer"
    fun predictive(patientId: Long? = null) =
        if (patientId != null) "predictive?patientId=$patientId" else "predictive"
    fun videoCall(roomName: String, interlocuteur: String, audioOnly: Boolean) =
        "videocall/$roomName?interlocuteur=${java.net.URLEncoder.encode(interlocuteur, "UTF-8")}&audioOnly=$audioOnly"
    fun sharedPatient(patientUid: String, patientNom: String) =
        "shared_patient/$patientUid?patientNom=${java.net.URLEncoder.encode(patientNom, "UTF-8")}"
}

/**
 * Navigation principale de l'application DiaSmart
 */
@Composable
fun DiabetoNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.SPLASH,
    callManager: CallManager? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        // ── Splash Screen anime ─────────────────────────────────────────────
        composable(Routes.SPLASH) {
            // isLoggedIn = Auth user present ET email verifie (sinon on force le passage par
            // la page de connexion pour saisir le code OTP)
            val fbUser = FirebaseAuth.getInstance().currentUser
            val hasEmail = !fbUser?.email.isNullOrBlank()
            val isLoggedIn = fbUser != null && (!hasEmail || fbUser.isEmailVerified)
            SplashScreen(
                isUserLoggedIn = isLoggedIn,
                onSplashFinished = { loggedIn ->
                    if (loggedIn) {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ── Onboarding ─────────────────────────────────────────────────────
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ── Authentification ─────────────────────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // ── Tableau de bord ──────────────────────────────────────────────────
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToPatients       = { navController.navigate(Routes.PATIENTS) },
                onNavigateToPatientDetail  = { id -> navController.navigate(Routes.patientDetail(id)) },
                onNavigateToRendezVous     = { navController.navigate(Routes.rendezVous()) },
                onNavigateToAddPatient     = { navController.navigate(Routes.PATIENTS_SEARCH) },
                onNavigateToChatbot        = { navController.navigate(Routes.chatbot()) },
                onNavigateToMessagerie     = { navController.navigate(Routes.MESSAGERIE) },
                onNavigateToRepasAnalyse   = { navController.navigate(Routes.REPAS_ANALYSE) },
                onNavigateToDataSharing    = { navController.navigate(Routes.dataSharing(0)) },
                onNavigateToMonMedecin     = { navController.navigate(Routes.dataSharing(1)) },
                onNavigateToSettings       = { navController.navigate(Routes.SETTINGS) },
                onNavigateToProfile        = { navController.navigate(Routes.PROFILE) },
                onNavigateToJournal        = { navController.navigate(Routes.journal()) },
                onNavigateToPedometer      = { navController.navigate(Routes.pedometer()) },
                onNavigateToPredictive     = { navController.navigate(Routes.predictive()) },
                onNavigateToValidations    = { navController.navigate(Routes.VALIDATIONS) },
                onNavigateToCommunity      = { navController.navigate(Routes.COMMUNITY) },
                onNavigateToReports        = { navController.navigate(Routes.REPORTS) }
            )
        }

        // ── Liste des patients (Mes patients cote medecin / Liste Room cote patient) ─
        composable(Routes.PATIENTS) {
            PatientsListScreen(
                onNavigateBack            = { navController.popBackStack() },
                onNavigateToPatientDetail = { id -> navController.navigate(Routes.patientDetail(id)) },
                onNavigateToAddPatient    = { navController.navigate(Routes.PATIENTS_SEARCH) },
                onNavigateToSharedPatientData = { uid, nom ->
                    navController.navigate(Routes.sharedPatient(uid, nom))
                },
                medecinMode = MedecinPatientsMode.MY_PATIENTS
            )
        }

        // ── Recherche de patients sur la plateforme (cote medecin) ───────────────
        composable(Routes.PATIENTS_SEARCH) {
            PatientsListScreen(
                onNavigateBack            = { navController.popBackStack() },
                onNavigateToPatientDetail = { id -> navController.navigate(Routes.patientDetail(id)) },
                onNavigateToAddPatient    = { navController.navigate(Routes.PATIENTS_SEARCH) },
                onNavigateToSharedPatientData = { uid, nom ->
                    navController.navigate(Routes.sharedPatient(uid, nom))
                },
                medecinMode = MedecinPatientsMode.PLATFORM_SEARCH
            )
        }

        // ── Detail d'un patient ───────────────────────────────────────────────
        composable(
            route     = Routes.PATIENT_DETAIL,
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) { back ->
            val patientId = back.arguments?.getLong("patientId") ?: 0L
            PatientDetailScreen(
                patientId              = patientId,
                onNavigateBack         = { navController.popBackStack() },
                onNavigateToEdit       = { navController.navigate(Routes.patientEdit(patientId)) },
                onNavigateToGlucose    = { navController.navigate(Routes.glucoseTracking(patientId)) },
                onNavigateToMedicaments = { navController.navigate(Routes.medicaments(patientId)) },
                onNavigateToRendezVous = { navController.navigate(Routes.rendezVous(patientId)) }
            )
        }

        // ── Ajout/Edition patient ─────────────────────────────────────────────
        composable(
            route     = Routes.PATIENT_EDIT,
            arguments = listOf(
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            PatientEditScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess  = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                    navController.popBackStack()
                }
            )
        }

        // ── Glycemie ──────────────────────────────────────────────────────────
        composable(
            route     = Routes.GLUCOSE_TRACKING,
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) { back ->
            val patientId = back.arguments?.getLong("patientId") ?: 0L
            GlucoseTrackingScreen(
                patientId      = patientId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Medicaments ───────────────────────────────────────────────────────
        composable(
            route     = Routes.MEDICAMENTS,
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) { back ->
            val patientId = back.arguments?.getLong("patientId") ?: 0L
            MedicamentsScreen(
                patientId      = patientId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Rendez-vous ───────────────────────────────────────────────────────
        composable(
            route     = Routes.RENDEZ_VOUS,
            arguments = listOf(
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            val patientId = back.arguments?.getLong("patientId")?.takeIf { it > 0 }
            RendezVousScreen(
                patientId      = patientId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdd = { pid -> navController.navigate(Routes.rendezVousEdit(patientId = pid)) }
            )
        }

        composable(
            route     = Routes.RENDEZ_VOUS_EDIT,
            arguments = listOf(
                navArgument("rdvId")     { type = NavType.LongType; defaultValue = -1L },
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            val rdvId     = back.arguments?.getLong("rdvId")?.takeIf { it > 0 }
            val patientId = back.arguments?.getLong("patientId")?.takeIf { it > 0 }
            RendezVousEditScreen(
                rdvId          = rdvId,
                patientId      = patientId,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess  = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refresh", true)
                    navController.popBackStack()
                }
            )
        }

        // ── Chatbot IA ────────────────────────────────────────────────────────
        composable(
            route     = Routes.CHATBOT,
            arguments = listOf(
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            val patientId = back.arguments?.getLong("patientId")?.takeIf { it > 0 }
            ChatbotScreen(
                patientId      = patientId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Analyse de repas ─────────────────────────────────────────────────
        composable(Routes.REPAS_ANALYSE) {
            RepasAnalyseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Partage de donnees ──────────────────────────────────────────────
        composable(
            route = Routes.DATA_SHARING,
            arguments = listOf(navArgument("tab") {
                type = NavType.IntType
                defaultValue = 0
            })
        ) { entry ->
            val initialTab = entry.arguments?.getInt("tab") ?: 0
            DataSharingScreen(
                onNavigateBack        = { navController.popBackStack() },
                onNavigateToPatientDetail = { id -> navController.navigate(Routes.patientDetail(id)) },
                initialTab = initialTab
            )
        }

        // ── Messagerie ────────────────────────────────────────────────────────
        composable(Routes.MESSAGERIE) {
            MessagerieScreen(
                onNavigateBack            = { navController.popBackStack() },
                onNavigateToConversation  = { convId ->
                    navController.navigate(Routes.conversation(convId, ""))
                }
            )
        }

        composable(
            route     = Routes.CONVERSATION,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("interlocuteur")  {
                    type         = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { back ->
            val conversationId = back.arguments?.getString("conversationId") ?: ""
            val interlocuteur  = back.arguments?.getString("interlocuteur")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            ConversationDetailScreen(
                conversationId  = conversationId,
                interlocuteurNom = interlocuteur,
                onNavigateBack  = { navController.popBackStack() },
                onNavigateToVideoCall = { room, nom, audioOnly ->
                    if (room == "voip") {
                        navController.navigate(Routes.VOIP_CALL)
                    } else {
                        navController.navigate(Routes.videoCall(room, nom, audioOnly))
                    }
                },
                callManager = callManager
            )
        }

        // ── Appel Video/Audio integre ────────────────────────────────────────
        composable(
            route     = Routes.VIDEO_CALL,
            arguments = listOf(
                navArgument("roomName")      { type = NavType.StringType },
                navArgument("interlocuteur") { type = NavType.StringType; defaultValue = "" },
                navArgument("audioOnly")     { type = NavType.BoolType; defaultValue = false }
            )
        ) { back ->
            val roomName      = back.arguments?.getString("roomName") ?: ""
            val interlocuteur = back.arguments?.getString("interlocuteur")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val audioOnly     = back.arguments?.getBoolean("audioOnly") ?: false
            VideoCallScreen(
                roomName          = roomName,
                interlocuteurNom  = interlocuteur,
                isAudioOnly       = audioOnly,
                onNavigateBack    = { navController.popBackStack() }
            )
        }

        // ── Appel VoIP natif WebRTC ──────────────────────────────────────────
        composable(Routes.VOIP_CALL) {
            callManager?.let { cm ->
                CallScreen(
                    callManager = cm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // ── Donnees patient partagees (vue medecin) ─────────────────────────
        composable(
            route     = Routes.SHARED_PATIENT,
            arguments = listOf(
                navArgument("patientUid") { type = NavType.StringType },
                navArgument("patientNom") { type = NavType.StringType; defaultValue = "" }
            )
        ) { back ->
            val patientUid = back.arguments?.getString("patientUid") ?: ""
            val patientNom = back.arguments?.getString("patientNom")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            SharedPatientDataScreen(
                patientUid        = patientUid,
                patientNom        = patientNom,
                onNavigateBack    = { navController.popBackStack() },
                onNavigateToRendezVous = { navController.navigate(Routes.rendezVous()) }
            )
        }

        // ── Parametres ─────────────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Rapports PDF (export patient ↔ medecin) ──────────────────────────
        composable(Routes.REPORTS) {
            ReportsScreen(onBack = { navController.popBackStack() })
        }

        // ── Profil utilisateur ──────────────────────────────────────────────
        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Carnet de bord ─────────────────────────────────────────────────
        composable(
            route     = Routes.JOURNAL,
            arguments = listOf(
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            JournalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Podometre ──────────────────────────────────────────────────────
        composable(
            route     = Routes.PEDOMETER,
            arguments = listOf(
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            PedometerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Validations ROLLY ────────────────────────────────────────────
        composable(Routes.VALIDATIONS) {
            ValidationsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Communaute patients ─────────────────────────────────────────
        composable(Routes.COMMUNITY) {
            CommunityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Courbes predictives ──────────────────────────────────────────
        composable(
            route     = Routes.PREDICTIVE,
            arguments = listOf(
                navArgument("patientId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            PredictiveGlucoseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
