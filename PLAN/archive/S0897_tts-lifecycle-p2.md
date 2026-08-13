# S0897 - TTS lifecycle: speech survives viewer close, init failure bricks state machine (P2 cluster)

**Ticket:** S0897
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком.

- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt:702 - TTS keeps speaking after viewer close and file swap - stop() is wired only to page/chapter navigation, not to any close/swap path
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt:86 - Failed TTS init permanently bricks the state machine in INITIALIZING - no retry or reset path short of host release()

## 1. Goal (RU)

Два независимых дефекта в TTS "Read Aloud" текстового вьюера:

- Речь продолжается после закрытия вьюера и после смены файла - `TtsReadAloudManager.stop()` вызывается только при пролистывании страниц (`nextPage`/`previousPage`), но не на путях закрытия и смены источника чтения. В итоге TTS озвучивает уже невидимый или устаревший текст.
- Провал инициализации TTS-движка навсегда клинит стейт-машину. После неудачного `onInit` объект `tts != null`, но `isInitialized == false`, а `onInit` повторно не вызывается - любой следующий `startReading` уходит в `INITIALIZING` без выхода, кроме `release()` хоста. Пользователь больше не может запустить озвучку до пересоздания вьюера.

Цель - остановить речь на всех путях закрытия/смены источника и сделать провал init восстановимым (следующая попытка пересоздаёт движок).

## 2. Constraints

- Не менять потоковую модель `TtsReadAloudManager` (S0871: state main-confined через `updateState`).
- `stop()` идемпотентен и безопасен при `IDLE` - лишние вызовы безвредны.
- Никаких новых строк/флейвор-гейтов/схем - только lifecycle-провязка.

## 3. Phases

### Phase 1 - Recoverable TTS init failure (`TtsReadAloudManager.kt`)

- Step 1.1: In `onInit`, failure branch (`status != SUCCESS`), tear down the dead engine before setting `ERROR`: call `tts?.shutdown()`, set `tts = null`, `isInitialized = false`, then `updateState(TtsState.ERROR)` and the existing toast.
  - Verification: after a failed init, `tts == null` so the next `startReading` re-enters the `tts == null` branch (line ~86) and constructs a fresh `TextToSpeech`, re-triggering `onInit`. No path leaves the machine stuck in `INITIALIZING`.
- Step 1.2: Confirm `toggle` `ERROR -> startReading` still reaches re-init (it does, because Step 1.1 nulls `tts`). No change needed beyond Step 1.1; note it in the audit.
  - Verification: grep `TtsReadAloudManager.kt` - failure branch nulls `tts`; `startReading` `tts == null` guard unchanged.

### Phase 2 - Stop TTS on every close / content-swap path (`TextViewerManager.kt`)

TTS reads `originalTextWithoutNumbers`. Every path that closes the viewer or replaces the read source (other than page nav, already handled) must call `ttsManager?.stop()` first.

- Step 2.1: `displayText(mediaFile, isWritable)` - add `ttsManager?.stop()` before `currentFile = mediaFile` (file swap).
- Step 2.2: `closeTextViewerFromBackPress()` - add `ttsManager?.stop()` inside the `currentFile != null` block, before `closePager()`.
- Step 2.3: `btnCloseTextViewer` click listener, text-file branch (`currentFile != null`) - add `ttsManager?.stop()` before `closePager()`.
- Step 2.4: `reopenWithEncoding(charset)` - add `ttsManager?.stop()` before `closePager()` (re-read replaces the buffer).
- Step 2.5: OCR/translation content swap + close - add `ttsManager?.stop()` at the start of `displayOcrText`, `displayTranslatedText`, and `hideOcrText` (source text changes or viewer content is torn down).
  - Verification (all steps): grep `TextViewerManager.kt` for `ttsManager?.stop()` - present in `nextPage`, `previousPage`, `displayText`, `closeTextViewerFromBackPress`, close-button text branch, `reopenWithEncoding`, `displayOcrText`, `displayTranslatedText`, `hideOcrText`. `release()` still calls `ttsManager?.release()` (unchanged).

### Phase 3 - Build gate

- Step 3.1: `standard debug` compiles (`a.ps1 dq`). Detekt-clean on both touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings on `TtsReadAloudManager.kt` / `TextViewerManager.kt`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0871 (TTS thread-confinement - do not regress).

## Related

- S0878 (audit tail container - triage source).
- S0871 (TTS state main-confinement).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** BlockNeedUserTest.

