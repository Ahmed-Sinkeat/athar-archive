# athar-archive — Android app plan

**Status (2026-07-25):** decided, not started. No Kotlin written yet.
**Decision: hybrid WebView shell in Kotlin.** Full native reconsidered only if
the app succeeds and the WebView feel proves to be the thing holding it back.

---

## The decision, and why

Three options were compared honestly before choosing.

| | Full native (Compose) | **Hybrid (WebView)** | TWA |
|---|---|---|---|
| Scroll/transition feel | best | good | same as browser |
| Arabic typography | **rebuild** (tashkeel, Amiri, tajweed spans, hemistich staggering) | identical to the site, free | identical |
| Needs a content API | **yes — does not exist** | no | no |
| Native audio / downloads / notifications | yes | yes | **no — impossible** |
| UI fix turnaround | Play Store review (1–3 days) | instant (web deploy) | instant |
| Build time | 3–6 months | 4–8 weeks | 1–2 days |
| Ongoing cost | **two UIs forever** | one UI | one UI |

**TWA is ruled out by construction, not by preference.** A Trusted Web Activity
renders inside a Chrome Custom Tab that the app does not own: no JS bridge can
be injected, no `MediaSessionService` can attach to its audio, no `WorkManager`
download can be tied to it. Every feature that justifies building an app at all
is unreachable. TWA exists only to put a PWA in the Play Store.

**Full native was rejected for now on two costs people don't see coming:**

1. **There is no content API.** Everything is prerendered HTML — 78k chapter
   pages, tafsir fragments, R2 objects. A native UI cannot consume that; it
   needs JSON/markdown endpoints plus a sync protocol. That is a substantial
   backend project *before* any Android UI exists.
2. **Two UIs, maintained by one person, forever.** Every future fix gets built
   twice. The archive's 6.5k entries exist because of shipping velocity; halving
   it permanently is the real price.

Note the update direction is the opposite of intuition: **adding a book never
needs an app release in either approach** (content comes from the server). What
full native makes slow is *UI* changes — each one becomes a store submission.

---

## Why an app at all — benefits, ranked by what's real

1. **Background audio.** The strongest single reason. شروح run ~59 minutes; in a
   browser Android suspends audio when the screen locks or the user switches
   apps. A native `MediaSessionService` keeps playing with the screen off, with
   lock-screen/Bluetooth/car controls. The PWA cannot fix this.
2. **Downloads that survive.** Browser storage is *evictable* — Android clears it
   under memory pressure, silently. Someone with 2 GB of downloaded audio can
   lose it. App storage is never evicted, and `WorkManager` gives resumable
   background downloads.
3. **أذكار reminders.** The أذكار page already tracks counters; morning/evening
   notifications are reliable only natively.
4. **Play Store discoverability.** Arabic-speaking users find Islamic apps by
   searching the store, not the web. Real distribution.
5. **Offline search over downloaded books.** See the sizing below.

**Not benefits** (do not put these in a pitch): speed — the PWA is already
cached and fast; UI quality — it would be a rebuild of something already tuned;
offline *reading* — the service worker already does it.

---

## Offline search — measured, not estimated

| Measurement | Value |
|---|---|
| `src/content` raw | **2.4 GB** (`book-lg` alone is 2.3 GB) |
| compressed (zstd-19) | **361 MB** |
| D1 index (for comparison) | 2.51 GB |

So:

- **Bundled in the APK:** metadata only — every title/author/topic, ~6.5k
  entries, a few MB. Lets you search *what exists* with no connection.
- **Full text of downloaded books:** the target. Download 20 books, search
  inside those 20 instantly, offline.
- **Whole archive offline:** ~500 MB with the FTS index on top. Viable as an
  opt-in bulk download *in a native app* (storage is not evicted) — not viable
  in the PWA, where the browser can drop 500 MB without warning.

**Correctness constraint:** the app must use **exactly** the normalisation in
`src/lib/ar-normalize.ts` (hamza seats, ta-marbuta, tashkeel stripping). If the
Kotlin port differs even slightly, the same query returns different results in
the app than on the site — a miserable class of bug. Port
`src/lib/ar-normalize.test.ts` alongside it so the port is *verified*, not hoped.

Index: Room + FTS4 over a pre-normalised column. FTS5 is not guaranteed across
Android SQLite versions without bundling your own SQLite.

---

## Pre-work — web-side, needed before any Kotlin

An audit of the current code found things that silently break once there is no
browser around the page. **All of this is web work that costs the site nothing
and is worth doing regardless.**

