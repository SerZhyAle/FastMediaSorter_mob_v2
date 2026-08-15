# Спецификация (compact bugfix): S0871 - TTS - engine не освобождается на onDestroy + гонка состояния (TtsReadAloudManager)

**Ticket:** S0871
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: MIXED (2026-07-02, dedicated skeptic). Finding 1 CONFIRMED P1: PlayerLifecycleManager.releaseResources() (:181-275) releases textViewerManager (:198), epubViewerManager (:247), pdfViewerManager (:253) but never references lyricsManager (grep: 4 usages repo-wide, none in destroy chain); the ONLY teardown is LyricsManager.hideLyricsViewer() (:211-214) via UI paths (PlayerControlsSetupManager :400, back-press :411-413) - PlayerActivity.onDestroy -> lifecycleManager.onDestroy -> releaseResources never reaches TTS. TtsReadAloudManager binds TextToSpeech(context=activity) (TtsReadAloudManager.kt:81, constructed PlayerManagerInitializer :518-519). PlayerActivity configChanges lacks uiMode/locale/density/fontScale (manifest :212) -> those DO recreate. Trigger: Read Aloud active -> system dark-mode/font-scale change (or Don't keep activities) -> leaked cross-process TTS binding + in-flight utterance queue against destroyed Activity. Finding 2 DOWNGRADED to P3: race is real (state/currentText/isInitialized plain vars :29-31, zero sync in file; UtteranceProgressListener writes from engine binder thread :43-57/:181-183, main-thread reads :106-126) BUT all 4 onStateChanged consumers are log-only or empty (EpubTtsDelegate :71-73, PdfTtsDelegate :82-84, LyricsManager :225-227 Timber.d; TextViewerManager :754-755 empty lambda) - worst effect is a stale toggle() misfire (re-tap fixes). Re-escalates to P1 if TtsState ever wires into UI. Fix shape: add lyricsManager release to releaseResources(); make state @Volatile + main-post the listener writes while touching the file.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt:181** - LyricsManager's TtsReadAloudManager (bound TextToSpeech engine) is never released on PlayerActivity.onDestroy - engine survives host teardown
  - Evidence: releaseResources() (PlayerLifecycleManager.kt:181-275, called from onDestroy():172-175 via PlayerActivity.onDestroy():1028 'lifecycleManager.onDestroy()') explicitly releases every other TTS host - 'if (activity._textViewerManager != null) activity.textViewerManager.release()' (:198), 'if (activity._epubViewerManager != null) activity.epubViewerManager.release()' (:247), 'if (activity._pdfViewerManager != null) activity.pdfViewerManager.close()' (:253) - but contains zero references to lyricsManager. LyricsManager releases its TTS only in hideLyricsViewer() (LyricsManager.kt:211-214 'ttsManager?.release(); ttsManager = null'), reachable solely from the back-press branch (PlayerLifecycleManager.kt:411-413 'activity.safeViews.lyricsViewerContainer.isVisible -> { activity.hideLyricsViewer(); return }') and the lyrics close control (PlayerControlsSetupManager.kt:400). The engine is created from the lyrics selection ActionMode: 'onReadAloud = { _ -> ensureTtsManager().startReading(safeViews.tvLyricsContent.text.toString()) }' (LyricsManager.kt:198-200, creation at :224-228), and LyricsManager is constructed with 'context = activity' (PlayerManagerInitializer.kt:518-519), so TtsReadAloudManager binds the TTS engine service with the PlayerActivity context ('tts = TextToSpeech(context, this)', TtsReadAloudManager.kt:81). TtsReadAloudManager.release() KDoc itself mandates the edge: 'Release TTS resources. Call from Activity onDestroy.' (TtsReadAloudManager.kt:116-117). Runtime path: open audio lyrics -> select text -> Read Aloud (TTS speaking, whole lyrics queued in 3500-char chunks) -> PlayerActivity destroyed without the back handler: manifest configChanges for PlayerActivity is 'orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden' (AndroidManifest.xml:212) so a uiMode (dark-theme), locale, density or fontScale change recreates the activity; likewise 'Don't keep activities' / system reclaim of the backgrounded activity. onDestroy runs releaseResources(), TextToSpeech.stop()/shutdown() are never called: queued utterances keep sounding after the host is gone and the bound engine ServiceConnection registered against the destroyed Activity context is leaked (framework force-unbinds it at context teardown with a ServiceConnectionLeaked error). Unreleased heavy resource surviving host teardown = P1 minimum per this unit's contract.
  - Fix hint: In PlayerLifecycleManager.releaseResources(), add the lyrics TTS teardown next to the other viewer releases (e.g. give LyricsManager a release() that does ttsManager?.release(); ttsManager = null and call it guarded like the other lateinit managers).
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt:181** - Unsynchronized cross-thread access to state/currentText/isInitialized - UtteranceProgressListener callbacks write state off the main thread
  - Evidence: 'private var state = TtsState.IDLE' (:29), 'private var currentText: String = ""' (:30) and 'private var isInitialized = false' (:31) are plain vars with no @Volatile/Mutex/synchronized. The UtteranceProgressListener installed at :43-57 calls 'updateState(TtsState.READING)' (onStart, :45), 'updateState(TtsState.IDLE)' (onDone, :49) and 'updateState(TtsState.ERROR)' (onError, :55); updateState writes 'state = newState' and invokes the owner callback (:181-184). Per the platform API contract, UtteranceProgressListener callbacks may be delivered on arbitrary (binder) threads, not the main thread, while toggle() (:106-113 'when (state) { TtsState.READING -> stop() .. }'), isReading() (:126) and startReading()/stop() (:76-101) read and write the same fields from the main thread. This is a data race on shared mutable state (taxonomy P1 row). Concrete path: TTS finishes reading -> onDone on a binder thread writes state=IDLE with no happens-before edge to the UI thread -> user taps the read-aloud toggle -> main thread may still observe stale READING -> toggle() calls stop() instead of startReading(), so the button does nothing (or, with a stale IDLE mid-read, restarts speech via QUEUE_FLUSH). Blast radius is bounded today: all four owner callbacks are log-only or empty (TextViewerManager.kt:754-755, EpubTtsDelegate.kt:71-73, PdfTtsDelegate.kt:82-84, LyricsManager.kt:225-227), so no UI is mutated off-main - the damage is a wrong toggle outcome, but any future owner that wires TtsState into UI inherits an off-main UI mutation.
  - Fix hint: Post updateState() to the main thread (e.g. Handler(Looper.getMainLooper()) or context.mainExecutor) so state is confined to one thread, which also makes the onStateChanged callback main-safe for UI consumers.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

TTS - engine не освобождается на onDestroy + гонка состояния (TtsReadAloudManager). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

- Finding 1 (P1, leak): `PlayerLifecycleManager.releaseResources()` (onDestroy chain) освобождает text/epub/pdf viewers, но не `LyricsManager`. Его `TtsReadAloudManager` привязывает `TextToSpeech(context = Activity)` и освобождается только в `LyricsManager.hideLyricsViewer()` - путь back-press / close-control, который `onDestroy` не проходит. `PlayerActivity` `configChanges` не покрывает `uiMode/locale/density/fontScale`, поэтому смена dark-theme / font-scale (или "Don't keep activities") пересоздаёт Activity -> leaked TTS `ServiceConnection` + очередь utterance против уничтожённой Activity (`ServiceConnectionLeaked`).
- Finding 2 (P3, data race - чиним попутно): `state` / `isInitialized` в `TtsReadAloudManager` - plain vars без синхронизации. `UtteranceProgressListener` (binder-поток) пишет `state` через `updateState`; main-поток читает его в `toggle()` / `isReading()`. Сегодня blast radius ограничен (все 4 owner-callback'а log-only/empty), но stale `toggle()` misfire реален, а любой будущий UI-consumer унаследовал бы off-main мутацию.

---

## 3. Исправление

Three files under `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/`.

Fix 1 (P1 leak):
1. `LyricsManager`: extract the TTS teardown into `private fun releaseTts() { ttsManager?.release(); ttsManager = null }`; `hideLyricsViewer()` now calls it. Add public `fun release() { releaseTts() }` - touches no Views, idempotent, safe from `onDestroy`.
2. `PlayerLifecycleManager.releaseResources()`: add a guarded `activity.lyricsManager.release()` next to the other viewer releases, wrapped in `try { .. } catch (e: UninitializedPropertyAccessException) {}` (mirrors the adjacent `translationManager` release; `lyricsManager` is a cross-class `lateinit`, so `isInitialized` is not reachable - try/catch is the established pattern).

Fix 2 (P3 race):
3. `TtsReadAloudManager`: mark `state` and `isInitialized` `@Volatile`; route `updateState()` through `Handler(Looper.getMainLooper()).post { .. }` so the `state` write and the owner `onStateChanged` callback are confined to the main thread. All `state` writes now flow through `updateState`, so the field is main-confined and the owner callback is main-safe for any future UI consumer.
   - Verification: no `state = ..` outside the posted block; binder-thread utterance callbacks only enqueue.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` (standard Kotlin compile, all 3 files) - PASS.
- Static gates `.\a.ps1 fg` (neuroslop, pm-flags, listener, flavor, ticket-log) - PASS.
- Optional on-device (deferred, not a merge gate): open audio lyrics, select text, Read Aloud (TTS speaking); toggle the system dark theme (or enable "Don't keep activities") to recreate `PlayerActivity`; confirm no `ServiceConnectionLeaked` in logcat and that speech stops at teardown.

---

## Last Audit

**Date:** 2026-07-02
**Verdict:** Verified
**Method:** static - `compileStandardDebugKotlin` + scoped gates + resource/concurrency inspection (CODE_AUDIT_PROTOCOL player-release + shared-state triggers). On-device leak/recreate regression optional, not a merge gate.

- Fix 1 present: `PlayerLifecycleManager.releaseResources()` now releases `lyricsManager` (guarded like `translationManager`); `LyricsManager.release()` -> `releaseTts()` frees the bound `TextToSpeech`, so the engine no longer survives `onDestroy`.
- Fix 2 present: `TtsReadAloudManager.state`/`isInitialized` are `@Volatile`; `updateState()` posts the state write + owner callback to the main thread. Verified no `state =` assignment exists outside the posted block, so all writes are main-confined and reads are consistent.
- Ordering check: INITIALIZING/READING/IDLE transitions all land on the main queue in submission order (speak -> onStart -> onDone), so the terminal state is correct; the queued-before-init path (`state == INITIALIZING` in `onInit`) still holds because the INITIALIZING post is enqueued long before the async `onInit`.
- Idempotency: `LyricsManager.release()` and `TtsReadAloudManager.release()` are null-safe and repeatable.

