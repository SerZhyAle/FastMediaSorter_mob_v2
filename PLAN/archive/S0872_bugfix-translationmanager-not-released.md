# Спецификация (compact bugfix): S0872 - TranslationManager (ML Kit) не освобождается в standalone doc/text хостах

**Ticket:** S0872
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1, both findings (2026-07-02, dedicated skeptic). FAMILY DRIFT confirmed: the sole translationManager.release() call site in the repo is PlayerLifecycleManager.kt:264 inside releaseResources() - live-reached from PlayerActivity.onDestroy (unified host correct); both standalone hosts never release. (1) DocumentStandaloneActivity.onDestroy = releaseActiveViewer()+super (:786-789); releaseActiveViewer (:792-803) dispatches only to pdf/epub/office managers; PdfViewerManager.close() (:684-711) and EpubViewerManager.release() (:636-640) never touch translationManager (PDF's translationEnabled=false :708 is a local flag). translationManager is by lazy (:170-193) force-initialized via pdf/epub manager ctor args (:295/:320). TranslationManager.release() (:410-413) closes recognitionBackend (offline OCR engines) + translationBackend.translator?.close() (TranslationBackend.kt:289-292, src/translationMlKit - real ML Kit native model on standard/noLegal/legacy/vr; no-op on lite/photos). (2) TextStandaloneActivity.onDestroy = textViewerManager.release()+super (:548-551); TextViewerManager.release() (:691-699) closes pager/TTS/undo/autosave only; lazy is realized on EVERY instance (setupViews -> setupControls :296) even without translate use; TextTranslationOverlayManager has NO release/close method at all. Trigger: ACTION_VIEW a pdf/epub/txt -> tap translate/OCR -> close activity -> native model + OCR engines leak per session. Fix shape: release translationManager in both standalone teardowns (mirror PlayerLifecycleManager:264); consider not force-initializing the lazy in Text host.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt:786** - onDestroy never releases TranslationManager - ML Kit Translator native model and offline OCR engines stay open after PDF/EPUB translation/OCR use
  - Evidence: onDestroy() is only `releaseActiveViewer(); super.onDestroy()` (lines 786-789). The activity owns `private val translationManager: TranslationManager by lazy { TranslationManager(...) }` (lines 170-193), forced whenever pdfViewerManager/epubViewerManager is built (ctor args, lines 295, 320), and translation/OCR are first-class wired features here (btnTranslatePdfCmd:604, btnOcrPdfCmd:608, btnTranslateEpubCmd:629, btnOcrEpubCmd:627). TranslationManager.release() exists and frees heavy natives: `recognitionBackend.release(); translationBackend.release()` (TranslationManager.kt:410-413) -> `translator?.close()` on the ML Kit Translator holding a loaded translation model (TranslationBackend.kt:289-292) and `offlineOcrEngineProvider.release()` (RecognitionBackend.kt:243-245). Neither PdfViewerManager.close() (lines 684-711) nor EpubViewerManager.release() (lines 636-640) calls it, and the only call site in the whole player package is the unified host: `activity.translationManager.release()` (PlayerLifecycleManager.kt:262-267). RUNTIME PATH: user opens a PDF from an external app, taps translate/OCR (Translator created, model loaded into native memory), closes the activity - onDestroy releases only the PDF viewer, translator.close() never runs, and the loaded model plus any initialized offline OCR engine remain resident until non-deterministic finalization, repeated per session. Violates the Layer-3 mirrored-host rule (same release contract in every host).
  - Fix hint: Track initialization (convert to `private val translationManagerDelegate = lazy { ... }` - the pattern already used for officeViewerHostDelegate at line 327) and call `if (translationManagerDelegate.isInitialized()) translationManager.release()` in onDestroy before super.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt:548** - onDestroy never releases TranslationManager - text-translation ML Kit Translator left unclosed on teardown
  - Evidence: onDestroy() is only `textViewerManager.release(); super.onDestroy()` (lines 548-551). The activity owns `private val translationManager: TranslationManager by lazy { ... }` (lines 205-228), forced by textViewerManager construction (ctor arg, line 248) which setupViews always triggers via `textViewerManager.setupControls()` (line 296). Translation is reachable: setupTextActionButtons shows btnTranslateTextCmd (line 307) and TextViewerManager wires it to `translationOverlayManager.toggleTranslation { ... }` (TextViewerManager.kt:325-327). TextViewerManager.release() frees only pager/TTS/undo/autosave (TextViewerManager.kt:691-699) - it does not touch translationManager, and no other code in this host calls TranslationManager.release() (sole call site in the player package is PlayerLifecycleManager.kt:264, the unified host). TranslationManager.release() -> `translator?.close()` (TranslationBackend.kt:289-292) frees the loaded ML Kit translation model. RUNTIME PATH: user opens a .txt from another app, taps the translate button (Translator created, model loaded natively), finishes the activity - the translator is never closed, leaving the model resident in native memory until GC finalization, accumulating across repeated standalone text sessions.
  - Fix hint: Mirror the unified host: lazy delegate + isInitialized() guard, then translationManager.release() in onDestroy when built.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

TranslationManager (ML Kit) не освобождается в standalone doc/text хостах. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`TranslationManager` (ML Kit) в обоих standalone-хостах (`DocumentStandaloneActivity`, `TextStandaloneActivity`) создавался через обычный `by lazy { ... }` без отдельной ссылки на делегат. `onDestroy()` каждого хоста освобождал только свой основной viewer-manager (`releaseActiveViewer()` -> pdf/epub/office managers; `textViewerManager.release()`), ни один из которых не трогает `translationManager` - единственный вызов `TranslationManager.release()` во всём player-пакете находился в объединённом хосте (`PlayerLifecycleManager.kt:264`). Поскольку `by lazy` не даёт способа проверить "был ли делегат реализован" без отдельного объекта делегата, у standalone-хостов не было возможности условно освободить ресурс, даже если бы вызов был добавлен напрямую. При использовании перевода/OCR в PDF/EPUB/тексте загруженная нативная ML Kit модель (`translationBackend.translator?.close()`) и офлайн-OCR движки (`recognitionBackend.release()`) оставались резидентными в памяти после закрытия активности до недетерминированной финализации GC.

---

## 3. Исправление

В обоих хостах `translationManager: TranslationManager by lazy { ... }` заменён на явный `translationManagerDelegate = lazy { ... }` + `translationManager: TranslationManager by translationManagerDelegate` (паттерн уже применялся для `officeViewerHostDelegate` в `DocumentStandaloneActivity`). В `onDestroy()` перед `super.onDestroy()` добавлена строка `if (translationManagerDelegate.isInitialized()) translationManager.release()` - освобождает ML Kit `Translator` и офлайн-OCR движки только если перевод/OCR реально использовался в сессии, зеркалируя контракт объединённого хоста (`PlayerLifecycleManager.kt:264`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (release-контракт ML Kit ресурса), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: оба хоста используют явный `Lazy`-делегат с `isInitialized()`-проверкой в `onDestroy()` перед освобождением, симметрично уже существующему `officeViewerHostDelegate` паттерну.
- Ручная device-проверка (BlockNeedUserTest, опционально): открыть PDF/EPUB/txt из внешнего приложения, использовать перевод или OCR, закрыть активность - ожидание: нет утечки нативной ML Kit модели между повторными сессиями (нет заметного роста памяти/логов повторной инициализации).

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `DocumentStandaloneActivity` uses explicit `translationManagerDelegate = lazy { ... }` + `translationManager by translationManagerDelegate` (:171,195) - PASS. `onDestroy()` calls `if (translationManagerDelegate.isInitialized()) translationManager.release()` before `super.onDestroy()` (:791) - PASS. `TextStandaloneActivity` same delegate pattern (:206,230) - PASS. `onDestroy()` same guarded release (:553) - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate - PASS. Dev log entries present for both files (S0872 @ 16:38-16:39) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Open a PDF/EPUB/txt from an external app, use translate or OCR, close the activity - expect no native ML Kit model leak across repeated sessions (no noticeable memory growth / re-init logs).

