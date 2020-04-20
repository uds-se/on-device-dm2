package org.droidmate.accessibility.automation.screenshot

import android.graphics.Bitmap
import org.droidmate.accessibility.automation.IEngine

interface IScreenshotEngine : IEngine {
    fun takeScreenshot(actionNr: Int): Bitmap?
    fun getAndStoreImgPixels(bm: Bitmap?, actionId: Int, delayedImgTransfer: Boolean): ByteArray
}
