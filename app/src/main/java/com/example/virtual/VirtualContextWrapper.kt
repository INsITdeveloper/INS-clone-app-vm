package com.example.virtual

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import com.example.data.CloneAppEntity
import dalvik.system.DexClassLoader
import java.io.File

data class ClassLoaderHookDiagnostic(
    val packageName: String,
    val sourceApkPath: String,
    val optimizedDexDir: String,
    val nativeLibDir: String,
    val loaderClassName: String,
    val parentLoaderName: String,
    val isHostApkResolved: Boolean
)

/**
 * Child-First / APK-First Isolated ClassLoader (`SandboxApkClassLoader`).
 *
 * Loads Android framework classes (`android.*`, `java.*`, `javax.*`, `dalvik.*`) from the OS
 * `BootClassLoader`, and loads all application classes, `androidx.*`, `com.google.*`, and `kotlin.*`
 * FIRST from the cloned APK's own `base.apk` + `split_*.apk` DEX files so they never collide
 * with the host container's libraries.
 */
class SandboxApkClassLoader(
    dexPath: String,
    optimizedDirectory: String?,
    librarySearchPath: String?,
    bootParent: ClassLoader,
    private val hostFallbackLoader: ClassLoader
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, bootParent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        val isFrameworkBootClass = name.startsWith("java.") ||
            name.startsWith("javax.") ||
            name.startsWith("dalvik.") ||
            name.startsWith("sun.") ||
            name.startsWith("org.json.") ||
            name.startsWith("org.w3c.") ||
            name.startsWith("org.xml.") ||
            name.startsWith("org.xmlpull.") ||
            (name.startsWith("android.") &&
                !name.startsWith("androidx.") &&
                !name.startsWith("android.support.") &&
                !name.startsWith("android.arch.") &&
                !name.startsWith("android.material."))

        if (isFrameworkBootClass) {
            return super.loadClass(name, resolve)
        }

        // 1. First load directly from the target APK's DEX files (base.apk + splits)
        try {
            val clazz = findClass(name)
            if (resolve) resolveClass(clazz)
            return clazz
        } catch (_: ClassNotFoundException) {
            // Fall through
        }

        // 2. Try BootClassLoader
        try {
            return super.loadClass(name, resolve)
        } catch (_: ClassNotFoundException) {
            // Fall through
        }

        // 3. Fallback to host ClassLoader
        return hostFallbackLoader.loadClass(name)
    }

    override fun findLibrary(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val lower = name.lowercase()
        // Block anti-tamper / signature-enforcement JNI libraries that call C abort() when run in a virtual container UID
        if (lower.contains("metasec") ||
            lower.contains("msaoaidsec") ||
            lower.contains("secenh") ||
            lower.contains("dexhelper") ||
            lower.contains("jiagu") ||
            lower.contains("bangcle") ||
            lower.contains(" crash") ||
            lower.contains("bugly") ||
            lower.contains("breakpad")
        ) {
            return null
        }
        val resolvedPath = runCatching { super.findLibrary(name) }.getOrNull() ?: return null
        if (!resolvedPath.contains("!/")) {
            val f = File(resolvedPath)
            // Never dlopen synthetic 512-byte stub files
            if (!f.exists() || f.length() <= 4096L) {
                return null
            }
        }
        return resolvedPath
    }
}

/**
 * ClassLoader Hooking Engine.
 * Creates an isolated `SandboxApkClassLoader` per cloned instance, loading bytecode strictly from
 * the extracted sandbox APK (`/data/user/0/com.ins.virtualspace/virtual/user/<slot>/<pkg>/base.apk` + splits)
 * and binding its optimized DEX cache and native `.so` library search directories to the clone's sandbox.
 */
object VirtualClassLoaderHook {

