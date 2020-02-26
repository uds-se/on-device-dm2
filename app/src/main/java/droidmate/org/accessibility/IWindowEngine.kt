package droidmate.org.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.parsing.DisplayDimension
import droidmate.org.accessibility.parsing.DisplayedWindow
import droidmate.org.accessibility.utils.NodeProcessor
import droidmate.org.accessibility.utils.PostProcessor
import org.droidmate.deviceInterface.exploration.DeviceResponse

interface IWindowEngine: IEngine {
    val lastDisplayDimension: DisplayDimension
    val lastWindows: List<DisplayedWindow>

    var lastResponse: DeviceResponse

    // Will be updated during the run, when the right command is sent (i.e. on AppLaunch)
    var launchedMainActivity: String

    fun verifyCoordinate(x: Int, y: Int)

    fun getDisplayRotation(): Int
    suspend fun getDisplayedWindows(): List<DisplayedWindow>

    // FIXME for the apps with interaction issues, check if we need different window types here
    suspend fun getAppRootNodes(): List<AccessibilityNodeInfo>

    suspend fun isKeyboardOpen(): Boolean

    suspend fun<T> exec(processor: NodeProcessor, postProcessor: PostProcessor<T>): List<T>

    suspend fun exec(processor: NodeProcessor)

    suspend fun fetchDeviceData(afterAction: Boolean = false): DeviceResponse
}