package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.example.clone.CloneManager
import androidx.lifecycle.viewModelScope
import com.example.data.CloneAppEntity
import com.example.data.InstallableAppCandidate
import com.example.data.IpcHookLogEntity
import com.example.data.VirtualSpaceDatabase
import com.example.data.VirtualSpaceRepository
import com.example.virtual.BinderIpcInterceptor
import com.example.virtual.CloneStorageStats
import com.example.virtual.DeviceHardwarePreset
import com.example.virtual.IdentitySpoofer
import com.example.virtual.MockLocationPreset
import com.example.virtual.SandboxFileItem
import com.example.virtual.SandboxFileType
import com.example.virtual.SqliteTableData
import com.example.virtual.VirtualSandboxStorage
import com.example.virtual.VirtualSqliteManager
import com.example.virtual.VirtualXmlPrefsManager
import com.example.virtual.XmlPrefEntry
import com.example.virtual.XmlPrefType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MainNavTab {
    HOME_SPACE,
    INS_MANAGER,
    IDENTITY_SPOOF,
    ARCHITECTURE
}

enum class InsManagerSubTab {
    FILE_EXPLORER,
    SQLITE_VIEWER,
    XML_PREFS_EDITOR
}

class VirtualSpaceViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = VirtualSpaceDatabase.getInstance(context)
    private val repository = VirtualSpaceRepository(context, database.virtualSpaceDao())

    val clonedApps: StateFlow<List<CloneAppEntity>> = repository.allClonedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHookLogs: StateFlow<List<IpcHookLogEntity>> = repository.recentHookLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTab = MutableStateFlow(MainNavTab.HOME_SPACE)
    val currentTab: StateFlow<MainNavTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _openedFolderName = MutableStateFlow<String?>(null)
    val openedFolderName: StateFlow<String?> = _openedFolderName.asStateFlow()

    private val _editingIdentityClone = MutableStateFlow<CloneAppEntity?>(null)
    val editingIdentityClone: StateFlow<CloneAppEntity?> = _editingIdentityClone.asStateFlow()

    private val _showAddCloneSheet = MutableStateFlow(false)
    val showAddCloneSheet: StateFlow<Boolean> = _showAddCloneSheet.asStateFlow()

    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()

    private val _installableCandidates = MutableStateFlow<List<InstallableAppCandidate>>(emptyList())
    val installableCandidates: StateFlow<List<InstallableAppCandidate>> = _installableCandidates.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // INS Manager State
    private val _selectedManagerCloneId = MutableStateFlow<Int?>(null)
    val selectedManagerCloneId: StateFlow<Int?> = _selectedManagerCloneId.asStateFlow()

    private val _insSubTab = MutableStateFlow(InsManagerSubTab.FILE_EXPLORER)
    val insSubTab: StateFlow<InsManagerSubTab> = _insSubTab.asStateFlow()

    private val _currentRelativePath = MutableStateFlow("")
    val currentRelativePath: StateFlow<String> = _currentRelativePath.asStateFlow()

    private val _directoryFiles = MutableStateFlow<List<SandboxFileItem>>(emptyList())
    val directoryFiles: StateFlow<List<SandboxFileItem>> = _directoryFiles.asStateFlow()

    private val _storageStats = MutableStateFlow<CloneStorageStats?>(null)
    val storageStats: StateFlow<CloneStorageStats?> = _storageStats.asStateFlow()

    // SQLite Viewer State
    private val _selectedDbFilePath = MutableStateFlow<String?>(null)
    val selectedDbFilePath: StateFlow<String?> = _selectedDbFilePath.asStateFlow()

    private val _sqliteTables = MutableStateFlow<List<String>>(emptyList())
    val sqliteTables: StateFlow<List<String>> = _sqliteTables.asStateFlow()

    private val _selectedSqliteTable = MutableStateFlow<String?>(null)
    val selectedSqliteTable: StateFlow<String?> = _selectedSqliteTable.asStateFlow()

    private val _sqliteTableData = MutableStateFlow<SqliteTableData?>(null)
    val sqliteTableData: StateFlow<SqliteTableData?> = _sqliteTableData.asStateFlow()

    // XML SharedPreferences Editor State
    private val _selectedXmlFilePath = MutableStateFlow<String?>(null)
    val selectedXmlFilePath: StateFlow<String?> = _selectedXmlFilePath.asStateFlow()

    private val _xmlPrefEntries = MutableStateFlow<List<XmlPrefEntry>>(emptyList())
    val xmlPrefEntries: StateFlow<List<XmlPrefEntry>> = _xmlPrefEntries.asStateFlow()

    private val _rawXmlContent = MutableStateFlow("")
    val rawXmlContent: StateFlow<String> = _rawXmlContent.asStateFlow()

    // Text/JSON File Preview State
    private val _previewTextFile = MutableStateFlow<Pair<String, String>?>(null)
    val previewTextFile: StateFlow<Pair<String, String>?> = _previewTextFile.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialSeed()
            _installableCandidates.value = repository.discoverInstallableApps()
        }
        viewModelScope.launch {
            clonedApps.collect { list ->
                if (list.isNotEmpty()) {
                    val currentId = _selectedManagerCloneId.value
                    val activeClone = list.find { it.id == currentId } ?: list.first()
                    if (currentId != activeClone.id) {
                        _selectedManagerCloneId.value = activeClone.id
                        refreshInsManagerForClone(activeClone, "")
                    } else {
                        refreshInsManagerForClone(activeClone, _currentRelativePath.value)
                    }
                    // Keep editingIdentityClone synced if open
                    _editingIdentityClone.value?.let { editing ->
                        list.find { it.id == editing.id }?.let { _editingIdentityClone.value = it }
                    }
                }
            }
        }
    }

    fun selectTab(tab: MainNavTab) {
        _currentTab.value = tab
    }

    fun setInsSubTab(subTab: InsManagerSubTab) {
        _insSubTab.value = subTab
    }

    fun toggleSearchBar() {
        _isSearchActive.value = !_isSearchActive.value
        if (!_isSearchActive.value) _searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openFolder(folderName: String?) {
        _openedFolderName.value = folderName
    }

    fun openIdentityEditor(clone: CloneAppEntity?) {
        _editingIdentityClone.value = clone
    }

    fun setShowAddCloneSheet(show: Boolean) {
        _showAddCloneSheet.value = show
        if (show) {
            viewModelScope.launch {
                _installableCandidates.value = repository.discoverInstallableApps()
            }
        }
    }

    fun setShowProfileDialog(show: Boolean) {
        _showProfileDialog.value = show
    }

    fun dismissBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun postBannerMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun cleanVirtualMemory() {
        viewModelScope.launch {
            val freedMb = repository.cleanVirtualMemory()
            _statusBannerMessage.value = "Pembersih Memori Virtual: $freedMb MB RAM dibebaskan & proses idle dihentikan."
        }
    }

    fun addNewClone(
        candidate: InstallableAppCandidate,
        folderName: String?,
        enableMockGps: Boolean
    ) {
        viewModelScope.launch {
            if (!CloneManager.canInstallPackages(context)) {
                _showAddCloneSheet.value = false
                _statusBannerMessage.value = "Izinkan \"Install unknown apps\" untuk INS Virtual Space, lalu tambahkan aplikasi lagi."
                runCatching {
                    context.startActivity(
                        CloneManager.unknownSourcesSettingsIntent(context)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return@launch
            }
            val created = repository.createNewClone(candidate, folderName, enableMockGps)
            _showAddCloneSheet.value = false
            _selectedManagerCloneId.value = created.id
            refreshInsManagerForClone(created, "")
            _statusBannerMessage.value = if (created.installedClonePackage.isNotBlank()) {
                "Clone ${created.recentsTaskTitle} terpasang sebagai ${created.installedClonePackage}"
            } else {
                "Clone ${created.recentsTaskTitle} dibuat, tetapi paket clone belum terpasang. Coba tambah ulang setelah izin diaktifkan."
            }
        }
    }

    fun importApkUri(
        uri: android.net.Uri,
        folderName: String?,
        enableMockGps: Boolean
    ) {
        viewModelScope.launch {
            if (!CloneManager.canInstallPackages(context)) {
                _showAddCloneSheet.value = false
                _statusBannerMessage.value = "Izinkan \"Install unknown apps\" untuk INS Virtual Space, lalu impor APK lagi."
                runCatching {
                    context.startActivity(
                        CloneManager.unknownSourcesSettingsIntent(context)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return@launch
            }
            val res = repository.importApkFromUriAndClone(uri, folderName, enableMockGps)
            res.onSuccess { created ->
                _showAddCloneSheet.value = false
                _selectedManagerCloneId.value = created.id
                refreshInsManagerForClone(created, "")
                _statusBannerMessage.value = if (created.installedClonePackage.isNotBlank()) {
                    "APK ${created.recentsTaskTitle} diimpor & terpasang sebagai ${created.installedClonePackage}"
                } else {
                    "APK ${created.recentsTaskTitle} diimpor ke sandbox (paket clone belum terpasang)."
                }
            }.onFailure { err ->
                _statusBannerMessage.value = "Gagal impor APK: ${err.message}"
            }
        }
    }

    fun deleteClone(clone: CloneAppEntity) {
        viewModelScope.launch {
            repository.deleteClone(clone)
            if (_editingIdentityClone.value?.id == clone.id) {
                _editingIdentityClone.value = null
            }
            _statusBannerMessage.value = "Sandbox & data klon ${clone.recentsTaskTitle} telah dihapus."
        }
    }

    fun clearCloneData(clone: CloneAppEntity, randomizeDeviceId: Boolean = true) {
        viewModelScope.launch {
            val (updated, freed) = repository.clearCloneDataAndResetIdentity(clone, randomizeDeviceId)
            _currentRelativePath.value = ""
            _selectedDbFilePath.value = null
            _selectedXmlFilePath.value = null
            refreshInsManagerForClone(updated, "")
            val idNote = if (randomizeDeviceId) " + Device ID baru (${updated.androidId.take(8)})" else ""
            _statusBannerMessage.value = "Data clone ${updated.recentsTaskTitle} berhasil dihapus bersih (${SandboxFileItem.formatBytes(freed)} di-reset$idNote). Data aplikasi asli di HP tetap aman!"
        }
    }

    fun clearCloneCacheFor(clone: CloneAppEntity) {
        viewModelScope.launch {
            val freed = repository.clearCloneCacheOnly(clone)
            refreshInsManagerForClone(clone, _currentRelativePath.value)
            _statusBannerMessage.value = "Cache clone ${clone.recentsTaskTitle} dibersihkan (${SandboxFileItem.formatBytes(freed)} dibebaskan)."
        }
    }

    fun toggleCloneFolder(clone: CloneAppEntity) {
        viewModelScope.launch {
            val newFolder = if (clone.folderName == "Alat") null else "Alat"
            val updated = clone.copy(folderName = newFolder)
            repository.updateCloneIdentity(updated)
            _statusBannerMessage.value = if (newFolder != null) {
                "${clone.appName} dipindahkan ke Folder '$newFolder'"
            } else {
                "${clone.appName} dikeluarkan ke Beranda Ruang Virtual"
            }
        }
    }

    fun randomizeCloneIdentity(clone: CloneAppEntity, preset: DeviceHardwarePreset? = null) {
        viewModelScope.launch {
            val randomized = if (preset != null) {
                IdentitySpoofer.randomizeIdentity(clone, preset)
            } else {
                IdentitySpoofer.randomizeIdentity(clone)
            }
            repository.updateCloneIdentity(randomized)
            _editingIdentityClone.value = randomized
            triggerHookSweepForClone(randomized)
            _statusBannerMessage.value = "Identitas baru diterapkan untuk ${randomized.appName}: ${randomized.buildManufacturer} ${randomized.buildModel} (IMEI: ${randomized.imei.take(8)}...)"
        }
    }

    fun saveCustomCloneIdentity(updated: CloneAppEntity) {
        viewModelScope.launch {
            repository.updateCloneIdentity(updated)
            _editingIdentityClone.value = updated
            triggerHookSweepForClone(updated)
            _statusBannerMessage.value = "Konfigurasi Device ID & Mock GPS disimpan untuk ${updated.recentsTaskTitle}"
        }
    }

    fun applyMockLocationPreset(clone: CloneAppEntity, preset: MockLocationPreset) {
        viewModelScope.launch {
            val updated = clone.copy(
                mockLocationEnabled = true,
                mockLatitude = preset.latitude,
                mockLongitude = preset.longitude,
                mockAccuracy = preset.accuracy,
                mockLocationName = preset.label
            )
            repository.updateCloneIdentity(updated)
            _editingIdentityClone.value = updated
            triggerHookSweepForClone(updated)
            _statusBannerMessage.value = "Mock Location terisolasi: ${preset.label} (${preset.latitude}, ${preset.longitude})"
        }
    }

    fun triggerHookSweepForClone(clone: CloneAppEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val interceptor = BinderIpcInterceptor(context, clone) {}
            val sweep = interceptor.executeDiagnosticSweep()
            sweep.interceptedLogs.forEach { repository.recordHookLog(it) }
        }
    }

    fun clearAllHookLogs() {
        viewModelScope.launch {
            repository.clearHookLogs()
            _statusBannerMessage.value = "Log intersepsi Binder IPC dibersihkan."
        }
    }

    fun openCloneInInsManager(clone: CloneAppEntity) {
        _selectedManagerCloneId.value = clone.id
        _currentTab.value = MainNavTab.INS_MANAGER
        _insSubTab.value = InsManagerSubTab.FILE_EXPLORER
        refreshInsManagerForClone(clone, "")
    }

    fun selectManagerClone(clone: CloneAppEntity) {
        _selectedManagerCloneId.value = clone.id
        refreshInsManagerForClone(clone, "")
    }

    fun navigateSandboxDirectory(relativePath: String) {
        val clone = getActiveManagerClone() ?: return
        _currentRelativePath.value = relativePath
        refreshInsManagerForClone(clone, relativePath)
    }

    fun navigateUpSandboxDirectory() {
        val current = _currentRelativePath.value
        if (current.isBlank()) return
        val parent = current.substringBeforeLast('/', "")
        navigateSandboxDirectory(parent)
    }

    fun openSandboxFileItem(item: SandboxFileItem) {
        val clone = getActiveManagerClone() ?: return
        val root = VirtualSandboxStorage.getSandboxRoot(context, clone)
        val file = File(item.absolutePath)
        when {
            item.isDirectory -> {
                val rel = file.relativeTo(root).path
                navigateSandboxDirectory(rel)
            }
            item.fileType == SandboxFileType.SQLITE_DB -> {
                loadSqliteDatabaseFile(file.absolutePath)
                _insSubTab.value = InsManagerSubTab.SQLITE_VIEWER
            }
            item.fileType == SandboxFileType.SHARED_PREFS_XML -> {
                loadXmlPreferencesFile(file.absolutePath)
                _insSubTab.value = InsManagerSubTab.XML_PREFS_EDITOR
            }
            else -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val content = runCatching {
                        file.readText().take(8000)
                    }.getOrDefault("Binary file (${item.formattedSize})")
                    _previewTextFile.value = item.name to content
                }
            }
        }
    }

    fun closePreviewTextFile() {
        _previewTextFile.value = null
    }

    fun refreshInsManagerForClone(clone: CloneAppEntity, relativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = VirtualSandboxStorage.listDirectory(context, clone, relativePath)
            val stats = VirtualSandboxStorage.getStorageStats(context, clone)
            _directoryFiles.value = files
            _storageStats.value = stats

            val root = VirtualSandboxStorage.getSandboxRoot(context, clone)
            val dbFiles = File(root, "databases").listFiles { f -> f.isFile && f.name.endsWith(".db") }?.toList().orEmpty()
            val xmlFiles = File(root, "shared_prefs").listFiles { f -> f.isFile && f.name.endsWith(".xml") }?.toList().orEmpty()

            if (_selectedDbFilePath.value == null || !File(_selectedDbFilePath.value!!).exists() || ! _selectedDbFilePath.value!!.startsWith(root.absolutePath)) {
                dbFiles.firstOrNull()?.let { loadSqliteDatabaseFile(it.absolutePath) }
            }
            if (_selectedXmlFilePath.value == null || !File(_selectedXmlFilePath.value!!).exists() || !_selectedXmlFilePath.value!!.startsWith(root.absolutePath)) {
                xmlFiles.firstOrNull()?.let { loadXmlPreferencesFile(it.absolutePath) }
            }
        }
    }

    fun loadSqliteDatabaseFile(absolutePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbFile = File(absolutePath)
            if (!dbFile.exists()) return@launch
            _selectedDbFilePath.value = absolutePath
            val tables = VirtualSqliteManager.listTables(dbFile)
            _sqliteTables.value = tables
            val firstTable = tables.firstOrNull()
            _selectedSqliteTable.value = firstTable
            if (firstTable != null) {
                _sqliteTableData.value = VirtualSqliteManager.readTable(dbFile, firstTable)
            } else {
                _sqliteTableData.value = null
            }
        }
    }

    fun selectSqliteTable(tableName: String) {
        val dbPath = _selectedDbFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _selectedSqliteTable.value = tableName
            _sqliteTableData.value = VirtualSqliteManager.readTable(File(dbPath), tableName)
        }
    }

    fun updateSqliteRow(rowId: String, updatedValues: Map<String, String>) {
        val dbPath = _selectedDbFilePath.value ?: return
        val table = _selectedSqliteTable.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = VirtualSqliteManager.updateRow(File(dbPath), table, rowId, updatedValues)
            _sqliteTableData.value = VirtualSqliteManager.readTable(File(dbPath), table)
            _statusBannerMessage.value = res.getOrElse { "Gagal update baris: ${it.message}" }
        }
    }

    fun insertSqliteRow(values: Map<String, String>) {
        val dbPath = _selectedDbFilePath.value ?: return
        val table = _selectedSqliteTable.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = VirtualSqliteManager.insertRow(File(dbPath), table, values)
            _sqliteTableData.value = VirtualSqliteManager.readTable(File(dbPath), table)
            _statusBannerMessage.value = res.getOrElse { "Gagal menambah baris: ${it.message}" }
        }
    }

    fun deleteSqliteRow(rowId: String) {
        val dbPath = _selectedDbFilePath.value ?: return
        val table = _selectedSqliteTable.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val res = VirtualSqliteManager.deleteRow(File(dbPath), table, rowId)
            _sqliteTableData.value = VirtualSqliteManager.readTable(File(dbPath), table)
            _statusBannerMessage.value = res.getOrElse { "Gagal menghapus baris: ${it.message}" }
        }
    }

    fun executeCustomSql(sql: String) {
        val dbPath = _selectedDbFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dbFile = File(dbPath)
            val result = VirtualSqliteManager.executeCustomSql(dbFile, sql)
            result.onSuccess { data ->
                val tables = VirtualSqliteManager.listTables(dbFile)
                _sqliteTables.value = tables
                if (data.columns.isNotEmpty()) {
                    _sqliteTableData.value = data
                } else {
                    val activeTable = _selectedSqliteTable.value ?: tables.firstOrNull()
                    if (activeTable != null) {
                        _sqliteTableData.value = VirtualSqliteManager.readTable(dbFile, activeTable)
                    }
                }
                _statusBannerMessage.value = data.statusMessage
            }.onFailure { err ->
                _statusBannerMessage.value = "SQL Error: ${err.message}"
            }
        }
    }

    fun loadXmlPreferencesFile(absolutePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val xmlFile = File(absolutePath)
            if (!xmlFile.exists()) return@launch
            _selectedXmlFilePath.value = absolutePath
            val entries = VirtualXmlPrefsManager.parseXmlFile(xmlFile)
            _xmlPrefEntries.value = entries
            _rawXmlContent.value = xmlFile.readText()
        }
    }

    fun upsertXmlPreferenceEntry(key: String, value: String, type: XmlPrefType) {
        val xmlPath = _selectedXmlFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val xmlFile = File(xmlPath)
            val current = _xmlPrefEntries.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.key == key }
            val newEntry = XmlPrefEntry(key, value, type)
            if (existingIdx >= 0) {
                current[existingIdx] = newEntry
            } else {
                current.add(newEntry)
            }
            VirtualXmlPrefsManager.writeEntriesToXml(xmlFile, current)
            _xmlPrefEntries.value = current
            _rawXmlContent.value = xmlFile.readText()
            _statusBannerMessage.value = "SharedPreferences '$key' disimpan secara real-time."
        }
    }

    fun deleteXmlPreferenceEntry(key: String) {
        val xmlPath = _selectedXmlFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val xmlFile = File(xmlPath)
            val updated = _xmlPrefEntries.value.filterNot { it.key == key }
            VirtualXmlPrefsManager.writeEntriesToXml(xmlFile, updated)
            _xmlPrefEntries.value = updated
            _rawXmlContent.value = xmlFile.readText()
            _statusBannerMessage.value = "Key '$key' dihapus dari XML."
        }
    }

    fun saveRawXmlContent(rawXml: String) {
        val xmlPath = _selectedXmlFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val xmlFile = File(xmlPath)
            val res = VirtualXmlPrefsManager.saveRawXml(xmlFile, rawXml)
            res.onSuccess { entries ->
                _xmlPrefEntries.value = entries
                _rawXmlContent.value = rawXml
                _statusBannerMessage.value = "Raw XML SharedPreferences berhasil disimpan (${entries.size} keys)."
            }.onFailure { err ->
                _statusBannerMessage.value = "Format XML tidak valid: ${err.message}"
            }
        }
    }

    fun clearSelectedCloneCache() {
        val clone = getActiveManagerClone() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val freed = VirtualSandboxStorage.clearCloneCache(context, clone)
            refreshInsManagerForClone(clone, _currentRelativePath.value)
            _statusBannerMessage.value = "Cache ${clone.recentsTaskTitle} dibersihkan (${SandboxFileItem.formatBytes(freed)} dibebaskan)."
        }
    }

    fun resetSelectedCloneData() {
        val clone = getActiveManagerClone() ?: return
        clearCloneData(clone, randomizeDeviceId = true)
    }

    fun getActiveManagerClone(): CloneAppEntity? {
        val list = clonedApps.value
        val id = _selectedManagerCloneId.value
        return list.find { it.id == id } ?: list.firstOrNull()
    }

    suspend fun prepareCloneLaunch(clone: CloneAppEntity): CloneAppEntity = withContext(Dispatchers.IO) {
        repository.markCloneLaunched(clone)
    }
}
