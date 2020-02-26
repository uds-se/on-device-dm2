package droidmate.org.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.IEngine.Companion.TAG
import droidmate.org.accessibility.IEngine.Companion.debug
import droidmate.org.accessibility.IEngine.Companion.debugFetch
import droidmate.org.accessibility.parsing.SelectorCondition
import droidmate.org.accessibility.parsing.UiHierarchy
import droidmate.org.accessibility.parsing.UiParser
import droidmate.org.accessibility.parsing.UiSelector
import droidmate.org.accessibility.utils.api
import droidmate.org.accessibility.utils.debugEnabled
import droidmate.org.accessibility.utils.debugOut
import droidmate.org.accessibility.utils.measurePerformance
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.droidmate.deviceInterface.exploration.*
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.system.measureTimeMillis


open class AutomationEngine(
    private val notificationChannel: Channel<Long>,
    private val service: TestService,
    /*val idleTimeout: Long = 100,*/
    private val interactiveTimeout: Long = 1000,
    imgQuality: Int = 10,
    delayedImgTransfer: Boolean = false,
    enablePrintouts: Boolean = true,
    private val context: Context = service.applicationContext,
    private val uiHierarchy: UiHierarchy = UiHierarchy(),
    private val screenshotEngine: IScreenshotEngine = ScreenshotEngine(imgQuality, delayedImgTransfer),
    private val keyboardEngine: IKeyboardEngine = KeyboardEngine(context),
    private val windowEngine: IWindowEngine = WindowEngine(
        uiHierarchy,
        screenshotEngine,
        keyboardEngine,
        service
    )
) : IEngine,
    IKeyboardEngine by keyboardEngine,
    IWindowEngine by windowEngine,
    IScreenshotEngine by screenshotEngine,
    CoroutineScope {
    var canceled = false

    private val random = Random(0)

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    init {
        // setting logcat debug/performance prints according to specified DM-2 configuration
        debugEnabled = enablePrintouts
        measurePerformance = measurePerformance && enablePrintouts
        debugOut("initialize environment", debug)

        if (delayedImgTransfer && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "warn we have no storage permission, we may not be able to store & fetch screenshots")
        }
    }

    fun run() = launch {
        Log.e(TAG, "Launching")
        setupDevice()
        Log.e(TAG, "Device setup")

        while (!canceled) {
            Log.e(TAG, "Continuing loop, waiting for idle")
            waitForIdle()
            Log.e(TAG, "Idle, acting")
            act()
            Log.e(TAG, "Acted, repeating loop")
        }
    }

    private suspend fun act() {
        val lastRotation = lastDisplayDimension
        Log.d(TAG, lastRotation.toString())
        val displayRotation = getDisplayRotation()
        Log.w(TAG, displayRotation.toString())
        val displayedWindows = getDisplayedWindows()
        Log.w(TAG, displayedWindows
            .joinToString { it.toString() })
        val rootNodes = getAppRootNodes()
        Log.w(TAG, rootNodes
            .joinToString { it.toString() })

        if (displayedWindows.none {
                it.w.pkgName.contains("ch.bailu.aat")
            }) {
            launchApp("ch.bailu.aat", 500)
        } else {
            val deviceData = windowEngine.fetchDeviceData()
            val widgets = deviceData.widgets
            val target = widgets
                .filter { it.clickable }
                .random(random)
            Log.w(TAG, "target: $target")
            //clickEvent(ClickEvent(target.idHash))
            //longClickEvent(LongClickEvent(target.idHash))
            //minimizeMaximize()
            //tick(Tick(target.idHash,
            //    target.boundaries.center.first,
            //    target.boundaries.center.second))
            //pressBack()
            //pressHome()
            pressEnter()

            /*val appWindow = displayedWindows.first {
                it.w.pkgName.contains("ch.bailu.aat")
            }
            val x = random.nextInt(appWindow.bounds.width())
            val y = random.nextInt(appWindow.bounds.height())
            click(Click(x, y))*/
        }
    }

    private suspend fun waitForIdle() {
        notificationChannel.receive()
    }

    private val activeAppPackage: String
        get() = service.rootInActiveWindow.packageName.toString()

    private fun setupDevice() {
        try {
            // wake up the device in order to have (non-black) screenshots
            sendKeyEvent(KeyEvent.KEYCODE_WAKEUP)
            // Orientation is set initially to natural, however can be changed by action
//			device.setOrientationNatural()
//			device.freezeRotation()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private suspend fun launchApp(appPackageName: String, launchActivityDelay: Long): Boolean {
        Log.i(TAG, "Launching app $appPackageName and waiting $launchActivityDelay ms for it to start")
        var success = false
        // Launch the app
        val intent = context.packageManager
            .getLaunchIntentForPackage(appPackageName)

        // Clear out any previous instances
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)

        // Update environment
        launchedMainActivity = try {
            intent?.component?.className ?: ""
        } catch (e: IllegalStateException) {
            ""
        }
        debugOut("determined launch-able main activity for pkg=${launchedMainActivity}", debugFetch)

        val loadTime = measureTimeMillis {
            context.startActivity(intent)

            // Wait for the app to appear
//            wait(Until.hasObject(By.pkg(appPackageName).depth(0)), waitTime)

            delay(launchActivityDelay)
            success = uiHierarchy.waitFor(this, interactiveTimeout, UiSelector.actableAppElem)

            // mute audio after app launch (for very annoying apps we may need a contentObserver listening on audio setting changes)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
        }

        Log.d(TAG, "TIME: load-time $loadTime millis")
        return success
    }

    private suspend fun minimizeMaximize() {
        val currentPackage = activeAppPackage
        Log.d(TAG, "Minimizing and maximizing current package $currentPackage")

        pressRecentApps()
        // Cannot use wait for changes because it crashes UIAutomator
        //delay(100) // avoid idle 0 which get the wait stuck for multiple seconds
        measureTimeMillis { waitForIdle() }
            .let { Log.d(TAG, "waited $it millis for IDLE") }

        for (i in (0 until 10)) {
            pressRecentApps()

            // Cannot use wait for changes because it waits some interact-able element
            //delay(100) // avoid idle 0 which get the wait stuck for multiple seconds
            measureTimeMillis { waitForIdle() }
                .let { Log.d(TAG, "waited $it millis for IDLE") }

            Log.d(TAG, "Current package name $activeAppPackage")
            if (activeAppPackage == currentPackage)
                break
        }
    }

    private val idMatch: (Int) -> SelectorCondition = { idHash ->
        { n: AccessibilityNodeInfo, xPath ->
            val layer = windowEngine.lastWindows
                .find { it.w.windowId == n.windowId }?.layer
                ?: n.window?.layer
            layer != null && idHash == UiParser.computeIdHash(xPath, layer)
        }
    }

    private fun coordinateClick(x: Int, y: Int, duration: Long = 500) {
        Log.i(TAG, "Clicking on coordinate ($x, $y) with a duration of $duration ms")
        if (api < Build.VERSION_CODES.N) {
            return
        }

        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val builder = GestureDescription.Builder()
        val gestureDescription = builder
            .addStroke(GestureDescription.StrokeDescription(path, 10, duration))
            .build()
        service.dispatchGesture(gestureDescription, null, null)
    }

    private suspend fun click(action: Click): Boolean {
        if (api < Build.VERSION_CODES.N) {
            return false
        }
        windowEngine.verifyCoordinate(action.x, action.y)
        coordinateClick(action.x, action.y)
        delay(action.delay)
        return true
    }

    private suspend fun clickEvent(action: ClickEvent): Boolean {
        // do this for API Level above 19 (exclusive)
        val success = uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) { nodeInfo ->
            //	Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // wait for display update
        if (success) {
            delay(action.delay)
        }
        Log.d(TAG, "perform successful=$success")
        return success
    }

    private suspend fun tick(action: Tick): Boolean {
        val success = uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) {
            val newStatus = !it.isChecked
            it.isChecked = newStatus
            it.isChecked == newStatus
        }
        if (!success) {
            windowEngine.verifyCoordinate(action.x, action.y)
            coordinateClick(action.x, action.y)
        }
        delay(action.delay)
        return success
    }

    private suspend fun longClick(action: LongClick): Boolean {
        if (api < Build.VERSION_CODES.N) {
            return false
        }
        windowEngine.verifyCoordinate(action.x, action.y)
        coordinateClick(action.x, action.y, 1000)
        delay(action.delay)
        return true
    }

    private suspend fun longClickEvent(action: LongClickEvent): Boolean {
        // do this for API Level above 19 (exclusive)
        val success = uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) { nodeInfo ->
            //	Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }

        // wait for display update
        if (success) {
            delay(action.delay)
        }
        Log.d(TAG, "perform successful=$success")
        return success
    }

    private fun pressRecentApps() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    private fun pressHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun pressBack() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun pressEnter() {
        sendKeyEvent(KeyEvent.KEYCODE_ENTER)
    }

    private fun sendKeyEvent(keyCode: Int) {
        try {
            val keyCommand = "input keyevent $keyCode"
            val runtime = Runtime.getRuntime()
            val shellProcess = runtime.exec(keyCommand)
            shellProcess.waitFor()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to send key event $keyCode", e)
        }
    }

    private fun enableWifi() {
        val wfm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val success = wfm.setWifiEnabled(true)

        if (!success) {
            Log.w(TAG, "Failed to ensure WiFi is enabled!")
        }
    }

    suspend fun execute(action: ExplorationAction): Any {
        return false
    }
}