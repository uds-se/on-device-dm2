package org.droidmate.accessibility.automation.utils

import android.util.Log
import kotlin.system.measureNanoTime
import org.droidmate.deviceInterface.DeviceConstants

var measurePerformance = true
var debugEnabled = true

fun debugOut(msg: String, enabled: Boolean = true) {
    @Suppress("ConstantConditionIf")
    if (debugEnabled && enabled) {
        Log.d("droidmate/uiad/DEBUG", msg)
    }
}

@Suppress("ConstantConditionIf")
inline fun <T> nullableDebugT(
    msg: String,
    block: () -> T?,
    timer: (Long) -> Unit = {},
    inMillis: Boolean = false
): T? {
    var res: T? = null
    if (measurePerformance) {
        measureNanoTime {
            res = block.invoke()
        }.let {
            timer(it)
            Log.d(
                DeviceConstants.deviceLogcatTagPrefix + "performance",
                "TIME: ${if (inMillis) "${(it / 1000000.0).toInt()} ms" else "${it / 1000.0} ns/1000"} \t $msg"
            )
        }
    } else res = block.invoke()
    return res
}

inline fun <T> debugT(
    msg: String,
    block: () -> T?,
    timer: (Long) -> Unit = {},
    inMillis: Boolean = false
): T {
    return nullableDebugT(msg, block, timer, inMillis)
        ?: throw RuntimeException("debugT is non nullable use nullableDebugT instead")
}
