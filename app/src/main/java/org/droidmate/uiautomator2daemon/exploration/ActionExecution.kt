package org.droidmate.uiautomator2daemon.exploration
/*
import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.AutomationEngine
import droidmate.org.accessibility.extensions.compress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.droidmate.deviceInterface.DeviceConstants
import org.droidmate.deviceInterface.exploration.ActionQueue
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.Click
import org.droidmate.deviceInterface.exploration.ClickEvent
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.deviceInterface.exploration.LaunchApp
import org.droidmate.deviceInterface.exploration.LongClick
import org.droidmate.deviceInterface.exploration.LongClickEvent
import org.droidmate.deviceInterface.exploration.PinchIn
import org.droidmate.deviceInterface.exploration.PinchOut
import org.droidmate.deviceInterface.exploration.RotateUI
import org.droidmate.deviceInterface.exploration.Scroll
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.deviceInterface.exploration.TextInsert
import org.droidmate.deviceInterface.exploration.Tick
import org.droidmate.deviceInterface.exploration.TwoPointerGesture
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.AutomationEnvironment
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.DisplayedWindow
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.SelectorCondition
import droidmate.org.accessibility.parsing.UiHierarchy
import droidmate.org.accessibility.parsing.UiParser.Companion.computeIdHash
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.actableAppElem
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.UiSelector.isWebView
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.backgroundScope
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.debugEnabled
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.debugOut
import org.droidmate.uiautomator2daemon.uiautomatorExtensions.isHomeScreen
import java.io.FileOutputStream
import kotlin.math.max

var idleTimeout: Long = 100
var interactiveTimeout: Long = 1000


private const val logTag = DeviceConstants.deviceLogcatTagPrefix + "ActionExecution"

var isWithinQueue = false

suspend fun ExplorationAction.execute(env: AutomationEngine): Any {
	val idMatch: (Int) -> SelectorCondition = { idHash ->
		{ n: AccessibilityNodeInfo, xPath ->
			val layer = env.lastWindows.find { it.w.windowId == n.windowId }?.layer ?: n.window?.layer
			layer != null && idHash == computeIdHash(xPath, layer)
		}
	}
	Log.d(logTag, "START execution ${toString()}($id)")
	val result: Any = when (this) { // REMARK this has to be an assignment for when to check for exhaustiveness
		is Click -> {
			env.device.verifyCoordinate(x, y)
			env.device.click(x, y, interactiveTimeout).apply {
				delay(delay)
			}
		}
		is Tick -> {
			var success = UiHierarchy.findAndPerform(env, idMatch(idHash)) {
				val newStatus = !it.isChecked
				it.isChecked = newStatus
				it.isChecked == newStatus
			}
			if (!success) {
				env.device.verifyCoordinate(x, y)
				success = env.device.click(x, y, interactiveTimeout)
			}
			delay(delay)
			success
		}
		is ClickEvent ->
			// do this for API Level above 19 (exclusive)
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->
				//				Log.d(logTag, "looking for click target, windows are ${env.getDisplayedWindows()}")
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
			}.also {
				if (it) {
					delay(delay)
				} // wait for display update
				Log.d(logTag, "perform successful=$it")
			}
		is LongClickEvent ->
			// do this for API Level above 19 (exclusive)
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
			}.also {
				if (it) {
					delay(delay)
				} // wait for display update
				Log.d(logTag, "perform successful=$it")
			}
		is LongClick -> {
			env.device.verifyCoordinate(x, y)
			env.device.longClick(x, y, interactiveTimeout).apply {
				delay(delay)
			}
		}
		is GlobalAction ->
			when (actionType) {
				ActionType.PressBack -> env.device.pressBack()
				ActionType.PressHome -> env.device.pressHome()
				ActionType.EnableWifi -> {
					val wfm = env.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
					wfm.setWifiEnabled(true).also {
						if (!it) Log.w(logTag, "Failed to ensure WiFi is enabled!")
					}
				}
				ActionType.MinimizeMaximize -> {
					env.device.minimizeMaximize()
					true
				}
				ActionType.FetchGUI -> fetchDeviceData(env = env, afterAction = false)
				ActionType.Terminate -> false /* should never be transferred to the device */
				ActionType.PressEnter -> env.device.pressEnter()
				ActionType.CloseKeyboard -> if (env.isKeyboardOpen()) //(UiHierarchy.any(env.device) { node, _ -> env.keyboardPkgs.contains(node.packageName) })
					env.device.pressBack()
				else true
			}//.also { if (it is Boolean && it) { delay(idleTimeout) } }// wait for display update (if no Fetch action)
		is TextInsert ->
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->
				if (nodeInfo.isFocusable) {
					nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
					Log.d(logTag, "focus input-field")
				} else if (nodeInfo.isClickable) {
					nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
					Log.d(logTag, "click non-focusable input-field")
				}
				// do this for API Level above 19 (exclusive)
				val args = Bundle()
				args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args).also {
					//					if(it) { delay(idleTimeout) } // wait for display update
					Log.d(logTag, "perform successful=$it")
					if (sendEnter && !isWithinQueue) {
						Log.d(logTag, "trigger enter")
						env.device.pressEnter()
					}  // when doing multiple action sending enter may trigger a continue button but not all elements are yet filled
					if (nodeInfo.isFocusable) {
						nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
					}
					delay(delay)
				}
			}
		is RotateUI -> env.device.rotate(rotation, env.automation)
		is LaunchApp -> {
			env.device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
			env.device.launchApp(packageName, env, launchActivityDelay, timeout)
		}
		is Swipe -> env.device.twoPointAction(start, end) { x0, y0, x1, y1 ->
			env.device.swipe(x0, y0, x1, y1, stepSize)
		}
		is TwoPointerGesture -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
		is PinchIn -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
		is PinchOut -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
		is Scroll -> // TODO we may trigger the UiObject2 method instead
			UiHierarchy.findAndPerform(env, idMatch(idHash)) { nodeInfo ->
				// do this for API Level above 19 (exclusive)
				nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
			}.also {
				if (it) {
					delay(idleTimeout)
				} // wait for display update
				Log.d(logTag, "perform successful=$it")
			}
		is ActionQueue -> {
			var success = true
			isWithinQueue = true
			actions.forEachIndexed { i, action ->
				if (i == actions.size - 1) isWithinQueue = false // reset var at the end of queue
				success = success &&
						action.execute(env).also {
							if (i < actions.size - 1 &&
								((action is TextInsert && actions[i + 1] is Click)
										|| action is Swipe)
							) {
								if (action is Swipe) {
									Log.d(logTag, "delay after swipe")
									delay(delay)
								}
								getOrStoreImgPixels(env.captureScreen(), env, action.id)
							}
						}.let { if (it is Boolean) it else true }
			}.apply {
				delay(delay)
				getOrStoreImgPixels(env.captureScreen(), env)
			}
		}
		else -> throw DeviceDaemonException("not implemented action $name was called in exploration/ActionExecution")
	}
	Log.d(logTag, "END execution of ${toString()} ($id)")
	return result
}


