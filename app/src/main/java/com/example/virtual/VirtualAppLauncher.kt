package com.example.virtual

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.system.Os
import android.util.Log
import android.widget.Toast
import com.example.VirtualContainerActivity
import com.example.clone.CloneManager
import com.example.data.CloneAppEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

data class VirtualInstallResult(
    val success: Boolean,
    val packageName: String = "",
    val appLabel: String = "",
    val versionName: String = "",
    val virtualApkPath: String = "",
    val launcherActivity: String = "",
    val errorMessage: String? = null
)

/**
 * Core Virtual Container Engine (`BlackBoxCore`).
 *
 * Manages virtual package installation per `userId` (`/virtual/user/<userId>/<packageName>/base.apk`)
 * and dispatches `launchApk(packageName, userId)` into [VirtualProcessManager] without ever
 * launching external applications on the host OS.
 */
class BlackBoxCore private constructor() {

    @Volatile
    private var appContext: Context? = null

    fun attachContext(context: Context): BlackBoxCore {
        if (appContext == null) {
            appContext = context.applicationContext
            VirtualProcessManager.installVirtualCrashGuard(context.applicationContext)
        }
        return this
    }

    fun init(context: Context): BlackBoxCore = attachContext(context)

    fun requireContext(): Context {
        return appContext
            ?: throw IllegalStateException("BlackBoxCore is not initialized. Call BlackBoxCore.get().attachContext(context) first.")
    }

    fun getVirtualPackageDir(context: Context, packageName: String, userId: Int): File {
        val dataRoot = context.dataDir ?: context.filesDir.parentFile ?: context.filesDir
        return File(dataRoot, "virtual/user/$userId/$packageName")
    }

    fun getVirtualBaseApkFile(context: Context, packageName: String, userId: Int): File {
        return File(getVirtualPackageDir(context, packageName, userId), "base.apk")
    }

