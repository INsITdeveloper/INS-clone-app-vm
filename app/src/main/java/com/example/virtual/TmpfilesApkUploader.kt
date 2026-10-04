package com.example.virtual

import android.content.Context
import com.example.data.CloneAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class TmpfilesUploadResult(
    val success: Boolean,
    val pageUrl: String = "",
    val directDownloadUrl: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

object TmpfilesApkUploader {

    private const val UPLOAD_ENDPOINT = "https://tmpfiles.org/api/v1/upload"

    /**
     * Resolves the APK file for this host application ("Clone App" APK itself).
     */
    fun getHostApkFile(context: Context): File? {
        val sourceDir = context.applicationInfo?.sourceDir ?: return null
        val file = File(sourceDir)
        return if (file.exists() && file.canRead()) file else null
    }

    /**
     * Resolves the APK file for a cloned app (either the sandbox's extracted `base.apk`
     * or the real installed host package's `sourceDir`).
     */
    fun getCloneApkFile(context: Context, clone: CloneAppEntity): File? {
        val sandboxApk = File(VirtualSandboxStorage.getSandboxRoot(context, clone), "base.apk")
        if (sandboxApk.exists() && sandboxApk.length() > 0L) {
            return sandboxApk
        }
        val resolved = VirtualApkLauncher.resolveInstalledApk(context, clone)
        if (resolved != null) {
            val hostFile = File(resolved.sourceApkPath)
            if (hostFile.exists() && hostFile.canRead()) {
                return hostFile
            }
        }
        return null
    }

    /**
     * Uploads [apkFile] to `https://tmpfiles.org/api/v1/upload` using multipart/form-data
     * and returns both the viewing page URL and direct download URL (`/dl/`).
     */
    suspend fun uploadApkToTmpfiles(
        apkFile: File,
        desiredFileName: String,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): TmpfilesUploadResult = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || !apkFile.canRead()) {
            return@withContext TmpfilesUploadResult(
                success = false,
                errorMessage = "File APK tidak ditemukan atau tidak dapat dibaca: ${apkFile.absolutePath}"
            )
        }

        val totalBytes = apkFile.length()
        val safeFileName = if (desiredFileName.endsWith(".apk", ignoreCase = true)) {
            desiredFileName
        } else {
            "$desiredFileName.apk"
        }

        val boundary = "----InsTmpfilesBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        var connection: HttpURLConnection? = null
        try {
            val url = URL(UPLOAD_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                doInput = true
                doOutput = true
                useCaches = false
                connectTimeout = 30_000
                readTimeout = 120_000
                requestMethod = "POST"
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("User-Agent", "CloneApp-VirtualSpace/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(64 * 1024)
            }

            DataOutputStream(connection.outputStream).use { outputStream ->
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"$lineEnd"
                )
                outputStream.writeBytes("Content-Type: application/vnd.android.package-archive$lineEnd")
                outputStream.writeBytes(lineEnd)

                BufferedInputStream(FileInputStream(apkFile)).use { fileInput ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var uploaded = 0L
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        uploaded += bytesRead
                        onProgress(uploaded, totalBytes)
                    }
                }

                outputStream.writeBytes(lineEnd)
                outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                outputStream.flush()
            }

            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = BufferedReader(InputStreamReader(responseStream)).use { reader ->
                reader.readText()
            }

            if (status in 200..299) {
                val urlMatch = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)
                val rawUrl = urlMatch?.groupValues?.getOrNull(1).orEmpty()
                    .replace("\\/", "/")
                    .replace("http://tmpfiles.org/", "https://tmpfiles.org/")

                if (rawUrl.isNotBlank()) {
                    val directUrl = rawUrl.replace(
                        "https://tmpfiles.org/",
                        "https://tmpfiles.org/dl/"
                    )
                    return@withContext TmpfilesUploadResult(
                        success = true,
                        pageUrl = rawUrl,
                        directDownloadUrl = directUrl,
                        fileName = safeFileName,
                        fileSizeBytes = totalBytes
                    )
                }
            }

            return@withContext TmpfilesUploadResult(
                success = false,
                fileName = safeFileName,
                fileSizeBytes = totalBytes,
                errorMessage = "HTTP $status: ${responseBody.take(240)}"
            )
        } catch (e: Exception) {
            return@withContext TmpfilesUploadResult(
                success = false,
                fileName = safeFileName,
                fileSizeBytes = totalBytes,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
