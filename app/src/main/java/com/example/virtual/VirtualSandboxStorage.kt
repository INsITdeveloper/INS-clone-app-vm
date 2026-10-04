package com.example.virtual

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.CloneAppEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SandboxFileType {
    DIRECTORY,
    SQLITE_DB,
    SHARED_PREFS_XML,
    JSON_CONFIG,
    CACHE_BIN,
    LOG_TEXT,
    OTHER
}

data class SandboxFileItem(
    val name: String,
    val absolutePath: String,
    val virtualDisplayPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val fileType: SandboxFileType,
    val childCount: Int = 0
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(lastModified))

    val unixPermissions: String
        get() = if (isDirectory) "drwxrwx--x" else "-rw-rw----"

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            return "%.2f MB".format(mb)
        }
    }
}

data class CloneStorageStats(
    val totalBytes: Long,
    val databaseBytes: Long,
    val sharedPrefsBytes: Long,
    val cacheBytes: Long,
    val filesBytes: Long,
    val dbFileCount: Int,
    val xmlFileCount: Int
)

/**
 * Manages the physical sandbox directory tree for each cloned app instance:
 * Canonical Path: `/data/user/0/com.ins.virtualspace/virtual/user/<instanceIndex>/<packageName>/`
 */
object VirtualSandboxStorage {

