package com.example.clone

import android.content.Context
import com.android.apksig.ApkSigner
import com.example.R
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Menyediakan kunci penandatanganan (self-signed) untuk APK hasil klon.
 *
 * Kunci disimpan sebagai PKCS#12 di `res/raw/clone_keystore.p12`. Ini adalah kunci
 * self-signed milik aplikasi ini sendiri (bukan kredensial layanan pihak ketiga),
 * dipakai hanya untuk menandatangani APK yang kita bangun ulang.
 */
object CloneSigning {

    private const val STORE_PASSWORD = "insclone"
    private const val ALIAS = "insclone"
    private const val KEY_ALIAS_NAME = "INS_CLONE"

    @Volatile
    private var cachedSignerConfig: ApkSigner.SignerConfig? = null

    fun signerConfig(context: Context): ApkSigner.SignerConfig {
        cachedSignerConfig?.let { return it }
        synchronized(this) {
            cachedSignerConfig?.let { return it }
            val keyStore = KeyStore.getInstance("PKCS12")
            context.resources.openRawResource(R.raw.clone_keystore).use { input ->
                keyStore.load(input, STORE_PASSWORD.toCharArray())
            }
            val privateKey = keyStore.getKey(ALIAS, STORE_PASSWORD.toCharArray()) as PrivateKey
            val certificate = keyStore.getCertificate(ALIAS) as X509Certificate
            val config = ApkSigner.SignerConfig.Builder(KEY_ALIAS_NAME, privateKey, listOf(certificate)).build()
            cachedSignerConfig = config
            return config
        }
    }

    /**
     * Menandatangani [inputApk] (v1 + v2 + v3) dan menulis hasilnya ke [outputApk].
     */
    fun sign(context: Context, inputApk: File, outputApk: File) {
        outputApk.parentFile?.mkdirs()
        val signer = ApkSigner.Builder(listOf(signerConfig(context)))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
        signer.sign()
    }
}
