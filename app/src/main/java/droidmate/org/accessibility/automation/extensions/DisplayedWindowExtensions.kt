package droidmate.org.accessibility.automation.extensions

import droidmate.org.accessibility.automation.parsing.DisplayedWindow

fun List<DisplayedWindow>.isHomeScreen() = count { it.isApp() }.let { nAppW ->
    nAppW == 0 || (nAppW == 1 && any { it.isLauncher && it.isApp() })
}

fun List<DisplayedWindow>.invalid() =
    isEmpty() || none { it.isLauncher || (it.isApp() && it.isExtracted()) }
