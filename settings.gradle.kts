pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (kirill yozuvini oʻqish uchun) JitPack orqali tarqatiladi.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.adaptech-cz.Tesseract4Android") }
        }
    }
}
rootProject.name = "AlifboKlaviatura"
include(":app")
