package droidmate.org.accessibility.automation.screenshot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.os.HandlerThread
import android.util.Log
import droidmate.org.accessibility.automation.IEngine
import droidmate.org.accessibility.automation.extensions.compress
import droidmate.org.accessibility.automation.screenshot.ScreenRecorderHandler.Companion.MESSAGE_START
import droidmate.org.accessibility.automation.screenshot.ScreenRecorderHandler.Companion.MESSAGE_TAKE_SCREENSHOT
import droidmate.org.accessibility.automation.screenshot.ScreenRecorderHandler.Companion.MESSAGE_TEARDOWN
import droidmate.org.accessibility.automation.utils.backgroundScope
import droidmate.org.accessibility.automation.utils.debugOut
import droidmate.org.accessibility.automation.utils.debugT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max


class ScreenRecorder(
    private val context: Context,
    private val mediaProjectionIntent: Intent,
    private val imgQuality: Int = 10,
    private val delayedImgTransfer: Boolean = false
): HandlerThread("screenRecorderThread"), IScreenshotEngine {
    private val imgDir: File by lazy {
        Environment.getExternalStorageDirectory()
            .resolve("DM-2")
            .resolve("images")
    }

    private var wt = 0.0
    private var wc = 0
    private var lastId = 0

    private lateinit var handler: ScreenRecorderHandler

    fun isInitialized() = ::handler.isInitialized

    override fun onLooperPrepared() {
        super.onLooperPrepared()

        handler = ScreenRecorderHandler(looper, context, mediaProjectionIntent)
        handler.sendEmptyMessage(MESSAGE_START)
    }

    override fun quit(): Boolean {
        handler.sendEmptyMessage(MESSAGE_TEARDOWN)
        return super.quit()
    }

    override fun takeScreenshot(): Bitmap? {
        handler.currBitmap = null
        handler.waitingScreenshot.set(true)
        handler.sendEmptyMessage(MESSAGE_TAKE_SCREENSHOT)

        while (handler.waitingScreenshot.get()) {
            Thread.sleep(50)
        }

        return handler.currBitmap
    }

    /**
     * Compressing an image no matter the quality, takes long time therefore the option of
     * storing these asynchronous and transferring them later is available via configuration
     */
    override fun getOrStoreImgPixels(bm: Bitmap?): ByteArray {
        return getOrStoreImgPixels(bm, lastId)
    }

    /**
     * Compressing an image no matter the quality, takes long time therefore the option of
     * storing these asynchronous and transferring them later is available via configuration
     */
    override fun getOrStoreImgPixels(bm: Bitmap?, actionId: Int): ByteArray {
        return debugT("wait for screen avg = ${wt / max(1, wc)}", {
            when { // if we couldn't capture screenshots
                bm == null -> {
                    Log.w(IEngine.TAG, "create empty image")
                    ByteArray(0)
                }
                delayedImgTransfer -> {
                    backgroundScope.launch(Dispatchers.IO) {
                        // we could use an actor getting id and bitmap via channel, instead of starting another coroutine each time
                        debugOut("create screenshot for action $actionId")
                        val os = FileOutputStream(imgDir.absolutePath + "/" + actionId + ".jpg")
                        bm.compress(Bitmap.CompressFormat.JPEG, imgQuality, os)
                        os.close()
                        bm.recycle()
                    }
                    ByteArray(0)
                }
                else -> bm.compress().also {
                    bm.recycle()
                }
            }
        }, inMillis = true,
            timer = { wt += it / 1000000.0; wc += 1 })
    }
}