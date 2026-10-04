package com.example.virtual

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.example.data.CloneAppEntity
import java.security.SecureRandom
import java.util.UUID

data class DeviceHardwarePreset(
    val title: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val tacPrefix: String // 8-digit TAC prefix for realistic IMEI generation
)

data class MockLocationPreset(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 3.0f
)

object IdentitySpoofer {
    private val secureRandom = SecureRandom()

    val hardwarePresets = listOf(
        DeviceHardwarePreset("Samsung S24 Ultra", "SM-S928B", "Samsung", "samsung", "35849109"),
        DeviceHardwarePreset("Google Pixel 9 Pro", "Pixel 9 Pro", "Google", "google", "35693811"),
        DeviceHardwarePreset("Xiaomi 14 Ultra", "24030PN60G", "Xiaomi", "xiaomi", "86492006"),
        DeviceHardwarePreset("ASUS ROG Phone 8", "AI2401_A", "ASUS", "asus", "35912010"),
        DeviceHardwarePreset("OnePlus 12 5G", "CPH2581", "OnePlus", "oneplus", "86941205"),
        DeviceHardwarePreset("Vivo X100 Pro", "V2324A", "vivo", "vivo", "86310904")
    )

    val locationPresets = listOf(
        MockLocationPreset("Jakarta, ID (SCBD)", -6.2246, 106.8097, 2.8f),
        MockLocationPreset("Tokyo, JP (Shibuya)", 35.6595, 139.7004, 3.1f),
        MockLocationPreset("Singapore, SG (Marina)", 1.2834, 103.8607, 2.5f),
        MockLocationPreset("Seoul, KR (Gangnam)", 37.4979, 127.0276, 3.4f),
        MockLocationPreset("San Francisco, US", 37.7749, -122.4194, 4.0f),
        MockLocationPreset("London, UK (Soho)", 51.5136, -0.1365, 3.2f)
    )

    /**
     * Generates a 16-character lowercase hex string matching Android's Settings.Secure.ANDROID_ID format.
     */
    fun generateAndroidId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a 15-digit Luhn-valid IMEI using an 8-digit TAC prefix + 6 random serial digits + 1 Luhn check digit.
     */
    fun generateLuhnValidImei(tacPrefix: String = hardwarePresets.random().tacPrefix): String {
        val cleanTac = tacPrefix.filter { it.isDigit() }.padEnd(8, '3').take(8)
        val serialPart = (1..6).map { secureRandom.nextInt(10) }.joinToString("")
        val first14 = cleanTac + serialPart
        val checkDigit = calculateLuhnCheckDigit(first14)
        return first14 + checkDigit
    }

    fun calculateLuhnCheckDigit(digits14: String): Int {
        var sum = 0
        for (i in digits14.indices) {
            var d = digits14[i].digitToInt()
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return (10 - (sum % 10)) % 10
    }

    fun generateImsi(mccMnc: String = "51010"): String {
        val subscriber = (1..10).map { secureRandom.nextInt(10) }.joinToString("")
        return (mccMnc + subscriber).take(15)
    }

    fun generateBuildSerial(): String {
        val chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return "R58" + (1..8).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    fun generateWifiMacAddress(): String {
        val octets = ByteArray(5)
        secureRandom.nextBytes(octets)
        // 02 prefix = Locally administered unicast MAC address
        return "02:" + octets.joinToString(":") { "%02X".format(it) }
    }

    fun generateWifiSsid(): String {
        val suffixes = listOf("5G_Secure", "Mesh_Pro", "Fiber_Ultra", "DualBand_AX", "PrivateNet")
        return "INS_${suffixes.random()}_${100 + secureRandom.nextInt(899)}"
    }

    fun generateAdvertisingId(): String = UUID.randomUUID().toString()

    /**
     * Randomizes all device identity attributes on an existing CloneAppEntity.
     */
    fun randomizeIdentity(
        current: CloneAppEntity,
        preset: DeviceHardwarePreset = hardwarePresets.random()
    ): CloneAppEntity {
        return current.copy(
            androidId = generateAndroidId(),
            imei = generateLuhnValidImei(preset.tacPrefix),
            imsi = generateImsi(),
            buildSerial = generateBuildSerial(),
            buildModel = preset.model,
            buildManufacturer = preset.manufacturer,
            buildBrand = preset.brand,
            wifiMac = generateWifiMacAddress(),
            wifiSsid = generateWifiSsid(),
            advertisingId = generateAdvertisingId()
        )
    }

    /**
     * Constructs an Android Location instance carrying the clone's isolated Mock GPS coordinates.
     */
    fun createIsolatedLocation(clone: CloneAppEntity, provider: String = "gps"): Location {
        return Location(provider).apply {
            latitude = clone.mockLatitude
            longitude = clone.mockLongitude
            accuracy = clone.mockAccuracy
            altitude = 42.5
            bearing = 180.0f
            speed = 0.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    @SuppressLint("HardwareIds")
    fun getHostRealAndroidId(context: Context): String {
        return runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_host_id"
        }.getOrDefault("restricted_host_id")
    }

    fun getHostRealModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}
