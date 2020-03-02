package droidmate.org.accessibility.automation.parsing

import android.graphics.Bitmap
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.automation.AutomationEngine
import droidmate.org.accessibility.automation.IWindowEngine
import droidmate.org.accessibility.automation.extensions.isValid
import droidmate.org.accessibility.automation.utils.NodeProcessor
import droidmate.org.accessibility.automation.utils.addAttribute
import droidmate.org.accessibility.automation.utils.debugOut
import droidmate.org.accessibility.automation.utils.debugT
import droidmate.org.accessibility.automation.utils.processTopDown
import droidmate.org.accessibility.automation.utils.visibleOuterBounds
import java.io.StringWriter
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.droidmate.deviceInterface.communication.UiElementProperties
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI

@Suppress("unused")
class UiHierarchy : UiParser() {
    private var nActions = 0
    private var ut = 0L
    suspend fun fetch(windows: List<DisplayedWindow>, img: Bitmap?): List<UiElementPropertiesI>? {
        val nodes = mutableListOf<UiElementProperties>()

        try {
            // we cannot use an error prone image for ui extraction -> rather work without it completely
            val validImg = img.isValid(windows)
            // TODO check if this filters out all os windows but keeps permission request dialogues
// 			debugOut("windows to extract: ${windows.map { "${it.isExtracted()}-${it.w.pkgName}:${it.w.windowId}[${visibleOuterBounds(it.area)}]" }}")
            Log.d(TAG, "current screen contains ${windows.size} app windows $windows")
            windows.forEach { w: DisplayedWindow ->
                // for now we are not interested in the Launcher elements
                if (w.isExtracted() && !w.isLauncher) {
                    w.area = w.initialArea.toMutableList()

                    if (w.rootNode == null) {
                        Log.w(TAG, "ERROR root should not be null (window=$w)")
                    }
                    check(w.rootNode != null) { "if extraction is enabled we have to have a rootNode" }
                    debugT("create bottom up", {
                        createBottomUp(
                            w,
                            w.rootNode!!,
                            parentXpath = "//",
                            nodes = nodes,
                            img = if (validImg) img else null
                        )
                        Log.d(
                            TAG,
                            "${w.w.pkgName}:${w.w.windowId} ${visibleOuterBounds(w.initialArea)} " +
                                    "#elems = ${nodes.size} ${w.initialArea} empty=${w.initialArea.isEmpty()}"
                        )
                    }, inMillis = true)
                }
            }
        } catch (e: Exception) {
            // the accessibilityNode service may throw this if the node is no longer up-to-date
            Log.e(
                "droidmate/UiDevice",
                "error while fetching widgets ${e.localizedMessage}\n last widget was ${nodes.lastOrNull()}",
                e
            )
            return null
        }
        return nodes
    }

    suspend fun getXml(engine: IWindowEngine): String = debugT(" fetching gui Dump ", {
        // val device = engine.service
        StringWriter().use { out ->
            // device.waitForIdle()

            val ser = Xml.newSerializer()
            ser.setOutput(out) // , "UTF-8")
            ser.startDocument("UTF-8", true)
            ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

            ser.startTag("", "hierarchy")
            ser.attribute("", "rotation", engine.getDisplayRotation().toString())

            ser.startTag("", "windows")
            engine.lastWindows.forEach {
                ser.startTag("", "window")
                ser.addAttribute("windowId", it.w.windowId)
                ser.addAttribute("windowLayer", it.layer)
                ser.addAttribute("isExtracted", it.isExtracted())
                ser.addAttribute("isKeyboard", it.isKeyboard)
                ser.addAttribute("windowType", it.windowType)
                ser.addAttribute("boundaries", it.bounds)
                ser.endTag("", "window")
            }
            ser.endTag("", "windows")

            // need to set the ending tag in the post processor to have a proper 'tree' representation
            engine.exec(
                nodeDumper(
                    ser,
                    engine.lastDisplayDimension.width,
                    engine.lastDisplayDimension.height
                )
            ) {
                ser.endTag("", "node")
            }

            ser.endTag("", "hierarchy")
            ser.endDocument()
            ser.flush()
            out.toString()
        }
    }, inMillis = true)

    /**
     * Check if this node fulfills the given condition and recursively check descendents if not
     * **/
    suspend fun any(
        engine: IWindowEngine,
        retry: Boolean = false,
        cond: SelectorCondition
    ): Boolean {
        return findAndPerform(
            engine,
            cond,
            retry
        ) { true }
    }

