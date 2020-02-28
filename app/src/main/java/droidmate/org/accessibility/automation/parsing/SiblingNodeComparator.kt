package droidmate.org.accessibility.automation.parsing

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.automation.utils.api

object SiblingNodeComparator: Comparator<Pair<Int, AccessibilityNodeInfo>> {
    private var parentBounds: Rect = Rect()
    /**
     * this function has to be called for the parent before using the compare function to sort its children,
     * in order to be able to detect 'empty' layout frames
     */
    fun initParentBounds(p: AccessibilityNodeInfo) {
        p.getBoundsInScreen(parentBounds)
    }

    /**
     * Comparator to be applied to a set of child nodes to determine their processing order.
     * Elements at the beginning of the list should be processed first,
     * since they are rendered 'on top' of their siblings OR in special cases the
     * sibling is assumed to be a transparent/framing element similar to insets which does
     * does not hide any other elements but is only used by the app for layout scaling features.
     *
     * @param o1 the first object to be compared.
     * @param o2 the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the
     *         first argument is less than, equal to, or greater than the
     *         second.
     * @throws NullPointerException if an argument is null and this
     *         comparator does not permit null arguments
     * @throws ClassCastException if the arguments' types prevent them from
     *         being compared by this comparator.
     */
    override fun compare(o1: Pair<Int, AccessibilityNodeInfo>?, o2: Pair<Int, AccessibilityNodeInfo>?): Int {
        if (o1 == null || o2 == null) throw NullPointerException("this comparator should not be called on nullable nodes")
        var n1 = o1.second
        val r1 = Rect().apply { n1.getBoundsInScreen(this) }
        var n2 = o2.second
        val r2 = Rect().apply { n2.getBoundsInScreen(this) }
        var swapped = false
        val (c1, c2) = when {
            o1.drawOrder() > o2.drawOrder() -> Pair(o1, o2)
            o1.drawOrder() < o2.drawOrder() -> Pair(o2, o1).also { swapped = true }
            // do not swap if they have the same drawing order to keep the order by index for equal drawing order
            else -> Pair(o1, o2)
        }
        // in case o1 and o2 were swapped update the rectangle variables
        if (swapped) {
            c1.second.getBoundsInScreen(r1)
            c2.second.getBoundsInScreen(r2)
            n1 = c1.second  // just for better readability
            n2 = c2.second
        }
        // check if c1 may be an empty/transparent frame element which is rendered in front of c2 but should not hide its sibling
        val c1IsTransparent =
            (r1 == parentBounds) && r1.contains(r2) &&  // the sibling c2 is completely hidden behind c1
                    n1.childCount == 0 && n2.childCount > 0 &&  // c2 would have child nodes but c1 does not
                    n1.isEnabled && n2.isEnabled && n1.isVisibleToUser && n2.isVisibleToUser && // if one node is not visible it does not matter which one is processed first
                    o1.first < o2.first    // check if the drawing order is different from the order defined via its hierarchy index => TODO check if this condition is too restrictive

        return when {
            c1IsTransparent && swapped -> -2
            c1IsTransparent -> 2
            else -> o2.drawOrder().compareTo(o1.drawOrder())  // inverted order since we want nodes with higher drawing Order to be processed first
        }
    }

    private fun Pair<Int, AccessibilityNodeInfo>.drawOrder() =
        this.let { (idx, node) -> if (api >= 24) node.drawingOrder else idx }
}
