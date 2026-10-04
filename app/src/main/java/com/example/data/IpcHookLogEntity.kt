package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ipc_hook_logs")
data class IpcHookLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cloneId: Int,
    val packageName: String,
    val appName: String,
    val serviceName: String, // e.g., "ITelephony", "Settings.Secure", "IWifiManager", "ILocationManager"
    val methodName: String,  // e.g., "getImei()", "getString(ANDROID_ID)", "getMacAddress()"
    val hostOriginalValue: String,
    val spoofedReturnValue: String,
    val timestamp: Long = System.currentTimeMillis()
)
