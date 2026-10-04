package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CloneAppEntity
import com.example.virtual.BinderIpcInterceptor
import com.example.virtual.IdentitySpoofer
import com.example.virtual.VirtualSandboxStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and virtual container identity isolation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("INS Virtual Space", appName)

        val id1 = IdentitySpoofer.generateAndroidId()
        val id2 = IdentitySpoofer.generateAndroidId()
        assertEquals(16, id1.length)
        assertNotEquals(id1, id2)

        val imei1 = IdentitySpoofer.generateLuhnValidImei("35693811")
        assertEquals(15, imei1.length)

        val clone = CloneAppEntity(
            id = 1,
            packageName = "com.pinedrama.short.video",
            appName = "PineDrama",
            instanceIndex = 0,
            androidId = id1,
            imei = imei1,
            imsi = IdentitySpoofer.generateImsi(),
            buildSerial = IdentitySpoofer.generateBuildSerial(),
            buildModel = "Pixel 9 Pro",
            buildManufacturer = "Google",
            buildBrand = "google",
            wifiMac = IdentitySpoofer.generateWifiMacAddress(),
            wifiSsid = "INS_5G",
            advertisingId = IdentitySpoofer.generateAdvertisingId(),
            mockLocationEnabled = true
        )

        assertEquals("PineDrama(Clone App)", clone.recentsTaskTitle)
        assertEquals(
            "/data/user/0/com.ins.virtualspace/virtual/user/0/com.pinedrama.short.video/",
            clone.canonicalVirtualDataPath
        )

        val sandboxRoot = VirtualSandboxStorage.ensureSandboxProvisioned(context, clone)
        assertTrue(sandboxRoot.exists())
        val extractedBaseApk = java.io.File(sandboxRoot, "base.apk")
        val extractedDex = java.io.File(sandboxRoot, "code_cache/classes.dex")
        assertTrue("Extracted base.apk must exist inside virtual sandbox", extractedBaseApk.exists() && extractedBaseApk.length() > 0)
        assertTrue("Extracted classes.dex must exist inside virtual sandbox", extractedDex.exists() && extractedDex.length() > 0)

        val interceptor = BinderIpcInterceptor(context, clone) {}
        val snap = interceptor.executeDiagnosticSweep()
        assertEquals(id1, snap.androidId)
        assertEquals(imei1, snap.imei)

        // Verify VirtualApkEngine mounts the extracted APK into VirtualContextWrapper & intercepts startActivity
        val extractedInfo = com.example.virtual.VirtualApkLauncher.extractApkIntoVirtualSandbox(context, clone, sandboxRoot)
        val (loader, _) = com.example.virtual.VirtualClassLoaderHook.createIsolatedClassLoader(context, clone, sandboxRoot)
        val vCtx = com.example.virtual.VirtualContextWrapper(context, clone, sandboxRoot, loader)
        val mounted = com.example.virtual.VirtualApkEngine.mountExtractedApk(context, clone, sandboxRoot, extractedInfo, vCtx)
        assertEquals("com.pinedrama.short.video", mounted.packageName)
        assertTrue(mounted.declaredActivities.isNotEmpty())

        var interceptedTarget: String? = null
        vCtx.onInterceptStartActivity = { intent ->
            interceptedTarget = intent.component?.className
        }
        vCtx.startActivity(android.content.Intent().setClassName("com.pinedrama.short.video", "com.pinedrama.short.video.MainActivity"))
        assertEquals("com.pinedrama.short.video.MainActivity", interceptedTarget)

        // Verify BlackBoxCore, VirtualProcessManager, and VirtualAppLauncher
        com.example.virtual.VirtualAppLauncher.init(context)
        val installedOk = com.example.virtual.VirtualAppLauncher.installToVirtualSpace(
            apkPath = extractedBaseApk.absolutePath,
            userId = 0,
            context = context,
            targetPackageHint = "com.pinedrama.short.video"
        )
        assertTrue("installToVirtualSpace should succeed on extracted base.apk", installedOk)
        assertTrue(com.example.virtual.BlackBoxCore.get().isInstalled("com.pinedrama.short.video", 0, context))
        assertTrue("PineDrama must be marked isRealApkLoaded in sandbox", mounted.isRealApkLoaded)

        // Verify Hapus Data Clone (resetCloneSandboxData) wipes clone data to fresh state while preserving base.apk
        VirtualSandboxStorage.resetCloneSandboxData(context, clone)
        val freshMarker = java.io.File(sandboxRoot, "files/.fresh_reset_state")
        assertTrue("Fresh reset marker must be created after Hapus Data Clone", freshMarker.exists())
        assertTrue("Staged base.apk must remain intact after Hapus Data Clone", extractedBaseApk.exists() && extractedBaseApk.length() > 0)
    }
}
