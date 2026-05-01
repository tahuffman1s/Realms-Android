package com.realmsoffate.game.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Bridges UpdateRepository's ReadyToInstall state to Android's package
 * installer. Owns the FileProvider URI construction and signature mismatch
 * detection (used in debug builds to warn the user that installing the
 * release APK over a debug build will require an uninstall + data wipe).
 */
object InstallLauncher {

    /** Build the install intent for [apk] using the updater's FileProvider. */
    fun buildIntent(context: Context, apk: File): Intent {
        val authority = "${context.packageName}.updates"
        val uri = FileProvider.getUriForFile(context, authority, apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * True if the APK at [apk] is signed by a different cert than the running
     * package. Returns false when we can't determine (treat as "no warning").
     *
     * Used in debug builds: a debug-signed install replacing a release-signed
     * APK (or vice versa) will trigger Android's signature-mismatch error and
     * require an uninstall, which deletes save data.
     */
    fun signatureMismatchAgainstInstalled(context: Context, apk: File): Boolean {
        return try {
            val installedSigs = installedSignatures(context, context.packageName) ?: return false
            val pkgSigs = apkSignatures(context, apk) ?: return false
            installedSigs != pkgSigs
        } catch (_: Exception) {
            false
        }
    }

    private fun installedSignatures(context: Context, packageName: String): Set<String>? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return null
        return sigs.map { sha256(it) }.toSet()
    }

    private fun apkSignatures(context: Context, apk: File): Set<String>? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return null
        return sigs.map { sha256(it) }.toSet()
    }

    private fun sha256(sig: Signature): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
