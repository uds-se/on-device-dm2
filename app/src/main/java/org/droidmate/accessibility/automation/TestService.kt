package org.droidmate.accessibility.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.droidmate.accessibility.automation.utils.backgroundScope

open class TestService : AccessibilityService() {
    companion object {
        private val TAG = TestService::class.java.simpleName
    }

    private val accessibilityChannel = Channel<Long>()
    private val idleNotificationChannel = Channel<Long>()
    private val scheduler =
        CoroutineScheduler(
            accessibilityChannel,
            idleNotificationChannel
        )
    private val automationEngine: AutomationEngine by lazy {
        AutomationEngine(idleNotificationChannel, this)
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Service connected")

        if (!scheduler.isRunning) {
            automationEngine.run()
            scheduler.start()
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Interrupting service")
        scheduler.isCanceled = true
        automationEngine.canceled = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "Accessibility event received ${AccessibilityEvent.eventTypeToString(event.eventType)}")
        if (event.windowChanges + event.contentChangeTypes > 0) {
            Log.d(TAG, "Relevant accessibility event received")
        }
        backgroundScope.launch {
            accessibilityChannel.send(event.eventTime)
        }
    }

    /*suspend fun waitForIdle() {
        waitingForIdle.set(true)
        while (waitingForIdle.get()) {
            delay(10)
        }
    }*/
}
