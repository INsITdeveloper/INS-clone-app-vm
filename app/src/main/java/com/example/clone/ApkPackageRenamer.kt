package com.example.clone

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType
import java.io.File

/**
 * Mengubah nama paket (package name) di dalam APK biner memakai ARSCLib.
 *
 * Ada dua hal yang harus ditangani agar APK hasil clone tetap bisa jalan:
 *
 * 1. **Nama class yang relatif.** Android menyelesaikan `android:name=".MainActivity"`
 *    relatif terhadap nama package di manifest. Kalau package diubah tanpa memperbaiki ini,
 *    class menjadi tidak ditemukan. Karena itu semua nama class relatif diubah menjadi absolut
 *    memakai package LAMA (tempat class benar-benar berada) dan TIDAK ikut diganti.
 *
 * 2. **Identifier yang harus unik.** Authority provider, nama permission, taskAffinity, dan
 *    action yang diawali package lama diganti ke package baru agar clone kedua tidak bentrok.
 *
 * Atribut `meta-data name` dan atribut deep-link (`data scheme/host/path`) sengaja TIDAK diubah
 * supaya library pihak ketiga dan deep link tetap berfungsi.
 */
object ApkPackageRenamer {

    private val CLASS_NAME_TAGS = setOf(
        "application", "activity", "service", "receiver", "provider", "instrumentation"
    )

    private val DATA_TAG_ATTRIBUTES = setOf(
        "scheme", "host", "port", "path", "pathPrefix", "pathPattern", "pathAdvancedPattern", "mimeType", "ssp"
    )

    fun renamePackage(
        inputApk: File,
        outputApk: File,
        newPackage: String,
        newAppLabel: String? = null
    ) {
        require(inputApk.exists()) { "APK sumber tidak ditemukan: ${inputApk.absolutePath}" }
        outputApk.parentFile?.mkdirs()

        val module = ApkModule.loadApkFile(inputApk)
        try {
            val manifest = module.getAndroidManifest()
            val oldPackage = manifest?.getPackageName()
                ?: runCatching { module.getPackageName() }.getOrNull()
                ?: ""

            if (manifest != null && oldPackage.isNotBlank()) {
                // Perbaiki nama class relatif SEBELUM package diubah.
                rewriteManifestReferences(manifest.getManifestElement(), oldPackage, newPackage)
            }

            // Mengubah atribut package + nama package di resource table (resources.arsc).
            module.setPackageName(newPackage)

            // Nama aplikasi clone agar mudah dibedakan dari aplikasi asli.
            if (manifest != null && !newAppLabel.isNullOrBlank()) {
                runCatching {
                    val application = manifest.getOrCreateApplicationElement()
                    val labelAttribute = application.getOrCreateAndroidAttribute(
                        AndroidManifestBlock.NAME_label,
                        AndroidManifestBlock.ID_label
                    )
                    labelAttribute.setValueAsString(newAppLabel)
                }
            }

            module.writeApk(outputApk)
        } finally {
            runCatching { module.close() }
        }
    }

    private fun rewriteManifestReferences(
        root: ResXmlElement?,
        oldPackage: String,
        newPackage: String
    ) {
        if (root == null) return
        val elements = root.recursiveElements()
        while (elements.hasNext()) {
            val element = elements.next()
            val tag = element.getName()?.lowercase() ?: ""
            val attributes = element.getAttributes()
            while (attributes.hasNext()) {
                val attribute = attributes.next()
                if (attribute.getValueType() != ValueType.STRING) continue
                val attributeName = attribute.getName() ?: ""
                val value = attribute.getValueAsString() ?: continue

                // 1. Nama class: ubah relatif -> absolut memakai package LAMA, jangan diganti.
                val isClassNameAttribute =
                    (tag in CLASS_NAME_TAGS && attributeName == "name") ||
                        (tag == "activity-alias" && (attributeName == "name" || attributeName == "targetActivity"))
                if (isClassNameAttribute) {
                    if (value.startsWith(".")) {
                        attribute.setValueAsString(oldPackage + value)
                    }
                    continue
                }

                // 2. Jangan sentuh key meta-data maupun atribut deep link.
                if (tag == "meta-data" && attributeName == "name") continue
                if (tag == "data" && attributeName in DATA_TAG_ATTRIBUTES) continue

                // 3. Identifier lain yang diawali package lama -> package baru.
                when {
                    value == oldPackage -> attribute.setValueAsString(newPackage)
                    value.startsWith("$oldPackage.") ->
                        attribute.setValueAsString(newPackage + value.substring(oldPackage.length))
                }
            }
        }
    }
}
