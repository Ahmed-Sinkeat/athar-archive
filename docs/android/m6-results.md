# M6 downloads + cache results

Recorded 20 Aug 2026 on branch `android/m6`.

## Implemented

- Explicit Download records pin intent before scheduling exact unique WorkManager work.
- Package transfers resume from a durable `.part`, require an exact HTTP 206 range, verify
  the complete SHA-256, persist the verified sidecar, resume interrupted frame import, and
  publish package + index + manifest under `filesDir/content/` before cleaning transfers.
- Unpin removes retention and transfer files but leaves imported content cache-eligible.
- Downloaded state requires all three facts: pin intent, complete imported availability, and
  a verified retained package. Startup reconciles those facts and re-enqueues incomplete pins.
- The imported-frame cache defaults to 2 GiB and supports 500 MiB, 2/5/10/20 GiB, or no
  limit. Clear-cache and LRU eviction preserve pins. Below 500 MiB free, read-through stops
  and unpinned cache is trimmed toward half the configured budget.
- Reader close cancels adjacent prefetch before another frame begins. Prefetch first uses an
  already-imported adjacent frame and treats remote prefetch failure as non-fatal, so a
  retained reader cannot crash merely because the network is absent.
- Android 13+ notification permission, a content-download channel, individual completion
  notifications, and a grouped summary are wired.

## Automated verification

Passed locally:

```text
./gradlew :core:data:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug :core:data:lintDebug
./gradlew :core:data:assembleDebugAndroidTest
pnpm test                         16 files, 142 tests
pnpm validate:content            8,342 entries
```

The JVM network suite proves exact-prefix resume, preservation of a truncated prefix for the
next attempt, and deletion of a complete hash-invalid package. The compiled Android suite
covers resumed download/import/retention, process death after package + index completion,
ordinary read-through without a pin, reader-close cancellation, unpin-to-eviction, and
low-storage eviction that preserves pinned frames.

## Real-device evidence

Device: Xiaomi `23053RN02Y`, Android 15, USB ADB.

- Installed the M6 debug APK as an update and downloaded the staging book
  `nawaqid-al-islam` (3,197-byte package) from the configured R2 custom domain.
- The UI changed to “downloaded”. `filesDir/content/` contained the addressed `.athar`,
  verified `.idx`, and manifest; `filesDir/transfers/` was empty after completion.
- Force-stop + cold launch preserved Downloaded state.
- Unpin removed retained transport files; re-download succeeded and Android exposed both
  `اكتمل التنزيل` and the `اكتملت تنزيلات المحتوى` grouped summary.
- With mobile data already off and Wi-Fi disabled, force-stop + cold launch opened the
  retained book and rendered its Arabic text. The process remained alive and AndroidRuntime
  recorded no exception. Wi-Fi was restored immediately after the check.
- This check exposed and fixed an escaped adjacent-prefetch `UnknownHostException`; the exact
  offline scenario then passed on the rebuilt APK.

## Remaining acceptance gate

`./gradlew :core:data:connectedDebugAndroidTest` builds both APKs but HyperOS rejects the new
test package before execution:

```text
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
Starting 0 tests on 23053RN02Y - 15
```

The production app can be updated over ADB; only installation of the separate
`com.atharchive.core.data.test` package is blocked. Enable **Developer options → Install via
USB**, then rerun the command above. M6 should receive its final milestone check only after
that suite executes successfully.
