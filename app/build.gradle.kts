plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Alifbo paketlari ro'yxati (registry.json) joylashgan manzil.
// GitHub'ga yuklagach OWNER/REPO ni o'zingiznikiga almashtiring yoki
// build vaqtida:  gradle assembleDebug -PregistryUrl=https://.../registry.json
// (ilova ichida Sozlamalar orqali ham o'zgartirsa bo'ladi)
val registryUrl: String = (project.findProperty("registryUrl") as String?)
    ?: "https://raw.githubusercontent.com/OWNER/REPO/main/registry/registry.json"

// Versiya: build vaqtida  -PversionName=1.2.0  (release workflow tegdan oladi).
// versionCode = major*10000 + minor*100 + patch, shunda yangi versiya eskisining ustiga oʻrnatiladi.
val appVersionName: String = (project.findProperty("versionName") as String?) ?: "1.2.0"
val appVersionCode: Int = appVersionName.split(".").mapNotNull { it.toIntOrNull() }.let {
    (it.getOrElse(0) { 0 }) * 10000 + (it.getOrElse(1) { 0 }) * 100 + (it.getOrElse(2) { 0 })
}.coerceAtLeast(1)

// Yangilanishlar tekshiriladigan GitHub repo (owner/repo).
val githubRepo: String = (project.findProperty("githubRepo") as String?) ?: "shaxboz1011/uzbek-klaviatura"

// Qatʼiy imzo kaliti (GitHub Secrets). Boʻlmasa — standart debug kalit (faqat sinov uchun).
val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "uz.alifbo.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.alifbo.keyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // Faqat telefonlar uchun (x86 emulyator kutubxonalari APK ni kattalashtirmasligi uchun chiqarib tashlandi).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("String", "REGISTRY_URL", "\"$registryUrl\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    signingConfigs {
        if (ksFile != null) {
            create("alifbo") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (ksFile != null) signingConfig = signingConfigs.getByName("alifbo")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("alifbo") else signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // registry/ papkasi: packs/*.json ilova ichiga "boshlang'ich" sifatida joylanadi.
    // Server (registry) dan yangiroq versiya kelsa, u ustun turadi.
    sourceSets {
        getByName("main") {
            assets.srcDir("../registry")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-ktx:1.9.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")

    // Kameradan matn oʻqish: CameraX + ML Kit (telefonning oʻzida, internetsiz).
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
