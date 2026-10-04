package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CloneAppEntity
import com.example.data.InstallableAppCandidate
import com.example.ui.theme.InsAmberWarning
import com.example.ui.theme.InsCrimsonDanger
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsIndigoSecondary
import com.example.ui.theme.InsNavyDeep
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.InsSurfaceElevated
import com.example.ui.theme.InsVioletTertiary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.virtual.TmpfilesApkUploader
import com.example.virtual.TmpfilesUploadResult
import com.example.virtual.VirtualApkLauncher
import kotlinx.coroutines.launch

@Composable
fun RealAppIconBox(
    packageName: String,
    appName: String,
    fallbackColorHex: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName, appName) {
        VirtualApkLauncher.loadAppIconBitmap(
            context = context,
            packageName = packageName,
            appName = appName,
            fallbackColorInt = fallbackColorHex.toInt(),
            addCloneBadge = false
        )
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = appName,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun HomeDualSpaceScreen(
    clonedApps: List<CloneAppEntity>,
    searchQuery: String,
    isSearchActive: Boolean,
    openedFolderName: String?,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCleanMemory: () -> Unit,
    onOpenProfile: () -> Unit,
    onLaunchClone: (CloneAppEntity) -> Unit,
    onConfigureIdentity: (CloneAppEntity) -> Unit,
    onOpenInInsManager: (CloneAppEntity) -> Unit,
    onToggleFolder: (CloneAppEntity) -> Unit,
    onClearCloneData: (CloneAppEntity, Boolean) -> Unit = { _, _ -> },
    onClearCloneCache: (CloneAppEntity) -> Unit = {},
    onDeleteClone: (CloneAppEntity) -> Unit,
    onOpenFolderModal: (String?) -> Unit,
    onOpenAddCloneSheet: () -> Unit
) {
    val filteredApps = remember(clonedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            clonedApps
        } else {
            clonedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true) ||
                    it.buildModel.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val rootApps = remember(filteredApps, searchQuery) {
        if (searchQuery.isNotBlank()) {
            filteredApps
        } else {
            filteredApps.filter { it.folderName == null }
        }
    }

    val alatFolderApps = remember(clonedApps) {
        clonedApps.filter { it.folderName == "Alat" }
    }

    var showTmpfilesDialog by remember { mutableStateOf(false) }
    var selectedCloneForUpload by remember { mutableStateOf<CloneAppEntity?>(null) }
    var clonePendingClearData by remember { mutableStateOf<CloneAppEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Modern Dual Space Header ("Clone App", Memory Cleaner, Search, Profile)
        Surface(
            color = InsSurfaceDark,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(InsCyanPrimary, InsIndigoSecondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = InsNavyDeep,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Clone App",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    modifier = Modifier.testTag("home_header_title")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = InsCyanPrimary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "DUAL SPACE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InsCyanPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "com.ins.virtualspace • Ketuk ikon untuk jalankan APK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                selectedCloneForUpload = null
                                showTmpfilesDialog = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("upload_apk_tmpfiles_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload APK ke Tmpfiles.org",
                                tint = InsAmberWarning
                            )
                        }
                        IconButton(
                            onClick = onCleanMemory,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clean_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Bersihkan Memori Virtual",
                                tint = InsEmeraldActive
                            )
                        }
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("search_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Cari Aplikasi Klon",
                                tint = InsCyanPrimary
                            )
                        }
                        IconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profil Ruang Virtual",
                                tint = Color.White
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Cari aplikasi terklon, package, atau model...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("search_input")
                    )
                }
            }
        }

        // 2. Main Cloned Apps Launcher Desktop Grid (Folder "Alat" + Cloned APKs)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("cloned_apps_grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (searchQuery.isBlank()) {
                item(key = "folder_alat") {
                    AlatFolderGridCard(
                        folderName = "Alat",
                        folderApps = alatFolderApps,
                        onClick = { onOpenFolderModal("Alat") }
                    )
                }
            }

            items(rootApps, key = { it.id }) { clone ->
                ClonedAppGridCard(
                    clone = clone,
                    onLaunch = { onLaunchClone(clone) },
                    onConfigureIdentity = { onConfigureIdentity(clone) },
                    onOpenInInsManager = { onOpenInInsManager(clone) },
                    onToggleFolder = { onToggleFolder(clone) },
                    onUploadToTmpfiles = {
                        selectedCloneForUpload = clone
                        showTmpfilesDialog = true
                    },
                    onClearData = { clonePendingClearData = clone },
                    onClearCache = { onClearCloneCache(clone) },
                    onDelete = { onDeleteClone(clone) }
                )
            }

            item(key = "add_clone_grid_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenAddCloneSheet)
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("grid_add_clone_card"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(InsSurfaceElevated.copy(alpha = 0.65f))
                            .border(1.dp, InsCyanPrimary.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Aplikasi ke Ruang Virtual",
                            tint = InsCyanPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tambah Klon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "APK / HP",
                        style = MaterialTheme.typography.labelSmall,
                        color = InsCyanPrimary,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(8.dp))
                VirtualEngineHeroBanner(
                    totalClones = clonedApps.size,
                    runningCount = clonedApps.count { it.isRunning },
                    mockGpsCount = clonedApps.count { it.mockLocationEnabled }
                )
            }
        }
    }

    if (openedFolderName != null) {
        FolderAlatDialog(
            folderName = openedFolderName,
            appsInFolder = alatFolderApps,
            onDismiss = { onOpenFolderModal(null) },
            onLaunchClone = {
                onOpenFolderModal(null)
                onLaunchClone(it)
            },
            onConfigureIdentity = {
                onOpenFolderModal(null)
                onConfigureIdentity(it)
            },
            onOpenInInsManager = {
                onOpenFolderModal(null)
                onOpenInInsManager(it)
            },
            onClearCloneData = {
                onOpenFolderModal(null)
                clonePendingClearData = it
            },
            onRemoveFromFolder = { onToggleFolder(it) },
            onAddAppToFolder = {
                onOpenFolderModal(null)
                onOpenAddCloneSheet()
            }
        )
    }

    clonePendingClearData?.let { targetClone ->
        ClearCloneDataConfirmationDialog(
            clone = targetClone,
            onDismiss = { clonePendingClearData = null },
            onConfirmClearData = { randomizeDeviceId ->
                val c = targetClone
                clonePendingClearData = null
                onClearCloneData(c, randomizeDeviceId)
            },
            onConfirmClearCacheOnly = {
                val c = targetClone
                clonePendingClearData = null
                onClearCloneCache(c)
            }
        )
    }

    if (showTmpfilesDialog) {
        TmpfilesUploadDialog(
            clonedApps = clonedApps,
            initialSelectedClone = selectedCloneForUpload,
            onDismiss = { showTmpfilesDialog = false }
        )
    }
}

