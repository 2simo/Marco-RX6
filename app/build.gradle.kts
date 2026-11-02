

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciKeystorePath = System.getenv("KEYSTORE_FILE")
val hasCiSigningKey = !ciKeystorePath.isNullOrBlank()

android {
    namespace = "com.marcorx6.library"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marcorx6.library"
        minSdk = 23
        targetSdk = 35
        versionCode = 9
        versionName = "7.2"
    }

    signingConfigs {
        create("ci") {
            if (hasCiSigningKey) {
                storeFile = file(ciKeystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "marcorx6"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
        create("release") {
            storeFile = file(
                System.getenv("KEYSTORE_FILE") ?: "release.jks"
            )
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "marcorx6"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasCiSigningKey) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
