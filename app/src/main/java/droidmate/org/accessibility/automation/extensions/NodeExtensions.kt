package droidmate.org.accessibility.automation.extensions

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

fun AccessibilityNodeInfo.getBounds(width: Int, height: Int): Rect = when {
    isEnabled && isVisibleToUser -> this.getVisibleBoundsInScreen(width, height)
    // this may lead to negative coordinates/width/height values
    else -> Rect().apply { getBoundsInScreen(this) }
}

/**
 * Returns the node's bounds clipped to the size of the display
 *
 * @param width pixel width of the display
 * @param height pixel height of the display
 * @return null if node is null, else a Rect containing visible bounds
 */
@SuppressLint("CheckResult")
private fun AccessibilityNodeInfo.getVisibleBoundsInScreen(width: Int, height: Int): Rect {
    /*if (node == null) {
        return Rect()
    }*/
    // targeted node's bounds
    val nodeRect = Rect()
    this.getBoundsInScreen(nodeRect)
    val displayRect = Rect(0, 0, width, height)
    nodeRect.intersect(displayRect)

    // On platforms that give us access to the node's window
    val window = Rect()
    if (this.window != null) {
        this.window.getBoundsInScreen(window)
        nodeRect.intersect(window)
    }
    return nodeRect
}
