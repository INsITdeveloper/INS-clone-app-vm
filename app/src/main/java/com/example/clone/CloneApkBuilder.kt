package com.example.clone

import android.content.Context
import java.io.File

/**
 * Hasil pembangunan APK klon: satu base APK + daftar split APK (config ABI/density/bahasa).
 */
data class BuiltCloneApk(
    val baseApk: File,
    val splitApks: List<File>
) {
    val allApks: List<File> get() = listOf(baseApk) + splitApks
}

/**
 * Membangun APK klon dari APK sumber: ganti nama paket -> tanda tangani ulang.
 *
 * Mendukung APK tunggal maupun APK bersplit (base + split_config.*). Setiap APK
 * di-rename ke paket baru lalu di-sign ulang dengan kunci milik aplikasi ini.
 */
object CloneApkBuilder {

    fun build(
        context: Context,
        sourceBaseApk: File,
        sourceSplitApks: List<File>,
        newPackage: String,
        workDir: File,
        newAppLabel: String? = null
    ): BuiltCloneApk {
        val renamedDir = File(workDir, "renamed").apply { mkdirs() }
        val signedDir = File(workDir, "signed").apply { mkdirs() }

        val baseRenamed = File(renamedDir, "base.apk")
        ApkPackageRenamer.renamePackage(sourceBaseApk, baseRenamed, newPackage, newAppLabel)
        val baseSigned = File(signedDir, "base.apk")
        CloneSigning.sign(context, baseRenamed, baseSigned)

        val signedSplits = sourceSplitApks.mapIndexed { index, splitSource ->
            val splitName = splitSource.name.takeIf { it.endsWith(".apk") } ?: "split_$index.apk"
            val splitRenamed = File(renamedDir, splitName)
            ApkPackageRenamer.renamePackage(splitSource, splitRenamed, newPackage)
            val splitSigned = File(signedDir, splitName)
            CloneSigning.sign(context, splitRenamed, splitSigned)
            splitSigned
        }

        return BuiltCloneApk(baseSigned, signedSplits)
    }
}
