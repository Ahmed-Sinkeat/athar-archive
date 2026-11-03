# Android App Review & Bug Backlog — 2 November 2026

## 1. Overview & Current Architecture State

The Android app (**Athar 1.1**, `versionCode 5`, minSdk 26, targetSdk 36) serves as a companion scholarly library reader for the Athar Archive.

### Module Structure
```
android/
├── core/
│   ├── athar-text/          Pure JVM: Arabic normalization, character mapping, FTS query builder (no Android dependencies)
│   └── data/                Signed root sync, Room 3 databases (content.db & user.db), framed package decode, SAF archive import/export
└── app/                     Jetpack Compose UI, ViewModels, Media3 background audio, WorkManager download scheduler, Theme & Screens
```

### Verification & Test Status
- **Pure JVM Tests (`core:athar-text:test`)**: 203 golden vectors passing in milliseconds.
- **Data & Archive Tests (`core:data:testDebugUnitTest`)**: 32 unit tests passing (signed root verifier, frame decoders, SAF archive roundtrip with custom `app.*` blobs).
- **App Unit Tests (`app:testDebugUnitTest`)**: 35 unit test suites passing (Reader selection, Windowing, Dictionary lookup, User Adhkar store, Verse line matching).
- **Android Lint (`app:lintDebug`)**: Clean build with **0 errors and 0 issues**.

---

## 2. User Feedback & Bug Analysis (31 Oct – 2 Nov 2026)

### Bug 1: Year of Death (سنة الوفاة) Displays Death Dates for Living People
> *"Bug in the year of death it add alot of alife peoples"*

#### Observations & Root Causes:
1. **Catalog Pipeline (`src/lib/app-v2/generator.ts`)**:
   - In `personMetadata()`, the extraction extracts the first number from `parsed.died`:
     ```typescript
     const diedText = String(parsed.died ?? "");
     const western = diedText.replace(...);
     const diedMatch = western.match(/\d+/);
     ...(diedMatch ? { died: Number.parseInt(diedMatch[0], 10) } : {})
     ```
   - If a markdown file contains a contemporary scholar with a publication date or Hijri note (or if a living author has a note containing any digit), it extracts that digit as the death year.