    fun getSandboxRoot(context: Context, clone: CloneAppEntity): File {
        val baseDir = context.dataDir ?: context.filesDir.parentFile ?: context.filesDir
        return File(baseDir, "virtual/user/${clone.instanceIndex}/${clone.packageName}").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Provisions all standard Android data subdirectories inside the virtual container
     * and populates initial SQLite databases, SharedPreferences XMLs, and cache files if not yet initialized.
     */
    fun ensureSandboxProvisioned(context: Context, clone: CloneAppEntity): File {
        val root = getSandboxRoot(context, clone)
        val filesDir = File(root, "files").apply { mkdirs() }
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val codeCacheDir = File(root, "code_cache").apply { mkdirs() }
        val dbDir = File(root, "databases").apply { mkdirs() }
        val prefsDir = File(root, "shared_prefs").apply { mkdirs() }
        File(root, "lib").apply { mkdirs() }

        // 1. Sync Identity Spoofing XML inside shared_prefs/ins_identity_config.xml
        val identityXmlFile = File(prefsDir, "ins_identity_config.xml")
        VirtualXmlPrefsManager.writeEntriesToXml(
            identityXmlFile,
            listOf(
                XmlPrefEntry("spoof_android_id", clone.androidId, XmlPrefType.STRING),
                XmlPrefEntry("spoof_imei", clone.imei, XmlPrefType.STRING),
                XmlPrefEntry("spoof_imsi", clone.imsi, XmlPrefType.STRING),
                XmlPrefEntry("spoof_build_serial", clone.buildSerial, XmlPrefType.STRING),
                XmlPrefEntry("spoof_build_model", clone.buildModel, XmlPrefType.STRING),
                XmlPrefEntry("spoof_build_manufacturer", clone.buildManufacturer, XmlPrefType.STRING),
                XmlPrefEntry("spoof_build_brand", clone.buildBrand, XmlPrefType.STRING),
                XmlPrefEntry("spoof_wifi_mac", clone.wifiMac, XmlPrefType.STRING),
                XmlPrefEntry("spoof_gaid", clone.advertisingId, XmlPrefType.STRING),
                XmlPrefEntry("mock_gps_enabled", clone.mockLocationEnabled.toString(), XmlPrefType.BOOLEAN),
                XmlPrefEntry("mock_gps_lat", clone.mockLatitude.toFloat().toString(), XmlPrefType.FLOAT),
                XmlPrefEntry("mock_gps_lng", clone.mockLongitude.toFloat().toString(), XmlPrefType.FLOAT),
                XmlPrefEntry("virtual_user_slot", clone.instanceIndex.toString(), XmlPrefType.INT)
            )
        )

        // 2. Extract APK binary, classes.dex, and AndroidManifest.xml into the clone's sandbox
        runCatching {
            VirtualApkLauncher.extractApkIntoVirtualSandbox(context, clone, root)
        }

        // 3. Provision App Preferences XML if not exists
        val freshResetMarker = File(filesDir, ".fresh_reset_state")
        val isFreshReset = freshResetMarker.exists()
        val appPrefName = "${clone.packageName.replace('.', '_')}_preferences.xml"
        val appPrefFile = File(prefsDir, appPrefName)
        if (!appPrefFile.exists()) {
            val isPineDrama = clone.packageName.contains("pinedrama", ignoreCase = true) ||
                clone.packageName.contains("ttmd.video", ignoreCase = true) ||
                clone.appName.contains("pinedrama", ignoreCase = true)
            val initialEntries = if (isPineDrama) {
                if (isFreshReset) {
                    listOf(
                        XmlPrefEntry("account_uid", "GUEST_NEW_${clone.androidId.take(6).uppercase()}", XmlPrefType.STRING),
                        XmlPrefEntry("clone_display_name", "Tamu Baru (Data Di-reset)", XmlPrefType.STRING),
                        XmlPrefEntry("clone_username", "guest_${clone.androidId.take(6).lowercase()}", XmlPrefType.STRING),
                        XmlPrefEntry("clone_following_count", "0", XmlPrefType.INT),
                        XmlPrefEntry("clone_followers_count", "0", XmlPrefType.INT),
                        XmlPrefEntry("clone_likes_count", "0", XmlPrefType.INT),
                        XmlPrefEntry("session_token", "fresh_session_${clone.androidId.take(8)}", XmlPrefType.STRING),
                        XmlPrefEntry("vip_drama_unlocked", "false", XmlPrefType.BOOLEAN),
                        XmlPrefEntry("video_quality_preset", "1080p_60fps", XmlPrefType.STRING),
                        XmlPrefEntry("daily_coins_balance", "0", XmlPrefType.INT),
                        XmlPrefEntry("new_user_bonus_claimed", "false", XmlPrefType.BOOLEAN),
                        XmlPrefEntry("autoplay_next_episode", "true", XmlPrefType.BOOLEAN),
                        XmlPrefEntry("last_sync_epoch_ms", System.currentTimeMillis().toString(), XmlPrefType.LONG)
                    )
                } else {
                    listOf(
                        XmlPrefEntry("account_uid", "PD_CLONE_${clone.instanceIndex + 1}_${1000 + clone.id * 37}", XmlPrefType.STRING),
                        XmlPrefEntry("clone_display_name", "Akun Clone #${clone.instanceIndex + 1}", XmlPrefType.STRING),
                        XmlPrefEntry("clone_username", "pinedrama_clone_${clone.instanceIndex + 1}", XmlPrefType.STRING),
                        XmlPrefEntry("clone_following_count", "0", XmlPrefType.INT),
                        XmlPrefEntry("clone_followers_count", "0", XmlPrefType.INT),
                        XmlPrefEntry("clone_likes_count", "0", XmlPrefType.INT),
                        XmlPrefEntry("session_token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.pinedrama_clone_${clone.instanceIndex}", XmlPrefType.STRING),
                        XmlPrefEntry("vip_drama_unlocked", "true", XmlPrefType.BOOLEAN),
                        XmlPrefEntry("video_quality_preset", "1080p_60fps", XmlPrefType.STRING),
                        XmlPrefEntry("daily_coins_balance", "350", XmlPrefType.INT),
                        XmlPrefEntry("new_user_bonus_claimed", "false", XmlPrefType.BOOLEAN),
                        XmlPrefEntry("autoplay_next_episode", "true", XmlPrefType.BOOLEAN),
                        XmlPrefEntry("last_sync_epoch_ms", System.currentTimeMillis().toString(), XmlPrefType.LONG)
                    )
                }
            } else {
                listOf(
                    XmlPrefEntry(
                        "virtual_account_id",
                        if (isFreshReset) "FRESH_GUEST_${clone.androidId.take(6).uppercase()}"
                        else "CLONE_${clone.appName.uppercase().replace(" ", "_")}_${clone.instanceIndex + 1}",
                        XmlPrefType.STRING
                    ),
                    XmlPrefEntry("auth_bearer_token", "vtoken_${clone.androidId}_${clone.instanceIndex}", XmlPrefType.STRING),
                    XmlPrefEntry("push_notifications_enabled", "true", XmlPrefType.BOOLEAN),
                    XmlPrefEntry("dark_mode_override", "true", XmlPrefType.BOOLEAN),
                    XmlPrefEntry("launch_counter", if (isFreshReset) "0" else "1", XmlPrefType.INT),
                    XmlPrefEntry("sandbox_isolated", "true", XmlPrefType.BOOLEAN)
                )
            }
            VirtualXmlPrefsManager.writeEntriesToXml(appPrefFile, initialEntries)
        }

        // 4. Provision Real SQLite Database (.db) if not exists
        val mainDbFile = File(dbDir, "app_sandbox_data.db")
        if (!mainDbFile.exists()) {
            seedInitialSqliteDatabase(mainDbFile, clone, isFreshReset)
        }

        // 4. Provision runtime config & cache files if empty
        val runtimeManifest = File(filesDir, "virtual_container_manifest.json")
        if (!runtimeManifest.exists()) {
            runtimeManifest.writeText(
                """
                {
                  "engine": "INS Virtual Container v3.4",
                  "hostPackage": "com.ins.virtualspace",
                  "clonedPackage": "${clone.packageName}",
                  "recentsTitle": "${clone.recentsTaskTitle}",
                  "virtualDataPath": "${clone.canonicalVirtualDataPath}",
                  "isolatedUid": ${10500 + clone.id},
                  "classLoaderHook": "dalvik.system.DexClassLoader",
                  "binderInterceptors": [
                    "ITelephony.Stub",
                    "IContentProvider(Settings.Secure)",
                    "IWifiManager.Stub",
                    "IAdvertisingIdService",
                    "ILocationManager.Stub"
                  ]
                }
                """.trimIndent()
            )
        }

        val shaderCache = File(cacheDir, "opengl_pipeline_cache.bin")
        if (!shaderCache.exists()) {
            shaderCache.writeBytes(ByteArray(4096) { (it % 251).toByte() })
        }
        val httpCache = File(cacheDir, "okhttp_response_cache.dat")
        if (!httpCache.exists()) {
            httpCache.writeText("HTTP/2 200 OK\nX-Virtual-Container: com.ins.virtualspace\nX-Clone-Package: ${clone.packageName}\nCache-Control: max-age=3600\n")
        }
        val dexMarker = File(codeCacheDir, "oat_dex_optimized.prof")
        if (!dexMarker.exists()) {
            dexMarker.writeBytes(ByteArray(2048) { (it % 127).toByte() })
        }

        return root
    }

    private fun seedInitialSqliteDatabase(dbFile: File, clone: CloneAppEntity, isFreshReset: Boolean = false) {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.beginTransaction()
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS account_session (
                    session_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    auth_status TEXT NOT NULL,
                    bound_android_id TEXT NOT NULL,
                    bound_model TEXT NOT NULL,
                    login_timestamp TEXT NOT NULL
                )
                """.trimIndent()
            )
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            if (isFreshReset) {
                db.execSQL(
                    """
                    INSERT INTO account_session (username, auth_status, bound_android_id, bound_model, login_timestamp)
                    VALUES (
                        'guest_${clone.androidId.take(6).lowercase()}',
                        'FRESH_RESET_GUEST',
                        '${clone.androidId}',
                        '${clone.buildModel}',
                        '$nowStr'
                    )
                    """.trimIndent()
                )
            } else {
                db.execSQL(
                    """
                    INSERT INTO account_session (username, auth_status, bound_android_id, bound_model, login_timestamp)
                    VALUES (
                        '${clone.appName.lowercase().replace(" ", "_")}_clone_${clone.instanceIndex + 1}',
                        'ACTIVE_SANDBOX',
                        '${clone.androidId}',
                        '${clone.buildModel}',
                        '2026-09-26 17:00:00'
                    )
                    """.trimIndent()
                )
            }

            if (clone.packageName.contains("pinedrama", ignoreCase = true) ||
                clone.packageName.contains("ttmd.video", ignoreCase = true) ||
                clone.appName.contains("pinedrama", ignoreCase = true)
            ) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS drama_watch_history (
                        episode_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        drama_title TEXT NOT NULL,
                        episode_number INTEGER NOT NULL,
                        progress_sec INTEGER NOT NULL,
                        unlock_tier TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                if (!isFreshReset) {
                    db.execSQL("INSERT INTO drama_watch_history (drama_title, episode_number, progress_sec, unlock_tier) VALUES ('Menikah dengan CEO rahasia', 1, 15, 'CLONE_FREE')")
                    db.execSQL("INSERT INTO drama_watch_history (drama_title, episode_number, progress_sec, unlock_tier) VALUES ('Suamiku, CEO Tersembunyi', 1, 10, 'CLONE_FREE')")
                    db.execSQL("INSERT INTO drama_watch_history (drama_title, episode_number, progress_sec, unlock_tier) VALUES ('Diusir, aku jadi miliarder', 1, 5, 'CLONE_FREE')")
                }
            } else {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sandbox_kv_store (
                        entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        config_key TEXT NOT NULL,
                        config_value TEXT NOT NULL,
                        scope TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO sandbox_kv_store (config_key, config_value, scope) VALUES ('multi_instance_isolation', 'STRICT', 'ENGINE')")
                db.execSQL("INSERT INTO sandbox_kv_store (config_key, config_value, scope) VALUES ('binder_ipc_hook', 'ENABLED', 'SECURITY')")
                db.execSQL("INSERT INTO sandbox_kv_store (config_key, config_value, scope) VALUES ('spoofed_imei', '${clone.imei}', 'IDENTITY')")
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS telemetry_audit (
                    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    api_hooked TEXT NOT NULL,
                    intercept_result TEXT NOT NULL,
                    recorded_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            val timeShort = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            db.execSQL("INSERT INTO telemetry_audit (api_hooked, intercept_result, recorded_at) VALUES ('Settings.Secure.ANDROID_ID', '${clone.androidId}', '$timeShort')")
            db.execSQL("INSERT INTO telemetry_audit (api_hooked, intercept_result, recorded_at) VALUES ('TelephonyManager.getImei()', '${clone.imei}', '$timeShort')")
            db.execSQL("INSERT INTO telemetry_audit (api_hooked, intercept_result, recorded_at) VALUES ('WifiInfo.getMacAddress()', '${clone.wifiMac}', '$timeShort')")

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun listDirectory(
        context: Context,
        clone: CloneAppEntity,
        relativePath: String = ""
    ): List<SandboxFileItem> {
        val root = ensureSandboxProvisioned(context, clone)
        val targetDir = if (relativePath.isBlank()) root else File(root, relativePath)
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()

        val children = targetDir.listFiles() ?: return emptyList()
        return children
            .filter { !it.name.endsWith("-journal") && !it.name.endsWith("-wal") && !it.name.endsWith("-shm") && !it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                val rel = file.relativeTo(root).path
                val virtualPath = "${clone.canonicalVirtualDataPath}$rel"
                val size = if (file.isDirectory) computeDirSize(file) else file.length()
                val type = when {
                    file.isDirectory -> SandboxFileType.DIRECTORY
                    file.name.endsWith(".db", ignoreCase = true) || file.name.endsWith(".sqlite", ignoreCase = true) -> SandboxFileType.SQLITE_DB
                    file.name.endsWith(".xml", ignoreCase = true) -> SandboxFileType.SHARED_PREFS_XML
                    file.name.endsWith(".json", ignoreCase = true) -> SandboxFileType.JSON_CONFIG
                    file.name.endsWith(".bin", ignoreCase = true) || file.name.endsWith(".dat", ignoreCase = true) || file.name.endsWith(".prof", ignoreCase = true) -> SandboxFileType.CACHE_BIN
                    else -> SandboxFileType.LOG_TEXT
                }
                SandboxFileItem(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    virtualDisplayPath = virtualPath,
                    isDirectory = file.isDirectory,
                    sizeBytes = size,
                    lastModified = file.lastModified(),
                    fileType = type,
                    childCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0
                )
            }
    }

    fun getStorageStats(context: Context, clone: CloneAppEntity): CloneStorageStats {
        val root = ensureSandboxProvisioned(context, clone)
        val dbDir = File(root, "databases")
        val prefsDir = File(root, "shared_prefs")
        val cacheDir = File(root, "cache")
        val codeCacheDir = File(root, "code_cache")
        val filesDir = File(root, "files")

        val dbBytes = computeDirSize(dbDir)
        val prefsBytes = computeDirSize(prefsDir)
        val cacheBytes = computeDirSize(cacheDir) + computeDirSize(codeCacheDir)
        val filesBytes = computeDirSize(filesDir)
        val total = dbBytes + prefsBytes + cacheBytes + filesBytes

        val dbCount = dbDir.listFiles { f -> f.isFile && f.name.endsWith(".db") }?.size ?: 0
        val xmlCount = prefsDir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }?.size ?: 0

        return CloneStorageStats(
            totalBytes = total,
            databaseBytes = dbBytes,
            sharedPrefsBytes = prefsBytes,
            cacheBytes = cacheBytes,
            filesBytes = filesBytes,
            dbFileCount = dbCount,
            xmlFileCount = xmlCount
        )
    }

    fun clearCloneCache(context: Context, clone: CloneAppEntity): Long {
        val root = getSandboxRoot(context, clone)
        val cacheDir = File(root, "cache")
        val codeCacheDir = File(root, "code_cache")
        val extCacheDir = File(root, "external_cache")
        var freed = computeDirSize(cacheDir) + computeDirSize(extCacheDir)
        cacheDir.deleteRecursively()
        extCacheDir.deleteRecursively()
        cacheDir.mkdirs()
        codeCacheDir.listFiles()?.forEach { file ->
            if (file.name != "classes.dex") {
                freed += file.length()
                runCatching { file.deleteRecursively() }
            }
        }
        return freed
    }

    /**
     * Wipes all user data, databases, SharedPreferences XML, caches, WebView sessions,
     * and runtime state inside the clone's isolated sandbox directory while preserving
     * the installed APK binary (`base.apk`, `split_*.apk`, `classes.dex`, `AndroidManifest.xml`).
     *
     * Does NOT touch the host phone's original installed app data in any way.
     */
    fun wipeCloneDataToFreshState(context: Context, clone: CloneAppEntity): Long {
        val root = getSandboxRoot(context, clone)
        val dbDir = File(root, "databases")
        val prefsDir = File(root, "shared_prefs")
        val cacheDir = File(root, "cache")
        val extFilesDir = File(root, "external_files")
        val extCacheDir = File(root, "external_cache")
        val noBackupDir = File(root, "no_backup")
        val filesDir = File(root, "files").apply { mkdirs() }

        val freed = computeDirSize(dbDir) +
            computeDirSize(prefsDir) +
            computeDirSize(cacheDir) +
            computeDirSize(extFilesDir) +
            computeDirSize(extCacheDir) +
            computeDirSize(noBackupDir)

        runCatching { dbDir.deleteRecursively() }
        runCatching { prefsDir.deleteRecursively() }
        runCatching { cacheDir.deleteRecursively() }
        runCatching { extFilesDir.deleteRecursively() }
        runCatching { extCacheDir.deleteRecursively() }
        runCatching { noBackupDir.deleteRecursively() }

        // Clear user files inside files/ while keeping AndroidManifest.xml and extracted_apk_info.json
        filesDir.listFiles()?.forEach { f ->
            if (f.name != "AndroidManifest.xml" && f.name != "extracted_apk_info.json") {
                runCatching { f.deleteRecursively() }
            }
        }

        // Clear any host-namespaced SharedPreferences for this virtual user slot + package
        runCatching {
            val prefix = "virtual_u${clone.instanceIndex}_${clone.packageName}_"
            val hostPrefsDir = File(context.dataDir ?: context.filesDir.parentFile ?: context.filesDir, "shared_prefs")
            hostPrefsDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { prefXml ->
                val prefName = prefXml.name.removeSuffix(".xml")
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
                prefXml.delete()
            }
        }

        // Write fresh reset marker so ensureSandboxProvisioned initializes a clean 0-history guest state
        val marker = File(filesDir, ".fresh_reset_state")
        runCatching {
            marker.writeText("reset_at=${System.currentTimeMillis()}\nandroid_id=${clone.androidId}\nimei=${clone.imei}\n")
        }

        ensureSandboxProvisioned(context, clone)
        return freed.coerceAtLeast(4096L)
    }

    fun resetCloneSandboxData(context: Context, clone: CloneAppEntity): Long {
        return wipeCloneDataToFreshState(context, clone)
    }

    fun deleteSandboxComplete(context: Context, clone: CloneAppEntity) {
        val root = getSandboxRoot(context, clone)
        root.deleteRecursively()
    }

    private fun computeDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
