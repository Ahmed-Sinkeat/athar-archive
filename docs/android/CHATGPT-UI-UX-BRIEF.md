# Athar Android — UI/UX brief for ChatGPT

Upload this file to ChatGPT together with one or more screenshots of the current website.
The screenshots are visual references, not layouts that must be copied. Ask ChatGPT to
redesign each referenced page as a native Android screen using the brief below.

---

## Prompt for the designer

You are designing **Athar Android (أهل الأثر)**, a native, Arabic-first, offline scholarly
library for serious readers, students and researchers. Study the attached website screenshot
for its identity, typography, hierarchy and atmosphere, then redesign the corresponding
experience for Android. Do not wrap, trace, or merely shrink the website.

The result must feel like the same institution but a better native reading tool: quiet,
trustworthy, information-dense, tactile and built for long sessions with classical Arabic.
It will be implemented in Kotlin and Jetpack Compose, so every design must be realistic to
build with native components and adaptive layouts.

### Product boundary

Athar is a **general Arabic scholarly-book library**, not a Qurʾan or Hadith application.

- Tafsir and Hadith works can appear as ordinary books.
- Do not create Qurʾan, surah, ayah, mushaf, recitation, tajwid, Hadith grading, isnad, or
  verse-linked tafsir screens.
- Do not place Qurʾan or Hadith as special bottom-navigation destinations.
- The core jobs are discovering books, downloading them, reading offline, searching Arabic
  text, organizing a personal library, taking notes, citing passages and playing recordings
  already attached to catalog entries.

### Relationship to the website

Preserve from the screenshot:

- The scholarly manuscript-inspired character
- Warm paper surfaces, maroon identity and restrained gold
- Strong Arabic typography and clear hierarchy
- Calm borders and modest ornament
- A sense of a curated archive rather than a commercial bookstore

Do not preserve:

- Desktop navigation bars, web breadcrumbs or hover interactions
- Website-sized hero sections
- Dense multi-column layouts on a phone
- Browser download behavior or web popovers
- Every site feature; the Android app is deliberately narrower

### Existing visual foundation

The website currently uses:

- Display and book titles: **Amiri**, with Noto Naskh Arabic fallback
- Reading text: **Noto Naskh Arabic**, with Amiri fallback
- Interface text: **IBM Plex Sans Arabic**
- Small controls: 8 dp radius
- Cards, sheets and panels: 10 dp radius
- Pills: fully rounded
- Depth primarily through borders and surface contrast, not permanent shadows

Paper theme:

| Token | Colour |
|---|---|
| Background | `#F4EEE2` |
| Card | `#FBF7ED` |
| Recessed surface | `#EDE5D3` |
| Primary ink | `#26190F` |
| Secondary ink | `#63523F` |
| Tertiary ink | `#98876F` |
| Brand maroon | `#7A2222` |
| Pressed maroon | `#5E1717` |
| Restrained gold | `#8E6A1C` |

Also design compatible dark and clean-light themes:

- Dark: `#0A0A0A` background, `#161613` card, cream text, `#D4A72C` accent
- Clean light: `#EEF1F6` background, white card, dark ink, `#1E4E8C` accent

You may refine these colours for Android contrast, but explain material changes. Dynamic
system colour is disabled: Athar must retain its identity.

### Navigation model

Start with four RTL bottom destinations:

1. **مكتبتي** — continue reading, Read later, Reading, Finished, collections and recent books
2. **تصفّح** — catalog browsing by author, topic, century and work type
3. **بحث** — catalog search and offline full-text search in downloaded books
4. **الدفتر** — bookmarks, highlights and notes across all books

Downloads are reached from the library/app bar and download status; they do not need a fifth
permanent destination. A compact audio player may sit above the bottom navigation while a
recording is active.

If you propose a different navigation system, show why it is clearer for RTL phone and
tablet use. Do not add destinations merely to make the bar look full.

### Required screen system

Design the attached page in the context of this complete system:

