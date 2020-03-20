package org.droidmate.accessibility.exploration

import android.os.Environment
import com.natpryce.konfig.CommandLineOption
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.overriding
import com.natpryce.konfig.parseArgs
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigurationBuilder
import org.droidmate.configuration.ConfigurationException
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.configuration.IConfigurationBuilder
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.logging.Markers
import org.droidmate.misc.EnvironmentConstants
import org.slf4j.LoggerFactory

class OnDeviceConfigurationBuilder : IConfigurationBuilder {
    private val configFile =
        File(Environment.getExternalStorageDirectory().path + "/defaultConfig.properties")

    @Throws(ConfigurationException::class)
    override fun build(cmdLineConfig: Configuration, fs: FileSystem): ConfigurationWrapper {
        val defaultConfig = ConfigurationProperties.fromFile(configFile)

        val customFile = when {
            cmdLineConfig.contains(ConfigProperties.Core.configPath) -> File(cmdLineConfig[ConfigProperties.Core.configPath].path)
            defaultConfig.contains(ConfigProperties.Core.configPath) -> File(defaultConfig[ConfigProperties.Core.configPath].path)
            else -> null
        }

        val config: Configuration =
            // command line
            cmdLineConfig overriding
                    // overrides custom config file
                    (if (customFile?.exists() == true)
                        ConfigurationProperties.fromFile(customFile)
                    else
                        cmdLineConfig) overriding
                    // overrides default config file
                    defaultConfig

        // Set the logging directory for the logback logger as early as possible
        val outputPath = Paths.get(config[ConfigProperties.Output.outputDir].toString())
            .resolve(ConfigurationWrapper.log_dir_name)

        System.setProperty("logsDir", outputPath.toString())
        assert(System.getProperty("logsDir") == outputPath.toString())

        return memoizedBuildConfiguration(config, fs)
    }

