package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AddCloneBottomSheet
import com.example.ui.ArchitectureBlueprintScreen
import com.example.ui.HomeDualSpaceScreen
import com.example.ui.IdentitySpoofScreen
import com.example.ui.InsManagerScreen
import com.example.ui.MainNavTab
import com.example.ui.VirtualSpaceViewModel
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsNavyDeep
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.InsSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.virtual.BlackBoxCore
import com.example.virtual.VirtualAppLauncher
import com.example.virtual.VirtualProcessManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        VirtualProcessManager.installVirtualCrashGuard(this)
        super.onCreate(savedInstanceState)
        VirtualAppLauncher.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                InsVirtualSpaceApp()
            }
        }
    }
}

@Composable
fun InsVirtualSpaceApp(
    viewModel: VirtualSpaceViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val clonedApps by viewModel.clonedApps.collectAsStateWithLifecycle()
    val hookLogs by viewModel.recentHookLogs.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val openedFolderName by viewModel.openedFolderName.collectAsStateWithLifecycle()
    val editingIdentityClone by viewModel.editingIdentityClone.collectAsStateWithLifecycle()
    val showAddCloneSheet by viewModel.showAddCloneSheet.collectAsStateWithLifecycle()
    val showProfileDialog by viewModel.showProfileDialog.collectAsStateWithLifecycle()
    val installableCandidates by viewModel.installableCandidates.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    // INS Manager states
    val selectedManagerCloneId by viewModel.selectedManagerCloneId.collectAsStateWithLifecycle()
    val insSubTab by viewModel.insSubTab.collectAsStateWithLifecycle()
    val currentRelativePath by viewModel.currentRelativePath.collectAsStateWithLifecycle()
    val directoryFiles by viewModel.directoryFiles.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val selectedDbFilePath by viewModel.selectedDbFilePath.collectAsStateWithLifecycle()
    val sqliteTables by viewModel.sqliteTables.collectAsStateWithLifecycle()
    val selectedSqliteTable by viewModel.selectedSqliteTable.collectAsStateWithLifecycle()
    val sqliteTableData by viewModel.sqliteTableData.collectAsStateWithLifecycle()
    val selectedXmlFilePath by viewModel.selectedXmlFilePath.collectAsStateWithLifecycle()
    val xmlPrefEntries by viewModel.xmlPrefEntries.collectAsStateWithLifecycle()
    val rawXmlContent by viewModel.rawXmlContent.collectAsStateWithLifecycle()
    val previewTextFile by viewModel.previewTextFile.collectAsStateWithLifecycle()

    val activeManagerClone = clonedApps.find { it.id == selectedManagerCloneId } ?: clonedApps.firstOrNull()

    // BackHandler for secondary tabs / folders
    BackHandler(enabled = openedFolderName != null || currentTab != MainNavTab.HOME_SPACE) {
        if (openedFolderName != null) {
            viewModel.openFolder(null)
        } else {
            viewModel.selectTab(MainNavTab.HOME_SPACE)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(InsNavyDeep)
    ) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            containerColor = InsNavyDeep,
            floatingActionButton = {
                if (currentTab == MainNavTab.HOME_SPACE) {
                    FloatingActionButton(
                        onClick = { viewModel.setShowAddCloneSheet(true) },
                        containerColor = InsCyanPrimary,
                        contentColor = InsNavyDeep,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.testTag("fab_add_clone")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah Aplikasi ke Ruang Virtual"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tambah Klon",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = InsSurfaceDark,
                        tonalElevation = 8.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentTab == MainNavTab.HOME_SPACE,
                            onClick = { viewModel.selectTab(MainNavTab.HOME_SPACE) },
                            icon = { Icon(Icons.Default.GridView, contentDescription = "Clone Space") },
                            label = { Text("Clone Space") },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = currentTab == MainNavTab.INS_MANAGER,
                            onClick = { viewModel.selectTab(MainNavTab.INS_MANAGER) },
                            icon = { Icon(Icons.Default.Storage, contentDescription = "INS Manager") },
                            label = { Text("INS Manager") },
                            modifier = Modifier.testTag("nav_tab_ins_manager")
                        )
                        NavigationBarItem(
                            selected = currentTab == MainNavTab.IDENTITY_SPOOF,
                            onClick = { viewModel.selectTab(MainNavTab.IDENTITY_SPOOF) },
                            icon = { Icon(Icons.Default.Fingerprint, contentDescription = "Identity Spoof") },
                            label = { Text("Identity ID") },
                            modifier = Modifier.testTag("nav_tab_identity")
                        )
                        NavigationBarItem(
                            selected = currentTab == MainNavTab.ARCHITECTURE,
                            onClick = { viewModel.selectTab(MainNavTab.ARCHITECTURE) },
                            icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = "Engine Core") },
                            label = { Text("Arsitektur") },
                            modifier = Modifier.testTag("nav_tab_architecture")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = InsSurfaceDark,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NavigationRailItem(
                            selected = currentTab == MainNavTab.HOME_SPACE,
                            onClick = { viewModel.selectTab(MainNavTab.HOME_SPACE) },
                            icon = { Icon(Icons.Default.GridView, contentDescription = "Clone Space") },
                            label = { Text("Clone Space") },
                            modifier = Modifier.testTag("rail_tab_home")
                        )
                        NavigationRailItem(
                            selected = currentTab == MainNavTab.INS_MANAGER,
                            onClick = { viewModel.selectTab(MainNavTab.INS_MANAGER) },
                            icon = { Icon(Icons.Default.Storage, contentDescription = "INS Manager") },
                            label = { Text("INS Manager") },
                            modifier = Modifier.testTag("rail_tab_ins_manager")
                        )
                        NavigationRailItem(
                            selected = currentTab == MainNavTab.IDENTITY_SPOOF,
                            onClick = { viewModel.selectTab(MainNavTab.IDENTITY_SPOOF) },
                            icon = { Icon(Icons.Default.Fingerprint, contentDescription = "Identity Spoof") },
                            label = { Text("Identity ID") },
                            modifier = Modifier.testTag("rail_tab_identity")
                        )
                        NavigationRailItem(
                            selected = currentTab == MainNavTab.ARCHITECTURE,
                            onClick = { viewModel.selectTab(MainNavTab.ARCHITECTURE) },
                            icon = { Icon(Icons.Default.DeveloperBoard, contentDescription = "Arsitektur") },
                            label = { Text("Arsitektur") },
                            modifier = Modifier.testTag("rail_tab_architecture")
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentTab) {
                        MainNavTab.HOME_SPACE -> {
                            HomeDualSpaceScreen(
                                clonedApps = clonedApps,
                                searchQuery = searchQuery,
                                isSearchActive = isSearchActive,
                                openedFolderName = openedFolderName,
                                onToggleSearch = viewModel::toggleSearchBar,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onCleanMemory = viewModel::cleanVirtualMemory,
                                onOpenProfile = { viewModel.setShowProfileDialog(true) },
                                onLaunchClone = { clone ->
                                    scope.launch {
                                        val launched = viewModel.prepareCloneLaunch(clone)
                                        VirtualAppLauncher.launchCloneInVirtualSpace(context, launched)
                                    }
                                },
                                onConfigureIdentity = { clone ->
                                    viewModel.openIdentityEditor(clone)
                                    viewModel.selectTab(MainNavTab.IDENTITY_SPOOF)
                                },
                                onOpenInInsManager = { clone ->
                                    viewModel.openCloneInInsManager(clone)
                                },
                                onToggleFolder = viewModel::toggleCloneFolder,
                                onClearCloneData = { clone, randomizeId ->
                                    viewModel.clearCloneData(clone, randomizeId)
                                },
                                onClearCloneCache = { clone ->
                                    viewModel.clearCloneCacheFor(clone)
                                },
                                onDeleteClone = viewModel::deleteClone,
                                onOpenFolderModal = viewModel::openFolder,
                                onOpenAddCloneSheet = { viewModel.setShowAddCloneSheet(true) }
                            )
                        }

                        MainNavTab.INS_MANAGER -> {
                            InsManagerScreen(
                                clonedApps = clonedApps,
                                selectedClone = activeManagerClone,
                                subTab = insSubTab,
                                currentRelativePath = currentRelativePath,
                                directoryFiles = directoryFiles,
                                storageStats = storageStats,
                                selectedDbFilePath = selectedDbFilePath,
                                sqliteTables = sqliteTables,
                                selectedSqliteTable = selectedSqliteTable,
                                sqliteTableData = sqliteTableData,
                                selectedXmlFilePath = selectedXmlFilePath,
                                xmlPrefEntries = xmlPrefEntries,
                                rawXmlContent = rawXmlContent,
                                previewTextFile = previewTextFile,
                                onSelectClone = viewModel::selectManagerClone,
                                onSelectSubTab = viewModel::setInsSubTab,
                                onNavigateDirectory = viewModel::navigateSandboxDirectory,
                                onNavigateUp = viewModel::navigateUpSandboxDirectory,
                                onOpenFileItem = viewModel::openSandboxFileItem,
                                onClosePreviewTextFile = viewModel::closePreviewTextFile,
                                onClearCache = viewModel::clearSelectedCloneCache,
                                onResetSandboxData = viewModel::resetSelectedCloneData,
                                onSelectSqliteTable = viewModel::selectSqliteTable,
                                onUpdateSqliteRow = viewModel::updateSqliteRow,
                                onInsertSqliteRow = viewModel::insertSqliteRow,
                                onDeleteSqliteRow = viewModel::deleteSqliteRow,
                                onExecuteCustomSql = viewModel::executeCustomSql,
                                onUpsertXmlEntry = viewModel::upsertXmlPreferenceEntry,
                                onDeleteXmlEntry = viewModel::deleteXmlPreferenceEntry,
                                onSaveRawXml = viewModel::saveRawXmlContent
                            )
                        }

                        MainNavTab.IDENTITY_SPOOF -> {
                            IdentitySpoofScreen(
                                clonedApps = clonedApps,
                                activeClone = editingIdentityClone ?: activeManagerClone,
                                hookLogs = hookLogs,
                                onSelectClone = { clone ->
                                    viewModel.openIdentityEditor(clone)
                                    viewModel.selectManagerClone(clone)
                                },
                                onRandomizeIdentity = viewModel::randomizeCloneIdentity,
                                onSaveCustomIdentity = viewModel::saveCustomCloneIdentity,
                                onApplyLocationPreset = viewModel::applyMockLocationPreset,
                                onTriggerSweep = { clone ->
                                    viewModel.triggerHookSweepForClone(clone)
                                    viewModel.postBannerMessage("Binder IPC Hook diuji untuk ${clone.recentsTaskTitle}")
                                },
                                onClearLogs = viewModel::clearAllHookLogs
                            )
                        }

                        MainNavTab.ARCHITECTURE -> {
                            ArchitectureBlueprintScreen()
                        }
                    }

                    // Floating Status Feedback Banner
                    if (statusBannerMessage != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Surface(
                                color = InsSurfaceElevated,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.7f)),
                                tonalElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = InsEmeraldActive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = statusBannerMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = viewModel::dismissBannerMessage,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Tutup Notifikasi",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add New Clone ModalBottomSheet (FAB '+')
    if (showAddCloneSheet) {
        AddCloneBottomSheet(
            candidates = installableCandidates,
            onDismiss = { viewModel.setShowAddCloneSheet(false) },
            onConfirmAddClone = { candidate, folder, enableMockGps ->
                viewModel.addNewClone(candidate, folder, enableMockGps)
            },
            onImportApkUri = { uri, folder, enableMockGps ->
                viewModel.importApkUri(uri, folder, enableMockGps)
            }
        )
    }

    // Virtual Engine Profile Dialog
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowProfileDialog(false) },
            containerColor = InsSurfaceDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = InsCyanPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("INS Virtual Space Engine", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("com.ins.virtualspace • v3.4.0-PRO", style = MaterialTheme.typography.labelSmall, color = InsCyanPrimary)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = InsSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Status Engine: ACTIVE (Root-Free Mode)", style = MaterialTheme.typography.labelMedium, color = InsEmeraldActive)
                            Text("Mount Path: /data/user/0/com.ins.virtualspace/virtual/user/0/", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily, color = Color.White)
                            Text("Total Instance Terklon: ${clonedApps.size} APK", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Intersepsi Binder Tercatat: ${hookLogs.size} Events", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowProfileDialog(false) }) {
                    Text("Tutup")
                }
            }
        )
    }
}
