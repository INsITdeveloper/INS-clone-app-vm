package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.IpcHookLogEntity
import com.example.ui.theme.InsAmberWarning
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsIndigoSecondary
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.InsSurfaceElevated
import com.example.ui.theme.InsVioletTertiary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.virtual.DeviceHardwarePreset
import com.example.virtual.IdentitySpoofer
import com.example.virtual.MockLocationPreset

@Composable
fun IdentityGuardOverviewScreen(
    clonedApps: List<CloneAppEntity>,
    hookLogs: List<IpcHookLogEntity>,
    onEditCloneIdentity: (CloneAppEntity) -> Unit,
    onRandomizeCloneIdentity: (CloneAppEntity) -> Unit,
    onTriggerSweep: (CloneAppEntity) -> Unit,
    onClearLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("identity_guard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Per-Clone Identity Spoofer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Isolasi ANDROID_ID, IMEI, Build Props, MAC Address, GAID & Fake GPS per instance",
                    style = MaterialTheme.typography.bodySmall,
                    color = InsCyanPrimary
                )
            }
        }

        // Per-Clone Identity Cards
        items(clonedApps, key = { it.id }) { clone ->
            Card(
                colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(clone.iconColorHex).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("identity_clone_card_${clone.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(clone.iconColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = clone.appName.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = clone.recentsTaskTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = clone.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onRandomizeCloneIdentity(clone) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("randomize_identity_btn_${clone.id}")
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(15.dp), tint = InsCyanPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Acak", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { onEditCloneIdentity(clone) },
                                colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("edit_identity_btn_${clone.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Manual", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = InsSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IdentityKeyValLine("Settings.Secure.ANDROID_ID", clone.androidId, InsCyanPrimary)
                            IdentityKeyValLine("TelephonyManager.getImei()", clone.imei, Color.White)
                            IdentityKeyValLine("Build.MANUFACTURER / MODEL", "${clone.buildManufacturer} ${clone.buildModel} (${clone.buildBrand})", InsVioletTertiary)
                            IdentityKeyValLine("Build.SERIAL", clone.buildSerial, Color.White)
                            IdentityKeyValLine("WifiInfo.getMacAddress()", "${clone.wifiMac} (${clone.wifiSsid})", InsEmeraldActive)
                            IdentityKeyValLine("Advertising ID (GAID)", clone.advertisingId, Color.White)
                            IdentityKeyValLine(
                                "Mock Location (Fake GPS)",
                                if (clone.mockLocationEnabled) "AKTIF • ${clone.mockLocationName} (${clone.mockLatitude}, ${clone.mockLongitude})"
                                else "NONAKTIF (Sensor GPS Asli)",
                                if (clone.mockLocationEnabled) InsEmeraldActive else InsAmberWarning
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Binder IPC Interception Log Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Binder IPC Interception Log",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "${hookLogs.size} panggilan API diintersepsi oleh Dynamic Proxy",
                        style = MaterialTheme.typography.labelSmall,
                        color = InsEmeraldActive
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (clonedApps.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onTriggerSweep(clonedApps.first()) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulasi IPC", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(onClick = onClearLogs) {
                        Text("Bersihkan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        items(hookLogs.take(25), key = { it.id }) { log ->
            Surface(
                color = InsSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${log.appName}(Clone App) • ${log.serviceName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InsCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = log.methodName,
                            style = MaterialTheme.typography.labelSmall,
                            color = InsVioletTertiary,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Spoofed Return → ${log.spoofedReturnValue}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InsEmeraldActive,
                        fontFamily = JetBrainsMonoFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityKeyValLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = valueColor,
            fontFamily = JetBrainsMonoFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerCloneIdentityConfigSheet(
    clone: CloneAppEntity,
    onDismiss: () -> Unit,
    onRandomizeAll: (DeviceHardwarePreset?) -> Unit,
    onApplyLocationPreset: (MockLocationPreset) -> Unit,
    onSaveManualIdentity: (CloneAppEntity) -> Unit
) {
    var androidId by remember(clone) { mutableStateOf(clone.androidId) }
    var imei by remember(clone) { mutableStateOf(clone.imei) }
    var imsi by remember(clone) { mutableStateOf(clone.imsi) }
    var serial by remember(clone) { mutableStateOf(clone.buildSerial) }
    var model by remember(clone) { mutableStateOf(clone.buildModel) }
    var manufacturer by remember(clone) { mutableStateOf(clone.buildManufacturer) }
    var brand by remember(clone) { mutableStateOf(clone.buildBrand) }
    var wifiMac by remember(clone) { mutableStateOf(clone.wifiMac) }
    var wifiSsid by remember(clone) { mutableStateOf(clone.wifiSsid) }
    var gaid by remember(clone) { mutableStateOf(clone.advertisingId) }

    var mockGpsEnabled by remember(clone) { mutableStateOf(clone.mockLocationEnabled) }
    var mockLat by remember(clone) { mutableStateOf(clone.mockLatitude.toString()) }
    var mockLng by remember(clone) { mutableStateOf(clone.mockLongitude.toString()) }
    var mockLabel by remember(clone) { mutableStateOf(clone.mockLocationName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = InsSurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Isolasi Identitas: ${clone.recentsTaskTitle}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = clone.canonicalVirtualDataPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = InsCyanPrimary,
                        fontFamily = JetBrainsMonoFamily
                    )
                }
                Button(
                    onClick = { onRandomizeAll(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = InsIndigoSecondary),
                    modifier = Modifier.testTag("sheet_randomize_all_button")
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Acak Semua")
                }
            }

            // Hardware Device Presets
            Text(
                text = "Preset Perangkat (1-Klik Spoof Model & TAC IMEI):",
                style = MaterialTheme.typography.labelLarge,
                color = InsCyanPrimary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IdentitySpoofer.hardwarePresets) { preset ->
                    FilterChip(
                        selected = model == preset.model,
                        onClick = { onRandomizeAll(preset) },
                        label = { Text(preset.title) },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                }
            }

            HorizontalDivider(color = InsSurfaceElevated)

            // Manual Device ID & Hardware Inputs
            Text(
                text = "Pengaturan Manual Device ID / IMEI / Build / MAC:",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )

            OutlinedTextField(
                value = androidId,
                onValueChange = { androidId = it },
                label = { Text("Settings.Secure.ANDROID_ID (16-hex)") },
                trailingIcon = {
                    IconButton(onClick = { androidId = IdentitySpoofer.generateAndroidId() }) {
                        Icon(Icons.Default.Casino, contentDescription = "Acak Android ID", tint = InsCyanPrimary)
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_android_id")
            )

            OutlinedTextField(
                value = imei,
                onValueChange = { imei = it },
                label = { Text("TelephonyManager.getImei() / getDeviceId() (15 digit Luhn)") },
                trailingIcon = {
                    IconButton(onClick = { imei = IdentitySpoofer.generateLuhnValidImei() }) {
                        Icon(Icons.Default.Casino, contentDescription = "Acak IMEI", tint = InsCyanPrimary)
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_imei")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Build.MODEL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Build.MANUFACTURER") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Build.BRAND") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Build.SERIAL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wifiMac,
                    onValueChange = { wifiMac = it },
                    label = { Text("WifiInfo.getMacAddress()") },
                    trailingIcon = {
                        IconButton(onClick = { wifiMac = IdentitySpoofer.generateWifiMacAddress() }) {
                            Icon(Icons.Default.Casino, contentDescription = "Acak MAC", tint = InsCyanPrimary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text("Wi-Fi SSID") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            OutlinedTextField(
                value = gaid,
                onValueChange = { gaid = it },
                label = { Text("Advertising ID (GAID UUID)") },
                trailingIcon = {
                    IconButton(onClick = { gaid = IdentitySpoofer.generateAdvertisingId() }) {
                        Icon(Icons.Default.Casino, contentDescription = "Acak GAID", tint = InsCyanPrimary)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = InsSurfaceElevated)

            // Isolated Mock Location (Fake GPS) Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mock Location (Fake GPS) Terisolasi",
                        style = MaterialTheme.typography.titleSmall,
                        color = InsEmeraldActive
                    )
                    Text(
                        text = "Mengintersepsi ILocationManager hanya untuk klon ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = mockGpsEnabled,
                    onCheckedChange = { mockGpsEnabled = it },
                    modifier = Modifier.testTag("switch_mock_gps")
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IdentitySpoofer.locationPresets) { locPreset ->
                    FilterChip(
                        selected = mockLabel == locPreset.label && mockGpsEnabled,
                        onClick = {
                            mockGpsEnabled = true
                            mockLat = locPreset.latitude.toString()
                            mockLng = locPreset.longitude.toString()
                            mockLabel = locPreset.label
                            onApplyLocationPreset(locPreset)
                        },
                        label = { Text(locPreset.label) },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mockLat,
                    onValueChange = { mockLat = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mockLng,
                    onValueChange = { mockLng = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val parsedLat = mockLat.toDoubleOrNull() ?: clone.mockLatitude
                    val parsedLng = mockLng.toDoubleOrNull() ?: clone.mockLongitude
                    onSaveManualIdentity(
                        clone.copy(
                            androidId = androidId.trim(),
                            imei = imei.trim(),
                            imsi = imsi.trim(),
                            buildSerial = serial.trim(),
                            buildModel = model.trim(),
                            buildManufacturer = manufacturer.trim(),
                            buildBrand = brand.trim(),
                            wifiMac = wifiMac.trim(),
                            wifiSsid = wifiSsid.trim(),
                            advertisingId = gaid.trim(),
                            mockLocationEnabled = mockGpsEnabled,
                            mockLatitude = parsedLat,
                            mockLongitude = parsedLng,
                            mockLocationName = mockLabel
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_identity_config_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Identitas & Terapkan ke Sandbox")
            }
        }
    }
}

@Composable
fun SystemArchitectureScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("architecture_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Arsitektur Sistem INS Virtual Space",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Package Spec: com.ins.virtualspace • Multi-Process & User-Space Sandbox Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = InsCyanPrimary
                )
            }
        }

        // Module Blueprint Card
        item {
            ArchitectureModuleCard(
                title = "1. Virtual Container & Process Isolation Engine",
                badge = "virtual.engine",
                color = InsCyanPrimary,
                points = listOf(
                    "VirtualContextWrapper: Mengalihkan getDataDir(), getFilesDir(), getCacheDir(), getDatabasePath(), dan getSharedPreferences() ke /data/user/0/com.ins.virtualspace/virtual/user/<slot>/<package_name>/.",
                    "VirtualClassLoaderHook: Memuat bytecode APK menggunakan dalvik.system.DexClassLoader terisolasi dengan direktori code_cache dan native lib khusus per instance klon.",
                    "Integrasi Recents / Task Manager: VirtualContainerActivity menggunakan FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK dan ActivityManager.TaskDescription(\"[NamaAplikasi](Clone App)\") agar setiap klon tampil terpisah di menu Recents Android."
                )
            )
        }

        item {
            ArchitectureModuleCard(
                title = "2. Binder IPC Dynamic Proxy & IdentitySpoofer",
                badge = "virtual.spoof",
                color = InsVioletTertiary,
                points = listOf(
                    "Java Dynamic Proxy (Proxy.newProxyInstance + InvocationHandler): Mengintersepsi panggilan IPC ke ITelephony.Stub, IContentProvider(Settings.Secure), IWifiManager.Stub, IAdvertisingIdService, dan ILocationManager.Stub.",
                    "Per-Clone Spoofing Matrix: Setiap instance memiliki ANDROID_ID (16-hex), IMEI (15-digit Luhn-valid), IMSI, Build.SERIAL/MODEL/MANUFACTURER/BRAND, Wi-Fi MAC Address, dan GAID yang unik.",
                    "Isolated Mock Location (Fake GPS): Menyuntikkan objek android.location.Location(\"gps\") palsu khusus untuk klon tanpa memengaruhi GPS aplikasi utama di luar ruang virtual."
                )
            )
        }

        item {
            ArchitectureModuleCard(
                title = "3. INS Manager (Virtual Data, SQLite & XML Explorer)",
                badge = "virtual.storage",
                color = InsEmeraldActive,
                points = listOf(
                    "VirtualSandboxStorage: Menjelajahi struktur direktori terisolasi (/databases, /shared_prefs, /files, /cache, /code_cache) beserta ukuran dan izin UNIX.",
                    "VirtualSqliteManager: Membuka file .db asli menggunakan SQLiteDatabase.openDatabase(OPEN_READWRITE), membaca skema PRAGMA table_info, mengedit/menghapus baris, dan mengeksekusi query SQL kustom.",
                    "VirtualXmlPrefsManager: Mem-parsing dan menulis ulang file XML SharedPreferences (<map>...</map>) secara real-time baik melalui mode Visual Key-Value maupun Raw XML."
                )
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, InsIndigoSecondary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Struktur Modul Proyek (Clean Architecture + MVVM)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val tree = """
                        com.ins.virtualspace (Host Container Engine)
                        ├── data/
                        │   ├── CloneAppEntity.kt        (Room Entity + Identity Profile)
                        │   ├── IpcHookLogEntity.kt      (Binder IPC Audit Log Entity)
                        │   ├── VirtualSpaceDao.kt       (Reactive Flow Queries)
                        │   ├── VirtualSpaceDatabase.kt  (Room SQLite Holder)
                        │   └── VirtualSpaceRepository.kt(APK Scanner & Sandbox Seeder)
                        ├── virtual/
                        │   ├── VirtualContextWrapper.kt (Filesystem & DB Redirection)
                        │   ├── VirtualClassLoaderHook.kt(Isolated DexClassLoader)
                        │   ├── BinderIpcInterceptor.kt  (Dynamic Proxy Hook Engine)
                        │   ├── IdentitySpoofer.kt       (Luhn IMEI, MAC, GAID, Fake GPS)
                        │   ├── VirtualSandboxStorage.kt (Sandbox Tree & Cache Cleaner)
                        │   ├── VirtualSqliteManager.kt  (Interactive SQLite CRUD & SQL)
                        │   └── VirtualXmlPrefsManager.kt(Real-time SharedPreferences XML)
                        ├── ui/
                        │   ├── HomeDualSpaceScreen.kt   (Clone App Grid & Folder 'Alat')
                        │   ├── InsManagerScreen.kt      (File Explorer, SQLite & XML UI)
                        │   ├── IdentityAndArchitectureScreens.kt
                        │   └── VirtualSpaceViewModel.kt (StateFlow & Coroutine Orchestrator)
                        └── VirtualContainerActivity.kt  (Recents '[App](Clone App)' Stub)
                    """.trimIndent()
                    Surface(
                        color = InsSurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tree,
                            style = MaterialTheme.typography.labelSmall,
                            color = InsCyanPrimary,
                            fontFamily = JetBrainsMonoFamily,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchitectureModuleCard(
    title: String,
    badge: String,
    color: Color,
    points: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = color.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontFamily = JetBrainsMonoFamily,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            points.forEach { pt ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun VirtualProfileSecurityDialog(
    totalClones: Int,
    runningClones: Int,
    totalHookEvents: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InsSurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = InsCyanPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Profil Ruang Virtual INS", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Package: com.ins.virtualspace", style = MaterialTheme.typography.labelSmall, color = InsCyanPrimary)
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
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IdentityKeyValLine("Mode Eksekusi", "Root-Free User-Space Sandbox", InsEmeraldActive)
                        IdentityKeyValLine("Jalur Data Virtual", "/data/user/0/com.ins.virtualspace/virtual/user/0/", InsCyanPrimary)
                        IdentityKeyValLine("Format Recents", "[NamaAplikasi](Clone App)", Color.White)
                        IdentityKeyValLine("Total Klon Terdaftar", "$totalClones Instance", Color.White)
                        IdentityKeyValLine("Proses Virtual Aktif", "$runningClones Running", InsEmeraldActive)
                        IdentityKeyValLine("Intersepsi Binder IPC", "$totalHookEvents Panggilan", InsVioletTertiary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary)
            ) {
                Text("Tutup")
            }
        }
    )
}
