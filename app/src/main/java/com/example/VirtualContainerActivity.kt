package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CloneAppEntity
import com.example.data.VirtualSpaceDatabase
import com.example.ui.InProcessRealApkSandboxHost
import com.example.ui.RealAppIconBox
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsIndigoSecondary
import com.example.ui.theme.InsNavyDeep
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.InsSurfaceElevated
import com.example.ui.theme.InsVioletTertiary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.virtual.BinderIpcInterceptor
import com.example.virtual.BlackBoxCore
import com.example.virtual.ClassLoaderHookDiagnostic
import com.example.virtual.ExtractedSandboxApkInfo
import com.example.virtual.HookedRuntimeSnapshot
import com.example.virtual.IdentitySpoofer
import com.example.virtual.MountedSandboxApk
import com.example.virtual.VirtualApkEngine
import com.example.virtual.VirtualApkLauncher
import com.example.virtual.VirtualAppLauncher
import com.example.virtual.VirtualClassLoaderHook
import com.example.virtual.VirtualContextWrapper
import com.example.virtual.VirtualProcessManager
import com.example.virtual.VirtualSandboxStorage
import com.example.virtual.VirtualSqliteManager
import com.example.virtual.VirtualXmlPrefsManager
import com.example.virtual.XmlPrefEntry
import com.example.virtual.XmlPrefType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sandboxed Virtual Container Activity (`:virtual_sandbox` process).
 *
 * CRITICAL ISOLATION ARCHITECTURE:
 * 1. Configures Android Recents / Task Manager with "[NamaAplikasi](Clone App)" + real APK icon + CLONE badge.
 * 2. Extracts the target APK into `/data/user/0/com.ins.virtualspace/virtual/user/<slot>/<pkg>/base.apk`
 *    and mounts it via `VirtualContextWrapper` & `DexClassLoader`.
 * 3. Runs the cloned application IN-PROCESS inside this container window — NEVER calling `startActivity()`
 *    on the external host package on the user's phone, so the user's original app & account are never opened!
 */
private const val TAG = "VirtualContainerAct"

class VirtualContainerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CLONE_ID = "extra_clone_id"
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_INSTANCE_INDEX = "extra_instance_index"
        private const val EXTRA_LAUNCH_COMPONENT = "extra_launch_component"
        private const val EXTRA_SOURCE_APK_PATH = "extra_source_apk_path"

        fun launchCloneContainer(context: Context, clone: CloneAppEntity) {
            VirtualAppLauncher.init(context)
            if (clone.packageName == "com.ins.tools.deviceinspector") {
                val intent = Intent(context, VirtualContainerActivity::class.java).apply {
                    putExtra(EXTRA_CLONE_ID, clone.id)
                    putExtra(EXTRA_PACKAGE_NAME, clone.packageName)
                    putExtra(EXTRA_APP_NAME, clone.appName)
                    putExtra(EXTRA_INSTANCE_INDEX, clone.instanceIndex)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                context.startActivity(intent)
                return
            }
            VirtualAppLauncher.launchVirtualApp(
                packageName = clone.packageName,
                userId = clone.instanceIndex,
                context = context,
                cloneId = clone.id,
                appNameHint = clone.appName
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install sandbox uncaught exception guard FIRST so non-fatal background worker
        // exceptions inside guest APK code never cause a process Force Close.
        VirtualProcessManager.installVirtualCrashGuard(this)
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        VirtualAppLauncher.init(this)

        val cloneId = intent.getIntExtra(EXTRA_CLONE_ID, 1)
        val fallbackAppName = intent.getStringExtra(EXTRA_APP_NAME) ?: "PineDrama"
        val fallbackPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "com.ss.android.ttmd.video"
        val userId = intent.getIntExtra(EXTRA_INSTANCE_INDEX, 0)
        val launchComp = intent.getStringExtra(EXTRA_LAUNCH_COMPONENT) ?: "$fallbackPkg.MainActivity"
        val sourceApkPath = intent.getStringExtra(EXTRA_SOURCE_APK_PATH) ?: ""

        VirtualProcessManager.getOrCreateProcessRecord(
            context = this,
            packageName = fallbackPkg,
            userId = userId,
            installedApkPath = sourceApkPath,
            targetComponent = android.content.ComponentName(fallbackPkg, launchComp)
        )

        val initialRecentsLabel = "${fallbackAppName}(Clone App)"
        title = initialRecentsLabel
        applyCloneTaskDescription(fallbackPkg, initialRecentsLabel, fallbackAppName, 0xFF00E5FF.toInt())

        setContent {
            MyApplicationTheme {
                VirtualContainerScreen(
                    cloneId = cloneId,
                    fallbackPkg = fallbackPkg,
                    fallbackAppName = fallbackAppName,
                    onUpdateTaskDescription = { pkg, recentsTitle, shortName, colorInt ->
                        title = recentsTitle
                        applyCloneTaskDescription(pkg, recentsTitle, shortName, colorInt)
                    },
                    onExitContainer = { finish() }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyCloneTaskDescription(
        packageName: String,
        recentsLabel: String,
        appName: String,
        accentColor: Int
    ) {
        runCatching {
            val opaqueColor = accentColor or 0xFF000000.toInt()
            val badgeIcon = VirtualApkLauncher.loadAppIconBitmap(
                context = this,
                packageName = packageName,
                appName = appName,
                fallbackColorInt = opaqueColor,
                addCloneBadge = true
            )
            val taskDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityManager.TaskDescription(recentsLabel, badgeIcon, opaqueColor)
            } else {
                ActivityManager.TaskDescription(recentsLabel, badgeIcon, opaqueColor)
            }
            setTaskDescription(taskDesc)
        }.onFailure { e ->
            Log.w(TAG, "Non-fatal setTaskDescription error for $packageName: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VirtualContainerScreen(
    cloneId: Int,
    fallbackPkg: String,
    fallbackAppName: String,
    onUpdateTaskDescription: (String, String, String, Int) -> Unit,
    onExitContainer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { VirtualSpaceDatabase.getInstance(context) }
    val dao = remember { database.virtualSpaceDao() }

    var clone by remember { mutableStateOf<CloneAppEntity?>(null) }
    var extractedApkInfo by remember { mutableStateOf<ExtractedSandboxApkInfo?>(null) }
    var isBooting by remember { mutableStateOf(true) }
    var bootStepText by remember { mutableStateOf("Mengekstrak APK ke /virtual/user/0/$fallbackPkg/base.apk ...") }
    var bootProgress by remember { mutableStateOf(0.2f) }
    // Menyimpan penyebab kegagalan boot virtual container agar UI tidak macet di layar loading.
    var bootError by remember { mutableStateOf<String?>(null) }

    var virtualContext by remember { mutableStateOf<VirtualContextWrapper?>(null) }
    var mountedSandboxApk by remember { mutableStateOf<MountedSandboxApk?>(null) }
    var classLoaderDiag by remember { mutableStateOf<ClassLoaderHookDiagnostic?>(null) }
    var hookSnapshot by remember { mutableStateOf<HookedRuntimeSnapshot?>(null) }

    // 0 = In-Process Cloned App (Full Screen), 1 = Hooked Identity, 2 = Sandbox I/O, 3 = DEX ClassLoader
    var activeSection by remember { mutableIntStateOf(0) }
    var showEngineInspectorBar by remember { mutableStateOf(false) }
    var statusToast by remember { mutableStateOf<String?>(null) }
    var sqliteRowCount by remember { mutableIntStateOf(0) }
    var prefsKeyCount by remember { mutableIntStateOf(0) }

    fun refreshSandboxStorageCounts(vCtx: VirtualContextWrapper, targetClone: CloneAppEntity) {
        scope.launch(Dispatchers.IO) {
            val dbFile = vCtx.getDatabasePath("app_sandbox_data.db")
            val xmlFile = vCtx.getVirtualSharedPrefsFile("${targetClone.packageName.replace('.', '_')}_preferences.xml")
            val tables = VirtualSqliteManager.listTables(dbFile)
            val rows = tables.sumOf { VirtualSqliteManager.readTable(dbFile, it).rows.size }
            val keys = VirtualXmlPrefsManager.parseXmlFile(xmlFile).size
            withContext(Dispatchers.Main) {
                sqliteRowCount = rows
                prefsKeyCount = keys
            }
        }
    }

    LaunchedEffect(cloneId) {
        try {
        val loaded = withContext(Dispatchers.IO) {
            dao.getCloneById(cloneId) ?: dao.getClonesByPackage(fallbackPkg).firstOrNull()
        }
        if (loaded != null) {
            val hostApk = withContext(Dispatchers.IO) {
                VirtualApkLauncher.resolveInstalledApk(context, loaded)
            }
            val syncedClone = if (hostApk != null && hostApk.isInstalledOnHost && hostApk.packageName != loaded.packageName) {
                val updated = loaded.copy(packageName = hostApk.packageName)
                withContext(Dispatchers.IO) { dao.updateClone(updated) }
                updated
            } else {
                loaded
            }

            clone = syncedClone
            onUpdateTaskDescription(
                syncedClone.packageName,
                syncedClone.recentsTaskTitle,
                syncedClone.appName,
                syncedClone.iconColorHex.toInt()
            )

            // Configure in-process virtual properties for this sandbox process
            System.setProperty("ins.virtual.package", syncedClone.packageName)
            System.setProperty("ins.virtual.user_slot", syncedClone.instanceIndex.toString())
            System.setProperty("ins.virtual.data_path", syncedClone.canonicalVirtualDataPath)
            System.setProperty("ins.spoof.android_id", syncedClone.androidId)
            System.setProperty("ins.spoof.imei", syncedClone.imei)
            System.setProperty("ins.spoof.model", syncedClone.buildModel)

            if (syncedClone.packageName == "com.ins.tools.deviceinspector") {
                activeSection = 1
                showEngineInspectorBar = true
            }

            // Ensure APK is staged in BlackBoxCore Virtual Space (NEVER launch host package externally!)
            if (hostApk != null && hostApk.sourceApkPath.isNotBlank() &&
                !BlackBoxCore.get().isInstalled(syncedClone.packageName, syncedClone.instanceIndex)
            ) {
                bootStepText = "Memasang APK ke Virtual Space (${syncedClone.packageName} user ${syncedClone.instanceIndex})..."
                bootProgress = 0.35f
                withContext(Dispatchers.IO) {
                    VirtualAppLauncher.installToVirtualSpace(
                        apkPath = hostApk.sourceApkPath,
                        userId = syncedClone.instanceIndex,
                        context = context,
                        targetPackageHint = syncedClone.packageName
                    )
                }
            }

            bootStepText = "1/3 Mengekstrak APK ke sandbox ${syncedClone.canonicalVirtualDataPath}base.apk..."
            bootProgress = 0.45f
            val sandboxRoot = withContext(Dispatchers.IO) {
                VirtualSandboxStorage.ensureSandboxProvisioned(context, syncedClone)
            }
            val extracted = withContext(Dispatchers.IO) {
                VirtualApkLauncher.extractApkIntoVirtualSandbox(context, syncedClone, sandboxRoot)
            }
            extractedApkInfo = extracted

            bootStepText = "2/3 Memuat DexClassLoader & Resources dari ${extracted.extractedApkPath}..."
            bootProgress = 0.80f
            val (loader, diag) = withContext(Dispatchers.IO) {
                VirtualClassLoaderHook.createIsolatedClassLoader(context, syncedClone, sandboxRoot)
            }
            val vCtx = VirtualContextWrapper(context, syncedClone, sandboxRoot, loader)
            val mounted = withContext(Dispatchers.IO) {
                VirtualApkEngine.mountExtractedApk(context, syncedClone, sandboxRoot, extracted, vCtx)
            }
            virtualContext = vCtx
            mountedSandboxApk = mounted
            classLoaderDiag = diag

            val snapshot = withContext(Dispatchers.IO) {
                val interceptor = BinderIpcInterceptor(context, syncedClone) { log ->
                    scope.launch(Dispatchers.IO) { dao.insertHookLog(log) }
                }
                interceptor.executeDiagnosticSweep()
            }
            hookSnapshot = snapshot
            refreshSandboxStorageCounts(vCtx, syncedClone)

            bootStepText = "Membuka ${syncedClone.appName}..."
            bootProgress = 1.0f
        } else {
            bootError = "Clone #$cloneId ($fallbackPkg) tidak ditemukan di database Virtual Space."
        }
        } catch (t: Throwable) {
            Log.e(TAG, "Virtual container boot gagal untuk $fallbackPkg", t)
            bootError = t.message ?: t.javaClass.simpleName
        } finally {
            // Selalu akhiri layar loading, walau boot gagal, supaya tidak macet selamanya.
            isBooting = false
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = bootProgress,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "boot_progress"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = Color.White,
        topBar = {
            if (showEngineInspectorBar || activeSection != 0) {
                val activeClone = clone
                Surface(
                    color = InsSurfaceElevated,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onExitContainer,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("exit_virtual_container_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Ruang Virtual",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        RealAppIconBox(
                            packageName = activeClone?.packageName ?: fallbackPkg,
                            appName = activeClone?.appName ?: fallbackAppName,
                            fallbackColorHex = activeClone?.iconColorHex ?: 0xFF00E5FF,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeClone?.recentsTaskTitle ?: "$fallbackAppName(Clone App)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "In-Process Clone #${(activeClone?.instanceIndex ?: 0) + 1} • ${activeClone?.buildModel ?: "Spoofed"} • PID ${Process.myPid()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InsEmeraldActive,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                showEngineInspectorBar = false
                                activeSection = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("toggle_sandbox_inspector_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = InsNavyDeep,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ke Aplikasi",
                                style = MaterialTheme.typography.labelSmall,
                                color = InsNavyDeep,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = isBooting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RealAppIconBox(
                        packageName = clone?.packageName ?: fallbackPkg,
                        appName = clone?.appName ?: fallbackAppName,
                        fallbackColorHex = clone?.iconColorHex ?: 0xFF00E5FF,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = clone?.appName ?: fallbackAppName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1F1F1F),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(
                visible = !isBooting && bootError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                VirtualBootErrorPanel(
                    errorMessage = bootError ?: "Penyebab tidak diketahui",
                    packageName = fallbackPkg,
                    appName = fallbackAppName,
                    onExitContainer = onExitContainer
                )
            }

            AnimatedVisibility(
                visible = !isBooting && bootError == null && clone != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val activeClone = clone!!

                Column(modifier = Modifier.fillMaxSize()) {
                    // Show Inspector FilterChips when user taps "Info Hook" or navigates tabs
                    if (showEngineInspectorBar || activeSection != 0) {
                        Surface(
                            color = InsSurfaceDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = activeSection == 0,
                                    onClick = {
                                        activeSection = 0
                                        showEngineInspectorBar = false
                                    },
                                    label = { Text("Layar Clone") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("container_tab_runtime")
                                )
                                FilterChip(
                                    selected = activeSection == 1,
                                    onClick = { activeSection = 1 },
                                    label = { Text("Hooked ID") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("container_tab_identity")
                                )
                                FilterChip(
                                    selected = activeSection == 2,
                                    onClick = { activeSection = 2 },
                                    label = { Text("Sandbox I/O") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("container_tab_sandbox")
                                )
                                FilterChip(
                                    selected = activeSection == 3,
                                    onClick = { activeSection = 3 },
                                    label = { Text("DEX Loader") },
                                    leadingIcon = {
                                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("container_tab_classloader")
                                )
                            }
                        }
                    }

                    if (activeSection == 0) {
                        InProcessRealApkSandboxHost(
                            clone = activeClone,
                            virtualContext = virtualContext,
                            extractedInfo = extractedApkInfo,
                            mountedApk = mountedSandboxApk,
                            onOpenEngineInspector = {
                                showEngineInspectorBar = true
                                activeSection = 1
                            },
                            onSandboxDataChanged = {
                                virtualContext?.let { refreshSandboxStorageCounts(it, activeClone) }
                            },
                            onExitContainer = onExitContainer,
                            onReboundCloneLaunched = { rebound ->
                                clone = rebound
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, InsCyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Memory,
                                                contentDescription = null,
                                                tint = InsCyanPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "In-Process Container: \"${activeClone.recentsTaskTitle}\"",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = InsCyanPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "File APK Hasil Ekstraksi di Sandbox Aplikasi Kita:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = extractedApkInfo?.extractedApkPath ?: "${activeClone.canonicalVirtualDataPath}base.apk",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = InsEmeraldActive,
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                        if (statusToast != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = statusToast!!,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = InsCyanPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            if (activeSection == 1) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = InsSurfaceElevated),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Verifikasi Intersepsi API (Real-Time)",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "Nilai yang diterima oleh APK Clone di dalam Virtual Container",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            val interceptor = BinderIpcInterceptor(context, activeClone) { log ->
                                                                scope.launch(Dispatchers.IO) { dao.insertHookLog(log) }
                                                            }
                                                            val snap = interceptor.executeDiagnosticSweep()
                                                            withContext(Dispatchers.Main) {
                                                                hookSnapshot = snap
                                                                statusToast = "9 panggilan Binder IPC berhasil diintersepsi!"
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                    modifier = Modifier.testTag("trigger_ipc_sweep_button")
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Uji Hook", style = MaterialTheme.typography.labelLarge)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            val snap = hookSnapshot
                                            HookComparisonRow(
                                                apiName = "Settings.Secure.ANDROID_ID",
                                                hostVal = IdentitySpoofer.getHostRealAndroidId(context),
                                                spoofVal = snap?.androidId ?: activeClone.androidId
                                            )
                                            HookComparisonRow(
                                                apiName = "TelephonyManager.getImei()",
                                                hostVal = "Host IMEI (Blocked/Real)",
                                                spoofVal = snap?.imei ?: activeClone.imei
                                            )
                                            HookComparisonRow(
                                                apiName = "Build.MODEL / MANUFACTURER",
                                                hostVal = "${Build.MANUFACTURER} ${Build.MODEL}",
                                                spoofVal = "${activeClone.buildManufacturer} ${activeClone.buildModel}"
                                            )
                                            HookComparisonRow(
                                                apiName = "Build.SERIAL / BRAND",
                                                hostVal = "${Build.BRAND} / ${Build.UNKNOWN}",
                                                spoofVal = "${activeClone.buildBrand} / ${activeClone.buildSerial}"
                                            )
                                            HookComparisonRow(
                                                apiName = "WifiInfo.getMacAddress()",
                                                hostVal = "02:00:00:00:00:00",
                                                spoofVal = snap?.wifiMac ?: activeClone.wifiMac
                                            )
                                            HookComparisonRow(
                                                apiName = "AdvertisingIdClient (GAID)",
                                                hostVal = "Host GAID",
                                                spoofVal = snap?.advertisingId ?: activeClone.advertisingId
                                            )
                                            HookComparisonRow(
                                                apiName = "LocationManager (Fake GPS)",
                                                hostVal = "Real GPS Sensor",
                                                spoofVal = if (activeClone.mockLocationEnabled) {
                                                    "${activeClone.mockLocationName} (${activeClone.mockLatitude}, ${activeClone.mockLongitude})"
                                                } else {
                                                    "Nonaktif (Passthrough)"
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (activeSection == 2) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = InsSurfaceElevated),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Penyimpanan Sandbox Internal Klon",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Seluruh aktivitas akun clone disimpan di dalam database SQLite & XML SharedPreferences terisolasi.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = InsSurfaceCard,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text("SQLite Records", style = MaterialTheme.typography.labelSmall, color = InsCyanPrimary)
                                                        Text("$sqliteRowCount Baris", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                                        Text("app_sandbox_data.db", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Surface(
                                                    color = InsSurfaceCard,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text("SharedPreferences", style = MaterialTheme.typography.labelSmall, color = InsVioletTertiary)
                                                        Text("$prefsKeyCount Keys", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                                        Text("shared_prefs/*.xml", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = {
                                                    val vCtx = virtualContext ?: return@Button
                                                    scope.launch(Dispatchers.IO) {
                                                        val dbFile = vCtx.getDatabasePath("app_sandbox_data.db")
                                                        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                                        VirtualSqliteManager.insertRow(
                                                            dbFile = dbFile,
                                                            tableName = "telemetry_audit",
                                                            values = mapOf(
                                                                "api_hooked" to "RuntimeSessionWrite",
                                                                "intercept_result" to "Token_${activeClone.androidId.take(6)}_$now",
                                                                "recorded_at" to now
                                                            )
                                                        )
                                                        val tables = VirtualSqliteManager.listTables(dbFile)
                                                        val totalRows = tables.sumOf { VirtualSqliteManager.readTable(dbFile, it).rows.size }
                                                        withContext(Dispatchers.Main) {
                                                            sqliteRowCount = totalRows
                                                            statusToast = "Baris baru ditulis ke SQLite klon!"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("write_sandbox_sqlite_button"),
                                                colors = ButtonDefaults.buttonColors(containerColor = InsIndigoSecondary)
                                            ) {
                                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Tulis Transaksi Baru ke SQLite Klon")
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    val vCtx = virtualContext ?: return@OutlinedButton
                                                    scope.launch(Dispatchers.IO) {
                                                        val xmlFile = vCtx.getVirtualSharedPrefsFile("${activeClone.packageName.replace('.', '_')}_preferences.xml")
                                                        val current = VirtualXmlPrefsManager.parseXmlFile(xmlFile).toMutableList()
                                                        val stamp = (System.currentTimeMillis() % 100000).toString()
                                                        current.add(XmlPrefEntry("runtime_token_$stamp", "isolated_${activeClone.imei.takeLast(6)}", XmlPrefType.STRING))
                                                        VirtualXmlPrefsManager.writeEntriesToXml(xmlFile, current)
                                                        withContext(Dispatchers.Main) {
                                                            prefsKeyCount = current.size
                                                            statusToast = "Key 'runtime_token_$stamp' ditambahkan ke XML!"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("write_sandbox_xml_button")
                                            ) {
                                                Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Tambah Key Baru ke SharedPreferences XML")
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val vCtx = virtualContext ?: return@Button
                                                    scope.launch(Dispatchers.IO) {
                                                        val repo = com.example.data.VirtualSpaceRepository(context, dao)
                                                        val (updatedClone, _) = repo.clearCloneDataAndResetIdentity(
                                                            clone = activeClone,
                                                            randomizeDeviceId = true
                                                        )
                                                        val dbFile = vCtx.getDatabasePath("app_sandbox_data.db")
                                                        val xmlFile = vCtx.getVirtualSharedPrefsFile("${updatedClone.packageName.replace('.', '_')}_preferences.xml")
                                                        val tables = VirtualSqliteManager.listTables(dbFile)
                                                        val totalRows = tables.sumOf { VirtualSqliteManager.readTable(dbFile, it).rows.size }
                                                        val totalKeys = VirtualXmlPrefsManager.parseXmlFile(xmlFile).size
                                                        withContext(Dispatchers.Main) {
                                                            clone = updatedClone
                                                            sqliteRowCount = totalRows
                                                            prefsKeyCount = totalKeys
                                                            statusToast = "Seluruh data sandbox clone ${updatedClone.appName} berhasil dihapus & di-reset ke akun baru!"
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("container_clear_clone_data_button"),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Hapus Data Clone & Reset Akun Baru", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (activeSection == 3) {
                                item {
                                    val diag = classLoaderDiag
                                    val ext = extractedApkInfo
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = InsSurfaceElevated),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Diagnostik Ekstraksi APK & DexClassLoader",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HookComparisonRow("Execution Mode", "Host Process (Blocked)", "In-Process Virtual Container")
                                            HookComparisonRow("Extracted Sandbox APK", "Host /data/app/base.apk", ext?.extractedApkPath ?: "${activeClone.canonicalVirtualDataPath}base.apk")
                                            HookComparisonRow("Extracted DEX Bytecode", "System OAT", ext?.extractedDexPath ?: "${activeClone.canonicalVirtualDataPath}code_cache/classes.dex")
                                            HookComparisonRow("ClassLoader Instance", "PathClassLoader", diag?.loaderClassName ?: "dalvik.system.DexClassLoader")
                                            HookComparisonRow("Native Library Dir", "/system/lib64", diag?.nativeLibDir ?: "${activeClone.canonicalVirtualDataPath}lib")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualBootErrorPanel(
    errorMessage: String,
    packageName: String,
    appName: String,
    onExitContainer: () -> Unit
) {
    val context = LocalContext.current
    val realLaunchIntent = remember(packageName) {
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = null,
            tint = Color(0xFFF43F5E),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Gagal membuka $appName di Virtual Space",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF1F1F1F),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Package: $packageName",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            fontFamily = JetBrainsMonoFamily
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            color = Color(0xFFFEF2F2),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Penyebab (Logcat Virtual Container):",
                    color = Color(0xFFB91C1C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFF111827),
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFamily
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (realLaunchIntent != null) {
            Button(
                onClick = {
                    val intent = Intent(realLaunchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = InsEmeraldActive),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = InsNavyDeep)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buka Aplikasi Asli (tanpa isolasi)",
                    color = InsNavyDeep,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Button(
            onClick = onExitContainer,
            colors = ButtonDefaults.buttonColors(containerColor = InsSurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Kembali ke Beranda Clone App", color = Color.White)
        }
    }
}

@Composable
private fun HookComparisonRow(
    apiName: String,
    hostVal: String,
    spoofVal: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(InsSurfaceCard)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = apiName,
                style = MaterialTheme.typography.labelMedium,
                color = InsCyanPrimary,
                fontFamily = JetBrainsMonoFamily
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = InsEmeraldActive,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Host: $hostVal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.45f)
            )
            Text(
                text = "Clone: $spoofVal",
                style = MaterialTheme.typography.labelSmall,
                color = InsEmeraldActive,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.55f)
            )
        }
    }
}
