package droidmate.org.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.Surface
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import droidmate.org.accessibility.automation.IKeyboardEngine
import droidmate.org.accessibility.automation.KeyboardEngine
import droidmate.org.accessibility.automation.parsing.UiHierarchy
import droidmate.org.accessibility.automation.screenshot.IScreenshotEngine
import droidmate.org.accessibility.automation.screenshot.ScreenshotEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class WindowEngineTest {
    companion object {
        private val TAG = WindowEngineTest::class.java.simpleName
    }

    private val instr = InstrumentationRegistry.getInstrumentation()
    /*private val service = mock<AccessibilityService> {
        on { windows } doReturn instr.uiAutomation.windows
    }*/
    private val service = Mockito.mock(AccessibilityService::class.java).also {
        `when`(it.windows).thenReturn(instr.uiAutomation.windows)
        `when`(it.rootInActiveWindow).thenReturn(instr.uiAutomation.rootInActiveWindow)
        `when`(it.getSystemService(any(String::class.java))).thenReturn(
            instr.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        )
    }
    private val context by lazy { instr.context }
    private val uiHierarchy: UiHierarchy by lazy { UiHierarchy() }
    private val screenshotEngine: IScreenshotEngine = Mockito.mock(ScreenshotEngine::class.java)
    //private val screenshotEngine: IScreenshotEngine = mock<ScreenshotEngine>()
    private val keyboardEngine: IKeyboardEngine by lazy {
        KeyboardEngine(
            context
        )
    }
    private val windowEngine = WindowEngine(uiHierarchy, screenshotEngine, keyboardEngine, service)

    @Test
    fun displayRotationTest() {
        val rotation = windowEngine.getDisplayRotation()
        assertTrue("Invalid device rotation $rotation", rotation == Surface.ROTATION_0)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun displayedWindowsTest() {
        runBlockingTest {
            val windows = windowEngine.getDisplayedWindows()
            assertTrue("No windows found", windows.isNotEmpty())
        }
    }
}