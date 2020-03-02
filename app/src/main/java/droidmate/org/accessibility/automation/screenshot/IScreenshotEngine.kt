package droidmate.org.accessibility.automation.screenshot

import android.graphics.Bitmap
import droidmate.org.accessibility.automation.IEngine

interface IScreenshotEngine : IEngine {
    fun takeScreenshot(actionNr: Int): Bitmap?
    fun getOrStoreImgPixels(bm: Bitmap?): ByteArray
    fun getOrStoreImgPixels(bm: Bitmap?, actionId: Int): ByteArray
}
