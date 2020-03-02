package droidmate.org.accessibility.automation.screenshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import droidmate.org.accessibility.R
import droidmate.org.accessibility.automation.AutomationEngine.Companion.screenshotPermissionChannel
import droidmate.org.accessibility.automation.utils.backgroundScope
import kotlinx.coroutines.launch

class ScreenshotPermissionRequest : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_permission_request)

        requestScreenshotPermission(1)
    }

    @Suppress("SameParameterValue")
    private fun requestScreenshotPermission(requestId: Int) {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            requestId
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val intent = if (resultCode == Activity.RESULT_OK && data != null) {
            data
        } else {
            throw RuntimeException("Unable to obtain screen recording permission")
        }

        backgroundScope.launch {
            screenshotPermissionChannel.send(intent)
        }

        finish()
    }
}
