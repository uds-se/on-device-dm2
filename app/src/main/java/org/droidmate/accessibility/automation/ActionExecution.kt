package org.droidmate.accessibility.automation

import android.util.Log
import android.view.KeyEvent
import java.lang.Integer.max
import org.droidmate.accessibility.automation.exceptions.DeviceDaemonException
import org.droidmate.accessibility.automation.exceptions.ErrorResponse
import org.droidmate.accessibility.automation.utils.debugT
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
import org.droidmate.deviceInterface.exploration.Scroll
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.deviceInterface.exploration.TextInsert
import org.droidmate.deviceInterface.exploration.Tick

private const val TAG = DeviceConstants.deviceLogcatTagPrefix + "ActionExecution"

var lastId = 0
var isWithinQueue = false

private var nActions = 0
private var tFetch = 0L
private var tExec = 0L
private var et = 0.0

suspend fun ExplorationAction.execute(env: AutomationEngine): DeviceResponse {
    Log.v(TAG, "Executing action: ($nActions) $this")

    return try {
        debugT(" EXECUTE-TIME avg = ${et / max(1, nActions)}", {
            isWithinQueue = false

            Log.v(TAG, "Performing GUI action $this [${this.id}]")

            val result = debugT("execute action avg= ${tExec / (max(nActions, 1) * 1000000)}",
                {
                    lastId = this.id
                    this.execute(env)
                },
                inMillis = true,
                timer = {
                    tExec += it
                }
            )
            // TODO if the previous action was not successful we should return an "ActionFailed"-DeviceResponse

            // only fetch once even if the action was a FetchGUI action
            if (!this.isFetch())
                debugT("FETCH avg= ${tFetch / (max(nActions, 1) * 1000000)}", {
                    env.fetchDeviceData(nActions, afterAction = true)
                },
                    inMillis = true,
                    timer = {
                        tFetch += it
                    }
                )
            else {
                result
            }
        }, inMillis = true, timer = {
            et += it / 1000000.0
            nActions += 1
        })
    } catch (e: Throwable) {
        Log.e(TAG, "Error: " + e.message)
        Log.e(TAG, "Printing stack trace for debug")
        e.printStackTrace()

        ErrorResponse(e)
    }
}

private suspend fun ExplorationAction.performAction(env: AutomationEngine): Any {
    Log.d(TAG, "START execution ${toString()}($id)")
    // REMARK this has to be an assignment for when to check for exhaustiveness
    val result: Any = when (this) {
        is Click -> env.click(this)
        is Tick -> env.tick(this)
        is ClickEvent -> env.clickEvent(this)
        is LongClickEvent -> env.longClickEvent(this)
        is LongClick -> env.longClick(this)
        is GlobalAction ->
            when (actionType) {
                ActionType.PressBack -> env.pressBack()
                ActionType.PressHome -> env.pressHome()
                ActionType.EnableWifi -> env.enableWifi()
                ActionType.MinimizeMaximize -> env.minimizeMaximize()
                ActionType.FetchGUI -> env.fetchDeviceData(this.id, afterAction = false)
                /* should never be transferred to the device */
                ActionType.Terminate -> false
                ActionType.PressEnter -> env.pressEnter()
                ActionType.CloseKeyboard -> if (env.isKeyboardOpen()) {
                    env.pressBack()
                } else {
                    true
                }
            }
        is TextInsert -> env.insertText(this)
        // is RotateUI -> env.device.rotate(rotation, env.automation)
        is LaunchApp -> {
            env.sendKeyEvent(KeyEvent.KEYCODE_WAKEUP)
            env.launchApp(packageName, launchActivityDelay)
        }
        /*
        is Swipe -> env.device.twoPointAction(start, end) { x0, y0, x1, y1 ->
            env.device.swipe(x0, y0, x1, y1, stepSize)
        }
        */
        // is TwoPointerGesture -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
        // is PinchIn -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
        // is PinchOut -> TODO("this requires a call on UiObject, which we currently do not match to our ui-extraction")
        is Scroll -> env.scroll(this)
        is ActionQueue -> {
            var success = true
            isWithinQueue = true
            actions.forEachIndexed { i, action ->
                if (i == actions.size - 1) isWithinQueue = false // reset var at the end of queue
                success = success &&
                        action.performAction(env).also {
                            if (i < actions.size - 1 &&
                                ((action is TextInsert && actions[i + 1] is Click) ||
                                        action is Swipe)
                            ) {
                                if (action is Swipe) {
                                    Log.d(TAG, "delay after swipe")
                                    kotlinx.coroutines.delay(delay)
                                }

                                env.takeScreenshot(action.id)
                            }
                        }.let {
                            if (it is Boolean) {
                                it
                            } else {
                                true
                            }
                        }
            }.apply {
                kotlinx.coroutines.delay(delay)
            }
        }
        else -> throw DeviceDaemonException("not implemented action $name was called in exploration/ActionExecution")
    }
    Log.d(TAG, "END execution of ${toString()} ($id)")
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
