package com.atharchive

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Production Application. Holds no domain logic (docs/main-plan.md §3) — DI
 * wiring lands with :core:data at M2, the navigation host with :feature:* at M3.
 *
 * Until then this module exists so R6 has a real, installable manifest whose
 * backup rules can be verified with `bmgr`.
 */
@HiltAndroidApp
class AtharApplication : Application()
