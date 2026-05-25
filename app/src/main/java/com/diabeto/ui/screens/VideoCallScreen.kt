package com.diabeto.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.*
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.diabeto.R
import androidx.core.content.ContextCompat
import com.diabeto.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoCallScreen(
    roomName: String,
    interlocuteurNom: String,
    isAudioOnly: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Permissions camera + micro
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val cameraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (cameraOk && audioOk) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Cleanup WebView
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isAudioOnly) Icons.Default.Call else Icons.Default.Videocam,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                interlocuteurNom,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                if (isAudioOnly) "Appel audio" else "Appel video",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        webView?.destroy()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                    }
                },
                actions = {
                    // Bouton raccrocher
                    IconButton(
                        onClick = {
                            webView?.destroy()
                            onNavigateBack()
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StatusRedDark,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    "Raccrocher",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OnBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (!permissionsGranted) {
                // Demande de permissions
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        if (isAudioOnly) Icons.Default.MicOff else Icons.Default.VideocamOff,
                        null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Autorisez la camera et le microphone\npour passer l'appel",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(stringResource(R.string.common_authorize))
                    }
                }
            } else {
                // WebView Jitsi Meet
                val jitsiUrl = buildString {
                    append("https://meet.jit.si/$roomName")
                    append("#config.prejoinPageEnabled=false")
                    append("&config.disableDeepLinking=true")
                    append("&config.disableInviteFunctions=true")
                    append("&config.hideConferenceSubject=true")
                    append("&config.subject=%20")
                    if (isAudioOnly) {
                        append("&config.startWithVideoMuted=true")
                    }
                    append("&interfaceConfig.MOBILE_APP_PROMO=false")
                    append("&interfaceConfig.SHOW_CHROME_EXTENSION_BANNER=false")
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = false
                                javaScriptCanOpenWindowsAutomatically = false
                                databaseEnabled = true
                                setSupportMultipleWindows(false)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                userAgentString = settings.userAgentString.replace("; wv", "")
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    // Only grant camera and microphone — deny everything else
                                    val allowed = request?.resources?.filter { res ->
                                        res == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                        res == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                    }?.toTypedArray()
                                    if (!allowed.isNullOrEmpty()) {
                                        request.grant(allowed)
                                    } else {
                                        request?.deny()
                                    }
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    if (newProgress >= 80) {
                                        isLoading = false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    // Garder dans le WebView pour Jitsi
                                    if (url.contains("meet.jit.si") || url.contains("jitsi")) {
                                        return false
                                    }
                                    return false
                                }
                            }

                            webView = this
                            loadUrl(jitsiUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OnBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Primary)
                            Text(
                                "Connexion en cours...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                "Appel ${if (isAudioOnly) "audio" else "video"} avec $interlocuteurNom",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
