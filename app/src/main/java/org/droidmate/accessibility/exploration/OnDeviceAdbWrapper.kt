package org.droidmate.accessibility.exploration

import java.text.SimpleDateFormat
import java.util.BitSet
import java.util.Date
import org.droidmate.accessibility.automation.utils.debugT
import org.droidmate.device.ITcpClientBase
import org.droidmate.device.TcpClientBase
import org.droidmate.device.TcpServerUnreachableException
import org.droidmate.misc.MonitorConstants
import org.droidmate.misc.MonitorConstants.Companion.monitor_time_formatter_locale
import org.droidmate.misc.MonitorConstants.Companion.monitor_time_formatter_pattern
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OnDeviceAdbWrapper {
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(OnDeviceAdbWrapper::class.java) }
    }

    private val coverageMonitorClient: ITcpClientBase<String, BitSet> =
        TcpClientBase("localhost", 60000)

    private val monitorTimeFormatter = SimpleDateFormat(
        monitor_time_formatter_pattern,
        monitor_time_formatter_locale
    )
    private fun getNowDate(): String {
        val nowDate = Date()
        return monitorTimeFormatter.format(nowDate)
    }

    fun readStatements(): List<List<String>> {
        return try {
            val data = debugT(
                "readStatements", {
                    coverageMonitorClient.queryServer(MonitorConstants.srvCmd_get_statements, 59701)
                },
                inMillis = true
            )

            val result = mutableListOf<List<String>>()
            val nowDate = getNowDate()
            var i = data.nextSetBit(0)
            while (i >= 0) {

                // operate on index i here
                if (i == Int.MAX_VALUE) {
                    break // or (i+1) would overflow
                }
                result.add(listOf(nowDate, i.toString()))
                i = data.nextSetBit(i + 1)
            }
            return result
        } catch (ignored: TcpServerUnreachableException) {
            log.trace("None of the monitor TCP servers were available while obtaining API logs.")
            emptyList()
        }
    }
}
