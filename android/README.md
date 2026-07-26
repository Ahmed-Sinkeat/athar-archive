# android/ — أهل الأثر companion app

Plan and decisions: [docs/android-app.md](../docs/android-app.md). Status:
**pre-work phase** — no app yet. What exists:

- `core/` — pure-JVM Kotlin module (no Android SDK needed for its tests).
  - `ArNormalize.kt` — port of `src/lib/ar-normalize.ts`, verified against
    golden vectors generated from the TS implementation.
  - `ArNormalizeTest.kt` + `ar-normalize-vectors.tsv` — regen vectors with
    `pnpm app:vectors` after any change to the TS normalizer.

Content the app will consume is already built and deployed by the site:
`pnpm app:gen` → `dist/r2-upload/app/v1/` (catalog + per-chapter JSON + hash
manifest), served at `/app/v1/*` — see `scripts/gen-app-content.ts`.

## Toolchain (not yet installed on this machine)

```sh
sudo pacman -S jdk21-openjdk gradle
cd android/core && gradle test   # must pass before any Kotlin is trusted
```

⚠️ Editing Arabic character classes: always write codepoints as `\u` escapes,
never paste literals — bidi display reorders them silently (bit twice here).
