plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ari Estado: app aparte, solo con el widget que muestra el progreso de Claude.
// Es la única que se conecta a internet (a api.github.com, por HTTPS); Ari no.
android {
    namespace = "com.ari.estado"
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

    // La misma clave de depuración que Ari, para instalar encima sin desinstalar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.ari.estado"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.0"
    }
}
