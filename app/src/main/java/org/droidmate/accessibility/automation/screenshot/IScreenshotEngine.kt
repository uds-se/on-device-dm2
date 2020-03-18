package org.droidmate.accessibility.automation.screenshot

import android.graphics.Bitmap
import org.droidmate.accessibility.automation.IEngine

interface IScreenshotEngine : IEngine {
    fun takeScreenshot(actionNr: Int): Bitmap?
    fun getOrStoreImgPixels(bm: Bitmap?): ByteArray
    fun getOrStoreImgPixels(bm: Bitmap?, actionId: Int): ByteArray
}
