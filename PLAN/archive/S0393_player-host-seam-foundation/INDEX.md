# Tactical Plan: S0393 - player-host-seam-foundation

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Feature:** A binding-agnostic host-seam so type-specific action delegates are written once; harvest the legacy `StandalonePlayerActivity` into the seam/specialized hosts, then deprecate it.
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 55
**Status:** BlockNeedUserTest
**Phases:** 8 / 8 done (Phase 03 deferred - no consumer yet; device-test sweep pending)
**Last updated:** 2026-06-10

> **Scope:** the enabling foundation (S0392 ROADMAP R0). NOT the per-file catch-up waves (those are later tickets consuming this seam). Engines stay untouched. The in-app player must not change behaviour - every seam step is regression-guarded by a target-variant build + device-test.

---

## Phase Overview

| # | Phase | Depends on | Status | File |
|---|-------|-----------|--------|------|
| 01 | seam-interface + in-app adapter | - | ✅ Done | [PHASE_01__seam-interface-inapp-adapter.md](PHASE_01__seam-interface-inapp-adapter.md) |
| 02 | migrate crop onto seam (draw → 02b) | 01 | ✅ Done (crop; device-verified) | [PHASE_02__migrate-crop-draw.md](PHASE_02__migrate-crop-draw.md) |
| 03 | Document/Text adopt capabilities | 01 | ⏭️ Deferred (no consuming seam delegate yet; keyboard ports are Activity-level) | [PHASE_03__doc-text-capabilities.md](PHASE_03__doc-text-capabilities.md) |
| 04 | legacy harvest diff → HARVEST.md | 01 | ✅ Done | [PHASE_04__legacy-harvest-diff.md](PHASE_04__legacy-harvest-diff.md) |
| 05 | port PiP (U1) + playback-control dialog (U2) | 04 | ✅ Done (both built green) | [PHASE_05__port-pip-playback-dialog.md](PHASE_05__port-pip-playback-dialog.md) |
| 06 | port keyboard (U4/U5) + WebView ActionMode (U3) + EPUB guard (U7/U8) | 04 | ✅ Done (U6 inline-find dropped per Q1) | [PHASE_06__port-input-webview.md](PHASE_06__port-input-webview.md) |
| 07 | deprecate legacy host | 05, 06 | ✅ Done (@Deprecated + TODO; nothing routes to it) | [PHASE_07__deprecate-legacy.md](PHASE_07__deprecate-legacy.md) |
| 08 | docs + catalog + device-test | all | ✅ Done (catalog/dev-log/MATRIX done; tags inserted; standard+noLegal green; awaiting device-test) | [PHASE_08__docs-catalog-devicetest.md](PHASE_08__docs-catalog-devicetest.md) |

