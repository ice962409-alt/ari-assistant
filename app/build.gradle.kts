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
        versionCode = 33
        versionName = "0.21.0"
        // Solo teléfonos de 64 bits (como el POCO): las librerías de voz pesan menos en el APK.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    // Reconocer voces: sherpa-onnx (Apache 2.0). GitHub Actions descarga el .aar en app/libs al
    // compilar (no vive en el repositorio; ver .github/workflows/android.yml).
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    testImplementation("junit:junit:4.13.2")
}
