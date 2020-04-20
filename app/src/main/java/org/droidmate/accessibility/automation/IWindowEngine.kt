package org.droidmate.accessibility.automation

import android.view.accessibility.AccessibilityNodeInfo
import org.droidmate.accessibility.automation.parsing.DisplayDimension
import org.droidmate.accessibility.automation.parsing.DisplayedWindow
import org.droidmate.accessibility.automation.utils.NodeProcessor
import org.droidmate.accessibility.automation.utils.PostProcessor
import org.droidmate.deviceInterface.exploration.DeviceResponse

interface IWindowEngine : IEngine {
    val lastDisplayDimension: DisplayDimension
    val lastWindows: List<DisplayedWindow>

    var lastResponse: DeviceResponse

    // Will be updated during the run, when the right command is sent (i.e. on AppLaunch)
    var launchedMainActivity: String

    fun verifyCoordinate(x: Int, y: Int)

    fun getDisplayDimension(): DisplayDimension
    fun getDisplayRotation(): Int
    suspend fun getDisplayedAppWindows(): List<DisplayedWindow>
    suspend fun getDisplayedWindows(): List<DisplayedWindow>

    // FIXME for the apps with interaction issues, check if we need different window types here
    suspend fun getAppRootNodes(): List<AccessibilityNodeInfo>

    suspend fun getRootNodes(): List<AccessibilityNodeInfo>

    suspend fun isKeyboardOpen(): Boolean

    suspend fun <T> exec(processor: NodeProcessor, postProcessor: PostProcessor<T>): List<T>

    suspend fun exec(processor: NodeProcessor)

    suspend fun fetchDeviceData(actionNr: Int, delayedImgFetch: Boolean, afterAction: Boolean = false): DeviceResponse
}
