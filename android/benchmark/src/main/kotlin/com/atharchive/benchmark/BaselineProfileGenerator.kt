package com.atharchive.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the app's baseline profile: the code of these journeys is compiled ahead of time on
 * install instead of running interpreted the first times a reader takes them. Without it only
 * the libraries' own profiles applied, so startup, the reader, search and downloads all began cold.
 *
 * Regenerate after large UI changes, on a phone with Android 13+ and the benchmark build:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.atharchive.benchmark.BaselineProfileGenerator
 * then copy the `*-baseline-prof.txt` it reports to app/src/main/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    // Three rounds settle the profile; each extra round on a real phone is another chance for
    // a UI step to flake (round five once found the books tab still loading).
    @Test
    fun journeys() = rule.collect(packageName = TargetPackage, maxIterations = 3, stableIterations = 2) {
        pressHome()
        startActivityAndWait()
        device.requireObject("books_screen")
        // The book opens before the list scrolls: a scrolled list recomposes the row it found.
        // Uncompiled (the rule resets compilation) and fetching its first page: slower than 5 s.
        device.requireFirstBook()
        // The topmost row that is mostly on screen: the list comes back scrolled on later
        // rounds, leaving slivers clipped under the tabs and the bottom bar.
        device.findObjects(By.res(Pattern.compile("book_row_.*")))
            .filter { it.visibleBounds.height() > device.displayHeight / 20 }
            .minBy { it.visibleBounds.top }
            .click()
        checkNotNull(device.wait(Until.findObject(By.res("reader_screen")), 20_000)) { "reader did not open" }
        device.scrollVerticallyFor(ReaderScrollDurationMs / 2)
        device.pressBack()
        device.requireObject("books_screen")
        device.flingVertically()

        device.requireObject("bottom_search").click()
        device.requireObject("search_screen")
        val field = device.requireObject("search_search")
        field.click()
        SearchTerm.forEachIndexed { index, _ ->
            field.text = SearchTerm.take(index + 1)
            device.waitForIdle()
        }
        // Back only while the keyboard covers the bar: on a bare tab it leaves the app.
        val books = device.wait(Until.findObject(By.res("bottom_books")), 1_000)
            ?: device.pressBack().let { device.requireObject("bottom_books") }
        books.click()
        device.requireObject("books_screen")
        device.requireObject("settings_action").click()
        // A tab with an app menu lists «الإعدادات» first; others open settings directly.
        device.wait(Until.findObject(By.text("الإعدادات")), 2_000)?.click()
        device.requireObject(By.text("التنزيلات والتخزين"), "downloads settings row").click()
        device.requireObject("downloads_screen")
        device.flingVertically()
    }
}
