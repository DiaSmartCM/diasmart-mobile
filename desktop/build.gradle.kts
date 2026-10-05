import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

// Meme numero de version que l'app Android (lu dans app/build.gradle.kts)
val versionDiaSmart: String = Regex("versionName = \"([0-9.]+)\"")
    .find(file("../app/build.gradle.kts").readText())?.groupValues?.get(1) ?: "1.0.0"

kotlin {
    jvmToolchain(17)
    // Le code commun (regles glycemie, suivi etablissement...) est compile
    // tel quel pour le PC : c'est du Kotlin pur.
    sourceSets["main"].kotlin.srcDir("../shared/src/commonMain/kotlin")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}

compose.desktop {
    application {
        mainClass = "com.diabeto.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "DiaSmart"
            packageVersion = versionDiaSmart
            description = "DiaSmart pour PC : espace etablissement de sante"
            vendor = "DiaSmart"
            // java.net.http (appels Firebase) et java.prefs (compteur d'essais)
            // ne sont pas dans le runtime minimal
            modules("java.net.http", "java.prefs")
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                menuGroup = "DiaSmart"
                // Fixe : permet a une nouvelle version de remplacer l'ancienne
                upgradeUuid = "5d0f3c7e-9a41-4b8e-a6a2-3f1d2c9b7e10"
            }
        }
    }
}