    fun createIsolatedClassLoader(
        hostContext: Context,
        clone: CloneAppEntity,
        sandboxRoot: File
    ): Pair<ClassLoader, ClassLoaderHookDiagnostic> {
        val codeCacheDir = File(sandboxRoot, "code_cache").apply { mkdirs() }
        val libDir = File(sandboxRoot, "lib").apply { mkdirs() }

        // Ensure APK and splits are staged inside our sandboxRoot
        val extractedInfo = VirtualApkLauncher.extractApkIntoVirtualSandbox(hostContext, clone, sandboxRoot)
        val extractedBaseApk = File(sandboxRoot, "base.apk")
        runCatching { extractedBaseApk.setReadOnly() }

        val resolvedHost = VirtualApkLauncher.resolveInstalledApk(hostContext, clone)
        val isSymlinkBaseApk = runCatching {
            java.nio.file.Files.isSymbolicLink(extractedBaseApk.toPath())
        }.getOrDefault(false)

        if (extractedBaseApk.exists() && !isSymlinkBaseApk) {
            runCatching { android.system.Os.chmod(extractedBaseApk.absolutePath, 292) } // 0444 read-only
            runCatching { extractedBaseApk.setReadOnly() }
        }

        val dexPathList = linkedSetOf<String>().apply {
            // Prefer /data/app/.../base.apk first when available so ART uses system read-only APK + oat cache
            resolvedHost?.sourceApkPath?.takeIf { it.isNotBlank() && File(it).exists() }?.let {
                add(it)
            }
            resolvedHost?.splitApkPaths?.forEach { splitPath ->
                if (File(splitPath).exists()) add(splitPath)
            }
            // Add sandbox base.apk if it is a standalone copied APK archive (not a writable symlink)
            if (extractedBaseApk.exists() && extractedBaseApk.length() > 16384L && (!isSymlinkBaseApk || isEmpty())) {
                add(extractedBaseApk.absolutePath)
            }
            extractedInfo.splitApkPaths.forEach { splitPath ->
                if (File(splitPath).exists()) add(splitPath)
            }
            sandboxRoot.listFiles()?.filter { it.name.startsWith("split_") && it.name.endsWith(".apk") }?.forEach { splitFile ->
                val isSplitSymlink = runCatching { java.nio.file.Files.isSymbolicLink(splitFile.toPath()) }.getOrDefault(false)
                if (!isSplitSymlink) {
                    runCatching { android.system.Os.chmod(splitFile.absolutePath, 292) }
                    runCatching { splitFile.setReadOnly() }
                    add(splitFile.absolutePath)
                }
            }
            if (isEmpty()) {
                add(hostContext.applicationInfo.sourceDir)
            }
        }
        val combinedDexPath = dexPathList.joinToString(File.pathSeparator)

        // Include real extracted nativeLibDir, sandbox/lib, AND direct APK!/lib/<abi> paths for split_config.<abi>.apk
        val supportedAbis = Build.SUPPORTED_ABIS ?: arrayOf("arm64-v8a", "armeabi-v7a", "x86_64")
        val combinedLibPath = linkedSetOf<String>().apply {
            resolvedHost?.nativeLibDir?.takeIf { it.isNotBlank() && File(it).exists() }?.let { add(it) }
            add(libDir.absolutePath)
            for (apkPath in dexPathList) {
                for (abi in supportedAbis) {
                    add("$apkPath!/lib/$abi")
                }
            }
        }.joinToString(File.pathSeparator)

        val bootLoader = Activity::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader().parent
            ?: hostContext.classLoader

        val dexLoader: ClassLoader = runCatching {
            SandboxApkClassLoader(
                dexPath = combinedDexPath,
                optimizedDirectory = codeCacheDir.absolutePath,
                librarySearchPath = combinedLibPath,
                bootParent = bootLoader,
                hostFallbackLoader = hostContext.classLoader
            )
        }.getOrElse { hostContext.classLoader }

        val diagnostic = ClassLoaderHookDiagnostic(
            packageName = resolvedHost?.packageName ?: clone.packageName,
            sourceApkPath = extractedInfo.extractedApkPath,
            optimizedDexDir = "${clone.canonicalVirtualDataPath}code_cache",
            nativeLibDir = "${clone.canonicalVirtualDataPath}lib",
            loaderClassName = dexLoader::class.java.name,
            parentLoaderName = (dexLoader.parent ?: ClassLoader.getSystemClassLoader())::class.java.name,
            isHostApkResolved = extractedInfo.isExtractedFromRealPhoneApk
        )

        return dexLoader to diagnostic
    }
}

