package org.droidmate.accessibility.exploration

import java.nio.file.Path
import java.nio.file.Paths
import org.droidmate.device.android_sdk.IApk

class OnDeviceApk(override val packageName: String) : IApk {
    override val applicationLabel: String = packageName
    override val fileName: String = packageName
    override val fileNameWithoutExtension: String = packageName
    override val inlined: Boolean = false
    override val instrumented: Boolean = false
    override val isDummy: Boolean = false
    override var launchableMainActivityName: String = ""
    override val path: Path = Paths.get(".")
}
