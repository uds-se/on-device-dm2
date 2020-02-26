package droidmate.org.accessibility

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

data class Scheduler(
    private val notificationChannel: Channel<Boolean>,
    private val waitInterval: Long = 1000L,
    private val timeout: Long = 5000L
): CoroutineScope {
    companion object {
        private val TAG = Scheduler::class.java.simpleName
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private val lastTimestamp = AtomicLong()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val checkTimeout = Runnable {
        launch {
            checkAndNotify(timeout, true)
        }
    }

    private val checkInterval = Runnable {
        launch {
            checkAndNotify(waitInterval, false)
        }
    }

    /*val isIdle: Boolean
        get() = idle.get()*/

    /*fun registerListener(listener: ()->Unit) {
        idleListeners.add(listener)
    }*/

    @Synchronized
    private fun schedule() {
        // Remove currently and reschedule checks
        Log.e(TAG, "Rescheduling, removing callbacks")
        mainHandler.removeCallbacks(checkInterval)
        mainHandler.removeCallbacks(checkTimeout)
        Log.e(TAG, "Rescheduling, posting callbacks")
        mainHandler.postDelayed(checkInterval, waitInterval)
        mainHandler.postDelayed(checkTimeout, timeout)
    }

    private suspend fun checkAndNotify(limit: Long, isTimeout: Boolean) {
        val currTime = SystemClock.uptimeMillis()
        val lastEntry = lastTimestamp.get()
        val diff = currTime - lastEntry
        if (diff > limit) {
            val message = if (isTimeout) {
                "$timeout elapsed without any event (timeout)"
            } else {
                "$waitInterval elapsed without any event (regular)"
            } + "Current=$currTime LastEvent=$lastEntry Diff=$diff"
            Log.i(TAG, message)

            if (isTimeout) {
                mainHandler.postDelayed(checkTimeout, timeout)
            }
            notifyListeners()
        }
    }

    private suspend fun notifyListeners() {
        Log.e(TAG, "Notifying listeners")
        notificationChannel.send(true)
        Log.e(TAG, "Listeners notified")
    }

    fun start() {
        reset(0)
    }

    fun reset(timestamp: Long) {
        //idle.set(false)
        lastTimestamp.set(timestamp)
        schedule()
    }

    fun teardown() {
        mainHandler.removeCallbacks(checkInterval)
        mainHandler.removeCallbacks(checkTimeout)
    }
}