/**
 * Sandboxed VirtualContextWrapper.
 * Redirects all filesystem, SQLite database, cache, SharedPreferences, Resources, AssetManager,
 * Theme, PackageManager, ApplicationContext, and Activity navigation calls from a cloned APK to
 * `/data/user/0/com.ins.virtualspace/virtual/user/<instance>/<package_name>/`.
 */
class VirtualContextWrapper(
    base: Context,
    val clone: CloneAppEntity,
    val sandboxRoot: File,
    private val customClassLoader: ClassLoader
) : ContextWrapper(base) {

    private val virtualFilesDir = File(sandboxRoot, "files").apply { mkdirs() }
    private val virtualCacheDir = File(sandboxRoot, "cache").apply { mkdirs() }
    private val virtualCodeCacheDir = File(sandboxRoot, "code_cache").apply { mkdirs() }
    private val virtualDatabasesDir = File(sandboxRoot, "databases").apply { mkdirs() }
    private val virtualSharedPrefsDir = File(sandboxRoot, "shared_prefs").apply { mkdirs() }

    var apkResources: Resources? = null
    var apkTheme: Resources.Theme? = null
    var apkApplicationInfo: ApplicationInfo? = null
    var sandboxApplication: Application? = null
    var sandboxPackageManager: PackageManager? = null
    var customLayoutInflater: LayoutInflater? = null
    var onInterceptStartActivity: ((Intent) -> Unit)? = null

    override fun getApplicationContext(): Context = sandboxApplication ?: this

    override fun getPackageManager(): PackageManager = sandboxPackageManager ?: super.getPackageManager()

    override fun getPackageName(): String = apkApplicationInfo?.packageName ?: clone.packageName

    override fun getOpPackageName(): String = baseContext.packageName

    override fun getClassLoader(): ClassLoader = customClassLoader

    override fun getResources(): Resources = apkResources ?: super.getResources()

    override fun getAssets(): AssetManager = apkResources?.assets ?: super.getAssets()

    override fun getTheme(): Resources.Theme = apkTheme ?: super.getTheme()

    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name && customLayoutInflater != null) {
            return customLayoutInflater
        }
        return super.getSystemService(name)
    }

    override fun getApplicationInfo(): ApplicationInfo {
        return (apkApplicationInfo ?: super.getApplicationInfo().apply {
            dataDir = sandboxRoot.absolutePath
            sourceDir = File(sandboxRoot, "base.apk").absolutePath
            publicSourceDir = File(sandboxRoot, "base.apk").absolutePath
        }).apply {
            if (metaData == null) {
                metaData = Bundle()
            }
        }
    }

    override fun createPackageContext(packageName: String?, flags: Int): Context {
        if (packageName == null || packageName == getPackageName() || packageName == clone.packageName || packageName == baseContext.packageName) {
            return this
        }
        return runCatching { super.createPackageContext(packageName, flags) }.getOrDefault(this)
    }

    override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration): Context {
        val baseCfgCtx = runCatching { super.createConfigurationContext(overrideConfiguration) }.getOrDefault(baseContext)
        return VirtualContextWrapper(
            base = baseCfgCtx,
            clone = clone,
            sandboxRoot = sandboxRoot,
            customClassLoader = customClassLoader
        ).also { copyStateTo(it) }
    }

    override fun createDisplayContext(display: android.view.Display): Context {
        val baseDispCtx = runCatching { super.createDisplayContext(display) }.getOrDefault(baseContext)
        return VirtualContextWrapper(
            base = baseDispCtx,
            clone = clone,
            sandboxRoot = sandboxRoot,
            customClassLoader = customClassLoader
        ).also { copyStateTo(it) }
    }

    private fun copyStateTo(target: VirtualContextWrapper) {
        target.apkResources = this.apkResources
        target.apkTheme = this.apkTheme
        target.apkApplicationInfo = this.apkApplicationInfo
        target.sandboxApplication = this.sandboxApplication
        target.sandboxPackageManager = this.sandboxPackageManager
        target.customLayoutInflater = this.customLayoutInflater
        target.onInterceptStartActivity = this.onInterceptStartActivity
    }

    override fun getPackageResourcePath(): String = File(sandboxRoot, "base.apk").absolutePath

    override fun getPackageCodePath(): String = File(sandboxRoot, "base.apk").absolutePath

    override fun getDataDir(): File = sandboxRoot

    override fun getFilesDir(): File = virtualFilesDir

    override fun getCacheDir(): File = virtualCacheDir

    override fun getCodeCacheDir(): File = virtualCodeCacheDir

    override fun getNoBackupFilesDir(): File = File(sandboxRoot, "no_backup").apply { mkdirs() }

    override fun getDir(name: String, mode: Int): File {
        return File(sandboxRoot, "app_$name").apply { mkdirs() }
    }

    override fun getExternalFilesDir(type: String?): File {
        return File(sandboxRoot, "external_files/${type ?: "root"}").apply { mkdirs() }
    }

    override fun getExternalCacheDir(): File {
        return File(sandboxRoot, "external_cache").apply { mkdirs() }
    }

    override fun getDatabasePath(name: String): File {
        val cleanName = if (name.endsWith(".db")) name else "$name.db"
        return File(virtualDatabasesDir, cleanName)
    }

    override fun databaseList(): Array<String> {
        return virtualDatabasesDir.list { _, name -> !name.endsWith("-journal") && !name.endsWith("-wal") && !name.endsWith("-shm") }
            ?: emptyArray()
    }

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
    }

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
    }

    fun getVirtualSharedPrefsFile(name: String): File {
        val cleanName = if (name.endsWith(".xml")) name else "$name.xml"
        return File(virtualSharedPrefsDir, cleanName)
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        // Store under a unique namespace AND sync with the clone's shared_prefs XML directory
        val namespacedKey = "virtual_u${clone.instanceIndex}_${clone.packageName}_$name"
        return super.getSharedPreferences(namespacedKey, mode)
    }

    override fun startActivity(intent: Intent?) {
        if (intent == null) return
        // Route any internal startActivity call inside the cloned APK back into our sandbox host container!
        onInterceptStartActivity?.invoke(intent)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        if (intent == null) return
        onInterceptStartActivity?.invoke(intent)
    }
}

