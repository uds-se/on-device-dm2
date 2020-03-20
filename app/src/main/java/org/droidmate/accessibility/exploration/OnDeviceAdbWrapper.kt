package org.droidmate.accessibility.exploration

import org.droidmate.device.android_sdk.AdbWrapperException
import org.droidmate.device.logcat.TimeFormattedLogcatMessage
import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutorException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OnDeviceAdbWrapper(private val sysCmdExecutor: ISysCmdExecutor) {
    companion object {
        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(OnDeviceAdbWrapper::class.java) }
    }
    /**
     * Command line explanation:
     * -d      : Dumps the logcat to the screen and exits.
     * -b main : Loads the "main" buffer.
     * -v time : Sets the message output format to time (see [2]).
     * *:s     : Suppresses all messages, besides the ones having messageTag.
     * Detailed explanation of the "*:s* filter:
     * * : all messages // except messageTag, overridden by next param, "messageTag"
     * S : SILENT: suppress all messages

     * Logcat reference:
     * [1] http://developer.android.com/tools/help/logcat.html
     * [2] http://developer.android.com/tools/debugging/debugging-log.html#outputFormat
     */
    private fun readMessagesFromLogcat(messageTag: String): List<String> {
        try {
            val commandDescription =
                "Executing adb (Android Debug Bridge) to read from logcat messages tagged: $messageTag"

            val stdStreams = sysCmdExecutor.execute(
                commandDescription, "logcat", "-d", "-b", "main", "-v", "time", "*:s", messageTag
            )

            return stdStreams[0]
                .split(System.lineSeparator())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: SysCmdExecutorException) {
            throw AdbWrapperException(e)
        }
    }

    fun readLogcatMessages(messageTag: String): List<TimeFormattedLogMessageI> {
        log.debug("readLogcatMessages(tag: $messageTag)")
        val messages = readMessagesFromLogcat(messageTag)
        return messages.map { TimeFormattedLogcatMessage.from(it) }
    }

    fun readStatements(): List<List<String>> {
        log.debug("readStatements()")

        /*try {
            val messages = this.tcpClients.getStatements()

            messages.forEach { msg ->
                assert(msg.size == 2) { "Expected 2 messages, received ${msg.size}" }
                assert(msg[0].isNotEmpty()) { "First part of the statement payload was empty" }
                assert(msg[1].isNotEmpty()) { "Second part of the statement payload was empty" }
            }

            return messages
        } catch (e: ApkExplorationException) {
            AndroidDevice.log.error("Error reading statements from monitor TCP server. Proceeding with exploration ${e.message}", e)
            return emptyList()
        }*/

        return emptyList()
    }
}
