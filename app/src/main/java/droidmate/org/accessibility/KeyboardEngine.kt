package droidmate.org.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import droidmate.org.accessibility.IEngine.Companion.debug
import droidmate.org.accessibility.extensions.getBounds
import droidmate.org.accessibility.parsing.SelectorCondition
import droidmate.org.accessibility.utils.debugOut

class KeyboardEngine(private val context: Context): IKeyboardEngine {
    private val keyboardPackages by lazy { computeKeyboardPackages() }

    override fun isKeyboard(node: AccessibilityNodeInfo?) : Boolean {
        return keyboardPackages.contains(node?.packageName)
    }

    override fun selectKeyboardRoot(minY: Int, width: Int, height: Int): SelectorCondition {
        return { node, _ ->
            val b = node.getBounds(width, height)
            debugOut("check $b", false)
            b.top > minY
        }
    }

    private fun computeKeyboardPackages(): List<String> {
        val inputMng = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return inputMng.inputMethodList
            .map {
                debugOut("computed keyboard packages ${it.packageName}", debug)
                it.packageName
            }
    }
}