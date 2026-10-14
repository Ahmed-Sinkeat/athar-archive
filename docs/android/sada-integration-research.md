# Athar ↔ Sada integration research

Investigated 12 Oct 2026. Source inspection and official Android documentation only;
no integration implemented, builds run, devices touched, or remote services changed.
Complexity below is comparative engineering judgment, not a delivery estimate.

## Recommendation

Keep both apps independently useful. Start with **Edit in Sada** for an explicitly
downloaded Athar recording. Follow with source metadata and **Open source in Athar**.
Use Android's existing file-sharing mechanism; this requires no account, new backend,
shared database, or new dependency. Athar still needs audio acquisition/verification
and a narrowly scoped file provider before it can send a file.

If the goal is specifically to avoid building Athar's player, an optional Media3
connection to Sada is technically plausible. It is a separate, larger change, with
installation, playback ownership, URI lifetime, and version-compatibility requirements.
Do not silently replace Athar's planned M9 with this dependency.

## What the repositories actually support

| Finding | Local evidence |
|---|---|
| Sada is `Projects/audio-editor`, package `com.tapeline.app`; benchmark package adds `.benchmark`. Its roadmap records 0.2.0 published and 0.2.1 in local review. | [README](/home/sinkeat/Projects/audio-editor/README.md), [roadmap](/home/sinkeat/Projects/audio-editor/docs/ROADMAP.md), [build configuration](/home/sinkeat/Projects/audio-editor/app/build.gradle.kts) |
| Sada receives single-file `ACTION_SEND` audio and `ACTION_VIEW` content audio/video. Runtime validation requires `content://`; text links, HTTPS URLs, `file://`, and multi-file sends are not supported by this entry point. | [manifest](/home/sinkeat/Projects/audio-editor/app/src/main/AndroidManifest.xml), [MainActivity](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/MainActivity.kt:145), [intent tests](/home/sinkeat/Projects/audio-editor/app/src/test/java/com/tapeline/app/IncomingMediaIntentTest.kt) |
| Receiving a file launches the existing import worker and opens Sada Lab when ready. This is an editor import, not a listen-only action. The worker retains an encoded local copy and builds indices. | [HomeScreen](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/ui/screens/HomeScreen.kt:777), [ExportWorker](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/work/ExportWorker.kt:507) |
| Sada has an exported Media3 `PlaybackService`, standard player commands, and custom sleep, A–B repeat, mark, silence, and sound commands. Its current callback accepts connections and advertises all custom commands without a caller-specific branch. It is a `MediaSessionService`, not a browsable/searchable `MediaLibraryService`. | [manifest](/home/sinkeat/Projects/audio-editor/app/src/main/AndroidManifest.xml), [PlaybackService](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/audio/PlaybackService.kt:337), [PlaybackCommands](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/audio/PlaybackCommands.kt) |
| Sada identifies listening progress, marks, playlists, and ranges by URI. There is no Athar content identity/provenance model in the inspected path. | [ListeningEntities](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/data/db/ListeningEntities.kt), [ListenMarkEntity](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/data/db/ListenMarkEntity.kt), [RecordingEntity](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/data/db/RecordingEntity.kt) |
| Sada implements timestamped transcript viewing, search, seek, and SRT sharing locally, but `TRANSCRIPTION_ENABLED = false`; the roadmap defers its release pending quality/model decisions. | [ModelCatalog](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/models/ModelCatalog.kt:28), [ModelToolsUi](/home/sinkeat/Projects/audio-editor/app/src/main/java/com/tapeline/app/models/ModelToolsUi.kt:128), [roadmap](/home/sinkeat/Projects/audio-editor/docs/ROADMAP.md) |
| Athar's signed catalog already carries audio IDs, addressed paths, hashes, formats, durations, sizes, and optional poem cues. Room retains those references. The staging poem has 29 timing cues. | [AppContentContract](../../android/core/data/src/main/kotlin/com/atharchive/core/data/content/AppContentContract.kt:81), [content database](../../android/core/data/src/main/kotlin/com/atharchive/core/data/db/content/ContentDatabase.kt:69), [plan](../main-plan.md) |
| Athar has outgoing text sharing but no incoming app-link/share route or file provider in its app manifest. Its audio UI still uses fixtures; real audio playback/download remains planned M9. Local M8 annotation/notebook and user archive work exists uncommitted. | [manifest](../../android/app/src/main/AndroidManifest.xml), [AtharApp](../../android/app/src/main/kotlin/com/atharchive/ui/AtharApp.kt), [plan](../main-plan.md), [annotation repository](../../android/core/data/src/main/kotlin/com/atharchive/core/data/annotation/AnnotationRepository.kt) |

