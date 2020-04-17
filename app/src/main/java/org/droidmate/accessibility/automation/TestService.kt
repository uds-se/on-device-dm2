package org.droidmate.accessibility.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.droidmate.accessibility.automation.utils.backgroundScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class TestService : AccessibilityService() {
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(TestService::class.java) }
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
        log.info("Service connected")

        if (!scheduler.isRunning) {
            automationEngine.run()
            scheduler.start()
        }
    }

    override fun onInterrupt() {
        log.info("Interrupting service")
        scheduler.isCanceled = true
        automationEngine.canceled = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        log.debug("Accessibility event received ${AccessibilityEvent.eventTypeToString(event.eventType)}")
        if (event.windowChanges + event.contentChangeTypes > 0) {
            log.debug("Relevant accessibility event received")
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
