# Catatan Perbaikan — INS Virtual Space (Clone App)

Repo ini punya dua lapisan: **engine "VM" in-process yang lama** (tidak bisa menjalankan APK
pihak ketiga sungguhan) dan **fitur Clone App baru berbasis re-sign + install** yang benar-benar
bekerja tanpa root. Lihat `CLONE_APP.md` untuk detail fitur clone.

## 1. Bug yang diperbaiki (lapisan lama)

- `VirtualContainerActivity.kt`: proses boot tidak dibungkus `try/catch`, sehingga exception apa pun
  membuat `isBooting` tetap `true` selamanya → layar macet di "Membuka <app>...". Sekarang dibungkus
  `try/catch/finally`, dan kegagalan ditampilkan lewat panel `VirtualBootErrorPanel` (dengan tombol
  fallback membuka aplikasi asli).

## 2. Fitur baru: Clone App sungguhan (tanpa root, Android 10–16)

Clone tidak lagi sekadar menyalin `base.apk`. Setiap clone sekarang:

1. Diambil APK-nya (base + split) dari aplikasi terpasang atau file APK impor.
2. Nama paketnya diubah menjadi unik (`<paketAsli>.inscloneN`) — termasuk memperbaiki nama class
   relatif (`.MainActivity`) agar tidak rusak, dan mengganti authority/permission agar tidak bentrok.
3. Ditandatangani ulang dengan kunci self-signed milik aplikasi.
4. Dipasang lewat `PackageInstaller` sebagai aplikasi kedua yang terpisah.
5. Dijalankan langsung lewat launcher intent.

APK yang sama bisa ditambah berkali-kali, dan APK lain juga bisa ditambah.

File baru: `app/src/main/java/com/example/clone/*`, `app/src/main/res/raw/clone_keystore.p12`.

## 3. Perubahan konfigurasi

- `app/build.gradle.kts`: tambah `io.github.reandroid:ARSCLib:1.4.0` dan
  `com.android.tools.build:apksig:8.13.0`; `minSdk` 24 → 29 (apksig memakai `java.util.Base64`).
- `AndroidManifest.xml`: tambah `REQUEST_INSTALL_PACKAGES`.
- `CloneAppEntity`: kolom baru `installedClonePackage`; database Room naik ke versi 2.

## 4. Batasan

Re-sign + install tidak berhasil untuk aplikasi dengan signature check, Play Integrity/SafetyNet,
native integrity check, atau Asset Packs. Untuk kasus itu perlu work profile / engine virtualisasi
(tahap berikutnya). Detail: `CLONE_APP.md`.

## 5. Yang perlu diuji di perangkat

Kode ini belum bisa di-build/diuji di lingkungan ini (tanpa Android SDK). Perlu dites langsung:
rename paket APK nyata, proses sign, dan pemasangan. Kalau ada APK yang gagal, logcat akan
menunjukkan penyebabnya.
