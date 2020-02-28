package droidmate.org.accessibility.automation.screenshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import droidmate.org.accessibility.automation.IEngine.Companion.TAG
import droidmate.org.accessibility.automation.extensions.compress
import droidmate.org.accessibility.automation.utils.backgroundScope
import droidmate.org.accessibility.automation.utils.debugOut
import droidmate.org.accessibility.automation.utils.debugT
import droidmate.org.accessibility.automation.utils.nullableDebugT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.CoroutineContext
import kotlin.math.max


/*object ScreenshotEngine : IScreenshotEngine, CoroutineScope {
        private const val SCREENCAP_NAME = "screencap"
        private const val VIRTUAL_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private val mutex = Mutex(false)

    private var mIntent: Intent? = null
    private var mBitmap: Bitmap? = null

    private var wt = 0.0
    private var wc = 0
    private var lastId = 0

    private lateinit var context: Context
    private var imgQuality: Int = 10
    private var delayedImgTransfer: Boolean = false
    private val imgDir: File by lazy {
        context.dataDir// Environment.getExternalStorageDirectory()
            //.resolve("Download")
            .resolve("DM-2")
            .resolve("images")
    }

    fun setup(context: Context, imgQuality: Int, delayedImgTransfer: Boolean) {
        this.context = context
        this.imgQuality = imgQuality
        this.delayedImgTransfer = delayedImgTransfer

        // delete content from previous explorations
        imgDir.deleteRecursively()
        if (!imgDir.exists()) {
            Files.createDirectories(Paths.get(imgDir.toURI()))
            Log.d(TAG, "Image directory: $imgDir exists: ${imgDir.exists()}")
        }

        initScreenshotRecorder()
    }

    override suspend fun takeScreenshot(): Bitmap? {
        mutex.lock()
        AsyncTask.execute {
            Looper.loop()
        }
        return nullableDebugT("img capture time", {
            mutex.withLock {
                if (mBitmap != null) {
                    try {
                        FileOutputStream(imgDir.resolve("a.png")).use { out ->
                            mBitmap?.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                out
                            ) // bmp is your Bitmap instance
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                mBitmap
            }
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

    /*override fun setIntent(intent: Intent) {
        mIntent = intent
    }

    private fun initScreenshotRecorder(): Boolean {
        val intent = mIntent ?: return false
        val mediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val mediaProjection =
            mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, intent.clone() as Intent)
                ?: return false

        AsyncTask.execute {
            Looper.prepare()
            val mainHandler: Handler? = Handler()
            val density = context.resources.displayMetrics.densityDpi
            val display =
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            val size = Point()
            display.getSize(size)
            val width = size.x
            val height = size.y
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
            /*val virtualDisplay = */mediaProjection.createVirtualDisplay(
            SCREENCAP_NAME, width,
            height, density,
            VIRTUAL_DISPLAY_FLAGS, imageReader.surface, null, mainHandler
        )
            mBitmap?.recycle()
            mBitmap = null
            imageReader.setOnImageAvailableListener({ reader ->
                if (mutex.isLocked) {
                    Log.d(TAG, "onImageAvailable")
                    //mediaProjection.stop()

                    var image: Image? = null
                    mBitmap = try {
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
                        mBitmap?.recycle()
                        Log.e(TAG, "Unable to acquire screenshot: ${e.message}", e)
                        null
                    }
                    image?.close()
                    reader.close()
                    if (mutex.isLocked) {
                        mutex.unlock()
                    }
                }
            }, mainHandler)
            /*mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    virtualDisplay?.release()
                    imageReader.setOnImageAvailableListener(null, null)
                    mediaProjection.unregisterCallback(this)

                    mutex.unlock()
                }
            }, null)*/

            Looper.loop()
        }
        return true
    }*/
}*/
