Code taken out of the app before launch (2026-11-07), not compiled. To bring تسميع back: move `tasmee/` to
`app/src/main/kotlin/com/atharchive/feature/`, restore the onnxruntime dependency, RECORD_AUDIO and the telemetry
provider removal in the manifest, `TasmeeRoute`, `onTasmee` in the two readers and `tasmeeText` in the ViewModel (see git diff of 2026-11-07).
- `tasmee-test/` → `app/src/test/kotlin/com/atharchive/feature/tasmee/`
