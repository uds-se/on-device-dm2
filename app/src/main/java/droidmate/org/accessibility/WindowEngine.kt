package droidmate.org.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import droidmate.org.accessibility.IEngine.Companion.TAG
import droidmate.org.accessibility.IEngine.Companion.debug
import droidmate.org.accessibility.IEngine.Companion.debugFetch
import droidmate.org.accessibility.extensions.getBounds
import droidmate.org.accessibility.extensions.invalid
import droidmate.org.accessibility.extensions.isHomeScreen
import droidmate.org.accessibility.extensions.visibleAxis
import droidmate.org.accessibility.parsing.DisplayDimension
import droidmate.org.accessibility.parsing.DisplayedWindow
import droidmate.org.accessibility.parsing.SiblingNodeComparator
import droidmate.org.accessibility.parsing.UiHierarchy
import droidmate.org.accessibility.utils.NodeProcessor
import droidmate.org.accessibility.utils.PostProcessor
import droidmate.org.accessibility.utils.debugEnabled
import droidmate.org.accessibility.utils.debugOut
import droidmate.org.accessibility.utils.debugT
import droidmate.org.accessibility.utils.processTopDown
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI

import java.lang.RuntimeException

class WindowEngine(
    private val uiHierarchy: UiHierarchy,
    private val screenshotEngine: IScreenshotEngine,
    private val keyboardEngine: IKeyboardEngine,
    private val service: AccessibilityService
): IWindowEngine {
    companion object {
        const val osPkg = "com.android.systemui"
    }
    private val wmService: WindowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var mLastDisplayDimension = DisplayDimension(0, 0)
    private var mLastWindows: List<DisplayedWindow> = emptyList()

    private val isInteractive =
        { w: UiElementPropertiesI -> w.clickable || w.longClickable || w.checked != null || w.isInputField }

    override val lastDisplayDimension: DisplayDimension
        get() = mLastDisplayDimension

    override val lastWindows: List<DisplayedWindow>
        get() = mLastWindows

    override var lastResponse: DeviceResponse = DeviceResponse.empty
    // Will be updated during the run, when the right command is sent (i.e. on AppLaunch)
    override var launchedMainActivity: String = ""

    override fun verifyCoordinate(x: Int, y: Int) {
        val dimension = getDisplayDimension()
        assert(x in 0 until dimension.width) { "Error on click coordinate invalid x:$x" }
        assert(y in 0 until dimension.height) { "Error on click coordinate invalid y:$y" }
    }

    override fun getDisplayRotation(): Int {
        debugOut("get display rotation", false)
        val rotation = wmService.defaultDisplay.rotation

        if (debugEnabled) {
            Log.d(TAG, "rotation is $rotation")
        }

        return rotation
    }

    override suspend fun getDisplayedWindows(): List<DisplayedWindow> {
        var windows: List<DisplayedWindow> = emptyList()
        var c = 0
        debugT("compute windows", {
            while (windows.invalid() && c++ < 10) {
                windows = computeDisplayedWindows()
                if (windows.invalid()) {
                    mLastWindows = emptyList()
                    delay(200)
                }
            }
        }, inMillis = true)

        if (windows.invalid()) {
            throw IllegalStateException("Error: Displayed Windows could not be extracted $windows")
        }

        return windows
    }

    // FIXME for the apps with interaction issues, check if we need different window types here
    override suspend fun getAppRootNodes() = getDisplayedWindows()
        .mapNotNull { if (it.isApp() || it.isKeyboard) it.rootNode else null }

    override suspend fun isKeyboardOpen(): Boolean = getDisplayedWindows()
        .any { it.isKeyboard }

    //FIXME for some reason this does not report the pixels of the system navigation bar in the bottom of the display
    private fun getDisplayDimension(): DisplayDimension {
        debugOut("get display dimension", false)
        val p = Point()
        wmService.defaultDisplay.getRealSize(p)

        if (debugEnabled) {
            Log.d(TAG, "dimensions are $p")
        }

        return DisplayDimension(p.x, p.y)
    }

    private suspend fun computeDisplayedWindows(): List<DisplayedWindow> {
        debugOut("compute displayCoordinates", false)
        // to compute which areas in the screen are not yet occupied by other windows (for UiElement-visibility)
        val displayDim = getDisplayDimension()
        // keep track of already processed windowIds to prevent re-processing when we have to
        // re-fetch windows due to missing accessibility roots
        val processedWindows = HashMap<Int, DisplayedWindow>()

        // visible windows in descending layer order
        var windows: MutableList<AccessibilityWindowInfo> = service.windows

        // Start with the active window, which seems to sometimes be missing from the list returned
        // by the UiAutomation.
        // this may fix issue where we sometimes cannot extract the state since no valid window is recognized
        val activeRoot = service.rootInActiveWindow

        if (activeRoot != null && windows.none { it.id == activeRoot.windowId }) {
            activeRoot.refresh()

            if (activeRoot.window != null)
                windows.add(0, activeRoot.window)
        }

        var count = 0
        while (count++ < 50 && windows
                .none { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root != null }
        ) {
            // wait until app/home window is available
            delay(10)
            windows = service.windows
        }
        // keyboards are always in the front
        windows.find { keyboardEngine.isKeyboard(it.root) }?.let { kw ->
            windows.remove(kw)
            windows.add(0, kw)
        }

        val uncoveredC = mutableListOf(Rect(0, 0, displayDim.width, displayDim.height))

        if (mLastDisplayDimension == displayDim) {
            // necessary since otherwise disappearing soft-keyboards would mark part of the app screen as invisible
            var canReuse = windows.size >= mLastWindows.size
            var c = 0
            while (canReuse && c < mLastWindows.size && c < windows.size) {
                with(mLastWindows[c]) {
                    val newW = windows[c++]
                    val cnd = canReuseFor(newW)

                    if (w.windowId == newW.id && !cnd) {
                        Log.d(TAG, "cannot reuse $this for ${newW.id}: ${newW.root?.packageName}")
                    }

                    if (cnd) {
                        Log.d(TAG, "can reuse window ${w.windowId} ${w.pkgName} ${w.boundaries}")
                        processedWindows[w.windowId] = this.apply { if (isExtracted()) rootNode = newW.root }
                    } else {
                        // no guarantees after we have one mismatching window
                        canReuse = false
                    }
                }
            }
            // wo could only partially reuse windows or none
            if (!canReuse) {
                if (processedWindows.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "partial reuse of windows ${processedWindows.entries.joinToString(separator = "  ") { (id, w) -> "$id: ${w.w.pkgName}" }}"
                    )
                }
                // then we need to mark the (reused) displayed window area as occupied
                processedWindows.values.forEach { it.bounds.visibleAxis(uncoveredC) }
            }
        }

        //FIXME why did we limit the number of windows here? Was were an infinite loop?
        count = 0
        while (count++ < 20 && (processedWindows.size < windows.size)) {
            var canContinue = true
            windows.forEach { window ->
                if (canContinue && !processedWindows.containsKey(window.id)) {
                    processWindows(window, uncoveredC)?.also {
                        debugOut("created window ${it.w.windowId} ${it.w.pkgName}")
                        processedWindows[it.w.windowId] = it
                    }
                        ?: let {
                            delay(10)
                            windows = service.windows
                            canContinue = false
                        }
                }
            }
            if (!canContinue) { // something went wrong in the window extraction => throw cached results away and try once again
                delay(100)
                Log.d(TAG, "window processing failed try once again")
                processedWindows.clear()
                windows = service.windows
                windows.forEach { window ->
                    processWindows(window, uncoveredC)?.also {
                        processedWindows[it.w.windowId] = it
                    }
                        ?: let {
                            Log.e(
                                TAG,
                                "window ${window.id}: ${window.root?.packageName} ${window.title}"
                            )
                        }
                }
            }
        }

        if (processedWindows.size < windows.size) {
            Log.e(
                TAG,
                "ERROR could not get rootNode for all windows[" +
                        "#dw=${processedWindows.size}, " +
                        "#w=${windows.size}] " +
                        "${getWindowRootNodes().mapNotNull { it.packageName }}"
            )
        }
        return processedWindows.values.toList()
            .also { displayedWindows ->
                mLastDisplayDimension = displayDim // store results to be potentially reused
                mLastWindows = displayedWindows
                debugOut(
                    "-- done displayed window computation [#windows = ${displayedWindows.size}] ${displayedWindows.joinToString(
                        separator = "\t "
                    ) { "${it.w.windowId}:(${it.layer})${it.w.pkgName}[${it.w.boundaries}] isK=${it.isKeyboard} isL=${it.isLauncher} isE=${it.isExtracted()} ${it.initialArea}" }}"
                )
            }
    }

    private suspend fun processWindows(
        window: AccessibilityWindowInfo,
        uncoveredC: MutableList<Rect>
    ): DisplayedWindow? {
        debugOut("process ${window.id}", false)
        var outRect = Rect()
        // REMARK we wait that the app AND keyboard root nodes are available for synchronization reasons
        // otherwise we may extract an app widget as definedAsVisible which would have been hidden behind the input window
        if (window.root == null &&
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        ) {
            val deviceRoots = getWindowRootNodes()
            val root = deviceRoots.find { it.windowId == window.id }

            // this is usually the case for input methods (i.e. the keyboard window)
            if (root != null) {
                root.getBoundsInScreen(outRect)
                // this is necessary since newly appearing keyboards may otherwise take the whole screen and
                // thus screw up our visibility analysis
                if (keyboardEngine.isKeyboard(root)) {
                    uncoveredC.firstOrNull()?.let { r ->
                        outRect.intersect(r)
                        if (outRect == r) {  // wrong keyboard boundaries reported
                            Log.d(TAG, "try to handle soft keyboard in front with $outRect")
                            uiHierarchy.findAndPerform(listOf(root),
                                keyboardEngine.selectKeyboardRoot(r.top + 1, r.width(), r.height()),
                                retry = false,
                                action = { node ->
                                    outRect = node.getBounds(r.width(), r.height())
                                    true
                                }
                            )
                        }
                    }
                }
                Log.d(
                    TAG,
                    "use device root for ${window.id} ${root.packageName}[$outRect] uncovered = $uncoveredC ${window.type}"
                )
                return DisplayedWindow(window, uncoveredC, outRect, keyboardEngine.isKeyboard(root), root)
            }
            Log.w(
                TAG,
                "warn no root for ${window.id} ${deviceRoots.map { "${it.packageName}" + " wId=${it.window?.id}" }}"
            )
            return null
        }
        window.getBoundsInScreen(outRect)
        if (outRect.isEmpty && window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            Log.w(TAG, "warn empty application window")
            return null
        }
        debugOut(
            "process window ${window.id} ${window.root?.packageName ?: "no ROOT!! type=${window.type}"}",
            debug
        )
        return DisplayedWindow(window, uncoveredC, outRect, keyboardEngine.isKeyboard(window.root))
    }

    override suspend fun fetchDeviceData(afterAction: Boolean): DeviceResponse {
        return coroutineScope {
            debugOut("start fetch execution", debugFetch)
            // waitForSync(env,afterAction)

            var windows = getDisplayedWindows()
            var isSuccessful = true

            // fetch the screenshot if available
            // could maybe use Espresso View.DecorativeView to fetch screenshot instead
            var img = screenshotEngine.takeScreenshot()

            debugOut("start element extraction", debugFetch)

            // we want the ui fetch first as it is fast but will likely solve synchronization issues
            val uiElements = uiHierarchy.fetch(windows, img)
                .let {
                    if (it == null || (!windows.isHomeScreen() && it.none(isInteractive))) {
                        //retry once for the case that AccessibilityNode tree was not yet stable
                        Log.w(
                            TAG, "first ui extraction failed or no interactive elements were found " +
                                    "\n $it, \n ---> start a second try"
                        )
                        windows = getDisplayedWindows()
                        img = screenshotEngine.takeScreenshot()
                        val secondRes = uiHierarchy.fetch(windows, img)
                        Log.d(TAG, "second try resulted in ${secondRes?.size} elements")
                        secondRes
                    } else {
                        it
                    }
                } ?: emptyList<UiElementPropertiesI>()
                .also {
                    isSuccessful = false
                    Log.e(TAG, "could not parse current UI screen ( $windows )")
                    throw RuntimeException("UI extraction failed for windows: $windows")
                }

//	Log.d(logTag, "uiHierarchy = $uiHierarchy")
            debugOut("INTERACTIVE Element in UI = ${uiElements.any(isInteractive)}")

//			val xmlDump = UiHierarchy.getXml(device)
            val appWindows = windows.filter { it.isExtracted() && !it.isKeyboard }
            val focusedWindow = appWindows.firstOrNull { it.w.hasFocus || it.w.hasInputFocus }
                ?: appWindows.firstOrNull()

            val focusedAppPkg = focusedWindow?.w?.pkgName ?: "no AppWindow detected"
            debugOut(
                "determined focused window $focusedAppPkg " +
                        "inputF=${focusedWindow?.w?.hasInputFocus}, focus=${focusedWindow?.w?.hasFocus}"
            )

            debugOut("started async ui extraction", debugFetch)

            debugOut("compute img pixels", debugFetch)
            val imgPixels = screenshotEngine.getOrStoreImgPixels(img)

            var xml: String =
                "TODO parse widget list on Pc if we need the XML or introduce a debug property to enable parsing" +
                        ", because (currently) we would have to traverse the tree a second time"
            if (debugEnabled) {
                xml = uiHierarchy.getXml(this@WindowEngine)
            }

            lastResponse = DeviceResponse.create(
                isSuccessful = isSuccessful,
                uiHierarchy = uiElements,
                uiDump = xml,
                launchedActivity = launchedMainActivity,
                capturedScreen = img != null,
                screenshot = imgPixels,
                appWindows = windows.mapNotNull { if (it.isExtracted()) it.w else null },
                isHomeScreen = windows.isHomeScreen()
            )

            lastResponse
        }
    }

    fun getNonSystemRootNodes():List<AccessibilityNodeInfo> = getWindowRootNodes()
        .filterNot { it.packageName == osPkg }

    private fun getWindowRootNodes(): List<AccessibilityNodeInfo> = getWindowRoots()

    /** Returns a list containing the root [AccessibilityNodeInfo]s for each active window  */
    private fun getWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableSetOf<AccessibilityNodeInfo>()
        // Start with the active window, which seems to sometimes be missing from the list returned
        // by the UiAutomation.
        if (service.rootInActiveWindow != null) {
            roots.add(service.rootInActiveWindow)
        }
        // Support multi-window searches for API level 21 and up.
        for (window in service.windows) {
            val root = window.root
            if (root == null) {
                Log.w(TAG, "Skipping null root node for window: $window")
                continue
            }
            roots.add(root)
        }
        return roots.toList()
    }

    override suspend fun<T> exec(processor: NodeProcessor, postProcessor: PostProcessor<T>): List<T> =
        getNonSystemRootNodes().mapIndexed { _, root: AccessibilityNodeInfo ->
            processTopDown(root, processor = processor, postProcessor = postProcessor)
        }

    override suspend fun exec(processor: NodeProcessor) {
        try {
            getNonSystemRootNodes().mapIndexed { _, root: AccessibilityNodeInfo ->
                processTopDown(root, processor = processor, postProcessor = { Unit })
            }
        } catch (e: Exception) {
            Log.w("droidmate/UiDevice", "error while processing AccessibilityNode tree ${e.localizedMessage}")
        }
    }

}