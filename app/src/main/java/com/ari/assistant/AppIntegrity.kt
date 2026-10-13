package com.ari.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * ¿Esta copia de Ari es la original? Cada APK oficial va firmado con la clave de Ari.
 * Si alguien modifica la app, tiene que volver a firmarla con otra clave, y aquí se nota:
 * Ari avisa y no abre, para que nadie use una copia alterada pensando que es la real.
 */
object AppIntegrity {

    /** Huella (SHA-256) del certificado con el que se firman los APK de Ari. */
    const val OFFICIAL_CERT_SHA256 = "4FDBB60B5E9B8A28CEAFC0870C50ADBB2C870B188E83D76DCC063D0E26E55CB9"

    fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02X".format(it) }

    /** true si la app está firmada con la clave oficial (o si Android no deja saberlo). */
    fun isOfficial(context: Context): Boolean = runCatching {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return true
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val list = certificates?.toList().orEmpty()
        list.isEmpty() || list.any { fingerprint(it.toByteArray()) == OFFICIAL_CERT_SHA256 }
    }.getOrDefault(true)
}
