package org.droidmate.accessibility.automation

import android.view.accessibility.AccessibilityNodeInfo
import org.droidmate.accessibility.automation.parsing.SelectorCondition

interface IKeyboardEngine : IEngine {
    fun isKeyboard(node: AccessibilityNodeInfo?): Boolean
    fun selectKeyboardRoot(minY: Int, width: Int, height: Int): SelectorCondition
}