| Finding | Uses | Behaviour in a WebView |
|---|---|---|
| `target="_blank"` | 8 | **Dead links** — includes the PayPal مساهمة button |
| `navigator.share` | 6 | Unavailable by default → share buttons fail silently |
| `Notification` | 11 | Web Notifications do not exist in a WebView |
| `beforeinstallprompt` | 3 | Would show «تثبيت التطبيق» *inside* the installed app |
| `navigator.clipboard` | 13 | Works, but needs explicit WebView permission config |

**The one that will hurt: storage migration.** `localStorage` is used 55× in
`reader.ts` alone, plus `library.ts`, `marks.ts`, `adhkar.ts` — bookmarks,
favourites, reading progress, أذكار counters, settings. **A WebView app has a
different storage origin from the browser**, so a PWA user who installs the app
loses all of it. This needs a migration path designed *before* launch, not after
the first angry message.

### The capability layer

The architectural answer to all of the above. One module where feature code stops
asking *"does the browser support this?"* and asks *"can this platform do this,
and how?"*:

```
share(text, url)   → navigator.share  OR  native bridge
notify(...)        → Notification     OR  native channel
openExternal(url)  → window.open      OR  native Intent
storage            → localStorage     OR  native, with migration
```

Write it web-only now, route every feature through it, and the app implements the
native half once — with **no change to `reader.ts`, `share.ts` or `adhkar.ts`**.
Without it, `if (isAndroidApp)` spreads through every feature, which is exactly
the two-codebases problem that choosing hybrid was meant to avoid.

### Scroll parity

WebView *is* Chrome, so anything that makes the site scroll better makes the app
scroll better — optimise once, get it twice.

- Scroll listeners are **already passive** ✅ (`marks.ts`, `reader.ts`)
- **`content-visibility` is absent** — the largest available win on long chapter
  pages, which currently lay out every off-screen paragraph.
  ⚠️ **Caveat:** it interacts with in-page find (`marks.ts` «بحث في الصفحة») and
  anchor jumps (`#v11`, athar, footnotes, annotation popups). Must be tested per
  page-type, never applied as a blanket rule.
- `backdrop-filter: blur(16px)` on a persistent element + `blur(6px)` on the
  annotation scrim — blur on anything sticky repaints every frame while scrolling.
  17 `position: fixed` + 6 `sticky` declarations; each is a compositing layer.

---

## Build order

| # | Step | Notes |
|---|------|-------|
| 0 | Web pre-work above | capability layer, storage migration, scroll parity |
| 1 | Skeleton + WebView | site loads, back button, no browser chrome |
| 2 | **Native audio** | Media3 + `MediaSessionService`; JS↔Kotlin bridge so `reader.ts` delegates playback |
| 3 | Downloads | `WorkManager`, resumable, survives app close |
| 4 | أذكار notifications | morning/evening channels |
| 5 | Offline search | Room FTS over downloaded books + ported normalisation |

**Step 2 is where the real difficulty is** — the bridge, and keeping the web UI
and the native player in sync. Steps 3–5 are comparatively mechanical.

Realistic: **4–8 weeks part-time** for all five.

---

## Prerequisites

Nothing Kotlin can be written *and verified* until the toolchain exists. As of
2026-07-25 this machine has `adb` only — no `java`, no `ANDROID_HOME`, no gradle.

```sh
sudo pacman -S jdk21-openjdk
# + Android command-line tools, then:
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

With a device connected over `adb`, the app can be built, installed and verified
rather than handed over unbuilt.

Also needed for release: Play Store developer account ($25 once), privacy policy,
content rating.

---

## Settled / open

**Settled**

- Hybrid WebView, Kotlin, Android-only (Flutter rejected — it would mean
  re-solving Arabic text rendering the browser already gets right, and only pays
  off if iOS is wanted; the PWA covers iOS meanwhile).
- WebView loads the **live site**, not bundled assets — the service worker,
  downloads and reading UI already work; shipping CSS tweaks through store review
  would be absurd.
- Code lives in this repo under `android/` — one history for content and app.
- Min SDK 26 (notification channels, WorkManager).

**Open**

- Storage migration mechanism (PWA → app) — **must be answered before launch**.
- Whether the opt-in full-archive download (~500 MB) ships in v1 or later.
- API cost: expected to **go down**, not up — R2 egress is free, JSON is cheaper
  than HTML, and local search reduces D1 reads (the 540M/month figure that
  prompted the investigation).