These findings describe the local trees, which both contain uncommitted changes.
They do not establish which integration hooks exist in the Play-delivered Sada APK.

## Feasible connections

| User workflow | Mechanism and missing work | Relative scope |
|---|---|---|
| **Athar → Edit in Sada** | Athar obtains and verifies audio against its catalog, exposes that file through `FileProvider`, and sends one `content://` URI with read access. Sada's existing receiver imports it. Basic file import can reuse the receiver; source attribution needs additional Sada handling. | Smallest useful bridge; Athar audio download is a prerequisite |
| **Sada → Open the source in Athar** | Retain the source entry ID/URL and audio identity on import; add an action that opens an Athar HTTPS App Link. Athar routes to the correct content kind and optionally timestamp/block. Without Athar installed, the URL opens the website. Website timestamp/block behavior must also be implemented if needed. | Small–medium |
| **Browse Athar recordings inside Sada** | Add an optional Athar source backed by the existing signed R2 catalog, then explicitly download/import selected tracks. Sada needs signature/hash verification, catalog presentation, and provenance retention; it does not need direct access to Athar's database or even Athar installed. The current four-entry staging catalog is only a starting slice, and expanding Sada beyond device-library browsing is a product decision. | Medium; reuse the content service, no new backend required |
| **Listen in Sada while reading Athar** | Athar connects to Sada's Media3 service, supplies a playable track, controls play/pause/seek, and observes source position. Athar can map original poem time to its existing cues. Requires media-item resolution/validation, file access or an explicitly supported network source, caller policy, availability handling, and a stable contract. | Medium–large; not a proven integration |
| **Save a spoken benefit to الكناشة** | Send selected text/comment plus original recording ID/hash, source link, and start/end time. Athar needs an incoming route and an audio-excerpt model: its current annotation endpoints refer to text blocks, not audio ranges. Preserve the excerpt even when the recording is unavailable. | Medium |
| **Search Athar from Sada** | Add an explicit action using a title, speaker, or selected transcript phrase to open Athar's search route. Requires incoming query routing; a website search link could provide an initial fallback after checking the site's actual URL contract. This opens Athar's search; it does not merge private databases. | Small–medium |
| **One search across Athar and Sada** | Each app answers searches over its own data through a deliberate API. A MediaLibraryService can expose audio browsing/search; transcript hits and notebook excerpts need an agreed result contract. Athar searches only locally available text today, and Sada transcript release is deferred. Do not promise full-archive or arbitrary speech search. | Large; defer until a concrete need |
| **Sada as an Athar authoring tool** | Export clips, optionally captions, then review and attach them through Athar's existing authoring/package/signing pipeline. File export is available; automated publication, transcript import, provenance and retiming are new work. | Manual workflow first; automatic publishing is separate |

