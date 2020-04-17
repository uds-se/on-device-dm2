package org.droidmate.accessibility.automation.parsing

import android.view.accessibility.AccessibilityNodeInfo

@Suppress("MemberVisibilityCanBePrivate")
object UiSelector {
    @JvmStatic
    val isWebView: SelectorCondition =
        { it, _ -> it.packageName == "android.webkit.WebView" || it.className == "android.webkit.WebView" }

    @JvmStatic
    val permissionRequest: SelectorCondition =
        { node, _ -> node.viewIdResourceName == "com.android.packageinstaller:id/permission_allow_button" }

    @JvmStatic
    val ignoreSystemElem: SelectorCondition =
        { node, _ ->
            node.viewIdResourceName?.let { !it.startsWith("com.android.systemui") } ?: false
        }

    // TODO check if need special case for packages "com.android.chrome" ??
    @JvmStatic
    val isActionable: SelectorCondition = { it, _ ->
        it.isEnabled &&
                it.isVisibleToUser &&
                (it.isClickable ||
                        it.isCheckable ||
                        it.isLongClickable ||
                        it.isScrollable ||
                        it.isEditable ||
                        it.isFocusable)
    }

    @JvmStatic
    val actionableAppElem = { node: AccessibilityNodeInfo, xpath: String ->
        // look for internal elements instead of WebView layouts
        ignoreSystemElem(node, xpath) &&
                !isWebView(node, xpath) &&
                (isActionable(node, xpath) || permissionRequest(node, xpath))
    }

    @JvmStatic
    val isHomeScreen: SelectorCondition =
        { node, _ ->
            when {
                node.packageName?.contains("systemui") ?: false -> true
                node.packageName?.contains("android.launcher") ?: false -> true
                else -> false
            }
        }

    @JvmStatic
    val isSearch: SelectorCondition =
        { node, _ ->
            when {
                node.packageName?.contains("com.google.android.googlequicksearchbox") ?: false -> true
                else -> false
            }
        }
}

typealias SelectorCondition = (AccessibilityNodeInfo, xPath: String) -> Boolean
