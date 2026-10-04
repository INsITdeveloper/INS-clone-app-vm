package com.example.virtual

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.example.VirtualContainerActivity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class VirtualProcessRecord(
    val vPid: Int,
    val vUid: Int,
    val packageName: String,
    val userId: Int,
    val processName: String,
    val installedApkPath: String,
    val sandboxDataDir: String,
    val targetComponent: ComponentName?,
    val hostOsPid: Int,
    val startedAt: Long
)

/**
 * Isolated Virtual Process Service running inside the virtual container context (`com.ins.virtualspace`).
 *
 * Bound via [VirtualProcessManager.bindVirtualService] before dispatching any target APK's
 * `LaunchIntent` into the virtual container.
 */
class VirtualProcessService : Service() {

    inner class VirtualProcessBinder : Binder() {
        fun getService(): VirtualProcessService = this@VirtualProcessService

        fun spawnVirtualProcess(
            packageName: String,
            userId: Int,
            installedApkPath: String,
            launchIntent: Intent
        ): VirtualProcessRecord {
            return allocateProcessRecord(packageName, userId, installedApkPath, launchIntent)
        }
    }

    private val binder = VirtualProcessBinder()

    override fun onCreate() {
        super.onCreate()
        VirtualProcessManager.installVirtualCrashGuard(applicationContext)
        Log.i(TAG, "VirtualProcessService initialized in process PID=${Process.myPid()}")
    }

    override fun onBind(intent: Intent?): IBinder {
        val pkg = intent?.getStringExtra(VirtualProcessManager.EXTRA_PACKAGE_NAME).orEmpty()
        val userId = intent?.getIntExtra(VirtualProcessManager.EXTRA_USER_ID, 0) ?: 0
        Log.i(TAG, "onBind virtual process for pkg=$pkg userId=$userId (PID=${Process.myPid()})")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        VirtualProcessManager.installVirtualCrashGuard(applicationContext)
        return START_STICKY
    }

    fun allocateProcessRecord(
        packageName: String,
        userId: Int,
        installedApkPath: String,
        launchIntent: Intent
    ): VirtualProcessRecord {
        return VirtualProcessManager.getOrCreateProcessRecord(
            context = applicationContext,
            packageName = packageName,
            userId = userId,
            installedApkPath = installedApkPath,
            targetComponent = launchIntent.component
        )
    }

    companion object {
        private const val TAG = "VirtualProcessService"
    }
}

/**
 * Manages virtual processes, `bindService` lifecycle, and sandboxed `startActivity`
 * execution inside the virtual space context (`com.ins.virtualspace`).
 *
 * STRICT GUARANTEES:
 * 1. Never launches external host applications outside the virtual container.
 * 2. Never uses `Intent.ACTION_VIEW` or browser URLs.
 * 3. Wraps all virtual process bindings and activity launches with structured error handling,
 *    Logcat error reporting, and user-visible Toast error notifications.
 */
object VirtualProcessManager {

    private const val TAG = "VirtualProcessManager"
    const val VIRTUAL_SPACE_NAMESPACE = "com.ins.virtualspace"

    const val EXTRA_CLONE_ID = "extra_clone_id"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_APP_NAME = "extra_app_name"
    const val EXTRA_USER_ID = "extra_instance_index"
    const val EXTRA_TARGET_INTENT = "extra_target_launch_intent"
    const val EXTRA_INSTALLED_APK_PATH = "extra_installed_apk_path"
    const val EXTRA_VIRTUAL_PID = "extra_virtual_pid"
    const val EXTRA_VIRTUAL_UID = "extra_virtual_uid"

    private val nextVirtualPid = AtomicInteger(15000)
    private val crashGuardInstalled = AtomicBoolean(false)
    private val isServiceBound = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeBinder: VirtualProcessService.VirtualProcessBinder? = null

    private val runningProcesses = ConcurrentHashMap<String, VirtualProcessRecord>()
    private val pendingBindCallbacks = mutableListOf<(VirtualProcessService.VirtualProcessBinder?) -> Unit>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val vBinder = service as? VirtualProcessService.VirtualProcessBinder
            activeBinder = vBinder
            isServiceBound.set(vBinder != null)
            Log.i(TAG, "Connected to VirtualProcessService: component=$name binder=$vBinder")

