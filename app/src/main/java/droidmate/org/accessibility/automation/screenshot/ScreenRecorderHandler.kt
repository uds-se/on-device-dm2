package droidmate.org.accessibility.automation.screenshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.WindowManager
import droidmate.org.accessibility.automation.IEngine
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecorderHandler(
    looper: Looper,
    private val context: Context,
    private val mediaProjectionIntent: Intent
): Handler(looper) {
    companion object {
        private const val RECORDING_NAME = "screencap"
        private const val VIRTUAL_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

        const val MESSAGE_START = 0
        const val MESSAGE_TAKE_SCREENSHOT = 1
        const val MESSAGE_TEARDOWN = 2
    }

    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader

    val waitingScreenshot = AtomicBoolean(false)

    var currBitmap: Bitmap? = null
        //private set

    private val mediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val mediaProjection by lazy {
        mediaProjectionManager.getMediaProjection(
            Activity.RESULT_OK,
            mediaProjectionIntent.clone() as Intent
        )
    }
    override fun handleMessage(msg: Message) {
        // process incoming messages here
        // this will run in non-ui/background thread
        return when (msg.what) {
            MESSAGE_START -> {

            }
            MESSAGE_TAKE_SCREENSHOT -> {
                setupRecording()
                //takeScreenshot()
            }
            MESSAGE_TEARDOWN -> {
                mediaProjection.stop()
            }
            else -> {
                throw IllegalStateException("Invalid message type: ${msg.what}")
            }
        }
    }

    private fun setupRecording() {
        val density = context.resources.displayMetrics.densityDpi
        val display =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            RECORDING_NAME,
            width,
            height,
            density,
            VIRTUAL_DISPLAY_FLAGS,
            imageReader.surface,
            null,
            null
        )
        imageReader.setOnImageAvailableListener({ reader ->
            Log.d(IEngine.TAG, "onImageAvailable")
            currBitmap?.recycle()
            currBitmap = null

            var image: Image? = null
            currBitmap = try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                currBitmap?.recycle()
                Log.e(IEngine.TAG, "Unable to acquire screenshot: ${e.message}", e)
                null
            }
            image?.close()
            reader.close()
            waitingScreenshot.set(false)
        }, null)

        registerTearDown()
    }

    private fun registerTearDown() {
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                virtualDisplay.release()
                imageReader.setOnImageAvailableListener(null, null)
                mediaProjection.unregisterCallback(this)
            }
        }, null)
    }
}