package droidmate.org.accessibility.automation.parsing

import java.util.LinkedList

data class Coordinate(val x: Int, val y: Int)

operator fun Coordinate.rangeTo(c: Coordinate): Collection<Coordinate> {
    return LinkedList<Coordinate>().apply {
        (x..c.x).forEach { px ->
            (y..c.y).forEach { py ->
                add(Coordinate(px, py))
            }
        }
    }
}