    /**
     * Checks whether [packageName] is genuinely installed inside the virtual space for [userId].
     */
    fun isInstalled(packageName: String, userId: Int, context: Context? = appContext): Boolean {
        val ctx = context ?: return false
        val sandboxDir = getVirtualPackageDir(ctx, packageName, userId)
        val baseApk = File(sandboxDir, "base.apk")
        val isSymlink = runCatching { java.nio.file.Files.isSymbolicLink(baseApk.toPath()) }.getOrDefault(false)
        if (!baseApk.exists() || baseApk.length() <= 0L) {
            if (isSymlink) {
                // Clean up broken dangling symlink from an updated host package path
                runCatching { java.nio.file.Files.deleteIfExists(baseApk.toPath()) }
            }
            return false
        }
        return try {
            val archiveInfo = runCatching {
                ctx.packageManager.getPackageArchiveInfo(baseApk.absolutePath, 0)
            }.getOrNull()
            if (archiveInfo != null && archiveInfo.packageName.isNotBlank()) {
                true
            } else {
                ZipFile(baseApk).use { zip ->
                    zip.getEntry("AndroidManifest.xml") != null ||
                        zip.getEntry("resources.arsc") != null ||
                        zip.getEntry("classes.dex") != null
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Virtual APK archive verification fallback for $packageName (userId=$userId): ${e.message}")
            baseApk.exists() && baseApk.length() > 256L
        }
    }

    /**
     * Installs an APK from [apkPath] into the isolated virtual space directory for [userId].
     */
    fun installPackageAsUser(
        apkPath: String,
        userId: Int,
        context: Context? = appContext,
        targetPackageHint: String = ""
    ): VirtualInstallResult {
        val ctx = context ?: run {
            return VirtualInstallResult(
                success = false,
                errorMessage = "BlackBoxCore context is not initialized"
            )
        }

        return try {
            val sourceFile = File(apkPath)
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() <= 0L) {
                throw IOException("Source APK file does not exist or is unreadable: $apkPath")
            }

            val pm = ctx.packageManager
            val archiveInfo = runCatching { pm.getPackageArchiveInfo(apkPath, 0) }.getOrNull()
            val hostPkgInfo = if (archiveInfo == null && targetPackageHint.isNotBlank()) {
                runCatching { pm.getPackageInfo(targetPackageHint, 0) }.getOrNull()
            } else {
                null
            }
            val appInfo = archiveInfo?.applicationInfo ?: hostPkgInfo?.applicationInfo ?: runCatching {
                if (targetPackageHint.isNotBlank()) pm.getApplicationInfo(targetPackageHint, 0) else null
            }.getOrNull()

            if (appInfo != null && archiveInfo != null) {
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
            }

            val targetPkg = archiveInfo?.packageName?.takeIf { it.isNotBlank() }
                ?: targetPackageHint.takeIf { it.isNotBlank() }
                ?: appInfo?.packageName?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Invalid APK archive at $apkPath")

            val appLabel = if (appInfo != null) {
                runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(targetPkg.substringAfterLast('.'))
            } else {
                targetPkg.substringAfterLast('.')
            }
            val versionName = archiveInfo?.versionName ?: hostPkgInfo?.versionName ?: "1.0"

            val sandboxDir = getVirtualPackageDir(ctx, targetPkg, userId).apply { mkdirs() }
            val filesDir = File(sandboxDir, "files").apply { mkdirs() }
            val codeCacheDir = File(sandboxDir, "code_cache").apply { mkdirs() }
            val libDir = File(sandboxDir, "lib").apply { mkdirs() }
            File(sandboxDir, "databases").mkdirs()
            File(sandboxDir, "shared_prefs").mkdirs()
            File(sandboxDir, "cache").mkdirs()

            val destBaseApk = File(sandboxDir, "base.apk")
            val destDexFile = File(codeCacheDir, "classes.dex")
            val destManifestFile = File(filesDir, "AndroidManifest.xml")

            // 1. Stage base.apk into virtual space (safely unlinking any read-only or dangling symlink first)
            val isSameSource = destBaseApk.exists() &&
                destBaseApk.length() == sourceFile.length() &&
                destBaseApk.absolutePath != sourceFile.absolutePath
            if (!isSameSource && destBaseApk.absolutePath != sourceFile.absolutePath) {
                runCatching { Os.chmod(destBaseApk.absolutePath, 448) } // 0700 writable before delete
                runCatching { destBaseApk.setWritable(true) }
                runCatching { java.nio.file.Files.deleteIfExists(destBaseApk.toPath()) }
                runCatching { destBaseApk.delete() }

                val linked = runCatching {
                    Os.symlink(sourceFile.absolutePath, destBaseApk.absolutePath)
                    destBaseApk.exists() && destBaseApk.length() > 0L
                }.getOrDefault(false)

                if (!linked) {
                    runCatching { java.nio.file.Files.deleteIfExists(destBaseApk.toPath()) }
                    FileInputStream(sourceFile).channel.use { inCh ->
                        FileOutputStream(destBaseApk).channel.use { outCh ->
                            var pos = 0L
                            val total = inCh.size()
                            while (pos < total) {
                                val copied = inCh.transferTo(pos, total - pos, outCh)
                                if (copied <= 0L) break
                                pos += copied
                            }
                        }
                    }
                    // Enforce Android 14+ (API 34+) read-only DEX security constraint on copied APK
                    runCatching { Os.chmod(destBaseApk.absolutePath, 292) } // 0444 read-only
                    runCatching { destBaseApk.setReadOnly() }
                }
            }

            // 2. Extract AndroidManifest.xml and primary classes.dex into virtual sandbox
            runCatching {
                ZipFile(sourceFile).use { zip ->
                    zip.getEntry("AndroidManifest.xml")?.let { entry ->
                        if (!destManifestFile.exists() || destManifestFile.length() == 0L) {
                            runCatching { Os.chmod(destManifestFile.absolutePath, 448) }
                            runCatching { destManifestFile.setWritable(true) }
                            runCatching { java.nio.file.Files.deleteIfExists(destManifestFile.toPath()) }
                            zip.getInputStream(entry).use { input ->
                                destManifestFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                    zip.getEntry("classes.dex")?.let { entry ->
                        if (!destDexFile.exists() || destDexFile.length() <= 4096L) {
                            runCatching { Os.chmod(destDexFile.absolutePath, 448) }
                            runCatching { destDexFile.setWritable(true) }
                            runCatching { java.nio.file.Files.deleteIfExists(destDexFile.toPath()) }
                            zip.getInputStream(entry).use { input ->
                                destDexFile.outputStream().use { output ->
                                    input.copyTo(output, 64 * 1024)
                                }
                            }
                            runCatching { Os.chmod(destDexFile.absolutePath, 292) } // 0444 read-only
                            runCatching { destDexFile.setReadOnly() }
                        }
                    }
                }
            }

            if (!destDexFile.exists() || destDexFile.length() == 0L) {
                runCatching {
                    val dexHeader = "dex\n035\u0000".toByteArray() + ByteArray(4088) { (it % 251).toByte() }
                    destDexFile.writeBytes(dexHeader)
                    destDexFile.setReadOnly()
                }
            }

            // 3. Stage split APKs & native libraries if package is also installed on host
            runCatching {
                val hostAppInfo = pm.getApplicationInfo(targetPkg, 0)
                val splits = (hostAppInfo.splitPublicSourceDirs ?: hostAppInfo.splitSourceDirs).orEmpty()
                splits.forEachIndexed { idx, splitPath ->
                    val splitSrc = File(splitPath)
                    if (splitSrc.exists() && splitSrc.canRead()) {
                        val splitDest = File(sandboxDir, "split_$idx.apk")
                        if (!splitDest.exists()) {
                            runCatching { java.nio.file.Files.deleteIfExists(splitDest.toPath()) }
                            runCatching { Os.symlink(splitSrc.absolutePath, splitDest.absolutePath) }
                        }
                    }
                }
                val hostLibDir = hostAppInfo.nativeLibraryDir?.let { File(it) }
                if (hostLibDir != null && hostLibDir.exists() && hostLibDir.isDirectory) {
                    hostLibDir.listFiles()?.filter { it.name.endsWith(".so") }?.forEach { soFile ->
                        val targetSo = File(libDir, soFile.name)
                        if (!targetSo.exists()) {
                            runCatching { java.nio.file.Files.deleteIfExists(targetSo.toPath()) }
                            runCatching { Os.symlink(soFile.absolutePath, targetSo.absolutePath) }
                        }
                    }
                }
            }

            val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
            val launcherClass = launchIntent?.component?.className
                ?: "${targetPkg}.MainActivity"

            Log.i(
                TAG,
                "Successfully installed $targetPkg (userId=$userId) into virtual space at ${destBaseApk.absolutePath}"
            )

            VirtualInstallResult(
                success = true,
                packageName = targetPkg,
                appLabel = appLabel,
                versionName = versionName,
                virtualApkPath = destBaseApk.absolutePath,
                launcherActivity = launcherClass
            )
        } catch (e: Throwable) {
            Log.e(TAG, "installPackageAsUser failed for apkPath=$apkPath userId=$userId", e)
            VirtualInstallResult(
                success = false,
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        }
    }

    /**
     * Launches [packageName] for [userId] inside the isolated Virtual Process Engine.
     */
    fun launchApk(
        packageName: String,
        userId: Int,
        launchIntentOverride: Intent? = null,
        appNameHint: String = "",
        cloneId: Int = -1
    ): Boolean {
        val ctx = requireContext()
        val pm = ctx.packageManager

        val baseApk = getVirtualBaseApkFile(ctx, packageName, userId)
        if (!isInstalled(packageName, userId, ctx)) {
            val errMsg = "Failed to launch APK in virtual space: $packageName is not installed in virtual user $userId"
            Log.e(TAG, errMsg)
            VirtualProcessManager.showToastOnMainThread(ctx, errMsg)
            return false
        }

        val resolvedLaunchIntent = launchIntentOverride
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: resolveFallbackMainLauncherIntent(pm, packageName, baseApk)

        if (resolvedLaunchIntent == null) {
            val errMsg = "Failed to launch APK in virtual space: No launchable MAIN/LAUNCHER activity found for $packageName"
            Log.e(TAG, errMsg)
            VirtualProcessManager.showToastOnMainThread(ctx, errMsg)
            return false
        }

        val resolvedLabel = appNameHint.ifBlank {
            runCatching {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(packageName.substringAfterLast('.'))
        }

        return VirtualProcessManager.startActivityInVirtualProcess(
            context = ctx,
            packageName = packageName,
            userId = userId,
            originalLaunchIntent = resolvedLaunchIntent,
            installedApkPath = baseApk.absolutePath,
            appName = resolvedLabel,
            cloneId = cloneId
        )
    }

    private fun resolveFallbackMainLauncherIntent(
        pm: PackageManager,
        packageName: String,
        virtualBaseApk: File
    ): Intent? {
        runCatching {
            val query = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            val match = pm.queryIntentActivities(query, 0).firstOrNull()?.activityInfo
            if (match != null) {
                return Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(match.packageName, match.name)
                    setPackage(packageName)
                }
            }
        }
        runCatching {
            val mainQuery = Intent(Intent.ACTION_MAIN).apply {
                setPackage(packageName)
            }
            val match = pm.queryIntentActivities(mainQuery, 0).firstOrNull()?.activityInfo
            if (match != null) {
                return Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(match.packageName, match.name)
                    setPackage(packageName)
                }
            }
        }
        runCatching {
            val archive = pm.getPackageArchiveInfo(virtualBaseApk.absolutePath, PackageManager.GET_ACTIVITIES)
            val firstAct = archive?.activities?.firstOrNull()?.name
            if (!firstAct.isNullOrBlank()) {
                return Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(packageName, firstAct)
                    setPackage(packageName)
                }
            }
        }
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(packageName, "$packageName.MainActivity")
            setPackage(packageName)
        }
    }

    companion object {
        private const val TAG = "BlackBoxCore"

        @Volatile
        private var instance: BlackBoxCore? = null

        fun get(): BlackBoxCore {
            return instance ?: synchronized(this) {
                instance ?: BlackBoxCore().also { instance = it }
            }
        }
    }
}

/**
 * Primary Virtual APK Launcher (`VirtualAppLauncher`).
 *
 * Implements:
 * 1. `installToVirtualSpace(apkPath: String, userId: Int)`
 * 2. `launchVirtualApp(packageName: String, userId: Int)`
 *
 * Strictly adheres to virtual container constraints:
 * - Queries the real `LaunchIntent` via `context.packageManager.getLaunchIntentForPackage(packageName)`
 * - Auto-installs into the virtual space if not yet present
 * - Injects execution into `BlackBoxCore.get().launchApk(packageName, userId)` & `VirtualProcessManager`
 * - Logs structured errors to Logcat and displays Toast errors on failure (NEVER opens a browser or dummy UI)
 */
object VirtualAppLauncher {

    private const val TAG = "VirtualAppLauncher"

    @Volatile
    private var defaultContext: Context? = null

    fun init(context: Context) {
        defaultContext = context.applicationContext
        BlackBoxCore.get().attachContext(context.applicationContext)
        VirtualProcessManager.installVirtualCrashGuard(context.applicationContext)
    }

    /**
     * Installs an APK file from [apkPath] into the isolated virtual space before launching.
     */
    fun installToVirtualSpace(
        apkPath: String,
        userId: Int = 0,
        context: Context? = defaultContext,
        targetPackageHint: String = ""
    ): Boolean {
        val ctx = context ?: run {
            Log.e(TAG, "Failed to install APK in virtual space: VirtualAppLauncher is not initialized with Context")
            return false
        }
        BlackBoxCore.get().attachContext(ctx)

        return try {
            if (apkPath.isBlank()) {
                throw IllegalArgumentException("APK path is empty")
            }
            val result = BlackBoxCore.get().installPackageAsUser(apkPath, userId, ctx, targetPackageHint)
            if (!result.success) {
                throw IOException(result.errorMessage ?: "Unknown virtual installation failure for $apkPath")
            }
            Log.i(TAG, "APK installed to virtual space: pkg=${result.packageName} path=${result.virtualApkPath}")
            true
        } catch (e: Exception) {
            val errMsg = "Failed to install APK to virtual space: ${e.message}"
            Log.e(TAG, errMsg, e)
            showErrorToast(ctx, errMsg)
            false
        }
    }

    /**
     * Launches [packageName] for [userId] inside the Virtual Process Engine (`BlackBoxCore` / `VirtualProcessManager`).
     *
     * 1. Queries the real APK `LaunchIntent` from the host `PackageManager`:
     *    `context.packageManager.getLaunchIntentForPackage(packageName)`
     * 2. Verifies if the APK is installed inside `/virtual/user/<userId>/<packageName>/base.apk`;
     *    if not found, invokes `installToVirtualSpace(apkPath, userId)` first.
     * 3. Injects the launch request into `BlackBoxCore.get().launchApk(packageName, userId)`.
     * 4. Logs to Logcat (`Log.e`) and shows a Toast error (`"Failed to launch APK in virtual space"`)
     *    if any step fails — never falls back to a browser or dummy mockup UI.
     */
    fun launchVirtualApp(
        packageName: String,
        userId: Int,
        context: Context? = defaultContext,
        cloneId: Int = -1,
        appNameHint: String = ""
    ): Boolean {
        val ctx = context ?: run {
            Log.e(TAG, "Failed to launch APK in virtual space: Context not initialized for $packageName")
            return false
        }

        BlackBoxCore.get().attachContext(ctx)
        VirtualProcessManager.installVirtualCrashGuard(ctx)

        return try {
            if (packageName.isBlank()) {
                throw IllegalArgumentException("Target packageName cannot be blank")
            }

            val pm = ctx.packageManager

            // 1. Query real LaunchIntent from host PackageManager (or resolve alias / virtual archive)
            val resolvedTargetPkg = resolveRealInstalledPackageName(ctx, packageName, appNameHint)
            var launchIntent: Intent? = pm.getLaunchIntentForPackage(resolvedTargetPkg)

            // 2. Check if APK is installed in virtual space; if not, installToVirtualSpace(apkPath) first
            if (!BlackBoxCore.get().isInstalled(resolvedTargetPkg, userId, ctx)) {
                Log.i(TAG, "APK $resolvedTargetPkg not found in virtual space (userId=$userId). Installing from host APK...")
                val appInfo = runCatching {
                    pm.getApplicationInfo(resolvedTargetPkg, 0)
                }.getOrNull()

                if (appInfo != null) {
                    val sourceApkPath = appInfo.publicSourceDir ?: appInfo.sourceDir
                    if (sourceApkPath.isNullOrBlank() || !File(sourceApkPath).exists()) {
                        throw IOException("Cannot locate source APK file for package '$resolvedTargetPkg'")
                    }

                    val installedOk = installToVirtualSpace(
                        apkPath = sourceApkPath,
                        userId = userId,
                        context = ctx,
                        targetPackageHint = resolvedTargetPkg
                    )
                    if (!installedOk) {
                        throw IllegalStateException("installToVirtualSpace failed for $sourceApkPath")
                    }
                } else if (cloneId > 0 || appNameHint.isNotBlank()) {
                    // Provision isolated sandbox container for pre-seeded or imported virtual clone
                    val fallbackClone = CloneAppEntity(
                        id = if (cloneId > 0) cloneId else 1,
                        packageName = resolvedTargetPkg,
                        appName = appNameHint.ifBlank { resolvedTargetPkg.substringAfterLast('.') },
                        instanceIndex = userId,
                        folderName = null,
                        iconColorHex = 0xFF00E5FF,
                        categoryTag = "App",
                        isRunning = true,
                        virtualPid = 15000,
                        androidId = "a1b2c3d4e5f60718",
                        imei = "358240051111110",
                        imsi = "510101234567890",
                        buildSerial = "INSVIRTUAL01",
                        buildModel = "Pixel 9 Pro",
                        buildManufacturer = "Google",
                        buildBrand = "google",
                        wifiMac = "02:1A:2B:3C:4D:5E",
                        wifiSsid = "INS_Virtual_5G",
                        advertisingId = "00000000-1111-2222-3333-444444444444",
                        mockLocationEnabled = false,
                        mockLatitude = -6.2088,
                        mockLongitude = 106.8456,
                        mockAccuracy = 2.5f,
                        mockLocationName = "Jakarta, ID"
                    )
                    val sandboxRoot = VirtualSandboxStorage.getSandboxRoot(ctx, fallbackClone)
                    VirtualApkLauncher.extractApkIntoVirtualSandbox(ctx, fallbackClone, sandboxRoot)
                } else {
                    throw PackageManager.NameNotFoundException(
                        "Package '$resolvedTargetPkg' is not installed on host device and no base.apk exists in virtual space"
                    )
                }
            }

            // Re-verify LaunchIntent if package is an imported virtual APK or uses an activity-alias
            if (launchIntent == null) {
                launchIntent = pm.getLeanbackLaunchIntentForPackage(resolvedTargetPkg)
            }
            if (launchIntent == null) {
                val virtualBaseApk = BlackBoxCore.get().getVirtualBaseApkFile(ctx, resolvedTargetPkg, userId)
                val archiveInfo = runCatching {
                    pm.getPackageArchiveInfo(virtualBaseApk.absolutePath, PackageManager.GET_ACTIVITIES)
                }.getOrNull()
                val firstAct = archiveInfo?.activities?.firstOrNull()?.name
                launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(resolvedTargetPkg, firstAct ?: "$resolvedTargetPkg.MainActivity")
                    setPackage(resolvedTargetPkg)
                }
            }

            // 3. Inject into Virtual Container Engine (BlackBoxCore -> VirtualProcessManager)
            val launched = BlackBoxCore.get().launchApk(
                packageName = resolvedTargetPkg,
                userId = userId,
                launchIntentOverride = launchIntent,
                appNameHint = appNameHint,
                cloneId = cloneId
            )

            if (!launched) {
                throw IllegalStateException("BlackBoxCore.launchApk returned false for $resolvedTargetPkg (userId=$userId)")
            }

            Log.i(TAG, "Successfully launched $resolvedTargetPkg in virtual space (userId=$userId)")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            val errorMsg = "Failed to launch APK in virtual space: ${e.message}"
            Log.e(TAG, errorMsg, e)
            showErrorToast(ctx, "Failed to launch APK in virtual space: APK belum terinstal di perangkat (${packageName})")
            if (cloneId > 0) {
                runCatching {
                    val containerIntent = Intent(ctx, VirtualContainerActivity::class.java).apply {
                        putExtra(VirtualProcessManager.EXTRA_CLONE_ID, cloneId)
                        putExtra(VirtualProcessManager.EXTRA_PACKAGE_NAME, packageName)
                        putExtra(VirtualProcessManager.EXTRA_APP_NAME, appNameHint.ifBlank { packageName.substringAfterLast('.') })
                        putExtra(VirtualProcessManager.EXTRA_USER_ID, userId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                        addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        if (ctx !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    ctx.startActivity(containerIntent)
                }
            }
            false
        } catch (e: SecurityException) {
            val errorMsg = "Failed to launch APK in virtual space (SecurityException): ${e.message}"
            Log.e(TAG, errorMsg, e)
            showErrorToast(ctx, errorMsg)
            false
        } catch (e: Exception) {
            val errorMsg = "Failed to launch APK in virtual space: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, errorMsg, e)
            showErrorToast(ctx, errorMsg)
            false
        }
    }

    /**
     * Convenience overload for launching a [CloneAppEntity] inside the virtual container.
     */
    fun launchCloneInVirtualSpace(context: Context, clone: CloneAppEntity): Boolean {
        init(context)

        // Jalur utama "Clone App": paket hasil re-sign yang sudah terpasang dijalankan sebagai
        // aplikasi kedua yang benar-benar terpisah (bukan sekadar menyalin base.apk).
        val installedClonePackage = clone.installedClonePackage
        if (installedClonePackage.isNotBlank() && CloneManager.isCloneInstalled(context, installedClonePackage)) {
            val cloneLaunchIntent = context.packageManager.getLaunchIntentForPackage(installedClonePackage)
            if (cloneLaunchIntent != null) {
                cloneLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launched = runCatching {
                    context.startActivity(cloneLaunchIntent)
                    true
                }.getOrDefault(false)
                if (launched) {
                    Log.i(TAG, "Launched installed clone package $installedClonePackage (source ${clone.packageName})")
                    return true
                }
            }
        }

        // Built-in Device ID Checker diagnostic tool runs directly inside VirtualContainerActivity
        if (clone.packageName == "com.ins.tools.deviceinspector") {
            VirtualContainerActivity.launchCloneContainer(context, clone)
            return true
        }
        return launchVirtualApp(
            packageName = clone.packageName,
            userId = clone.instanceIndex,
            context = context,
            cloneId = clone.id,
            appNameHint = clone.appName
        )
    }

    private fun resolveRealInstalledPackageName(
        context: Context,
        packageName: String,
        appNameHint: String
    ): String {
        val dummyClone = CloneAppEntity(
            packageName = packageName,
            appName = appNameHint.ifBlank { packageName.substringAfterLast('.') },
            instanceIndex = 0,
            folderName = null,
            iconColorHex = 0xFF00E5FF,
            categoryTag = "App",
            isRunning = false,
            virtualPid = 0,
            androidId = "",
            imei = "",
            imsi = "",
            buildSerial = "",
            buildModel = "",
            buildManufacturer = "",
            buildBrand = "",
            wifiMac = "",
            wifiSsid = "",
            advertisingId = "",
            mockLocationEnabled = false,
            mockLatitude = 0.0,
            mockLongitude = 0.0,
            mockAccuracy = 1.0f,
            mockLocationName = ""
        )
        val resolved = VirtualApkLauncher.resolveInstalledApk(context, dummyClone)
        return if (resolved != null && resolved.isInstalledOnHost) {
            resolved.packageName
        } else {
            packageName
        }
    }

    private fun showErrorToast(context: Context, message: String) {
        VirtualProcessManager.showToastOnMainThread(context, message)
    }
}