    override fun build(
        args: Array<String>,
        fs: FileSystem,
        vararg options: CommandLineOption
    ): ConfigurationWrapper {
        return build(
            parseArgs(
                args,
                *options,
                // Core
                CommandLineOption(
                    ConfigProperties.Core.logLevel,
                    description = "Logging level of the entirety of application. Possible values, comma separated: info, debug, trace, warn, error."
                ),
                CommandLineOption(
                    ConfigProperties.Core.configPath,
                    description = "Path to a custom configuration file, which replaces the default configuration.",
                    short = "config"
                ),
                CommandLineOption(
                    ConfigProperties.Core.hostIp,
                    description = "allows to specify an adb host different from localhost, i.e. to allow container environments to access host devices"
                ),
                // ApiMonitorServer
                CommandLineOption(
                    ConfigProperties.ApiMonitorServer.monitorSocketTimeout,
                    description = "Socket timeout to communicate with the API monitor service."
                ),
                CommandLineOption(
                    ConfigProperties.ApiMonitorServer.monitorUseLogcat,
                    description = "Use logical for API logging instead of TCPServer (deprecated)."
                ),
                CommandLineOption(
                    ConfigProperties.ApiMonitorServer.basePort,
                    description = "The base port for the communication with the the API monitor service. DroidMate communicates over this base port + device index."
                ),
                // ExecutionMode
                CommandLineOption(
                    ConfigProperties.ExecutionMode.inline,
                    description = "If present, instead of normal run, DroidMate will inline all non-inlined apks. Before inlining backup copies of the apks will be created and put into a sub-directory of the directory containing the apks. This flag cannot be combined with another execution mode."
                ),
                CommandLineOption(
                    ConfigProperties.ExecutionMode.explore,
                    description = "Run DroidMate in exploration mode."
                ),
                CommandLineOption(
                    ConfigProperties.ExecutionMode.coverage,
                    description = "If present, instead of normal run, DroidMate will run in 'instrument APK for coverage' mode. This flag cannot be combined with another execution mode."
                ),
                // Deploy
                CommandLineOption(
                    ConfigProperties.Deploy.installApk,
                    description = "Reinstall the app to the device. If the app is not previously installed the exploration will fail"
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.installAux,
                    description = "Reinstall the auxiliary files (UIAutomator and Monitor) to the device. If the auxiliary files are not previously installed the exploration will fail."
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.uninstallApk,
                    description = "Uninstall the APK after the exploration."
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.uninstallAux,
                    description = "Uninstall auxiliary files (UIAutomator and Monitor) after the exploration."
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.replaceResources,
                    description = "Replace the resources from the extracted resources folder upon execution."
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.shuffleApks,
                    description = "ExplorationStrategy the apks in the input directory in a random order."
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.deployRawApks,
                    description = "Deploys apks to device in 'raw' form, that is, without instrumenting them. Will deploy them raw even if instrumented version is available from last run."
                ),
                CommandLineOption(
                    ConfigProperties.Deploy.installMonitor,
                    description = "Install the API monitor into the device."
                ),
                // DeviceCommunication
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.checkAppIsRunningRetryAttempts,
                    description = "Number of attempts to check if an app is running on the device."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.checkAppIsRunningRetryDelay,
                    description = "Timeout for each attempt to check if an app is running on the device in milliseconds."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootAttempts,
                    description = "Determines how often DroidMate checks if a device is available after a reboot."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootFirstDelay,
                    description = "The first timeout after a device rebooted, before its availability will be checked."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootLaterDelays,
                    description = "The non-first timeout after a device rebooted, before its availability will be checked."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.stopAppRetryAttempts,
                    description = "Number of attempts to close an 'application has stopped' dialog."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.stopAppSuccessCheckDelay,
                    description = "Delay after each failed attempt close an 'application has stopped' dialog"
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.waitForCanRebootDelay,
                    description = "Delay (in milliseconds) after an attempt was made to reboot a device, before."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.deviceOperationAttempts,
                    description = "Number of attempts to retry other failed device operations."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.deviceOperationDelay,
                    description = "Delay (in milliseconds) after an attempt was made to perform a device operation, before retrying again."
                ),
                CommandLineOption(
                    ConfigProperties.DeviceCommunication.waitForDevice,
                    description = "Wait for a device to be connected to the PC instead of cancelling the exploration."
                ),
                // Exploration
                CommandLineOption(
                    ConfigProperties.Exploration.apksDir,
                    description = "Directory containing the apks to be processed by DroidMate."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.apksLimit,
                    description = "Limits the number of apks on which DroidMate will run. 0 means no limit."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.apkNames,
                    description = "Filters apps on which DroidMate will be run. Supply full file names, separated by commas, surrounded by square brackets. If the list is empty, it will run on all the apps in the apks dir. Example value: [app1.apk, app2.apk]"
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.deviceIndex,
                    description = "Index of the device to be used (from adb devices). Zero based."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.deviceSerialNumber,
                    description = "Serial number of the device to be used. Mutually exclusive to index."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.runOnNotInlined,
                    description = "Allow DroidMate to run on non-inlined apks."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.launchActivityDelay,
                    description = "Delay (in milliseconds) to wait for the app to load before continuing the exploration after a reset (or exploration start)."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.launchActivityTimeout,
                    description = "Maximum amount of time to be waited for an app to start after a reset in milliseconds."
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.apiVersion,
                    description = "Has to be set to the Android API version corresponding to the (virtual) devices on which DroidMate will run. Currently supported values: api23"
                ),
                CommandLineOption(
                    ConfigProperties.Exploration.widgetActionDelay,
                    description = "Default delay to be applied after interacting with a widget (click, long click, tick)"
                ),
                // Output
                CommandLineOption(
                    ConfigProperties.Output.outputDir,
                    description = "Path to the directory that will contain DroidMate exploration output."
                ),
                CommandLineOption(
                    ConfigProperties.Output.screenshotDir,
                    description = "Path to the directory that will contain the screenshots from an exploration."
                ),
                CommandLineOption(
                    ConfigProperties.Output.reportDir,
                    description = "Path to the directory that will contain the report files."
                ),
                // Strategies
                CommandLineOption(
                    ConfigProperties.Strategies.reset,
                    description = "Enables use of the reset strategy during an exploration."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.explore,
                    description = "Enables use of biased random exploration strategy."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.terminate,
                    description = "Enables use of default terminate strategy."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.back,
                    description = "Enables use of 'press back button' strategy"
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.allowRuntimeDialog,
                    description = "Enables use of strategy to always click 'Allow' on permission dialogs."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.denyRuntimeDialog,
                    description = "Enables use of strategy to always click 'Deny' on permission dialogs."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.playback,
                    description = "Enables use of playback strategy (if a playback model is provided)."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.dfs,
                    description = "Enables use of Depth-First-Search strategy."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.rotateUI,
                    description = "Enables use of Rotate UI strategy."
                ),
                CommandLineOption(
                    ConfigProperties.Strategies.minimizeMaximize,
                    description = "Enables use of Minimize-Maximize strategy to attempt to close the app and reopen it on the same screen."
                ),
                // Strategies parameters
                CommandLineOption(
                    ConfigProperties.Strategies.Parameters.uiRotation,
                    description = "Value of the UI rotation for Rotate UI strategy. Valid values are: 0, 90, 180, 270. Other values will be rounded to one of these."
                ),

                // Selectors
                CommandLineOption(
                    ConfigProperties.Selectors.pressBackProbability,
                    description = "Probability of randomly pressing the back button while exploring. Set to 0 to disable the press back strategy."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.playbackModelDir,
                    description = "Directory of a previous exploration model. Required for playback."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.resetEvery,
                    description = "Number of actions to automatically reset the exploration from its initial activity. Set to 0 to disable."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.actionLimit,
                    description = "How many actions the GUI exploration strategy can conduct before terminating."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.timeLimit,
                    description = "How long the exploration of any given apk should take, in milli seconds. If set to 0, instead actionsLimit will be used."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.randomSeed,
                    description = "The seed for a random generator used by a random-clicking GUI exploration strategy. If null, a seed will be randomized."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.stopOnExhaustion,
                    description = "Terminate exploration when all widgets have been explored at least 1x."
                ),
                CommandLineOption(
                    ConfigProperties.Selectors.dfs,
                    description = "Use Depth-First-Search strategy, if the strategy is registered."
                ),
                // Report
                CommandLineOption(
                    ConfigProperties.Report.inputDir,
                    description = "Path to the directory containing report input. The input is to be DroidMate exploration output."
                ),
                CommandLineOption(
                    ConfigProperties.Report.includePlots,
                    description = "Include plots on reports (requires gnu plot)."
                ),
                // UiAutomatorServer
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.startTimeout,
                    description = "How long DroidMate should wait, in milliseconds, for message on logcat confirming that UiAutomatorDaemonServer has started on android (virtual) device."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.waitForIdleTimeout,
                    description = "Timeout for a device to be idle an operation."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.waitForInteractableTimeout,
                    description = "Timeout for a widget to be available after an operation."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.enablePrintOuts,
                    description = "Enable or disable debug and performance outputs on the device output (in the LogCat)."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.socketTimeout,
                    description = "Socket timeout to communicate with the UiDaemonServer."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.basePort,
                    description = "The base port for the communication with the devices. DroidMate communicates over this base port + device index."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.delayedImgFetch,
                    description = "Option to allow for faster exploration by delaying screen-shot fetch to an asynchronous call."
                ),
                CommandLineOption(
                    ConfigProperties.UiAutomatorServer.imgQuality,
                    description = "Quality of the image to be stored for fetching."
                ),
                // StatementCoverage
                CommandLineOption(
                    StatementCoverageMF.Companion.StatementCoverage.enableCoverage,
                    description = "If true, the statement coverage of the exploration will be measured. This requires the apk to be instrumented with 'coverage' mode."
                ),
                CommandLineOption(
                    StatementCoverageMF.Companion.StatementCoverage.onlyCoverAppPackageName,
                    description = "Only instrument statement coverage for statements belong inside the app package name scope. Libraries with other package names will be ignored. Be aware that this filtering might not be always correct."
                ),
                CommandLineOption(
                    StatementCoverageMF.Companion.StatementCoverage.coverageDir,
                    description = "Path to the directory that will contain the coverage data."
                ),
                CommandLineOption(
                    org.droidmate.explorationModel.config.ConfigProperties.Output.debugMode,
                    description = "enable debug output"
                )
            ).first, fs
        )
    }

    @Throws(ConfigurationException::class)
    override fun build(
        args: Array<String>,
        vararg options: CommandLineOption
    ): ConfigurationWrapper = build(args, FileSystems.getDefault(), *options)

    companion object {
        private val log by lazy { LoggerFactory.getLogger(ConfigurationBuilder::class.java) }

        @JvmStatic
        private fun memoizedBuildConfiguration(cfg: Configuration, fs: FileSystem): ConfigurationWrapper {
            log.debug("memoizedBuildConfiguration(args, fileSystem)")

            return bindAndValidate(ConfigurationWrapper(cfg, fs))
        }

        @JvmStatic
        @Throws(ConfigurationException::class)
        private fun bindAndValidate(config: ConfigurationWrapper): ConfigurationWrapper {
            try {
                setupResourcesAndPaths(config)
                validateExplorationSettings(config)
                normalizeAndroidApi(config)
            } catch (e: ConfigurationException) {
                throw e
            }

            logConfigurationInEffect(config)

            return config
        }

        @JvmStatic
        private fun normalizeAndroidApi(config: ConfigurationWrapper) {
            // Currently supports only API23 as configuration (works with API 24, 25 and 26 as well)
            assert(config[ConfigProperties.Exploration.apiVersion] == ConfigurationWrapper.api23)
        }

        @JvmStatic
        private fun validateExplorationSettings(cfg: ConfigurationWrapper) {
            validateExplorationStrategySettings(cfg)

            val apkNames = Files.list(cfg.getPath(cfg[ConfigProperties.Exploration.apksDir]))
                .filter { it.toString().endsWith(".apk") }
                .map { it.fileName.toString() }

            if (cfg[ConfigProperties.Deploy.deployRawApks] && arrayListOf("inlined", "monitored").any { apkNames.anyMatch { s -> s.contains(it) } })
                throw ConfigurationException(
                    "DroidMate was instructed to deploy raw apks, while the apks dir contains an apk " +
                            "with 'inlined' or 'monitored' in its name. Please do not mix such apk with raw apks in one dir.\n" +
                            "The searched apks dir path: ${cfg.getPath(cfg[ConfigProperties.Exploration.apksDir]).toAbsolutePath()}")
        }

        @JvmStatic
        private fun validateExplorationStrategySettings(cfg: ConfigurationWrapper) {
            if (cfg[ConfigProperties.Selectors.randomSeed] == -1L) {
                log.info("Generated random seed: ${cfg.randomSeed}")
            }
        }

        @JvmStatic
        @Throws(ConfigurationException::class)
        private fun setupResourcesAndPaths(cfg: ConfigurationWrapper) {
            cfg.droidmateOutputDirPath = cfg.getPath(cfg[ConfigProperties.Output.outputDir]).toAbsolutePath()
            cfg.resourceDir = cfg.droidmateOutputDirPath
                .resolve(EnvironmentConstants.dir_name_temp_extracted_resources)
            cfg.droidmateOutputReportDirPath = cfg.droidmateOutputDirPath
                .resolve(cfg[ConfigProperties.Output.reportDir]).toAbsolutePath()
            cfg.reportInputDirPath = cfg.getPath(cfg[ConfigProperties.Report.inputDir]).toAbsolutePath()

            cfg.uiautomator2DaemonApk = Paths.get(".")
            log.debug("Using uiautomator2-daemon.apk located at ${cfg.uiautomator2DaemonApk}")

            cfg.uiautomator2DaemonTestApk = Paths.get(".")
            log.debug("Using uiautomator2-daemon-test.apk located at ${cfg.uiautomator2DaemonTestApk}")

            cfg.monitorApk = null
            log.debug("Using ${EnvironmentConstants.monitor_apk_name} located at ${cfg.monitorApk}")

            cfg.apiPoliciesFile = null
            log.debug("Using ${EnvironmentConstants.api_policies_file_name} located at ${cfg.apiPoliciesFile}")

            cfg.apksDirPath = cfg.getPath(cfg[ConfigProperties.Exploration.apksDir]).toAbsolutePath()

            Files.createDirectories(cfg.apksDirPath)
            log.debug("Reading APKs from: ${cfg.apksDirPath.toAbsolutePath()}")

            if (Files.notExists(cfg.droidmateOutputDirPath)) {
                Files.createDirectories(cfg.droidmateOutputDirPath)
                log.info("Writing output to: ${cfg.droidmateOutputDirPath}")
            }
        }

        /**
         * To keep the source DRY, we use apache's ReflectionToStringBuilder, which gets the field names and values using
         * reflection.
         */
        @JvmStatic
        private fun logConfigurationInEffect(config: Configuration) {

            // The customized display style strips the output of any data except the field name=value pairs.
            val displayStyle = StandardToStringStyle()
            displayStyle.isArrayContentDetail = true
            displayStyle.isUseClassName = false
            displayStyle.isUseIdentityHashCode = false
            displayStyle.contentStart = ""
            displayStyle.contentEnd = ""
            displayStyle.fieldSeparator = System.lineSeparator()

            val configurationDump = ReflectionToStringBuilder(config, displayStyle).toString()
                .split(System.lineSeparator())
                .sorted()

            val sb = StringBuilder()
            sb.appendln("--------------------------------------------------------------------------------")
                .appendln("Working dir:   ${System.getProperty("user.dir")}")
                .appendln("")
                .appendln("Configuration dump:")
                .appendln("")

            configurationDump.forEach { sb.appendln(it) }

            sb.appendln("")
                .appendln("End of configuration dump")
                .appendln("--------------------------------------------------------------------------------")

            log.debug(Markers.runData, sb.toString())
        }
    }
}
