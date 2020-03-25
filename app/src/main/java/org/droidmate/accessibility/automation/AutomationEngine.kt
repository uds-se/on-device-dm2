package org.droidmate.accessibility.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.droidmate.accessibility.automation.IEngine.Companion.TAG
import org.droidmate.accessibility.automation.IEngine.Companion.debug
import org.droidmate.accessibility.automation.IEngine.Companion.debugFetch
import org.droidmate.accessibility.automation.parsing.SelectorCondition
import org.droidmate.accessibility.automation.parsing.UiHierarchy
import org.droidmate.accessibility.automation.parsing.UiParser
import org.droidmate.accessibility.automation.parsing.UiSelector
import org.droidmate.accessibility.automation.screenshot.IScreenshotEngine
import org.droidmate.accessibility.automation.screenshot.ScreenRecorder
import org.droidmate.accessibility.automation.utils.api
import org.droidmate.accessibility.automation.utils.debugEnabled
import org.droidmate.accessibility.automation.utils.debugOut
import org.droidmate.accessibility.automation.utils.debugT
import org.droidmate.accessibility.automation.utils.measurePerformance
import org.droidmate.accessibility.exploration.OnDeviceApk
import org.droidmate.accessibility.exploration.OnDeviceConfigurationBuilder
import org.droidmate.accessibility.exploration.OnDeviceExploration
import org.droidmate.command.ExploreCommandBuilder
import org.droidmate.deviceInterface.exploration.Click
import org.droidmate.deviceInterface.exploration.ClickEvent
import org.droidmate.deviceInterface.exploration.Direction
import org.droidmate.deviceInterface.exploration.LongClick
import org.droidmate.deviceInterface.exploration.LongClickEvent
import org.droidmate.deviceInterface.exploration.Scroll
import org.droidmate.deviceInterface.exploration.TextInsert
import org.droidmate.deviceInterface.exploration.Tick
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.explorationModel.factory.DefaultModelProvider

