package org.droidmate.accessibility.exploration

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.droidmate.device.android_sdk.AdbWrapperException
import org.droidmate.device.logcat.TimeFormattedLogcatMessage
import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutorException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OnDeviceAdbWrapper(private val sysCmdExecutor: ISysCmdExecutor) {
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(OnDeviceAdbWrapper::class.java) }

        private val logcatTimeInputFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    }

    private var lastStatementTime: LocalDateTime? = null

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
    private fun readMessagesFromLogcat(messageTag: String, minTimeStamp: LocalDateTime?): List<String> {
        try {
            val commandDescription =
                "Executing adb (Android Debug Bridge) to read from logcat messages tagged: $messageTag"

            val command = mutableListOf(
                "logcat", "-d", "-b", "main", "-v", "time", "-s", "'$messageTag'"
            )

            if (minTimeStamp != null) {
                val formattedTimeStamp = logcatTimeInputFormat.format(minTimeStamp)
                command.add("-t")
                command.add("'$formattedTimeStamp'")
            }

            val stdStreams = sysCmdExecutor.execute(commandDescription, *command.toTypedArray())

            return stdStreams[0]
                .split(System.lineSeparator())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: SysCmdExecutorException) {
            throw AdbWrapperException(e)
        }
    }

    @Suppress("SameParameterValue")
    private fun readLogcatMessages(
        messageTag: String,
        minTimeStamp: LocalDateTime?
    ): List<TimeFormattedLogMessageI> {
        log.debug("readLogcatMessages(tag: $messageTag)")
        val messages = readMessagesFromLogcat(messageTag, minTimeStamp)
        return messages.map { TimeFormattedLogcatMessage.from(it) }
    }

    fun readStatements(): List<List<String>> {
        log.debug("readStatements()")

        /*return try {
            val messages = readLogcatMessages("Runtime", lastStatementTime)
            val coverageMessages = messages
                .filter { it.messagePayload.contains("DM2Coverage") }

            if (coverageMessages.isNotEmpty()) {
                lastStatementTime = coverageMessages.last().time
            }

            coverageMessages
                .map {
                    val data = it.messagePayload.split(": ")
                    listOf(data.dropLast(1).last(), data.last())
                }
        } catch (e: Exception) {
            log.error("Error reading statements from logcat. Proceeding with exploration ${e.message}", e)
            emptyList()
        }*/

        return emptyList()
    }
}
