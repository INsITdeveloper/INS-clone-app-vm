package com.example.virtual

import android.content.Context
import android.location.Location
import android.os.Build
import com.example.data.CloneAppEntity
import com.example.data.IpcHookLogEntity
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Interfaces representing the hooked Binder / System Service contracts inside the Virtual Container.
 * Calls to these interfaces pass through Java Dynamic Proxy InvocationHandlers, allowing real-time
 * interception, logging, and per-clone spoofing without root access.
 */
interface IHookedTelephonyBinder {
    fun getDeviceId(): String
    fun getImei(slotIndex: Int): String
    fun getSubscriberId(): String
}

interface IHookedSettingsBinder {
    fun getSecureString(name: String): String
}

interface IHookedWifiBinder {
    fun getMacAddress(): String
    fun getSSID(): String
}

interface IHookedAdvertisingBinder {
    fun getAdvertisingId(): String
    fun isLimitAdTrackingEnabled(): Boolean
}

interface IHookedLocationBinder {
    fun getLastKnownLocation(provider: String): Location?
}

interface IHookedBuildEnvironment {
    fun getSerial(): String
    fun getModel(): String
    fun getManufacturer(): String
    fun getBrand(): String
}

data class HookedRuntimeSnapshot(
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
    val mockLocation: Location?,
    val interceptedLogs: List<IpcHookLogEntity>
)

/**
 * Dynamic Proxy Binder IPC Interceptor Engine.
 * Creates proxied service stubs for a specific CloneAppEntity and intercepts every hardware/identity query.
 */
