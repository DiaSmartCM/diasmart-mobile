// Version PC de DiaSmart (Compose Desktop). Build Gradle separe de l'app
// Android : il reutilise directement le code du module commun shared/.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "DiaSmartPC"
