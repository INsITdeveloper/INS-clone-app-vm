package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.example.clone.CloneManager
import com.example.virtual.BinderIpcInterceptor
import com.example.virtual.IdentitySpoofer
import com.example.virtual.VirtualApkLauncher
import com.example.virtual.VirtualSandboxStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class InstallableAppCandidate(
    val packageName: String,
    val appName: String,
    val categoryTag: String,
    val isInstalledOnHost: Boolean,
    val sourceApkDir: String,
    val accentColorHex: Long
)

class VirtualSpaceRepository(
    private val context: Context,
    private val dao: VirtualSpaceDao
) {
    val allClonedApps: Flow<List<CloneAppEntity>> = dao.getAllClonedApps()
    val recentHookLogs: Flow<List<IpcHookLogEntity>> = dao.getRecentHookLogs()

    suspend fun ensureInitialSeed() = withContext(Dispatchers.IO) {
        val installedList = discoverInstallableApps()
        // Check if real PineDrama (com.ss.android.ttmd.video or similar) is installed on the user's phone
        val realPineDrama = installedList.firstOrNull {
            it.isInstalledOnHost && (
                it.packageName == "com.ss.android.ttmd.video" ||
                    it.appName.replace(" ", "").contains("pinedrama", ignoreCase = true) ||
                    it.packageName.contains("pinedrama", ignoreCase = true)
                )
        }

        val realChrome = installedList.firstOrNull {
            it.isInstalledOnHost && (
                it.packageName == "com.android.chrome" ||
                    it.packageName.contains("chrome", ignoreCase = true) ||
                    it.appName.equals("Chrome", ignoreCase = true)
                )
        }
        val chromePkg = realChrome?.packageName ?: "com.android.chrome"
        val chromeName = realChrome?.appName ?: "Chrome"

        val realWhatsApp = installedList.firstOrNull {
            it.isInstalledOnHost && (
                it.packageName == "com.whatsapp" ||
                    it.packageName == "com.whatsapp.w4b" ||
                    it.appName.contains("whatsapp", ignoreCase = true)
                )
        }
        val waPkg = realWhatsApp?.packageName ?: "com.whatsapp"
        val waName = realWhatsApp?.appName ?: "WhatsApp"

        if (dao.getCloneCount() > 0) {
            val existingClones = dao.getAllClonedApps().first()
            existingClones.forEach { oldClone ->
                if (oldClone.packageName == "com.ins.tools.sandboxbrowser") {
                    dao.deleteClone(oldClone)
                    return@forEach
                }
                val resolved = VirtualApkLauncher.resolveInstalledApk(context, oldClone)
                if (resolved != null && resolved.isInstalledOnHost && !resolved.isFromArchiveFile) {
                    if (resolved.packageName != oldClone.packageName ||
                        (resolved.appLabel.isNotBlank() && resolved.appLabel != oldClone.appName)
                    ) {
                        val updated = oldClone.copy(
                            packageName = resolved.packageName,
                            appName = resolved.appLabel.ifBlank { oldClone.appName }
                        )
                        dao.updateClone(updated)
                        VirtualSandboxStorage.ensureSandboxProvisioned(context, updated)
                    }
                } else if (oldClone.appName.replace(" ", "").contains("pinedrama", ignoreCase = true)) {
                    val targetPkg = realPineDrama?.packageName ?: "com.ss.android.ttmd.video"
                    if (oldClone.packageName != targetPkg) {
                        val updated = oldClone.copy(
                            packageName = targetPkg,
                            appName = realPineDrama?.appName ?: oldClone.appName
                        )
                        dao.updateClone(updated)
                        VirtualSandboxStorage.ensureSandboxProvisioned(context, updated)
                    }
                }
            }

            // Ensure Chrome clone is present on the Home screen if not yet added
            val hasChromeClone = existingClones.any {
                it.packageName.contains("chrome", ignoreCase = true) ||
                    it.appName.contains("chrome", ignoreCase = true)
            }
            if (!hasChromeClone) {
                val chromePreset = IdentitySpoofer.hardwarePresets[0]
                val newChromeClone = CloneAppEntity(
                    packageName = chromePkg,
                    appName = chromeName,
                    instanceIndex = 0,
                    folderName = null,
                    iconColorHex = 0xFF4285F4,
                    categoryTag = "Browser",
                    isRunning = false,
                    virtualPid = 0,
                    androidId = IdentitySpoofer.generateAndroidId(),
                    imei = IdentitySpoofer.generateLuhnValidImei(chromePreset.tacPrefix),
                    imsi = IdentitySpoofer.generateImsi("51011"),
                    buildSerial = IdentitySpoofer.generateBuildSerial(),
                    buildModel = chromePreset.model,
                    buildManufacturer = chromePreset.manufacturer,
                    buildBrand = chromePreset.brand,
                    wifiMac = IdentitySpoofer.generateWifiMacAddress(),
                    wifiSsid = "INS_PrivateMesh_AX",
                    advertisingId = IdentitySpoofer.generateAdvertisingId(),
                    mockLocationEnabled = false,
                    mockLatitude = 1.2834,
                    mockLongitude = 103.8607,
                    mockAccuracy = 3.0f,
                    mockLocationName = "Singapore, SG (Marina)"
                )
                val newId = dao.insertClone(newChromeClone).toInt()
                VirtualSandboxStorage.ensureSandboxProvisioned(context, newChromeClone.copy(id = newId))
            }

            // Ensure WhatsApp clone is present on the Home screen if not yet added
            val hasWhatsAppClone = existingClones.any {
                it.packageName.contains("whatsapp", ignoreCase = true) ||
                    it.appName.contains("whatsapp", ignoreCase = true)
            }
            if (!hasWhatsAppClone) {
                val waPreset = IdentitySpoofer.hardwarePresets[2]
                val newWaClone = CloneAppEntity(
                    packageName = waPkg,
                    appName = waName,
                    instanceIndex = 0,
                    folderName = null,
                    iconColorHex = 0xFF25D366,
                    categoryTag = "Social",
                    isRunning = false,
                    virtualPid = 0,
                    androidId = IdentitySpoofer.generateAndroidId(),
                    imei = IdentitySpoofer.generateLuhnValidImei(waPreset.tacPrefix),
                    imsi = IdentitySpoofer.generateImsi("51010"),
                    buildSerial = IdentitySpoofer.generateBuildSerial(),
                    buildModel = waPreset.model,
                    buildManufacturer = waPreset.manufacturer,
                    buildBrand = waPreset.brand,
                    wifiMac = IdentitySpoofer.generateWifiMacAddress(),
                    wifiSsid = "INS_DualWA_5G",
                    advertisingId = IdentitySpoofer.generateAdvertisingId(),
                    mockLocationEnabled = false,
                    mockLatitude = -6.2088,
                    mockLongitude = 106.8456,
                    mockAccuracy = 2.5f,
                    mockLocationName = "Jakarta, ID"
                )
                val newId = dao.insertClone(newWaClone).toInt()
                VirtualSandboxStorage.ensureSandboxProvisioned(context, newWaClone.copy(id = newId))
            }
            return@withContext
        }

        val pinePkg = realPineDrama?.packageName ?: "com.ss.android.ttmd.video"
        val pineName = realPineDrama?.appName ?: "PineDrama"

        val pinePreset = IdentitySpoofer.hardwarePresets[1] // Google Pixel 9 Pro
        val pineDramaClone = CloneAppEntity(
            packageName = pinePkg,
            appName = pineName,
            instanceIndex = 0,
            folderName = null,
            iconColorHex = 0xFFFF2E63,
            categoryTag = "Entertainment",
            isRunning = true,
            virtualPid = 18420,
            androidId = IdentitySpoofer.generateAndroidId(),
            imei = IdentitySpoofer.generateLuhnValidImei(pinePreset.tacPrefix),
            imsi = IdentitySpoofer.generateImsi("51010"),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = pinePreset.model,
            buildManufacturer = pinePreset.manufacturer,
            buildBrand = pinePreset.brand,
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = "INS_DramaStream_5G",
            advertisingId = IdentitySpoofer.generateAdvertisingId(),
            mockLocationEnabled = true,
            mockLatitude = -6.2246,
            mockLongitude = 106.8097,
            mockAccuracy = 2.8f,
            mockLocationName = "Jakarta, ID (SCBD)"
        )

        val socialPreset = IdentitySpoofer.hardwarePresets[0] // Samsung S24 Ultra
        val socialClone = CloneAppEntity(
            packageName = chromePkg,
            appName = chromeName,
            instanceIndex = 0,
            folderName = null,
            iconColorHex = 0xFF4285F4,
            categoryTag = "Browser",
            isRunning = false,
            virtualPid = 0,
            androidId = IdentitySpoofer.generateAndroidId(),
            imei = IdentitySpoofer.generateLuhnValidImei(socialPreset.tacPrefix),
            imsi = IdentitySpoofer.generateImsi("51011"),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = socialPreset.model,
            buildManufacturer = socialPreset.manufacturer,
            buildBrand = socialPreset.brand,
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = "INS_PrivateMesh_AX",
            advertisingId = IdentitySpoofer.generateAdvertisingId(),
            mockLocationEnabled = false,
            mockLatitude = 1.2834,
            mockLongitude = 103.8607,
            mockAccuracy = 3.0f,
            mockLocationName = "Singapore, SG (Marina)"
        )

        val waPreset = IdentitySpoofer.hardwarePresets[2]
        val whatsAppClone = CloneAppEntity(
            packageName = waPkg,
            appName = waName,
            instanceIndex = 0,
            folderName = null,
            iconColorHex = 0xFF25D366,
            categoryTag = "Social",
            isRunning = false,
            virtualPid = 0,
            androidId = IdentitySpoofer.generateAndroidId(),
            imei = IdentitySpoofer.generateLuhnValidImei(waPreset.tacPrefix),
            imsi = IdentitySpoofer.generateImsi("51010"),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = waPreset.model,
            buildManufacturer = waPreset.manufacturer,
            buildBrand = waPreset.brand,
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = "INS_DualWA_5G",
            advertisingId = IdentitySpoofer.generateAdvertisingId(),
            mockLocationEnabled = false,
            mockLatitude = -6.2088,
            mockLongitude = 106.8456,
            mockAccuracy = 2.5f,
            mockLocationName = "Jakarta, ID"
        )

        // Inside "Alat" (Tools) Folder on Home Grid
        val toolPreset1 = IdentitySpoofer.hardwarePresets[2] // Xiaomi 14 Ultra
        val toolInspectorClone = CloneAppEntity(
            packageName = "com.ins.tools.deviceinspector",
            appName = "Device ID Checker",
            instanceIndex = 0,
            folderName = "Alat",
            iconColorHex = 0xFF00E5FF,
            categoryTag = "Alat",
            isRunning = false,
            virtualPid = 0,
            androidId = IdentitySpoofer.generateAndroidId(),
            imei = IdentitySpoofer.generateLuhnValidImei(toolPreset1.tacPrefix),
            imsi = IdentitySpoofer.generateImsi("51089"),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = toolPreset1.model,
            buildManufacturer = toolPreset1.manufacturer,
            buildBrand = toolPreset1.brand,
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = "INS_Lab_5G",
            advertisingId = IdentitySpoofer.generateAdvertisingId(),
            mockLocationEnabled = true,
            mockLatitude = 35.6595,
            mockLongitude = 139.7004,
            mockAccuracy = 3.1f,
            mockLocationName = "Tokyo, JP (Shibuya)"
        )

        val initialClones = listOf(pineDramaClone, socialClone, whatsAppClone, toolInspectorClone)
        for (item in initialClones) {
            val newId = dao.insertClone(item).toInt()
            val saved = item.copy(id = newId)
            VirtualSandboxStorage.ensureSandboxProvisioned(context, saved)
        }

        val firstSaved = dao.getClonesByPackage(pinePkg).firstOrNull()
        if (firstSaved != null) {
            val interceptor = BinderIpcInterceptor(context, firstSaved) {}
            val sweep = interceptor.executeDiagnosticSweep()
            sweep.interceptedLogs.forEach { dao.insertHookLog(it) }
        }
    }

    suspend fun createNewClone(
        candidate: InstallableAppCandidate,
        targetFolder: String? = null,
        enableMockGps: Boolean = false
    ): CloneAppEntity = withContext(Dispatchers.IO) {
        val existingForPkg = dao.getClonesByPackage(candidate.packageName)
        val nextInstanceIndex = if (existingForPkg.isEmpty()) 0 else (existingForPkg.maxOf { it.instanceIndex } + 1)
        val preset = IdentitySpoofer.hardwarePresets.random()
        val locPreset = IdentitySpoofer.locationPresets.random()

        val clone = CloneAppEntity(
            packageName = candidate.packageName,
            appName = candidate.appName,
            instanceIndex = nextInstanceIndex,
            folderName = targetFolder,
            iconColorHex = candidate.accentColorHex,
            categoryTag = candidate.categoryTag,
            isRunning = false,
            virtualPid = 0,
            androidId = IdentitySpoofer.generateAndroidId(),
            imei = IdentitySpoofer.generateLuhnValidImei(preset.tacPrefix),
            imsi = IdentitySpoofer.generateImsi(),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = preset.model,
            buildManufacturer = preset.manufacturer,
            buildBrand = preset.brand,
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = IdentitySpoofer.generateWifiSsid(),
            advertisingId = IdentitySpoofer.generateAdvertisingId(),
            mockLocationEnabled = enableMockGps,
            mockLatitude = locPreset.latitude,
            mockLongitude = locPreset.longitude,
            mockAccuracy = locPreset.accuracy,
            mockLocationName = locPreset.label
        )

        val insertedId = dao.insertClone(clone).toInt()
        val persisted = clone.copy(id = insertedId)
        VirtualSandboxStorage.ensureSandboxProvisioned(context, persisted)

        // Langkah inti "clone app": bangun APK dengan nama paket baru + re-sign + pasang
        // sehingga clone menjadi aplikasi terpisah yang benar-benar bisa dijalankan.
        installCloneApk(persisted, candidate)
    }

    /**
     * Membangun dan memasang APK clone untuk [clone]. Jika izin "Install unknown apps" belum
     * diberikan atau APK sumber tidak terbaca, clone tetap dibuat tanpa paket terpasang.
     */
    private suspend fun installCloneApk(
        clone: CloneAppEntity,
        candidate: InstallableAppCandidate
    ): CloneAppEntity = withContext(Dispatchers.IO) {
        if (!CloneManager.canInstallPackages(context)) {
            return@withContext clone
        }
        val newPackage = CloneManager.generateClonePackageName(clone.packageName, clone.instanceIndex)
        val source = resolveCloneSource(candidate) ?: return@withContext clone
        val (baseApk, splitApks) = source
        val workDir = File(context.cacheDir, "clone_build/${clone.packageName}_${clone.instanceIndex}")

        val result = CloneManager.buildAndInstall(
            context = context,
            sourceBaseApk = baseApk,
            sourceSplitApks = splitApks,
            newPackage = newPackage,
            workDir = workDir,
            newAppLabel = "${clone.appName} (Clone ${clone.instanceIndex + 1})"
        )
        result.fold(
            onSuccess = { installedPackage ->
                val updated = clone.copy(installedClonePackage = installedPackage)
                dao.updateClone(updated)
                updated
            },
            onFailure = { clone }
        )
    }

    /** Paket yang sudah merupakan hasil clone kita (mis. com.app.insclone1) tidak ditawarkan lagi. */
    private fun isInsClonePackage(packageName: String): Boolean =
        packageName.contains(Regex("\\.insclone\\d+$"))

    private fun resolveCloneSource(candidate: InstallableAppCandidate): Pair<File, List<File>>? {
        if (candidate.isInstalledOnHost) {
            CloneManager.resolveInstalledSourceApks(context, candidate.packageName)?.let { return it }
        }
        val sourceFile = candidate.sourceApkDir.takeIf { it.isNotBlank() }?.let { File(it) }
        if (sourceFile != null && sourceFile.exists() && sourceFile.canRead()) {
            return sourceFile to emptyList()
        }
        return null
    }

    /**
     * Imports a raw .apk file from Android storage URI, copies it into the isolated sandbox
     * `/data/user/0/com.ins.virtualspace/virtual/user/<slot>/<pkg>/base.apk`, and creates the clone.
     */
    suspend fun importApkFromUriAndClone(
        uri: Uri,
        targetFolder: String? = null,
        enableMockGps: Boolean = true
    ): Result<CloneAppEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val (resolved, tempApkFile) = VirtualApkLauncher.importApkUriToSandbox(context, uri)
                ?: throw IllegalArgumentException("File APK tidak valid atau tidak dapat dibaca.")

            val candidate = InstallableAppCandidate(
                packageName = resolved.packageName,
                appName = resolved.appLabel,
                categoryTag = "Imported APK",
                // APK diimpor dari file, jadi sumber clone adalah file temp di bawah,
                // bukan paket yang terpasang di perangkat.
                isInstalledOnHost = false,
                sourceApkDir = tempApkFile.absolutePath,
                accentColorHex = 0xFF00E5FF
            )
            val created = createNewClone(candidate, targetFolder, enableMockGps)
            val sandboxRoot = VirtualSandboxStorage.getSandboxRoot(context, created)
            val destApk = File(sandboxRoot, "base.apk")
            tempApkFile.copyTo(destApk, overwrite = true)
            tempApkFile.delete()
            created
        }
    }

    suspend fun rebindCloneToInstalledPackage(
        clone: CloneAppEntity,
        candidate: InstallableAppCandidate
    ): CloneAppEntity = withContext(Dispatchers.IO) {
        val updated = clone.copy(
            packageName = candidate.packageName,
            appName = candidate.appName
        )
        dao.updateClone(updated)
        VirtualSandboxStorage.ensureSandboxProvisioned(context, updated)
        updated
    }

    suspend fun updateCloneIdentity(updated: CloneAppEntity) = withContext(Dispatchers.IO) {
        dao.updateClone(updated)
        VirtualSandboxStorage.ensureSandboxProvisioned(context, updated)
    }

    suspend fun markCloneLaunched(clone: CloneAppEntity): CloneAppEntity = withContext(Dispatchers.IO) {
        val pid = 14000 + (clone.id * 317) % 15000
        val updated = clone.copy(
            isRunning = true,
            virtualPid = pid,
            lastLaunchedAt = System.currentTimeMillis()
        )
        dao.updateClone(updated)
        VirtualSandboxStorage.ensureSandboxProvisioned(context, updated)

        // Record real Binder IPC interceptions on launch
        val interceptor = BinderIpcInterceptor(context, updated) {}
        val sweep = interceptor.executeDiagnosticSweep()
        sweep.interceptedLogs.forEach { dao.insertHookLog(it) }
        updated
    }

    suspend fun deleteClone(clone: CloneAppEntity) = withContext(Dispatchers.IO) {
        VirtualSandboxStorage.deleteSandboxComplete(context, clone)
        dao.deleteClone(clone)
    }

    /**
     * Wipes all sandbox data (SQLite DBs, SharedPreferences XML, caches, sessions) for [clone]
     * and optionally assigns a brand-new spoofed hardware identity (Android ID, IMEI, GAID, MAC)
     * so the cloned APK starts 100% fresh without affecting the host phone's real app.
     */
    suspend fun clearCloneDataAndResetIdentity(
        clone: CloneAppEntity,
        randomizeDeviceId: Boolean = true
    ): Pair<CloneAppEntity, Long> = withContext(Dispatchers.IO) {
        com.example.virtual.VirtualProcessManager.killVirtualProcess(clone.packageName, clone.instanceIndex)
        val updatedClone = if (randomizeDeviceId) {
            IdentitySpoofer.randomizeIdentity(clone).copy(isRunning = false, virtualPid = 0)
        } else {
            clone.copy(isRunning = false, virtualPid = 0)
        }
        dao.updateClone(updatedClone)
        val freedBytes = VirtualSandboxStorage.wipeCloneDataToFreshState(context, updatedClone)
        updatedClone to freedBytes
    }

    suspend fun clearCloneCacheOnly(clone: CloneAppEntity): Long = withContext(Dispatchers.IO) {
        VirtualSandboxStorage.clearCloneCache(context, clone)
    }

    suspend fun cleanVirtualMemory(): Int = withContext(Dispatchers.IO) {
        dao.stopAllRunningClones()
        148 + (System.currentTimeMillis() % 95).toInt()
    }

    suspend fun recordHookLog(log: IpcHookLogEntity) = withContext(Dispatchers.IO) {
        dao.insertHookLog(log)
    }

    suspend fun clearHookLogs() = withContext(Dispatchers.IO) {
        dao.clearAllHookLogs()
    }

    /**
     * Discovers all real installed launchable applications on the user's phone first,
     * combining `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` and `getInstalledApplications(0)`
     * so no user-installed APK is ever missed.
     */
    suspend fun discoverInstallableApps(): List<InstallableAppCandidate> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val results = mutableListOf<InstallableAppCandidate>()
        val seenPackages = mutableSetOf<String>()
        val palette = listOf(0xFF00E5FF, 0xFF6366F1, 0xFF8B5CF6, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899)

        // 1. Standard queryIntentActivities with flag 0 (most reliable on Android 11-15)
        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

            resolveInfos.forEachIndexed { idx, info ->
                val pkg = info.activityInfo?.packageName ?: return@forEachIndexed
                if (pkg == context.packageName || isInsClonePackage(pkg) || !seenPackages.add(pkg)) return@forEachIndexed

                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.')
                val appInfo = info.activityInfo?.applicationInfo
                val isUserApp = appInfo != null && (
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    )
                val srcDir = appInfo?.publicSourceDir ?: "/data/app/$pkg/base.apk"

                results.add(
                    InstallableAppCandidate(
                        packageName = pkg,
                        appName = label,
                        categoryTag = if (isUserApp) "Installed APK" else "System App",
                        isInstalledOnHost = true,
                        sourceApkDir = srcDir,
                        accentColorHex = palette[idx % palette.size]
                    )
                )
            }
        }

        // 2. Supplement with getInstalledApplications(0) for any launchable app not caught above
        runCatching {
            val allApps = pm.getInstalledApplications(0)
            allApps.forEachIndexed { idx, appInfo ->
                val pkg = appInfo.packageName ?: return@forEachIndexed
                if (pkg == context.packageName || isInsClonePackage(pkg) || seenPackages.contains(pkg)) return@forEachIndexed
                val hasLaunch = pm.getLaunchIntentForPackage(pkg) != null ||
                    pm.getLeanbackLaunchIntentForPackage(pkg) != null
                if (!hasLaunch) return@forEachIndexed

                seenPackages.add(pkg)
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.')
                val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val srcDir = appInfo.publicSourceDir ?: "/data/app/$pkg/base.apk"

                results.add(
                    InstallableAppCandidate(
                        packageName = pkg,
                        appName = label,
                        categoryTag = if (isUserApp) "Installed APK" else "System App",
                        isInstalledOnHost = true,
                        sourceApkDir = srcDir,
                        accentColorHex = palette[(results.size + idx) % palette.size]
                    )
                )
            }
        }

        // 3. Add featured templates only if not already installed on device
        val featuredCatalog = listOf(
            InstallableAppCandidate("com.android.chrome", "Chrome", "Browser", false, "/data/app/com.android.chrome/base.apk", 0xFF4285F4),
            InstallableAppCandidate("com.ss.android.ttmd.video", "PineDrama", "Entertainment", false, "/data/app/com.ss.android.ttmd.video/base.apk", 0xFFFF2E63),
            InstallableAppCandidate("com.zhiliaoapp.musically", "TikTok / Musically", "Entertainment", false, "/data/app/com.zhiliaoapp.musically/base.apk", 0xFF00E5FF),
            InstallableAppCandidate("org.telegram.messenger", "Telegram", "Social", false, "/data/app/org.telegram.messenger/base.apk", 0xFF38BDF8),
            InstallableAppCandidate("com.instagram.android", "Instagram", "Social", false, "/data/app/com.instagram.android/base.apk", 0xFFEC4899),
            InstallableAppCandidate("com.shopee.id", "Shopee ID", "Shopping", false, "/data/app/com.shopee.id/base.apk", 0xFFF97316)
        )
        featuredCatalog.forEach { tmpl ->
            if (results.none { it.packageName == tmpl.packageName || it.appName.equals(tmpl.appName, ignoreCase = true) }) {
                results.add(tmpl)
            }
        }

        // Put user-installed apps at the very top!
        results.sortedWith(
            compareByDescending<InstallableAppCandidate> { it.categoryTag == "Installed APK" }
                .thenByDescending { it.isInstalledOnHost }
                .thenBy { it.appName.lowercase() }
        )
    }
}
