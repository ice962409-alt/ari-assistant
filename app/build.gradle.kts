plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ari.assistant"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
            )
        }
    }

    // Clave de depuración fija para que cada APK de GitHub Actions tenga la
    // misma firma y se pueda instalar encima del anterior sin desinstalar.
    // Es solo para depuración; nunca usarla para publicar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.ari.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "0.13.1"
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    testImplementation("junit:junit:4.13.2")
}