/*
//REMARK keep the order of first wait for windowUpdate, then wait for idle, then extract windows to minimize synchronization issues with opening/closing keyboard windows
private suspend fun waitForSync(env: AutomationEnvironment, afterAction: Boolean) {
	try {
		env.lastWindows.firstOrNull { it.isApp() && !it.isKeyboard && !it.isLauncher }?.let {
			env.device.waitForWindowUpdate(it.w.pkgName, env.interactiveTimeout) //wait sync on focused window
		}

		debugT("wait for IDLE avg = ${time / max(1, cnt)} ms", {
			env.waitForIdle(100, env.idleTimeout)
//		env.device.waitForIdle(env.idleTimeout) // this has a minimal delay of 500ms between events until the device is considered idle
		}, inMillis = true,
			timer = {
				Log.d(logTag, "time=${it / 1000000}")
				time += it / 1000000
				cnt += 1
			}) // this sometimes really sucks in performance but we do not yet have any reliable alternative
		debugOut("check if we have a webView", debugFetch)
		if (afterAction && UiHierarchy.any(env, cond = isWebView)) { // waitForIdle is insufficient for WebView's therefore we need to handle the stabilize separately
			debugOut("WebView detected wait for interactive element with different package name", debugFetch)
			UiHierarchy.waitFor(env, interactiveTimeout, actableAppElem)
		}
	} catch(e: java.util.concurrent.TimeoutException) {
		Log.e(logTag, "No idle state with idle timeout: 100ms within global timeout: ${env.idleTimeout}ms", e)
	}
}
*/

/*
private var time: Long = 0
private var cnt = 0
private const val debugFetch = false

private typealias twoPointStepableAction = (x0:Int,y0:Int,x1:Int,y1:Int)->Boolean
private fun AccessibilityService.twoPointAction(start: Pair<Int,Int>, end: Pair<Int,Int>, action: twoPointStepableAction):Boolean{
	val (x0,y0) = start
	val (x1,y1) = end
	verifyCoordinate(x0,y0)
	verifyCoordinate(x1,y1)
	return action(x0, y0, x1, y1)
}
*/

/*
private fun AccessibilityService.rotate(rotation: Int,automation: UiAutomation):Boolean {
	val currRotation = (displayRotation * 90)
	android.util.Log.d(logTag, "Current rotation $currRotation")
	// Android supports the following rotations:
	// ROTATION_0 = 0;
	// ROTATION_90 = 1;
	// ROTATION_180 = 2;
	// ROTATION_270 = 3;
	// Thus, instead of 0-360 we have 0-3
	// The rotation calculations is: [(current rotation in degrees + rotation) / 90] % 4
	// Ex: curr = 90, rotation = 180 => [(90 + 360) / 90] % 4 => 1
	val newRotation = ((currRotation + rotation) / 90) % 4
	Log.d(logTag, "New rotation $newRotation")
	unfreezeRotation()
	return automation.setRotation(newRotation)
}
*/
*/