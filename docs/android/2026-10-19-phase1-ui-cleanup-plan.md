# Phase 1 — Android UI cleanup: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

## Status (2026-10-19)

Tasks 1–11 are implemented and committed on `android/m7` (one commit per task; Tasks 4 and 5 share one). Core and app unit tests pass and the androidTest sources compile. **Not done:** the phone install, the favourites before/after count, and the owner's visual check of Tasks 6, 7, 8, 10 and 11.

Deviations from the plan:

| Task | Deviation | Why |
|---|---|---|
| 1 | Old collection/`setStatus` repository methods were removed in Task 2, not Task 1 | the app had to compile between commits |
| 2 | Audio playlists and collections were removed entirely; the adhkar fold runs from `AtharApp` | the ViewModel has no Context |
| 4+5 | One commit; Articles hides the filter button when no group applies (Recent tab) | screens were rewritten once |
| 7 | `FootnoteModeTest` was written after the code | RED not observed |
| 8 | The whole «صفحة القراءة» block and its preview were removed from «المظهر والقراءة»; the title is unchanged. The «Aa» popover keeps a scroll valve (max 560 dp) | same palette as «Aa»; huge font scales |
| 9 | `ReaderPanelTab.Search` stays in the enum (the top search icon opens it) but is hidden from the tab row | keeps the icon working |
| 10 | Selected fill is `primaryAction` / `accentFill` with `onPrimaryAction` / `onAccent` (white, 7.7:1) | `accent` + white is 3.1:1 in the night palette |
| 11 | Kitab draws honorifics only from code points (U+FDFA, U+FD40–FD4D), not from spelled-out phrases; the text was not rewritten | Phase 2 |

**Goal:** Make each thing in the app work one way everywhere: one way to save (المفضّلة), the same tabs in every section, one filter panel that fits the screen and keeps its choices, a top bar that hides on scroll, one footnote mode at a time, an «Aa» panel that fits, no duplicate paths, and solid-coloured selected controls.

**Architecture:** Favourites reuse the existing `libraryEntry` table: a row means "favourite". There is no Room schema change. A one-time, idempotent fold moves collection items and adhkar favourites into that table, then clears them. The UI changes go through the shared components in `ui/components/AtharSectionUi.kt` (tabs, filter sheet, collapsing header) so every section changes the same way. Per-section choices persist in one small SharedPreferences file.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room 3 (`androidx.room3`), Hilt, Navigation3, SharedPreferences, JUnit4 (JVM only).

**Spec:** the 20-item audit and the decisions from 2026-10-19, restated in **Scope** below. Phase 2 (the model formatting pass) is outlined at the end and is out of scope here.

## Scope (the spec)

| # | Agreed change | Audit items |
|---|---|---|
| S1 | One way to save, «المفضّلة», with a star icon on books, poems, articles, issues, audio and adhkar. Remove «محفوظاتي», its shelves and «مجموعات». Existing shelf, collection and adhkar items all become favourites, so nothing is lost. The bookmark icon is used only for page bookmarks in the reader. | 1–6 |
| S2 | Books, poetry, articles, issues and audio all have the tabs **الكل · الأخيرة · المحمّلة · المفضّلة**, each as its own tab. | 7 |
| S3 | One «تصفية» button per section opens one panel that fits without inner scrolling. Sort, type, topic and century are tap-to-choose buttons that apply immediately, plus «مسح». Remove the author filter and Kannashah «مجمّع» grouping. | 8, 9, 11, 18 |
| S4 | Sort and filter choices persist per section across app restarts. | 10 |
| S5 | The whole top bar (title, search, tabs, filter row) hides on scroll down and returns on a small scroll up. | 20 |
| S6 | Footnote setting: «أسفل الصفحة» or «عند الضغط على الرقم». They are exclusive and never both active. | 19 |
| S7 | The «Aa» panel fits without scrolling. Font size and line spacing leave «الإعدادات» → «المظهر والقراءة». | 14, 17 |
| S8 | Remove duplicates: ⋮ → «التنزيلات», the reader side-panel «البحث» tab, the lone «—» lines, and the repeated first line on poem rows. | 12, 13, 15, 16 |
| S9 | Selected tabs, segments and chips get a solid accent fill with light text, replacing the tinted background with accent text. | user, 2026-10-19 |
| S10 | Prose books use the same font as Turath (**Kitab**). Quran text uses **Amiri Quran**. | user, 2026-10-19 |

