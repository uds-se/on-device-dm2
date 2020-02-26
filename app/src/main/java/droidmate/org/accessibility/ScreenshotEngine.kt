package droidmate.org.accessibility

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import droidmate.org.accessibility.IEngine.Companion.TAG
import droidmate.org.accessibility.extensions.compress
import droidmate.org.accessibility.utils.backgroundScope
import droidmate.org.accessibility.utils.debugOut
import droidmate.org.accessibility.utils.debugT
import droidmate.org.accessibility.utils.nullableDebugT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

open class ScreenshotEngine @JvmOverloads constructor(
    private val imgQuality: Int,
    private val delayedImgTransfer: Boolean,
    private val imgDir: File = Environment.getExternalStorageDirectory().resolve("DM-2/images")
): IScreenshotEngine {
    private var wt = 0.0
    private var wc = 0
    private var lastId = 0

    init {
         setup()
     }

    private fun setup() {
        // delete content from previous explorations
        imgDir.deleteRecursively()
        if (!imgDir.exists()) {
            imgDir.mkdirs()
        }
    }

    override fun takeScreenshot(): Bitmap? {
        return nullableDebugT("img capture time", {
            // UiHierarchy.getScreenShot(automation)
            null
        }, inMillis = true)
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
                    Log.w(TAG, "create empty image")
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