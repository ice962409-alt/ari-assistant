// R8 9.1.29: la R8 que trae AGP 8.13.2 no entiende los metadatos de Kotlin 2.4 y daba cientos
// de avisos al encoger la versión de entrega ("An error occurred when parsing kotlin metadata").
// Es la versión mínima para Kotlin 2.4 según
// https://developer.android.com/studio/build/kotlin-d8-r8-versions
// Esa versión solo está en el repositorio de versiones de R8 (no en Google Maven), como indica
// la guía oficial de R8 para usar una versión distinta de la que trae AGP.
buildscript {
    repositories {
        google()
        maven("https://storage.googleapis.com/r8-releases/raw") {
            content { includeModule("com.android.tools", "r8") }
        }
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.29")
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}
