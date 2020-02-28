package droidmate.org.accessibility

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import droidmate.org.accessibility.automation.Scheduler
import droidmate.org.accessibility.automation.utils.backgroundScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertTrue

import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class SchedulerTest {
    companion object {
        private val TAG = SchedulerTest::class.java.simpleName
    }

    private suspend fun testScheduler(maxElapsedTime: Long, timeout: Boolean) {
        val mutex = Mutex(true)
        val notificationChannel = Channel<Boolean>()
        val scheduler = if (timeout) {
            Scheduler(
                notificationChannel,
                500,
                200
            )
        } else {
            Scheduler(
                notificationChannel,
                200,
                500
            )
        }
        backgroundScope.launch {
            val elapsedTime = measureTimeMillis {
                notificationChannel.receive()
            }

            Log.d(TAG, "Elapsed time until callback: $elapsedTime ms")
            val diff = abs(elapsedTime - maxElapsedTime)
            Log.d(TAG, "Difference from target: $diff ms")
            assertTrue("Scheduler did not run in $maxElapsedTime", diff < 15)
            if (mutex.isLocked) {
                mutex.unlock()
            }
        }
        scheduler.start()
        mutex.lock()
        scheduler.teardown()
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
