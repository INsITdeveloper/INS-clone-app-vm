package com.example.clone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Titik masuk tingkat tinggi untuk fitur "Clone App".
 *
 * Alur kerja:
 *  1. Ambil APK sumber (dari aplikasi terpasang atau file APK yang diimpor).
 *  2. Ganti nama paketnya menjadi unik per clone (`<paketAsli>.inscloneN`).
 *  3. Tanda tangani ulang dengan kunci milik aplikasi ini.
 *  4. Pasang lewat PackageInstaller sehingga menjadi aplikasi kedua yang benar-benar terpisah.
 *
 * Karena setiap clone memakai nama paket sendiri, APK yang sama bisa ditambah berkali-kali
 * tanpa bentrok, dan APK lain juga bisa ditambah.
 */
object CloneManager {

    /** Menghasilkan nama paket unik untuk clone ke-[instanceIndex]. */
    fun generateClonePackageName(originalPackage: String, instanceIndex: Int): String {
        val suffix = "insclone${instanceIndex + 1}"
        return "$originalPackage.$suffix"
    }

    /** Cek apakah paket clone sudah benar-benar terpasang di perangkat. */
    fun isCloneInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    /** Mengambil base APK + split APK dari aplikasi yang terpasang di perangkat. */
    fun resolveInstalledSourceApks(context: Context, packageName: String): Pair<File, List<File>>? {
        val pm = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        val basePath = appInfo.publicSourceDir ?: appInfo.sourceDir ?: return null
        val base = File(basePath)
        if (!base.exists() || !base.canRead()) return null
        val splits = (appInfo.splitPublicSourceDirs ?: appInfo.splitSourceDirs)
            ?.filter { it.isNotBlank() }
            ?.map { File(it) }
            ?.filter { it.exists() && it.canRead() }
            ?: emptyList()
        return base to splits
    }

    /**
     * Membangun lalu memasang APK klon. Mengembalikan nama paket yang terpasang.
     */
    suspend fun buildAndInstall(
        context: Context,
        sourceBaseApk: File,
        sourceSplitApks: List<File>,
        newPackage: String,
        workDir: File,
        newAppLabel: String? = null
    ): Result<String> {
        return runCatching {
            val built = CloneApkBuilder.build(
                context = context,
                sourceBaseApk = sourceBaseApk,
                sourceSplitApks = sourceSplitApks,
                newPackage = newPackage,
                workDir = workDir,
                newAppLabel = newAppLabel
            )
            val reportedPackage = CloneInstaller.install(context, built).getOrThrow()
            reportedPackage.ifBlank { newPackage }
        }
    }

    /** Apakah aplikasi ini boleh memasang APK (izin "Install unknown apps"). */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Intent menuju pengaturan "Install unknown apps" untuk aplikasi ini. */
    fun unknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }
}
