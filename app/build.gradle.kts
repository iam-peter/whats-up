plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.whatsup"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.whatsup"
        minSdk = 31
        targetSdk = 36
        // CI passes -PversionCode/-PversionName so nightly builds install over each other.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        manifestPlaceholders["widgetLabel"] = "@string/widget_name"
    }

    signingConfigs {
        // The release key never enters the repository: CI restores it from
        // secrets, locally it can be passed the same way.
        create("release") {
            System.getenv("WHATSUP_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("WHATSUP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WHATSUP_KEY_ALIAS") ?: "whatsup"
                keyPassword = System.getenv("WHATSUP_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the key, the release APK is left unsigned.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
        debug {
            // Installs next to the signed release build instead of replacing it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "whats-up (debug)"
            manifestPlaceholders["widgetLabel"] = "whats-up calendar (debug)"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
