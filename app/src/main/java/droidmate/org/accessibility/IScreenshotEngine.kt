package droidmate.org.accessibility

import android.graphics.Bitmap

interface IScreenshotEngine: IEngine {
    fun takeScreenshot(): Bitmap?
    fun getOrStoreImgPixels(bm: Bitmap?): ByteArray
    fun getOrStoreImgPixels(bm: Bitmap?, actionId: Int): ByteArray
}