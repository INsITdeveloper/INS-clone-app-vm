package com.example.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.CloneAppEntity
import com.example.data.InstallableAppCandidate
import com.example.data.VirtualSpaceDatabase
import com.example.data.VirtualSpaceRepository
import com.example.ui.theme.InsAmberWarning
import com.example.ui.theme.InsCrimsonDanger
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsNavyDeep
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.virtual.BlackBoxCore
import com.example.virtual.ExtractedSandboxApkInfo
import com.example.virtual.MountedSandboxApk
import com.example.virtual.VirtualApkEngine
import com.example.virtual.VirtualApkLauncher
import com.example.virtual.VirtualAppLauncher
import com.example.virtual.VirtualContextWrapper
import com.example.virtual.VirtualProcessManager
import com.example.virtual.VirtualSqliteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "InProcessApkHostScreen"

/**
 * Full-Screen Isolated Virtual APK Host (`InProcessRealApkSandboxHost`).
 *
 * Strictly mounts and executes the target APK (`base.apk` + split APKs) inside the isolated
 * virtual container process (`com.ins.virtualspace`) without:
 * - Launching external host apps outside the sandbox
 * - Using `Intent.ACTION_VIEW` / `Uri.parse` / web browsers / WebViews
 * - Displaying fake mockup UIs when an APK fails to load
 */