**Still open (follow-ups, not blocking legacy deprecation):** draw-overlay migration onto the seam (Phase 02b - seam now exposes displayedBitmap/displayRect/overlay mount, so it's unblocked); Document/Text full `PlayerHostCapabilities` (Phase 03 - deferred, no consumer yet); on-device verification sweep (Phase 08).

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked`

---

## Risk-ordered execution

1. **01** is additive (new interface + in-app adapter, zero behaviour change) - the safe brick; build + smoke before anything else.
2. **02/03/04** can proceed once 01 lands; 04 (harvest diff) is read-only research feeding 05/06.
3. **05/06** port real legacy capability - highest standalone-side risk; device-test each.
4. **07** only after 05/06 confirm nothing unique remains in legacy; then `@Deprecated` + `TODO`.

## Completion Gate

- [ ] Seam interface exists; in-app `PlayerActivity` implements it; crop/draw delegates consume it; in-app behaviour unchanged (device-test).
- [ ] Document/Text implement `PlayerHostCapabilities` (or a narrow sub-contract).
- [ ] Every unique legacy capability ported (PiP, playback-control dialog, keyboard, text-scroll keys, WebView ActionMode); nothing lost.
- [ ] Legacy `StandalonePlayerActivity` `@Deprecated` + `TODO`-delete; no external routing targets it.
- [ ] `standard` build green; device-test of in-app + standalone hosts.
- [ ] Adding a new type-specific action is a single-site change through the seam.

---

## Blockers Log

- (none)

## Audit fixes (2026-06-10, adversarial auditor)

- **F1 [S2]** `LinkDownloadWorker` + `LinkAutoDownloadResultPresenter` still launched the deprecated legacy host directly → repointed to `StandalonePlayerDispatcherActivity`. Now nothing routes to legacy (claim true). Legacy is removable once verified.
- **F2 [S2]** in-app crop dropped the `imageView` FIT_CENTER normalization (only photoView was set) → added `PlayerActionHost.prepareImageSurfacesForCrop()`; in-app overrides to normalize both surfaces. In-app crop behaviour restored.
- **F3 [S3]** keyboard help-key (U5) was inert in the new hosts → added `onShowHelp` to `StandaloneKeyboardManager`, wired to `InputHelpDialogFragment` in PhotoVideo/Audio/Document/Text.
- **F4 [S3]** PhotoVideo kept its own `PhotoVideoStandaloneKeyboardManager` (the duplication the seam exists to remove) → migrated onto the shared `StandaloneKeyboardManager`; duplicate deleted.
- **F5 [S3]** U2 Audio playback-control dialog was claimed but unwired → HARVEST.md amended to mark it deferred (no audio-lane trigger; not a legacy loss).
- Remaining auditor-noted deferrals (honest, out of scope): Document/Text full `PlayerHostCapabilities`, draw-overlay migration, present-but-inert doc/text command buttons (S0392 R1/R8), PDF/EPUB END-key (parity with legacy).

## Second audit (2026-06-10) - fixes + deferrals

Fixed: F3 defensive `ACTION_SEND_MULTIPLE` in dispatcher; F5 `onConfigurationChanged` reapply-insets on PhotoVideo/Audio/Text/Document; F6 rotation-sensor keyboard route; F4 Document overflow pinned outside its scroll (portrait+land); F7 stale MATRIX cells (PiP/playback-dialog/ActionMode/keyboard → present). Finding "crop scaleType" was already fixed (F2). `ACTION_SEND_MULTIPLE` confirmed a dead path (no manifest filter declares it) - the dispatcher handling is defensive only.

Deferred (pre-existing S0380 gaps surfaced by the audit, NOT S0393 regressions - each its own follow-up):
- **Audio playback-control dialog (speed/volume):** AudioStandaloneActivity has `videoPlayerHandle=null` and no controls surface; needs an audio `VideoPlayerHandle` + a trigger. Never existed in the specialized audio host since S0380; HARVEST U2 over-claim corrected.
- **Audio/Text command-bar scroll wrappers:** only PhotoVideo + Document have the `HorizontalScrollView`+pinned-overflow; Audio/Text clip only when paging is active on a narrow screen (same mechanical fix, deferred).
- **Document/Text present-but-inert command buttons** (OCR/Lens/copy/edit/text-search/translate): S0392 ROADMAP R1/R8 dead-weight cleanup.
- **Inert `showModelDownloadPrompt`** in Document/Text translation: first-use on a missing model no-ops; pre-existing S0380.

## Change Log

- 2026-06-10 - Tactical plan authored from S0392 ROADMAP R0 + owner decisions (foundation-first, harvest-then-deprecate legacy).

## Wave-C complete (2026-06-10) — via direct engine calls

Owner insight: the engines/use-cases already exist; wire the call + show results in a dialog rather than decoupling the binding-typed managers. Done that way - no manager rewrites, no new overlay views:

- **PhotoVideo (image):** image-edit dialog (`ImageEditDialog`), OCR (`recognition.extractTextOnly` → `ScrollableTextDialog`), image-translate (`recognizeAndTranslate` → dialog), Google Lens (`GoogleLensShare`), print (`PrintHelper.printBitmap`). All via the overflow menu, gated on the editable-image state.
- **PhotoVideo (video):** save-frame (`TextureView.getBitmap` → MediaStore Pictures), black-screen (`BlackScreenOverlayManager`), sleep-timer (dialog + delayed pause).
- **Audio:** YouTube Music (search intent), lyrics (`SearchLyricsUseCase` → dialog), sleep-timer.
- **Document:** PDF OCR (`extractTextFromCurrentPage` → dialog), PDF Google Lens (`shareCurrentPageToGoogleLens`), EPUB translator guard, real translation-model download prompt.
- **Text:** copy + translate buttons surfaced; real translation-model download prompt.
- **Shared overflow menu hygiene:** image/video/audio-only items are hidden in the hosts that don't own them (also fixes the pre-existing S0390 dead crop items in Audio/Doc/Text).

All three previously-"blocked" items are now CLOSED (none needed a copy job - the engines existed):
- **EPUB OCR** — `extractTextFromCurrentChapter()` self-contained (extracts via JS → clipboard + toast), no callback needed; wired `btnOcrEpubCmd` + surfaced it (the EPUB manager surfaces search/translate but not OCR).
- **Text search** — wired `btnSearchTextCmd` to a query-input dialog → `textViewerManager.searchText()` + highlight first match + count toast.
- **Text edit** — built a `TextEditorSaveFlow` (inject `SaveTextNoteUseCase`), passed it to `TextViewerManager`, and pass `isWritable=true` for writable local text files (content-URI opens stay read-only). `btnEditTextCmd` shown when writable.

→ Standalone now has FULL type-specific action parity; nothing is blocked.

## Third audit (2026-06-10) - all 3 actionable items closed

- **#1 ACTION_SEND_MULTIPLE fall-through** — the dispatcher resolved the first URI but forwarded the intent verbatim (action still `SEND_MULTIPLE`, which has no `data`), so specialized hosts hit their `else` branch and exited. Fix: dispatcher now normalizes the forward to `ACTION_VIEW` + the resolved `data` URI (single point; robust for any inbound action). Still a defensive path (no manifest filter declares SEND_MULTIPLE).
- **#2 Audio speed control loss** — `AudioStandaloneActivity` had `videoPlayerHandle=null` and no speed control. Fix: a dedicated playback-speed picker (`menu_playback_speed` → `Player.setPlaybackSpeed`), no full handle needed (track/subtitle sections don't apply to audio). Hidden in the other hosts (video uses its own playback-control dialog).
- **#3 Dead settings buttons** — `btnPdfTextSettingsCmd` / `btnTextSettingsCmd` were defined in the standalone doc/text layouts but never wired (only the in-app unified layout wires them). Fix: stripped from all 4 standalone layout files (Rule 20 dead-weight); in-app unaffected.

Both `standard` and `noLegal` debug compiled green (audit-confirmed).

## Fourth fix (2026-06-13) - bitmap-action over-gating on non-local images

Symptom: an image opened from a non-local share (Instagram/Telegram/browser `content://` from a third-party FileProvider) showed only delete/share/favorite/info/open-in-FMS - all the image actions were missing. Root cause: every image action in `PhotoVideoStandaloneActivity` was gated on `editableImageFile != null`, which `ResolveLocalPathFromUriUseCase` only sets for a resolvable local writable path (MediaStore `DATA` column or a primary-volume SAF document). A third-party FileProvider URI resolves to `NotLocal`, so the gate hid everything.

The in-app player gates the same actions differently (`CommandPanelLayoutPlanner.buildActiveCommands`): OCR/translate/print on `isImage` + feature flags, rotation on the sensor - none on write access. Aligned the standalone host to that split:

- **OCR / translate / print** - regated from `editable` to `binding.photoView.drawable != null` (a rendered bitmap). They read the displayed bitmap (`extractTextOnly` / `recognizeAndTranslate` / `PrintHelper.printBitmap`), never the source file, so they now appear for non-local images too. `VIDEO` naturally excluded (no drawable in `photoView`).
- **Rotation toggle** (`btnEditRotate`) - decoupled from `editableImageFile`; gated on `hasAccelerometer` only and set once in `setupFileOperationButtons`. It locks/unlocks the orientation sensor and needs no file.
- **Crop / crop-to-file / compress / edit / Google Lens** - kept on `editable`: they overwrite the source in place or share `File(path)`, so a local writable path is genuinely required (`GoogleLensShare.shareImageFile` takes a `File`, not a `Uri`).

`compileStandardDebugKotlin` green.

Deferred (feature work, not over-gating defects - the in-app player has them but the standalone never shipped them):
- **Image text-settings dialog** (`IMAGE_TEXT_SETTINGS`): the in-app entry runs through `TranslationButtonManager`, which is constructor-bound to `ActivityPlayerUnifiedBinding`; surfacing it in the trimmed host needs the dialog extracted into a binding-free helper, not a gate flip. Languages remain configurable from app Settings.
- **Draw overlay** (`DRAW_OVERLAY`): `displayedBitmap` is still `null` in the host ("draw overlay not yet wired"); a real view-overlay port.
- **Crop-to-file / compress on non-local images**: would need to materialize the content URI to a temp file first (no source path otherwise); larger than a gate change.
