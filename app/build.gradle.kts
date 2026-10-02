import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localValue(key: String, default: String = ""): String =
    (localProps.getProperty(key) ?: default).trim()

android {
    namespace = "com.example.pengo"
    compileSdk {
        version = release(37)
    }

    val releaseStore = localValue("RELEASE_STORE_FILE").takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
    signingConfigs {
        if (releaseStore != null && releaseStore.exists()) {
            create("release") {
                storeFile = releaseStore
                storePassword = localValue("RELEASE_STORE_PASSWORD")
                keyAlias = localValue("RELEASE_KEY_ALIAS")
                keyPassword = localValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.pengo.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        buildConfigField("String", "PENGO_API_URL", "\"${localValue("PENGO_API_URL", "https://nbojdnnnpdwuteiuxnlq.supabase.co")}\"")
        buildConfigField("String", "PENGO_ANON_KEY", "\"${localValue("PENGO_ANON_KEY")}\"")
        buildConfigField("String", "MERCHANT_UPI_ID", "\"${localValue("MERCHANT_UPI_ID", "7206959166@kotakbank")}\"")
        buildConfigField("String", "MERCHANT_NAME", "\"${localValue("MERCHANT_NAME", "Pengo")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.androidx.biometric)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
