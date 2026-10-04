// ============================================================================
// Module commun DiaSmart (Kotlin Multiplatform)
// Le code de commonMain est du Kotlin pur : il servira a l'app Android, a la
// future version PC (desktop) et, plus tard, a la version iPhone.
// Regle : rien d'Android ni de java.* dans commonMain.
// ============================================================================

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // Dates multiplateformes (sur Android, s'appuie sur java.time)
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.diabeto.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
