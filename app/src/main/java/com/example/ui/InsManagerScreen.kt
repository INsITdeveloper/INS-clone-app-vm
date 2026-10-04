package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CloneAppEntity
import com.example.ui.theme.InsAmberWarning
import com.example.ui.theme.InsCrimsonDanger
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsIndigoSecondary
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.InsSurfaceElevated
import com.example.ui.theme.InsVioletTertiary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.virtual.CloneStorageStats
import com.example.virtual.SandboxFileItem
import com.example.virtual.SandboxFileType
import com.example.virtual.SqliteTableData
import com.example.virtual.XmlPrefEntry
import com.example.virtual.XmlPrefType

@Composable
fun InsManagerScreen(
    clonedApps: List<CloneAppEntity>,
    selectedClone: CloneAppEntity?,
    subTab: InsManagerSubTab,
    currentRelativePath: String,
    directoryFiles: List<SandboxFileItem>,
    storageStats: CloneStorageStats?,
    selectedDbFilePath: String?,
    sqliteTables: List<String>,
    selectedSqliteTable: String?,
    sqliteTableData: SqliteTableData?,
    selectedXmlFilePath: String?,
    xmlPrefEntries: List<XmlPrefEntry>,
    rawXmlContent: String,
    previewTextFile: Pair<String, String>?,
    onSelectClone: (CloneAppEntity) -> Unit,
    onSelectSubTab: (InsManagerSubTab) -> Unit,
    onNavigateDirectory: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFileItem: (SandboxFileItem) -> Unit,
    onClosePreviewTextFile: () -> Unit,
    onClearCache: () -> Unit,
    onResetSandboxData: () -> Unit,
    onSelectSqliteTable: (String) -> Unit,
    onUpdateSqliteRow: (String, Map<String, String>) -> Unit,
    onInsertSqliteRow: (Map<String, String>) -> Unit,
    onDeleteSqliteRow: (String) -> Unit,
    onExecuteCustomSql: (String) -> Unit,
    onUpsertXmlEntry: (String, String, XmlPrefType) -> Unit,
    onDeleteXmlEntry: (String) -> Unit,
    onSaveRawXml: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header & Clone Switcher
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INS Manager",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            modifier = Modifier.testTag("ins_manager_header")
                        )
                        Text(
                            text = "Virtual Sandbox Explorer • SQLite Viewer • XML Editor",
                            style = MaterialTheme.typography.labelSmall,
                            color = InsCyanPrimary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onClearCache,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("ins_clear_cache_button")
                        ) {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = InsEmeraldActive,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Cache", style = MaterialTheme.typography.labelSmall, color = InsEmeraldActive)
                        }
                        OutlinedButton(
                            onClick = onResetSandboxData,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, InsAmberWarning.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("ins_reset_data_button")
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                tint = InsAmberWarning,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Data", style = MaterialTheme.typography.labelSmall, color = InsAmberWarning)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                // Horizontal Clone Instance Selector
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(clonedApps, key = { it.id }) { app ->
                        val isSelected = selectedClone?.id == app.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectClone(app) },
                            label = {
                                Text(
                                    text = app.recentsTaskTitle,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(app.iconColorHex))
                                )
                            },
                            modifier = Modifier.testTag("ins_select_clone_${app.id}")
                        )
                    }
                }
            }
        }

        // Sub-Tab Row: File Explorer | SQLite Viewer | XML Editor
        TabRow(
            selectedTabIndex = subTab.ordinal,
            containerColor = InsSurfaceElevated,
            contentColor = InsCyanPrimary
        ) {
            Tab(
                selected = subTab == InsManagerSubTab.FILE_EXPLORER,
                onClick = { onSelectSubTab(InsManagerSubTab.FILE_EXPLORER) },
                text = { Text("Direktori Sandbox", style = MaterialTheme.typography.labelLarge) },
                icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("ins_subtab_explorer")
            )
            Tab(
                selected = subTab == InsManagerSubTab.SQLITE_VIEWER,
                onClick = { onSelectSubTab(InsManagerSubTab.SQLITE_VIEWER) },
                text = { Text("SQLite Viewer", style = MaterialTheme.typography.labelLarge) },
                icon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("ins_subtab_sqlite")
            )
            Tab(
                selected = subTab == InsManagerSubTab.XML_PREFS_EDITOR,
                onClick = { onSelectSubTab(InsManagerSubTab.XML_PREFS_EDITOR) },
                text = { Text("XML SharedPrefs", style = MaterialTheme.typography.labelLarge) },
                icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("ins_subtab_xml")
            )
        }

        if (selectedClone != null) {
            when (subTab) {
                InsManagerSubTab.FILE_EXPLORER -> {
                    SandboxFileExplorerPane(
                        clone = selectedClone,
                        currentRelativePath = currentRelativePath,
                        files = directoryFiles,
                        stats = storageStats,
                        onNavigateDirectory = onNavigateDirectory,
                        onNavigateUp = onNavigateUp,
                        onOpenFileItem = onOpenFileItem
                    )
                }
                InsManagerSubTab.SQLITE_VIEWER -> {
                    SqliteInteractiveViewerPane(
                        clone = selectedClone,
                        selectedDbFilePath = selectedDbFilePath,
                        tables = sqliteTables,
                        selectedTable = selectedSqliteTable,
                        tableData = sqliteTableData,
                        onSelectTable = onSelectSqliteTable,
                        onUpdateRow = onUpdateSqliteRow,
                        onInsertRow = onInsertSqliteRow,
                        onDeleteRow = onDeleteSqliteRow,
                        onExecuteSql = onExecuteCustomSql
                    )
                }
                InsManagerSubTab.XML_PREFS_EDITOR -> {
                    XmlSharedPreferencesEditorPane(
                        clone = selectedClone,
                        selectedXmlFilePath = selectedXmlFilePath,
                        entries = xmlPrefEntries,
                        rawXmlContent = rawXmlContent,
                        onUpsertEntry = onUpsertXmlEntry,
                        onDeleteEntry = onDeleteXmlEntry,
                        onSaveRawXml = onSaveRawXml
                    )
                }
            }
        }
    }

    // Text / JSON Config File Preview Dialog
    if (previewTextFile != null) {
        AlertDialog(
            onDismissRequest = onClosePreviewTextFile,
            containerColor = InsSurfaceDark,
            title = {
                Text(text = previewTextFile.first, style = MaterialTheme.typography.titleMedium, color = InsCyanPrimary)
            },
            text = {
                Surface(
                    color = InsSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewTextFile.second,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onClosePreviewTextFile) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun SandboxFileExplorerPane(
    clone: CloneAppEntity,
    currentRelativePath: String,
    files: List<SandboxFileItem>,
    stats: CloneStorageStats?,
    onNavigateDirectory: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFileItem: (SandboxFileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sandbox_file_explorer_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Canonical Sandbox Path Card + Storage Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Jalur Isolasi Virtual Aktif:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${clone.canonicalVirtualDataPath}${if (currentRelativePath.isNotEmpty()) "$currentRelativePath/" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = InsCyanPrimary
                    )
                    Text(
                        text = "Alias Mount: ${clone.legacyAliasDataPath}${if (currentRelativePath.isNotEmpty()) "$currentRelativePath/" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (stats != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StorageMiniMetric("Total", SandboxFileItem.formatBytes(stats.totalBytes), InsCyanPrimary)
                            StorageMiniMetric("SQLite (${stats.dbFileCount})", SandboxFileItem.formatBytes(stats.databaseBytes), InsIndigoSecondary)
                            StorageMiniMetric("XML (${stats.xmlFileCount})", SandboxFileItem.formatBytes(stats.sharedPrefsBytes), InsVioletTertiary)
                            StorageMiniMetric("Cache", SandboxFileItem.formatBytes(stats.cacheBytes), InsEmeraldActive)
                        }
                    }
                }
            }
        }

        // Quick Directory Jump Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentRelativePath.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onNavigateUp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("explorer_up_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ke Atas", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FilterChip(
                    selected = currentRelativePath.isEmpty(),
                    onClick = { onNavigateDirectory("") },
                    label = { Text("/ (Root)") }
                )
                FilterChip(
                    selected = currentRelativePath == "databases",
                    onClick = { onNavigateDirectory("databases") },
                    label = { Text("databases/") }
                )
                FilterChip(
                    selected = currentRelativePath == "shared_prefs",
                    onClick = { onNavigateDirectory("shared_prefs") },
                    label = { Text("shared_prefs/") }
                )
                FilterChip(
                    selected = currentRelativePath == "cache",
                    onClick = { onNavigateDirectory("cache") },
                    label = { Text("cache/") }
                )
                FilterChip(
                    selected = currentRelativePath == "files",
                    onClick = { onNavigateDirectory("files") },
                    label = { Text("files/") }
                )
            }
        }

        items(files, key = { it.absolutePath }) { item ->
            val iconColor = when (item.fileType) {
                SandboxFileType.DIRECTORY -> InsCyanPrimary
                SandboxFileType.SQLITE_DB -> InsIndigoSecondary
                SandboxFileType.SHARED_PREFS_XML -> InsVioletTertiary
                SandboxFileType.JSON_CONFIG -> InsEmeraldActive
                SandboxFileType.CACHE_BIN -> InsAmberWarning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val iconVec = when (item.fileType) {
                SandboxFileType.DIRECTORY -> Icons.Default.Folder
                SandboxFileType.SQLITE_DB -> Icons.Default.TableChart
                SandboxFileType.SHARED_PREFS_XML -> Icons.Default.Code
                SandboxFileType.JSON_CONFIG -> Icons.Default.DataObject
                else -> Icons.Default.Description
            }

            Surface(
                color = InsSurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, iconColor.copy(alpha = 0.28f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFileItem(item) }
                    .testTag("sandbox_file_${item.name}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = iconVec, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.unixPermissions} • ${item.formattedDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.labelMedium,
                            color = iconColor,
                            fontFamily = JetBrainsMonoFamily
                        )
                        Text(
                            text = when (item.fileType) {
                                SandboxFileType.DIRECTORY -> "${item.childCount} item"
                                SandboxFileType.SQLITE_DB -> "Buka SQLite →"
                                SandboxFileType.SHARED_PREFS_XML -> "Edit XML →"
                                else -> "Lihat File"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageMiniMetric(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SqliteInteractiveViewerPane(
    clone: CloneAppEntity,
    selectedDbFilePath: String?,
    tables: List<String>,
    selectedTable: String?,
    tableData: SqliteTableData?,
    onSelectTable: (String) -> Unit,
    onUpdateRow: (String, Map<String, String>) -> Unit,
    onInsertRow: (Map<String, String>) -> Unit,
    onDeleteRow: (String) -> Unit,
    onExecuteSql: (String) -> Unit
) {
    var editingRow by remember { mutableStateOf<Map<String, String>?>(null) }
    var isInsertingRow by remember { mutableStateOf(false) }
    var customSqlInput by remember { mutableStateOf("") }
    var showSqlConsole by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sqlite_viewer_pane"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Database Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, InsIndigoSecondary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SQLite Database Sandbox",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = selectedDbFilePath?.substringAfterLast('/') ?: "app_sandbox_data.db",
                                style = MaterialTheme.typography.labelMedium,
                                color = InsCyanPrimary,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showSqlConsole = !showSqlConsole },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("toggle_sql_console_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SQL Query", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { isInsertingRow = true },
                                colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("sqlite_add_row_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Baris", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (showSqlConsole) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customSqlInput,
                            onValueChange = { customSqlInput = it },
                            placeholder = { Text("SELECT * FROM account_session LIMIT 10;") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_sql_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (customSqlInput.isNotBlank()) {
                                    onExecuteSql(customSqlInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = InsIndigoSecondary),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Jalankan SQL")
                        }
                    }
                }
            }
        }

        // Table Selector Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tables, key = { it }) { tbl ->
                    FilterChip(
                        selected = tbl == selectedTable,
                        onClick = { onSelectTable(tbl) },
                        label = {
                            Text(text = tbl, fontFamily = JetBrainsMonoFamily)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("sqlite_table_chip_$tbl")
                    )
                }
            }
        }

        // Table Schema & Interactive Rows
        if (tableData != null) {
            item {
                Text(
                    text = "${tableData.statusMessage} • Ketuk baris untuk mengedit atau menghapus sel secara langsung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(tableData.rows.indices.toList(), key = { idx -> tableData.rows[idx]["_rowid_"] ?: idx.toString() }) { idx ->
                val row = tableData.rows[idx]
                val rowId = row["_rowid_"] ?: (idx + 1).toString()
                Surface(
                    color = InsSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, InsIndigoSecondary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingRow = row }
                        .testTag("sqlite_row_$rowId")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = InsIndigoSecondary.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ROWID #$rowId",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InsCyanPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Baris",
                                    tint = InsCyanPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Sel", style = MaterialTheme.typography.labelSmall, color = InsCyanPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        row.entries.filter { it.key != "_rowid_" }.forEach { (colName, colVal) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = colName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = JetBrainsMonoFamily,
                                    modifier = Modifier.weight(0.4f)
                                )
                                Text(
                                    text = colVal,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontFamily = JetBrainsMonoFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Row Dialog
    if (editingRow != null && tableData != null) {
        val row = editingRow!!
        val rowId = row["_rowid_"] ?: "1"
        val fieldStates = remember(row) {
            mutableStateMapOf<String, String>().apply {
                row.forEach { (k, v) -> if (k != "_rowid_") put(k, v) }
            }
        }

        AlertDialog(
            onDismissRequest = { editingRow = null },
            containerColor = InsSurfaceDark,
            title = {
                Text("Edit Baris SQLite (rowid=$rowId)", style = MaterialTheme.typography.titleMedium, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fieldStates.keys.forEach { col ->
                        OutlinedTextField(
                            value = fieldStates[col] ?: "",
                            onValueChange = { fieldStates[col] = it },
                            label = { Text(col) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateRow(rowId, fieldStates.toMap())
                        editingRow = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary)
                ) {
                    Text("Simpan Perubahan")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDeleteRow(rowId)
                            editingRow = null
                        }
                    ) {
                        Text("Hapus Baris", color = InsCrimsonDanger)
                    }
                    TextButton(onClick = { editingRow = null }) {
                        Text("Batal")
                    }
                }
            }
        )
    }

    // Insert New Row Dialog
    if (isInsertingRow && tableData != null) {
        val newFields = remember(tableData.columns) {
            mutableStateMapOf<String, String>().apply {
                tableData.columns.filter { !it.isPrimaryKey }.forEach { put(it.name, "") }
            }
        }
        AlertDialog(
            onDismissRequest = { isInsertingRow = false },
            containerColor = InsSurfaceDark,
            title = {
                Text("Tambah Baris ke '${tableData.tableName}'", style = MaterialTheme.typography.titleMedium, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    newFields.keys.forEach { col ->
                        OutlinedTextField(
                            value = newFields[col] ?: "",
                            onValueChange = { newFields[col] = it },
                            label = { Text(col) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onInsertRow(newFields.toMap())
                        isInsertingRow = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary)
                ) {
                    Text("Insert Row")
                }
            },
            dismissButton = {
                TextButton(onClick = { isInsertingRow = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun XmlSharedPreferencesEditorPane(
    clone: CloneAppEntity,
    selectedXmlFilePath: String?,
    entries: List<XmlPrefEntry>,
    rawXmlContent: String,
    onUpsertEntry: (String, String, XmlPrefType) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onSaveRawXml: (String) -> Unit
) {
    var isRawMode by remember { mutableStateOf(false) }
    var rawEditorBuffer by remember(rawXmlContent) { mutableStateOf(rawXmlContent) }
    var editingEntry by remember { mutableStateOf<XmlPrefEntry?>(null) }
    var showAddKeyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("xml_prefs_editor_pane"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, InsVioletTertiary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SharedPreferences XML Editor",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = selectedXmlFilePath?.substringAfterLast('/') ?: "preferences.xml",
                                style = MaterialTheme.typography.labelMedium,
                                color = InsVioletTertiary,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !isRawMode,
                                onClick = { isRawMode = false },
                                label = { Text("Key-Value") }
                            )
                            FilterChip(
                                selected = isRawMode,
                                onClick = {
                                    rawEditorBuffer = rawXmlContent
                                    isRawMode = true
                                },
                                label = { Text("Raw XML") },
                                modifier = Modifier.testTag("xml_raw_mode_chip")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${entries.size} parameter aktif di dalam ruang virtual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isRawMode) {
                            Button(
                                onClick = { showAddKeyDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = InsVioletTertiary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("xml_add_key_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Key", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (isRawMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = InsSurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = rawEditorBuffer,
                            onValueChange = { rawEditorBuffer = it },
                            textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .testTag("raw_xml_textfield")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onSaveRawXml(rawEditorBuffer) },
                            colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_raw_xml_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simpan Raw XML Secara Real-Time")
                        }
                    }
                }
            }
        } else {
            items(entries, key = { it.key }) { entry ->
                Surface(
                    color = InsSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InsVioletTertiary.copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingEntry = entry }
                        .testTag("xml_entry_${entry.key}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = InsVioletTertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "<${entry.type.tagName}>",
                                style = MaterialTheme.typography.labelSmall,
                                color = InsVioletTertiary,
                                fontFamily = JetBrainsMonoFamily,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.key,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = entry.value,
                                style = MaterialTheme.typography.labelMedium,
                                color = InsCyanPrimary,
                                fontFamily = JetBrainsMonoFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { editingEntry = entry }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Key", tint = InsCyanPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteEntry(entry.key) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Key", tint = InsCrimsonDanger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // Edit or Add XML Entry Dialog
    if (editingEntry != null || showAddKeyDialog) {
        val initial = editingEntry
        var keyText by remember(initial) { mutableStateOf(initial?.key ?: "") }
        var valText by remember(initial) { mutableStateOf(initial?.value ?: "") }
        var selectedType by remember(initial) { mutableStateOf(initial?.type ?: XmlPrefType.STRING) }

        AlertDialog(
            onDismissRequest = {
                editingEntry = null
                showAddKeyDialog = false
            },
            containerColor = InsSurfaceDark,
            title = {
                Text(
                    text = if (initial != null) "Edit SharedPreferences Key" else "Tambah SharedPreferences Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = { Text("Attribute name (Key)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = valText,
                        onValueChange = { valText = it },
                        label = { Text("Nilai (Value)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        XmlPrefType.entries.forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t.tagName) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyText.isNotBlank()) {
                            onUpsertEntry(keyText.trim(), valText, selectedType)
                            editingEntry = null
                            showAddKeyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary)
                ) {
                    Text("Simpan ke XML")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingEntry = null
                        showAddKeyDialog = false
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
