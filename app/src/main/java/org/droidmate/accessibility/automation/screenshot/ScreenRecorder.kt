package org.droidmate.accessibility.automation.screenshot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.HandlerThread
import java.io.FileOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.droidmate.accessibility.automation.extensions.compress
import org.droidmate.accessibility.automation.screenshot.ScreenRecorderHandler.Companion.MESSAGE_START
import org.droidmate.accessibility.automation.screenshot.ScreenRecorderHandler.Companion.MESSAGE_TEARDOWN
import org.droidmate.accessibility.automation.utils.debugOut
import org.droidmate.accessibility.automation.utils.debugT
import org.droidmate.accessibility.automation.utils.imgDir
import org.droidmate.accessibility.automation.utils.ioScope
import org.droidmate.accessibility.automation.utils.nullableDebugT
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ScreenRecorder private constructor(
    private val context: Context,
    private val mediaProjectionIntent: Intent,
    private val imgQuality: Int = 10
) : HandlerThread("screenRecorderThread"), IScreenshotEngine, CoroutineScope {

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(ScreenRecorder::class.java) }

        var instance: ScreenRecorder? = null
            private set

        fun new(
            context: Context,
            mediaProjectionIntent: Intent,
            imgQuality: Int = 10
        ): ScreenRecorder {
            instance =
                ScreenRecorder(context, mediaProjectionIntent, imgQuality)
            return get()
        }

        fun get(): ScreenRecorder {
            return instance ?: throw IllegalStateException("Screen recorder is not initialized")
        }
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private val bitmapChannel = Channel<Bitmap?>()

    private var wt = 0.0
    private var wc = 0

    private lateinit var handler: ScreenRecorderHandler

    fun isInitialized() = ::handler.isInitialized

    override fun onLooperPrepared() {
        super.onLooperPrepared()

        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            log.warn(
                "warn we have no storage permission, we may not be able to store & fetch screenshots"
            )
        }

        handler = ScreenRecorderHandler(looper, bitmapChannel, context, mediaProjectionIntent)
        // handler.sendEmptyMessage(MESSAGE_START)
    }

    override fun quit(): Boolean {
        handler.sendEmptyMessage(MESSAGE_TEARDOWN)
        return super.quit()
    }

    override fun takeScreenshot(actionNr: Int): Bitmap? {
        return nullableDebugT("take screenshot", {
            var bitmap: Bitmap?
            // handler.sendEmptyMessage(MESSAGE_TAKE_SCREENSHOT)
            handler.sendEmptyMessage(MESSAGE_START)
            runBlocking {
                ioScope.coroutineContext[Job]?.children?.forEach { it.join() }
                bitmap = bitmapChannel.receive()

                handler.sendEmptyMessage(MESSAGE_TEARDOWN)

                bitmap
            }
        }, inMillis = true)
    }

    /**
     * Compressing an image no matter the quality, takes long time therefore the option of
     * storing these asynchronous and transferring them later is available via configuration
     */
    override fun getAndStoreImgPixels(bm: Bitmap?, actionId: Int, delayedImgTransfer: Boolean): ByteArray {
        runBlocking { ioScope.coroutineContext[Job]?.children?.forEach { it.join() } }
        return debugT("wait for screen avg = ${wt / max(1, wc)}", {
            when { // if we couldn't capture screenshots
                bm == null -> {
                    log.warn("create empty image")
                    ByteArray(0)
                }
                delayedImgTransfer -> {
                    ioScope.launch(Dispatchers.IO) {
                        // we could use an actor getting id and bitmap via channel, instead of starting another coroutine each time
                        debugOut("create screenshot for action $actionId")
                        val os = FileOutputStream(imgDir.absolutePath + "/" + actionId + ".jpg")
                        bm.compress(Bitmap.CompressFormat.JPEG, imgQuality, os)
                        os.close()
                        bm.recycle()
                    }
                    ByteArray(0)
                }
                else -> bm.compress().also { bm.recycle() }
            }
        }, inMillis = true,
            timer = { wt += it / 1000000.0; wc += 1 })
    }
}