Android documents both [cross-app file sharing](https://developer.android.com/training/secure-file-sharing)
and [receiving shared data](https://developer.android.com/develop/ui/compose/sharing/receive).
`FileProvider` avoids exposing raw filesystem paths. A temporary read grant lets the
receiving app access the selected file, rather than the sender's whole private storage.
[Grant details](https://developer.android.com/training/secure-file-sharing/share-file).

Verified HTTPS [App Links](https://developer.android.com/training/app-links/about)
associate a domain with an app and give a website fallback. They require app intent
filters plus hosted Digital Asset Links statements matching the deployed app-signing
certificate. Signing/configuration of the published build must be checked when implemented.
[Setup](https://developer.android.com/training/app-links/add-applinks).

Media3 explicitly supports a [MediaController connected to another app](https://developer.android.com/media/media3/session/connect-to-media-app),
with playback commands and state listeners. Cross-app service connections require
appropriate package visibility declarations. A [MediaLibraryService](https://developer.android.com/media/media3/session/serve-content)
adds browsing and search callbacks; an exported playback service alone does not expose
a searchable library. This supports the proposed architecture, not a claim that Sada
already implements it.

## Boundaries that affect the design

- **A remote URL cannot use Sada's existing import route.** It rejects non-content
  schemes. Download-and-share fits the current boundary; adding streaming to the
  activity is a distinct product/API change. A playback-service connection needs its
  own validation and tests, including any HTTP fallback behavior.
- **Temporary URI access is not a durable download.** Sada currently copies the
  source inside an asynchronous worker. Losing the grant before that copy completes
  can break import/retry. Keep Athar's shared file available until handoff completes
  and verify grant expiry/process-death behavior; do not assume
  `takePersistableUriPermission` succeeds for a FileProvider URI. Android distinguishes
  [temporary grants](https://developer.android.com/training/secure-file-sharing/share-file)
  from [persistable document grants](https://developer.android.com/training/data-storage/shared/documents-files).
- **Stable source identity matters.** An evolving filename or local URI cannot alone
  identify the same recording in both apps. A later metadata contract should carry
  version, entry kind/ID, audio ID/hash, title/speaker/source URL, and optional source
  time range. Retain that information on Sada's project/import, not just on a screen.
- **Edits change time coordinates.** Speeding playback preserves source coordinates
  if the player reports them correctly; cutting, joining, or rendering a new timeline
  needs an original-to-edited mapping. Do not attach Athar's old cues to edited output
  as if its hash and timestamps were unchanged. Start with the original recording.
- **Playback ownership must be explicit.** Sada owns its service queue, listening
  progress and audio focus. Athar should control Sada only after the user chooses
  that mode, handle disconnect/uninstall, and avoid two active players competing.
  Optional Sada integration must not make reading depend on Sada installation.
- **Resolve requested media deliberately.** Media3's default callback accepts items
  with playable local configuration; IDs/request metadata require explicit resolution.
  [MediaSession.Callback](https://developer.android.com/reference/androidx/media3/session/MediaSession.Callback).
  Sada currently does not provide an Athar ID resolver. Restrict bridge commands to
  intended callers and validate requested paths/URIs; Media3 supplies caller information
  for [per-controller behavior](https://developer.android.com/media/media3/session/control-playback).
- **Private libraries stay private.** Exchange selected files/records or scoped API
  results; do not open the other app's Room database. Android's [app-specific storage](https://developer.android.com/training/data-storage/app-specific)
  is isolated. Broad storage access and shared UIDs are unnecessary for these workflows.
- **Different deployment versions are normal.** Check companion availability and
  gracefully handle missing/older versions. The local benchmark package differs from
  the production package. A simple activity launch can handle a missing target;
  preflight discovery and service binding need suitable `<queries>` declarations.
  [Package visibility guidance](https://developer.android.com/training/package-visibility/use-cases).
- **Do not duplicate Sada's entire engine into Athar.** Inspection shows playback is
  coupled to Sada's database, preferences, Rust engine, enhancement policy and Pro
  checks. A shared player library is possible but would be an extraction project.
  A small standard Media3 player in Athar plus optional Sada editing is simpler initially.

## Concrete first implementation scope, if selected

1. Obtain and hash-verify one staging poem audio file in Athar; expose only the audio
   handoff directory through FileProvider.
2. Add **Edit in Sada** using the existing single-audio send contract and a missing-app
   fallback. Retain a complete verified source during import; preserve the original.
3. Check one Opus handoff, missing target, unreadable/expired access, and interrupted
   import. Establish behavior before promising unattended resumable handoffs.
4. Separately add source identity retention and an Athar source-opening route. This
   requires Sada changes; it is not achieved by adding unused Intent extras.

The other options remain alternatives or follow-ups, not an implementation commitment.
No new shared module, automatic sync, AI processing, account system, or cloud publishing
is needed for the first bridge.