@Composable
private fun VirtualEngineHeroBanner(
    totalClones: Int,
    runningCount: Int,
    mockGpsCount: Int
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InsSurfaceCard.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_virtual_engine_banner),
                contentDescription = "INS Virtual Container Engine Banner",
                contentScale = ContentScale.Crop,
                alpha = 0.24f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                InsNavyDeep.copy(alpha = 0.94f),
                                InsNavyDeep.copy(alpha = 0.72f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = InsEmeraldActive.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(InsEmeraldActive)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "IN-PROCESS APK SANDBOX AKTIF",
                                style = MaterialTheme.typography.labelSmall,
                                color = InsEmeraldActive
                            )
                        }
                    }
                    Text(
                        text = "NO-ROOT",
                        style = MaterialTheme.typography.labelMedium,
                        color = InsCyanPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ruang Virtual Terisolasi (/virtual/user/0/)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadgePill(label = "Klon", value = "$totalClones APK", color = InsCyanPrimary)
                    StatBadgePill(label = "Task", value = "$runningCount Aktif", color = InsEmeraldActive)
                    StatBadgePill(label = "GPS", value = "$mockGpsCount Spoof", color = InsVioletTertiary)
                }
            }
        }
    }
}

@Composable
private fun StatBadgePill(label: String, value: String, color: Color) {
    Surface(
        color = InsSurfaceDark.copy(alpha = 0.8f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AlatFolderGridCard(
    folderName: String,
    folderApps: List<CloneAppEntity>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag("folder_alat_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(InsSurfaceElevated.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniFolderAppIcon(folderApps.getOrNull(0))
                    MiniFolderAppIcon(folderApps.getOrNull(1))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniFolderAppIcon(folderApps.getOrNull(2))
                    MiniFolderAppIcon(folderApps.getOrNull(3))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = folderName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${folderApps.size} Alat",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniFolderAppIcon(app: CloneAppEntity?) {
    if (app != null) {
        RealAppIconBox(
            packageName = app.packageName,
            appName = app.appName,
            fallbackColorHex = app.iconColorHex,
            modifier = Modifier
                .size(23.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(InsSurfaceDark.copy(alpha = 0.55f))
        )
    }
}

@Composable
private fun ClonedAppGridCard(
    clone: CloneAppEntity,
    onLaunch: () -> Unit,
    onConfigureIdentity: () -> Unit,
    onOpenInInsManager: () -> Unit,
    onToggleFolder: () -> Unit,
    onUploadToTmpfiles: () -> Unit = {},
    onClearData: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onLaunch)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag("clone_card_${clone.packageName}_${clone.instanceIndex}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            RealAppIconBox(
                packageName = clone.packageName,
                appName = clone.appName,
                fallbackColorHex = clone.iconColorHex,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = if (clone.isRunning) 1.5.dp else 0.5.dp,
                        color = if (clone.isRunning) InsEmeraldActive else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(18.dp)
                    )
            )

            // Top-right 3-dots menu button on the icon corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
            ) {
                Surface(
                    color = InsNavyDeep.copy(alpha = 0.82f),
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { menuExpanded = true }
                        .testTag("clone_menu_${clone.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi Klon ${clone.appName}",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Jalankan APK (${clone.recentsTaskTitle})") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onLaunch()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Hapus Data Clone (Reset Akun Baru)",
                                color = InsAmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = InsAmberWarning
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onClearData()
                        },
                        modifier = Modifier.testTag("menu_clear_data_${clone.id}")
                    )
                    DropdownMenuItem(
                        text = { Text("Bersihkan Cache Clone", color = InsEmeraldActive) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = InsEmeraldActive
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onClearCache()
                        },
                        modifier = Modifier.testTag("menu_clear_cache_${clone.id}")
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Device ID & Mock GPS Spoof") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onConfigureIdentity()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Buka di INS Manager (DB/XML)") },
                        leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenInInsManager()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (clone.folderName == "Alat") "Keluarkan dari Folder Alat"
                                else "Pindah ke Folder Alat"
                            )
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onToggleFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Upload APK ke Tmpfiles.org", color = InsAmberWarning) },
                        leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = InsAmberWarning) },
                        onClick = {
                            menuExpanded = false
                            onUploadToTmpfiles()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Hapus Klon & Sandbox", color = InsCrimsonDanger) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = InsCrimsonDanger)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }

            // Bottom-end Clone badge
            Surface(
                color = if (clone.isRunning) InsEmeraldActive else InsNavyDeep,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (clone.isRunning) InsNavyDeep else InsCyanPrimary),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = if (clone.instanceIndex == 0) "CLONE" else "#${clone.instanceIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = if (clone.isRunning) InsNavyDeep else InsCyanPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = clone.appName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = InsSurfaceElevated.copy(alpha = 0.75f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .clickable(onClick = onConfigureIdentity)
                    .testTag("quick_spoof_${clone.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (clone.mockLocationEnabled) Icons.Default.GpsFixed else Icons.Default.Security,
                        contentDescription = "Konfigurasi Identitas ${clone.appName}",
                        tint = if (clone.mockLocationEnabled) InsEmeraldActive else InsCyanPrimary,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = clone.buildModel.take(8),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = InsAmberWarning.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, InsAmberWarning.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clickable(onClick = onClearData)
                    .testTag("quick_clear_data_${clone.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Hapus Data Clone ${clone.appName}",
                        tint = InsAmberWarning,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Hapus Data",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = InsAmberWarning,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ClearCloneDataConfirmationDialog(
    clone: CloneAppEntity,
    onDismiss: () -> Unit,
    onConfirmClearData: (Boolean) -> Unit,
    onConfirmClearCacheOnly: () -> Unit
) {
    var randomizeDeviceId by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InsSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = InsAmberWarning
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Hapus Data Clone (${clone.appName})?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "User #${clone.instanceIndex} • ${clone.packageName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InsCyanPrimary,
                        fontFamily = JetBrainsMonoFamily
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = InsSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "100% Aman untuk Aplikasi Asli di HP:",
                            style = MaterialTheme.typography.labelMedium,
                            color = InsEmeraldActive,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tindakan ini HANYA menghapus data akun, sesi login, database SQLite, SharedPreferences, dan cache di dalam direktori virtual:\n${clone.canonicalVirtualDataPath}\nData aplikasi ${clone.appName} asli di HP Anda tidak akan tersentuh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Surface(
                    color = InsSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { randomizeDeviceId = !randomizeDeviceId }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset Android ID & Device ID Baru",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Buat clone terbaca sebagai perangkat baru saat dibuka kembali",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = randomizeDeviceId,
                            onCheckedChange = { randomizeDeviceId = it }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onConfirmClearCacheOnly,
                    border = BorderStroke(1.dp, InsEmeraldActive),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_clear_cache_only_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = InsEmeraldActive, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bersihkan Cache Saja (Tanpa Logout Akun)", color = InsEmeraldActive)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmClearData(randomizeDeviceId) },
                colors = ButtonDefaults.buttonColors(containerColor = InsCrimsonDanger),
                modifier = Modifier.testTag("confirm_clear_clone_data_button")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hapus Data & Reset Clone", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun FolderAlatDialog(
    folderName: String,
    appsInFolder: List<CloneAppEntity>,
    onDismiss: () -> Unit,
    onLaunchClone: (CloneAppEntity) -> Unit,
    onConfigureIdentity: (CloneAppEntity) -> Unit,
    onOpenInInsManager: (CloneAppEntity) -> Unit,
    onClearCloneData: (CloneAppEntity) -> Unit = {},
    onRemoveFromFolder: (CloneAppEntity) -> Unit,
    onAddAppToFolder: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InsSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = InsVioletTertiary)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Folder Virtual: $folderName", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        text = "${appsInFolder.size} Aplikasi Terisolasi",
                        style = MaterialTheme.typography.labelSmall,
                        color = InsCyanPrimary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (appsInFolder.isEmpty()) {
                    Text(
                        text = "Belum ada aplikasi di dalam folder '$folderName'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    appsInFolder.forEach { clone ->
                        Surface(
                            color = InsSurfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(clone.iconColorHex).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLaunchClone(clone) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RealAppIconBox(
                                    packageName = clone.packageName,
                                    appName = clone.appName,
                                    fallbackColorHex = clone.iconColorHex,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = clone.appName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(
                                        text = "${clone.buildModel} • ID: ${clone.androidId.take(8)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InsCyanPrimary
                                    )
                                }
                                IconButton(onClick = { onClearCloneData(clone) }) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = "Hapus Data Clone", tint = InsAmberWarning)
                                }
                                IconButton(onClick = { onConfigureIdentity(clone) }) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Spoof ID", tint = InsCyanPrimary)
                                }
                                IconButton(onClick = { onOpenInInsManager(clone) }) {
                                    Icon(Icons.Default.Storage, contentDescription = "INS Manager", tint = InsVioletTertiary)
                                }
                                IconButton(onClick = { onRemoveFromFolder(clone) }) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Keluarkan dari folder", tint = InsAmberWarning)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAddAppToFolder,
                colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah ke Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCloneBottomSheet(
    candidates: List<InstallableAppCandidate>,
    onDismiss: () -> Unit,
    onConfirmAddClone: (InstallableAppCandidate, String?, Boolean) -> Unit,
    onImportApkUri: ((Uri, String?, Boolean) -> Unit)? = null
) {
    var filterQuery by remember { mutableStateOf("") }
    var targetFolder by remember { mutableStateOf<String?>(null) }
    var enableMockGps by remember { mutableStateOf(true) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && onImportApkUri != null) {
            onImportApkUri(uri, targetFolder, enableMockGps)
        }
    }

    val filtered = remember(candidates, filterQuery) {
        if (filterQuery.isBlank()) candidates
        else candidates.filter {
            it.appName.contains(filterQuery, ignoreCase = true) ||
                it.packageName.contains(filterQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = InsSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tambah Aplikasi ke Ruang Virtual",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Pilih aplikasi terinstal di HP atau impor file .APK",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onImportApkUri != null) {
                    OutlinedButton(
                        onClick = { apkPickerLauncher.launch("application/vnd.android.package-archive") },
                        border = BorderStroke(1.dp, InsCyanPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("import_apk_file_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = InsCyanPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Impor .APK", style = MaterialTheme.typography.labelMedium, color = InsCyanPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = targetFolder == null,
                    onClick = { targetFolder = null },
                    label = { Text("Beranda Utama") }
                )
                FilterChip(
                    selected = targetFolder == "Alat",
                    onClick = { targetFolder = "Alat" },
                    label = { Text("Folder 'Alat'") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fake GPS", style = MaterialTheme.typography.labelSmall, color = InsEmeraldActive)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(checked = enableMockGps, onCheckedChange = { enableMockGps = it })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Cari APK yang terinstal di HP Anda...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_clone_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.packageName }) { candidate ->
                    Surface(
                        color = InsSurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(candidate.accentColorHex).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RealAppIconBox(
                                packageName = candidate.packageName,
                                appName = candidate.appName,
                                fallbackColorHex = candidate.accentColorHex,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = candidate.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (candidate.isInstalledOnHost) InsEmeraldActive.copy(alpha = 0.2f) else InsCyanPrimary.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (candidate.isInstalledOnHost) "TERINSTAL" else candidate.categoryTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (candidate.isInstalledOnHost) InsEmeraldActive else InsCyanPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = candidate.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onConfirmAddClone(candidate, targetFolder, enableMockGps) },
                                colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("clone_candidate_btn_${candidate.packageName}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kloning", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TmpfilesUploadDialog(
    clonedApps: List<CloneAppEntity>,
    initialSelectedClone: CloneAppEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetClone by remember { mutableStateOf(initialSelectedClone) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var uploadedBytesState by remember { mutableStateOf(0L) }
    var totalBytesState by remember { mutableStateOf(0L) }
    var uploadResult by remember { mutableStateOf<TmpfilesUploadResult?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Link disalin: $text", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        containerColor = InsSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = InsAmberWarning)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Upload APK ke Tmpfiles.org",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "POST https://tmpfiles.org/api/v1/upload",
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
                    text = "Pilih file APK yang ingin diunggah ke Tmpfiles.org:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Host App APK itself ("Clone App" APK)
                Surface(
                    color = if (targetClone == null) InsCyanPrimary.copy(alpha = 0.16f) else InsSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (targetClone == null) InsCyanPrimary else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUploading) {
                            targetClone = null
                            uploadResult = null
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = InsCyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "APK Clone App (Aplikasi Utama Ini)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = context.packageName,
                                color = InsEmeraldActive,
                                fontSize = 10.sp,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    }
                }

                // Option 2: Cloned Apps APKs
                if (clonedApps.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(135.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(clonedApps, key = { it.id }) { itemClone ->
                            val isSelected = targetClone?.id == itemClone.id
                            Surface(
                                color = if (isSelected) InsCyanPrimary.copy(alpha = 0.16f) else InsSurfaceCard,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) InsCyanPrimary else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isUploading) {
                                        targetClone = itemClone
                                        uploadResult = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RealAppIconBox(
                                        packageName = itemClone.packageName,
                                        appName = itemClone.appName,
                                        fallbackColorHex = itemClone.iconColorHex,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${itemClone.appName} (base.apk)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = itemClone.packageName,
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isUploading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = InsEmeraldActive
                        )
                        val mbUploaded = String.format("%.2f", uploadedBytesState / (1024.0 * 1024.0))
                        val mbTotal = String.format("%.2f", totalBytesState / (1024.0 * 1024.0))
                        Text(
                            text = "Mengunggah ke tmpfiles.org... $mbUploaded MB / $mbTotal MB (${(uploadProgress * 100).toInt()}%)",
                            color = InsEmeraldActive,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                }

                uploadResult?.let { result ->
                    if (result.success) {
                        Surface(
                            color = InsEmeraldActive.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Berhasil Diunggah ke Tmpfiles.org!",
                                    color = InsEmeraldActive,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Link Download Langsung:\n${result.directDownloadUrl}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { copyToClipboard("Tmpfiles APK URL", result.directDownloadUrl) },
                                        colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = InsNavyDeep, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salin Direct Link", color = InsNavyDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { copyToClipboard("Tmpfiles Page URL", result.pageUrl) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = InsCyanPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salin Page URL", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = InsCrimsonDanger.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, InsCrimsonDanger.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Gagal upload: ${result.errorMessage}",
                                color = InsCrimsonDanger,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isUploading,
                onClick = {
                    scope.launch {
                        isUploading = true
                        uploadResult = null
                        uploadProgress = 0f
                        val selected = targetClone
                        val apkFile = if (selected == null) {
                            TmpfilesApkUploader.getHostApkFile(context)
                        } else {
                            TmpfilesApkUploader.getCloneApkFile(context, selected)
                        }
                        val fileName = if (selected == null) {
                            "CloneApp-DualSpace-v1.0.apk"
                        } else {
                            "${selected.appName.replace(" ", "_")}-Clone.apk"
                        }
                        if (apkFile == null) {
                            uploadResult = TmpfilesUploadResult(
                                success = false,
                                errorMessage = "File APK belum tersedia di perangkat untuk diunggah."
                            )
                            isUploading = false
                            return@launch
                        }
                        totalBytesState = apkFile.length()
                        val res = TmpfilesApkUploader.uploadApkToTmpfiles(
                            apkFile = apkFile,
                            desiredFileName = fileName,
                            onProgress = { uploaded, total ->
                                uploadedBytesState = uploaded
                                totalBytesState = total
                                uploadProgress = if (total > 0L) {
                                    (uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            }
                        )
                        uploadResult = res
                        isUploading = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = InsEmeraldActive),
                modifier = Modifier.testTag("start_tmpfiles_upload_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = InsNavyDeep, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isUploading) "Mengunggah..." else "Upload Sekarang",
                    color = InsNavyDeep,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isUploading,
                onClick = onDismiss
            ) {
                Text("Tutup", color = Color.Gray)
            }
        }
    )
}
