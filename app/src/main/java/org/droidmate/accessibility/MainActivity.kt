package org.droidmate.accessibility

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.facebook.drawee.backends.pipeline.Fresco
import org.droidmate.accessibility.automation.AutomationEngine
import org.droidmate.accessibility.automation.screenshot.ScreenRecorder

class MainActivity : AppCompatActivity() {
    companion object {
        internal val TAG = MainActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fresco.initialize(this)
        setContentView(R.layout.activity_main)

        val appList = findViewById<ListView>(R.id.installed_app_list)
        val appAdapter = AppAdapter(this)
        appList.adapter = appAdapter

        appList.onItemClickListener = OnItemClickListener { _, _, _, l ->
            AutomationEngine.targetPackage = appAdapter.getItem(l.toInt()).packageName
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        if (checkStoragePermission()) {
            requestScreenRecordingPermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission is granted")
            true
        } else {
            Log.v(TAG, "Permission is revoked")
            ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), 1)
            false
        }
    }

    private fun requestScreenRecordingPermission() {
        Log.v(TAG, "Requesting screen recording permission")
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 2)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission: ${permissions.first()} was ${grantResults.first()}")
            requestScreenRecordingPermission()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.v(TAG, "Screen recording permission returned. Result is $resultCode")
        if (resultCode == Activity.RESULT_OK && data != null) {
            Log.v(TAG, "Starting screen recording")
            val screenRecorder = ScreenRecorder.new(this, data)
            screenRecorder.start()

            while (!screenRecorder.isInitialized()) {
                Thread.sleep(10)
            }
        } else {
            Log.e(TAG, "Unable to obtain screen recording permission")
        }
    }

    /*private fun debug() {
        var bmp: Bitmap? = null
        measureTimeMillis { bmp = screenRecorder.takeScreenshot() }
            .let { Log.d(TAG, "waited $it millis for screenshot") }
        saveScreenshot(bmp, "a")


        measureTimeMillis { bmp = screenRecorder.takeScreenshot() }
            .let { Log.d(TAG, "waited $it millis for screenshot") }
        saveScreenshot(bmp, "b")
        measureTimeMillis { bmp = screenRecorder.takeScreenshot() }
            .let { Log.d(TAG, "waited $it millis for screenshot") }
        saveScreenshot(bmp, "c")
        screenRecorder.quit()
    }

    private val imgDir: File by lazy {
        val d = Environment.getExternalStorageDirectory()
            .resolve("DM-2")
            .resolve("images")

        // delete content from previous explorations
        d.deleteRecursively()
        if (!d.exists()) {
            d.mkdirs()
            Log.d(IEngine.TAG, "Image directory: $d exists: ${d.exists()}")
        }

        d
    }

    private fun saveScreenshot(bitmap: Bitmap?, name: String) {
        if (bitmap != null) {
            try {
                FileOutputStream(imgDir.resolve("$name.png")).use { out ->
                    // bmp is your Bitmap instance
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }*/
}
