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

android {
    namespace = "uz.alifbo.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.alifbo.keyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "REGISTRY_URL", "\"$registryUrl\"")
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
}
