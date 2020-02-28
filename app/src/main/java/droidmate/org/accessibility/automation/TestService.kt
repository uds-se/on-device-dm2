package droidmate.org.accessibility.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import droidmate.org.accessibility.automation.utils.backgroundScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    private val waitingForIdle = AtomicBoolean(false)
    private lateinit var automationEngine: AutomationEngine

    override fun onServiceConnected() {
        Log.i(TAG, "Service connected")
        automationEngine =
            AutomationEngine(
                idleNotificationChannel,
                this
            )
        automationEngine.run()
        scheduler.start()
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
