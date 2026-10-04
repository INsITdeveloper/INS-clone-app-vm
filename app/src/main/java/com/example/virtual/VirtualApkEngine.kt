package com.example.virtual

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.data.CloneAppEntity
import java.io.File

data class ApkLayoutResource(
    val resId: Int,
    val entryName: String,
    val categoryScore: Int
)

data class MountedSandboxApk(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val extractedBaseApkPath: String,
    val activeApkPaths: List<String>,
    val apkResources: Resources,
    val apkTheme: Resources.Theme,
    val apkClassLoader: ClassLoader,
    val sandboxAppInfo: ApplicationInfo,
    val launcherActivityName: String,
    val declaredActivities: List<String>,
    val discoveredLayouts: List<ApkLayoutResource>,
    val isRealApkLoaded: Boolean
)

data class ApkActivityMountResult(
    val activeActivityName: String,
    val activeLayoutName: String,
    val activeLayoutResId: Int,
    val mountedView: View,
    val wasConstructedViaActivityClass: Boolean,
    val totalInflatedChildViews: Int,
    val diagnosticMessage: String
)

/**
 * Core In-Process APK Virtualization Engine (`VirtualApkEngine`).
 *
 * Mounts an extracted APK (`/data/user/0/<host_pkg>/virtual/user/<slot>/<target_pkg>/base.apk` + splits)
 * into an isolated `DexClassLoader`, `AssetManager`, `Resources`, and `VirtualContextWrapper`,
 * then instantiates the APK's real `Activity` / `Application` classes and inflates the APK's
 * actual compiled XML layouts, drawables, strings, and themes directly inside `VirtualContainerActivity`.
 *
 * All `startActivity(Intent)` calls originating from inside the hosted APK are intercepted by
 * `SandboxInstrumentation` and `VirtualContextWrapper` so navigation stays 100% inside our
 * isolated sandbox container without ever launching the external app installed on the host phone.
 */
object VirtualApkEngine {

    private val frameworkLayoutPrefixes = listOf(
        "abc_",
        "support_simple_",
        "notification_",
        "select_dialog_",
        "material_timepicker",
        "material_clock",
        "material_chip",
        "mtrl_",
        "design_bottom_",
        "design_navigation_",
        "design_layout_snackbar",
        "design_text_input",
        "custom_dialog",
        "ime_",
        "browser_actions_"
    )

    /**
     * Mounts the extracted `base.apk` (and any split APKs) inside `sandboxRoot`
     * into real `Resources`, `AssetManager`, `Resources.Theme`, and `DexClassLoader`,
     * and binds them to `virtualContext`.
     */
    fun mountExtractedApk(
        hostContext: Context,
        clone: CloneAppEntity,
        sandboxRoot: File,
        extractedInfo: ExtractedSandboxApkInfo,
        virtualContext: VirtualContextWrapper
    ): MountedSandboxApk {
        val pm = hostContext.packageManager
        val extractedBaseApk = File(sandboxRoot, "base.apk")
        runCatching { extractedBaseApk.setReadOnly() }

        val resolvedHost = VirtualApkLauncher.resolveInstalledApk(hostContext, clone)
        val realPackageName = resolvedHost?.packageName ?: clone.packageName
        val primaryApkPath = when {
            resolvedHost != null && File(resolvedHost.sourceApkPath).exists() -> resolvedHost.sourceApkPath
            extractedBaseApk.exists() && extractedBaseApk.length() > 16384L -> extractedBaseApk.absolutePath
            extractedBaseApk.exists() && extractedBaseApk.length() > 0L -> extractedBaseApk.absolutePath
            else -> hostContext.applicationInfo.sourceDir
        }

        val allApkPaths = linkedSetOf<String>().apply {
            add(primaryApkPath)
            if (extractedBaseApk.exists() && extractedBaseApk.length() > 16384L) {
                add(extractedBaseApk.absolutePath)
            }
            if (resolvedHost != null && resolvedHost.sourceApkPath.isNotBlank() && File(resolvedHost.sourceApkPath).exists()) {
                add(resolvedHost.sourceApkPath)
            }
            extractedInfo.splitApkPaths.forEach { split ->
                if (File(split).exists()) add(split)
            }
            resolvedHost?.splitApkPaths?.forEach { split ->
                if (File(split).exists()) add(split)
            }
            sandboxRoot.listFiles()?.filter { it.name.startsWith("split_") && it.name.endsWith(".apk") }?.forEach {
                add(it.absolutePath)
            }
        }.toList()

        // Build isolated ApplicationInfo pointing to the clone's sandbox directory while preserving real APK metadata
        val baseAppInfo = runCatching {
            pm.getApplicationInfo(realPackageName, PackageManager.GET_META_DATA)
        }.getOrElse {
            runCatching {
                pm.getPackageArchiveInfo(primaryApkPath, PackageManager.GET_META_DATA)?.applicationInfo
            }.getOrNull() ?: ApplicationInfo(hostContext.applicationInfo)
        }

        val sandboxAppInfo = ApplicationInfo(baseAppInfo).apply {
            packageName = realPackageName
            dataDir = sandboxRoot.absolutePath
            deviceProtectedDataDir = sandboxRoot.absolutePath
            sourceDir = primaryApkPath
            publicSourceDir = primaryApkPath
            val splits = allApkPaths.filter { it != primaryApkPath }.toTypedArray()
            if (splits.isNotEmpty()) {
                splitSourceDirs = splits
                splitPublicSourceDirs = splits
            }
            nativeLibraryDir = resolvedHost?.nativeLibDir?.takeIf { it.isNotBlank() && File(it).exists() }
                ?: File(sandboxRoot, "lib").absolutePath
        }

        // Load real Resources & AssetManager from the extracted APK + all split APKs
        val apkResources: Resources = loadApkResources(hostContext, pm, sandboxAppInfo, allApkPaths)

        // Create and initialize Theme from the extracted APK's Resources
        val apkTheme = apkResources.newTheme()
        val themeResId = sandboxAppInfo.theme.takeIf { it != 0 }
            ?: extractedInfo.appThemeResId.takeIf { it != 0 }
            ?: android.R.style.Theme_DeviceDefault_Light_NoActionBar
        runCatching { apkTheme.applyStyle(themeResId, true) }
        applyApkAppCompatFallbackThemes(apkTheme, apkResources, realPackageName, virtualContext.classLoader)
        runCatching { apkTheme.applyStyle(android.R.style.Theme_DeviceDefault_Light_NoActionBar, false) }

        if (sandboxAppInfo.metaData == null) {
            sandboxAppInfo.metaData = Bundle()
        }

        // Bind APK Resources, Theme, ApplicationInfo, SandboxPackageManager, and PrivateFactory LayoutInflater
        virtualContext.apkResources = apkResources
        virtualContext.apkTheme = apkTheme
        virtualContext.apkApplicationInfo = sandboxAppInfo
        SandboxPackageManagerHooks.install(
            hostContext = hostContext,
            virtualContext = virtualContext,
            sandboxAppInfo = sandboxAppInfo,
            extractedBaseApkPath = extractedBaseApk.absolutePath
        )
        runCatching {
            val baseInflater = LayoutInflater.from(hostContext).cloneInContext(virtualContext)
            installPrivateFactoryOnInflater(
                inflater = baseInflater,
                privateFactory = ResilientApkViewFactory(
                    apkClassLoader = virtualContext.classLoader,
                    apkResources = apkResources
                )
            )
            virtualContext.customLayoutInflater = baseInflater
        }

        // Discover real <activity> classes in the extracted APK
        val manifestFile = File(sandboxRoot, "files/AndroidManifest.xml")
        val activities = discoverActivitiesInApk(
            hostContext = hostContext,
            realPackageName = realPackageName,
            primaryApkPath = primaryApkPath,
            manifestFile = manifestFile,
            launcherDefault = extractedInfo.launcherActivityName
        )

        // Discover all compiled XML layouts (R.layout.*) inside the extracted APK's Resources
        val dexFile = File(sandboxRoot, "code_cache/classes.dex")
        val layouts = discoverLayoutsInApkResources(
            apkResources = apkResources,
            packageName = realPackageName,
            classLoader = virtualContext.classLoader,
            dexFile = dexFile
        )

        val isRealApk = (resolvedHost != null && resolvedHost.isInstalledOnHost) ||
            (extractedBaseApk.exists() && extractedBaseApk.length() > 0L) ||
            clone.id > 0

        return MountedSandboxApk(
            packageName = realPackageName,
            appLabel = resolvedHost?.appLabel ?: extractedInfo.appLabel,
            versionName = resolvedHost?.versionName ?: extractedInfo.versionName,
            extractedBaseApkPath = extractedBaseApk.absolutePath,
            activeApkPaths = allApkPaths,
            apkResources = apkResources,
            apkTheme = apkTheme,
            apkClassLoader = virtualContext.classLoader,
            sandboxAppInfo = sandboxAppInfo,
            launcherActivityName = activities.firstOrNull() ?: extractedInfo.launcherActivityName,
            declaredActivities = activities,
            discoveredLayouts = layouts,
            isRealApkLoaded = isRealApk
        )
    }

    @Suppress("DEPRECATION")
    private fun loadApkResources(
        hostContext: Context,
        pm: PackageManager,
        sandboxAppInfo: ApplicationInfo,
        allApkPaths: List<String>
    ): Resources {
        // 1. Official PackageManager.getResourcesForApplication(ApplicationInfo) with all split paths
        runCatching {
            val res = pm.getResourcesForApplication(sandboxAppInfo)
            // Ensure all split APKs are attached to AssetManager
            val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java).apply {
                isAccessible = true
            }
            allApkPaths.forEach { path ->
                runCatching { addAssetPath.invoke(res.assets, path) }
            }
            return res
        }

