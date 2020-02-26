package droidmate.org.accessibility.utils

import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.parsing.SiblingNodeComparator
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.droidmate.deviceInterface.exploration.Rectangle
import org.xmlpull.v1.XmlSerializer

const val TAG = "droidmate/Accessibility"
val backgroundScope = CoroutineScope(Dispatchers.Default + CoroutineName("background") + Job())   //same dispatcher as GlobalScope.launch

val api = Build.VERSION.SDK_INT

fun visibleOuterBounds(r: Collection<Rect>): Rectangle = with(r.filter { !it.isEmpty }) {
	val pl = minBy { it.left }
	val pt = minBy { it.top }
	val pr = maxBy { it.right }
	val pb = maxBy { it.bottom }
	return Rectangle.create(pl?.left ?: 0, pt?.top ?: 0, right = pr?.right ?: 0, bottom = pb?.bottom ?: 0)
}

fun XmlSerializer.addAttribute(name: String, value: Any?){
	val valueString = when (value){
		null -> "null"
		is Int -> value.toString()
		is Boolean -> java.lang.Boolean.toString(value)
		else -> safeCharSeqToString(value.toString().replace("<","&lt").replace(">","&gt"))
	}
	try {
		attribute("", name, valueString)
	} catch (e: Throwable) {
		throw java.lang.RuntimeException("'$name':'$value' contains illegal characters")
	}
}

fun safeCharSeqToString(cs: CharSequence?): String {
	return if (cs == null) {
		""
	} else {
		stripInvalidXMLChars(cs).trim()
	}
}

/** 
 * According to:
 * http://www.w3.org/TR/xml11/#charsets
 * 			[#x1-#x8], [#xB-#xC], [#xE-#x1F], [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF],
 * 			[#x1FFFE-#x1FFFF], [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF],
 * 			[#x4FFFE-#x4FFFF], [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF],
 * 			[#x7FFFE-#x7FFFF], [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF],
 * 			[#xAFFFE-#xAFFFF], [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF],
 * 			[#xDFFFE-#xDFFFF], [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF],
 * 			[#x10FFFE-#x10FFFF].
**/
private fun stripInvalidXMLChars(cs: CharSequence): String {
	val ret = StringBuffer()

	for (element in cs) {
		val ch = element.toInt()

		if (ch in 0x1..0x8 || ch in 0xB..0xC || ch in 0xE..0x1F ||
			ch == 0x14 ||
			ch in 0x7F..0x84 || ch in 0x86..0x9f ||
			ch in 0xFDD0..0xFDDF || ch in 0x1FFFE..0x1FFFF ||
			ch in 0x2FFFE..0x2FFFF || ch in 0x3FFFE..0x3FFFF ||
			ch in 0x4FFFE..0x4FFFF || ch in 0x5FFFE..0x5FFFF ||
			ch in 0x6FFFE..0x6FFFF || ch in 0x7FFFE..0x7FFFF ||
			ch in 0x8FFFE..0x8FFFF || ch in 0x9FFFE..0x9FFFF ||
			ch in 0xAFFFE..0xAFFFF || ch in 0xBFFFE..0xBFFFF ||
			ch in 0xCFFFE..0xCFFFF || ch in 0xDFFFE..0xDFFFF ||
			ch in 0xEFFFE..0xEFFFF || ch in 0xFFFFE..0xFFFFF ||
			ch in 0x10FFFE..0x10FFFF)
			ret.append(".")
		else
			ret.append(ch.toChar())
	}
	return ret.toString()
}

/** @return true if children should be recursively traversed */
typealias NodeProcessor = suspend (rootNode: AccessibilityNodeInfo, index: Int, xPath: String)	-> Boolean
typealias PostProcessor<T> = (rootNode: AccessibilityNodeInfo)	-> T

suspend fun<T> processTopDown(node: AccessibilityNodeInfo, index: Int=0, processor: NodeProcessor, postProcessor: PostProcessor<T>, parentXpath: String = "//"):T {
	val nChildren = node.childCount
	val xPath = parentXpath + "${node.className}[${index + 1}]"
	val proceed = processor(node, index, xPath)

	try {
		if (proceed)
			(0 until nChildren).map { i -> Pair(i, node.getChild(i)) }
				.sortedWith(SiblingNodeComparator)
				.map { (i, child) ->
					processTopDown(child, i, processor, postProcessor, "$xPath/").also {
						child.recycle()
					}
				}
	} catch (e: Exception) {    // the accessibilityNode service may throw this if the node is no longer up-to-date
		Log.w("droidmate/UiDevice", "error child of $parentXpath node no longer available ${e.localizedMessage}")
		node.refresh()
	}

	return postProcessor(node)
}