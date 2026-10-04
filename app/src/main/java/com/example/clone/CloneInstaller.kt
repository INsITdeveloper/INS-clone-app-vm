package com.example.clone

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Memasang APK klon lewat `PackageInstaller`.
 *
 * Menampilkan dialog konfirmasi pemasangan bawaan Android (karena bukan dari Play Store).
 * Hasil (sukses/gagal) diterima lewat BroadcastReceiver internal.
 */
object CloneInstaller {

    private const val INSTALL_TIMEOUT_MS = 10 * 60 * 1000L

    suspend fun install(context: Context, built: BuiltCloneApk): Result<String> {
        val appContext = context.applicationContext
        val action = "com.example.clone.INSTALL_RESULT.${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<Result<String>>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                runCatching { appContext.unregisterReceiver(this) }
                val status = intent?.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                ) ?: PackageInstaller.STATUS_FAILURE
                val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    val installedPackage =
                        intent?.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
                    deferred.complete(Result.success(installedPackage))
                } else {
                    deferred.complete(
                        Result.failure(
                            IllegalStateException(
                                message ?: "Pemasangan APK klon gagal (status $status)"
                            )
                        )
                    )
                }
            }
        }

        try {
            // RECEIVER_EXPORTED: hasil instalasi dikirim oleh PackageInstaller sistem lewat
            // PendingIntent; action-nya unik sehingga aman untuk diekspor.
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (t: Throwable) {
            return Result.failure(t)
        }

        try {
            withContext(Dispatchers.IO) {
                val installer = appContext.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)
                try {
                    built.allApks.forEach { apk ->
                        val entryName = if (apk == built.baseApk) "base.apk" else apk.name
                        session.openWrite(entryName, 0, apk.length()).use { output ->
                            apk.inputStream().use { input -> input.copyTo(output) }
                            session.fsync(output)
                        }
                    }

                    val callbackIntent = Intent(action).setPackage(appContext.packageName)
                    val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        sessionId,
                        callbackIntent,
                        pendingFlags
                    )
                    session.commit(pendingIntent.intentSender)
                } finally {
                    runCatching { session.close() }
                }
            }
        } catch (t: Throwable) {
            runCatching { appContext.unregisterReceiver(receiver) }
            return Result.failure(t)
        }

        return withTimeoutOrNull(INSTALL_TIMEOUT_MS) { deferred.await() }
            ?: run {
                runCatching { appContext.unregisterReceiver(receiver) }
                Result.failure(IllegalStateException("Waktu pemasangan APK klon habis"))
            }
    }
}