class BinderIpcInterceptor(
    private val hostContext: Context,
    private val clone: CloneAppEntity,
    private val onHookTriggered: (IpcHookLogEntity) -> Unit
) {
    private val classLoader: ClassLoader = BinderIpcInterceptor::class.java.classLoader!!

    private fun recordHook(
        serviceName: String,
        methodName: String,
        hostOriginal: String,
        spoofedValue: String
    ) {
        onHookTriggered(
            IpcHookLogEntity(
                cloneId = clone.id,
                packageName = clone.packageName,
                appName = clone.appName,
                serviceName = serviceName,
                methodName = methodName,
                hostOriginalValue = hostOriginal,
                spoofedReturnValue = spoofedValue,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    val telephonyProxy: IHookedTelephonyBinder = Proxy.newProxyInstance(
        classLoader,
        arrayOf(IHookedTelephonyBinder::class.java),
        object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any {
                return when (method.name) {
                    "getDeviceId" -> {
                        recordHook("ITelephony.Stub", "getDeviceId()", "SecurityException / HostIMEI", clone.imei)
                        clone.imei
                    }
                    "getImei" -> {
                        val slot = (args?.firstOrNull() as? Int) ?: 0
                        recordHook("ITelephony.Stub", "getImei(slot=$slot)", "SecurityException / HostIMEI", clone.imei)
                        clone.imei
                    }
                    "getSubscriberId" -> {
                        recordHook("ITelephony.Stub", "getSubscriberId()", "Host_IMSI_Hidden", clone.imsi)
                        clone.imsi
                    }
                    else -> ""
                }
            }
        }
    ) as IHookedTelephonyBinder

    val settingsProxy: IHookedSettingsBinder = Proxy.newProxyInstance(
        classLoader,
        arrayOf(IHookedSettingsBinder::class.java),
        object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any {
                val key = (args?.firstOrNull() as? String) ?: "android_id"
                val hostId = IdentitySpoofer.getHostRealAndroidId(hostContext)
                return if (key == "android_id") {
                    recordHook("IContentProvider(Settings.Secure)", "getString(ANDROID_ID)", hostId, clone.androidId)
                    clone.androidId
                } else {
                    ""
                }
            }
        }
    ) as IHookedSettingsBinder

    val wifiProxy: IHookedWifiBinder = Proxy.newProxyInstance(
        classLoader,
        arrayOf(IHookedWifiBinder::class.java),
        object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any {
                return when (method.name) {
                    "getMacAddress" -> {
                        recordHook("IWifiManager.Stub", "WifiInfo.getMacAddress()", "02:00:00:00:00:00 (Android Default)", clone.wifiMac)
                        clone.wifiMac
                    }
                    "getSSID" -> {
                        recordHook("IWifiManager.Stub", "WifiInfo.getSSID()", "\"Host_WiFi\"", "\"${clone.wifiSsid}\"")
                        "\"${clone.wifiSsid}\""
                    }
                    else -> ""
                }
            }
        }
    ) as IHookedWifiBinder

    val advertisingProxy: IHookedAdvertisingBinder = Proxy.newProxyInstance(
        classLoader,
        arrayOf(IHookedAdvertisingBinder::class.java),
        object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any {
                return when (method.name) {
                    "getAdvertisingId" -> {
                        recordHook("IAdvertisingIdService", "getId() [GAID]", "00000000-host-gaid-0000", clone.advertisingId)
                        clone.advertisingId
                    }
                    "isLimitAdTrackingEnabled" -> true
                    else -> ""
                }
            }
        }
    ) as IHookedAdvertisingBinder

    val locationProxy: IHookedLocationBinder = Proxy.newProxyInstance(
        classLoader,
        arrayOf(IHookedLocationBinder::class.java),
        object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                val provider = (args?.firstOrNull() as? String) ?: "gps"
                return if (clone.mockLocationEnabled) {
                    val mockLoc = IdentitySpoofer.createIsolatedLocation(clone, provider)
                    recordHook(
                        "ILocationManager.Stub",
                        "getLastLocation($provider)",
                        "Real GPS Sensor",
                        "${clone.mockLocationName} (${"%.4f".format(clone.mockLatitude)}, ${"%.4f".format(clone.mockLongitude)})"
                    )
                    mockLoc
                } else {
                    recordHook("ILocationManager.Stub", "getLastLocation($provider)", "Real GPS Sensor", "Passthrough (Mock Off)")
                    null
                }
            }
        }
    ) as IHookedLocationBinder

    val buildEnvironmentProxy: IHookedBuildEnvironment = Proxy.newProxyInstance(
        classLoader,
        arrayOf(IHookedBuildEnvironment::class.java),
        object : InvocationHandler {
            override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any {
                return when (method.name) {
                    "getSerial" -> {
                        recordHook("android.os.Build", "Build.SERIAL", Build.UNKNOWN, clone.buildSerial)
                        clone.buildSerial
                    }
                    "getModel" -> {
                        recordHook("android.os.Build", "Build.MODEL", Build.MODEL, clone.buildModel)
                        clone.buildModel
                    }
                    "getManufacturer" -> {
                        recordHook("android.os.Build", "Build.MANUFACTURER", Build.MANUFACTURER, clone.buildManufacturer)
                        clone.buildManufacturer
                    }
                    "getBrand" -> {
                        recordHook("android.os.Build", "Build.BRAND", Build.BRAND, clone.buildBrand)
                        clone.buildBrand
                    }
                    else -> ""
                }
            }
        }
    ) as IHookedBuildEnvironment

    /**
     * Executes a full diagnostic sweep across all hooked Binder proxies and returns the snapshot.
     */
    fun executeDiagnosticSweep(): HookedRuntimeSnapshot {
        val logs = mutableListOf<IpcHookLogEntity>()
        val localInterceptor = BinderIpcInterceptor(hostContext, clone) { logs.add(it) }
        val androidId = localInterceptor.settingsProxy.getSecureString("android_id")
        val imei = localInterceptor.telephonyProxy.getImei(0)
        val imsi = localInterceptor.telephonyProxy.getSubscriberId()
        val serial = localInterceptor.buildEnvironmentProxy.getSerial()
        val model = localInterceptor.buildEnvironmentProxy.getModel()
        val manufacturer = localInterceptor.buildEnvironmentProxy.getManufacturer()
        val brand = localInterceptor.buildEnvironmentProxy.getBrand()
        val mac = localInterceptor.wifiProxy.getMacAddress()
        val ssid = localInterceptor.wifiProxy.getSSID()
        val gaid = localInterceptor.advertisingProxy.getAdvertisingId()
        val loc = localInterceptor.locationProxy.getLastKnownLocation("gps")

        // Also forward logs to main listener
        logs.forEach { onHookTriggered(it) }

        return HookedRuntimeSnapshot(
            androidId = androidId,
            imei = imei,
            imsi = imsi,
            buildSerial = serial,
            buildModel = model,
            buildManufacturer = manufacturer,
            buildBrand = brand,
            wifiMac = mac,
            wifiSsid = ssid,
            advertisingId = gaid,
            mockLocation = loc,
            interceptedLogs = logs
        )
    }
}
