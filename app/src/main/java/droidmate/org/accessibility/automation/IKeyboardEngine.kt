package droidmate.org.accessibility.automation

import android.view.accessibility.AccessibilityNodeInfo
import droidmate.org.accessibility.automation.IEngine
import droidmate.org.accessibility.automation.parsing.SelectorCondition

interface IKeyboardEngine: IEngine {
    fun isKeyboard(node: AccessibilityNodeInfo?) : Boolean
    fun selectKeyboardRoot(minY: Int, width: Int, height: Int): SelectorCondition


}