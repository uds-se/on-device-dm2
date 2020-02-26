package droidmate.org.accessibility.extensions

import android.graphics.Rect
import droidmate.org.accessibility.utils.debugOut
import org.droidmate.deviceInterface.exploration.Rectangle
import java.util.LinkedList
import kotlin.math.abs

var markedAsOccupied = true

fun Rect.toRectangle() = Rectangle(left, top, abs(width()), abs(height()))

fun Rectangle.toRect() = Rect(leftX,topY,rightX,bottomY)

/** given a set of available window areas ([uncovered]) the (sub-)areas intersecting with [this] are computed,
 * i.e. all areas of this element which are not visible due to overlaying are NOT in the result list.
 * All these intersecting areas are removed from the list of [uncovered] such that later parsed
 * parent and sibling elements can not occupy these areas.*/
fun Rect.visibleAxis(uncovered: MutableCollection<Rect>, isSingleElement: Boolean = false): List<Rect> {
    if (uncovered.isEmpty() || this.isEmpty) return emptyList()
    markedAsOccupied = true
    val newR = LinkedList<Rect>()
    var changed = false
    val del = LinkedList<Rect>()
    return uncovered.mapNotNull {
        val r = Rect()
        if (!it.isEmpty && r.setIntersect(this, it) && !r.isEmpty) {
            changed = true
            if (!isSingleElement || r != it) {  // try detect elements which are for some reason rendered 'behind' an transparent layout element
                del.add(it)
            } else {
                markedAsOccupied = false
            }
            // this probably is done by the apps to determine their definedAsVisible app areas
            newR.apply {
                // add surrounding ones areas
                add(Rect(it.left, it.top, it.right, r.top - 1))// above intersection
                add(Rect(it.left, r.top, r.left - 1, r.bottom))  // left from intersection
                add(Rect(r.right + 1, r.top, it.right, r.bottom)) // right from intersection
                add(Rect(it.left, r.bottom + 1, it.right, it.bottom))  // below from intersection
            }
            r
        } else null
    }.also { res ->
        if (changed) {
            uncovered.addAll(newR)
            uncovered.removeAll { it.isEmpty || del.contains(it) }
            debugOut("for $this intersections=$res modified uncovered=$uncovered", false)
        }
    }
}

/** used only in the specific case where a parent node boundaries are ONLY defined by it's children,
 * meaning it has no own 'uncovered' coordinates, then there is no need to modify the input list
 */
fun Rect.visibleAxisR(uncovered: Collection<Rectangle>): List<Rectangle> {
    if (this.isEmpty) return emptyList()
    return uncovered.mapNotNull {
        val r = Rect()
        if (!it.isEmpty() && r.setIntersect(this, it.toRect()) && !r.isEmpty) {
            r.toRectangle()
        } else null
    }.also { res ->
        //(uncovered is not modified)
        if (uncovered.isNotEmpty()) {
            debugOut("for $this intersections=$res", false)
        }
    }
}