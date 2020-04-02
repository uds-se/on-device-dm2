package org.droidmate.accessibility.exploration

import java.time.format.DateTimeFormatter
import java.util.LinkedList
import kotlin.collections.ArrayList
import org.droidmate.accessibility.automation.utils.debugT
import org.droidmate.device.ITcpClientBase
import org.droidmate.device.TcpClientBase
import org.droidmate.device.TcpServerUnreachableException
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.MonitorConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OnDeviceAdbWrapper(private val sysCmdExecutor: ISysCmdExecutor) {
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(OnDeviceAdbWrapper::class.java) }

        private val logcatTimeInputFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    }

    private val coverageMonitorClient: ITcpClientBase<String, LinkedList<ArrayList<String>>> =
        TcpClientBase("localhost", 60000)

    fun readStatements(): List<List<String>> {
        return try {
            debugT(
                "readStatements", {
                    coverageMonitorClient.queryServer(MonitorConstants.srvCmd_get_statements, 59701)
                },
                inMillis = true
            )
        } catch (ignored: TcpServerUnreachableException) {
            log.trace("None of the monitor TCP servers were available while obtaining API logs.")
            emptyList()
        }
    }
}
