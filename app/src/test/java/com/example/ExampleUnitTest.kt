package com.example

import com.example.data.CloneAppEntity
import com.example.virtual.IdentitySpoofer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun cloneEntity_formatsRecentsTitleAndSandboxPathCorrectly() {
        val clone = CloneAppEntity(
            id = 1,
            packageName = "com.pinedrama.short.video",
            appName = "PineDrama",
            instanceIndex = 0,
            androidId = IdentitySpoofer.generateAndroidId(),
            imei = IdentitySpoofer.generateLuhnValidImei(),
            imsi = IdentitySpoofer.generateImsi(),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = "Pixel 9 Pro",
            buildManufacturer = "Google",
            buildBrand = "google",
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = "INS_5G",
            advertisingId = IdentitySpoofer.generateAdvertisingId()
        )

        assertEquals("PineDrama(Clone App)", clone.recentsTaskTitle)
        assertEquals(
            "/data/user/0/com.ins.virtualspace/virtual/user/0/com.pinedrama.short.video/",
            clone.canonicalVirtualDataPath
        )
    }

    @Test
    fun identitySpoofer_generatesUniqueAndValidIdentifiersPerClone() {
        val id1 = IdentitySpoofer.generateAndroidId()
        val id2 = IdentitySpoofer.generateAndroidId()
        assertEquals(16, id1.length)
        assertNotEquals(id1, id2)

        val imei1 = IdentitySpoofer.generateLuhnValidImei("35693811")
        val imei2 = IdentitySpoofer.generateLuhnValidImei("35693811")
        assertEquals(15, imei1.length)
        assertNotEquals(imei1, imei2)
        val expectedCheck = IdentitySpoofer.calculateLuhnCheckDigit(imei1.take(14))
        assertEquals(expectedCheck, imei1.last().digitToInt())

        val mac = IdentitySpoofer.generateWifiMacAddress()
        assertTrue(mac.startsWith("02:"))
        assertEquals(17, mac.length)
    }
}
