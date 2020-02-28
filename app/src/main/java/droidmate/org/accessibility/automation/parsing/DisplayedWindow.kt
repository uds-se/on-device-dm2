package droidmate.org.accessibility.automation.parsing

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import droidmate.org.accessibility.automation.extensions.visibleAxis
import droidmate.org.accessibility.automation.utils.api
import droidmate.org.accessibility.automation.utils.debugOut
import droidmate.org.accessibility.automation.utils.visibleOuterBounds
import org.droidmate.deviceInterface.exploration.AppWindow

private const val debug = false

/** on device we need additional information to compute UiElement visibility this information is wrapped within this class.
 * It is the Callers responsibility to recycle [root] when it is no longer needed.
 *
 * @param area this area coordinates are used to compute correct UiElement visibility in the Ui extraction/parsing
 * @param rootNode is null if we do not want to extract the elements from this window, i.e. if it is a system window like status/navigation bar
 * */
data class DisplayedWindow(
	val w: AppWindow,
    val initialArea: List<Rect>,
    var rootNode: AccessibilityNodeInfo?,
	val isKeyboard: Boolean,
    val layer: Int,
	val bounds: Rect,
	val windowType:Int,val isLauncher: Boolean
) {
	var area: MutableList<Rect> = mutableListOf()

	fun isExtracted() = rootNode != null
	fun isApp() = windowType == AccessibilityWindowInfo.TYPE_APPLICATION

	fun canReuseFor(newW: AccessibilityWindowInfo): Boolean {
		val b = Rect()
		newW.getBoundsInScreen(b)
		val canReuse = w.windowId == newW.id && layer == newW.layer
				&& bounds == b
				&& (!isExtracted() ||
				(newW.root != null && w.pkgName == newW.root.packageName))

		if (!canReuse) {
			debugOut("extracted = ${isExtracted()} newW = ${newW.layer}, $b")
		}

		return canReuse
	}

	companion object {
		operator fun invoke(
			wInfo: AccessibilityWindowInfo,
			uncoveredCoordinates: MutableList<Rect>,
			outRect: Rect,
			isKeyboard: Boolean,
			deviceRoot: AccessibilityNodeInfo? = null
		): DisplayedWindow {

			val root: AccessibilityNodeInfo? =
				deviceRoot ?: wInfo.root // REMARK: do not recycle root nodes, only instances requested via getChild
			// compute which points on the screen are occupied by this window (and are not occupied by a higher layer window)
			debugOut(
				"start window visibility computation for ${root?.packageName} $outRect" +
						"type=${wInfo.type} " +
						if (api >= 24) "title='${wInfo.title}' " else "" +
								"desc='${root?.contentDescription}', accF=${wInfo.isAccessibilityFocused}, isF=${wInfo.isFocused}, ${wInfo.layer}, isActive=${wInfo.isActive}"
				, debug
			)
			val area = outRect.visibleAxis(uncoveredCoordinates)
			debugOut("create ${wInfo.id} $area", debug)
			return DisplayedWindow(
				AppWindow(
					wInfo.id,
					root?.packageName?.toString() ?: "systemWindow_WithoutRoot",
					wInfo.isFocused,
					wInfo.isAccessibilityFocused,
					visibleOuterBounds(area)
				),
				area,
				isKeyboard = isKeyboard,  // we cannot use type = INPUT_METHOD as this does not work on all devices e.g. Nexus 5X
				rootNode = if (wInfo.type == AccessibilityWindowInfo.TYPE_APPLICATION || wInfo.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
					root
				} else {
					null
				},
				layer = wInfo.layer,
				bounds = outRect,
				windowType = wInfo.type,
				isLauncher = when {
					api >= 24 && wInfo.title?.contains("systemui") ?: false -> true
					root?.packageName?.contains("systemui") ?: false -> true
					api >= 24 && (wInfo.title?.contains("Launcher") ?: false) -> true
					root?.packageName?.contains("android.launcher") ?: false -> true
					else -> false // REMARK for some devices isKeyboard may be true for the launcher window
				}
			).also {
				//				wInfo.recycle()
			}
		}
	}
}