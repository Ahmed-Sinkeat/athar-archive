package com.atharchive.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one benchmark that exists before M0 reports: cold start.
 *
 * Budget (docs/main-plan.md §17): cold start to interactive library < 800 ms,
 * hard fail at 1.5 s, measured on a low-memory API 26 profile — not a flagship.
 *
 * The remaining §17 budgets land as R7 is built out. Deliberately not stubbed
 * here: an empty benchmark that always passes is worse than a missing one.
 */
@RunWith(AndroidJUnit4::class)
class ColdStartBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        // :m0 during M0; becomes com.atharchive at M3 with targetProjectPath.
        const val TARGET_PACKAGE = "com.atharchive.m0"
    }
}
