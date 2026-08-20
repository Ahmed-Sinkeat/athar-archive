# M7 search + library organization results

Recorded 20 Aug 2026 on branch `android/m7`.

## Implemented

- Block search uses the M0-selected contentless FTS5 layout with `detail=none`, no prefix
  index, and `block_fts.rowid = block.rowid`. Catalog title, author, and topic metadata has
  a separate weighted FTS table.
- Catalog apply, frame import, and FTS maintenance share the same content-database write
  transaction. A block-delete trigger removes its FTS row during frame eviction, and an
  unmanaged schema version rebuilds both indexes from retained relational rows when needed.
- User input is converted to generated FTS syntax by `FtsQueryBuilder.compactPlan`; closed
  phrases use AND candidate retrieval followed by mandatory exact normalized-source
  verification. Candidate retrieval continues in deterministic 80-row batches, so 80 false
  co-occurrences cannot hide a later exact result.
- Search debounces for 120 ms, cancels superseded work, starts at two characters, searches
  the complete local catalog plus locally readable passages, and applies field, content
  type, source, author, and ordering choices as bound structured predicates rather than
  interpolated `MATCH` syntax.
- Snippets retain the original vocalised block text. `normalizeWithMap` supplies exact
  UTF-16 highlight ranges, including absorbed tashkeel, and a result tap opens the source
  reader at the semantic block ordinal with the full-source range briefly highlighted.
- `athar_user.db` owns Read later, Reading, and Finished status, recent opens/searches, and
  user-created collections. The library screen also derives Continue, Downloaded, and
  Recent views without moving disposable content state into the user database.
- Collection deletion removes only its membership rows. Shelves, collections, membership,
  and pins remain present when the separate content database is replaced.

## Automated verification

Passed locally:

```text
./gradlew :core:athar-text:test :core:data:test
./gradlew :core:data:compileDebugAndroidTestKotlin
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
./gradlew :app:lintDebug :core:data:lintDebug
./gradlew :core:data:connectedDebugAndroidTest    12 tests on Android 15
adb shell am instrument ...BooksNavigationTest  4 tests on Android 15
pnpm test                         16 files, 142 tests
pnpm validate:content            8,342 entries
```

The Android data tests cover exact-phrase retrieval when the first valid result is candidate
81, vocalised source-range mapping, catalog field/author filtering, atomic FTS eviction, and
survival of shelves and collections across replacement of the content database. The app UI
suite now covers the current RTL destinations, current catalog tabs, the library route and
collection dialog, and live search input; obsolete M2 fixture assertions were removed.

## Real-device evidence

Device: Xiaomi `23053RN02Y`, Android 15, USB ADB.

- The M7 debug APK installed and cold-launched successfully.
- The library action opened the real `مكتبتي` screen, including the empty Continue shelf and
  collection-creation dialog.
- The complete 12-test data suite passed on-device: seven `ContentDatabaseImportTest`, two
  `ContentSearchTest`, and three `ContentTransferRunnerTest` cases.
- The complete four-test `BooksNavigationTest` suite passed on-device. It covers the RTL
  primary destinations, current catalog tabs, the library route, collection creation, and
  live local-search input.
- The phrase test indexed 82 real Room blocks, rejected the first 81 token co-occurrences,
  returned the vocalised `طَلَبُ العِلْمِ` block, and then proved eviction removed the FTS
  hit in the same transaction.

## HyperOS test-runner note

HyperOS rejected Android Gradle Plugin's default `com.atharchive.test` instrumentation
package even though **Install via USB** and **USB debugging (Security settings)** were both
enabled. The app now declares `com.atharchive.instrumentation` as its test application ID;
that APK installs successfully.

HyperOS also assigns Athar `MIUIOP(10021): ignore`, which prevents Compose's activity rule
from bringing the test activity out of the background. Device acceptance temporarily set
that Athar-only app-op to `allow`, ran the test runner directly, then restored `ignore` and
removed the hidden test APK:

```text
adb shell appops set com.atharchive 10021 allow
adb shell am instrument -w -r -e class com.atharchive.BooksNavigationTest \
  com.atharchive.instrumentation/androidx.test.runner.AndroidJUnitRunner
# OK (4 tests)
adb shell appops set com.atharchive 10021 ignore
```

The production M7 APK remains installed; only the instrumentation APK and temporary app-op
were cleaned up after the successful run.
