# Fitur Clone App — Cara Kerja & Batasan

Fitur ini mengubah "clone" dari sekadar menyalin `base.apk` menjadi **aplikasi kedua yang
benar-benar terpasang dan bisa dijalankan**, tanpa root, pada Android 10–16 (API 29–36).

## Alur

1. **Tambah aplikasi**
   - Dari aplikasi terpasang di HP (`discoverInstallableApps`), atau
   - Dari file APK yang diimpor (file picker → `importApkFromUriAndClone`).
2. **Ambil APK sumber**: `base.apk` + semua `split_config.*.apk` (jika ada).
3. **Ganti nama paket** menjadi unik per clone: `<paketAsli>.insclone<N>` (ARSCLib).
   - Atribut `package` pada `<manifest>` **dan** nama package di `resources.arsc`.
   - Semua string di manifest yang diawali paket lama (authority provider, nama permission,
     `targetPackage`, dll) ikut diganti agar tidak bentrok.
   - `android:label` aplikasi diganti menjadi `<Nama> (Clone N)`.
4. **Tanda tangani ulang** dengan kunci self-signed milik aplikasi ini (apksig, v1+v2+v3).
5. **Pasang** lewat `PackageInstaller` (muncul dialog konfirmasi pemasangan Android).
6. **Simpan** nama paket hasil (`installedClonePackage`) di database.
7. **Buka clone**: `launchCloneInVirtualSpace` menjalankan paket hasil re-sign itu lewat
   launcher intent biasa — jadi clone benar-benar jalan sebagai aplikasi terpisah.

Karena setiap clone punya nama paket sendiri, **APK yang sama bisa ditambah berkali-kali**
(`.insclone1`, `.insclone2`, …) dan **APK lain juga bisa ditambah**.

## File utama

| File | Peran |
|---|---|
| `clone/CloneManager.kt` | Orkestrasi + nama paket + cek izin |
| `clone/CloneApkBuilder.kt` | Build (rename + sign) base & split |
| `clone/ApkPackageRenamer.kt` | Rename package via ARSCLib |
| `clone/CloneSigning.kt` | Load kunci + sign via apksig |
| `clone/CloneInstaller.kt` | Pasang via PackageInstaller |
| `res/raw/clone_keystore.p12` | Kunci self-signed (alias `insclone`, pass `insclone`) |

## Izin yang dibutuhkan

- `android.permission.REQUEST_INSTALL_PACKAGES` (sudah ditambahkan ke manifest).
- Pengguna harus mengaktifkan **"Install unknown apps"** untuk aplikasi ini. Aplikasi otomatis
  membuka halaman pengaturan tersebut jika belum aktif.

## Batasan (penting)

Cara re-sign + install ini **tidak berhasil untuk semua aplikasi**. Yang biasanya gagal:

- Aplikasi yang **memverifikasi tanda tangan sendiri** saat runtime (signature check) → bisa
  menolak jalan / crash.
- Aplikasi dengan **Play Integrity / SafetyNet / DRM** (Netflix, banking, game online) → diblokir.
- Aplikasi dengan **native library yang memverifikasi integritas** (beberapa app chat/game).
- Aplikasi dengan **Asset Packs** (khusus Play) → split tidak lengkap.
- Aplikasi sistem (system app) → sering butuh permission level sistem.

Untuk kasus-kasus di atas, isolasi yang benar-benar andal hanya bisa lewat **work profile /
user kedua Android** (mis. Shelter/Island) atau **engine virtualisasi** (VirtualApp/BlackBox).
Jalur work profile & engine akan ditambahkan sebagai tahap berikutnya.

## Catatan teknis

- `minSdk` dinaikkan ke **29** karena apksig memakai `java.util.Base64` (API 26+) dan agar
  sesuai target Android 10–16.
- Database Room naik ke versi 2 (kolom `installedClonePackage`); memakai
  `fallbackToDestructiveMigration()` sehingga data lama akan di-reset saat upgrade.
