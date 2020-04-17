package org.droidmate.accessibility.automation.extensions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.facebook.imagepipeline.request.BasePostprocessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import org.droidmate.accessibility.automation.parsing.DisplayedWindow
import org.droidmate.accessibility.automation.utils.debugT
import org.droidmate.deviceInterface.exploration.Rectangle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private var t = 0.0
private var c = 0

private val log: Logger by lazy { LoggerFactory.getLogger("BitmapExtensions") }

fun Bitmap.toByteArray(): ByteArray {
    val h = this.height
    val size = this.rowBytes * h
    val buffer = ByteBuffer.allocate(size * 4) // *4 since each pixel is is 4 byte big
    this.copyPixelsToBuffer(buffer)
// 		val config = Bitmap.Config.valueOf(bm.getConfig().name)
    return buffer.array()
}

// keep it here for now, it may become useful later on
@Suppress("unused")
fun ByteArray.toBitmap(width: Int, height: Int): Bitmap {
    // should be the value from above 'val config = ..' call
    val config = Bitmap.Config.ARGB_8888
    val bm = Bitmap.createBitmap(width, height, config)
    val buffer = ByteBuffer.wrap(this)
    bm.copyPixelsFromBuffer(buffer)
    return bm
}

fun Bitmap.compress(): ByteArray {
    return debugT("compress image avg = ${t / max(1, c)}", {
        var bytes = ByteArray(0)
        val stream = ByteArrayOutputStream()
        try {
            this.setHasAlpha(false)
            this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()

            bytes = stream.toByteArray()
            stream.close()
        } catch (e: Exception) {
            log.warn("Failed to compress screenshot: ${e.message}. Stacktrace: ${e.stackTrace}")
        }

        bytes
    }, inMillis = true, timer = { t += it / 1000000.0; c += 1 })
}

private val windowFilter: (window: DisplayedWindow, value: Int) -> Int = { w, v ->
    if (w.isExtracted()) {
        v
    } else {
        0
    }
}

private val windowWidth: (DisplayedWindow?) -> Int = { window ->
    window?.w?.boundaries?.let {
        windowFilter(
            window,
            it.leftX + it.width
        )
    } ?: 0
}

private val windowHeight: (DisplayedWindow?) -> Int = { window ->
    window?.w?.boundaries?.let {
        windowFilter(
            window,
            it.topY + it.height
        )
    } ?: 0
}

fun Bitmap?.isValid(appWindows: List<DisplayedWindow>): Boolean {
    return if (this != null) {
        try {
            val maxWidth = windowWidth(
                appWindows.maxBy(windowWidth)
            )
            val maxHeight = windowHeight(
                appWindows.maxBy(windowHeight)
            )

            (maxWidth == 0 && maxHeight == 0) || ((maxWidth <= this.width) && (maxHeight <= this.height))
        } catch (e: Exception) {
            log.error("Error on screen validation ${e.message}. Stacktrace: ${e.stackTrace}")
            false
        }
    } else
        false
}

class BitmapProcessor(private val b: Rectangle) : BasePostprocessor() {
    var contentHash = 0
        private set

    override fun process(destBitmap: Bitmap?, sourceBitmap: Bitmap?) {
        if (sourceBitmap == null || destBitmap == null || b.isEmpty()) {
            return
        }

        val c = Canvas(destBitmap)
        c.drawBitmap(sourceBitmap, b.toRect(), Rect(0, 0, b.width, b.height), null)

        // now subImg contains all its pixel of the area specified by b
        // convert the image into byte array to determine a deterministic hash value
        contentHash = destBitmap.toByteArray().contentHashCode()
    }
}
