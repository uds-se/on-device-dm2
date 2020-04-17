package org.droidmate.accessibility.automation

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class CoroutineScheduler(
    private val accessibilityEventChannel: Channel<Long>,
    private val idleNotificationChannel: Channel<Long>,
    private val waitInterval: Long = 200L,
    private val timeout: Long = 500L
) : CoroutineScope {
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(CoroutineScheduler::class.java) }
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private var canceled = AtomicBoolean(false)
    private val lastTimestamp = AtomicLong()
    private val lastTimeout = AtomicLong()
    private val mutex = Mutex(false)

    var isCanceled: Boolean
        get() = canceled.get()
        set(value) = canceled.set(value)

    val isRunning: Boolean
        get() {
            return lastTimestamp.get() > 0
        }

    fun start() {
        val currTime = SystemClock.uptimeMillis()
        launch {
            runListener()
        }
        launch {
            lastTimestamp.set(currTime)
            runScheduledTask(waitInterval)
        }
        launch {
            lastTimeout.set(currTime)
            runScheduledTimeout(timeout)
        }
    }

    private suspend fun runListener() {
        while (!isCanceled) {
            val timeStamp = accessibilityEventChannel.receive()
            log.trace(
                "Accessibility event with time $timeStamp received. " +
                        "Current timestamp = ${SystemClock.uptimeMillis()}"
            )
            lastTimestamp.set(SystemClock.uptimeMillis())
        }
    }

    private suspend fun runScheduledTask(delayInterval: Long) {
        log.trace("Scheduling task with interval of $delayInterval ms")
        while (!isCanceled) {
            // Determine how long we should wait
            val delayTime = delayInterval - (SystemClock.uptimeMillis() - lastTimestamp.get())
            log.trace("Delaying scheduled task for $delayTime ms")
            // Wait
            delay(delayTime)

            mutex.withLock {
                log.trace("(Locked) Checking if scheduled task must run")
                // Check the expected timestamp
                val expectedTimestamp = lastTimestamp.get() + delayTime

                // If we are after the expected timestamp, notify idle
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime > expectedTimestamp) {
                    log.trace("(Locked) Scheduled task must run, notifying channel")
                    // lastTimestamp.set(currentTime)
                    idleNotificationChannel.send(currentTime)
                }
            }
        }
    }

    private suspend fun runScheduledTimeout(delayInterval: Long) {
        log.trace("Scheduling timeout task with interval of $delayInterval ms")
        while (!isCanceled) {
            // Determine how long we should wait
            val delayTime = delayInterval - (SystemClock.uptimeMillis() - lastTimeout.get())
            log.trace("Delaying timeout for for $delayTime ms")
            // Wait
            delay(delayTime)

            mutex.withLock {
                log.trace("(Locked) Checking if timeout task must run")
                // Check the expected timestamp
                val expectedTimestamp = lastTimeout.get() + delayTime

                // If We are after the expected timestamp, update the timestamp and notify idle
                val currentTime = SystemClock.uptimeMillis()
                if (currentTime > expectedTimestamp) {
                    log.trace("(Locked) Timeout task must run, notifying channel")
                    lastTimestamp.set(currentTime)
                    lastTimeout.set(currentTime)
                    idleNotificationChannel.send(currentTime)
                }
            }
        }
    }
}
