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
    // Para Google Play, la tienda volverá a firmar con su propia clave (Play App Signing).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Versión de entrega: código encogido y revuelto (R8), sin modo de depuración (nadie puede
    // leer los datos de Ari con un cable) y firmada con la misma clave para instalarse encima.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.ari.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 81
        versionName = "0.66.1"
        // El intermediario de informes de fallos (contrato en el repositorio ari-informes). No es secreto.
        buildConfigField("String", "INFORMES_URL", "\"https://ari-informes.javierhedzd.workers.dev/v1/informe\"")
        // De qué compilación de GitHub Actions viene este APK: ARI lo usa para saber si hay una más nueva.
        buildConfigField("long", "BUILD_RUN", (System.getenv("GITHUB_RUN_ID") ?: "0") + "L")
        buildConfigField("String", "BUILD_BRANCH", "\"" + (System.getenv("GITHUB_REF_NAME") ?: "") + "\"")
        // Dónde ARI Trader revisa si hay una versión nueva (archivo público, sin llave).
        buildConfigField("String", "TRADER_UPDATE_URL", "\"https://raw.githubusercontent.com/javierhedzd-ops/ari-web/main/ari-trader-latest.json\"")
        // Solo teléfonos de 64 bits (como el POCO): las librerías de voz pesan menos en el APK.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Dos versiones con el mismo código:
    // - completa: la Ari del dueño, la CEO, con su equipo de modelos (src/completa).
    // - otros: para las demás personas, sin el equipo ni el permiso de ubicación (src/otros).
    flavorDimensions += "version"
    productFlavors {
        create("completa") {
            dimension = "version"
        }
        create("otros") {
            dimension = "version"
            // La Ari comercial: su propio paquete, para convivir con la ARI del dueño y publicarse aparte.
            applicationId = "com.ari.asistente"
            versionNameSuffix = "-otros"
        }
        create("trader") {
            dimension = "version"
            // ARI Trader: copia para el hermano del dueño, especializada en trading. App aparte,
            // sin los poderes de CEO (control del teléfono, GitHub, auto-mejora, equipo de modelos).
            applicationId = "com.ari.trader"
            versionNameSuffix = "-trader"
        }
    }
}

dependencies {
    // Versión fija (antes latest.release): la que resolvió el run 38874693461. Así el mismo commit
    // siempre compila el mismo motor. Para actualizarla, cambiarla aquí a propósito.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    // Reconocer voces: sherpa-onnx (Apache 2.0). GitHub Actions descarga el .aar en app/libs al
    // compilar (no vive en el repositorio; ver .github/workflows/android.yml).
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    testImplementation("junit:junit:4.13.2")
    // Pruebas de la base de datos real (SQLite de Android) en la JVM: AriDatabaseTest.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