@Composable
fun InProcessRealApkSandboxHost(
    clone: CloneAppEntity,
    virtualContext: VirtualContextWrapper?,
    extractedInfo: ExtractedSandboxApkInfo?,
    mountedApk: MountedSandboxApk?,
    onOpenEngineInspector: () -> Unit,
    onSandboxDataChanged: () -> Unit,
    onExitContainer: () -> Unit = {},
    onReboundCloneLaunched: (CloneAppEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val hostActivity = context as? Activity
    val scope = rememberCoroutineScope()

    // Track the currently mounted Activity and R.layout.* inside the extracted APK
    var activeActivityName by rememberSaveable(clone.id) {
        mutableStateOf(
            mountedApk?.launcherActivityName
                ?: extractedInfo?.launcherActivityName
                ?: "${clone.packageName}.MainActivity"
        )
    }
    var activeLayoutResId by rememberSaveable(clone.id) {
        mutableIntStateOf(0)
    }
    var activeLayoutLabel by rememberSaveable(clone.id) {
        mutableStateOf(activeActivityName.substringAfterLast('.'))
    }
    var lastErrorLog by rememberSaveable(clone.id) {
        mutableStateOf<String?>(null)
    }
    // Virtual activity backstack so Android Back button pops back inside the cloned APK
    val activityBackstack = remember(clone.id) {
        mutableListOf<Pair<String, Int>>()
    }

    androidx.activity.compose.BackHandler(enabled = activityBackstack.isNotEmpty()) {
        val previous = activityBackstack.removeAt(activityBackstack.lastIndex)
        activeActivityName = previous.first
        activeLayoutResId = previous.second
        activeLayoutLabel = previous.first.substringAfterLast('.')
    }

    var showSandboxControlSheet by remember { mutableStateOf(false) }
    var showApkComponentPicker by remember { mutableStateOf(false) }
    var showBindInstalledAppDialog by remember { mutableStateOf(false) }
    var showClearCloneDataDialog by remember { mutableStateOf(false) }
    var randomizeIdOnClear by remember { mutableStateOf(true) }
    var dataResetCounter by rememberSaveable(clone.id) { mutableIntStateOf(0) }
    var installedAppsOnPhone by remember { mutableStateOf<List<InstallableAppCandidate>>(emptyList()) }
    var pickerTab by remember { mutableIntStateOf(0) } // 0 = Layouts (R.layout.*), 1 = Activities (<activity>)

    val resolvedHost = remember(clone.packageName, clone.appName) {
        VirtualApkLauncher.resolveInstalledApk(context, clone)
    }

    val processRecord = remember(clone.packageName, clone.instanceIndex) {
        VirtualProcessManager.getRunningProcess(clone.packageName, clone.instanceIndex)
    }

    fun executeClearCloneDataInContainer(randomizeDeviceId: Boolean) {
        scope.launch {
            val db = VirtualSpaceDatabase.getInstance(context)
            val repo = VirtualSpaceRepository(context, db.virtualSpaceDao())
            val (updatedClone, _) = withContext(Dispatchers.IO) {
                repo.clearCloneDataAndResetIdentity(clone, randomizeDeviceId = randomizeDeviceId)
            }
            dataResetCounter += 1
            onReboundCloneLaunched(updatedClone)
            onSandboxDataChanged()
            Toast.makeText(
                context,
                "Data clone ${clone.appName} berhasil dihapus! Akun & sesi dimulai dari awal.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun executeClearCloneCacheInContainer() {
        scope.launch {
            val db = VirtualSpaceDatabase.getInstance(context)
            val repo = VirtualSpaceRepository(context, db.virtualSpaceDao())
            withContext(Dispatchers.IO) {
                repo.clearCloneCacheOnly(clone)
            }
            onSandboxDataChanged()
            Toast.makeText(
                context,
                "Cache clone ${clone.appName} berhasil dibersihkan!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(clone.id, mountedApk?.isRealApkLoaded) {
        VirtualAppLauncher.init(context)
        if (mountedApk == null || !mountedApk.isRealApkLoaded) {
            val errMsg = "Failed to launch APK in virtual space: ${clone.packageName} (userId=${clone.instanceIndex}) not installed in virtual container"
            Log.e(TAG, errMsg)
            lastErrorLog = errMsg
        } else {
            lastErrorLog = null
        }
    }

    fun recordSandboxActivityTransition(eventLabel: String) {
        val vCtx = virtualContext ?: return
        scope.launch(Dispatchers.IO) {
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val dbFile = vCtx.getDatabasePath("app_sandbox_data.db")
            VirtualSqliteManager.insertRow(
                dbFile = dbFile,
                tableName = "telemetry_audit",
                values = mapOf(
                    "api_hooked" to "SandboxApkHost",
                    "intercept_result" to eventLabel,
                    "recorded_at" to stamp
                )
            )
            onSandboxDataChanged()
        }
    }

    fun handleInternalStartActivity(intent: Intent) {
        val targetLayoutId = intent.getIntExtra("sandbox_target_layout_id", 0)
        val targetLayoutName = intent.getStringExtra("sandbox_target_layout_name")
        val pkgName = mountedApk?.packageName ?: clone.packageName
        val rawAct = intent.component?.className
        val resolvedAct: String = when {
            !rawAct.isNullOrBlank() -> if (rawAct.startsWith(".")) "$pkgName$rawAct" else rawAct
            targetLayoutId == 0 -> {
                val acts = mountedApk?.declaredActivities.orEmpty()
                val idx = acts.indexOf(activeActivityName)
                if (idx >= 0 && idx + 1 < acts.size) {
                    acts[idx + 1]
                } else {
                    acts.firstOrNull { !it.lowercase().contains("splash") && it != activeActivityName }
                        ?: activeActivityName
                }
            }
            else -> activeActivityName
        }

        if (resolvedAct != activeActivityName || targetLayoutId != activeLayoutResId) {
            if (activityBackstack.lastOrNull() != (activeActivityName to activeLayoutResId)) {
                activityBackstack.add(activeActivityName to activeLayoutResId)
                if (activityBackstack.size > 16) {
                    activityBackstack.removeAt(0)
                }
            }
        }

        activeActivityName = resolvedAct
        if (targetLayoutId != 0) {
            activeLayoutResId = targetLayoutId
            if (!targetLayoutName.isNullOrBlank()) {
                activeLayoutLabel = "R.layout.$targetLayoutName"
            }
        } else {
            activeLayoutResId = 0
            activeLayoutLabel = resolvedAct.substringAfterLast('.')
        }
        recordSandboxActivityTransition("startActivity -> ${activeActivityName.substringAfterLast('.')} ($activeLayoutLabel)")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        if (mountedApk != null && mountedApk.isRealApkLoaded && virtualContext != null && hostActivity != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        com.example.virtual.SandboxSafeHostFrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { hostFrame ->
                        val desiredKey = "${mountedApk.packageName}:${activeActivityName}:${activeLayoutResId}:${dataResetCounter}"
                        if (hostFrame.tag != desiredKey) {
                            hostFrame.tag = desiredKey
                            runCatching { hostFrame.removeAllViews() }

                            try {
                                val mountResult = VirtualApkEngine.mountAndLaunchApkScreen(
                                    hostActivity = hostActivity,
                                    virtualContext = virtualContext,
                                    mounted = mountedApk,
                                    targetActivityName = activeActivityName,
                                    targetLayoutResId = activeLayoutResId.takeIf { it != 0 },
                                    onInternalStartActivity = { intent ->
                                        handleInternalStartActivity(intent)
                                    },
                                    onUserInteractionLogged = { msg ->
                                        recordSandboxActivityTransition(msg)
                                    }
                                )

                                if (mountResult.activeActivityName.isNotBlank() && mountResult.activeActivityName != activeActivityName) {
                                    activeActivityName = mountResult.activeActivityName
                                    hostFrame.tag = "${mountedApk.packageName}:${activeActivityName}:${activeLayoutResId}:${dataResetCounter}"
                                }
                                activeLayoutLabel = mountResult.activeLayoutName
                                VirtualApkEngine.attachMountedViewResilient(
                                    hostFrame = hostFrame,
                                    mountedView = mountResult.mountedView,
                                    virtualContext = virtualContext,
                                    mounted = mountedApk
                                )
                            } catch (e: Throwable) {
                                val errMsg = "Virtual APK mount error for ${clone.packageName}: ${e.message ?: e.javaClass.simpleName}"
                                Log.e(TAG, errMsg, e)
                                lastErrorLog = errMsg
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("in_process_extracted_apk_view_host")
                )
            }
        } else {
            // Structured Virtual Space Error Diagnostic Panel (NO fake UI mockup, NO browser redirect!)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0x26F43F5E),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Virtual Launch Error",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Failed to launch APK in virtual space",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF43F5E),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${clone.recentsTaskTitle} • Package: ${clone.packageName} • UserId: ${clone.instanceIndex}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontFamily = JetBrainsMonoFamily
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = InsSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Logcat Virtual Container Diagnostic:",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = lastErrorLog
                                ?: "E/VirtualAppLauncher: Package ${clone.packageName} base.apk not found in ${clone.canonicalVirtualDataPath}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (resolvedHost != null && resolvedHost.sourceApkPath.isNotBlank()) {
                    Button(
                        onClick = {
                            val installed = VirtualAppLauncher.installToVirtualSpace(
                                apkPath = resolvedHost.sourceApkPath,
                                userId = clone.instanceIndex,
                                context = context
                            )
                            if (installed) {
                                VirtualAppLauncher.launchVirtualApp(
                                    packageName = resolvedHost.packageName,
                                    userId = clone.instanceIndex,
                                    context = context,
                                    cloneId = clone.id,
                                    appNameHint = resolvedHost.appLabel
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InsEmeraldActive),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("install_and_launch_virtual_apk_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = InsNavyDeep)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "InstallToVirtualSpace & Launch (${resolvedHost.appLabel})",
                            color = InsNavyDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        scope.launch {
                            val db = VirtualSpaceDatabase.getInstance(context)
                            val repo = VirtualSpaceRepository(context, db.virtualSpaceDao())
                            val all = repo.discoverInstallableApps().filter { it.isInstalledOnHost }
                            installedAppsOnPhone = all
                            showBindInstalledAppDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("bind_installed_app_button")
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = InsNavyDeep)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pilih APK Terinstal di HP ke Virtual Space",
                        color = InsNavyDeep,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onExitContainer) {
                    Text("Kembali ke Beranda Clone App", color = Color.Gray)
                }
            }
        }

        // Subtle Floating Assistive Clone Pill on the right edge
        Surface(
            color = Color(0xCC0F172A),
            border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = 140.dp)
                .clickable { showSandboxControlSheet = true }
                .testTag("open_apk_component_picker_button")
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(InsEmeraldActive)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CLONE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Menu Sandbox Clone",
                    tint = InsCyanPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    // Floating Clone Assistive Menu Dialog
    if (showSandboxControlSheet) {
        AlertDialog(
            onDismissRequest = { showSandboxControlSheet = false },
            containerColor = InsSurfaceDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RealAppIconBox(
                        packageName = clone.packageName,
                        appName = clone.appName,
                        fallbackColorHex = clone.iconColorHex,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = clone.recentsTaskTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "User #${clone.instanceIndex} • ${clone.buildModel} • ID: ${clone.androidId.take(8)}",
                            color = InsEmeraldActive,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = InsSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Path APK Terisolasi di Virtual Space:",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                            Text(
                                text = mountedApk?.extractedBaseApkPath ?: "${clone.canonicalVirtualDataPath}base.apk",
                                color = InsEmeraldActive,
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${mountedApk?.discoveredLayouts?.size ?: 0} Layout XML & ${mountedApk?.declaredActivities?.size ?: 1} Activity dimuat di Virtual Process",
                                color = InsCyanPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            showSandboxControlSheet = false
                            showClearCloneDataDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InsCrimsonDanger),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_clear_clone_data_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Data Clone (Reset Akun Baru)", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showSandboxControlSheet = false
                            executeClearCloneCacheInContainer()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InsSurfaceCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_clear_clone_cache_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = InsEmeraldActive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bersihkan Cache Clone", color = InsEmeraldActive)
                    }

                    Button(
                        onClick = {
                            showSandboxControlSheet = false
                            showApkComponentPicker = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InsSurfaceCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = InsCyanPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Layout XML / Activity dari base.apk", color = Color.White)
                    }

                    Button(
                        onClick = {
                            showSandboxControlSheet = false
                            onOpenEngineInspector()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InsSurfaceCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = InsEmeraldActive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Info Hook, Device ID & Database Sandbox", color = Color.White)
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    OutlinedButton(
                        onClick = {
                            showSandboxControlSheet = false
                            onExitContainer()
                        },
                        border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kembali ke Beranda Clone App", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSandboxControlSheet = false }) {
                    Text("Tutup", color = InsCyanPrimary)
                }
            }
        )
    }

    if (showClearCloneDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearCloneDataDialog = false },
            containerColor = InsSurfaceDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = InsAmberWarning)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Hapus Data Clone (${clone.appName})?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "User #${clone.instanceIndex} • ${clone.canonicalVirtualDataPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InsCyanPrimary,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Seluruh sesi akun, riwayat tontonan, database SQLite, SharedPreferences, dan cache milik clone ini akan dihapus kembali ke kondisi baru (Fresh Install). Aplikasi ${clone.appName} asli di HP Anda 100% aman dan tidak terpengaruh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Surface(
                        color = InsSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { randomizeIdOnClear = !randomizeIdOnClear }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Acak Ulang Android ID & Device ID",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Mulai ulang sebagai perangkat & akun baru",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = randomizeIdOnClear,
                                onCheckedChange = { randomizeIdOnClear = it }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCloneDataDialog = false
                        executeClearCloneDataInContainer(randomizeIdOnClear)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InsCrimsonDanger),
                    modifier = Modifier.testTag("in_app_confirm_clear_data_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus Data & Reset", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCloneDataDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    // Modal Dialog: Real Extracted APK Layout (`R.layout.*`) & Activity (`<activity>`) Switcher
    if (showApkComponentPicker && mountedApk != null) {
        AlertDialog(
            onDismissRequest = { showApkComponentPicker = false },
            containerColor = InsSurfaceDark,
            title = {
                Column {
                    Text(
                        text = "Komponen Asli APK (${mountedApk.appLabel})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = mountedApk.extractedBaseApkPath,
                        fontSize = 10.sp,
                        color = InsEmeraldActive,
                        fontFamily = JetBrainsMonoFamily
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = pickerTab == 0,
                            onClick = { pickerTab = 0 },
                            label = { Text("Layout XML (${mountedApk.discoveredLayouts.size})") },
                            leadingIcon = {
                                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        )
                        FilterChip(
                            selected = pickerTab == 1,
                            onClick = { pickerTab = 1 },
                            label = { Text("Activity (${mountedApk.declaredActivities.size})") },
                            leadingIcon = {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (pickerTab == 0) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(mountedApk.discoveredLayouts, key = { it.resId }) { layoutItem ->
                                val isSelected = layoutItem.resId == activeLayoutResId
                                Surface(
                                    color = if (isSelected) InsSurfaceCard else Color(0xFF111827),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) InsCyanPrimary else Color.White.copy(alpha = 0.12f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeLayoutResId = layoutItem.resId
                                            activeLayoutLabel = "R.layout.${layoutItem.entryName}"
                                            showApkComponentPicker = false
                                            recordSandboxActivityTransition("Mount R.layout.${layoutItem.entryName}")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "R.layout.${layoutItem.entryName}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = JetBrainsMonoFamily
                                            )
                                            Text(
                                                text = "Resource ID: 0x${Integer.toHexString(layoutItem.resId)} • Diambil dari resources.arsc",
                                                color = InsEmeraldActive,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = InsCyanPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(mountedApk.declaredActivities, key = { it }) { actName ->
                                val isSelected = actName == activeActivityName
                                Surface(
                                    color = if (isSelected) InsSurfaceCard else Color(0xFF111827),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) InsCyanPrimary else Color.White.copy(alpha = 0.12f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeActivityName = actName
                                            activeLayoutResId = 0
                                            showApkComponentPicker = false
                                            recordSandboxActivityTransition("Launch Activity $actName")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = actName.substringAfterLast('.'),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = actName,
                                                color = InsCyanPrimary,
                                                fontSize = 10.sp,
                                                fontFamily = JetBrainsMonoFamily,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isSelected) InsEmeraldActive else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApkComponentPicker = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showBindInstalledAppDialog) {
        AlertDialog(
            onDismissRequest = { showBindInstalledAppDialog = false },
            containerColor = InsSurfaceDark,
            title = {
                Text(
                    text = "Pilih APK Terinstal ke Virtual Space",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(installedAppsOnPhone, key = { it.packageName }) { cand ->
                        Surface(
                            color = InsSurfaceCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBindInstalledAppDialog = false
                                    scope.launch {
                                        val db = VirtualSpaceDatabase.getInstance(context)
                                        val repo = VirtualSpaceRepository(context, db.virtualSpaceDao())
                                        val updatedClone = repo.rebindCloneToInstalledPackage(clone, cand)
                                        val installedOk = VirtualAppLauncher.installToVirtualSpace(
                                            apkPath = cand.sourceApkDir,
                                            userId = updatedClone.instanceIndex,
                                            context = context
                                        )
                                        if (installedOk) {
                                            onReboundCloneLaunched(updatedClone)
                                            VirtualAppLauncher.launchVirtualApp(
                                                packageName = updatedClone.packageName,
                                                userId = updatedClone.instanceIndex,
                                                context = context,
                                                cloneId = updatedClone.id,
                                                appNameHint = updatedClone.appName
                                            )
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RealAppIconBox(
                                    packageName = cand.packageName,
                                    appName = cand.appName,
                                    fallbackColorHex = cand.accentColorHex,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cand.appName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = cand.packageName,
                                        color = InsEmeraldActive,
                                        fontSize = 10.sp,
                                        fontFamily = JetBrainsMonoFamily
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = InsCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBindInstalledAppDialog = false }) {
                    Text("Tutup", color = InsCyanPrimary)
                }
            }
        )
    }
}