1. Library home
2. Browse/catalog and filters
3. Search entry, suggestions and results
4. Book detail with metadata, download state and Continue reading
5. Full-screen reader
6. Contents, printed-volume/page jump and footnote sheet
7. Text-selection actions: Copy, Highlight, Note, Share
8. Notebook with book/topic/kind/colour filters
9. Collections and reading-status management
10. Downloads/storage manager
11. Compact and expanded attached-audio player
12. Reader settings

### Reader requirements

The reader is the product's most important screen. Prioritize text over chrome.

- Full RTL Arabic layout with correct directional icons
- Adjustable text size, line height and content width/margins
- Paper, dark and clean-light themes
- Optional tashkil hiding without changing copied text
- Chapter table of contents
- Printed volume and page markers/jump
- Footnotes that open without losing reading position
- Search inside the current book and exact jump to the matched paragraph
- Bookmarks, multi-paragraph highlights and notes
- Reading progress and reliable position restoration
- A calm distraction-free mode without hiding essential navigation behind unknown gestures

On phones, use a compact top app bar and a contextual bottom reader bar or sheets. On
tablets, explore a two-pane layout with contents/library beside the reader. Do not put long
Arabic prose inside small decorative cards.

### Offline and state requirements

The interface must communicate these states clearly without technical language:

- Available online, downloading, verifying, importing, downloaded and update available
- Cached versus deliberately downloaded, without making users understand databases
- Download progress, retry and insufficient-storage states
- Entirely offline startup with locally stored catalog data
- Search limited to downloaded text while offline, with an honest explanation
- Notes that are exact, approximately restored, or unable to find their old passage

Never block the opening screen behind a network spinner. Existing local content appears
immediately; synchronization is secondary and non-blocking.

### Accessibility and Android constraints

- Design first at **360 × 800 dp** and also show the key adaptive reader at tablet size
- RTL is the source layout, not a mirrored afterthought
- Minimum interactive target: 48 × 48 dp
- Support Android system font scale up to 200%
- Arabic reading text needs generous, explicitly specified line height
- All icon buttons require visible or accessibility labels
- Do not communicate state by colour alone
- Respect reduced motion and use short, functional transitions
- Account for status/navigation bars and gesture insets
- Prefer standard Android behaviors for Back, selection, sheets, notifications and media

### Avoid

- Generic green-and-gold “Islamic app” styling
- Excessive arches, mosque silhouettes, geometric ornaments or calligraphy decoration
- Gradients, glassmorphism, glowing shadows or oversized rounded cards
- A shopping-store book grid
- Tiny Arabic text or Latin-first spacing
- Fake AI summaries, chat, social feeds, streaks or gamification
- Specialist Qurʾan/Hadith/tafsir features
- Visual concepts that cannot be implemented faithfully in Jetpack Compose

### What to return for each screenshot

Return all of the following:

1. A polished phone mockup of the redesigned Android screen in Arabic and RTL
2. The screen's purpose and primary action in one sentence
3. An annotated layout with dimensions, spacing, type sizes and component behavior
4. Reusable component names and their default/pressed/disabled/loading/error states
5. Exact Arabic interface copy—do not use placeholder gibberish
6. Navigation and Back behavior
7. Offline, empty, loading and error variations relevant to that screen
8. What was retained from the website screenshot and what was intentionally changed
9. Any unresolved product decision as a short explicit question

Do not output implementation code unless asked. First establish a coherent visual system and
screen behavior. Keep later screens consistent with decisions already made in earlier ones.

---

## Recommended screenshots to upload

For the best first pass, upload these separately or together:

1. Website home page — brand, palette and broad hierarchy
2. Books/catalog page — cards, filters and information density
3. Book landing page — metadata, contents and download actions
4. A chapter midway through a long book — the actual reading experience
5. Reader settings or footnotes open — current interaction language

If sending only one screenshot, start with a **long-book reader screen**, because the reader
should establish the typography, chrome and interaction system that the rest of the app
follows.