**Decision D1 (settled 2026-10-19):** use Turath's font for prose books and a Quranic font for ayat.
- **Prose: Kitab** ([quranacademy/kitab-font](https://github.com/quranacademy/kitab-font)). It is the font Turath uses (`font-family: Kitab`), licensed SIL OFL, built on Scheherazade with Monotype-Naskh-style shapes, and it has honorific glyphs (U+FDFA, U+FD40–FD4D…). It replaces Amiri (`res/font/amiri_*.ttf`) in the book reader. Turath's line height is 1.75.
- **Quran: Amiri Quran** (SIL OFL, from the Amiri project). It renders the ordinary Imla'i Quran text these books contain. KFGQPC Uthmanic Hafs was rejected for two reasons: its EULA forbids modification and redistribution, and it expects Uthmani-encoded text, which the books don't use.

## Global Constraints

- Do not publish the app or upload new app content without explicit authorization from the owner.
- Phone builds must use `cd android && set -a && . ~/.config/athar/android-release.env && set +a && ./gradlew :app:installDebug`. Never print, read or copy values from `~/.config/athar/*.env`.
- No Room schema version bump. `AtharUserDatabase` stays `version = 1`.
- No new dependencies, except the two OFL font sets in Task 11.
- The backup export format keeps working both ways: old backups that contain collections still import, and their items become favourites.
- No popup or panel scrolls inside itself at font scale 1.0 on a 360×640 dp screen. At larger font scales the sheet may grow to full height, and that sheet is the only scroll.
- Touch targets stay at least 48 dp. Selected text on a solid fill must have at least 4.5:1 contrast in every palette.
- Copy is Arabic as written in Scope: «المفضّلة», «الأخيرة», «المحمّلة», «الكل», «تصفية», «مسح», «أسفل الصفحة», «عند الضغط على الرقم».
- Testing is lean: unit tests on the JVM only, with no GUI, screenshot or e2e tests. The owner checks the look on the phone.
- Commit after each task. Each commit ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Un-favourite after the fold must stick.** If the fold left collection rows behind, the next start would add the item back. Clearing collection rows is part of the same transaction. Pinned by `foldIsIdempotentAndRespectsRemoval` in Task 1.
2. **The header is hidden and the list can't scroll.** After a filter or search shrinks the list, the user would be stuck with no tabs and no search. The header must show again whenever the tab, filter, sort or query changes, or when the list is at the top. This is checked in Task 6 step 3 on the phone.
3. **Tap-mode footnotes in the windowed reader.** The 50-block `ReaderWindow` does not attach note bodies, which was the bug fixed on 2026-10-19. Tap mode must look bodies up in `state.footnotes`. A number with no body does nothing and must not crash. Task 7.
4. **A stored setting value that no longer exists.** Examples are a sort name from an older build or the removed author filter. It must fall back to the default and must not crash at startup. Task 5 uses `enumValueOrNull` with a fallback.
5. **The legacy footnote boolean.** `reader_footnotes=false` used to mean hidden, which no longer exists. It must map to «عند الضغط على الرقم» and not to footers. Pinned by `FootnoteModeTest` in Task 7.

---

### Task 1: Favourites store and one-time fold (core/data)

**Files:**
- Modify: `android/core/data/src/main/kotlin/com/atharchive/core/data/repository/PersonalLibraryRepository.kt`
- Modify: `android/core/data/src/main/kotlin/com/atharchive/core/data/db/user/UserDatabase.kt` (DAO only, about lines 150–220)
- Test: `android/core/data/src/test/kotlin/com/atharchive/core/data/repository/FavouritesFoldTest.kt`

**Interfaces:**
- Produces: `suspend fun PersonalLibraryRepository.toggleFavourite(entityId: String)`, which replaces `toggleReadLater`.
- Produces: `suspend fun PersonalLibraryRepository.foldIntoFavourites(extraIds: Collection<String>): Int`. It returns the number of rows added.
- Produces: `fun PersonalLibraryRepository.observeFavouriteIds(): Flow<Set<String>>`.
- Produces: `internal fun favouritesToAdd(existing: Set<String>, items: List<CollectionItemEntity>, extraIds: Collection<String>, now: Long): List<LibraryEntryEntity>`. This is pure, and new rows get `status = LibraryStatus.READ_LATER` and `addedAt = item.addedAt` (extras get `now`).
- Produces: DAO `@Transaction suspend fun foldFavourites(rows: List<LibraryEntryEntity>)`. It inserts the rows, then deletes all `collectionItem` and `userCollection` rows.
- Removes: `createCollection`, `renameCollection`, `deleteCollection`, `addToCollection`, `removeFromCollection`, `observeCollections`, `observeCollectionItems` and `setStatus` from the repository. The archive keeps its own DAO calls.

- [x] **Step 1: Write the failing test**

```kotlin
class FavouritesFoldTest {
    @Test fun collectionItemsAndExtrasBecomeFavouritesOnce() {
        val items = listOf(CollectionItemEntity("c1", "book-a", 10), CollectionItemEntity("c2", "book-a", 20), CollectionItemEntity("c1", "audio:x", 30))
        val rows = favouritesToAdd(existing = setOf("book-b"), items = items, extraIds = listOf("dhikr:7", "book-b"), now = 99)
        assertEquals(listOf("book-a", "audio:x", "dhikr:7"), rows.map { it.entityId })
        assertEquals(10L, rows.first().addedAt)
        assertTrue(rows.all { it.status == LibraryStatus.READ_LATER })
    }
    @Test fun foldIsIdempotentAndRespectsRemoval() {
        // After a fold the collection tables are empty, so a second fold with no new extras adds nothing,
        // and an id the user un-favourited is not resurrected.
        assertEquals(emptyList<LibraryEntryEntity>(), favouritesToAdd(existing = emptySet(), items = emptyList(), extraIds = emptyList(), now = 1))
    }
}
```

- [x] **Step 2: Run the test and check it fails**

Run: `cd android && ./gradlew :core:data:testDebugUnitTest --tests '*FavouritesFoldTest*'`
Expected: FAIL to compile, with `favouritesToAdd` unresolved.

- [x] **Step 3: Implement `favouritesToAdd`, the DAO `foldFavourites`, and the repository methods listed above**

Keep the first-seen order and remove duplicates by `entityId`. `observeFavouriteIds` maps `observeLibraryEntries()` to a set of `entityId` values. Add a `// ponytail:` comment on `LibraryStatus.READ_LATER` explaining that the stored value stays "readLater" so old backups round-trip, and that any row means favourite.

- [x] **Step 4: Run the test and check it passes**

Run: the same command. Expected: PASS. Also run `./gradlew :core:data:testDebugUnitTest`. Expected: all pass, including `UserDataArchiveTest`.

- [x] **Step 5: Commit**: `feat(android): favourites store with one-time fold of collections`

### Task 2: Star on every item; remove «محفوظاتي» and collections UI

**Files:**
- Delete: `android/app/src/main/kotlin/com/atharchive/feature/library/LibraryScreen.kt`, `LibraryModels.kt`
- Modify: `android/app/src/main/kotlin/com/atharchive/data/AtharContentViewModel.kt` (`toggleReadLater` at about line 815, the collection functions at about 820–846, the library flows at about 195–245, `shelfIds` at 403/428/454/566/588, `LibraryShelf` mapping at about 1270)
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/AtharApp.kt`:
  - the ⋮ entry at line 384
  - `LibraryRoute` at about line 417
  - the collection calls at 436 and 591
  - the adhkar prefs at about 633–648
  - backup copy at line 1054
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/components/AtharSectionUi.kt`. Rename `AtharBookmarkButton` (line 581) to `AtharFavouriteButton`, with a star icon and the labels «إضافة … إلى المفضّلة» / «إزالة … من المفضّلة».
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/audio/AudioScreen.kt`. Move save out of ⋮ (line 343) onto the row, like the other sections.
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/audio/PlayerScreen.kt`. «إضافة إلى مجموعة» (line 523) becomes the star.
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/adhkar/AdhkarScreen.kt`. Use the same star.
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/icons/` to add `AtharIcons.Star` / `StarFilled` if they are missing.

**Interfaces:**
- Consumes: from Task 1, `toggleFavourite`, `foldIntoFavourites` and `observeFavouriteIds`.
- Produces: `AtharContentViewModel.toggleFavourite(entityId: String)` and `val favouriteIds: StateFlow<Set<String>>`.
  - Adhkar ids are `"dhikr:<DhikrUi.id>"`.
  - Audio ids stay `"audio:<id>"`.
- Produces: the `saved: Boolean` field on each section's UI model keeps its name and now means favourite.

- [x] **Step 1: Run the fold in the ViewModel `init`**
  - Call `foldIntoFavourites(extraIds = context.getSharedPreferences("adhkar", MODE_PRIVATE).getStringSet("saved", emptySet()).map { "dhikr:$it" })` once.
  - Remove the `saved` prefs key only after it returns successfully.
  - Run the fold again after every successful backup import.
- [x] **Step 2: Replace the adhkar `savedIds` prefs logic in `AtharApp.kt`** so it uses `favouriteIds` and `toggleFavourite("dhikr:${dhikr.id}")`.
- [x] **Step 3: Delete the Library feature, `LibraryRoute`, the ⋮ «محفوظاتي» entry and every collection call.** Change the backup copy at line 1054 to «المقتطفات ومواضع القراءة والمفضّلة».
- [x] **Step 4: Swap the bookmark icon for the star on every list row, in the audio row and in the player.** The reader's page-bookmark icon stays unchanged.
- [x] **Step 5: Verify**
  - Run: `cd android && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL.
  - Run: `grep -rn "محفوظاتي\|Collection(\|LibraryShelf\|toggleReadLater" android/app/src/main`. Expected: no output.
- [x] **Step 6: Commit**: `feat(android): one favourites system; drop محفوظاتي, shelves and collections`

### Task 3: The same four tabs in every content section

**Files:**
- Modify the tab enums:
  - `feature/books/BookModels.kt` (`BooksTab`)
  - `feature/poetry/PoemModels.kt`
  - `feature/articles/ArticleModels.kt` (`ArticlesTab`)
  - `feature/issues/IssueModels.kt` (`IssuesTab`)
  - `feature/audio/AudioModels.kt` (`AudioTab`)
- Modify the matching `*Screen.kt` filters and empty-state copy.
- Modify: `data/AtharContentViewModel.kt`. Add `recentRank` to the poem, article, issue and audio UI models, computed from `readingHistory`. Books keep the position-based `recentRank` at line 405.
- Modify: `feature/kannashah/KannashahModels.kt` / `KannashahScreen.kt`. Remove `KannashahView` (الكل/مجمّع) and `KannashahGrouping`. Kannashah has no tabs because it is already your saved excerpts.

**Interfaces:**
- Produces a shared enum in `ui/components/AtharSectionUi.kt`: `enum class SectionTab(val key: String, val label: String) { All("all","الكل"), Recent("recent","الأخيرة"), Downloaded("downloaded","المحمّلة"), Favourites("favourites","المفضّلة") }`.
  - It replaces the five per-section tab enums.
  - Rename the existing private composable `SectionTab` (line 173) to `SectionTabCell` to avoid the clash.
- Each item model has `recentRank: Int?`, `downloaded: Boolean` and `saved: Boolean`. The tab filter is `when (tab) { All -> true; Recent -> recentRank != null; Downloaded -> downloaded; Favourites -> saved }`.

- [x] **Step 1: Record opens for every kind of item.**
  - Call `personalLibrary.recordOpened(id)` when a poem opens in `poemReaderState`/its open path.
  - Call it with `"audio:<id>"` when an audio item starts playing.
  - Articles and issues already go through `openReader` (line 689).
- [x] **Step 2: Add `recentRank` to the four models.** The rank is the index by descending latest `openedAt` per `entityId`.
- [x] **Step 3: Switch the five screens to `SectionTab` and the shared filter.** In الأخيرة, order by `recentRank`. Each tab gets its own empty-state copy, for example «لا توجد عناصر مفضّلة» / «اضغط النجمة على أي عنصر ليظهر هنا.».
- [x] **Step 4: Remove the Kannashah tabs, grouping and `ExcerptGroup`.** The «١ مقتطفًا» label (line 133/140) goes with it.
- [x] **Step 5: Verify**: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL.
- [x] **Step 6: Commit**: `feat(android): same four tabs in every section`

### Task 4: One «تصفية» sheet that fits

**Files:**
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/components/AtharSectionUi.kt`. Replace `AtharFilterMenu`/`AtharFilterGroup` (line 326) with the sheet.
- Modify the filter rows in the Books, Poetry, Articles, Issues, Audio and Kannashah screens.
- Test: `android/app/src/test/kotlin/com/atharchive/ui/components/AtharMenuTest.kt` (existing). Update it or delete the cases that covered the removed two-level menu.

**Interfaces:**
- Produces: `data class AtharChipGroup(val title: String, val options: List<String>, val selected: String, val onSelect: (String) -> Unit)`. The first option is the "all" default.
- Produces: `@Composable fun AtharFilterButton(activeCount: Int, groups: List<AtharChipGroup>, onClear: () -> Unit, tagPrefix: String)`.
  - It shows «تصفية», or «تصفية · ٢» when filters are active.
  - It opens a `ModalBottomSheet` with one `FlowRow` of chips per group and «مسح» at the top.
  - Taps apply immediately.

- [x] **Step 1: Implement the sheet.**
  - Chips wrap with `FlowRow`, and the sheet does not use a nested `verticalScroll`.
  - Groups are الترتيب, النوع, الموضوع and القرن. Each section passes only the groups it has.
  - Topic and century options list only values present in that section.
  - In الأخيرة the الترتيب group is hidden, but the button stays where it is, so the row does not jump (audit 11).
- [x] **Step 2: Remove the author filter from `BooksScreen.kt`** (line 112 and the group at 200–205). Search already matches authors.
- [x] **Step 3: Replace each section's separate الموضوع/النوع/الترتيب buttons with one `AtharFilterButton`.**
- [x] **Step 4: Verify**: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL. Run `grep -rn "AtharFilterMenu\|AtharFilterGroup" android/app/src`. Expected: no output.
- [x] **Step 5: Commit**: `feat(android): one fitting filter sheet per section`

### Task 5: Choices persist per section

**Files:**
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/components/AtharSectionUi.kt`
- Modify: the six section screens, where `rememberSaveable` holds sort and filters (for example `BooksScreen.kt:109-115`).

**Interfaces:**
- Produces: `@Composable fun rememberPersisted(key: String, default: String): MutableState<String>`. It is backed by `context.getSharedPreferences("list_state", MODE_PRIVATE)` and writes on every set. Keys look like `"books.sort"`, `"books.kind"`, `"poetry.topic"`.
- Produces: `inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E?`. Enum state is stored by `name` and read as `enumValueOrNull<BookSort>(stored) ?: BookSort.Newest`.

- [x] **Step 1: Implement both helpers.** A stored option that is no longer in the current option list falls back to the default.
- [x] **Step 2: Move sort and filter state to `rememberPersisted`.** The search query and tab stay `rememberSaveable`: the app opens on «الكل» with a clear search, while the filter button's count shows when filters are active.
- [x] **Step 3: Verify**: build and test as in Task 4. On the phone, set A–Z, force-stop the app, reopen it, and check it is still A–Z.
- [x] **Step 4: Commit**: `feat(android): sort and filters persist per section`

### Task 6: The whole top bar hides on scroll

**Files:**
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/components/AtharSectionUi.kt` (`AtharCollapsingHeader`, lines 415–444)
- Modify: the Books, Poetry, Articles, Issues, Audio and Kannashah screens. Each already uses `rememberAtharCollapsingHeader()` and `AtharCollapsible`; for example `BooksScreen.kt:164-200`.

**Interfaces:**
- Produces: `fun AtharCollapsingHeader.show()`. It sets the header visible and resets the travel count.

- [x] **Step 1: Move `AtharTopBar` and `AtharSearchField` inside the existing `AtharCollapsible(header)`** on each screen. Lower `Threshold` from 36f to 24f so a small scroll up brings it back.
- [x] **Step 2: Call `header.show()`** from a `LaunchedEffect(tab, query, filters, sort)`, and whenever `!listState.canScrollBackward`.
- [x] **Step 3: Verify** with a build. On the phone:
  - Scroll down and check the whole bar hides; scroll up a little and check it returns.
  - Type a search that leaves 1 result and check the bar is visible.
- [x] **Step 4: Commit**: `feat(android): whole top bar hides on scroll`

### Task 7: Footnote mode, footer or tap, never both

**Files:**
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderModels.kt:164` (`showFootnotes`)
- Modify: `android/app/src/main/kotlin/com/atharchive/data/AppearanceStore.kt:56,78`
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderBlockView.kt:270` (PageNotes) and about line 338 (`markedText` sup spans)
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderSettingsPopover.kt:111-113`
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderScreen.kt` (about line 435). This includes committing the uncommitted footer fix from 2026-10-19 here.

**Interfaces:**
- Produces: `enum class FootnoteMode { Footer, Tap }`. `ReaderSettings.footnoteMode: FootnoteMode = FootnoteMode.Footer` replaces `showFootnotes`.
- Produces: `internal fun footnoteModeOf(stored: String?, legacyShow: Boolean?): FootnoteMode`.
  - The stored name wins.
  - Otherwise `legacyShow == false → Tap` and anything else gives `Footer`.
  - The key is `reader_footnote_mode`.

- [x] **Step 0: Write the test** `android/app/src/test/kotlin/com/atharchive/data/FootnoteModeTest.kt`

```kotlin
@Test fun legacyAndStoredValuesMap() {
    assertEquals(FootnoteMode.Footer, footnoteModeOf(stored = null, legacyShow = null))
    assertEquals(FootnoteMode.Footer, footnoteModeOf(stored = null, legacyShow = true))
    assertEquals(FootnoteMode.Tap, footnoteModeOf(stored = null, legacyShow = false))
    assertEquals(FootnoteMode.Tap, footnoteModeOf(stored = "Tap", legacyShow = true))
    assertEquals(FootnoteMode.Footer, footnoteModeOf(stored = "Bogus", legacyShow = null))
}
```

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests '*FootnoteModeTest*'`. Expected: FAIL to compile until `footnoteModeOf` exists, then PASS.

- [x] **Step 1: Apply the two modes in `ReaderBlockView`.**
  - The PageNotes footer is drawn only in `Footer` mode.
  - Sup numbers are always visible, and clickable only in `Tap` mode.
  - In tap mode the body comes from `state.footnotes[target]`. A missing body does nothing.
- [x] **Step 2: Persist the mode with `footnoteModeOf`.** Change the «الحواشي» row options to «أسفل الصفحة» / «عند الضغط على الرقم».
- [x] **Step 3: Verify** with a build. On the phone, open كتاب الإيمان لأبي عبيد:
  - Footer mode shows the footer and the numbers do not open a popup.
  - Tap mode shows no footer, and tapping a number opens the note.
- [x] **Step 4: Commit**: `feat(android): exclusive footnote mode — footer or tap`

### Task 8: Compact «Aa»; font settings leave «الإعدادات»

**Files:**
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderSettingsPopover.kt` (the rows and `SegmentRow` at line 172)
- Modify: `android/app/src/main/kotlin/com/atharchive/ui/settings/AppearanceScreen.kt:113-135`

- [x] **Step 1: Put each popover label on the same line as its segments.**
  - The label takes about 30% of the width at the start, and the segments take the rest.
  - Rows keep a 48 dp minimum height, and the popover's `verticalScroll` is removed.
  - The rows are حجم الخط, تباعد الأسطر, الهوامش, التشكيل, الحواشي, أرقام الصفحات and مظهر القراءة.
- [x] **Step 2: Remove «حجم الخط» and «تباعد الأسطر»** (and «صفحة القراءة» if it is the same palette choice as «مظهر القراءة») from `AppearanceScreen`.
- [x] **Step 3: Verify** with a build. On the phone, «Aa» shows every row including «مظهر القراءة» without scrolling.
- [x] **Step 4: Commit**: `fix(android): Aa panel fits; no duplicate font settings`

### Task 9: Remove the remaining duplicates

**Files:**
- Modify: `ui/AtharApp.kt:388`. Remove the ⋮ «التنزيلات» entry; «الإعدادات» → «التنزيلات والتخزين» (line 1046) stays. Update the KDoc at `ui/components/AtharTopBar.kt:45`.
- Modify: `feature/reader/ReaderPanel.kt:56`. Remove the `Search("البحث")` panel tab; the top search icon stays.
- Modify: `data/AtharContentViewModel.kt:575,598,1056`. Use `.ifBlank { "" }`, and have the row composables skip a blank topic line. Also update `feature/poetry/PoemModels.kt:45` (`sizeLabel` default `""`).
- Modify: `feature/poetry/PoetryScreen.kt`. Hide the preview line when it equals the title after trimming.

- [x] **Step 1: Make the four removals and edits above.**
- [x] **Step 2: Verify**: build and test as before. Run `grep -rn 'ifBlank { "—" }' android/app/src/main`. Expected: no output.
- [x] **Step 3: Commit**: `fix(android): remove duplicate paths and empty «—» lines`

### Task 10: Solid-colour selected controls

**Files:**
- Modify: `ui/components/AtharSectionUi.kt`:
  - `SectionTabCell`, the renamed `SectionTab` at about line 173
  - the chips from Task 4
  - `AtharMenuButton` (line 250) when active
- Modify: `feature/reader/ReaderSettingsPopover.kt` (`SegmentRow`)
- Modify: `feature/reader/ReaderTheme.kt:22` (`ReaderColors`)

**Interfaces:**
- Produces: `ReaderColors.onAccent: Color`, defined per palette. App controls use `AtharTheme.colors.accent` with `AtharTheme.colors.onPrimaryAction`.

- [x] **Step 1: Restyle the selected state.**
  - Selected controls get `background(accent)` with `onAccent`/`onPrimaryAction` text in SemiBold.
  - Unselected controls keep the current `divider.copy(alpha = 0.28f)` with secondary text.
  - Tabs become filled pills (`AtharCorners.Control`) instead of accent text.
- [x] **Step 2: Set `onAccent` for every reader palette** so that it reaches at least 4.5:1 contrast against `accent`. Add a `// ponytail:` note if any value was picked by eye instead of computed.
- [x] **Step 3: Verify** with a build, then have the owner check it on the phone in light, dark and each reader palette.
- [x] **Step 4: Commit**: `style(android): solid fill for selected tabs, segments and chips`

### Task 11: Kitab for prose, Amiri Quran for ayat

**Files:**
- Create: `android/app/src/main/res/font/kitab_regular.ttf`, `kitab_bold.ttf` (from the kitab-font repo), and `amiri_quran.ttf` (from the Amiri release).
- Create: `android/app/src/main/assets/licenses/OFL-Kitab.txt`, `OFL-Amiri.txt`.
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderTheme.kt` / the reader typography (where `amiri_regular`/`amiri_bold` are used).
- Modify: `android/app/src/main/kotlin/com/atharchive/feature/reader/ReaderBlockView.kt:349-351`. The `CitationRe` loop already finds ﴿…﴾ ranges and colours them `colors.ayah`.

**Interfaces:**
- Produces: `val KitabFontFamily: FontFamily` and `val QuranFontFamily: FontFamily`.

- [x] **Step 1: Bundle the three font files and their OFL licence texts.**
- [x] **Step 2: Prose fonts and line height.** Prose `p`/`h1`/`h2`/`quote`/`li` blocks and footnotes use `KitabFontFamily`, with the line-height base set to 1.75 (Turath's value). The existing تباعد الأسطر setting still scales it. Poems keep their current font.
- [x] **Step 3: Quran ranges.** For ranges that start with `\uFD3F` («﴿»), add `fontFamily = QuranFontFamily` to the existing ayah `SpanStyle`. Styling only applies to ranges that are already found, so offsets, selection and highlights are unchanged.
- [x] **Step 4: Check the honorific glyphs.** Find out whether Kitab draws the honorific glyph from the spelled-out text («صلى الله عليه وسلم», «رحمه الله») through its `lellah.fea` ligatures, or only from the code points.
  - If only from code points: report it and don't rewrite the text. Swapping phrases for code points changes characters and offsets, so it belongs to Phase 2.
- [x] **Step 5: Verify.**
  - Build and report the APK size increase in the commit message.
  - The owner checks كتاب الإيمان on the phone: prose in Kitab, an ayah in Amiri Quran, and that tashkeel doesn't collide at 1.75.
- [x] **Step 6: Commit**: `feat(android): Kitab reader font (as Turath), Amiri Quran for ayat`

### Final: Build, install, hand to owner

- [x] Run `cd android && ./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest`. Expected: all pass.
- [ ] Install with the Global Constraints command. Expected: `Installed on 1 device`.
- [ ] Do an upgrade check on the phone's real data. Favourites should equal the old shelf, collection and adhkar items, with none missing; compare counts before and after with `adb exec-out run-as com.atharchive cat databases/athar_user.db` and sqlite3.
- [ ] The owner eyeballs the result. Nothing is pushed or published without the owner's go-ahead.

---

## Phase 2 — Model formatting pass (outline, separate plan)

- **Model and method:** Sonnet 5.5 does a markup-only pass, one book at a time, over about 200 small books.
  - A script checks that the text is unchanged letter for letter: diacritics-insensitive, with whitespace normalized.
  - Any book that fails the check is rejected and stays as it is.
- **Storage:** the results go in sidecar files next to the stable-ID sidecars, so the source text is never edited in place.
- **Pilot:** 5 books first, starting with كتاب الإيمان لأبي عبيد (محققًا). The owner reviews them on the phone before the rest run.
- **What it marks:**
  - headings, poems inside prose, and the exact footnote anchors
  - plain-digit references turned into real note references
  - printed page-number lines such as «-46» and «-6» removed
  - sentences split by a page break joined in continuous (no-page) mode
  - the book info card at the start hidden
- **Extra outputs to brainstorm then:** search aids (key terms and alternative spellings), wiki-style links between books, people and places, and chapter summaries for the table of contents.
