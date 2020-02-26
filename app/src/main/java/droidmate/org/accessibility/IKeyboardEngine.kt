package droidmate.org.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.parsing.SelectorCondition

interface IKeyboardEngine: IEngine {
    fun isKeyboard(node: AccessibilityNodeInfo?) : Boolean
    fun selectKeyboardRoot(minY: Int, width: Int, height: Int): SelectorCondition


}