    @JvmOverloads
    suspend fun findAndPerform(
        engine: IWindowEngine,
        cond: SelectorCondition,
        retry: Boolean = true,
        action: suspend (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        return findAndPerform(engine.getAppRootNodes(), cond, retry, action)
    }

    /**
     * Looks for a UiElement fulfilling [cond] and executes [action] on it.
     * The search condition should be unique to avoid unwanted side-effects on other nodes which
     * fulfill the same condition.
     */
    @JvmOverloads
    suspend fun findAndPerform(
        roots: List<AccessibilityNodeInfo>,
        cond: SelectorCondition,
        retry: Boolean = true,
        action: suspend (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        var found = false
        var successful = false

        debugOut("called findAndPerform (which will process the accessibility node tree until condition)")

        val processor: NodeProcessor = { node, _, xPath ->
            when {
                found -> false // we already found our target and performed our action -> stop searching
// 				!isActive -> {Log.w(TAG,"process became inactive"); false} //TODO this is still experimental
                !node.isVisibleToUser -> {
// 					Log.d(TAG,"node $xPath is invisible")
                    false
                }
                !node.refresh() -> {
                    Log.w(TAG, "refresh on node $xPath failed"); false
                }
                // do not traverse deeper
                else -> {
                    found = cond(node, xPath)
                        .also { isFound ->
                            if (isFound) {
                                successful = action(node).run {
                                    if (retry && !this) {
                                        Log.d(
                                            TAG,
                                            "action failed on $node\n with id ${computeIdHash(
                                                xPath,
                                                node.window.layer
                                            )}, try a second time"
                                        )
                                        delay(20)
                                        action(node)
                                    } else {
                                        this
                                    }
                                }.also {
                                    Log.d(TAG, "action returned $it")
                                }
                            }
                        }
                    !found // continue if condition is not fulfilled yet
                }
            }
        }
// 			Log.d(TAG, "roots are ${roots.map { it.packageName }}")
        roots.forEach { root ->
            // 		Log.d(TAG, "search in root $root with ${root.childCount} children")
            // only continue search if element is not yet found in any previous root (otherwise we would overwrite the result accidentally)
            if (!found) {
                processTopDown(root, processor = processor, postProcessor = { Unit })
            }
        }
        if (retry && !found) {
            Log.d(TAG, "didn't find target, try a second time")
            delay(20)
            roots.forEach { root ->
                if (!found) {
                    processTopDown(root, processor = processor, postProcessor = { Unit })
                }
            }
        }
        Log.d(TAG, "found = $found")
        return found && successful
    }

    /**
     * @param timeout amount of milliseconds, maximal spend to wait for condition [cond] to become true (default 10s)
     * @return if the condition was fulfilled within timeout
     * */
    @JvmOverloads
    suspend fun waitFor(
        env: AutomationEngine,
        timeout: Long = 10000,
        cond: SelectorCondition
    ): Boolean {
        return waitFor(env, timeout, 10, cond)
    }

    /**
     * @param pollTime time interval (in ms) to recheck the condition [cond]
     * */
    suspend fun waitFor(
        env: AutomationEngine,
        timeout: Long,
        pollTime: Long,
        cond: SelectorCondition
    ): Boolean =
        coroutineScope {
            // lookup should only take less than 100ms (avg 50-80ms) if the UiAutomator did not screw up
            val scanTimeout =
                100 // this is the maximal number of milliseconds, which is spend for each lookup in the hierarchy
            var time = 0.0
            var found = false

            while (!found && time < timeout) {
                measureTimeMillis {
                    with(async {
                        any(
                            env,
                            retry = false,
                            cond = cond
                        )
                    }) {
                        var i = 0
                        while (!isCompleted && i < scanTimeout) {
                            delay(10)
                            i += 10
                        }
                        if (isCompleted)
                            found = await()
                        else {
                            cancel()
                        }
                    }
                }.run {
                    time += this
                    // engine.device.runWatchers() // to update the exploration view?
                    if (!found && this < pollTime) {
                        delay(pollTime - this)
                    }
                    Log.d(TAG, "$found single wait iteration $this")
                }
            }
            found.also {
                Log.d(TAG, "wait was successful: $found")
            }
        }

    /*fun getScreenShot(automation: UiAutomation): Bitmap? {
        var screenshot: Bitmap? = null
        debugT("first screen-fetch attempt ", {
            try {
// 				screenshot = Screenshot.capture()?.bitmap // REMARK we cannot use this method as it would screw up the window handles in the UiAutomation
                screenshot = automation.takeScreenshot()
            } catch (e: Exception) {
                Log.w(TAG, "exception on screenshot-capture")
            }
        }, inMillis = true)
        return screenshot.also {
            if (it == null)
                Log.w(TAG, "no screenshot available")
        }
    }*/
}
