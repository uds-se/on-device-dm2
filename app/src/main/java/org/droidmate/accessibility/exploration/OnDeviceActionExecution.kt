package org.droidmate.accessibility.exploration

import java.time.LocalDateTime
import kotlin.math.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.droidmate.accessibility.automation.AutomationEngine
import org.droidmate.accessibility.automation.execute
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.error.DeviceException
import org.droidmate.device.error.DeviceExceptionMissing
import org.droidmate.deviceInterface.exploration.ActionQueue
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.LaunchApp
import org.droidmate.explorationModel.debugT
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory

private val log by lazy { LoggerFactory.getLogger("OnDevice-ActionRun") }

private lateinit var snapshot: DeviceResponse
lateinit var exception: DeviceException

private var performT: Long = 0
private var performN: Int = 0

private suspend fun performAction(action: ExplorationAction, automationEngine: AutomationEngine) {
    when {
        action.name == ActionType.Terminate.name -> automationEngine.terminate()
        action is LaunchApp || (action is ActionQueue && action.actions.any { it is LaunchApp }) -> {
            defaultExecution(action, automationEngine)
        }
        else -> debugT("perform $action on average ${performT / max(performN, 1)} ms", {
            if (action is ActionQueue) check(action.actions.isNotEmpty()) { "ERROR your strategy should never decide for EMPTY ActionQueue" }
            defaultExecution(action, automationEngine)
        }, timer = {
            performT += it / 1000000
            performN += 1
        }, inMillis = true)
    }
}

@Throws(DeviceException::class)
suspend fun ExplorationAction.execute(app: IApk, automationEngine: AutomationEngine): ActionResult {
    snapshot = DeviceResponse.empty
    exception = DeviceExceptionMissing()

    val startTime = LocalDateTime.now()
    try {
        log.trace("$name.execute(app=${app.fileName}, device)")

        log.debug("1. Assert only background API logs are present, if any.")
        // val logsHandler = DeviceLogsHandler(device)
// 		debugT("reading logcat", { logsHandler.readClearAndAssertOnlyBackgroundApiLogsIfAny() }, inMillis = true)
// 		logs = logsHandler.getLogs()

        log.debug("2. Perform action $this ($id)")

        performAction(this, automationEngine)

        log.trace("$name.execute(app=${app.fileName}, device) - DONE")
    } catch (e: Throwable) {
        exception = if (e !is DeviceException) {
            DeviceException(e)
        } else {
            e
        }
        log.warn(
            Markers.appHealth,
            "! Caught ${e.javaClass.simpleName} while performing device explorationTrace of $this.",
            e
        )
    }
    val endTime = LocalDateTime.now()

    // For post-conditions, see inside the constructor call made line below.
    return ActionResult(
        this,
        startTime,
        endTime,
        emptyList(),
        snapshot,
        exception = exception.toString(),
        screenshot = snapshot.screenshot
    )
}

@Throws(DeviceException::class)
private suspend fun defaultExecution(
    action: ExplorationAction,
    automationEngine: AutomationEngine,
    isSecondTry: Boolean = false
): Unit = coroutineScope {
    try {
        snapshot = action.execute(automationEngine)
    } catch (e: Exception) {
        log.warn("Failed to perform $action, retry once")
        delay(1000)
        if (!isSecondTry) {
            defaultExecution(action, automationEngine, isSecondTry = true)
        }
    }
}
