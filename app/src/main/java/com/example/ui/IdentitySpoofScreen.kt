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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
fun IdentitySpoofScreen(
    clonedApps: List<CloneAppEntity>,
    activeClone: CloneAppEntity?,
    hookLogs: List<IpcHookLogEntity>,
    onSelectClone: (CloneAppEntity) -> Unit,
    onRandomizeIdentity: (CloneAppEntity, DeviceHardwarePreset?) -> Unit,
    onSaveCustomIdentity: (CloneAppEntity) -> Unit,
    onApplyLocationPreset: (CloneAppEntity, MockLocationPreset) -> Unit,
    onTriggerSweep: (CloneAppEntity) -> Unit,
    onClearLogs: () -> Unit
) {
    val selected = activeClone ?: clonedApps.firstOrNull()
    if (selected == null) return

    var androidIdInput by remember(selected) { mutableStateOf(selected.androidId) }
    var imeiInput by remember(selected) { mutableStateOf(selected.imei) }
    var serialInput by remember(selected) { mutableStateOf(selected.buildSerial) }
    var modelInput by remember(selected) { mutableStateOf(selected.buildModel) }
    var manufacturerInput by remember(selected) { mutableStateOf(selected.buildManufacturer) }
    var brandInput by remember(selected) { mutableStateOf(selected.buildBrand) }
    var wifiMacInput by remember(selected) { mutableStateOf(selected.wifiMac) }
    var gaidInput by remember(selected) { mutableStateOf(selected.advertisingId) }

    var mockGpsEnabled by remember(selected) { mutableStateOf(selected.mockLocationEnabled) }
    var mockLatInput by remember(selected) { mutableStateOf(selected.mockLatitude.toString()) }
    var mockLngInput by remember(selected) { mutableStateOf(selected.mockLongitude.toString()) }
    var mockLabelInput by remember(selected) { mutableStateOf(selected.mockLocationName) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + Clone Selector
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
                            text = "Per-Clone Identity Spoofer",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            modifier = Modifier.testTag("identity_spoof_header")
                        )
                        Text(
                            text = "Isolasi ANDROID_ID, IMEI, Build Props, MAC, GAID & Fake GPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = InsCyanPrimary
                        )
                    }
                    Button(
                        onClick = { onRandomizeIdentity(selected, null) },
                        colors = ButtonDefaults.buttonColors(containerColor = InsCyanPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("randomize_all_identity_button")
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Acak Semua", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(clonedApps, key = { it.id }) { app ->
                        FilterChip(
                            selected = app.id == selected.id,
                            onClick = { onSelectClone(app) },
                            label = { Text(app.recentsTaskTitle) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(app.iconColorHex))
                                )
                            },
                            modifier = Modifier.testTag("spoof_select_clone_${app.id}")
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. One-Click Hardware Model Presets
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, InsCyanPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = InsCyanPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Preset Profil Perangkat (One-Click Spoof)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IdentitySpoofer.hardwarePresets.forEach { preset ->
                                FilterChip(
                                    selected = modelInput == preset.model,
                                    onClick = { onRandomizeIdentity(selected, preset) },
                                    label = { Text("${preset.manufacturer} ${preset.model}") },
                                    modifier = Modifier.testTag("preset_hw_${preset.model}")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Manual Device ID / IMEI / Build Props / MAC / GAID Editor
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, InsIndigoSecondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = InsCyanPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Manipulasi Identitas Per-Instance",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Slot #${selected.instanceIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = InsCyanPrimary
                            )
                        }

                        SpoofInputField(
                            label = "Settings.Secure.ANDROID_ID (16-Hex)",
                            value = androidIdInput,
                            onValueChange = { androidIdInput = it },
                            onRandomizeSingle = { androidIdInput = IdentitySpoofer.generateAndroidId() },
                            tag = "input_android_id"
                        )
                        SpoofInputField(
                            label = "TelephonyManager.getImei() (15-Digit Luhn)",
                            value = imeiInput,
                            onValueChange = { imeiInput = it },
                            onRandomizeSingle = { imeiInput = IdentitySpoofer.generateLuhnValidImei() },
                            tag = "input_imei"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manufacturerInput,
                                onValueChange = { manufacturerInput = it },
                                label = { Text("Build.MANUFACTURER") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = brandInput,
                                onValueChange = { brandInput = it },
                                label = { Text("Build.BRAND") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = modelInput,
                                onValueChange = { modelInput = it },
                                label = { Text("Build.MODEL") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = serialInput,
                                onValueChange = { serialInput = it },
                                label = { Text("Build.SERIAL") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SpoofInputField(
                            label = "WifiInfo.getMacAddress()",
                            value = wifiMacInput,
                            onValueChange = { wifiMacInput = it },
                            onRandomizeSingle = { wifiMacInput = IdentitySpoofer.generateWifiMacAddress() },
                            tag = "input_wifi_mac"
                        )
                        SpoofInputField(
                            label = "Advertising ID (GAID UUID)",
                            value = gaidInput,
                            onValueChange = { gaidInput = it },
                            onRandomizeSingle = { gaidInput = IdentitySpoofer.generateAdvertisingId() },
                            tag = "input_gaid"
                        )
                    }
                }
            }

            // 3. Isolated Mock Location (Fake GPS) Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, InsEmeraldActive.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = InsEmeraldActive, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Mock Location (Fake GPS) Terisolasi",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Intersepsi ILocationManager hanya untuk klon ini",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = mockGpsEnabled,
                                onCheckedChange = { mockGpsEnabled = it },
                                modifier = Modifier.testTag("switch_mock_gps")
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IdentitySpoofer.locationPresets.forEach { loc ->
                                FilterChip(
                                    selected = mockLabelInput == loc.label,
                                    onClick = {
                                        mockGpsEnabled = true
                                        mockLatInput = loc.latitude.toString()
                                        mockLngInput = loc.longitude.toString()
                                        mockLabelInput = loc.label
                                        onApplyLocationPreset(selected, loc)
                                    },
                                    label = { Text(loc.label) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = mockLatInput,
                                onValueChange = { mockLatInput = it },
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = mockLngInput,
                                onValueChange = { mockLngInput = it },
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                val updated = selected.copy(
                                    androidId = androidIdInput.trim(),
                                    imei = imeiInput.trim(),
                                    buildSerial = serialInput.trim(),
                                    buildModel = modelInput.trim(),
                                    buildManufacturer = manufacturerInput.trim(),
                                    buildBrand = brandInput.trim(),
                                    wifiMac = wifiMacInput.trim(),
                                    advertisingId = gaidInput.trim(),
                                    mockLocationEnabled = mockGpsEnabled,
                                    mockLatitude = mockLatInput.toDoubleOrNull() ?: selected.mockLatitude,
                                    mockLongitude = mockLngInput.toDoubleOrNull() ?: selected.mockLongitude,
                                    mockLocationName = mockLabelInput
                                )
                                onSaveCustomIdentity(updated)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = InsEmeraldActive),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_identity_config_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simpan Identitas & Sinkronkan ke Sandbox XML", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. Real-Time Binder IPC Interception Audit Log
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = InsSurfaceElevated),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = InsVioletTertiary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Log Intersepsi Binder IPC (${hookLogs.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                            }
                            Row {
                                IconButton(onClick = { onTriggerSweep(selected) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Uji Hook", tint = InsCyanPrimary)
                                }
                                IconButton(onClick = onClearLogs) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Bersihkan Log", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        hookLogs.take(12).forEach { log ->
                            Surface(
                                color = InsSurfaceCard,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${log.serviceName} → ${log.methodName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = InsCyanPrimary,
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                        Text(
                                            text = log.appName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InsVioletTertiary
                                        )
                                    }
                                    Text(
                                        text = "Spoofed: ${log.spoofedReturnValue}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InsEmeraldActive,
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
        }
    }
}

@Composable
private fun SpoofInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRandomizeSingle: () -> Unit,
    tag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
        trailingIcon = {
            IconButton(onClick = onRandomizeSingle) {
                Icon(Icons.Default.Casino, contentDescription = "Acak $label", tint = InsCyanPrimary)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}