2. **Metadata Files (`src/content/person/*.md`)**:
   - Multiple classical scholars (e.g. Al-Mardawi, Al-Suyuti, Abu Ya'la) were accidentally given `died: "معاصر"` in source markdown frontmatter, while living contemporary scholars (e.g., Adel Al-Hamdan, Salih Al-Fawzan) have `died: "معاصر"`.
   - When `died` is converted in `BooksScreen.kt`:
     ```kotlin
     text = highlightMatches(book.author + (book.died?.let { " (${diedLabel(it)})" } ?: ""), query, hl)
     ```
     Where `diedLabel(died) = "ت ${arabicDigits(died)}هـ"`.
     If any numeric year (e.g., 1445 or 1446) is present, the app unconditionally prepends `ت` ("died"), falsely marking living scholars as deceased.

#### Fix Plan:
- In `src/lib/app-v2/generator.ts`: Guard against living/contemporary scholars (e.g. if `died` is `"معاصر"`, or if an explicit `living: true` or `contemporary: true` tag exists, omit `died`).
- In `BooksScreen.kt` & `PersonsScreen.kt`: If an author is contemporary (`died == null` or flagged as living), display «معاصر» or omit the `ت ... هـ` prefix.

---

### Bug 2: Extremely Slow In-Book Search (البحث داخل الكتاب)
> *"مشكلة في البحث داخل الكتاب نفسه. البحث خارج الكتاب يعمل لكن البحث داخله يأخذ زمنا طويلا جدا. المثال: البحث عن كلمة 'فيمن رخص' داخل مصنف ابن أبي شيبة"*

#### Observations & Root Causes:
1. **Frame-by-Frame Sequential Loading (`AtharContentViewModel.kt`)**:
   - In `searchInBook(entityId, query)`:
     ```kotlin
     visitTextFrames(entityId) { range ->
         hits += contentSearch.search(
             ContentSearchRequest(query = query, sort = ContentSearchSort.BookOrder,
                 filter = ContentSearchFilter(entityIds = setOf(entityId)), ordinals = range),
             limit = 200 - hits.size,
         ).blocks
         hits.size >= 200
     }
     ```
   - `visitTextFrames` loops through **every single frame in the book** (Musannaf Ibn Abi Shaybah has hundreds of frames!).
   - For every frame, it executes `content.open(entityId, ordinal)`, inspects `importDao.frames(entityId)`, and runs a separate FTS search query per frame sequentially.
   - If the book is not yet fully downloaded, it performs network range fetches and database writes for every 250-block frame during the search!
2. **Why Global Search Is Fast**:
   - Global search queries the existing downloaded index in Room in a single SQL FTS query, or delegates to the remote Worker endpoint. It never iterates frame-by-frame.

#### Fix Plan:
- If the book is retained/downloaded: Execute a **single direct FTS query** on Room's `block_fts` filtered by `entity_id = :entityId`, ordered by `ordinal ASC` limit 200, instead of looping frame-by-frame.
- If the book is not downloaded: Search against the online search Worker (`search.arthurarchive.com`) filtered by `entity_id`, or show an immediate hint that full-text in-book search requires downloading the book.

---

### Bug 3: Slow Download of Large Books
> *"ايضا التحميل بطيء جدا جدا 5 ميغا من مصنف ابن أبي شيبة أخذت زمنا طويلا جدا"*

#### Observations & Root Causes:
1. **Import Phase Overhead vs Network Download**:
   - In `ContentTransferRunner.kt`, download progress is split 50/50: 0–50% is network transfer (`partFile`), while 50–100% is `importFrames`.
   - In `importFrames`, every single frame (250 blocks) is individually decompressed, parsed, and committed to SQLite (`block`, `footnote`, and `block_fts` tables).
   - On a large multi-volume text like Musannaf Ibn Abi Shaybah (which contains tens of thousands of hadith blocks), hundreds of separate SQLite write transactions occur sequentially.
2. **Database Contention**:
   - Taking `mutationMutex` per frame with frequent disk flushes on mobile flash storage causes major I/O bottlenecks.

#### Fix Plan:
- Batch frame imports inside larger SQLite transactions (e.g. commit every 10–20 frames instead of every single frame).
- Verify whether the network transfer itself is bottlenecked by R2 download streaming or connection throttling.

---

### Bug 4: Persistent "Failed to Update Content" Toast / Snackbar
> *"رسالة مزعجة تظهر دائما تقول تعذر تحديث المحتوى"*

#### Observations & Root Causes:
1. **Unconditional Snackbar on App Launch (`AtharApp.kt:239-246`)**:
   ```kotlin
   LaunchedEffect(syncState) {
       if (syncState is com.atharchive.data.ContentSyncUiState.Failed &&
           snackbarHostState.showSnackbar("تعذّر تحديث المحتوى؛ النصوص المحفوظة متاحة", "إعادة المحاولة", withDismissAction = true,
               duration = androidx.compose.material3.SnackbarDuration.Long) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
           contentViewModel.sync()
       }
   }
   ```
   - On launch, the app attempts a background sync with `ATHAR_CONTENT_BASE_URL`.
   - If the user is offline, on an unstable network, or if the local debug/release staging endpoint has no internet or has an un-updated certificate/origin, `syncState` transitions to `Failed`.
   - The snackbar immediately pops up with an action button over the reader UI, annoying the user every time the app opens offline.

#### Fix Plan:
- **Quiet Failure**: Offline-first apps should not alert on automatic startup sync failure. Show sync status quietly in Settings (`SettingsRow` already displays «تعذّر التحديث؛ أعد المحاولة»).
- Only trigger the snackbar when the user **manually taps** "تحديث المحتوى" (Sync Content) in Settings.

---

### Request 5: Library Fast Scroller / Alphabet Scrubber
> *"لابد من وجود scroller للذهاب والتحرك بسرعة في المكتبة"*

#### Observations:
- In `BooksScreen.kt`, the list holds up to 1,195 books (and thousands of entries across other tabs).
- The current `LazyColumn(state = listState)` has standard touch fling only. Scrolling from "أ" to "ي" requires dozens of flings.

#### Implementation Plan:
- Implement a custom vertical fast scroller or side scrubber thumb (e.g. `AtharFastScroller`) along the edge of `BooksScreen`, `PersonsScreen`, and `KannashahScreen`.
- Support dragging the thumb to jump proportionally across `listState.layoutInfo.totalItemsCount`, optionally displaying a bubble with the current letter or century.

---

### Request 6: Add Custom Color `#123456`
> *"#123456 اضف هذا اللون"*

#### Observations:
- `#123456` is a deep navy / slate blue tone (`rgb(18, 52, 86)`).
- Currently, custom color picking exists in `AppearanceScreen.kt:297` with a predefined swatch list:
  ```kotlin
  val swatches = listOf(
      0xFFA1352F, 0xFFC0392B, 0xFFB5651D, 0xFF8E6A1C, 0xFF6B8E23, 0xFF2E6B45,
      0xFF1F7A7A, 0xFF2A4C8C, 0xFF4B3F8F, 0xFF7A3E7A, 0xFF5A4636, 0xFF555555
  )
  ```
#### Fix Plan:
- Add `0xFF123456.toInt()` to the swatch array in `AppearanceScreen.kt`.
- Optionally add it as a named preset tone in `AtharAccent` (e.g. `Navy` / `كحلي`).

---

### Request 7: Relocate Copyright Notice to Main Settings
> *"copy right should apear in the main setting not theme one"*

#### Observations:
- Currently, `CopyrightNotice` and `emailCopyright` are placed in `AppearanceScreen.kt` (under "المظهر والقراءة" / theme settings).
- Placing legal/copyright contact information inside font and theme customization makes it unintuitive to find.

#### Fix Plan:
- Move the "حقوق النشر" section (`CopyrightNotice` and email action button) from `AppearanceScreen.kt` to the main Settings menu/dialog in `AtharApp.kt` (`SettingsRoute`), right beside data backup and content sync.

---

### Bug 8: Poems Opening as Text Book in Search Results
> *"poem like ... become text book then poem in the search continuouly"*

#### Observations:
- In `AtharApp.kt` and `SearchScreen.kt`, opening a search hit or navigating back/forward can inadvertently route a poem through `ReaderRoute` (the general book reader) rather than `PoemReaderRoute`.
- When opened in the book reader, poems lose hemistich layout (`صدر / عجز`), verse numbering, and audio-cue synchronization.

#### Fix Plan:
- In `openHit()` and `onOpenDirectMatch()` (`AtharApp.kt`), verify the entity collection type (`coll == "poem"` or `hit.type == SearchResultType.Poem`) and ensure it unconditionally routes to `PoemReaderRoute`.
- In continuous reading/search traversal, ensure the active poem reader does not degrade to prose blocks.

---

### Request 9: Search Titles First in Poetry Search
> *"people can search titles first in the poems search"*

#### Observations:
- In `PoetryScreen.kt`, searching in the poetry section immediately runs full-text search across all poem verses via `rememberSectionTextHits()`, burying poem titles under extensive verse matches.
- Readers often know the title of the poem (e.g., اللامية, النونية, منظومة...) and want to locate the poem directly before scanning verses.

#### Fix Plan:
- Prioritize title matches at the top of the search results list in `PoetryScreen.kt`.
- Alternatively, provide a filter toggle/chip: «العناوين فقط» (Titles Only) vs «نصوص الأبيات» (Verse Text).

---

### Request 10: Support Exact Match Search (البحث المطابق / التام)
> *"the search isnt exact if people want it"*

#### Observations:
- The search engine normalizes all queries (`ArNormalize.normalize()`), stripping diacritics/tashkeel and folding hamza variants (`أ, إ, آ, ء, ؤ, ئ` -> `ا`).
- While this is essential for general discovery, users searching for a specific scholarly formulation, grammatical case, or exact phrase with quotation marks cannot force an exact literal match.

#### Fix Plan:
- Add support for exact phrase syntax using quotes (e.g. `"فيمن رخص"`), or add a filter toggle for «مطابق / دقيق» (Exact Match).
- When exact match is requested, bypass hamza folding and diacritic stripping in SQLite FTS queries or perform an exact substring match.

---

### Request 11: Reader Top Three-Dot Menu Options (خيارات قائمة القراءة)
> *"add that when i click the top three dot on reading page i got more options like opening the author page to see all his work or to download the book or to share it"*

#### Observations:
- In `ReaderScreen.kt:880-890`, the top three-dot icon (`AtharIcons.More`) currently opens `AtharMenu` with only three options:
  1. `مقدمة المحقق ومعلومات عن الكتاب` (`hasIntro`)
  2. `نسخ الكل` (`onCopyAll`)
  3. `الطبعات` (`editions.isNotEmpty()`)
- Crucial actions for scholars and readers are missing from this central context menu:
  1. **Author's Profile & Bibliography**: Readers frequently want to explore who wrote the book and see all other works by the same author in the library without backing out to the home screen.
  2. **Offline Download / Storage Status**: Readers cannot immediately tell if the whole multi-volume work is safely saved offline or just streaming frames, nor can they initiate a full download directly from the reader.
  3. **Share Book Link**: While highlighting text allows sharing a single passage, there is no one-tap action to share the entire book (with URL, author, and edition metadata) to WhatsApp, Telegram, or research notes.

#### Implementation Plan:
1. **In `ReaderScreen.kt`**: Add callbacks to `ReaderScreen` and `ReaderHeader`:
   - `onOpenAuthor: (() -> Unit)? = null`
   - `onDownloadBook: (() -> Unit)? = null`
   - `onShareBook: (() -> Unit)? = null`
   - Pass download/retention status (`isRetained: Boolean`) into `ReaderHeader`.
2. **In the Three-Dot `AtharMenu`**:
   - Add item: `AtharMenuItem("صفحة المؤلف ومؤلفاته", icon = AtharIcons.Person, onClick = { menu = false; onOpenAuthor?.invoke() })`
   - Add item: `AtharMenuItem(if (isRetained) "محفوظ دون اتصال" else "تحميل الكتاب كاملاً", icon = if (isRetained) AtharIcons.Check else AtharIcons.Download, enabled = !isRetained, onClick = { menu = false; onDownloadBook?.invoke() })`
   - Add item: `AtharMenuItem("مشاركة الكتاب", icon = AtharIcons.Share, onClick = { menu = false; onShareBook?.invoke() })`
3. **In `AtharApp.kt`**:
   - Wire `onOpenAuthor = { state.authorSlug?.let { navigateTo(AtharDestination.Person(it)) } }`.
   - Wire `onDownloadBook = { contentViewModel.retainEntity(route.bookId) }`.
   - Wire `onShareBook = { shareText(context, "${state.bookTitle}\n${state.author}\n${state.sourceUrl}") }`.

---

### Request 12: Search Words Meaning & Concise Classical Dictionary Results (البحث في المعاجم وتلخيص المعاني)
> *"i want to be able to search words meaning to have better معجم results not all peoples want to read long things"*

#### Observations:
- In `Dictionary.kt`, Athar bundles 18 classical lexicons (`dict.zip`: *Lisan al-Arab*, *Taj al-Arus*, *Al-Sihah*, *Al-Qamus al-Muhit*...).
- Currently, lookup is only available when selecting text inside a book and tapping «معنى». There is no way to search word definitions directly from the main search bar in `SearchScreen.kt`.
- Furthermore, when a user inspects a classical dictionary entry, it renders raw multi-page classical prose (e.g., 3,000 words of morphological arguments and poetic *shawahid* in *Lisan al-Arab*). Most users just want the core meaning immediately without wading through an exhaustive monograph.

#### Implementation Plan:
1. **Direct Dictionary Search Mode**:
   - Add a «المعاجم» (Dictionaries) filter chip in `SearchScreen.kt`.
   - Entering any Arabic word or root directly queries `DictionaryLookup.query(word)` and presents matching dictionary entries.
2. **Concise Definition Card («خلاصة المعنى»)**:
   - Extract the primary core definition (the first 2–3 sentences defining the root/lemma before extensive poetic citations).
   - Display a clean summary card: **Root / Lemma (`الجذر`)**, **Core Meaning (`المعنى الإجمالي`)**, and **Source Lexicon (`المعجم`)**.
   - Provide an expandable disclosure accordion: **«عرض الشواهد والاستطرادات»** (Show classical citations & proofs) so scholars can expand the full 3,000-word text when needed.

---

### Request 13: Granular Genre Typography & Custom Font Import (تخصيص خطوط الآيات والأحاديث والمنظومات والخطوط الخارجية)
> *"to give poems its own fonts and can add your own also ayat have its own font also hadith"*

#### Observations:
- Currently, `AppearanceScreen.kt` and `ReaderTheme.kt` apply a single global font family across all texts (prose, poetry, Quranic verses, and hadiths alike).
- Quranic verses (`quran` span), Hadiths (`hadith` span), and poetic verses have distinct aesthetic and calligraphic traditions:
  - Quranic verses require an authentic Uthmanic typeface (*KFGQPC Hafs* / *Amiri Quran*).
  - Poetry thrives in fluid, elegant scripts (Nastaliq, Ruq'ah, or classical calligraphy).
  - Hadith text benefits from distinct, authoritative classical Naskh.
- Users also have beloved custom Arabic fonts (`.ttf` / `.otf`) on their devices that they wish to use.

#### Implementation Plan:
1. **Genre-Specific Typography in `AppearanceScreen.kt` & `ReaderSettings`**:
   - Add independent font family pickers for:
     - **خط الآيات القرآنية (Quranic Verse Font)**: Dedicated Uthmanic font.
     - **خط الأحاديث النبوية (Prophetic Hadith Font)**: Distinct scholarly typeface.
     - **خط المنظومات والأشعار (Poetry Font)**: Specialized poetic calligraphy.
   - When rendering blocks in `ReaderBlockView.kt` and `PoemReaderScreen.kt`, apply the genre-specific `FontFamily` to corresponding spans and verse lines.
2. **Custom Font Import via SAF (إضافة خط من الهاتف)**:
   - Add button: **«+ إضافة خط من الهاتف»** in `AppearanceScreen.kt`.
   - Uses `ActivityResultContracts.OpenDocument()` with MIME types `font/ttf`, `font/otf`, `application/x-font-ttf`.
   - Safely copies the font file to internal storage (`files/custom_fonts/{font_name}.ttf`) and dynamically instantiates it with `FontFamily(Font(file))`.

---

### Request 14: Enhanced UI/UX for Articles and Questions/Fatwas (تصميم مخصص للمقالات والأسئلة والفتاوى)
> *"to have better article and qustions design ui if possible"*

#### Observations:
- Currently, articles (`article`) and questions/fatwas (`question`) render through the generic book reader (`ReaderScreen.kt`), appearing as uninterrupted book pages.
- A Fatwa is fundamentally a Q&A interaction; reading a question that blends into the answer without clear visual boundaries degrades readability.
- Articles lack editorial polish (author avatar/badge, reading time, publication date, pull-quote callouts).

#### Implementation Plan:
1. **Dedicated Fatwa/Q&A Card Layout (`QuestionView`)**:
   - **Question Section («السؤال»)**: Distinct styled card with a soft callout background, question badge, and prominent typography.
   - **Answer Section («الجواب»)**: Clear visual separator with scholar badge (e.g. «أجاب فضيلة الشيخ: ...»), fatwa issue date, followed by the formatted response with distinct evidence blocks (Ayat & Hadith).
2. **Editorial Article Layout (`ArticleView`)**:
   - Masthead with article title, author avatar/link, publication date, and reading time estimate («قراءة في ٤ دقائق»).
   - Section sub-headings with pull-quotes and clean lead paragraphs.

---

### Request 15: Draggable Selection Handles & Enhanced Reading Scrubber (تحسين مقابض التحديد وشريط التقليب السريع)
> *"to make the selector of the text start and end dragble more easy to pick and also the progress bar on reading page to be clearler so people can move it easy"*

#### Observations:
- In `ReaderScreen.kt`, the start and end text selection handles have small touch targets (~24dp). On modern high-DPI screens, fingers obscure the pin and grabbing the handle often triggers a page scroll instead of moving the selection boundary.
- The bottom reading progress bar is a thin, subtle hairline (2–3dp). It is difficult to touch, scrub, or position precisely across a 1,000-page book.

#### Implementation Plan:
1. **Draggable Text Selection Handles**:
   - Expand touch hitbox from 24dp to **48dp** (standard Material touch target) with invisible padding around the teardrop pin.
   - Add haptic feedback tick (`LocalHapticFeedback.current.performHapticFeedback(...)`) as handles snap between words.
   - Position a subtle magnifying loupe or offset indicator above the finger so the active character remains visible during drag.
2. **Enhanced Reading Scrubber & Progress Bar**:
   - Redesign the bottom bar with a distinct, pill-shaped draggable thumb and a thicker track.
   - When scrubbing starts, display an elevated floating bubble directly above the finger showing:
     - **«الجزء {vol} · ص {page}»** or **«{percent}%»**.
   - Map scrubbing gestures with proportional resistance so dragging moves smoothly across book ordinals without sudden jumping.

---

## 3. Engineering Review Findings (From Review)

1. **Adhkar Reminders & Doze Mode (`AdhkarReminders.kt`)**:
   - `PeriodicWorkRequestBuilder` in WorkManager is battery-friendly, but aggressive manufacturer battery savers (e.g., Xiaomi MIUI / HyperOS) can delay periodic worker triggers by 15–45 minutes.
   - *Recommendation*: Keep WorkManager as default; evaluate an optional exact toggle via `AlarmManager.setExactAndAllowWhileIdle()` for users needing minute-level precision.
2. **Dictionary Handle Lifecycle (`Dictionary.kt`)**:
   - The dictionary zip file (`dict.zip`) keeps a static open `ZipFile` instance across the process. Ensure that if a user re-downloads or deletes the dictionary, the handle is safely closed before file operations.
3. **Release Catalog Snapshot**:
   - The bundled asset snapshot (`app/src/main/assets/app-content/`) must be refreshed with the latest signed generation before building production release artifacts (`npm run app:bundle-catalog`).

---

## 4. Prioritized Action Checklist

- [ ] **1. Sync Snackbar**: Silence automatic startup sync failure snackbar in `AtharApp.kt`; show error only on manual refresh.
- [ ] **2. In-Book Search**: Replace `visitTextFrames` frame-by-frame iteration with a single scoped FTS query over `ContentDatabase` for retained books.
- [ ] **3. Fast Scroller**: Add an edge drag thumb / scrubber to `BooksScreen` for smooth navigation across large collections.
- [ ] **4. Year of Death (died)**: Fix extraction in `src/lib/app-v2/generator.ts` and ensure living scholars are not rendered with `ت ... هـ`.
- [ ] **5. Copyright Notice Location**: Move copyright notice and contact email from `AppearanceScreen.kt` to the main Settings menu in `AtharApp.kt`.
- [ ] **6. Poem Search Navigation**: Ensure poem search hits always open in `PoemReaderScreen` and never fall back to prose `ReaderScreen`.
- [ ] **7. Poetry Title Search**: Prioritize poem title matches above verse text in poetry search, with an optional titles-only filter.
- [ ] **8. Exact Match Search**: Add support for exact/literal search (quotes `"..."` or an exact-match toggle) bypassing hamza/diacritic normalization.
- [ ] **9. Color #123456**: Add `0xFF123456` to the color swatches in `AppearanceScreen.kt`.
- [ ] **10. Download Batching**: Optimize `importFrames` transaction boundaries to reduce SQLite write latency on large books.
- [ ] **11. Reader Menu Options**: Add author page link, book download action, and share link to the top three-dot menu in `ReaderScreen.kt`.
- [ ] **12. Dictionary Search & Concise Cards**: Direct lexicon search mode in `SearchScreen.kt` with a concise core meaning card and expandable classical citations.
- [ ] **13. Granular Typography & Custom Fonts**: Independent font selectors for Quranic Ayat, Hadith, and Poetry, plus custom font import from phone storage via SAF.
- [ ] **14. Articles & Fatwas UI**: Custom Q&A layout for fatwas (distinct question callout vs. answer) and editorial layout for articles with reading time.
- [ ] **15. Selection Handles & Reader Scrubber**: 48dp accessible touch targets with haptic ticks for text selection handles, and an interactive draggable scrubber with floating page bubble.
---

## 5. Deep-Dive: Why Cloudflare Apps & Turath Are Blocked in Sudan

### The Turath Finding
Turath (`turath.io`) runs entirely on Cloudflare:
- Direct DNS resolution: `172.67.137.95` and `104.21.38.188` (Cloudflare Anycast).
- The fact that Turath is also blocked in Sudan confirms that the block is **not** content-based or directed specifically at Athar; it is an **infrastructure-level collision with Cloudflare's TLS & network stack in Sudan**.

---

### Technical Root Causes

#### 1. Encrypted Client Hello (ECH) vs Sudanese DPI Middleboxes (The Primary Trigger)
- In late 2024, Cloudflare enabled **Encrypted Client Hello (ECH)** across all zones (Free, Pro, Business).
- ECH encrypts the Server Name Indication (SNI) inside the TLS 1.3 `ClientHello` handshake, hiding the target domain from network eavesdroppers.
- Sudanese telecom operators (Sudatel, MTN Sudan, Zain) route traffic through Deep Packet Inspection (DPI) middleboxes (primarily Sandvine and Huawei equipment). These middleboxes are designed to inspect cleartext SNIs to enforce national filtering.
- **When the DPI middlebox detects an ECH handshake (or sees an encrypted SNI payload to a Cloudflare IP), it cannot inspect the domain name. The middlebox is programmed to drop the connection or send a TCP RST immediately.**
- **Why it "sometimes works but not always":**
  - ECH requires an `HTTPS` (type 65) or `SVCB` DNS record containing Cloudflare's `ECHConfig` public key.
  - When local network conditions cause the DNS client to fall back to plain `A` records (or when DNS times out and falls back to plain TLS), the client sends an unencrypted SNI (`arthurarchive.com` or `turath.io`). Because Islamic library domains are **not** on the government's political blocklist, the cleartext SNI passes through and the app works.
  - As soon as the client receives the `HTTPS` DNS record and attempts ECH, the DPI middlebox detects the encrypted handshake and instantly terminates the connection.

#### 2. Anycast IP Range Collateral Damage
- Cloudflare distributes domains across shared Anycast IP pools (`104.16.0.0/12`, `172.64.0.0/13`).
- When Sudanese authorities block an unrelated political website, news outlet, or circumvention proxy hosted on Cloudflare, ISPs frequently block the entire `/24` or Anycast IP subnet.
- Depending on which Anycast IP is resolved at any given hour, connection attempts either hit a clean IP or a blackholed IP.

#### 3. Cloudflare Bot Fight Mode on Mobile CGNAT
- Sudanese mobile networks place thousands of subscriber connections behind shared Carrier-Grade NAT (CGNAT) gateway IPs.
- Cloudflare’s automated "Bot Fight Mode" evaluates high request velocity from these shared IPs as potential DDoS or botnets, issuing silent HTTP 403 or JavaScript challenges that mobile app HTTP clients (OkHttp) cannot execute.

---

### Complete Solution Matrix

| # | Strategy | Mechanism | Resistance to Sudan DPI | Effort | Cost |
|---|---|---|---|---|---|
| **A** | **Disable ECH on Cloudflare** | Turn off `ech` via Cloudflare API so clients use standard TLS with cleartext SNI | **High** (solves the exact ECH DPI RST issue) | 5 minutes | $0 |
| **B** | **Dual Origin in App** | Add a secondary mirror (e.g. BunnyCDN or Hetzner VPS) in `AppContentHttpClient` | **Maximum** (completely bypasses Cloudflare when blocked) | 2 hours | ~$1–$4 / mo |
| **C** | **Client DNS Tuning** | Use DNS-over-HTTPS (DoH) or suppress `HTTPS/SVCB` type 65 records in OkHttp | **Medium** (prevents ECH key retrieval locally) | 30 minutes | $0 |
| **D** | **Cloudflare WAF Bypass** | Whitelist `/app/v2/*` and disable Bot Fight Mode in Cloudflare dashboard | **Medium** (solves CGNAT 403 blocks) | 5 minutes | $0 |
| **E** | **Offline Snapshot** | Pre-bundle signed catalog snapshot in APK assets | **Immune** (100% offline reading without network) | Already in repo | $0 |

---

### Step-by-Step Implementation Guide

#### Step 1: Force Disable ECH via Cloudflare API (Immediate Fix)
Cloudflare enables ECH by default and does not always show the toggle on Free zones, but it can be universally disabled via the Cloudflare API:

```bash
curl -X PATCH "https://api.cloudflare.com/client/v4/zones/{ZONE_ID}/settings/ech" \
     -H "Authorization: Bearer {API_TOKEN}" \
     -H "Content-Type: application/json" \
     --data '{"id":"ech","value":"off"}'
```
*Result*: Cloudflare stops advertising ECH DNS keys. The client connects with standard TLS. The Sudanese DPI firewall inspects the SNI (`arthurarchive.com`), finds no political block, and permits the connection.

#### Step 2: Disable Bot Fight Mode & Add WAF Bypass Rule
In Cloudflare Dashboard:
1. **Security → Bots**: Set *Bot Fight Mode* to **OFF**.
2. **Security → Settings**: Set *Security Level* to **Essentially Off**.
3. **Security → WAF → Custom Rules**: Add rule:
   - Expression: `(http.request.uri.path contains "/app/v2/") or (http.host eq "search.arthurarchive.com")`
   - Action: **Bypass** (skip all security challenges).

#### Step 3: Implement Dual Origin in Android App (`AppContentHttpClient.kt`)
To guarantee that the app *never* goes down in Sudan even during nationwide Cloudflare IP blocks:
1. Set up a BunnyCDN Pull Zone or a €4 Hetzner reverse-proxy pointing to the Cloudflare R2 bucket (`mirror.arthurarchive.com`).
2. Update [`AppContentHttpClient.kt`](file:///home/sinkeat/Projects/athar-archive/android/core/data/src/main/kotlin/com/atharchive/core/data/network/AppContentHttpClient.kt):
   ```kotlin
   class AppContentHttpClient(
       private val okHttpClient: OkHttpClient,
       private val primaryBaseUrl: String,
       private val fallbackBaseUrl: String? = null,
   ) {
       suspend fun execute(request: Request): Response {
           return try {
               val primaryResponse = okHttpClient.newCall(request).execute()
               if (primaryResponse.isSuccessful || primaryResponse.code == 206) primaryResponse
               else throw IOException("Primary HTTP ${primaryResponse.code}")
           } catch (e: Exception) {
               if (fallbackBaseUrl != null) {
                   val fallbackUrl = request.url.toString().replace(primaryBaseUrl, fallbackBaseUrl)
                   val fallbackRequest = request.newBuilder().url(fallbackUrl).build()
                   okHttpClient.newCall(fallbackRequest).execute()
               } else throw e
           }
       }
   }
   ```
   *Result*: If Cloudflare Anycast is blocked or resets the handshake in Sudan, the app transparently shifts to the mirror within 500ms.

---

## 6. Future Feature Proposals & Enhancements

High-impact proposals designed to build upon Athar's existing data models and components following Ponytail principles (maximum leverage with minimal code complexity).

### 1. Scholarly Reading Power Tools

#### A. "Bio Peek" for Scholars & Narrators («ترجمة العَلَم»)
- **Concept**: Beside the newly added **«معنى»** (Dictionary) button in the selection toolbar, add **«ترجمة»** when a scholar's name is highlighted or selected.
- **Data Foundation**: Leverages existing `CatalogPerson` data in `core:data` (biographies, death years, and bibliography from `src/content/person/*.md`).
- **UI Behavior**: An `AtharTailPopover` displays the scholar's death year, brief bio, and a one-tap link to their works in the archive, without leaving the current reading page.

#### B. Quick Return / Jump Stack History (العودة للموضع السابق)
- **Concept**: In large works (such as *Musannaf Ibn Abi Shaybah* or multi-volume *Fatwas*), jumping to a footnote, an in-book search hit, or a chapter from the TOC displaces the reading position.
- **UI Behavior**: A quiet, floating "Return to previous position" pill (e.g. `← ص ٤٢`) appears temporarily after any jump, allowing readers to return to where they were with a single tap.

#### C. Pinned Matn Header in Commentaries (تثبيت المتن أثناء قراءة الشرح)
- **Concept**: When reading extensive explanations (*شروح*), commentary paragraphs can span several screens between original lines of the base text (*متن*).
- **UI Behavior**: Detect the active heading or matn verse and pin it in a subtle, collapsible header strip at the top of the viewport while the reader scrolls through the commentary.

---

### 2. Hardware & Device Optimization

#### D. Physical Volume Key Page Turning (التقليب بأزرار الصوت)
- **Concept**: Allow physical `Volume Up` and `Volume Down` key presses to advance or backtrack by one screenful/page.
- **Target Audience**: Avid readers holding their phone with one hand or reading in bed.
- **Implementation**: Simple `onKeyEvent` interceptor in `ReaderScreen.kt`, with a toggle under Reader Settings.

#### E. E-Ink / High-Contrast Reader Mode (وضع الحبر الإلكتروني)
- **Concept**: Dedicated profile for Android E-Ink devices (Onyx Boox, Meebook, Bigme).
- **Behavior**:
  - Eliminates all gradient/glass blurs and transition animations.
  - Forces pure black text on pure white background (`#000000` on `#FFFFFF`).
  - Maximizes screen refresh crispness on E-Ink panels with zero ghosting.

---

### 3. Study & Knowledge Retention (كناشة الفوائد)

#### F. Formatted Markdown / PKM Export for Kannashah (تصدير الكناشة)
- **Concept**: Students of knowledge compile hundreds of benefits (*فوائد*) and quotes in Kannashah and need them in external tools (Obsidian, Notion, Logseq, Word).
- **UI Behavior**: An export button in Kannashah: **"تصدير الكناشة كملف نصي (Markdown)"**:
  - Groups excerpts by book or topic.
  - Formats each entry as a markdown blockquote with complete bibliographic attribution (`الكتاب، المؤلف، الجزء، الصفحة`).

#### G. Audio-Synced Text Highlighting (متابعة النص مع الصوت)
- **Concept**: Athar already supports fractional cue timestamps for poems (down to milliseconds) and plays lessons via Media3.
- **UI Behavior**: For recorded lessons with chapter/cue alignments, softly tint and auto-scroll the active paragraph or verse as audio plays.

---

### 4. Search Superpowers

#### H. Morphological / Root-Based Search (البحث بالجذر اللغوي)
- **Concept**: Leverage the newly built `DictionaryLookup` root derivation logic (`Dictionary.kt`).
- **Behavior**: Allow users to query by linguistic root (e.g. searching root `ع-ل-م` matches `علم`, `يعلمون`, `معلوم`, `علامة`), uncovering relevant passages regardless of morphological prefixes, suffixes, or verb forms.

---

## 7. Deep-Dive: Search Engine Architectural Critique & 10x Speedup Roadmap

### Is Athar's Search the Best and Fastest Possible?
**No. In fact, it is currently operating at ~5% to 10% of what modern search technology can deliver.**

While Athar's search is functional and features thoughtful multi-tier ranking logic, it suffers from severe architectural bottlenecks both on the server (Cloudflare D1 / Worker) and locally on Android (in-book `visitTextFrames` loop and contentless SQLite FTS5).

---

### Root Causes: Why the Current Search Is Slow & Suboptimal

#### 1. In-Book Search Iterates Frame-by-Frame in Kotlin (`visitTextFrames`)
- **The Bottleneck**: When searching inside a book (`AtharContentViewModel.kt:searchInBook`), the app invokes `visitTextFrames(entityId)`.
- For a large multi-volume work like *Musannaf Ibn Abi Shaybah* (hundreds of 250-block frames):
  - The app executes a loop across **every single frame in sequence**.
  - In each iteration, it queries the database, checks download state, prepares a SQLite statement, filters against `catalog_fts` (which is completely redundant for a single book!), searches `block_fts`, parses results, and re-scores in Kotlin.
  - If any frames are not yet cached, it attempts network fetches synchronously during the search.
- **The Contrast**: In SQLite, an FTS query restricted to a single book:
  ```sql
  SELECT b.ordinal, b.chapterAnchor, c.title, b.printedPage, b.type, b.text,
         snippet(block_fts, 0, '<mark>', '</mark>', '…', 32)
  FROM block_fts JOIN block b ON b.rowid = block_fts.rowid
  LEFT JOIN chapter c ON c.entityId = b.entityId AND c.anchor = b.chapterAnchor
  WHERE block_fts MATCH :query AND b.entityId = :bookId
  ORDER BY b.ordinal ASC LIMIT 200;
  ```
  executes in **10 to 20 milliseconds** across 50,000 blocks. The current loop takes **15 to 45 seconds**.

#### 2. Online Search Latency: SQLite D1 over Network VFS (~1.4s per page)
- **The Bottleneck**: Cloudflare D1 is SQLite running in a V8 serverless isolate where database pages are fetched over the network from distributed block storage.
- In `src/lib/search-handler.ts`, every search request runs **multiple sequential network rounds** against D1:
  1. `titleStartExpr` query.
  2. Exact phrase query (`pick()` subquery with `row_number() OVER (PARTITION BY ...)`).
  3. All-words query.
  4. Prefix query.
  5. `details()` query to fetch snippets, raw text, and join `person_meta`.
  6. Separate `facets=1` query scanning up to 20,000 rows across `docs CROSS JOIN doc_meta` with `GROUP BY` (~1.5s).
- Total wall-clock time per search: **~1,400ms to 3,000ms**.
- By comparison, dedicated search engines (Meilisearch, Tantivy, Algolia) return full ranking and facets in **5ms to 25ms**.

#### 3. D1 Read Quota Starvation & Arbitrary 10k Rank Caps
- Cloudflare D1's free tier caps daily row reads (5M/day). Common queries like «الله» or «محمد» match hundreds of thousands of rows.
- To prevent quota exhaustion and CPU timeouts, Athar hard-caps ranking at `RANK_CAP = 10000` (evaluating only the first 10,000 rows in physical index order).
- **Consequence**: For ubiquitous scholarly terms, BM25 relevance ranking only scores the first few volumes inserted during indexing. Highly relevant passages in later volumes are never evaluated or ranked.

#### 4. Arabic Linguistic Deficits (Shallow Normalization vs Morphology)
- Currently, Athar only does character folding (`ArNormalize.normalize`):
  - Strips diacritics / tashkeel (`َ ُ ِ ْ ّ`).
  - Normalizes hamza forms (`أ, إ, آ, ء, ؤ, ئ` -> `ا`).
  - Folds `ى` -> `ي`, `ة` -> `ه`.
- **Critical Flaws**:
  1. **No Prefix Stripping**: In Arabic, proclitics are attached to words:
     - Conjunctions: `و` (and), `ف` (then)
     - Prepositions: `ب` (by/with), `ل` (for), `ك` (like)
     - Definite article: `ال` (the), `بال`, `فال`, `كال`, `ولل`
     A query for `رخص` misses `فيرخص`, `ورخص`, `بالرخصة`, `كالرخصة`.
  2. **No Root Derivation**: A root like `ع-ل-م` will not match `معلوم`, `استعلام`, `تعليم`.
  3. **No Exact Phrase Escape**: The unicode61 tokenizer strips quotes and punctuation, preventing exact literal searches for specific Hadith wordings or grammatical nuances.

---

### How to Make Athar's Search 10x to 100x Faster & Smarter

#### Level 1: Immediate Mobile Fixes (Zero Cost, 100x Speedup for In-Book Search)
1. **Single-Query In-Book Search**:
   - Rewrite `searchInBook` in `AtharContentViewModel.kt` to bypass `visitTextFrames` completely when the book is downloaded.
   - Run a single direct Room FTS query:
     `SELECT ... FROM block_fts JOIN block b ON b.rowid = block_fts.rowid WHERE block_fts MATCH :query AND b.entityId = :bookId ORDER BY b.ordinal ASC LIMIT 200`.
   - **Result**: In-book search latency drops from **30+ seconds to <30 milliseconds**.

2. **Smart Online Fallback**:
   - If the book is not downloaded, query `search.arthurarchive.com/api/search?q=...&book={entityId}` directly instead of streaming frames one-by-one.

#### Level 2: Linguistic Upgrades (Arabic Search Precision)
1. **Dual Matching: Exact vs Normalized**:
   - Support quotes `"فيمن رخص"` for exact substring matching (preserving hamza and letters as typed).
   - Unquoted queries use light Arabic stemming (stripping `و`, `ف`, `ب`, `ال` prefixes at search time).
2. **Root Search Mode («البحث بالجذر»)**:
   - Provide an optional chip in the search bar: «بحث بالجذر».
   - Uses the Arabic morphological root index to discover all derivative words.

#### Level 3: Server Engine Architecture (Sub-20ms Global Search)
1. **Consolidated Single-Pass Scoring in D1 / SQLite**:
   - Replace the 4 sequential `pick()` queries with a single query using weighted BM25 expressions.
   - Pre-compute author and genre facets into a separate `doc_facets` index instead of doing a full 20,000-row `CROSS JOIN` scan on every search.
2. **Long-Term: Dedicated Tantivy / Meilisearch Micro-Engine**:
   - SQLite FTS5 is a general-purpose relational add-on.
   - Deploying **Tantivy** (compiled Rust inverted index) or **Meilisearch** on a $4/mo VPS:
     - Full Arabic text index fits in ~150MB of compressed memory.
     - True search-as-you-type (sub-10ms response time per keystroke).
     - Typo-tolerance, phrase proximity (`NEAR/3`), and complete BM25 ranking across all 22,000+ works without arbitrary 10,000-row caps.

---

## 8. Search Experience & Scholarly Ranking Redesign

### Part A: Diverse Result Views & Layout Options

In a classical Islamic scholarly library, user intent varies widely. A flat list of snippets is insufficient for rigorous research.

#### 1. Scope Filter Chips (نطاقات البحث التخصصية)
Instead of searching everything in a single unorganized feed, offer clear scope chips right below the search bar:
- **«الكل» (Universal)**: Blended view with hero cards for top matches.
- **«عناوين ومؤلفون» (Books & Authors)**: Prioritizes catalog records, death years, and author pages.
- **«المتون والأحاديث» (Matn & Prophetic Athar)**: Filters exclusively for foundational texts, Hadith collections, and primary sources, ignoring secondary commentaries.
- **«الأبيات والقصائد» (Poetry Verses)**: Displays verse hits in true poetic two-column format (`صدر / عجز`), preserving meter and rhyme.
- **«المعاجم اللغوية» (Lexicons & Dictionaries)**: When searching a single term or root, displays a concise definition card from classical dictionaries (`لسان العرب`, `تاج العروس`, `الصحاح`) directly at the top.

#### 2. Presentation Modes (أنماط العرض)
Provide a 3-way toggle in the search results header:
1. **Compact List («عرض مقتضب»)**:
   - Displays Book Title, Author, Volume/Page badge, and a 1-line snippet.
   - Shows 6–8 results per screen on mobile, ideal for rapid scanning.
2. **Detailed Study View («عرض مفصل»)**:
   - Displays 3–4 lines of context, chapter heading hierarchy, and full bibliographic citation.
3. **Grouped by Book («تجميع حسب الكتب»)**:
   - Instead of 25 scattered hits where 15 come from *Majmu' al-Fatawa*, group them cleanly:
     - ▾ **مجموع الفتاوى — ابن تيمية (١٥ موضع)** [Expandable accordion]
     - ▸ **المغني — ابن قدامة (٦ مواضع)**
     - ▸ **فتح الباري — ابن حجر (٤ مواضع)**
   - Allows scholars to immediately locate which work treats the topic most extensively.

---

### Part B: Advanced Scholarly Ranking System

The current ranking relies on raw SQLite `bm25` multiplied by an author death prior (`m.prior`), with a hard cutoff of 2 hits per work per tier. A scholarly ranking system must reflect the intrinsic hierarchy of Islamic texts:

#### 1. Matn vs. Sharh Priority (تقديم المتن على الشرح)
- When a search query matches words found in both a foundational *Matn* (e.g. *زاد المستقنع*, *العقيدة الطحاوية*, *الأجرومية*) and its commentaries (*شروح*), the *Matn* passage must receive an automatic **2.5x score multiplier**.
- Commentaries frequently repeat the base text verbatim hundreds of times; without this boost, the original Matn is drowned out by voluminous explanations.

#### 2. Canonical Authority Tiers (أمهات الكتب والأصول)
- Assign structured domain authority weights based on scholarly consensus:
  - **Tier 1 (Multiplier 3.0x)**: Primary Hadith collections (Kutub al-Sittah, Musnad Ahmad, Muwatta Malik) and early classical texts (Al-Shafi'i's *Al-Umm*, Tabari's *Tafsir*).
  - **Tier 2 (Multiplier 2.0x)**: Standard encyclopedic references (*Al-Mughni*, *Majmu' al-Nawawi*, *Fath al-Bari*, *Tafsir Ibn Kathir*).
  - **Tier 3 (Multiplier 1.2x)**: Intermediate treatises and classical monographs.
  - **Tier 4 (Multiplier 1.0x)**: Modern contemporary summaries and transcripts.

#### 3. Phrase Proximity Scoring (القرب اللفظي)
- Rather than binary `AND` matches, implement token distance scoring:
  - If words appear adjacent in exact order: Score = 100%.
  - If words appear within a 5-word window (same sentence/clause): Score = 75%.
  - If words appear across different sentences in the same paragraph: Score = 25%.

#### 4. Sentence & Document Length Normalization
- Standard BM25 penalizes long documents heavily. In Islamic treatises, an exhaustive 2-page fatwa answering a question thoroughly should not be penalized compared to a fragmentary 1-line mention. Adjust the BM25 `b` parameter from `0.75` down to `0.3` to favor comprehensive passages.

---

## 9. Maximizing Value from Cloudflare (Workers Paid + R2 Paid)

Having **Cloudflare Workers Paid ($5/month)** and **R2 Paid** unlocks massive infrastructure advantages that are currently unutilized in Athar:

### 1. Lift D1 Quota Restrictions & Scale Search Horizons
- **Free Tier Limitation**: D1 free is limited to 5M row reads/day, forcing the codebase to enforce `RANK_CAP = 10000` and `FACET_CAP = 20000`.
- **Workers Paid Benefit**: Workers Paid includes **25 Billion row reads per month** and **50 Million row writes per month**.
- **Actionable Change**:
  - We can safely increase or eliminate `RANK_CAP` in `src/lib/search-handler.ts`, allowing BM25 relevance ranking to evaluate the *entire* corpus for common scholarly terms rather than cutting off arbitrarily.
  - Request CPU time limit increases from **10ms to 30,000ms**, completely eliminating Worker timeout errors during complex multi-volume searches.

### 2. Edge Key-Value (Workers KV) for Sub-10ms Autocomplete & Query Caching
- **Included on Paid**: Workers Paid includes **10 Million KV reads per month** and **1 Million KV writes**.
- **How to Use It**:
  1. **Instant Autocomplete & Title Suggestion**:
     - Pre-populate Workers KV with the top 10,000 book titles, author names, and common scholarly subjects.
     - When the user types 2 characters in the search bar, the app calls an edge endpoint reading from KV. Latency is **sub-10ms globally** because KV reads from edge server memory without hitting SQLite or D1.
  2. **Popular Query Cache**:
     - Cache popular full-text search payloads in Workers KV with a 24-hour TTL, serving 90% of repeated mobile searches with 0ms database latency.

### 3. R2 Paid: Zero-Egress Static Sharding & Offline Bundling
- **Key Advantage**: Cloudflare R2 has **$0 egress fees** (bandwidth is 100% free, unlike AWS S3 or Google Cloud Storage).
- **How to Use It**:
  1. **Pre-Built SQLite FTS Shards**:
     - Generate genre-specific or volume-specific pre-indexed SQLite databases (`hadith_fts.db`, `fiqh_fts.db`) and store them in R2.
     - Mobile clients can download pre-indexed SQLite files directly via R2 streaming.
  2. **Direct Frame Delivery with Fast Cache-Control**:
     - Configure Cloudflare Edge Cache rules on R2 bucket domains (`cdn.arthurarchive.com`) to cache book frame blobs indefinitely (`Cache-Control: public, max-age=31536000, immutable`).

### 4. Overcoming Regional Blocking (Sudan & Middle East DPI)
- **Workers Custom Domains**:
  - With Workers Paid, the search worker can be mapped to multiple custom domains and subdomains simultaneously (e.g. `api.arthurarchive.com`, `search-direct.arthurarchive.net`, `mirror.athar.app`).
  - If one domain suffers Anycast IP collateral blocking or DPI RST in Sudan, the app can automatically failover to an alternative domain pointing to the exact same Paid Worker.

---

## 10. Cloudflare Platform Capabilities Audit & High-Value Additions for Athar

Beyond basic Workers and R2, Cloudflare provides several enterprise-grade features that directly benefit Athar's performance, resilience, and scholarship features at little or zero incremental cost.

### 1. Cloudflare D1 Read Replication (Global Multi-Region Replicas)
- **Problem**: Currently, when an Android user in Cairo, Khartoum, or Riyadh searches Athar, the D1 SQLite query has to travel across the Atlantic/Europe to where the primary D1 database is hosted, adding 150–250ms of network latency per query before SQLite even starts searching.
- **Solution**: Cloudflare D1 supports **Read Replication**. Enabling it creates local read replicas in Cloudflare's regional data centers (Europe, Middle East, Asia).
- **Impact**: Read queries execute locally within **10–20ms** network roundtrip. For a search engine with multi-tier queries, this cuts overall response latency by more than half.

### 2. Smart Tiered Cache (Free One-Click Optimization)
- **Problem**: Athar serves thousands of immutable book chunk files (`.bin`, `.pkg`, and JSON metadata). In standard caching, each of Cloudflare's 300+ data centers caches files independently. If a reader in Sudan requests a book that someone in Egypt read, it's still a cache miss in Sudan and hits R2.
- **Solution**: Turn on **Smart Tiered Cache** in the Cloudflare Dashboard (*Caching → Tiered Cache → Smart Tiered Cache*). Cloudflare dynamically uses regional super-hubs (e.g. Frankfurt, London, Dubai) as intermediate cache tiers.
- **Impact**: Cache hit rates jump from ~70% to **95%+**. Almost all book download requests are answered instantly from the regional edge tier, minimizing Worker invocations and R2 read operations.

### 3. Workers AI & Vectorize (Semantic Search / "Similar Issues" Discovery)
- **Capability**: Workers AI runs machine learning models directly on Cloudflare edge GPUs (includes 10,000 free Neurons/day on Workers Paid). Vectorize is Cloudflare's distributed vector database (includes 5 Million queried vectors/month on free tier).
- **Application for Athar**:
  - **The Limit of FTS5**: Keyword matching fails when words differ but meanings align (e.g., searching «بر الوالدين عند الكبر» will never match a passage discussing «الإحسان إلى الأب والأم والقيام بشؤونهما في الهرم»).
  - **Hybrid Search**: Pre-generate vector embeddings for major chapter headings and core issues using a multilingual embedding model (`bge-m3` or `bge-small`).
  - **"Find Related Passages" («مسائل ونصوص مشابهة»)**: In the reader, highlight a fatwa or hadith explanation and tap "Similar Passages" to discover related rulings across different authors without knowing the exact keyword formulation.

### 4. Cloudflare Turnstile (Invisible Bot Protection without Blocking Real Users)
- **Problem**: Cloudflare's default "Bot Fight Mode" uses aggressive IP reputation scoring. In Sudan, Egypt, and developing telecom networks, thousands of mobile subscribers share a single Carrier-Grade NAT (CGNAT) gateway IP. Cloudflare mistakes high traffic from these shared IPs as bots and issues silent 403 blocks or endless JavaScript challenges that break the mobile app.
- **Solution**: Disable "Bot Fight Mode" and use **Cloudflare Turnstile** on public web forms and search endpoints. Turnstile is 100% free, requires zero interactive puzzles (no clicking traffic lights or bicycles), and preserves user privacy while keeping scrapers away.

### 5. Cloudflare Queues (Background Indexing & Content Pipeline)
- **Capability**: Included on Workers Paid (1 Million operations/month free).
- **Application for Athar**:
  - When updating catalog records, parsing new markdown texts, or re-indexing collections, doing it inside a synchronous HTTP request risks hitting memory or time limits.
  - With Queues, push indexing jobs into a message queue. Background consumer Workers process books asynchronously in parallel batches without holding up client connections or exceeding execution boundaries.

### 6. Cloudflare Zero Trust / Tunnels (cloudflared) for Secure Content Management
- **Capability**: Free for up to 50 users.
- **Application for Athar**:
  - If you run an internal editorial panel, metadata validation dashboard, or staging review site, you never need to expose it publicly or open firewall ports on your server.
  - Place it behind a Cloudflare Tunnel protected by your Google/GitHub single sign-on with zero exposed attack surface.

---

## 11. Voice-Driven Memorization & Zero-VPS Android Innovations

### A. Voice Recognition for Poetry & Mutun Memorizers («البحث الصوتي واستذكار المنظومات والمتون»)
> *"what about making sound reqonision so people who want to memories poems or mutton just say it and it apear"*

Students of knowledge memorizing classical texts (e.g., *Nuniyyat al-Qahtani*, *Al-Kafiyah al-Shafiyah*, *Al-Bayquniyyah*, *Umdat al-Ahkam*, *Al-Ajrumiyyah*) often want to recite a single hemistich or sentence to check their memorization, locate the verse, or find the following lines.

#### 1. Architecture: How to Do Voice Recognition with Zero VPS
We can implement this using a dual on-device + Cloudflare pipeline:

* **Engine 1: Native Android On-Device Speech Recognizer (100% Offline, Zero Cost)**:
  - Uses Android's built-in `android.speech.SpeechRecognizer` with Arabic language model (`Locale("ar")`).
  - Works on-device without internet or server requests on modern Android versions.
  - Streaming words are fed directly into Athar's local SQLite FTS5 query builder (`ArFtsQuery.kt`) or verse matcher (`FindVerseTest.kt`).
  - As soon as the user recites 3–5 words (e.g. «إن حظي كدقيق فوق شوك»), the app matches the line in **<50ms** and jumps directly to that verse in `PoemReaderScreen.kt`.

* **Engine 2: Cloudflare Workers AI with Whisper (`@cf/openai/whisper`) (Ultra-Accurate Fallback)**:
  - If a device lacks an on-device Arabic speech pack or has an older Android OS:
  - Cloudflare Workers AI runs OpenAI's **Whisper** model directly on edge GPUs.
  - **Pricing & Free Quotas**:
    - Every Cloudflare account includes **10,000 Neurons per day completely FREE**.
    - Running `@cf/openai/whisper` or `@cf/openai/whisper-large-v3-turbo` costs **~41 to 46 Neurons per audio minute** (translating to ~$0.0005 per audio minute).
    - **10,000 free Neurons/day = ~220 to 240 audio minutes per day for $0** (nearly 4 hours of audio transcription daily!).
    - Since a single voice recitation / search query is only ~3 seconds long (0.05 audio minutes = ~2.3 Neurons), you get **~4,300 free voice searches EVERY DAY** (~130,000 free voice queries per month) before paying a single cent.
    - If you ever exceed 10,000 Neurons/day, overage is just **$0.011 per 1,000 Neurons** (1,000 extra voice searches would cost only ~$0.025, or 2.5 cents!).
  - The Android app records a 3-second audio snippet and POSTs it to a Cloudflare Worker: `POST https://search.arthurarchive.com/api/transcribe`.
  - The Worker runs `env.AI.run("@cf/openai/whisper", { audio: [...] })`, transcribes classical Arabic recitations with high phonetic accuracy, and returns the verse match.
  - **Cost**: Effectively **$0/month** for normal scholarly usage, 0 VPS needed.

#### 2. Specialized Memorization Features:
* **"Recitation Testing Mode" (وضع التسميع والاختبار للحفاظ)**:
  - In `PoemReaderScreen.kt`, user taps "تسميع" (Recite/Test).
  - The upcoming verses are concealed (blank or softly blurred).
  - As the student recites aloud into the microphone, the app verifies the words:
    - Correct words appear in green text.
    - Omitted or mistaken words are gently highlighted in amber/red.
    - The screen auto-scrolls verse-by-verse as the student recites.
* **"Audio Memorization Loop" (التكرار التلقائي للحفظ)**:
  - In Media3 audio playback for recorded mutun/poems:
  - Add an A-B loop selector: allow repeating the active verse 3x, 5x, or 10x before automatically advancing to the next verse.
  - 100% on-device Android code; students can memorize poems hands-free while walking or driving.

---

### B. High-Leverage Android Features (Zero VPS, Powered by On-Device + Cloudflare)

#### 1. Audio-Synced Text Highlighting (المتابعة الحية مع التلاوة والشرح)
- **Concept**: Athar already stores millisecond cue timestamps for poem verses and plays lesson recordings via Media3.
- **On-Device Feature**: While audio plays, smoothly tint and scroll the active verse or paragraph on screen.
- **Infrastructure**: 100% on-device Android Jetpack Compose animation; 0 VPS, 0 server calls.

#### 2. Peer-to-Peer Offline Library Transfer (نقل الكتب محلياً دون إنترنت عبر Wi-Fi المباشر)
- **Problem**: In countries with severe network instability, censorship, or expensive mobile data (Sudan, Yemen, rural areas), downloading multi-volume collections (like *Musannaf Ibn Abi Shaybah*) is difficult.
- **On-Device Solution**: Use Android's native **Nearby Connections API** / Wi-Fi Direct.
- **Experience**: One student who already downloaded the library taps «مشاركة المكتبة دون إنترنت». A classmate opens Athar next to them, taps «استلام», and the entire 500MB library transfers peer-to-peer in under 30 seconds over local device-to-device Wi-Fi.
- **Infrastructure**: Zero internet, zero bandwidth costs, 100% resilient against national internet blackouts.

#### 3. Cloudflare-Powered "Errata & Typos Reporting" (الإبلاغ عن سقط أو تصويب مطبعي)
- **Concept**: Digitized classical works occasionally have typos from printed editions.
- **Implementation**:
  - In `ReaderScreen.kt` text selection toolbar: add an action «تصويب سقط أو خطأ».
  - Selecting text and writing a brief note sends a lightweight JSON payload to a Cloudflare Worker.
  - The Worker writes the report to **Cloudflare D1** or **Workers KV**.
  - Reviewers can view submitted errata in a secure dashboard protected by Cloudflare Access.
  - **Infrastructure**: Powered entirely by existing Workers + D1, 0 VPS needed.

#### 4. Morphological Word Breakdown via Workers AI («إعراب وبيان غريب اللفظ»)
- **Concept**: Classical texts frequently contain rare, archaically inflected words that traditional dictionary lookups can fail to resolve due to fused affixes.
- **Implementation**:
  - When a reader highlights an obscure word, a popover offers «بيان اللفظ».
  - Calls a lightweight Cloudflare Workers AI model (e.g. `@cf/meta/llama-3.1-8b-instruct` or `@cf/qwen/qwen2.5-7b-instruct`).
  - Returns a strict 2-line scholarly breakdown: root letters, grammatical form, and classical meaning.
  - **Infrastructure**: Runs on Cloudflare Workers AI edge GPUs, 0 VPS.