/**
 * Installs an isolated `IPackageManager` Binder proxy on a cloned `ApplicationPackageManager`
 * instance for [virtualContext] so that any `PackageManager` call inside the cloned APK
 * (`getActivityInfo`, `getApplicationInfo`, `getPackageInfo`, `getServiceInfo`, `getProviderInfo`)
 * resolves the virtual sandbox `ApplicationInfo` (`dataDir = sandboxRoot`, `sourceDir = base.apk`)
 * and never fails with `PackageManager.NameNotFoundException`.
 */
object SandboxPackageManagerHooks {

    fun install(
        hostContext: Context,
        virtualContext: VirtualContextWrapper,
        sandboxAppInfo: ApplicationInfo,
        extractedBaseApkPath: String
    ) {
        runCatching {
            val hostPm = hostContext.packageManager
            val appPmClass = hostPm.javaClass
            if (!appPmClass.name.contains("ApplicationPackageManager")) return

            val mPmField = appPmClass.getDeclaredField("mPM").apply { isAccessible = true }
            val mContextField = runCatching {
                appPmClass.getDeclaredField("mContext").apply { isAccessible = true }
            }.getOrNull()

            val originalIPm = mPmField.get(hostPm) ?: return
            val iPmInterface = Class.forName("android.content.pm.IPackageManager")

            val proxyIPm = java.lang.reflect.Proxy.newProxyInstance(
                iPmInterface.classLoader,
                arrayOf(iPmInterface)
            ) { _, method, args ->
                val methodName = method.name
                val targetPkg = sandboxAppInfo.packageName

                try {
                    when (methodName) {
                        "getApplicationInfo" -> {
                            val pkgArg = args?.firstOrNull() as? String
                            if (pkgArg == targetPkg || pkgArg == virtualContext.clone.packageName) {
                                val realResult = runCatching {
                                    if (args != null) method.invoke(originalIPm, *args) else method.invoke(originalIPm)
                                }.getOrNull() as? ApplicationInfo
                                return@newProxyInstance ApplicationInfo(realResult ?: sandboxAppInfo).apply {
                                    packageName = targetPkg
                                    dataDir = virtualContext.sandboxRoot.absolutePath
                                    deviceProtectedDataDir = virtualContext.sandboxRoot.absolutePath
                                    sourceDir = sandboxAppInfo.sourceDir
                                    publicSourceDir = sandboxAppInfo.publicSourceDir
                                    splitSourceDirs = sandboxAppInfo.splitSourceDirs
                                    splitPublicSourceDirs = sandboxAppInfo.splitPublicSourceDirs
                                    nativeLibraryDir = sandboxAppInfo.nativeLibraryDir
                                    if (metaData == null) {
                                        metaData = sandboxAppInfo.metaData ?: Bundle()
                                    }
                                }
                            }
                        }
                        "getActivityInfo" -> {
                            val compArg = args?.firstOrNull() as? android.content.ComponentName
                            val realActInfo = runCatching {
                                if (args != null) method.invoke(originalIPm, *args) else method.invoke(originalIPm)
                            }.getOrNull() as? android.content.pm.ActivityInfo

                            if (realActInfo != null) {
                                realActInfo.applicationInfo = sandboxAppInfo
                                if (realActInfo.metaData == null) realActInfo.metaData = Bundle()
                                return@newProxyInstance realActInfo
                            }
                            if (compArg != null && (compArg.packageName == targetPkg || compArg.packageName == virtualContext.clone.packageName || compArg.packageName == hostContext.packageName)) {
                                return@newProxyInstance android.content.pm.ActivityInfo().apply {
                                    packageName = targetPkg
                                    name = compArg.className
                                    applicationInfo = sandboxAppInfo
                                    theme = sandboxAppInfo.theme.takeIf { it != 0 }
                                        ?: android.R.style.Theme_DeviceDefault_Light_NoActionBar
                                    labelRes = sandboxAppInfo.labelRes
                                    nonLocalizedLabel = sandboxAppInfo.nonLocalizedLabel
                                    metaData = Bundle()
                                    exported = true
                                }
                            }
                        }
                        "getPackageInfo" -> {
                            val pkgArg = args?.firstOrNull() as? String
                            val realPkgInfo = runCatching {
                                if (args != null) method.invoke(originalIPm, *args) else method.invoke(originalIPm)
                            }.getOrNull() as? android.content.pm.PackageInfo

                            if (realPkgInfo != null) {
                                if (pkgArg == targetPkg || pkgArg == virtualContext.clone.packageName) {
                                    realPkgInfo.applicationInfo = sandboxAppInfo
                                }
                                return@newProxyInstance realPkgInfo
                            }
                            if ((pkgArg == targetPkg || pkgArg == virtualContext.clone.packageName) && File(extractedBaseApkPath).exists()) {
                                val flags = (args?.getOrNull(1) as? Number)?.toInt() ?: 0
                                val archivePkgInfo = runCatching {
                                    hostPm.getPackageArchiveInfo(extractedBaseApkPath, flags)
                                }.getOrNull()
                                if (archivePkgInfo != null) {
                                    archivePkgInfo.applicationInfo = sandboxAppInfo
                                    return@newProxyInstance archivePkgInfo
                                }
                            }
                        }
                        "getInstallerPackageName" -> {
                            return@newProxyInstance "com.android.vending"
                        }
                    }

                    if (args != null) {
                        method.invoke(originalIPm, *args)
                    } else {
                        method.invoke(originalIPm)
                    }
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException ?: e
                }
            }

            // Instantiate an isolated ApplicationPackageManager copy holding proxyIPm
            val ctor = appPmClass.declaredConstructors.firstOrNull { it.parameterTypes.size == 2 }
            if (ctor != null) {
                ctor.isAccessible = true
                val baseCtx = mContextField?.get(hostPm) ?: hostContext
                val isolatedPm = ctor.newInstance(baseCtx, proxyIPm) as? PackageManager
                if (isolatedPm != null) {
                    virtualContext.sandboxPackageManager = isolatedPm
                }
            }
        }
    }
}
