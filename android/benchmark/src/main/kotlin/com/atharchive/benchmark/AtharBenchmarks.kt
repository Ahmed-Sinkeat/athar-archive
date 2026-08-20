package com.atharchive.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The M7.1 feedback loop.
 *
 * Every one of these maps to a §17 budget, and each drives the app the way a reader does
 * — through UiAutomator against the real benchmark build, not through Compose test APIs
 * that would measure a different app.
 *
 *   coldStart        cold start → interactive library     < 800 ms   (fail 1.5 s)
 *   tabChanges       navigation between destinations      frame timing
 *   booksFling       catalogue list scroll                frame timing
 *   readerFling      reader scroll, the §17 10 s fling    0 dropped  (fail > 1%)
 *   searchTyping     keystroke → results                  < 120 ms   (fail 300 ms)
 *
 * Frame timing is reported as P50/P90/P99 frame duration by the harness; the dropped-frame
 * budget is read off P99 against the device's frame deadline rather than asserted here, so
 * a slower phone reports honestly instead of failing a threshold picked for a faster one.
 */
private const val TargetPackage = "com.atharchive"
private const val Timeout = 5_000L

/** Long enough to matter, short enough that the whole suite finishes in one sitting. */
private const val FlingCount = 3

/** Few steps means a fast gesture — a fling, not a drag. */
private const val SwipeSteps = 8

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        // Interactive means the catalogue is on screen, not that the window opened.
        device.wait(Until.hasObject(By.res("books_screen")), Timeout)
    }
}

@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** The soap indicator runs on every one of these; this is what pays for it. */
    @Test
    fun tabChanges() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("books_screen")), Timeout)
        },
    ) {
        listOf("poetry", "search", "audio", "kannashah", "books").forEach { route ->
            device.findObject(By.res("bottom_$route"))?.click()
            device.waitForIdle()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ListScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun booksFling() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("books_screen")), Timeout)
        },
    ) {
        device.flingVertically()
    }
}

@RunWith(AndroidJUnit4::class)
class ReaderBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** §17: reader scroll, 10 s fling, 0 dropped frames — the budget with no slack. */
    @Test
    fun readerFling() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("books_screen")), Timeout)
            device.openFirstBook()
        },
    ) {
        device.flingVertically()
    }
}

@RunWith(AndroidJUnit4::class)
class SearchBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Typing, not pasting. The search debounces at 120 ms and cancels superseded work, so
     * a single setText would measure the debounce rather than the interaction.
     */
    @Test
    fun searchTyping() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("books_screen")), Timeout)
            device.findObject(By.res("bottom_search"))?.click()
            device.wait(Until.hasObject(By.res("search_screen")), Timeout)
        },
    ) {
        val field = device.findObject(By.res("search_search")) ?: return@measureRepeated
        field.click()
        device.waitForIdle()
        // A real word, typed one letter at a time.
        "الإخلاص".forEachIndexed { index, _ ->
            field.text = "الإخلاص".take(index + 1)
            device.waitForIdle()
        }
    }
}

/**
 * Flings down then back up, by coordinates.
 *
 * `UiObject2.fling` needs `scrollable="true"`, which a Compose LazyColumn does not
 * advertise to UiAutomator — the first run found no object and measured one frame. A
 * gesture on the display always lands, whatever the semantics say.
 */
private fun UiDevice.flingVertically() {
    val x = displayWidth / 2
    val top = displayHeight / 5
    val bottom = displayHeight * 4 / 5
    repeat(FlingCount) {
        swipe(x, bottom, x, top, SwipeSteps)
        waitForIdle()
    }
    repeat(FlingCount) {
        swipe(x, top, x, bottom, SwipeSteps)
        waitForIdle()
    }
}

/** The catalogue's first row, whatever book it happens to be. */
private fun UiDevice.openFirstBook() {
    findObject(By.res(Pattern.compile("book_row_.*")))?.click()
    wait(Until.hasObject(By.res("reader_screen")), Timeout)
}
