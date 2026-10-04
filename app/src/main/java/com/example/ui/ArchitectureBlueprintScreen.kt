package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.InsCyanPrimary
import com.example.ui.theme.InsEmeraldActive
import com.example.ui.theme.InsIndigoSecondary
import com.example.ui.theme.InsSurfaceCard
import com.example.ui.theme.InsSurfaceDark
import com.example.ui.theme.InsSurfaceElevated
import com.example.ui.theme.InsVioletTertiary
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ArchitectureBlueprintScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = "Arsitektur Sistem & Engine",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.testTag("architecture_header")
                )
                Text(
                    text = "INS Virtual App Cloner / Dual Space (com.ins.virtualspace)",
                    style = MaterialTheme.typography.labelSmall,
                    color = InsCyanPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ArchitectureLayerCard(
                    icon = Icons.Default.Layers,
                    badge = "LAYER 1 • VIRTUAL CONTAINER & PROCESS ISOLATION",
                    title = "ClassLoader Hooking & VirtualContextWrapper",
                    accentColor = InsCyanPrimary,
                    codeSnippet = """
                        // Isolasi DEX & Path Redirection tanpa Root
                        val dexLoader = DexClassLoader(
                            sourceApkPath,
                            "/data/user/0/com.ins.virtualspace/virtual/user/0/<pkg>/code_cache",
                            "/data/user/0/com.ins.virtualspace/virtual/user/0/<pkg>/lib",
                            hostContext.classLoader.parent
                        )
                        // Recents TaskDescription: "[NamaAplikasi](Clone App)"
                        val taskDesc = ActivityManager.TaskDescription(
                            "${'$'}{appName}(Clone App)", cloneIconBitmap, accentColor
                        )
                        activity.setTaskDescription(taskDesc)
                    """.trimIndent(),
                    bullets = listOf(
                        "Setiap klon di-mount pada /data/user/0/com.ins.virtualspace/virtual/user/<slot>/<package_name>/.",
                        "VirtualContextWrapper mengintersepsi getDataDir(), getFilesDir(), getCacheDir(), getDatabasePath(), dan getSharedPreferences().",
                        "VirtualContainerActivity menggunakan FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK agar muncul terpisah di Android Recents."
                    )
                )
            }

            item {
                ArchitectureLayerCard(
                    icon = Icons.Default.Security,
                    badge = "LAYER 2 • PER-CLONE BINDER IPC INTERCEPTION",
                    title = "Dynamic Proxy IdentitySpoofer Engine",
                    accentColor = InsEmeraldActive,
                    codeSnippet = """
                        // Java Dynamic Proxy pada ServiceManager / Binder Stubs
                        Proxy.newProxyInstance(loader, arrayOf(ITelephony::class.java)) { _, method, args ->
                            when (method.name) {
                                "getImei", "getDeviceId" -> clone.spoofedImei // Luhn-valid 15-digit
                                "getSubscriberId"        -> clone.spoofedImsi
                                else -> method.invoke(realTelephonyBinder, args)
                            }
                        }
                    """.trimIndent(),
                    bullets = listOf(
                        "Settings.Secure.ANDROID_ID: Menghasilkan 16-karakter hex unik per klon.",
                        "TelephonyManager.getDeviceId() / getImei(): Generator IMEI 15-digit dengan algoritma checksum Luhn.",
                        "Build Props & Network: Spoofing Build.SERIAL, MODEL, MANUFACTURER, BRAND, WifiInfo.getMacAddress(), dan GAID.",
                        "Mock Location Terisolasi: Menginjeksi objek Location(gps) per-klon tanpa mempengaruhi aplikasi utama di luar container."
                    )
                )
            }

            item {
                ArchitectureLayerCard(
                    icon = Icons.Default.FolderSpecial,
                    badge = "LAYER 3 • INS MANAGER (VIRTUAL DATA & DB ENGINE)",
                    title = "Sandbox Explorer, SQLite Viewer & XML Editor",
                    accentColor = InsVioletTertiary,
                    codeSnippet = """
                        /data/user/0/com.ins.virtualspace/virtual/user/0/<package_name>/
                        ├── databases/
                        │   └── app_sandbox_data.db      (Interactive SQLite CRUD)
                        ├── shared_prefs/
                        │   ├── ins_identity_config.xml  (Real-time Spoof Config)
                        │   └── <pkg>_preferences.xml    (Live XML PullParser/Serializer)
                        ├── cache/ & code_cache/         (One-Click Cache Cleaner)
                        └── files/ & lib/                (Isolated Assets & Native SO)
                    """.trimIndent(),
                    bullets = listOf(
                        "SQLite Viewer Interaktif: Membuka file .db di direktori virtual, membaca skema PRAGMA table_info, serta mendukung edit/insert/delete baris dan eksekusi query SQL.",
                        "SharedPreferences XML Editor: Membaca dan menulis ulang tag <map>, <string>, <int>, <boolean>, <long>, <float> secara real-time.",
                        "One-Click Cache & Data Cleaner: Menghapus direktori cache/code_cache atau mereset seluruh sandbox klon dengan sekali klik."
                    )
                )
            }

            item {
                ArchitectureLayerCard(
                    icon = Icons.Default.AccountTree,
                    badge = "LAYER 4 • MODULAR PROJECT STRUCTURE",
                    title = "Struktur Paket & Modul Aplikasi",
                    accentColor = InsIndigoSecondary,
                    codeSnippet = """
                        com.ins.virtualspace (UI & Engine Modules)
                        ├── data/
                        │   ├── CloneAppEntity.kt          (State klon, Path & Identity)
                        │   ├── IpcHookLogEntity.kt        (Log audit intersepsi Binder)
                        │   ├── VirtualSpaceDao.kt         (Reactive Flow Room DAO)
                        │   └── VirtualSpaceRepository.kt  (Manajemen APK & Seed PineDrama)
                        ├── virtual/
                        │   ├── IdentitySpoofer.kt         (Generator IMEI Luhn, MAC, GPS)
                        │   ├── BinderIpcInterceptor.kt    (Dynamic Proxy IPC Hooks)
                        │   ├── VirtualContextWrapper.kt   (Redireksi I/O & DexClassLoader)
                        │   ├── VirtualSandboxStorage.kt   (Manajer File /virtual/user/0/)
                        │   ├── VirtualSqliteManager.kt    (Engine CRUD SQLite .db)
                        │   └── VirtualXmlPrefsManager.kt  (Engine XML SharedPreferences)
                        └── ui/
                            ├── HomeDualSpaceScreen.kt     (Grid Klon, Folder Alat, FAB +)
                            ├── InsManagerScreen.kt        (Explorer, SQLite & XML Editor)
                            ├── IdentitySpoofScreen.kt     (Per-Clone Spoofer & Fake GPS)
                            └── VirtualContainerActivity.kt(Runtime Sandbox & Recents Task)
                    """.trimIndent(),
                    bullets = listOf(
                        "Arsitektur MVVM + Clean Repository dengan Room Persistence & Kotlin Coroutines Flow.",
                        "Desain adaptif Material Design 3 menggunakan NavigationBar (Compact) dan NavigationRail (Tablet/Expanded)."
                    )
                )
            }
        }
    }
}

@Composable
private fun ArchitectureLayerCard(
    icon: ImageVector,
    badge: String,
    title: String,
    accentColor: Color,
    codeSnippet: String,
    bullets: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InsSurfaceCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = InsSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = codeSnippet,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = InsCyanPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            bullets.forEach { b ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", style = MaterialTheme.typography.bodySmall, color = accentColor)
                    Text(
                        text = b,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
