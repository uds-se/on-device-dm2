package org.droidmate.accessibility.exploration

import com.natpryce.konfig.ConfigurationProperties
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.droidmate.accessibility.automation.AutomationEngine
import org.droidmate.accessibility.automation.utils.imgDir
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.error.DeviceExceptionMissing
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.device.logcat.ApiLogcatMessageListExtensions
import org.droidmate.deviceInterface.exploration.ActionQueue
import org.droidmate.deviceInterface.exploration.Click
import org.droidmate.deviceInterface.exploration.EmptyAction
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.deviceInterface.exploration.TextInsert
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.launchApp
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.strategy.ExplorationStrategyPool
import org.droidmate.explorationModel.ModelFeatureI
import org.droidmate.explorationModel.config.ConfigProperties.ModelProperties.path.cleanDirs
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.ModelProvider
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.FailableExploration
import org.droidmate.misc.ISysCmdExecutor
import org.droidmate.misc.SysCmdExecutor
import org.droidmate.misc.deleteDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class OnDeviceExploration<M, S, W>(
    private val automationEngine: AutomationEngine,
    private val cfg: ConfigurationWrapper,
    private val strategyProvider: ExplorationStrategyPool,
    private var modelProvider: ModelProvider<M>,
    private val watcher: MutableList<ModelFeatureI> = mutableListOf()
) where M : AbstractModel<S, W>, S : State<W>, W : Widget {
    companion object {
        @JvmStatic
        protected val log: Logger by lazy { LoggerFactory.getLogger(OnDeviceExploration::class.java) }
    }

    private val sysCmdExecutor: ISysCmdExecutor by lazy { SysCmdExecutor() }
    private lateinit var explorationContext: ExplorationContext<M, S, W>
    // Construct initial action and execute it on the device to obtain initial result.
    var action: ExplorationAction = EmptyAction
    lateinit var result: ActionResult
    var isFirst = true

    private val strategyScheduler by lazy {
        strategyProvider.apply {
            init(cfg, explorationContext)
        }
    }

    suspend fun setup(app: IApk) {
        if (cfg[cleanDirs]) {
            cleanOutputDir(cfg)
        }

        val reportDir = cfg.droidmateOutputReportDirPath

        if (!Files.exists(reportDir)) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(reportDir)
            }
        }

        assert(Files.exists(reportDir)) { "Unable to create report directory ($reportDir)" }

        // initialize the config and clear the 'currentModel' from the provider if any
        val modelConfig = ConfigurationProperties.fromFile(OnDeviceConfigurationBuilder.configFile)
        modelProvider.init(ModelConfig(modelConfig, appName = AutomationEngine.targetPackage, cfg = cfg))
        // Use the received exploration eContext (if any) otherwise construct the object that
        // will hold the exploration output and that will be returned from this method.
        // Note that a different eContext is created for each exploration if none it provider
        val adbWrapper = OnDeviceAdbWrapper()
        explorationContext = ExplorationContext(
            cfg,
            app,
            { adbWrapper.readStatements() },
            LocalDateTime.now(),
            watcher = watcher,
            model = modelProvider.get()
        )
    }

    suspend fun onFinished() = coroutineScope {
        explorationContext.close()

        // we use coroutineScope here to ensure that this function waits for all coroutines spawned within this method
        watcher.forEach { feature ->
            (feature as? ModelFeature)?.let {
                // this is meant to be in the current coroutineScope and not in feature, such this scope waits for its completion
                launch(CoroutineName("eContext-finish")) {
                    it.onFinalFinished()
                }
            }
        }
        log.info("Writing reports finished.")
    }

    private fun cleanOutputDir(cfg: ConfigurationWrapper) {
        val outputDir = cfg.droidmateOutputDirPath

        if (!Files.isDirectory(outputDir))
            return

        arrayListOf(cfg[reportDir]).forEach {

            val dirToDelete = outputDir.resolve(it)
            if (Files.isDirectory(dirToDelete))
                dirToDelete.deleteDir()
        }

        Files.walk(outputDir)
            .filter { it.parent != null && it.parent.fileName != null }
            .filter { it.parent.fileName.toString() != EnvironmentConstants.dir_name_temp_extracted_resources }
            .filter { it.parent.fileName.toString() != ConfigurationWrapper.log_dir_name }
            .filter { Files.isRegularFile(it) }
            .forEach { Files.delete(it) }

        Files.walk(outputDir)
            .filter { it.parent != null && it.parent.fileName != null }
            .filter { it.parent.fileName.toString() != EnvironmentConstants.dir_name_temp_extracted_resources }
            .filter { it.parent.fileName.toString() != ConfigurationWrapper.log_dir_name }
            .forEach { assert(Files.isDirectory(it)) { "Unable to clean the output directory. File remaining ${it.toAbsolutePath()}" } }
    }

    private suspend fun ExplorationContext<M, S, W>.verify() {
        try {
            assert(this.explorationTrace.size > 0) { "Exploration trace should not be empty" }
            assert(this.explorationStartTime > LocalDateTime.MIN) { "Start date/time not set for exploration" }
            assert(this.explorationEndTime > LocalDateTime.MIN) { "End date/time not set for exploration" }

            assertLastActionIsTerminateOrResultIsFailure()
            assertLastGuiSnapshotIsHomeOrResultIsFailure()
            assertOnlyLastActionMightHaveDeviceException()
            assertDeviceExceptionIsMissingOnSuccessAndPresentOnFailureNeverNull()

            assertLogsAreSortedByTime()
            warnIfTimestampsAreIncorrectWithGivenTolerance()
        } catch (e: AssertionError) {
            log.error(e.message, e)
        }
    }

    private fun ExplorationContext<M, S, W>.assertLogsAreSortedByTime() {
        val apiLogs = explorationTrace.getActions()
            .mapQueueToSingleElement()
            .flatMap { deviceLog -> deviceLog.deviceLogs.map { ApiLogcatMessage.from(it) } }

        assert(explorationStartTime <= explorationEndTime)

        val ret = ApiLogcatMessageListExtensions.sortedByTimePerPID(apiLogs)
        assert(ret)
    }

    private suspend fun moveScreenShot(actionId: Int, targetDir: Path, eContext: ExplorationContext<M, S, W>) = eContext.imgTransfer.launch(Dispatchers.IO) {
        // pull the image from device, store it in the image directory defined in ModelConfig and remove it on device
        val fileName = "$actionId.jpg"
        val srcFile = imgDir.resolve(fileName)
        val dstFile = targetDir.resolve(fileName)
        var c = 0
        do { // try for up to 3 times to pull a screenshot image
            delay(2000) // the device is going to need some time to compress the image, if the image is time critical you should disable delayed fetch
        } while (isActive && c++ < 3 && ! srcFile.exists())

        if (! File(srcFile.toString()).exists())
            log.warn("unable to fetch state image for action $actionId")
        else
            Files.move(srcFile.toPath(), dstFile, StandardCopyOption.REPLACE_EXISTING)
    }

    var capturedPreviously = false
    suspend fun explorationLoop(app: IApk): Boolean {
        log.debug("explorationLoop(app=${app.packageName}) - time: " + explorationContext.explorationStartTime)

        try {
            // Execute the exploration loop proper, starting with the values of initial reset action and its result.
            if (isFirst || !action.isTerminate()) {
                try {
                    // decide for an action
                    // check if we need to initialize timeProvider.getNow() here
                    action = strategyScheduler.nextAction(explorationContext)
                    // execute action
                    result = action.execute(app, automationEngine)
                    if (automationEngine.delayedImgFetch) {
                        /* if images are not critical for the exploration strategy
                        they can be stored asynchronous (faster critical path),
                        move the files to the correct location as soon as they are available */
                        if (capturedPreviously && action is ActionQueue) {
                            val aq = (action as ActionQueue)
                            aq.actions.forEachIndexed { i, a ->
                                log.debug("action queue element {} should have screenshot for ExploreCommand {}", i, a)
                                if (i < aq.actions.size - 1 &&
                                    ((a is TextInsert && aq.actions[i + 1] is Click) || a is Swipe))
                                    moveScreenShot(
                                        a.id,
                                        explorationContext.model.config.imgDst,
                                        explorationContext
                                    )
                            }
                        }
                        if (result.guiSnapshot.capturedScreen) {
                            val id =
                                if (action.isTerminate()) action.id + 1 else action.id // terminate is not send to the device instead we terminate the app process and issue Fetch which will have a higher id value
                            log.debug("action {} should have screenshot for ExploreCommand {}", id, action)
                            moveScreenShot(id, explorationContext.model.config.imgDst, explorationContext)
                        }
                    }
                    capturedPreviously = result.guiSnapshot.capturedScreen

                    explorationContext.update(action, result)

                    if (isFirst) {
                        log.info("Initial action: $action")
                        isFirst = false
                    }

                    // Propagate exception if there was any exception on device
                    if (!result.successful && exception !is DeviceExceptionMissing) {
                        explorationContext.exceptions.add(exception)
                    }
                } catch (e: Throwable) {
                    // the decide call of a strategy may issue an exception e.g. when trying to
                    // interact on non-actable elements
                    log.error(
                        "Exception during exploration\n" +
                            " ${e.localizedMessage}", e
                    )
                    explorationContext.exceptions.add(e)
                    explorationContext.launchApp().execute(app, automationEngine)
                }
            }

            return action.isTerminate()
        } catch (e: Throwable) {
            // the loop handles internal error if possible, however if the launchApp after
            // exception fails we end in this catch this means likely the uiAutomator is dead
            // or we lost device connection
            log.error("unhandled device exception \n ${e.localizedMessage}", e)
            explorationContext.exceptions.add(e)
            strategyScheduler.close()
            return true
        }
    }

    fun getExplorationResult(): FailableExploration {
        explorationContext.explorationEndTime = LocalDateTime.now()
        return FailableExploration(explorationContext, explorationContext.exceptions)
    }
}