open class AutomationEngine(
    private val notificationChannel: Channel<Long>,
    private val service: TestService,
    /*val idleTimeout: Long = 100,*/
    private val interactiveTimeout: Long = 1000,
    enablePrintouts: Boolean = true,
    private val context: Context = service.applicationContext,
    private val uiHierarchy: UiHierarchy = UiHierarchy(),
    private val screenshotEngine: IScreenshotEngine = ScreenRecorder.get(),
    private val keyboardEngine: IKeyboardEngine = KeyboardEngine(
        context
    ),
    private val windowEngine: IWindowEngine = WindowEngine(
        uiHierarchy,
        screenshotEngine,
        keyboardEngine,
        service
    )
) : IEngine,
    IKeyboardEngine by keyboardEngine,
    IWindowEngine by windowEngine,
    IScreenshotEngine,
    CoroutineScope {
    companion object {
        var targetPackage = ""
        val screenshotPermissionChannel = Channel<Intent>()
    }

    var canceled = false

    private val cfg by lazy {
        OnDeviceConfigurationBuilder().build(emptyArray())
    }

    private val exploration by lazy {
        val builder = ExploreCommandBuilder.fromConfig(cfg)

        OnDeviceExploration(
            this,
            cfg = cfg,
            strategyProvider = ExplorationStrategyPool(
                builder.strategies,
                builder.selectors
            ),
            modelProvider = DefaultModelProvider()
        )
    }

    private val random = Random(0)

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    init {
        // setting logcat debug/performance prints according to specified DM-2 configuration
        debugEnabled = enablePrintouts
        measurePerformance = measurePerformance && enablePrintouts
        debugOut("initialize environment", debug)
    }

    fun run() = launch {
        val apk = OnDeviceApk(targetPackage)
        setupDevice()
        exploration.setup(apk)

        supervisorScope {
            while (!canceled) {
                Log.d(TAG, "Continuing loop, waiting for idle")
                waitForIdle()
                Log.d(TAG, "Idle, acting")
                canceled = exploration.explorationLoop(apk)
                Log.d(TAG, "Acted, repeating loop")
            }

            exploration.onFinished()
            exploration.getExplorationResult()
        }
    }

    /*
    private suspend fun act(actionNr: Int) {
        val displayedWindows = getDisplayedWindows()
        if (displayedWindows.none {
                it.w.pkgName.contains(targetPackage)
            }) {
            launchApp(targetPackage, 500)
        } else {
            Log.d("droidmate/performance", "Here")
            debugT("----------Act($actionNr)", {
                val deviceData = debugT("fetch device data", {
                    windowEngine.fetchDeviceData(actionNr)
                }, inMillis = true)
                val widgets = deviceData.widgets
                val target = widgets
                    .filter { it.clickable }
                    .random(random)
                Log.w(TAG, "target: $target")
                debugT("click event", {
                    clickEvent(ClickEvent(target.idHash))
                }, inMillis = true)
                // longClickEvent(LongClickEvent(target.idHash))
                // minimizeMaximize()
                // tick(Tick(target.idHash,
                //    target.boundaries.center.first,
                //    target.boundaries.center.second))
                // pressBack()
                // pressHome()
                // pressEnter()

                /*val appWindow = displayedWindows.first {
                it.w.pkgName.contains("ch.bailu.aat")
            }
            val x = random.nextInt(appWindow.bounds.width())
            val y = random.nextInt(appWindow.bounds.height())
            click(Click(x, y))*/
            }, inMillis = true)
        }
    }
    */

    suspend fun waitForIdle() {
        notificationChannel.receive()
    }

    private val activeAppPackage: String
        get() = service.rootInActiveWindow.packageName.toString()

    private fun setupDevice() {
        try {
            // wake up the device in order to have (non-black) screenshots
            sendKeyEvent(KeyEvent.KEYCODE_WAKEUP)
            // Orientation is set initially to natural, however can be changed by action
// 			device.setOrientationNatural()
// 			device.freezeRotation()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    suspend fun launchApp(appPackageName: String, launchActivityDelay: Long): Boolean {
        Log.i(TAG, "Launching app $appPackageName and waiting $launchActivityDelay ms for it to start")
        var success = false
        // Launch the app
        val intent = context.packageManager
            .getLaunchIntentForPackage(appPackageName)

        // Clear out any previous instances
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        // Update environment
        launchedMainActivity = try {
            intent?.component?.className ?: ""
        } catch (e: IllegalStateException) {
            ""
        }
        debugOut("determined launch-able main activity for pkg=$launchedMainActivity", debugFetch)

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

    suspend fun minimizeMaximize(): Boolean {
        val currentPackage = activeAppPackage
        Log.d(TAG, "Minimizing and maximizing current package $currentPackage")

        pressRecentApps()
        // Cannot use wait for changes because it crashes UIAutomator
        // delay(100) // avoid idle 0 which get the wait stuck for multiple seconds
        debugT("waitForIdle", { waitForIdle() }, inMillis = true)

        for (i in (0 until 10)) {
            pressRecentApps()

            // Cannot use wait for changes because it waits some interact-able element
            // delay(100) // avoid idle 0 which get the wait stuck for multiple seconds
            debugT("waitForIdle", { waitForIdle() }, inMillis = true)

            Log.d(TAG, "Current package name $activeAppPackage")
            if (activeAppPackage == currentPackage)
                break
        }

        return activeAppPackage == currentPackage
    }

    private val idMatch: (Int) -> SelectorCondition = { idHash ->
        { n: AccessibilityNodeInfo, xPath ->
            val layer = windowEngine.lastWindows
                .find { it.w.windowId == n.windowId }?.layer
                ?: n.window?.layer
            layer != null && idHash == UiParser.computeIdHash(xPath, layer)
        }
    }

    fun coordinateClick(x: Int, y: Int, duration: Long = 500) {
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

    suspend fun click(action: Click): Boolean {
        if (api < Build.VERSION_CODES.N) {
            return false
        }
        windowEngine.verifyCoordinate(action.x, action.y)
        coordinateClick(action.x, action.y)
        delay(action.delay)
        return true
    }

    suspend fun clickEvent(action: ClickEvent): Boolean {
        // do this for API Level above 19 (exclusive)
        val success = uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) { nodeInfo ->
            // Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // wait for display update
        if (success) {
            delay(action.delay)
        }
        Log.d(TAG, "perform successful=$success")
        return success
    }

    suspend fun insertText(action: TextInsert): Boolean {
        return uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) { nodeInfo ->
            if (nodeInfo.isFocusable) {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                Log.d(TAG, "focus input-field")
            } else if (nodeInfo.isClickable) {
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "click non-focusable input-field")
            }
            // do this for API Level above 19 (exclusive)
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                action.text
            )
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                .also {
                    Log.d(TAG, "perform successful=$it")
                    // when doing multiple action sending enter may trigger a continue
                    // button but not all elements are yet filled
                    if (action.sendEnter && !isWithinQueue) {
                        Log.d(TAG, "trigger enter")
                        pressEnter()
                    }
                    if (nodeInfo.isFocusable) {
                        nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                    }
                    delay(action.delay)
                }
        }
    }

    suspend fun scroll(action: Scroll): Boolean {
        // do this for API Level above 19 (exclusive)
        val success = uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) { nodeInfo ->
            // Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
            when (action.direction) {
                Direction.UP -> {
                    nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)
                }
                Direction.DOWN -> {
                    nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
                }
                Direction.LEFT -> {
                    nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id)
                }
                else -> {
                    nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id)
                }
            }
        }

        // wait for display update
        Log.d(TAG, "perform successful=$success")
        return success
    }

    suspend fun tick(action: Tick): Boolean {
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

    suspend fun longClick(action: LongClick): Boolean {
        if (api < Build.VERSION_CODES.N) {
            return false
        }
        windowEngine.verifyCoordinate(action.x, action.y)
        coordinateClick(action.x, action.y, 1000)
        delay(action.delay)
        return true
    }

    suspend fun longClickEvent(action: LongClickEvent): Boolean {
        // do this for API Level above 19 (exclusive)
        val success = uiHierarchy.findAndPerform(windowEngine, idMatch(action.idHash)) { nodeInfo ->
            // Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
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

    fun pressHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun pressBack() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun pressEnter() {
        sendKeyEvent(KeyEvent.KEYCODE_ENTER)
    }

    fun sendKeyEvent(keyCode: Int) {
        try {
            val keyCommand = "input keyevent $keyCode"
            val runtime = Runtime.getRuntime()
            val shellProcess = runtime.exec(keyCommand)
            shellProcess.waitFor()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to send key event $keyCode", e)
        }
    }

    fun enableWifi() {
        val wfm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val success = wfm.setWifiEnabled(true)

        if (!success) {
            Log.w(TAG, "Failed to ensure WiFi is enabled!")
        }
    }

    override fun takeScreenshot(actionNr: Int): Bitmap? {
        return screenshotEngine.takeScreenshot(actionNr)
    }

    override fun getOrStoreImgPixels(bm: Bitmap?): ByteArray {
        return screenshotEngine.getOrStoreImgPixels(bm)
    }

    override fun getOrStoreImgPixels(bm: Bitmap?, actionId: Int): ByteArray {
        return screenshotEngine.getOrStoreImgPixels(bm, actionId)
    }

    fun terminate() {
        canceled = true
        targetPackage = ""
    }
}