Both P2 findings implemented; `standard debug` Kotlin compile PASS.

- **Phase 1 - init-failure recovery (`TtsReadAloudManager.kt` onInit else-branch).** On `status != SUCCESS` the dead engine is now torn down (`tts?.shutdown()`, `tts = null`, `isInitialized = false`) before `updateState(ERROR)`. Next `startReading` re-enters the `tts == null` branch and constructs a fresh `TextToSpeech`, re-triggering `onInit` - the machine can no longer be permanently stuck in `INITIALIZING`. Thread model unchanged (S0871 `updateState` post preserved). Evidence rung: static + compile (P2).
- **Phase 2 - stop TTS on close/swap (`TextViewerManager.kt`).** `ttsManager?.stop()` now fires on every read-source teardown/swap path, not only page nav: `displayText` (file swap), `closeTextViewerFromBackPress`, `btnCloseTextViewer` text-file branch, `reopenWithEncoding`, `displayOcrText`, `displayTranslatedText`, `hideOcrText`. `release()` still owns full `ttsManager?.release()`. `stop()` is idempotent (no-op at `IDLE`), so added calls are safe. Grep confirms 9 `stop()` sites (2 pre-existing page-nav + 7 new) + 1 `release()`.

**Residual / device gate.** Finding #2 is audible runtime behavior; verify on device via `/spec-sweep`:
- While read-aloud is speaking, swap to another text file -> speech stops (no stale narration).
- While speaking, close the viewer (back-press and close button) -> speech stops.
- While speaking OCR/translation text, hide or re-run OCR -> speech stops.
- (Best-effort) Force a TTS init failure (no TTS engine/data) -> after the error toast, a later read-aloud attempt re-initializes rather than hanging.

Probe tags (`Timber.d("S0897: ..")`) inserted at the file-swap, viewer-close, and init-failure entries; remove on transition out of `BlockNeedUserTest`.

### Manual device test - 2026-07-10 (emulator-5554, Android 13 x86_64, standard debug)

TTS precondition OK: `com.google.android.tts` present and usable (init succeeds, `currentLocale en-US`); `tts_default_synth` was null, set to the Google engine. So the engine gap did NOT block any sub-check - remaining INCONCLUSIVE verdicts are from emulator popup-menu tap injection wedging over the long session, not a missing engine. Evidence: `temp/S0897/device-test-evidence.txt`.

- **Sub-check 1 - file swap while speaking: PASS.** Reading active (`Started reading 19004 chars`); tapped Next file -> `TextViewerManager: S0897: file swap - TTS stopped`. Expected speech stops on swap: confirmed.
- **Sub-check 2a - back-press while speaking: PASS.** `Started reading 13028 chars`; hardware BACK -> `TextViewerManager: S0897: text-viewer close - TTS stopped`, focus returned to `BrowseActivity`. Expected speech stops on close: confirmed.
- **Sub-check 2b - dedicated close button: INCONCLUSIVE.** `btnCloseTextViewer` (content-desc "Закрыть") only surfaces in fullscreen and on this build acted as a fullscreen-exit toggle (toolbar returned, focus stayed in viewer, no close probe); its distinct close-path tap could not be isolated. Code-parity with 2a (same `ttsManager?.stop()` + same `text-viewer close` probe), which is proven.
- **Sub-check 3 - OCR/translation hide or re-run: INCONCLUSIVE (not reached).** Needs image + OCR(Tesseract) + read-aloud-on-OCR-text + hide/re-run; the mid-session popup-menu tap wedge made driving that multi-step flow unreliable.
- **Sub-check 4 - forced init failure then recovery: init-reset PASS, recovery INCONCLUSIVE on device.** With the engine disabled (`pm disable-user`), Read Aloud produced `TTS: Initialization failed with status=-1` then `TtsReadAloudManager: S0897: TTS init failed - engine reset for retry` - the failure branch tears down and nulls the engine (the core fix). The immediate post-failure successful re-read could not be captured because the popup-menu tap injection had wedged (10+ Read Aloud taps logged nothing though the menu was confirmed open). Strong indirect proof the machine is not stuck: the same session drove 4+ independent `startReading` cycles, each re-initialized and read.

Net: the stop-on-lifecycle-edge contract is confirmed on the two paths that could be driven (file swap, back-press close) plus the init-failure engine-reset; close-button, OCR, and post-failure re-read remain unverified on device due to the emulator input wedge. Status left `BlockNeedUserTest` (per test-run scope: no status flip).
