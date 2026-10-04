package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloned_apps")
data class CloneAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val instanceIndex: Int = 0,
    val folderName: String? = null, // null = Home Grid, "Alat" = inside Alat folder
    val iconColorHex: Long = 0xFF00E5FF,
    val categoryTag: String = "App",
    val isRunning: Boolean = false,
    val virtualPid: Int = 0,
    val lastLaunchedAt: Long = System.currentTimeMillis(),

    // Nama paket APK hasil re-sign yang benar-benar terpasang sebagai aplikasi clone terpisah
    // (mis. "com.whatsapp.insclone1"). Kosong = clone belum benar-benar terpasang.
    val installedClonePackage: String = "",

    // Per-Clone Isolated Device Identity Spoofing
    val androidId: String,
    val imei: String,
    val imsi: String,
    val buildSerial: String,
    val buildModel: String,
    val buildManufacturer: String,
    val buildBrand: String,
    val wifiMac: String,
    val wifiSsid: String,
    val advertisingId: String,

    // Per-Clone Isolated Mock Location (Fake GPS)
    val mockLocationEnabled: Boolean = false,
    val mockLatitude: Double = -6.2088,
    val mockLongitude: Double = 106.8456,
    val mockAccuracy: Float = 3.2f,
    val mockLocationName: String = "Jakarta, ID"
) {
    val recentsTaskTitle: String
        get() = if (instanceIndex == 0) {
            "${appName}(Clone App)"
        } else {
            "${appName}#${instanceIndex + 1}(Clone App)"
        }

    val canonicalVirtualDataPath: String
        get() = "/data/user/0/com.ins.virtualspace/virtual/user/$instanceIndex/$packageName/"

    val legacyAliasDataPath: String
        get() = "/data/data/$packageName/"
}
