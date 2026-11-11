package com.atharchive.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
 *   bookOpen         tap a row → first page shown          AtharBookOpen trace section
 *   bulkDownload     «تنزيل الكل» on المسائل, local server   cpuCoreMs (÷ items) + janky frames
 *
 * Frame timing is reported as P50/P90/P99 frame duration by the harness; the dropped-frame
 * budget is read off P99 against the device's frame deadline rather than asserted here, so
 * a slower phone reports honestly instead of failing a threshold picked for a faster one.
 */
/** The benchmark build (its own id, beside the real app). */
internal const val TargetPackage = "com.atharchive.bench"
private const val Timeout = 5_000L

/** A short, repeatable gesture sequence for screens without a dedicated duration budget. */
private const val FlingCount = 3

/** The reader budget in §17 is explicitly defined over ten seconds of scrolling. */
internal const val ReaderScrollDurationMs = 10_000L
internal const val SearchTerm = "الإخلاص"
private const val SearchLatencyTraceName = "AtharSearchKeystrokeToResults"

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
        device.requireObject("books_screen")
        device.requireFirstBook()
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
            device.requireObject("books_screen")
            device.requireFirstBook()
        },
    ) {
        listOf("poetry", "search", "audio", "kannashah", "books").forEach { route ->
            device.requireObject("bottom_$route").click()
            device.requireObject("${route}_screen")
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
            device.requireObject("books_screen")
            device.requireFirstBook()
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
            device.requireObject("books_screen")
            device.openFirstBook()
        },
    ) {
        device.scrollVerticallyFor(ReaderScrollDurationMs)
    }
}

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class BookOpenBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun smallBookWarm() = open(null, StartupMode.WARM)

    @Test
    fun smallBookCold() = open(null, StartupMode.COLD)

    /** مجموع الفتاوى: the book the 1.2.3 slowdown was reported on. */
    @Test
    fun largeBookWarm() = open("مجموع الفتاوى", StartupMode.WARM)

    @Test
    fun largeBookCold() = open("مجموع الفتاوى", StartupMode.COLD)

    private fun open(title: String?, mode: StartupMode) = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(TraceSectionMetric("AtharBookOpen", TraceSectionMetric.Mode.First), FrameTimingMetric()),
        iterations = 5,
        startupMode = mode,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.requireObject("books_screen")
            device.requireFirstBook()
            if (title != null) {
                device.requireObject("books_search").text = title
                device.waitForIdle()
            }
        },
    ) {
        device.openFirstBook()
        device.wait(Until.gone(By.res("reader_loading")), Timeout)
        device.waitForIdle()
        device.pressBack()
    }
}

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class SearchBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * Typing, not pasting. The max async trace covers a keystroke, debounce, Room query and
     * the results frame; the frame metric catches input/rendering jank during the same trip.
     */
    @Test
    fun searchTyping() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(SearchLatencyTraceName, TraceSectionMetric.Mode.Max),
        ),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.requireObject("books_screen")
            device.requireFirstBook()
            device.requireObject("bottom_search").click()
            device.requireObject("search_screen")
        },
    ) {
        val field = device.requireObject("search_search")
        field.click()
        device.waitForIdle()
        // A real word, typed one letter at a time.
        SearchTerm.forEachIndexed { index, _ ->
            field.text = SearchTerm.take(index + 1)
            device.waitForIdle()
        }
        device.requireObject(searchResultsReadyTag(SearchTerm))
    }
}

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class BulkDownloadBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * The 953 مسائل in one tap, from a fresh install's catalog (the build must point at the local
     * server, see android/README.md). One iteration: each one re-downloads everything.
     * Limits read off the report, as for frames: 6.3 measured 127 core-s for the run.
     */
    @Test
    fun bulkDownload() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric(), ProcessCpuMetric()),
        iterations = 1,
        startupMode = StartupMode.COLD,
        setupBlock = {
            device.executeShellCommand("pm clear $TargetPackage")
            startActivityAndWait()
            device.requireObject("books_screen")
            // A cleared install syncs the whole catalog first.
            checkNotNull(device.wait(Until.findObject(By.res(Pattern.compile("book_row_.*"))), 120_000L)) { "catalog never synced" }
            device.requireObject("settings_action").click()
            device.requireObject("settings_downloads").click()
            device.requireObject("downloads_screen")
        },
    ) {
        val row = By.res(Pattern.compile("bulk_المسائل( · نصوص)?"))
        device.requireObject(row, "المسائل bulk row").click()
        device.requireObject(By.res(Pattern.compile("bulk_confirm(_mobile)?_المسائل.*")), "download confirm").click()
        // A finished row stops being clickable.
        checkNotNull(device.wait(Until.findObject(row.enabled(false)), 600_000L)) { "مسائل download never finished" }
    }
}

/** CPU time the app's threads spent on a core during the measured block, from the trace's scheduler slices. */
@OptIn(ExperimentalMetricApi::class)
private class ProcessCpuMetric : androidx.benchmark.macro.TraceMetric() {
    override fun getMeasurements(
        captureInfo: CaptureInfo,
        traceSession: androidx.benchmark.traceprocessor.TraceProcessor.Session,
    ): List<Measurement> {
        val ns = traceSession.query(
            "SELECT SUM(s.dur) AS d FROM sched s JOIN thread USING(utid) JOIN process p USING(upid) " +
                "WHERE p.name LIKE '${captureInfo.targetPackageName}%'",
        ).firstOrNull()?.double("d") ?: 0.0
        return listOf(Measurement("cpuCoreMs", ns / 1e6))
    }
}

/**
 * Flings down then back up, by coordinates.
 *
 * `UiObject2.fling` needs `scrollable="true"`, which a Compose LazyColumn does not
 * advertise to UiAutomator — the first run found no object and measured one frame. A
 * gesture on the display always lands, whatever the semantics say.
 */
internal fun UiDevice.flingVertically() {
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

/**
 * Keeps the reader producing frames for the full §17 sample window.
 *
 * Alternating direction prevents a short fixture from spending most of the measurement
 * pinned at one end of the list. The final `waitForIdle` keeps inertial frames inside the
 * measured block.
 */
internal fun UiDevice.scrollVerticallyFor(durationMs: Long) {
    require(durationMs > 0)
    val x = displayWidth / 2
    val top = displayHeight / 5
    val bottom = displayHeight * 4 / 5
    val deadline = SystemClock.elapsedRealtime() + durationMs
    var down = true
    do {
        if (down) swipe(x, bottom, x, top, SwipeSteps)
        else swipe(x, top, x, bottom, SwipeSteps)
        down = !down
    } while (SystemClock.elapsedRealtime() < deadline)
    waitForIdle()
}

/** The catalogue's first row, after its asynchronous Room flow has populated the screen. */
internal fun UiDevice.requireFirstBook(): UiObject2 = requireObject(
    selector = By.res(Pattern.compile("book_row_.*")),
    description = "first catalogue row",
)

internal fun UiDevice.openFirstBook() {
    requireFirstBook().click()
    requireObject("reader_screen")
}

internal fun UiDevice.requireObject(resourceId: String): UiObject2 = requireObject(
    selector = By.res(resourceId),
    description = "resource-id $resourceId",
)

internal fun UiDevice.requireObject(selector: BySelector, description: String): UiObject2 =
    checkNotNull(wait(Until.findObject(selector), Timeout)) {
        "Timed out after ${Timeout}ms waiting for $description"
    }

private fun searchResultsReadyTag(query: String): String =
    "search_results_${(query.hashCode().toLong() and 0xffffffffL).toString(16)}"