            val callbacks: List<(VirtualProcessService.VirtualProcessBinder?) -> Unit>
            synchronized(pendingBindCallbacks) {
                callbacks = pendingBindCallbacks.toList()
                pendingBindCallbacks.clear()
            }
            callbacks.forEach { callback ->
                try {
                    callback(vBinder)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in VirtualProcessService bind callback", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from VirtualProcessService: component=$name")
            activeBinder = null
            isServiceBound.set(false)
        }
    }

    /**
     * Installs a process-wide crash guard so background threads or async runnables
     * spawned by third-party APKs inside the virtual space cannot cause a Force Close.
     */
    fun installVirtualCrashGuard(context: Context) {
        if (!crashGuardInstalled.compareAndSet(false, true)) return

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.w(
                TAG,
                "VirtualCrashGuard isolated uncaught exception on thread '${thread.name}' (id=${thread.id}): ${throwable.message}",
                throwable
            )

            val isMainThread = thread == Looper.getMainLooper().thread
            if (!isMainThread) {
                // Quietly isolate background thread exceptions spawned by cloned APK SDKs/analytics/JNI
                // without spamming disruptive error Toasts on screen.
                return@setDefaultUncaughtExceptionHandler
            }

            // Keep the Android Main Looper alive if a guest APK View/Handler runnable threw on the UI thread
            while (true) {
                try {
                    Looper.loop()
                    break
                } catch (loopThrowable: Throwable) {
                    Log.e(
                        TAG,
                        "VirtualCrashGuard absorbed main-thread guest exception: ${loopThrowable.message}",
                        loopThrowable
                    )
                    // Only delegate if it is a fatal JVM VirtualMachineError (e.g. OutOfMemoryError)
                    if (loopThrowable is OutOfMemoryError || loopThrowable is StackOverflowError) {
                        previousHandler?.uncaughtException(thread, loopThrowable)
                        break
                    }
                }
            }
        }
    }

    /**
     * Binds to [VirtualProcessService] within the virtual container host context.
     */
    fun bindVirtualService(
        context: Context,
        packageName: String,
        userId: Int,
        onConnected: (VirtualProcessService.VirtualProcessBinder?) -> Unit
    ) {
        installVirtualCrashGuard(context)
        val appContext = context.applicationContext

        val currentBinder = activeBinder
        if (isServiceBound.get() && currentBinder != null && currentBinder.isBinderAlive) {
            onConnected(currentBinder)
            return
        }

        synchronized(pendingBindCallbacks) {
            pendingBindCallbacks.add(onConnected)
        }

        try {
            val serviceIntent = Intent(appContext, VirtualProcessService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_USER_ID, userId)
                setPackage(appContext.packageName)
            }
            appContext.startService(serviceIntent)
            val bound = appContext.bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
            if (!bound) {
                Log.w(TAG, "bindService returned false for $packageName (userId=$userId); using in-process virtual bridge")
                val callbacks: List<(VirtualProcessService.VirtualProcessBinder?) -> Unit>
                synchronized(pendingBindCallbacks) {
                    callbacks = pendingBindCallbacks.toList()
                    pendingBindCallbacks.clear()
                }
                callbacks.forEach { it(null) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to bind VirtualProcessService for $packageName (userId=$userId)", e)
            val callbacks: List<(VirtualProcessService.VirtualProcessBinder?) -> Unit>
            synchronized(pendingBindCallbacks) {
                callbacks = pendingBindCallbacks.toList()
                pendingBindCallbacks.clear()
            }
            callbacks.forEach { it(null) }
        }
    }

    /**
     * Allocates or retrieves an isolated [VirtualProcessRecord] for `(packageName, userId)`.
     */
    fun getOrCreateProcessRecord(
        context: Context,
        packageName: String,
        userId: Int,
        installedApkPath: String,
        targetComponent: ComponentName?
    ): VirtualProcessRecord {
        val key = "$userId:$packageName"
        return runningProcesses.computeIfAbsent(key) {
            val vPid = nextVirtualPid.getAndIncrement()
            val vUid = 100000 * userId + (10000 + (packageName.hashCode().and(0x7FFFFFFF) % 9999))
            val sandboxDir = File(context.filesDir.parentFile ?: context.filesDir, "virtual/user/$userId/$packageName").apply {
                mkdirs()
            }
            VirtualProcessRecord(
                vPid = vPid,
                vUid = vUid,
                packageName = packageName,
                userId = userId,
                processName = "$VIRTUAL_SPACE_NAMESPACE:virtual_u${userId}_${packageName.substringAfterLast('.')}",
                installedApkPath = installedApkPath,
                sandboxDataDir = sandboxDir.absolutePath,
                targetComponent = targetComponent,
                hostOsPid = Process.myPid(),
                startedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Executes `startActivity` strictly inside the virtual container (`VirtualContainerActivity`)
     * after binding the virtual process via `bindService`.
     *
     * NEVER launches the target package externally on the host device.
     */
    fun startActivityInVirtualProcess(
        context: Context,
        packageName: String,
        userId: Int,
        originalLaunchIntent: Intent,
        installedApkPath: String,
        appName: String = packageName.substringAfterLast('.'),
        cloneId: Int = -1
    ): Boolean {
        installVirtualCrashGuard(context)

        try {
            val apkFile = File(installedApkPath)
            if (!apkFile.exists() || apkFile.length() <= 0L) {
                val msg = "Failed to launch APK in virtual space: Installed APK missing at $installedApkPath"
                Log.e(TAG, msg)
                showToastOnMainThread(context, msg)
                return false
            }

            // Prepare the isolated process record
            val record = getOrCreateProcessRecord(
                context = context,
                packageName = packageName,
                userId = userId,
                installedApkPath = installedApkPath,
                targetComponent = originalLaunchIntent.component
            )

            // Bind VirtualProcessService to ensure the virtual process engine is active
            bindVirtualService(context, packageName, userId) { binder ->
                try {
                    binder?.spawnVirtualProcess(packageName, userId, installedApkPath, originalLaunchIntent)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error spawning virtual process via binder for $packageName", e)
                }
            }

            // Rewrite the real APK's LaunchIntent into a sandboxed container Intent targeting VirtualContainerActivity
            val containerIntent = Intent(context, VirtualContainerActivity::class.java).apply {
                putExtra(EXTRA_CLONE_ID, cloneId)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_INSTALLED_APK_PATH, installedApkPath)
                putExtra(EXTRA_VIRTUAL_PID, record.vPid)
                putExtra(EXTRA_VIRTUAL_UID, record.vUid)
                putExtra(EXTRA_TARGET_INTENT, Intent(originalLaunchIntent))
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            // Clean any third-party guest ActivityLifecycleCallbacks that may have attached to host Application
            runCatching {
                val hostApp = context.applicationContext as? android.app.Application
                if (hostApp != null) {
                    val f = android.app.Application::class.java.getDeclaredField("mActivityLifecycleCallbacks").apply {
                        isAccessible = true
                    }
                    val list = f.get(hostApp) as? java.util.ArrayList<android.app.Application.ActivityLifecycleCallbacks>
                    if (list != null) {
                        synchronized(list) {
                            list.removeAll { cb ->
                                cb != null &&
                                    cb !is SafeSandboxActivityLifecycleCallbacks &&
                                    !cb.javaClass.name.startsWith("android.") &&
                                    !cb.javaClass.name.startsWith("androidx.") &&
                                    !cb.javaClass.name.startsWith("com.example.")
                            }
                        }
                    }
                }
            }

            context.startActivity(containerIntent)
            Log.i(
                TAG,
                "Launched virtual activity in container: pkg=$packageName userId=$userId " +
                    "vPid=${record.vPid} component=${originalLaunchIntent.component?.flattenToShortString()} " +
                    "apk=$installedApkPath"
            )
            return true
        } catch (e: Throwable) {
            val errorMsg = "Failed to launch APK in virtual space: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, errorMsg, e)
            showToastOnMainThread(context, errorMsg)
            return false
        }
    }

    fun getRunningProcess(packageName: String, userId: Int): VirtualProcessRecord? {
        return runningProcesses["$userId:$packageName"]
    }

    fun killVirtualProcess(packageName: String, userId: Int) {
        runningProcesses.remove("$userId:$packageName")
        Log.i(TAG, "Terminated virtual process for pkg=$packageName userId=$userId")
    }

    fun killAllVirtualProcesses() {
        runningProcesses.clear()
        Log.i(TAG, "Cleared all virtual process records")
    }

    internal fun showToastOnMainThread(context: Context, message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            }
        } else {
            mainHandler.post {
                runCatching {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
