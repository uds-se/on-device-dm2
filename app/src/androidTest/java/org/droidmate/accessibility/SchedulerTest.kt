package org.droidmate.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.droidmate.accessibility.automation.CoroutineScheduler
import org.droidmate.accessibility.automation.utils.backgroundScope
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@RunWith(AndroidJUnit4::class)
class SchedulerTest {
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(SchedulerTest::class.java) }
    }

    private suspend fun testScheduler(maxElapsedTime: Long, timeout: Boolean) {
        val mutex = Mutex(true)
        val accessibilityChannel = Channel<Long>()
        val notificationChannel = Channel<Long>()
        val scheduler = if (timeout) {
            CoroutineScheduler(
                accessibilityChannel,
                notificationChannel,
                500,
                200
            )
        } else {
            CoroutineScheduler(
                accessibilityChannel,
                notificationChannel,
                200,
                500
            )
        }
        backgroundScope.launch {
            val elapsedTime = measureTimeMillis {
                notificationChannel.receive()
            }

            log.debug("Elapsed time until callback: $elapsedTime ms")
            val diff = abs(elapsedTime - maxElapsedTime)
            log.debug("Difference from target: $diff ms")
            assertTrue("Scheduler did not run in $maxElapsedTime", diff < 15)
            if (mutex.isLocked) {
                mutex.unlock()
            }
        }
        scheduler.start()
        mutex.lock()
        scheduler.isCanceled = true
    }

    @Test
    fun intervalScheduleTest() {
        runBlocking {
            testScheduler(200, false)
        }
    }

    @Test
    fun timeoutScheduleTest() {
        runBlocking {
            testScheduler(200, true)
        }
    }
}
