import java.util.Properties

// Lecture des secrets locaux (TURN credentials, keystore).
// La cle Gemini n'est plus dans l'APK depuis v2.1.37 : toutes les requetes
// passent par le proxy Vercel (/api/rolly-chat).
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.diabeto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diabeto"
        minSdk = 26
        targetSdk = 34
        versionCode = 60
        versionName = "2.1.44"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Secrets injectés depuis local.properties (jamais dans le code source).
        // v2.1.37 : GEMINI_API_KEY supprime — la cle vit cote serveur (Vercel env).
        buildConfigField(
            "String",
            "TURN_USERNAME",
            "\"${localProps.getProperty("TURN_USERNAME", "")}\""
        )
        buildConfigField(
            "String",
            "TURN_PASSWORD",
            "\"${localProps.getProperty("TURN_PASSWORD", "")}\""
        )
    }

    signingConfigs {
        create("release") {
            storeFile = file("../diasmart-release.jks")
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = localProps.getProperty("KEY_ALIAS", "")
            keyPassword = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Utiliser aussi la clé release pour le debug → même signature partout
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // WebRTC (native peer-to-peer audio/video)
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // SQLCipher — encryption for Room database (medical data protection)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Kotlin Serialization (pour parsing JSON des réponses Gemini)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Charts
    implementation("com.patrykandpatrick.vico:compose:2.0.0-alpha.15")
    implementation("com.patrykandpatrick.vico:compose-m2:2.0.0-alpha.15")
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.15")
    implementation("com.patrykandpatrick.vico:core:2.0.0-alpha.15")

    // Date/Time Picker
    implementation("io.github.vanpra.compose-material-dialogs:datetime:0.9.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // Geolocation (Fused Location Provider for doctor/patient positions)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ============================================================
    // Firebase BOM (gestion centralisée des versions)
    // ============================================================
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.10.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    // OkHttp (appels REST vers notre API Vercel pour la verification email OTP)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Google Sign-In (Credential Manager)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:1.5.0-rc01")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0-rc01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.firebase:firebase-analytics")

    // Firebase Vertex AI (Gemini via Firebase) — version explicite car pas dans tous les BOM
    implementation("com.google.firebase:firebase-vertexai:16.0.2")

    // Firebase App Check (protection anti-abus)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Coroutines pour Firebase (await, etc.)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Coil (chargement d'images)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // (v2.1.37 : SDK Gemini Android supprime — ROLLY passe par le proxy Vercel,
    //  cf. RollyChatClient.kt. La cle API vit cote serveur uniquement.)
    // (v2.1.31 : MediaPipe tasks-genai supprime — Gemma on-device retire pour
    //  reduire la taille de l'APK.)

    // ============================================================
    // Biometric / Device-credential authentication (App Lock)
    // - empreinte digitale, mot de passe, schema, code PIN
    // - BiometricPrompt unifie + fallback automatique sur les credentials systeme
    // ============================================================
    implementation("androidx.biometric:biometric:1.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Room schema export — configure the output directory for KSP
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
