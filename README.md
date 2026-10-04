# INS Virtual Space (Clone App VM) 🛡️📱

**INS Virtual Space** adalah Virtual Container Engine & Dual Space Manager berkinerja tinggi untuk Android yang dibangun menggunakan **Kotlin** dan **Jetpack Compose** (Material 3). Mesin ini memungkinkan cloning dan eksekusi APK Android secara terisolasi tanpa memerlukan akses root.

---

## 🌟 Fitur Utama

- **🚀 In-Process Virtual Container**:
  - Menjalankan instance `Activity` asli dari `base.apk` melalui refleksi low-level `Activity.attach(...)` dengan `PhoneWindow` terisolasi.
  - Lifecycle management lengkap (`performCreate`, `performStart`, `performResume`, `onStop`, `onDestroy`).
  - Dinamis layout inflation langsung dari resources APK terkloning ke host View.
  - Backstack navigasi internal terintegrasi dengan tombol Back Android (`BackHandler`).

- **🎭 Identity Spoofing & Isolation**:
  - Isolasi per-clone instance dengan ID unik (`instanceIndex`).
  - Device fingerprint spoofer (IMEI, Android ID, MAC address, Hardware Build Serial).
  - SharedPreferences sandbox terpisah per-clone (`/data/user/<id>/<package>/shared_prefs`).
  - SQLite database viewer & manager terisolasi per clone.

- **🔌 Virtual System Services**:
  - Sandboxed `IPackageManager` proxy untuk mengembalikan metadata virtual (`base.apk` & sandbox paths).
  - Inisialisasi `<provider>` (`androidx.startup.InitializationProvider`, dll.) sebelum `Application.onCreate()`.
  - Binder IPC hook logger & telemetry dashboard.

- **🎨 Modern Android Architecture**:
  - 100% Jetpack Compose UI dengan Material Design 3.
  - Arsitektur MVVM (Model-View-ViewModel) dengan Room Database & Kotlin Coroutines/Flow.
  - Clean edge-to-edge support & responsive layout.

---

## 🏗️ Struktur Proyek

```
app/src/main/java/com/example/
├── MainActivity.kt               # Entrypoint aplikasi utama
├── VirtualContainerActivity.kt   # Container aktivitas virtual
├── virtual/                      # Core Virtual Engine
│   ├── VirtualApkEngine.kt       # Engine loading APK & inisialisasi Activity
│   ├── VirtualContextWrapper.kt  # Context sandbox, Resource & Asset injection
│   ├── VirtualAppLauncher.kt     # Pengelola instalasi & peluncuran virtual
│   ├── VirtualProcessManager.kt  # Manajemen multi-proses & isolasi
│   ├── IdentitySpoofer.kt        # Spoofing data identitas perangkat
│   ├── VirtualSandboxStorage.kt  # Virtual internal storage
│   ├── VirtualSqliteManager.kt   # SQLite database sandbox
│   └── VirtualXmlPrefsManager.kt # XML SharedPreferences manager
├── ui/                           # UI Jetpack Compose (M3)
│   ├── HomeDualSpaceScreen.kt    # Dashboard Dual Space & daftar klon
│   ├── InProcessApkHostScreen.kt # Layar penampil aplikasi virtual
│   ├── IdentitySpoofScreen.kt    # Layar konfigurasi spoof identitas
│   └── VirtualSpaceViewModel.kt  # State management terpusat
└── data/                         # Room Database & Entities
    ├── VirtualSpaceDatabase.kt
    ├── VirtualSpaceRepository.kt
    └── CloneAppEntity.kt
```

---

## 🛠️ Build & Instalasi

### Persyaratan:
- Android Studio Ladybug / Meerkat atau yang lebih baru
- JDK 17+
- Android SDK 34 (Android 14) / 35 (Android 15)

### Perintah Gradle:
```bash
# Build Debug APK
gradle assembleDebug

# Jalankan Unit & Robolectric Tests
gradle :app:testDebugUnitTest
```

---

## 📄 Lisensi
Dibuat oleh **INsITdeveloper**.
Hak cipta dilindungi undang-undang.