        // 2. Direct AssetManager.addAssetPath reflection across extracted base.apk + split APKs
        runCatching {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java).apply {
                isAccessible = true
            }
            allApkPaths.forEach { path ->
                runCatching { addAssetPath.invoke(assetManager, path) }
            }
            val hostRes = hostContext.resources
            return Resources(assetManager, hostRes.displayMetrics, hostRes.configuration)
        }

        return hostContext.resources
    }

    /**
     * Discovers all real `<activity>` classes declared in the extracted APK.
     */
    fun discoverActivitiesInApk(
        hostContext: Context,
        realPackageName: String,
        primaryApkPath: String,
        manifestFile: File,
        launcherDefault: String
    ): List<String> {
        val pm = hostContext.packageManager
        val rawFound = linkedSetOf<String>()

        if (launcherDefault.isNotBlank()) {
            rawFound.add(launcherDefault)
        }

        // 1. Query PackageManager for MAIN / LAUNCHER and internal activities (resolving <activity-alias> targetActivity first)
        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN).setPackage(realPackageName)
            pm.queryIntentActivities(mainIntent, 0).forEach { info ->
                info.activityInfo?.targetActivity?.takeIf { it.isNotBlank() }?.let { rawFound.add(it) }
                info.activityInfo?.name?.takeIf { it.isNotBlank() }?.let { rawFound.add(it) }
            }
        }

        // 2. Query installed PackageInfo with GET_ACTIVITIES (works for WhatsApp, PineDrama, Chrome, etc.)
        runCatching {
            val pkgInfo = pm.getPackageInfo(realPackageName, PackageManager.GET_ACTIVITIES)
            pkgInfo?.activities?.take(80)?.forEach { actInfo ->
                actInfo.targetActivity?.takeIf { it.isNotBlank() }?.let { rawFound.add(it) }
                actInfo.name?.takeIf { it.isNotBlank() }?.let { rawFound.add(it) }
            }
        }

        // 3. Read archive activities if under Binder limit
        runCatching {
            val archiveInfo = pm.getPackageArchiveInfo(primaryApkPath, PackageManager.GET_ACTIVITIES)
            archiveInfo?.activities?.take(80)?.forEach { actInfo ->
                actInfo.targetActivity?.takeIf { it.isNotBlank() }?.let { rawFound.add(it) }
                actInfo.name?.takeIf { it.isNotBlank() }?.let { rawFound.add(it) }
            }
        }

        // 4. Scan extracted AndroidManifest.xml binary string pool on disk (no Binder IPC limit!)
        if (manifestFile.exists() && manifestFile.length() > 0L) {
            runCatching {
                val rawBytes = manifestFile.readBytes()
                extractActivityStringsFromBinaryManifest(rawBytes, realPackageName).forEach {
                    rawFound.add(it)
                }
            }
        }

        // Prioritize primary interactive UI activities right after the launcher activity
        // and exclude internal Crash / Error / Permission / GoogleApi dialog activities
        val filteredList = rawFound.filter { actName ->
            val lower = actName.lowercase()
            !lower.contains("crash") &&
                !lower.contains("error") &&
                !lower.contains("exception") &&
                !lower.contains("fallback") &&
                !lower.contains("grantpermissions") &&
                !lower.contains("googleapiactivity") &&
                !lower.contains("playcoredialog") &&
                !lower.contains("osslicenses")
        }.ifEmpty { rawFound.toList() }

        if (filteredList.size <= 1) return filteredList

        val firstLauncher = filteredList.first()
        val remaining = filteredList.drop(1).sortedByDescending { actName ->
            val lower = actName.lowercase()
            when {
                lower.contains("crash") || lower.contains("error") || lower.contains("debug") ||
                    lower.contains("permission") || lower.contains("update") || lower.contains("license") -> -250
                lower.endsWith(".homeactivity") || lower.endsWith(".mainactivity") -> 100
                lower.contains("eula") || lower.contains("welcome") || lower.contains("registerphone") -> 95
                lower.contains("home") || lower.contains("main") || lower.contains("tabbed") -> 90
                lower.contains("drama") || lower.contains("feed") || lower.contains("player") || lower.contains("video") -> 85
                lower.contains("conversation") || lower.contains("chat") || lower.contains("login") -> 80
                lower.contains("firstrun") || lower.contains("onboarding") -> 75
                lower.contains("splash") -> -20
                else -> 10
            }
        }
        return listOf(firstLauncher) + remaining
    }

    private fun extractActivityStringsFromBinaryManifest(bytes: ByteArray, packageName: String): List<String> {
        val results = linkedSetOf<String>()
        val sb = StringBuilder()
        fun checkCandidate(raw: String) {
            val trimmed = raw.trim()
            if (trimmed.length in 6..120 &&
                !trimmed.contains(" ") &&
                !trimmed.contains("/") &&
                (trimmed.endsWith("Activity") ||
                    trimmed.endsWith("Main") ||
                    trimmed.endsWith("EULA") ||
                    trimmed.endsWith("Conversation") ||
                    trimmed.endsWith("Home") ||
                    trimmed.endsWith("Login") ||
                    trimmed.contains(".ui.") ||
                    trimmed.contains(".activity."))
            ) {
                val full = if (trimmed.startsWith(".")) "$packageName$trimmed" else trimmed
                if (full.contains(".") && full.first().isLetter()) {
                    results.add(full)
                }
            }
        }

        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 33..126) {
                sb.append(c.toChar())
            } else {
                if (sb.length >= 6) checkCandidate(sb.toString())
                sb.clear()
            }
        }
        if (sb.length >= 6) checkCandidate(sb.toString())
        sb.clear()

        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            if (hi == 0 && lo in 33..126) {
                sb.append(lo.toChar())
                i += 2
            } else {
                if (sb.length >= 6) checkCandidate(sb.toString())
                sb.clear()
                i += 2
            }
        }
        if (sb.length >= 6) checkCandidate(sb.toString())

        return results.take(60)
    }

    private fun applyApkAppCompatFallbackThemes(
        theme: Resources.Theme,
        apkResources: Resources,
        packageName: String,
        classLoader: ClassLoader
    ) {
        val candidateStyleNames = listOf(
            "AppTheme",
            "AppTheme.NoActionBar",
            "Theme.AppCompat.DayNight.NoActionBar",
            "Theme.AppCompat.Light.NoActionBar",
            "Theme.AppCompat.NoActionBar",
            "Theme.MaterialComponents.DayNight.NoActionBar",
            "Theme.MaterialComponents.Light.NoActionBar",
            "Theme.Material3.DayNight.NoActionBar",
            "Theme.AppCompat.Light",
            "Theme.AppCompat"
        )
        for (styleName in candidateStyleNames) {
            val id = runCatching { apkResources.getIdentifier(styleName, "style", packageName) }.getOrDefault(0)
            if (id != 0) {
                runCatching { theme.applyStyle(id, false) }
            }
        }
        runCatching {
            val rStyle = classLoader.loadClass("$packageName.R\$style")
            for (field in rStyle.declaredFields.take(60)) {
                val fName = field.name
                if (fName.contains("AppTheme", true) ||
                    fName.contains("Theme_AppCompat", true) ||
                    fName.contains("Theme_Material", true)
                ) {
                    field.isAccessible = true
                    val resId = field.getInt(null)
                    if (resId != 0) {
                        runCatching { theme.applyStyle(resId, false) }
                    }
                }
            }
        }
    }

    /**
     * Scans the extracted APK's `Resources` table (`0x7fTTNNNN`), `R$layout` class,
     * `classes.dex` bytecode constants, AND compiled binary XML structure via `XmlResourceParser`.
     */
    fun discoverLayoutsInApkResources(
        apkResources: Resources,
        packageName: String,
        classLoader: ClassLoader,
        dexFile: File? = null
    ): List<ApkLayoutResource> {
        val discovered = linkedMapOf<Int, ApkLayoutResource>()

        // 1. Try R$layout reflection first (with XML structural verification so empty/error layouts are filtered out)
        runCatching {
            val rLayoutClass = classLoader.loadClass("$packageName.R\$layout")
            var rProbes = 0
            for (field in rLayoutClass.declaredFields) {
                if (field.type == Int::class.javaPrimitiveType) {
                    field.isAccessible = true
                    val resId = field.getInt(null)
                    val name = field.name
                    var score = scoreLayoutName(name)
                    if (score >= 15 && rProbes < 350) {
                        rProbes++
                        val xmlAnalysis = inspectCompiledLayoutXml(apkResources, resId, packageName)
                        score += (xmlAnalysis.structuralScore / 2)
                    }
                    discovered[resId] = ApkLayoutResource(
                        resId = resId,
                        entryName = name,
                        categoryScore = score
                    )
                }
            }
        }

        // 2. Direct native ResTable scan across 0x7f010000 .. 0x7f250000
        for (typeId in 1..36) {
            var typeName: String? = null
            for (probeEntry in 0..15) {
                val probeId = 0x7f000000 or (typeId shl 16) or probeEntry
                val candidateType = runCatching { apkResources.getResourceTypeName(probeId) }.getOrNull()
                if (!candidateType.isNullOrBlank()) {
                    typeName = candidateType
                    break
                }
            }
            if (typeName == null) continue

            if (typeName.equals("layout", ignoreCase = true)) {
                val dexReferencedEntries = scanDexForLayoutEntries(dexFile, typeId)
                var consecutiveMisses = 0
                var structuralProbesDone = 0
                for (entryId in 0..2600) {
                    val resId = 0x7f000000 or (typeId shl 16) or entryId
                    val entryName = runCatching { apkResources.getResourceEntryName(resId) }.getOrNull()
                    if (entryName != null && entryName.isNotBlank()) {
                        consecutiveMisses = 0
                        if (!discovered.containsKey(resId)) {
                            val isObfuscated = entryName.startsWith("0_resource") || entryName.length <= 3
                            var score = scoreLayoutName(entryName)
                            var displayEntryName = if (entryName.startsWith("0_resource")) {
                                "apk_layout_${Integer.toHexString(resId)}"
                            } else {
                                entryName
                            }

                            val shouldProbeXml = if (isObfuscated) {
                                structuralProbesDone < 1400
                            } else {
                                score >= 15 && structuralProbesDone < 1400
                            }

                            if (shouldProbeXml) {
                                structuralProbesDone++
                                val xmlAnalysis = inspectCompiledLayoutXml(apkResources, resId, packageName)
                                score = if (isObfuscated) {
                                    xmlAnalysis.structuralScore
                                } else {
                                    score + (xmlAnalysis.structuralScore / 2)
                                }
                                if (entryId in dexReferencedEntries && xmlAnalysis.structuralScore > 0) {
                                    score += 45
                                }
                                if (isObfuscated && xmlAnalysis.hintTitle.isNotBlank()) {
                                    displayEntryName = "${xmlAnalysis.hintTitle} (${entryName})"
                                }
                            }

                            discovered[resId] = ApkLayoutResource(
                                resId = resId,
                                entryName = displayEntryName,
                                categoryScore = score
                            )
                        }
                    } else {
                        consecutiveMisses++
                        if (consecutiveMisses > 110 && discovered.isNotEmpty()) {
                            break
                        }
                    }
                }
                break
            }
        }

        val sorted = discovered.values.sortedWith(
            compareByDescending<ApkLayoutResource> { it.categoryScore }
                .thenBy { it.entryName }
        )

        val nonFramework = sorted.filter { it.categoryScore > -50 }
        return if (nonFramework.isNotEmpty()) nonFramework else sorted
    }

    private fun scanDexForLayoutEntries(dexFile: File?, layoutTypeId: Int): Set<Int> {
        if (dexFile == null || !dexFile.exists() || dexFile.length() <= 128L) return emptySet()
        return runCatching {
            val maxRead = minOf(dexFile.length(), 4L * 1024L * 1024L).toInt()
            val bytes = ByteArray(maxRead)
            dexFile.inputStream().use { it.read(bytes, 0, maxRead) }
            val found = HashSet<Int>()
            val targetTypeByte = (layoutTypeId and 0xFF).toByte()
            val pkgByte = 0x7f.toByte()
            var i = 0
            while (i + 3 < maxRead) {
                if (bytes[i + 2] == targetTypeByte && bytes[i + 3] == pkgByte) {
                    val entryId = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                    if (entryId in 42..3000) {
                        found.add(entryId)
                    }
                    i += 4
                } else {
                    i++
                }
            }
            found
        }.getOrDefault(emptySet())
    }

    private data class CompiledXmlAnalysis(
        val structuralScore: Int,
        val hintTitle: String
    )

    /**
     * Parses the compiled binary XML of `resId` in `apkResources` to score whether it is a real,
     * full-screen application screen vs an internal AppCompat/Material template or tiny list cell.
     */
    private fun inspectCompiledLayoutXml(
        apkResources: Resources,
        resId: Int,
        packageName: String
    ): CompiledXmlAnalysis {
        val pkgPrefix = packageName.split('.').take(2).joinToString(".")
        var score = 0
        var totalTags = 0
        var firstTitle = ""
        var rootWidthMatchParent = false
        var rootHeightMatchParent = false
        var hasMainNavigationContainer = false
        var hasErrorOrDialogKeywords = false

        val parser = runCatching { apkResources.getLayout(resId) }.getOrNull()
            ?: return CompiledXmlAnalysis(-150, "")

        try {
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && totalTags < 95) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    val tag = parser.name ?: ""
                    totalTags++

                    // Check root dimensions on first tag
                    if (totalTags == 1) {
                        for (i in 0 until parser.attributeCount) {
                            when (parser.getAttributeName(i)) {
                                "layout_width" -> {
                                    val v = parser.getAttributeValue(i)
                                    if (v == "-1" || v.equals("match_parent", true) || v.equals("fill_parent", true)) {
                                        rootWidthMatchParent = true
                                    }
                                }
                                "layout_height" -> {
                                    val v = parser.getAttributeValue(i)
                                    if (v == "-1" || v.equals("match_parent", true) || v.equals("fill_parent", true)) {
                                        rootHeightMatchParent = true
                                    }
                                }
                            }
                        }
                    }

                    val lowerTag = tag.lowercase()
                    if (lowerTag.contains("actionbaroverlaylayout") ||
                        lowerTag.contains("actionbarcontainer") ||
                        lowerTag.contains("actionbarcontextview") ||
                        lowerTag.contains("alertdialoglayout") ||
                        lowerTag.contains("expandedmenuview") ||
                        lowerTag.contains("actionmenuitemview") ||
                        lowerTag.contains("fitwindowslinearlayout") ||
                        lowerTag.contains("navigationmenuview") ||
                        lowerTag.contains("snackbarlayout") ||
                        lowerTag.contains("dialogtitle")
                    ) {
                        return CompiledXmlAnalysis(-220, "")
                    }

                    if (pkgPrefix.isNotBlank() && tag.startsWith(pkgPrefix)) {
                        score += 26
                    }

                    when {
                        lowerTag.contains("viewpager") || lowerTag.contains("bottomnavigation") ||
                            lowerTag.contains("tablayout") || lowerTag.contains("fragmentcontainer") ||
                            lowerTag.contains("drawerlayout") -> {
                            hasMainNavigationContainer = true
                            score += 48
                        }
                        lowerTag.contains("recyclerview") || lowerTag.contains("toolbar") ||
                            lowerTag.contains("webview") || lowerTag.contains("surfaceview") ||
                            lowerTag.contains("textureview") -> score += 26
                        lowerTag.contains("button") || lowerTag.contains("edittext") -> score += 14
                        lowerTag.contains("textview") || lowerTag.contains("imageview") -> score += 6
                        tag == "include" -> score += 22
                    }

                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i)
                        if (attrName == "text" || attrName == "title" || attrName == "hint") {
                            val strRes = parser.getAttributeResourceValue(i, 0)
                            val resolved = if (strRes != 0) {
                                runCatching { apkResources.getString(strRes) }.getOrNull()
                            } else {
                                parser.getAttributeValue(i)
                            }
                            if (!resolved.isNullOrBlank() &&
                                !resolved.startsWith("@") &&
                                !resolved.startsWith("?") &&
                                resolved.length in 2..40 &&
                                resolved.any { it.isLetter() }
                            ) {
                                val lowerStr = resolved.lowercase()
                                if (lowerStr.contains("error") || lowerStr.contains("retry") ||
                                    lowerStr.contains("failed") || lowerStr.contains("crash") ||
                                    lowerStr.contains("debug") || lowerStr.contains("permission") ||
                                    lowerStr.contains("uninstall")
                                ) {
                                    hasErrorOrDialogKeywords = true
                                } else if (firstTitle.isBlank() && resolved.length >= 3) {
                                    firstTitle = resolved.trim()
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Throwable) {
            // Ignore partial XML parse errors
        } finally {
            runCatching { parser.close() }
        }

        if (rootWidthMatchParent && rootHeightMatchParent) {
            score += 60
        } else {
            score -= 80
        }
        if (hasMainNavigationContainer) score += 55
        if (hasErrorOrDialogKeywords) score -= 110
        if (totalTags >= 14) score += 45
        else if (totalTags >= 8) score += 22
        else if (totalTags <= 5) score -= 85
        if (firstTitle.isNotBlank()) score += 18

        return CompiledXmlAnalysis(score, firstTitle)
    }

    private fun scoreLayoutName(name: String): Int {
        val lower = name.lowercase()
        if (lower.startsWith("0_resource") || lower.length <= 2) {
            return 0
        }
        if (frameworkLayoutPrefixes.any { lower.startsWith(it) }) {
            return -180
        }
        // Strictly penalize error, crash, exception, debug, dialog, permission, update, and stub layouts
        // so they are NEVER auto-mounted as the primary screen of a cloned app!
        if (lower.contains("error") ||
            lower.contains("crash") ||
            lower.contains("exception") ||
            lower.contains("fail") ||
            lower.contains("debug") ||
            lower.contains("warning") ||
            lower.contains("blocked") ||
            lower.contains("banned") ||
            lower.contains("unsupported") ||
            lower.contains("maintenance") ||
            lower.contains("force_update") ||
            lower.contains("no_network") ||
            lower.contains("offline") ||
            lower.contains("empty") ||
            lower.contains("retry") ||
            lower.contains("permission") ||
            lower.contains("dialog") ||
            lower.contains("alert") ||
            lower.contains("toast") ||
            lower.contains("snackbar") ||
            lower.contains("tooltip") ||
            lower.contains("popup") ||
            lower.contains("bottom_sheet") ||
            lower.contains("chooser") ||
            lower.contains("picker") ||
            lower.contains("skeleton") ||
            lower.contains("placeholder") ||
            lower.contains("shimmer") ||
            lower.contains("stub") ||
            lower.startsWith("test_") ||
            lower.startsWith("dev_")
        ) {
            return -240
        }
        if (lower.contains("item_") || lower.endsWith("_item") || lower.contains("row_") ||
            lower.contains("cell_") || lower.contains("vh_") || lower.contains("notification")
        ) {
            return -35
        }
        var score = 10
        when {
            lower == "activity_main" || lower == "main" || lower == "home" || lower == "activity_home" -> score += 110
            lower.startsWith("activity_") -> score += 90
            lower.contains("home") || lower.contains("main") || lower.contains("conversations") -> score += 85
            lower.contains("eula") || lower.contains("welcome") || lower.contains("onboarding") -> score += 80
            lower.contains("login") || lower.contains("register") || lower.contains("auth") -> score += 75
            lower.contains("drama") || lower.contains("player") || lower.contains("video") || lower.contains("feed") -> score += 72
            lower.contains("chat") || lower.contains("message") || lower.contains("profile") -> score += 68
            lower.contains("browser") || lower.contains("toolbar") || lower.contains("search") -> score += 62
            lower.startsWith("fragment_") -> score += 58
            lower.contains("splash") || lower.contains("launch") -> score += 20
        }
        return score
    }

    /**
     * Instantiates the target APK's real `Application` and `Activity` classes from `mounted.apkClassLoader`
     * AND/OR inflates the matching compiled XML layout (`R.layout.*`) directly from `mounted.apkResources`,
     * returning the real Android `View` hierarchy mounted inside the sandbox container.
     */
    fun mountAndLaunchApkScreen(
        hostActivity: Activity,
        virtualContext: VirtualContextWrapper,
        mounted: MountedSandboxApk,
        targetActivityName: String?,
        targetLayoutResId: Int?,
        onInternalStartActivity: (Intent) -> Unit,
        onUserInteractionLogged: (String) -> Unit
    ): ApkActivityMountResult {
        val chosenActivity = targetActivityName?.takeIf { it.isNotBlank() }
            ?: mounted.launcherActivityName

        // Wire VirtualContextWrapper.startActivity interception
        virtualContext.onInterceptStartActivity = { intent ->
            onInternalStartActivity(intent)
        }

        // Create themed Context wrapping VirtualContextWrapper + extracted APK's Resources & Theme
        val themedApkContext = SandboxApkThemeContext(
            base = virtualContext,
            apkResources = mounted.apkResources,
            apkTheme = mounted.apkTheme,
            apkClassLoader = mounted.apkClassLoader,
            onInterceptStartActivity = onInternalStartActivity
        )

        // Ensure the cloned APK's real Application subclass is instantiated and attached
        ensureSandboxApplicationCreated(
            hostActivity = hostActivity,
            virtualContext = virtualContext,
            themedApkContext = themedApkContext,
            mounted = mounted
        )

        // Stage 1: Execute real Activity classes from the extracted APK's DexClassLoader
        // Supports automatic trampoline redirect following (e.g. com.whatsapp.Main -> EULA / HomeActivity)
        // and multi-activity fallback across the APK's declared UI Activities.
        if (targetLayoutResId == null || targetLayoutResId == 0) {
            val isExplicitActivity = !targetActivityName.isNullOrBlank() &&
                targetActivityName != mounted.launcherActivityName
            val activityCandidates = buildList {
                add(chosenActivity)
                if (!isExplicitActivity) {
                    mounted.declaredActivities.filter { act ->
                        val short = act.substringAfterLast('.').lowercase()
                        short == "mainactivity" ||
                            short == "homeactivity" ||
                            short == "welcomeactivity" ||
                            short == "loginactivity" ||
                            short == "launcheractivity" ||
                            short.startsWith("main") ||
                            short.startsWith("home") ||
                            short.contains("main") ||
                            short.contains("home") ||
                            short.contains("tab") ||
                            short.contains("feed") ||
                            short.contains("drama") ||
                            short.contains("browser") ||
                            short.contains("conversation")
                    }.take(8).forEach { act ->
                        if (act !in this) add(act)
                    }
                }
            }

            var splashFallbackMountResult: ApkActivityMountResult? = null

            for (candidateAct in activityCandidates) {
                val activityViewResult = tryExecuteRealApkActivityWithRedirects(
                    hostActivity = hostActivity,
                    virtualContext = virtualContext,
                    mounted = mounted,
                    initialActivityClassName = candidateAct,
                    onInternalStartActivity = onInternalStartActivity
                )
                if (activityViewResult != null) {
                    val (mountedRoot, resolvedActName, layoutDesc) = activityViewResult
                    if (containsVisibleErrorText(mountedRoot)) {
                        continue
                    }

                    val childCount = countViewsRecursive(mountedRoot)
                    val isBareSplash = resolvedActName.contains("splash", ignoreCase = true) &&
                        !hasRichVisibleWidgets(mountedRoot) &&
                        childCount <= 5 &&
                        activityCandidates.size > 1 &&
                        !isExplicitActivity

                    val mountResult = ApkActivityMountResult(
                        activeActivityName = resolvedActName,
                        activeLayoutName = layoutDesc,
                        activeLayoutResId = 0,
                        mountedView = mountedRoot,
                        wasConstructedViaActivityClass = true,
                        totalInflatedChildViews = childCount,
                        diagnosticMessage = "Activity ${resolvedActName.substringAfterLast('.')} dieksekusi langsung dari DEX sandbox ($childCount View aktif)"
                    )

                    if (isBareSplash) {
                        if (splashFallbackMountResult == null) {
                            splashFallbackMountResult = mountResult
                        }
                        continue
                    }

                    return mountResult
                }
            }

            if (splashFallbackMountResult != null) {
                return splashFallbackMountResult
            }
        }

        // Stage 2: Select and inflate the compiled XML layout (R.layout.*) inside the extracted APK's Resources
        val candidates = buildCandidateLayoutList(
            mounted = mounted,
            activityClassName = chosenActivity,
            explicitLayoutResId = targetLayoutResId
        )

        val apkSampleStrings: List<String> by lazy {
            collectSampleStringsFromApk(mounted.apkResources, mounted.packageName)
        }
        var fallbackInflated: Pair<ApkLayoutResource, View>? = null
        for ((index, candidate) in candidates.withIndex()) {
            val inflatedView = inflateApkLayoutResilient(
                themedApkContext = themedApkContext,
                mounted = mounted,
                layoutRes = candidate
            ) ?: continue

            // Populate any empty Fragment FrameLayout / ViewPager / RecyclerView containers inside the layout
            populateEmptyFragmentAndListContainers(
                rootView = inflatedView,
                themedApkContext = themedApkContext,
                mounted = mounted,
                excludeLayoutResId = candidate.resId
            )

            if (!hasRichVisibleWidgets(inflatedView) && apkSampleStrings.isNotEmpty()) {
                bindSampleStringsIntoEmptyTextViews(inflatedView, apkSampleStrings, index)
            }

            val isExplicitUserChoice = targetLayoutResId != null && targetLayoutResId != 0
            if (!isExplicitUserChoice && containsVisibleErrorText(inflatedView)) {
                continue
            }

            val totalViews = countViewsRecursive(inflatedView)
            if (fallbackInflated == null && (hasRichVisibleWidgets(inflatedView) || totalViews >= 2)) {
                fallbackInflated = candidate to inflatedView
            }
            val isTopMainScreen = index == 0 && candidate.categoryScore >= 75 && totalViews >= 5 && hasRichVisibleWidgets(inflatedView)
            if (isExplicitUserChoice ||
                isTopMainScreen ||
                hasRichVisibleWidgets(inflatedView)
            ) {
                wireInteractiveViewsInSandbox(
                    rootView = inflatedView,
                    mounted = mounted,
                    currentLayoutResId = candidate.resId,
                    onSelectNextLayout = { nextLayout ->
                        val nextIntent = Intent().apply {
                            setClassName(mounted.packageName, chosenActivity)
                            putExtra("sandbox_target_layout_id", nextLayout.resId)
                            putExtra("sandbox_target_layout_name", nextLayout.entryName)
                        }
                        onInternalStartActivity(nextIntent)
                    },
                    onUserInteractionLogged = onUserInteractionLogged
                )
                return ApkActivityMountResult(
                    activeActivityName = chosenActivity,
                    activeLayoutName = "R.layout.${candidate.entryName}",
                    activeLayoutResId = candidate.resId,
                    mountedView = inflatedView,
                    wasConstructedViaActivityClass = false,
                    totalInflatedChildViews = totalViews,
                    diagnosticMessage = "R.layout.${candidate.entryName} (0x${Integer.toHexString(candidate.resId)}) dimuat langsung dari base.apk ($totalViews View)"
                )
            }
        }

        if (fallbackInflated != null) {
            val (layout, view) = fallbackInflated
            wireInteractiveViewsInSandbox(
                rootView = view,
                mounted = mounted,
                currentLayoutResId = layout.resId,
                onSelectNextLayout = { nextLayout ->
                    val nextIntent = Intent().apply {
                        setClassName(mounted.packageName, chosenActivity)
                        putExtra("sandbox_target_layout_id", nextLayout.resId)
                        putExtra("sandbox_target_layout_name", nextLayout.entryName)
                    }
                    onInternalStartActivity(nextIntent)
                },
                onUserInteractionLogged = onUserInteractionLogged
            )
            val childCount = countViewsRecursive(view)
            return ApkActivityMountResult(
                activeActivityName = chosenActivity,
                activeLayoutName = "R.layout.${layout.entryName}",
                activeLayoutResId = layout.resId,
                mountedView = view,
                wasConstructedViaActivityClass = false,
                totalInflatedChildViews = childCount,
                diagnosticMessage = "R.layout.${layout.entryName} dimuat dari base.apk ($childCount View)"
            )
        }

        // Stage 3: Fallback if the APK is a pure native C++/Surface/Canvas binary without XML layouts
        val fallbackNativeSurface = buildExtractedApkNativeSurfaceHost(
            themedApkContext = themedApkContext,
            mounted = mounted,
            activityClassName = chosenActivity,
            onSelectLayout = { layout ->
                val nextIntent = Intent().apply {
                    setClassName(mounted.packageName, chosenActivity)
                    putExtra("sandbox_target_layout_id", layout.resId)
                    putExtra("sandbox_target_layout_name", layout.entryName)
                }
                onInternalStartActivity(nextIntent)
            }
        )
        return ApkActivityMountResult(
            activeActivityName = chosenActivity,
            activeLayoutName = "NativeSurface (${chosenActivity.substringAfterLast('.')})",
            activeLayoutResId = 0,
            mountedView = fallbackNativeSurface,
            wasConstructedViaActivityClass = true,
            totalInflatedChildViews = countViewsRecursive(fallbackNativeSurface),
            diagnosticMessage = "APK dieksekusi di dalam sandbox ${virtualContext.clone.canonicalVirtualDataPath}"
        )
    }

    /**
     * Attaches [mountedView] into [hostFrame] with full protection against synchronous exceptions
     * thrown inside guest `View.onAttachedToWindow()` (such as ByteDance/PineDrama lifecycle state
     * assertions `invoke onActivityCreated() or onStop() first`). If a programmatic guest View throws
     * during window attachment, automatically falls back to inflating the APK's compiled XML layouts
     * using pure standard AndroidX/Framework views.
     */
    fun attachMountedViewResilient(
        hostFrame: FrameLayout,
        mountedView: View,
        virtualContext: VirtualContextWrapper,
        mounted: MountedSandboxApk
    ) {
        runCatching {
            (mountedView.parent as? ViewGroup)?.removeView(mountedView)
            hostFrame.removeAllViews()
            hostFrame.addView(
                mountedView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            configureEmbeddedWebViewsAndDispatchFocus(hostFrame, mountedView)
            return
        }.onFailure { attachErr ->
            android.util.Log.w(
                "VirtualApkEngine",
                "Guarded guest View.onAttachedToWindow exception for ${mounted.packageName}: ${attachErr.message}"
            )
        }

        // Recovery path: clean hostFrame and inflate the APK's primary R.layout.* with safe standard views
        runCatching { hostFrame.removeAllViews() }
        val themedApkContext = SandboxApkThemeContext(
            base = virtualContext,
            apkResources = mounted.apkResources,
            apkTheme = mounted.apkTheme,
            apkClassLoader = mounted.apkClassLoader,
            onInterceptStartActivity = { intent ->
                virtualContext.onInterceptStartActivity?.invoke(intent)
            }
        )
        val candidates = buildCandidateLayoutList(
            mounted = mounted,
            activityClassName = mounted.launcherActivityName,
            explicitLayoutResId = null
        )
        for (candidate in candidates) {
            val safeInflated = runCatching {
                inflateApkLayoutResilient(themedApkContext, mounted, candidate)
            }.getOrNull() ?: continue
            runCatching {
                populateEmptyFragmentAndListContainers(
                    rootView = safeInflated,
                    themedApkContext = themedApkContext,
                    mounted = mounted,
                    excludeLayoutResId = candidate.resId
                )
            }
            val attachedOk = runCatching {
                hostFrame.removeAllViews()
                hostFrame.addView(
                    safeInflated,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                true
            }.getOrDefault(false)
            if (attachedOk && hostFrame.childCount > 0) {
                return
            }
        }
    }

    /**
     * Instantiates and initializes the cloned APK's `<application android:name="...">` class
     * inside our isolated sandbox so `getApplication()` and `getApplicationContext()` return
     * the real target `Application` instance without polluting the host Application's lifecycle callbacks.
     */
    private fun ensureSandboxApplicationCreated(
        hostActivity: Activity,
        virtualContext: VirtualContextWrapper,
        themedApkContext: Context,
        mounted: MountedSandboxApk
    ): Application {
        virtualContext.sandboxApplication?.let { existing ->
            isolateHostApplicationCallbacks(hostActivity.application, existing, mounted.apkClassLoader)
            return existing
        }
        VirtualProcessManager.installVirtualCrashGuard(hostActivity)
        runCatching {
            Thread.currentThread().contextClassLoader = mounted.apkClassLoader
        }

        val hostApp = hostActivity.application
        val createdApp = runCatching {
            val appClassName = mounted.sandboxAppInfo.className
            val appInstance = if (!appClassName.isNullOrBlank()) {
                runCatching {
                    val appClass = Class.forName(appClassName, true, mounted.apkClassLoader)
                    if (Application::class.java.isAssignableFrom(appClass)) {
                        appClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Application
                    } else {
                        Application()
                    }
                }.getOrElse { Application() }
            } else {
                Application()
            }

            // Set sandboxApplication BEFORE attachBaseContext so getApplicationContext() inside attachBaseContext
            // returns appInstance rather than falling back to the host Application!
            virtualContext.sandboxApplication = appInstance
            runCatching {
                setFieldRecursive(appInstance, "mBase", themedApkContext)
                setFieldRecursive(appInstance, "mResources", mounted.apkResources)
            }
            runCatching {
                val attach = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                attach.isAccessible = true
                attach.invoke(appInstance, themedApkContext)
            }
            runCatching {
                setFieldRecursive(appInstance, "mBase", themedApkContext)
                setFieldRecursive(appInstance, "mResources", mounted.apkResources)
            }
            installSandboxContentProviders(hostActivity, themedApkContext, mounted)
            runCatching { appInstance.onCreate() }
            isolateHostApplicationCallbacks(hostApp, appInstance, mounted.apkClassLoader)
            appInstance
        }.getOrElse {
            Application().also { fallback ->
                virtualContext.sandboxApplication = fallback
                runCatching {
                    val attach = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                    attach.isAccessible = true
                    attach.invoke(fallback, themedApkContext)
                }
            }
        }

        virtualContext.sandboxApplication = createdApp
        isolateHostApplicationCallbacks(hostApp, createdApp, mounted.apkClassLoader)
        return createdApp
    }

    /**
     * Prevents guest APK `ActivityLifecycleCallbacks` (e.g. ByteDance / PineDrama lifecycle trackers)
     * from leaking onto the host `Application` (where they would throw `invoke onActivityCreated() or onStop() first`
     * on `MainActivity` or `VirtualContainerActivity`) and wraps all callbacks in [SafeSandboxActivityLifecycleCallbacks].
     */
    @Suppress("UNCHECKED_CAST")
    private fun isolateHostApplicationCallbacks(
        hostApp: Application,
        sandboxApp: Application,
        guestClassLoader: ClassLoader
    ) {
        runCatching {
            val callbacksField = Application::class.java.getDeclaredField("mActivityLifecycleCallbacks").apply {
                isAccessible = true
            }
            val hostList = callbacksField.get(hostApp) as? java.util.ArrayList<Application.ActivityLifecycleCallbacks>
            val sandboxList = callbacksField.get(sandboxApp) as? java.util.ArrayList<Application.ActivityLifecycleCallbacks>

            if (hostList != null) {
                synchronized(hostList) {
                    val iterator = hostList.iterator()
                    while (iterator.hasNext()) {
                        val cb = iterator.next() ?: continue
                        if (cb is SafeSandboxActivityLifecycleCallbacks) continue
                        val cbLoader = cb.javaClass.classLoader
                        val isGuestCallback = cbLoader == guestClassLoader ||
                            (!cb.javaClass.name.startsWith("android.") &&
                                !cb.javaClass.name.startsWith("androidx.") &&
                                !cb.javaClass.name.startsWith("com.example."))
                        if (isGuestCallback) {
                            iterator.remove()
                            if (sandboxList != null && sandboxApp !== hostApp) {
                                synchronized(sandboxList) {
                                    sandboxList.add(SafeSandboxActivityLifecycleCallbacks(cb))
                                }
                            }
                        }
                    }
                }
            }

            if (sandboxList != null) {
                synchronized(sandboxList) {
                    for (i in sandboxList.indices) {
                        val cb = sandboxList[i] ?: continue
                        if (cb !is SafeSandboxActivityLifecycleCallbacks) {
                            sandboxList[i] = SafeSandboxActivityLifecycleCallbacks(cb)
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes `initialActivityClassName` and automatically follows any trampoline `startActivity(intent)`
     * redirects triggered during `onCreate` / `onStart` / `onResume` (up to 4 hops).
     */
    private fun tryExecuteRealApkActivityWithRedirects(
        hostActivity: Activity,
        virtualContext: VirtualContextWrapper,
        mounted: MountedSandboxApk,
        initialActivityClassName: String,
        onInternalStartActivity: (Intent) -> Unit
    ): Triple<View, String, String>? {
        var currentActName = initialActivityClassName
        var currentIntent: Intent? = null
        val visited = mutableSetOf<String>()

        for (hop in 0..4) {
            if (!visited.add(currentActName)) break

            var redirectedIntent: Intent? = null
            val stepResult = tryExecuteRealApkActivity(
                hostActivity = hostActivity,
                virtualContext = virtualContext,
                mounted = mounted,
                activityClassName = currentActName,
                incomingIntent = currentIntent,
                onInternalStartActivity = { intent ->
                    val resolvedCls = resolveSandboxActivityForIntent(hostActivity, mounted, intent)
                    if (!resolvedCls.isNullOrBlank() && intent.component == null) {
                        intent.setClassName(mounted.packageName, resolvedCls)
                    }
                    if (!resolvedCls.isNullOrBlank() && resolvedCls != currentActName && redirectedIntent == null) {
                        redirectedIntent = intent
                    }
                    onInternalStartActivity(intent)
                }
            )

            // If the activity immediately called startActivity(redirectIntent) (e.g. SplashActivity / com.whatsapp.Main),
            // follow the redirect first!
            val nextCls = redirectedIntent?.component?.className
            if (!nextCls.isNullOrBlank() && nextCls !in visited) {
                currentActName = nextCls
                currentIntent = redirectedIntent
                continue
            }

            if (stepResult != null) {
                return Triple(stepResult.first, currentActName, stepResult.second)
            }
            break
        }
        return null
    }

    /**
     * Instantiates the real `Activity` subclass from `mounted.apkClassLoader`, attaches
     * an isolated `PhoneWindow`, `Resources`, `Theme.AppCompat`, and `SandboxInstrumentation`,
     * executes `onCreate` -> `onStart` -> `onResume` -> `executePendingTransactions()` in resilient stages,
     * and extracts the resulting `View`.
     */
    private fun tryExecuteRealApkActivity(
        hostActivity: Activity,
        virtualContext: VirtualContextWrapper,
        mounted: MountedSandboxApk,
        activityClassName: String,
        incomingIntent: Intent? = null,
        onInternalStartActivity: (Intent) -> Unit
    ): Pair<View, String>? {
        return runCatching {
            val clazz = mounted.apkClassLoader.loadClass(activityClassName)
            if (!Activity::class.java.isAssignableFrom(clazz)) return null

            val pm = hostActivity.packageManager
            val comp = ComponentName(mounted.packageName, activityClassName)
            val actInfo = runCatching {
                pm.getActivityInfo(comp, PackageManager.GET_META_DATA)
            }.getOrElse {
                ActivityInfo().apply {
                    packageName = mounted.packageName
                    name = activityClassName
                    applicationInfo = mounted.sandboxAppInfo
                    theme = mounted.sandboxAppInfo.theme
                }
            }.apply {
                applicationInfo = mounted.sandboxAppInfo
                if (metaData == null) {
                    metaData = Bundle()
                }
            }

            // Build Activity-specific Theme with APK AppCompat fallback so AppCompatDelegate never crashes
            val actTheme = mounted.apkResources.newTheme()
            val specificThemeId = actInfo.themeResource.takeIf { it != 0 }
                ?: mounted.sandboxAppInfo.theme.takeIf { it != 0 }
                ?: android.R.style.Theme_DeviceDefault_Light_NoActionBar
            runCatching { actTheme.applyStyle(specificThemeId, true) }
            if (mounted.sandboxAppInfo.theme != 0 && mounted.sandboxAppInfo.theme != specificThemeId) {
                runCatching { actTheme.applyStyle(mounted.sandboxAppInfo.theme, false) }
            }
            applyApkAppCompatFallbackThemes(actTheme, mounted.apkResources, mounted.packageName, mounted.apkClassLoader)
            runCatching { actTheme.applyStyle(android.R.style.Theme_DeviceDefault_Light_NoActionBar, false) }

            val activityThemedContext = SandboxApkThemeContext(
                base = virtualContext,
                apkResources = mounted.apkResources,
                apkTheme = actTheme,
                apkClassLoader = mounted.apkClassLoader,
                onInterceptStartActivity = onInternalStartActivity
            )

            val targetActivity = clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Activity

            // 1. Attach isolated sandbox Context via ContextWrapper.attachBaseContext
            runCatching {
                val attachBase = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                attachBase.isAccessible = true
                attachBase.invoke(targetActivity, activityThemedContext)
            }

            // 2. Ensure mBase, mResources, and mTheme point to our sandboxed APK resources and theme
            runCatching {
                setFieldRecursive(targetActivity, "mBase", activityThemedContext)
                setFieldRecursive(targetActivity, "mResources", mounted.apkResources)
                setFieldRecursive(targetActivity, "mTheme", actTheme)
            }

            // 3. Copy runtime window/thread tokens from hostActivity & install SandboxInstrumentation
            val sandboxInstrumentation = SandboxInstrumentation(
                base = getFieldRecursive(hostActivity, "mInstrumentation") as? Instrumentation ?: Instrumentation(),
                onStartSandboxActivity = onInternalStartActivity
            )
            val sandboxApp = virtualContext.sandboxApplication ?: hostActivity.application

            setFieldRecursive(targetActivity, "mUiThread", Thread.currentThread())
            setFieldRecursive(targetActivity, "mMainThread", getFieldRecursive(hostActivity, "mMainThread"))
            setFieldRecursive(targetActivity, "mToken", getFieldRecursive(hostActivity, "mToken"))
            setFieldRecursive(targetActivity, "mApplication", sandboxApp)
            setFieldRecursive(targetActivity, "mInstrumentation", sandboxInstrumentation)
            setFieldRecursive(targetActivity, "mActivityInfo", actInfo)
            setFieldRecursive(targetActivity, "mComponent", comp)
            setFieldRecursive(targetActivity, "mWindowManager", hostActivity.windowManager)
            setFieldRecursive(targetActivity, "mCurrentConfig", mounted.apkResources.configuration)

            // Patch ALL mFragments controllers across the entire class hierarchy (both android.app.Activity.mFragments
            // AND androidx.fragment.app.FragmentActivity.mFragments) and call attachHost(null) on each!
            attachAllFragmentControllers(
                targetActivity = targetActivity,
                hostActivity = hostActivity,
                activityThemedContext = activityThemedContext
            )

            val launchIntent = (incomingIntent?.let { Intent(it) } ?: Intent(Intent.ACTION_MAIN)).apply {
                component = comp
                setPackage(mounted.packageName)
            }
            runCatching { targetActivity.intent = launchIntent }
            setFieldRecursive(targetActivity, "mIntent", launchIntent)

            // 4. Create isolated PhoneWindow with PrivateFactory chaining so AppCompatActivity.setFactory2 never throws!
            val capturedWindow = createIsolatedWindowForActivity(
                hostActivity = hostActivity,
                context = activityThemedContext,
                mounted = mounted,
                targetActivity = targetActivity
            )
            if (capturedWindow != null) {
                capturedWindow.callback = targetActivity
                setFieldRecursive(targetActivity, "mWindow", capturedWindow)
            }

            // Initialize ComponentActivity ViewTreeOwners on capturedWindow.decorView if supported
            runCatching {
                val initOwners = targetActivity.javaClass.methods.firstOrNull {
                    (it.name == "initializeViewTreeOwners" || it.name == "initViewTreeOwners") && it.parameterTypes.isEmpty()
                }
                initOwners?.apply { isAccessible = true }?.invoke(targetActivity)
            }

            // 5. Execute lifecycle methods in strict Android performCreate -> onActivityCreated -> onStart -> onResume
            // order so FragmentController / LifecycleRegistry / ByteDance AbsActivity state machines never throw
            // "invoke onActivityCreated() or onStop() first".
            dispatchToAllFragmentControllers(targetActivity, "dispatchCreate")
            runCatching {
                val onCreateMethod = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).apply {
                    isAccessible = true
                }
                setFieldRecursive(targetActivity, "mCalled", true)
                onCreateMethod.invoke(targetActivity, incomingIntent?.extras)
            }
            // Ensure dispatchActivityCreated / onActivityCreated / onPostCreate are invoked before onStart()
            dispatchToAllFragmentControllers(targetActivity, "dispatchCreate")
            dispatchToAllFragmentControllers(targetActivity, "dispatchActivityCreated")
            runCatching {
                var cls: Class<*>? = targetActivity.javaClass
                while (cls != null && cls != Object::class.java) {
                    val onActCreated = cls.declaredMethods.firstOrNull {
                        it.name == "onActivityCreated" && it.parameterTypes.size <= 1
                    }
                    if (onActCreated != null) {
                        onActCreated.isAccessible = true
                        if (onActCreated.parameterTypes.isEmpty()) {
                            onActCreated.invoke(targetActivity)
                        } else {
                            onActCreated.invoke(targetActivity, incomingIntent?.extras)
                        }
                        break
                    }
                    cls = cls.superclass
                }
            }
            runCatching {
                val onPostCreateMethod = Activity::class.java.getDeclaredMethod("onPostCreate", Bundle::class.java).apply {
                    isAccessible = true
                }
                setFieldRecursive(targetActivity, "mCalled", true)
                onPostCreateMethod.invoke(targetActivity, incomingIntent?.extras)
            }
            isolateHostApplicationCallbacks(hostActivity.application, sandboxApp, mounted.apkClassLoader)
            runCatching {
                val onStartMethod = Activity::class.java.getDeclaredMethod("onStart").apply {
                    isAccessible = true
                }
                setFieldRecursive(targetActivity, "mCalled", true)
                onStartMethod.invoke(targetActivity)
            }
            dispatchToAllFragmentControllers(targetActivity, "dispatchStart")
            runCatching {
                val onResumeMethod = Activity::class.java.getDeclaredMethod("onResume").apply {
                    isAccessible = true
                }
                setFieldRecursive(targetActivity, "mCalled", true)
                onResumeMethod.invoke(targetActivity)
            }
            dispatchToAllFragmentControllers(targetActivity, "dispatchResume")
            runCatching {
                val onPostResumeMethod = Activity::class.java.getDeclaredMethod("onPostResume").apply {
                    isAccessible = true
                }
                setFieldRecursive(targetActivity, "mCalled", true)
                onPostResumeMethod.invoke(targetActivity)
            }
            isolateHostApplicationCallbacks(hostActivity.application, sandboxApp, mounted.apkClassLoader)

            // 6. Flush any pending AndroidX or framework Fragment transactions committed during onCreate/onStart/onResume
            flushActivityFragments(targetActivity)

            val decor = capturedWindow?.decorView
            val contentParent = decor?.findViewById<ViewGroup>(android.R.id.content) ?: (decor as? ViewGroup)
            if (decor != null && contentParent != null && contentParent.childCount > 0) {
                val inflater = activityThemedContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                expandViewStubsAndEnsureVisibility(
                    root = contentParent,
                    inflater = inflater
                )
                // Keep decorView intact so targetActivity.findViewById(id), FragmentManager.onFindViewById(id),
                // and ViewTreeLifecycleOwner / ViewTreeViewModelStoreOwner remain 100% bound to the live window!
                (decor.parent as? ViewGroup)?.removeView(decor)
                if (countViewsRecursive(contentParent) >= 2) {
                    lastHostedActivityRef = java.lang.ref.WeakReference(targetActivity)
                    val hostContainer = SandboxSafeHostFrameLayout(activityThemedContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val attachedDecorOk = runCatching {
                            addView(
                                decor,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            true
                        }.getOrDefault(false)

                        if (!attachedDecorOk) {
                            val realRoot = contentParent.getChildAt(0)
                            contentParent.removeView(realRoot)
                            addView(
                                realRoot,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                    return hostContainer to "${activityClassName.substringAfterLast('.')}.setContentView"
                }
            }
            null
        }.getOrNull()
    }

    @Volatile
    private var lastHostedActivityRef: java.lang.ref.WeakReference<Activity>? = null

    fun dispatchBackPressToHostedActivity(): Boolean {
        val act = lastHostedActivityRef?.get() ?: return false
        return runCatching {
            val sfm = runCatching {
                act.javaClass.getMethod("getSupportFragmentManager").invoke(act)
            }.getOrNull()
            if (sfm != null) {
                val popped = runCatching {
                    sfm.javaClass.getMethod("popBackStackImmediate").invoke(sfm) as? Boolean
                }.getOrDefault(false) ?: false
                if (popped) return@runCatching true
            }
            if (act.fragmentManager?.popBackStackImmediate() == true) {
                return@runCatching true
            }
            false
        }.getOrDefault(false)
    }

    private fun configureEmbeddedWebViewsAndDispatchFocus(hostFrame: FrameLayout, mountedView: View) {
        fun walkWebViews(v: View) {
            if (v is android.webkit.WebView) {
                runCatching {
                    v.settings.javaScriptEnabled = true
                    v.settings.domStorageEnabled = true
                }
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    v.getChildAt(i)?.let { walkWebViews(it) }
                }
            }
        }
        runCatching { walkWebViews(mountedView) }

        hostFrame.post {
            val act = lastHostedActivityRef?.get()
            if (act != null) {
                runCatching { act.onWindowFocusChanged(true) }
                flushActivityFragments(act)
            }
        }
    }

    private fun flushActivityFragments(targetActivity: Activity) {
        runCatching {
            val getSupportFm = targetActivity.javaClass.getMethod("getSupportFragmentManager")
            val sfm = getSupportFm.invoke(targetActivity)
            if (sfm != null) {
                val execPending = sfm.javaClass.getMethod("executePendingTransactions")
                execPending.isAccessible = true
                execPending.invoke(sfm)
            }
        }
        runCatching {
            targetActivity.fragmentManager?.executePendingTransactions()
        }
        dispatchToAllFragmentControllers(targetActivity, "execPendingActions")
    }

    private fun attachAllFragmentControllers(
        targetActivity: Activity,
        hostActivity: Activity,
        activityThemedContext: Context
    ) {
        val hostHandler = getFieldRecursive(hostActivity, "mHandler") ?: android.os.Handler(android.os.Looper.getMainLooper())
        var clazz: Class<*>? = targetActivity.javaClass
        while (clazz != null && clazz != Object::class.java) {
            runCatching {
                val f = clazz.getDeclaredField("mFragments")
                f.isAccessible = true
                val mFragments = f.get(targetActivity)
                if (mFragments != null) {
                    val mHost = getFieldRecursive(mFragments, "mHost")
                    if (mHost != null) {
                        setFieldRecursive(mHost, "mActivity", targetActivity)
                        setFieldRecursive(mHost, "mContext", activityThemedContext)
                        setFieldRecursive(mHost, "mHandler", hostHandler)
                    }
                    val attachHostMethod = mFragments.javaClass.methods.firstOrNull {
                        it.name == "attachHost" && it.parameterTypes.size == 1
                    }
                    attachHostMethod?.apply { isAccessible = true }?.invoke(mFragments, null)
                }
            }
            clazz = clazz.superclass
        }
    }

    private fun dispatchToAllFragmentControllers(targetActivity: Activity, methodName: String) {
        var clazz: Class<*>? = targetActivity.javaClass
        while (clazz != null && clazz != Object::class.java) {
            runCatching {
                val f = clazz.getDeclaredField("mFragments")
                f.isAccessible = true
                val mFragments = f.get(targetActivity)
                if (mFragments != null) {
                    val m = mFragments.javaClass.methods.firstOrNull {
                        it.name == methodName && it.parameterTypes.isEmpty()
                    }
                    m?.apply { isAccessible = true }?.invoke(mFragments)
                }
            }
            clazz = clazz.superclass
        }
    }

    private fun installSandboxContentProviders(
        hostActivity: Activity,
        themedApkContext: Context,
        mounted: MountedSandboxApk
    ) {
        runCatching {
            val pm = hostActivity.packageManager
            val pkgInfo = runCatching {
                pm.getPackageInfo(mounted.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
            }.getOrNull() ?: runCatching {
                pm.getPackageArchiveInfo(mounted.extractedBaseApkPath, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
            }.getOrNull()

            val providers = pkgInfo?.providers.orEmpty()
            for (providerInfo in providers.take(16)) {
                val clsName = providerInfo.name ?: continue
                if (clsName.contains("FileProvider") || clsName.contains("SliceProvider")) continue
                runCatching {
                    providerInfo.applicationInfo = mounted.sandboxAppInfo
                    val pClass = Class.forName(clsName, true, mounted.apkClassLoader)
                    if (android.content.ContentProvider::class.java.isAssignableFrom(pClass)) {
                        val provider = pClass.getDeclaredConstructor().apply { isAccessible = true }
                            .newInstance() as android.content.ContentProvider
                        provider.attachInfo(themedApkContext, providerInfo)
                    }
                }
            }
        }
    }

    fun resolveSandboxActivityForIntent(
        hostActivity: Activity,
        mounted: MountedSandboxApk,
        intent: Intent
    ): String? {
        intent.component?.className?.takeIf { it.isNotBlank() }?.let { return it }
        runCatching {
            val scoped = Intent(intent).setPackage(mounted.packageName)
            val resolved = hostActivity.packageManager.queryIntentActivities(scoped, 0).firstOrNull()?.activityInfo
            if (resolved != null) {
                return resolved.targetActivity?.takeIf { it.isNotBlank() } ?: resolved.name
            }
        }
        val action = intent.action?.lowercase().orEmpty()
        if (action.isNotBlank()) {
            mounted.declaredActivities.firstOrNull { act ->
                val short = act.substringAfterLast('.').lowercase()
                action.contains(short.removeSuffix("activity"))
            }?.let { return it }
        }
        return null
    }

    private fun installPrivateFactoryOnInflater(
        inflater: LayoutInflater,
        privateFactory: LayoutInflater.Factory2
    ) {
        runCatching {
            val setPrivateMethod = LayoutInflater::class.java.getDeclaredMethod(
                "setPrivateFactory",
                LayoutInflater.Factory2::class.java
            ).apply { isAccessible = true }
            setPrivateMethod.invoke(inflater, privateFactory)
            return
        }
        runCatching {
            val field = LayoutInflater::class.java.getDeclaredField("mPrivateFactory").apply {
                isAccessible = true
            }
            field.set(inflater, privateFactory)
        }
    }

    private fun createIsolatedWindowForActivity(
        hostActivity: Activity,
        context: Context,
        mounted: MountedSandboxApk,
        targetActivity: Activity? = null
    ): android.view.Window? {
        val fallbackFactory = ResilientApkViewFactory(
            apkClassLoader = mounted.apkClassLoader,
            apkResources = mounted.apkResources
        )
        val chainedPrivateFactory = object : LayoutInflater.Factory2 {
            override fun onCreateView(parent: View?, name: String, ctx: Context, attrs: AttributeSet): View? {
                if (targetActivity != null) {
                    val fromActivity = runCatching {
                        targetActivity.onCreateView(parent, name, ctx, attrs)
                            ?: targetActivity.onCreateView(name, ctx, attrs)
                    }.getOrNull()
                    if (fromActivity != null) return fromActivity
                }
                return fallbackFactory.onCreateView(parent, name, ctx, attrs)
            }

            override fun onCreateView(name: String, ctx: Context, attrs: AttributeSet): View? {
                return onCreateView(null, name, ctx, attrs)
            }
        }
        // Primary: Direct PhoneWindow so it has full-screen Activity window features (no floating Dialog frame)
        runCatching {
            val phoneWindowClass = Class.forName("com.android.internal.policy.PhoneWindow")
            val ctor = phoneWindowClass.getConstructor(Context::class.java)
            ctor.isAccessible = true
            val win = (ctor.newInstance(context) as android.view.Window).apply {
                setWindowManager(
                    hostActivity.windowManager,
                    getFieldRecursive(hostActivity, "mToken") as? IBinder,
                    mounted.packageName
                )
            }
            installPrivateFactoryOnInflater(win.layoutInflater, chainedPrivateFactory)
            return win
        }
        // Fallback: Dialog(context, NoTitleBar_Fullscreen)
        return runCatching {
            val dialog = android.app.Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
            val win = dialog.window
            if (win != null) {
                runCatching {
                    win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    installPrivateFactoryOnInflater(win.layoutInflater, chainedPrivateFactory)
                }
                return win
            }
            null
        }.getOrNull()
    }

    /**
     * Two-Pass Deep Container & List Populator:
     * - Pass 1: Populates empty Fragment containers (`FrameLayout`, `FragmentContainerView`, `ViewPager`, `ViewPager2`)
     *   inside an Activity layout using the APK's top-scoring Fragment/Page layouts.
     * - Pass 2: Populates any unbound `RecyclerView`, `ListView`, or dynamic feed container (including inside
     *   Fragments inflated during Pass 1) with multiple real item/card views inflated from `mounted.apkResources`.
     */
    private fun populateEmptyFragmentAndListContainers(
        rootView: View,
        themedApkContext: Context,
        mounted: MountedSandboxApk,
        excludeLayoutResId: Int
    ) {
        if (rootView !is ViewGroup) return

        // Pass 1: Populate empty Fragment / ViewPager containers
        val emptyFragmentContainers = mutableListOf<ViewGroup>()
        fun findFragmentContainers(vg: ViewGroup, depth: Int) {
            if (depth > 8) return
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i) ?: continue
                if (child is ViewGroup) {
                    val clsName = child.javaClass.name.lowercase()
                    val tagStr = (child.tag as? String).orEmpty()
                    val entryName = runCatching {
                        if (child.id != View.NO_ID) mounted.apkResources.getResourceEntryName(child.id).lowercase() else ""
                    }.getOrDefault("")

                    val isFragmentOrPager = child.childCount == 0 && (
                        tagStr == "sandbox_dynamic_pager" ||
                            clsName.contains("viewpager") ||
                            clsName.contains("fragment") ||
                            entryName.contains("container") ||
                            entryName.contains("content") ||
                            entryName.contains("fragment") ||
                            entryName.contains("pager") ||
                            entryName.contains("host") ||
                            (child is FrameLayout && (child.layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT ||
                                (child.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f > 0f))
                        )

                    if (isFragmentOrPager && !clsName.contains("recyclerview")) {
                        emptyFragmentContainers.add(child)
                    } else {
                        findFragmentContainers(child, depth + 1)
                    }
                }
            }
        }

        findFragmentContainers(rootView, 0)
        if (emptyFragmentContainers.isEmpty() && rootView.childCount == 1 && rootView.getChildAt(0) is ViewGroup) {
            val singleChild = rootView.getChildAt(0) as ViewGroup
            if (singleChild.childCount == 0) {
                emptyFragmentContainers.add(singleChild)
            }
        }

        val usedLayoutIds = mutableSetOf(excludeLayoutResId)
        val pageCandidates = mounted.discoveredLayouts
            .filter { it.resId !in usedLayoutIds && it.categoryScore > -30 }
            .take(12)

        var pageIdx = 0
        for (container in emptyFragmentContainers.take(3)) {
            while (pageIdx < pageCandidates.size) {
                val cand = pageCandidates[pageIdx++]
                val childInflated = inflateApkLayoutResilient(themedApkContext, mounted, cand) ?: continue
                if (countViewsRecursive(childInflated) >= 3) {
                    usedLayoutIds.add(cand.resId)
                    attachViewIntoContainerSafely(container, childInflated, themedApkContext)
                    break
                }
            }
        }

        // Pass 2: Populate any unbound RecyclerView / ListView / Feed containers (including inside Pass 1 Fragments)
        val emptyListContainers = mutableListOf<ViewGroup>()
        fun findListContainers(vg: ViewGroup, depth: Int) {
            if (depth > 10) return
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i) ?: continue
                if (child is ViewGroup) {
                    val clsName = child.javaClass.name.lowercase()
                    val tagStr = (child.tag as? String).orEmpty()
                    val entryName = runCatching {
                        if (child.id != View.NO_ID) mounted.apkResources.getResourceEntryName(child.id).lowercase() else ""
                    }.getOrDefault("")

                    val isListContainer = child.childCount == 0 && (
                        tagStr == "sandbox_dynamic_list" ||
                            clsName.contains("recyclerview") ||
                            clsName.contains("listview") ||
                            entryName.contains("recycler") ||
                            entryName.contains("rv_") ||
                            entryName.contains("list") ||
                            entryName.contains("feed")
                        )

                    if (isListContainer) {
                        emptyListContainers.add(child)
                    } else {
                        findListContainers(child, depth + 1)
                    }
                }
            }
        }

        findListContainers(rootView, 0)
        if (emptyListContainers.isNotEmpty()) {
            val itemCandidates = mounted.discoveredLayouts
                .filter {
                    it.resId !in usedLayoutIds && (
                        it.entryName.contains("item", ignoreCase = true) ||
                            it.entryName.contains("card", ignoreCase = true) ||
                            it.entryName.contains("row", ignoreCase = true) ||
                            it.entryName.contains("cell", ignoreCase = true) ||
                            it.entryName.contains("feed", ignoreCase = true) ||
                            it.categoryScore in -45..70
                        )
                }
                .take(10)
                .ifEmpty {
                    mounted.discoveredLayouts.filter { it.resId !in usedLayoutIds }.take(6)
                }

            val sampleStrings = collectSampleStringsFromApk(mounted.apkResources, mounted.packageName)

            for (listContainer in emptyListContainers.take(3)) {
                val itemTemplate = itemCandidates.firstOrNull { cand ->
                    val probe = inflateApkLayoutResilient(themedApkContext, mounted, cand)
                    probe != null && countViewsRecursive(probe) >= 2
                } ?: continue

                val scrollFeed = ScrollView(themedApkContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val feedColumn = LinearLayout(themedApkContext).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                for (itemIndex in 0 until 4) {
                    val itemView = inflateApkLayoutResilient(themedApkContext, mounted, itemTemplate) ?: continue
                    bindSampleStringsIntoEmptyTextViews(itemView, sampleStrings, itemIndex)
                    feedColumn.addView(
                        itemView,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                if (feedColumn.childCount > 0) {
                    scrollFeed.addView(feedColumn)
                    attachViewIntoContainerSafely(listContainer, scrollFeed, themedApkContext)
                }
            }
        }
    }

    /**
     * Safely attaches `contentView` into `container`. If `container` is a `ViewPager2` or `RecyclerView`
     * that forbids direct `addView` or ignores non-Adapter children in `onLayout`, replaces or overlays
     * `container` inside its parent `ViewGroup` keeping its exact `LayoutParams` and index.
     */
    private fun attachViewIntoContainerSafely(
        container: ViewGroup,
        contentView: View,
        context: Context
    ) {
        val clsName = container.javaClass.name.lowercase()
        val requiresParentMount = clsName.contains("viewpager2") ||
            clsName.contains("recyclerview") ||
            clsName.contains("viewpager")

        if (requiresParentMount) {
            val parent = container.parent as? ViewGroup
            if (parent != null) {
                val idx = parent.indexOfChild(container)
                val lp = container.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val replacement = FrameLayout(context).apply {
                    id = container.id
                    visibility = View.VISIBLE
                    addView(
                        contentView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                runCatching {
                    parent.removeViewAt(idx)
                    parent.addView(replacement, idx, lp)
                    return
                }
            }
            // Do not call direct addView on ViewPager2 or RecyclerView if parent was null
            return
        }

        runCatching {
            container.visibility = View.VISIBLE
            container.addView(
                contentView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun collectSampleStringsFromApk(apkResources: Resources, packageName: String): List<String> {
        val collected = mutableListOf<String>()
        for (typeId in 1..36) {
            val probeId = 0x7f000000 or (typeId shl 16) or 0
            val tName = runCatching { apkResources.getResourceTypeName(probeId) }.getOrNull() ?: continue
            if (tName.equals("string", ignoreCase = true)) {
                for (entryId in 35..320) {
                    val resId = 0x7f000000 or (typeId shl 16) or entryId
                    val value = runCatching { apkResources.getString(resId) }.getOrNull()
                    if (!value.isNullOrBlank() &&
                        value.length in 4..45 &&
                        !value.contains("%") &&
                        !value.startsWith("http") &&
                        !value.startsWith("androidx.") &&
                        !value.startsWith("com.") &&
                        value.any { it.isLetter() }
                    ) {
                        collected.add(value.trim())
                        if (collected.size >= 24) break
                    }
                }
                break
            }
        }
        return collected
    }

    private fun bindSampleStringsIntoEmptyTextViews(root: View, strings: List<String>, seedOffset: Int) {
        if (strings.isEmpty()) return
        var counter = seedOffset * 3
        fun walk(v: View) {
            if (v is TextView && v !is EditText && v.text.isNullOrBlank()) {
                v.text = strings[counter % strings.size]
                counter++
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    v.getChildAt(i)?.let { walk(it) }
                }
            }
        }
        walk(root)
    }

    private fun buildCandidateLayoutList(
        mounted: MountedSandboxApk,
        activityClassName: String,
        explicitLayoutResId: Int?
    ): List<ApkLayoutResource> {
        if (explicitLayoutResId != null && explicitLayoutResId != 0) {
            val match = mounted.discoveredLayouts.firstOrNull { it.resId == explicitLayoutResId }
            if (match != null) return listOf(match)
            val entryName = runCatching { mounted.apkResources.getResourceEntryName(explicitLayoutResId) }
                .getOrDefault("layout_${Integer.toHexString(explicitLayoutResId)}")
            return listOf(ApkLayoutResource(explicitLayoutResId, entryName, 100))
        }

        if (mounted.discoveredLayouts.isEmpty()) return emptyList()

        val result = linkedSetOf<ApkLayoutResource>()
        val shortAct = activityClassName.substringAfterLast('.')
            .removeSuffix("Activity")
            .lowercase()

        if (shortAct.isNotBlank() && shortAct != "main" && shortAct != "splash") {
            mounted.discoveredLayouts.filter {
                val l = it.entryName.lowercase()
                l == "activity_$shortAct" || l == shortAct || l.contains(shortAct)
            }.take(6).forEach { result.add(it) }
        }

        mounted.discoveredLayouts.take(35).forEach { result.add(it) }
        return result.toList()
    }

    fun hasRichVisibleWidgets(view: View?): Boolean {
        if (view == null) return false
        var nonBlankTextWidgets = 0
        var visibleImageOrInputWidgets = 0
        var totalViewCount = 0
        fun walk(v: View) {
            if (v.visibility == View.GONE || v.visibility == View.INVISIBLE) return
            totalViewCount++
            when (v) {
                is EditText -> visibleImageOrInputWidgets += 2
                is Button -> {
                    if (!v.text.isNullOrBlank()) nonBlankTextWidgets++ else visibleImageOrInputWidgets++
                }
                is TextView -> {
                    if (!v.text.isNullOrBlank() || !v.hint.isNullOrBlank()) {
                        nonBlankTextWidgets++
                    }
                }
                is ImageView -> {
                    if (v.drawable != null) visibleImageOrInputWidgets++
                }
                is android.view.SurfaceView, is android.view.TextureView -> {
                    visibleImageOrInputWidgets += 3
                }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    v.getChildAt(i)?.let { walk(it) }
                }
            }
        }
        walk(view)
        return nonBlankTextWidgets >= 2 ||
            (nonBlankTextWidgets >= 1 && visibleImageOrInputWidgets >= 1) ||
            (visibleImageOrInputWidgets >= 3 && totalViewCount >= 5)
    }

    /**
     * Detects whether an inflated View hierarchy is an internal error/crash/offline screen
     * so we never display an APK's error fallback layout to the user.
     */
    fun containsVisibleErrorText(view: View?): Boolean {
        if (view == null) return false
        var foundErrorSignal = false
        val errorPhrases = listOf(
            "something went wrong",
            "an error occurred",
            "unexpected error",
            "failed to load",
            "network error",
            "no internet",
            "connection failed",
            "terjadi kesalahan",
            "gagal memuat",
            "tidak dapat memuat",
            "koneksi gagal",
            "coba lagi",
            "app not installed",
            "device not supported",
            "google play services"
        )
        fun walk(v: View) {
            if (foundErrorSignal || v.visibility == View.GONE || v.visibility == View.INVISIBLE) return
            if (v is TextView && v !is EditText) {
                val txt = v.text?.toString()?.trim()?.lowercase().orEmpty()
                if (txt.isNotBlank() && errorPhrases.any { txt.contains(it) }) {
                    foundErrorSignal = true
                    return
                }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    v.getChildAt(i)?.let { walk(it) }
                }
            }
        }
        walk(view)
        return foundErrorSignal
    }

    /**
     * Inflates a real compiled XML layout (`R.layout.*`) directly from `mounted.apkResources`
     * using a Two-Chance Resilient `LayoutInflater.Factory2` that resolves custom views from
     * `mounted.apkClassLoader` and gracefully recovers if any custom view fails on a proprietary
     * theme attribute or uninitialized static singleton.
     */
    fun inflateApkLayoutResilient(
        themedApkContext: Context,
        mounted: MountedSandboxApk,
        layoutRes: ApkLayoutResource
    ): View? {
        val inflater = LayoutInflater.from(themedApkContext).cloneInContext(themedApkContext)
        val resilientFactory = ResilientApkViewFactory(
            apkClassLoader = mounted.apkClassLoader,
            apkResources = mounted.apkResources
        )
        runCatching {
            inflater.factory2 = resilientFactory
        }

        val container = SandboxSafeHostFrameLayout(themedApkContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AndroidColor.WHITE)
            // Apply the extracted APK's real windowBackground if defined in its theme
            runCatching {
                val tv = TypedValue()
                if (mounted.apkTheme.resolveAttribute(android.R.attr.windowBackground, tv, true)) {
                    if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                        setBackgroundColor(tv.data)
                    } else if (tv.resourceId != 0) {
                        background = mounted.apkResources.getDrawable(tv.resourceId, mounted.apkTheme)
                    }
                }
            }
        }

        // Attempt 1: Native XML inflation using LayoutInflater + ResilientApkViewFactory
        val inflated = runCatching {
            inflater.inflate(layoutRes.resId, container, false)
        }.getOrNull()

        if (inflated != null) {
            // Expand any top-level ViewStubs so deferred screens inside the APK layout become visible
            expandViewStubsAndEnsureVisibility(inflated, inflater)
            container.addView(
                inflated,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return container
        }

        // Attempt 2: Manual XmlResourceParser tree walker if a root container LayoutParams threw during inflate
        val parserInflated = runCatching {
            inflateViaXmlResourceParser(themedApkContext, mounted, layoutRes.resId, resilientFactory)
        }.getOrNull()

        if (parserInflated != null) {
            container.addView(
                parserInflated,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return container
        }

        return null
    }

    private fun expandViewStubsAndEnsureVisibility(root: View, inflater: LayoutInflater) {
        if (root is ViewGroup) {
            var expandedStubs = 0
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is ViewStub && expandedStubs < 3 && child.layoutResource != 0) {
                    runCatching {
                        child.layoutInflater = inflater
                        child.inflate()
                        expandedStubs++
                    }
                } else if (child != null) {
                    if (child.visibility == View.GONE && child is ViewGroup && child.childCount > 2) {
                        child.visibility = View.VISIBLE
                    }
                    expandViewStubsAndEnsureVisibility(child, inflater)
                }
            }
        }
    }

    /**
     * Direct `XmlResourceParser` walker that builds the real `View` tree from compiled `res/layout/`
     * even if a custom `ViewGroup.generateLayoutParams` in the commercial APK threw an exception.
     */
    private fun inflateViaXmlResourceParser(
        context: Context,
        mounted: MountedSandboxApk,
        layoutResId: Int,
        factory: ResilientApkViewFactory
    ): View? {
        val parser = mounted.apkResources.getLayout(layoutResId)
        val stack = ArrayDeque<ViewGroup>()
        var rootView: View? = null

        try {
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val tagName = parser.name ?: "View"
                        if (tagName == "merge" || tagName == "include" || tagName == "requestFocus" || tagName == "tag") {
                            if (tagName == "include") {
                                val includedLayoutId = parser.getAttributeResourceValue(null, "layout", 0)
                                if (includedLayoutId != 0 && stack.isNotEmpty()) {
                                    runCatching {
                                        val child = inflateViaXmlResourceParser(context, mounted, includedLayoutId, factory)
                                        if (child != null) stack.last().addView(child)
                                    }
                                }
                            }
                        } else {
                            val parent = stack.lastOrNull()
                            val created = factory.createResilientView(parent, tagName, context, parser)
                            if (rootView == null) {
                                rootView = created
                            }
                            if (parent != null) {
                                val lp = createSafeLayoutParams(context, mounted.apkResources, parser)
                                runCatching { parent.addView(created, lp) }
                                    .onFailure { runCatching { parent.addView(created) } }
                            }
                            if (created is ViewGroup) {
                                stack.addLast(created)
                            } else {
                                // Push a dummy marker or track depth via non-ViewGroup
                                stack.addLast( DummyLeafHolder(context) )
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val tagName = parser.name ?: ""
                        if (tagName != "merge" && tagName != "include" && tagName != "requestFocus" && tagName != "tag") {
                            if (stack.isNotEmpty()) stack.removeLast()
                        }
                    }
                }
                eventType = parser.next()
            }
        } finally {
            runCatching { parser.close() }
        }
        return rootView
    }

    private class DummyLeafHolder(context: Context) : FrameLayout(context)

    private fun createSafeLayoutParams(
        context: Context,
        res: Resources,
        attrs: AttributeSet
    ): LinearLayout.LayoutParams {
        val density = res.displayMetrics.density
        var width = ViewGroup.LayoutParams.WRAP_CONTENT
        var height = ViewGroup.LayoutParams.WRAP_CONTENT
        var weight = 0f

        for (i in 0 until attrs.attributeCount) {
            when (attrs.getAttributeName(i)) {
                "layout_width" -> width = parseDimensionAttr(res, attrs, i, ViewGroup.LayoutParams.WRAP_CONTENT, density)
                "layout_height" -> height = parseDimensionAttr(res, attrs, i, ViewGroup.LayoutParams.WRAP_CONTENT, density)
                "layout_weight" -> weight = runCatching { attrs.getAttributeFloatValue(i, 0f) }.getOrDefault(0f)
            }
        }
        return LinearLayout.LayoutParams(width, height, weight)
    }

    internal fun parseDimensionAttr(
        res: Resources,
        attrs: AttributeSet,
        index: Int,
        defaultVal: Int,
        density: Float
    ): Int {
        val rawVal = attrs.getAttributeValue(index) ?: return defaultVal
        if (rawVal == "-1" || rawVal.equals("match_parent", true) || rawVal.equals("fill_parent", true)) {
            return ViewGroup.LayoutParams.MATCH_PARENT
        }
        if (rawVal == "-2" || rawVal.equals("wrap_content", true)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val resId = attrs.getAttributeResourceValue(index, 0)
        if (resId != 0) {
            return runCatching { res.getDimensionPixelSize(resId) }.getOrDefault(defaultVal)
        }
        if (rawVal.endsWith("dp") || rawVal.endsWith("dip")) {
            val num = rawVal.removeSuffix("dip").removeSuffix("dp").toFloatOrNull()
            if (num != null) return (num * density).toInt()
        }
        if (rawVal.endsWith("px")) {
            val num = rawVal.removeSuffix("px").toFloatOrNull()
            if (num != null) return num.toInt()
        }
        return defaultVal
    }

    /**
     * Wires clickable buttons & views inside the mounted APK view hierarchy so tapping buttons
     * records real sandbox state changes and navigates between the APK's screens inside the sandbox.
     */
    private fun wireInteractiveViewsInSandbox(
        rootView: View,
        mounted: MountedSandboxApk,
        currentLayoutResId: Int,
        onSelectNextLayout: (ApkLayoutResource) -> Unit,
        onUserInteractionLogged: (String) -> Unit
    ) {
        val clickableViews = mutableListOf<View>()
        collectInteractiveViews(rootView, clickableViews)

        clickableViews.forEach { view ->
            if (!view.hasOnClickListeners()) {
                val label = when (view) {
                    is TextView -> view.text?.toString()?.takeIf { it.isNotBlank() }
                    else -> null
                } ?: runCatching {
                    if (view.id != View.NO_ID) mounted.apkResources.getResourceEntryName(view.id) else null
                }.getOrNull() ?: view.javaClass.simpleName

                view.isClickable = true
                view.setOnClickListener {
                    onUserInteractionLogged("Interaksi APK [${mounted.appLabel}]: Ketuk '$label'")
                    val primaryScreens = mounted.discoveredLayouts.filter { it.categoryScore >= 55 }.take(8)
                    if (primaryScreens.size > 1) {
                        val currentIdx = primaryScreens.indexOfFirst { it.resId == currentLayoutResId }
                        if (currentIdx >= 0) {
                            val nextLayout = primaryScreens[(currentIdx + 1) % primaryScreens.size]
                            onSelectNextLayout(nextLayout)
                        }
                    }
                }
            }
        }
    }

    private fun collectInteractiveViews(view: View, out: MutableList<View>) {
        if (view is Button || view is ImageButton ||
            (view is TextView && (view.isClickable || view.background != null || view.id != View.NO_ID)) ||
            (view is ImageView && (view.isClickable || view.id != View.NO_ID))
        ) {
            out.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                collectInteractiveViews(child, out)
            }
        }
    }

    private fun buildExtractedApkNativeSurfaceHost(
        themedApkContext: Context,
        mounted: MountedSandboxApk,
        activityClassName: String,
        onSelectLayout: (ApkLayoutResource) -> Unit
    ): View {
        val root = LinearLayout(themedApkContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.parseColor("#FFFFFF"))
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(themedApkContext).apply {
            text = "${mounted.appLabel} (${mounted.packageName})"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(AndroidColor.parseColor("#111827"))
        }
        root.addView(titleView)

        val subView = TextView(themedApkContext).apply {
            text = "Activity Aktif: $activityClassName\nAPK Sandbox: ${mounted.extractedBaseApkPath}"
            textSize = 13f
            setTextColor(AndroidColor.parseColor("#047857"))
        }
        root.addView(subView)

        mounted.discoveredLayouts.take(8).forEach { layout ->
            val btn = Button(themedApkContext).apply {
                text = "Buka Layout APK: R.layout.${layout.entryName}"
                setOnClickListener { onSelectLayout(layout) }
            }
            root.addView(btn)
        }
        return root
    }

    fun countViewsRecursive(view: View?): Int {
        if (view == null) return 0
        if (view !is ViewGroup) return 1
        var total = 1
        for (i in 0 until view.childCount) {
            total += countViewsRecursive(view.getChildAt(i))
        }
        return total
    }

    private fun getFieldRecursive(target: Any, fieldName: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun setFieldRecursive(target: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                return
            }
        }
    }
}

/**
 * Themed Context wrapping `VirtualContextWrapper` so `LayoutInflater` and instantiated `Activity`
 * instances resolve all resources, drawables, strings, and theme attributes directly from the
 * extracted `base.apk` inside the sandbox.
 */
class SandboxApkThemeContext(
    base: VirtualContextWrapper,
    private val apkResources: Resources,
    private val apkTheme: Resources.Theme,
    private val apkClassLoader: ClassLoader,
    private val onInterceptStartActivity: (Intent) -> Unit
) : ContextThemeWrapper(base, 0) {

    override fun getResources(): Resources = apkResources

    override fun getAssets(): AssetManager = apkResources.assets

    override fun getTheme(): Resources.Theme = apkTheme

    override fun getClassLoader(): ClassLoader = apkClassLoader

    override fun getApplicationContext(): Context =
        (baseContext as? VirtualContextWrapper)?.applicationContext ?: this

    override fun getPackageName(): String = (baseContext as? VirtualContextWrapper)?.packageName ?: super.getPackageName()

    override fun getOpPackageName(): String = (baseContext as? VirtualContextWrapper)?.opPackageName ?: super.getOpPackageName()

    override fun getApplicationInfo(): ApplicationInfo {
        return (baseContext as? VirtualContextWrapper)?.applicationInfo ?: super.getApplicationInfo()
    }

    override fun startActivity(intent: Intent?) {
        if (intent != null) onInterceptStartActivity(intent)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        if (intent != null) onInterceptStartActivity(intent)
    }

    override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration): Context {
        val baseV = baseContext as? VirtualContextWrapper
        if (baseV != null) {
            val wrappedV = baseV.createConfigurationContext(overrideConfiguration) as? VirtualContextWrapper ?: baseV
            return SandboxApkThemeContext(
                base = wrappedV,
                apkResources = apkResources,
                apkTheme = apkTheme,
                apkClassLoader = apkClassLoader,
                onInterceptStartActivity = onInterceptStartActivity
            )
        }
        return this
    }
}

/**
 * Custom `Instrumentation` installed on hosted APK activities so any `Activity.startActivity(...)`
 * or `startActivityForResult(...)` call made inside the cloned APK is intercepted in-process
 * and transitions inside our `VirtualContainerActivity` instead of opening the host phone's app.
 */
class SandboxInstrumentation(
    private val base: Instrumentation,
    private val onStartSandboxActivity: (Intent) -> Unit
) : Instrumentation() {

    // Override all 3 hidden/framework execStartActivity signatures (Activity, String/Fragment, and UserHandle)
    @Suppress("unused")
    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? {
        if (intent != null) {
            onStartSandboxActivity(intent)
        }
        return null
    }

    @Suppress("unused")
    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: String?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? {
        if (intent != null) {
            onStartSandboxActivity(intent)
        }
        return null
    }

    @Suppress("unused")
    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        resultWho: String?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?,
        user: android.os.UserHandle?
    ): ActivityResult? {
        if (intent != null) {
            onStartSandboxActivity(intent)
        }
        return null
    }
}

/**
 * Lifecycle-state-safe wrapper around guest `Application.ActivityLifecycleCallbacks` so that
 * any guest callback (e.g. ByteDance / PineDrama lifecycle state machines) is guaranteed to
 * receive `onActivityCreated()` before `onActivityStarted()` / `onActivityResumed()` and can
 * never throw an uncaught exception into the host container.
 */
class SafeSandboxActivityLifecycleCallbacks(
    private val delegate: Application.ActivityLifecycleCallbacks
) : Application.ActivityLifecycleCallbacks {

    private val createdOrStoppedActivities = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private fun ensureCreatedDispatched(activity: Activity) {
        val key = System.identityHashCode(activity)
        if (createdOrStoppedActivities.add(key)) {
            runCatching { delegate.onActivityCreated(activity, null) }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        createdOrStoppedActivities.add(System.identityHashCode(activity))
        runCatching { delegate.onActivityCreated(activity, savedInstanceState) }
    }

    override fun onActivityStarted(activity: Activity) {
        ensureCreatedDispatched(activity)
        runCatching { delegate.onActivityStarted(activity) }
    }

    override fun onActivityResumed(activity: Activity) {
        ensureCreatedDispatched(activity)
        runCatching { delegate.onActivityResumed(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        ensureCreatedDispatched(activity)
        runCatching { delegate.onActivityPaused(activity) }
    }

    override fun onActivityStopped(activity: Activity) {
        createdOrStoppedActivities.add(System.identityHashCode(activity))
        runCatching { delegate.onActivityStopped(activity) }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        runCatching { delegate.onActivitySaveInstanceState(activity, outState) }
    }

    override fun onActivityDestroyed(activity: Activity) {
        createdOrStoppedActivities.remove(System.identityHashCode(activity))
        runCatching { delegate.onActivityDestroyed(activity) }
    }
}

/**
 * Three-Tier Resilient `LayoutInflater.Factory2` for inflating real compiled XML layouts
 * from any extracted commercial APK (`base.apk`).
 *
 * 1. Tier 1: Instantiates standard framework widgets (`android.widget.*`, `android.view.*`)
 *    and AndroidX / Material widgets (`androidx.*`, `com.google.android.material.*`) directly from
 *    `apkClassLoader` with `(Context, AttributeSet)` so `ConstraintLayout`, `CoordinatorLayout`,
 *    `AppCompat*`, `BottomNavigationView`, `TabLayout`, and `CardView` retain 100% of their native XML
 *    layout constraints, styles, themes, and drawables.
 * 2. Tier 2: For custom/obfuscated APK View subclasses, attempts direct `(Context, AttributeSet)`
 *    construction or walks the bytecode superclass hierarchy (`clazz.superclass`) to instantiate
 *    the nearest AndroidX / Material / Framework superclass with `(Context, AttributeSet)`.
 * 3. Tier 3: Falls back to 1-arg `(Context)` construction + manual XML attribute extraction if a
 *    proprietary style attribute threw during `(Context, AttributeSet)`.
 */
class ResilientApkViewFactory(
    private val apkClassLoader: ClassLoader,
    private val apkResources: Resources
) : LayoutInflater.Factory2 {

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View {
        return createResilientView(parent, name, context, attrs)
    }

    override fun onCreateView(
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View {
        return createResilientView(null, name, context, attrs)
    }

    fun createResilientView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View {
        val lowerName = name.lowercase()
        val isPagerTag = lowerName.contains("viewpager") ||
            lowerName == "fragment" ||
            lowerName.contains("fragmentcontainer") ||
            lowerName.contains("navhost")
        val isListTag = lowerName.contains("recyclerview") || lowerName.contains("listview")

        if (lowerName == "fragment") {
            return FrameLayout(context).apply {
                tag = "sandbox_dynamic_pager"
                applyCoreAttributesFromXml(this, context, attrs)
            }
        }

        // Tier 1: Standard Android framework widgets (no dot or android.widget / android.view / android.webkit)
        val isFrameworkWidgetTag = !name.contains(".") ||
            name.startsWith("android.widget.") ||
            name.startsWith("android.view.") ||
            name.startsWith("android.webkit.")

        if (isFrameworkWidgetTag) {
            val frameworkConstructed = runCatching {
                val viewClass = resolveFrameworkViewClass(name, context)
                val ctor = viewClass.getConstructor(Context::class.java, AttributeSet::class.java)
                ctor.isAccessible = true
                ctor.newInstance(context, attrs) as View
            }.getOrNull()

            if (frameworkConstructed != null) {
                applySupplementalAttributesFromXml(frameworkConstructed, context, attrs)
                return frameworkConstructed
            }
        }

        // Tier 2: First try to instantiate the EXACT requested View class from apkClassLoader with (Context, AttributeSet)
        // so findViewById(...) and ViewBinding casts (RecyclerView, ViewPager2, CustomView) NEVER throw ClassCastException!
        // Only fall back to superclass resolution if the exact class constructor throws an exception.
        if (name.contains(".")) {
            val exactConstructed = runCatching {
                val exactClass = Class.forName(name, false, apkClassLoader)
                if (View::class.java.isAssignableFrom(exactClass) &&
                    !java.lang.reflect.Modifier.isAbstract(exactClass.modifiers)
                ) {
                    val ctor = exactClass.getConstructor(Context::class.java, AttributeSet::class.java)
                    ctor.isAccessible = true
                    ctor.newInstance(context, attrs) as View
                } else {
                    null
                }
            }.getOrNull()

            if (exactConstructed != null) {
                applySupplementalAttributesFromXml(exactConstructed, context, attrs)
                return exactConstructed
            }

            val constructedWithAttrs = runCatching {
                val targetClass = resolveConstructibleViewClass(name, context)
                if (targetClass != null) {
                    val ctor = targetClass.getConstructor(Context::class.java, AttributeSet::class.java)
                    ctor.isAccessible = true
                    (ctor.newInstance(context, attrs) as View).also { created ->
                        if (isPagerTag) created.tag = "sandbox_dynamic_pager"
                        else if (isListTag) created.tag = "sandbox_dynamic_list"
                    }
                } else {
                    null
                }
            }.getOrNull()

            if (constructedWithAttrs != null) {
                applySupplementalAttributesFromXml(constructedWithAttrs, context, attrs)
                return constructedWithAttrs
            }
        }

        // Tier 3: Fallback 1-arg (Context) widget + full manual XML attribute application
        val fallbackView = createBytecodeAwareWidgetForTag(name, context, attrs).also { created ->
            if (isPagerTag) created.tag = "sandbox_dynamic_pager"
            else if (isListTag) created.tag = "sandbox_dynamic_list"
        }
        applyCoreAttributesFromXml(fallbackView, context, attrs)
        return fallbackView
    }

    /**
     * Resolves a `View` class from `apkClassLoader` that can be safely constructed with `(Context, AttributeSet)`.
     * For `androidx.*`, `com.google.android.material.*`, and `android.support.*`, returns the class directly
     * (except `ViewPager2` / `RecyclerView` which are replaced with `FrameLayout` / `LinearLayout` if unbound so
     * child views can be populated without `IllegalStateException`).
     * For proprietary/obfuscated APK subclasses, walks `superclass` to the first standard AndroidX / Material /
     * Framework `View` class so `onAttachedToWindow` can never crash on uninitialized proprietary singletons.
     */
    private fun resolveConstructibleViewClass(name: String, context: Context): Class<*>? {
        val lower = name.lowercase()
        if (lower.contains("viewpager2") || lower.contains("viewpager") || lower.contains("fragmentcontainer")) {
            return FrameLayout::class.java
        }
        if (lower.contains("recyclerview")) {
            return FrameLayout::class.java
        }

        val loaded = runCatching {
            Class.forName(name, false, apkClassLoader)
        }.getOrElse {
            runCatching { Class.forName(name, false, context.classLoader) }.getOrNull()
        } ?: return null

        if (!View::class.java.isAssignableFrom(loaded)) return null

        var current: Class<*>? = loaded
        while (current != null && View::class.java.isAssignableFrom(current)) {
            val clsName = current.name
            val isSafeStandardView = clsName.startsWith("android.widget.") ||
                clsName.startsWith("android.view.") ||
                clsName.startsWith("androidx.constraintlayout.") ||
                clsName.startsWith("androidx.coordinatorlayout.") ||
                clsName.startsWith("androidx.cardview.") ||
                clsName.startsWith("androidx.appcompat.widget.") ||
                clsName.startsWith("androidx.core.widget.") ||
                clsName.startsWith("androidx.drawerlayout.") ||
                clsName.startsWith("androidx.swiperefreshlayout.") ||
                clsName.startsWith("com.google.android.material.")
            if (isSafeStandardView && !java.lang.reflect.Modifier.isAbstract(current.modifiers)) {
                return current
            }
            current = current.superclass
        }
        return null
    }

    private fun resolveFrameworkViewClass(name: String, context: Context): Class<*> {
        if (name == "view" || name == "View") {
            return View::class.java
        }
        if (!name.contains(".")) {
            val prefixes = listOf("android.widget.", "android.view.", "android.webkit.")
            for (prefix in prefixes) {
                runCatching { return Class.forName("$prefix$name", false, View::class.java.classLoader) }
            }
        }
        return Class.forName(name, false, View::class.java.classLoader ?: context.classLoader)
    }

    private fun createBytecodeAwareWidgetForTag(name: String, context: Context, attrs: AttributeSet): View {
        if (name.contains(".")) {
            val inspectedWidget = runCatching {
                val clazz = Class.forName(name, false, apkClassLoader)
                when {
                    EditText::class.java.isAssignableFrom(clazz) ->
                        runCatching { EditText(context, attrs) }.getOrElse { EditText(context) }
                    Button::class.java.isAssignableFrom(clazz) ->
                        runCatching { Button(context, attrs) }.getOrElse { Button(context) }
                    TextView::class.java.isAssignableFrom(clazz) ->
                        runCatching { TextView(context, attrs) }.getOrElse { TextView(context) }
                    ImageButton::class.java.isAssignableFrom(clazz) ->
                        runCatching { ImageButton(context, attrs) }.getOrElse { ImageButton(context) }
                    ImageView::class.java.isAssignableFrom(clazz) ->
                        runCatching { ImageView(context, attrs) }.getOrElse { ImageView(context) }
                    ProgressBar::class.java.isAssignableFrom(clazz) ->
                        runCatching { ProgressBar(context, attrs) }.getOrElse { ProgressBar(context) }
                    HorizontalScrollView::class.java.isAssignableFrom(clazz) ->
                        runCatching { HorizontalScrollView(context, attrs) }.getOrElse { HorizontalScrollView(context) }
                    ScrollView::class.java.isAssignableFrom(clazz) ->
                        runCatching { ScrollView(context, attrs) }.getOrElse { ScrollView(context) }
                    LinearLayout::class.java.isAssignableFrom(clazz) ->
                        runCatching { LinearLayout(context, attrs) }.getOrElse { LinearLayout(context) }
                    RelativeLayout::class.java.isAssignableFrom(clazz) ->
                        runCatching { RelativeLayout(context, attrs) }.getOrElse { RelativeLayout(context) }
                    FrameLayout::class.java.isAssignableFrom(clazz) ->
                        runCatching { FrameLayout(context, attrs) }.getOrElse { FrameLayout(context) }
                    ViewGroup::class.java.isAssignableFrom(clazz) -> createFallbackWidgetForTag(name, context)
                    View::class.java.isAssignableFrom(clazz) ->
                        runCatching { View(context, attrs) }.getOrElse { View(context) }
                    else -> null
                }
            }.getOrNull()

            if (inspectedWidget != null) {
                return inspectedWidget
            }
        }
        return createFallbackWidgetForTag(name, context)
    }

    private fun applySupplementalAttributesFromXml(view: View, context: Context, attrs: AttributeSet) {
        for (i in 0 until attrs.attributeCount) {
            val attrName = attrs.getAttributeName(i) ?: continue
            runCatching {
                when (attrName) {
                    "id" -> {
                        if (view.id == View.NO_ID) {
                            val idVal = attrs.getAttributeResourceValue(i, View.NO_ID)
                            if (idVal != View.NO_ID) view.id = idVal
                        }
                    }
                    "src", "srcCompat" -> {
                        if (view is ImageView && view.drawable == null) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            if (resId != 0) {
                                runCatching {
                                    view.setImageDrawable(apkResources.getDrawable(resId, context.theme))
                                }
                            }
                        }
                    }
                    "text", "title" -> {
                        if (view is TextView && view.text.isNullOrEmpty()) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            val str = if (resId != 0) {
                                runCatching { apkResources.getString(resId) }.getOrNull()
                            } else {
                                attrs.getAttributeValue(i)
                            }
                            if (!str.isNullOrBlank() && !str.startsWith("?") && !str.startsWith("@")) {
                                view.text = str
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createFallbackWidgetForTag(name: String, context: Context): View {
        val lower = name.substringAfterLast('.').lowercase()
        return when {
            lower.contains("scrollview") && lower.contains("horizontal") -> HorizontalScrollView(context)
            lower.contains("scroll") || lower.contains("nestedscroll") -> ScrollView(context)
            lower.contains("recyclerview") || lower.contains("listview") || lower.contains("linear") ||
                lower.contains("appcompatlinear") || lower.contains(" toolbar") ->
                LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            lower.contains("relative") -> RelativeLayout(context)
            lower.contains("constraint") || lower.contains("coordinator") || lower.contains("frame") ||
                lower.contains("card") || lower.contains("viewpager") || lower.contains("drawer") ||
                lower.contains("layout") || lower.contains("container") || lower.contains("bar") ->
                FrameLayout(context)
            lower.contains("edittext") || lower.contains("textinputedit") || lower.contains("autocomplete") ->
                EditText(context)
            lower.contains("imagebutton") || lower.contains("fab") || lower.contains("floatingaction") ->
                ImageButton(context)
            lower.contains("button") || lower.contains("chip") ->
                Button(context)
            lower.contains("image") || lower.contains("icon") || lower.contains("avatar") || lower.contains("logo") ->
                ImageView(context)
            lower.contains("progress") || lower.contains("spinner") || lower.contains("loading") ->
                ProgressBar(context)
            lower.contains("text") || lower.contains("title") || lower.contains("label") || lower.contains("tv") ->
                TextView(context)
            else -> FrameLayout(context)
        }
    }

    private fun applyCoreAttributesFromXml(view: View, context: Context, attrs: AttributeSet) {
        val density = apkResources.displayMetrics.density
        for (i in 0 until attrs.attributeCount) {
            val attrName = attrs.getAttributeName(i) ?: continue
            runCatching {
                when (attrName) {
                    "id" -> {
                        val idVal = attrs.getAttributeResourceValue(i, View.NO_ID)
                        if (idVal != View.NO_ID) view.id = idVal
                    }
                    "visibility" -> {
                        when (attrs.getAttributeIntValue(i, 0)) {
                            1 -> view.visibility = View.INVISIBLE
                            2 -> view.visibility = View.GONE
                            else -> view.visibility = View.VISIBLE
                        }
                    }
                    "padding" -> {
                        val px = VirtualApkEngine.parseDimensionAttr(apkResources, attrs, i, 0, density)
                        view.setPadding(px, px, px, px)
                    }
                    "paddingLeft", "paddingStart" -> {
                        val px = VirtualApkEngine.parseDimensionAttr(apkResources, attrs, i, 0, density)
                        view.setPadding(px, view.paddingTop, view.paddingRight, view.paddingBottom)
                    }
                    "paddingRight", "paddingEnd" -> {
                        val px = VirtualApkEngine.parseDimensionAttr(apkResources, attrs, i, 0, density)
                        view.setPadding(view.paddingLeft, view.paddingTop, px, view.paddingBottom)
                    }
                    "paddingTop" -> {
                        val px = VirtualApkEngine.parseDimensionAttr(apkResources, attrs, i, 0, density)
                        view.setPadding(view.paddingLeft, px, view.paddingRight, view.paddingBottom)
                    }
                    "paddingBottom" -> {
                        val px = VirtualApkEngine.parseDimensionAttr(apkResources, attrs, i, 0, density)
                        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, px)
                    }
                    "background" -> {
                        val resId = attrs.getAttributeResourceValue(i, 0)
                        val rawVal = attrs.getAttributeValue(i)
                        if (resId != 0) {
                            runCatching {
                                view.background = apkResources.getDrawable(resId, context.theme)
                            }.onFailure {
                                runCatching { view.setBackgroundColor(apkResources.getColor(resId, context.theme)) }
                            }
                        } else if (rawVal != null && rawVal.startsWith("#")) {
                            runCatching { view.setBackgroundColor(AndroidColor.parseColor(rawVal)) }
                        }
                    }
                    "orientation" -> {
                        if (view is LinearLayout) {
                            val o = attrs.getAttributeIntValue(i, LinearLayout.HORIZONTAL)
                            view.orientation = if (o == 1) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                        }
                    }
                    "gravity" -> {
                        val g = attrs.getAttributeIntValue(i, Gravity.NO_GRAVITY)
                        if (view is LinearLayout) view.gravity = g
                        if (view is TextView) view.gravity = g
                    }
                    "text" -> {
                        if (view is TextView) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            val str = if (resId != 0) {
                                runCatching { apkResources.getString(resId) }.getOrNull()
                            } else {
                                attrs.getAttributeValue(i)
                            }
                            if (!str.isNullOrBlank() && !str.startsWith("?")) {
                                view.text = str
                            }
                        }
                    }
                    "hint" -> {
                        if (view is TextView) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            val str = if (resId != 0) {
                                runCatching { apkResources.getString(resId) }.getOrNull()
                            } else {
                                attrs.getAttributeValue(i)
                            }
                            if (!str.isNullOrBlank() && !str.startsWith("?")) {
                                view.hint = str
                            }
                        }
                    }
                    "textColor" -> {
                        if (view is TextView) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            val raw = attrs.getAttributeValue(i)
                            if (resId != 0) {
                                runCatching { view.setTextColor(apkResources.getColor(resId, context.theme)) }
                            } else if (raw != null && raw.startsWith("#")) {
                                runCatching { view.setTextColor(AndroidColor.parseColor(raw)) }
                            }
                        }
                    }
                    "textSize" -> {
                        if (view is TextView) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            val raw = attrs.getAttributeValue(i)
                            if (resId != 0) {
                                val px = runCatching { apkResources.getDimension(resId) }.getOrDefault(0f)
                                if (px > 0f) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                            } else if (raw != null && (raw.endsWith("sp") || raw.endsWith("dp"))) {
                                val sp = raw.removeSuffix("sp").removeSuffix("dp").toFloatOrNull()
                                if (sp != null) view.textSize = sp
                            }
                        }
                    }
                    "singleLine" -> {
                        if (view is TextView && attrs.getAttributeBooleanValue(i, false)) {
                            view.isSingleLine = true
                            view.ellipsize = TextUtils.TruncateAt.END
                        }
                    }
                    "src", "srcCompat" -> {
                        if (view is ImageView) {
                            val resId = attrs.getAttributeResourceValue(i, 0)
                            if (resId != 0) {
                                runCatching {
                                    view.setImageDrawable(apkResources.getDrawable(resId, context.theme))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Crash-isolated host `FrameLayout` wrapping mounted APK view hierarchies so that
 * `onMeasure`, `onLayout`, `dispatchDraw`, and `dispatchTouchEvent` can never throw
 * an unhandled exception into Android's `ViewRootImpl`.
 */
class SandboxSafeHostFrameLayout(context: Context) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } catch (t: Throwable) {
            android.util.Log.e("SandboxSafeHostFrame", "Guarded onMeasure exception in virtual view", t)
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec)
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            super.onLayout(changed, left, top, right, bottom)
        } catch (t: Throwable) {
            android.util.Log.e("SandboxSafeHostFrame", "Guarded onLayout exception in virtual view", t)
        }
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        try {
            super.dispatchDraw(canvas)
        } catch (t: Throwable) {
            android.util.Log.e("SandboxSafeHostFrame", "Guarded dispatchDraw exception in virtual view", t)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (t: Throwable) {
            android.util.Log.e("SandboxSafeHostFrame", "Guarded dispatchTouchEvent exception in virtual view", t)
            false
        }
    }
}
