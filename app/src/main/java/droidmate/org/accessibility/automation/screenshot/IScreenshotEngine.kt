package droidmate.org.accessibility.automation.screenshot

import android.content.Intent
import android.graphics.Bitmap
import droidmate.org.accessibility.automation.IEngine

interface IScreenshotEngine: IEngine {
    // fun setIntent(intent: Intent)
    fun takeScreenshot(): Bitmap?
    fun getOrStoreImgPixels(bm: Bitmap?): ByteArray
    fun getOrStoreImgPixels(bm: Bitmap?, actionId: Int): ByteArray
}