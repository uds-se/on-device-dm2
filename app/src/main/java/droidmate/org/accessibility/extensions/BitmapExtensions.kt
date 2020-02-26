package droidmate.org.accessibility.extensions

import android.graphics.Bitmap
import android.util.Log
import droidmate.org.accessibility.parsing.DisplayedWindow
import droidmate.org.accessibility.utils.TAG
import droidmate.org.accessibility.utils.debugT
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max

private var t = 0.0
private var c = 0

fun Bitmap.toByteArray(): ByteArray {
    val h = this.height
    val size = this.rowBytes * h
    val buffer = ByteBuffer.allocate(size * 4)  // *4 since each pixel is is 4 byte big
    this.copyPixelsToBuffer(buffer)
//		val config = Bitmap.Config.valueOf(bm.getConfig().name)
    return buffer.array()
}

@Suppress("unused") // keep it here for now, it may become useful later on
fun ByteArray.toBitmap(width: Int, height: Int): Bitmap {
    val config = Bitmap.Config.ARGB_8888  // should be the value from above 'val config = ..' call
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
            Log.w(TAG, "Failed to compress screenshot: ${e.message}. Stacktrace: ${e.stackTrace}")
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
            Log.e(TAG, "Error on screen validation ${e.message}. Stacktrace: ${e.stackTrace}")
            false
        }
    } else
        false
}