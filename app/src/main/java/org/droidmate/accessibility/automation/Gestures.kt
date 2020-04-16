package org.droidmate.accessibility.automation

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Matrix
import android.graphics.Path

/**
 * Extracted from: https://bit.ly/2Jh85O4
 */
object Gestures {
    /**
     * Create a description of a click gesture
     *
     * @param x The x coordinate to click. Must not be negative.
     * @param y The y coordinate to click. Must not be negative.
     *
     * @return A description of a click at (x, y)
     */
    fun createClick(x: Int, y: Int, duration: Long = 100): GestureDescription {
        val clickPath = Path()
        clickPath.moveTo(x.toFloat(), y.toFloat())
        clickPath.lineTo(x.toFloat() + 1, y.toFloat())
        val builder = GestureDescription.Builder()
        return builder
            .addStroke(StrokeDescription(clickPath, 0, duration))
            .build()
    }

    /**
     * Create a description of a long click gesture
     *
     * @param x The x coordinate to click. Must not be negative.
     * @param y The y coordinate to click. Must not be negative.
     *
     * @return A description of a click at (x, y)
     */
    fun createLongClick(x: Int, y: Int, duration: Long = 1000): GestureDescription {
        return createClick(x, y, duration)
    }

    /**
     * Create a description of a swipe gesture
     *
     * @param startX The x coordinate of the starting point. Must not be negative.
     * @param startY The y coordinate of the starting point. Must not be negative.
     * @param endX The x coordinate of the ending point. Must not be negative.
     * @param endY The y coordinate of the ending point. Must not be negative.
     * @param duration The time, in milliseconds, to complete the gesture. Must not be negative.
     *
     * @return A description of a swipe from ({@code startX}, {@code startY}) to
     * ({@code endX}, {@code endY}) that takes {@code duration} milliseconds. Returns {@code null}
     * if the path specified for the swipe is invalid.
     */
    fun createSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): GestureDescription {
        val swipePath = Path()
        swipePath.moveTo(startX.toFloat(), startY.toFloat())
        swipePath.lineTo(endX.toFloat(), endY.toFloat())
        val builder = GestureDescription.Builder()
        return builder
            .addStroke(StrokeDescription(swipePath, 0, duration))
            .build()
    }

    /**
     * Create a description for a pinch (or zoom) gesture.
     *
     * @param centerX The x coordinate of the center of the pinch. Must not be negative.
     * @param centerY The y coordinate of the center of the pinch. Must not be negative.
     * @param startSpacing The spacing of the touch points at the beginning of the gesture. Must not
     * be negative.
     * @param endSpacing The spacing of the touch points at the end of the gesture. Must not be
     * negative.
     * @param orientation The angle, in degrees, of the gesture. 0 represents a horizontal pinch
     * @param duration The time, in milliseconds, to complete the gesture. Must not be negative.
     *
     * @return A description of a pinch centered at ({code centerX}, `centerY`) that starts
     * with the touch points spaced by `startSpacing` and ends with them spaced by
     * `endSpacing` that lasts `duration` ms. Returns `null` if either path
     * specified for the pinch is invalid.
     */
    fun createPinch(
        centerX: Int,
        centerY: Int,
        startSpacing: Int,
        endSpacing: Int,
        orientation: Float,
        duration: Long
    ): GestureDescription {
        require(!(startSpacing < 0 || endSpacing < 0)) {
            "Pinch spacing cannot be negative"
        }

        val startPoint1 = FloatArray(2)
        val endPoint1 = FloatArray(2)
        val startPoint2 = FloatArray(2)
        val endPoint2 = FloatArray(2)

        /* Build points for a horizontal gesture centered at the origin */
        startPoint1[0] = (startSpacing / 2).toFloat()
        startPoint1[1] = 0.toFloat()
        endPoint1[0] = (endSpacing / 2).toFloat()
        endPoint1[1] = 0.toFloat()
        startPoint2[0] = (-startSpacing / 2).toFloat()
        startPoint2[1] = 0.toFloat()
        endPoint2[0] = (-endSpacing / 2).toFloat()
        endPoint2[1] = 0.toFloat()

        /* Rotate and translate the points */
        val matrix = Matrix()
        matrix.setRotate(orientation)
        matrix.postTranslate(centerX.toFloat(), centerY.toFloat())
        matrix.mapPoints(startPoint1)
        matrix.mapPoints(endPoint1)
        matrix.mapPoints(startPoint2)
        matrix.mapPoints(endPoint2)

        val path1 = Path()
        path1.moveTo(startPoint1[0], startPoint1[1])
        path1.lineTo(endPoint1[0], endPoint1[1])
        val path2 = Path()
        path2.moveTo(startPoint2[0], startPoint2[1])
        path2.lineTo(endPoint2[0], endPoint2[1])

        val builder = GestureDescription.Builder()
        return builder
            .addStroke(StrokeDescription(path1, 0, duration))
            .addStroke(StrokeDescription(path2, 0, duration))
            .build()
    }
}
