# Phase 04 - Specialized activities

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 (Phase 01 skipped)
**Blocks:** Phase 05
**Steps done:** 4 / 4 (Text, PhotoVideo, Audio, Document)
**Started:** 2026-06-07
**Completed:** 2026-06-08

---

> **Revised approach (2026-06-07, owner-chosen: incremental decouple, Text first).** The viewer managers are hard-bound to `ActivityPlayerUnifiedBinding` (see INDEX Phase 04 Architectural Finding). A trimmed per-type layout needs them decoupled first. The decouple is mechanical for the view-lookup layer (`PlayerBindingSafeViews` is entirely `findViewById`-based) but harder for OCR: `TextOcrDisplayManager` uses **direct** binding fields for image/video views (`binding.photoView`, `binding.imageView`, `binding.playerView`, `binding.epubWebView`, `binding.officeDocumentViewerContainer`, `binding.audioCoverArtView`...) because OCR renders text **over** media - a feature standalone text never uses.
>
> Decouple sub-steps (Text lane):
> - [x] **D1 - `PlayerBindingSafeViews` accepts any `root: View`** (secondary ctor keeps `ActivityPlayerUnifiedBinding`). Backward-compatible (23 internal-player callers unaffected), behavior-preserving. Build SUCCESSFUL v2.60.6071.420.
> - [x] **D2 - `TextOcrDisplayManager` root-based + null-safe**: dropped its `binding` param, all views via `PlayerBindingSafeViews` (media/overlay views via nullable `*OrNull` accessors added to `PlayerBindingSafeViews`); updated its construction in `TextViewerManager`. Build SUCCESSFUL v2.60.6071.438, internal player intact.
> - [x] **D3 - `TextViewerManager` + collaborators take `root: View`**: decoupled `TextViewerGestureDetectors`, `TextViewerLoader`, `TextEditorActionPanelCallbacks` (binding was unused), `TextEditorModeController`, and `TextViewerManager` itself from `ActivityPlayerUnifiedBinding`; all views via `PlayerBindingSafeViews(root)`. Build SUCCESSFUL v2.60.6071.636 (collaborators) + v2.60.6071.641 (full).
> - [x] **D4 - both call sites updated**: `StandaloneViewManager.createTextViewerManager` and internal `PlayerViewerFactory.createTextViewerManager` now pass `<binding>.root`. Internal player intact.
> - [x] **D5 - DONE (build SUCCESSFUL v2.60.6071.944):** `StandaloneFileOperationsHandler` decoupled to `root: View`; `activity_standalone_text.xml` (+ `-land`) created (command panel + the 3 text includes + progressBar, no media views); `TextStandaloneActivity` created (intent parse, file-ops, favourite, `TextViewerManager(root)`); manifest registers it with a `text/plain` VIEW filter. **Known v1 gap:** `btnSearchTextCmd` is shown by the loader but its search-panel toggle is not wired (needs `SearchControlsManager` decouple) - follow-up. Prep was (`setVisibleIfPresent` lets the layout omit pdf/epub controls; build v2.60.6071.922). **Recipe (verified against `activity_player_unified.xml`):** trimmed layout = a `topCommandPanel` (btnBack + file-op buttons btnDeleteCmd/btnShareCmd/btnFavorite/btnInfoCmd/btnRenameCmd/btnOverflowMenu + text buttons btnCopyTextCmd/btnEditTextCmd/btnTranslateTextCmd/btnSearchTextCmd/btnTextSettingsCmd) + `<include @layout/player_text_viewer_container_content>` + `<include @layout/player_search_panel_content>` + `<include @layout/player_translation_overlay_content>` + `progressBar`. Omit all media/pdf/epub/audio/controlsOverlay/touch-zone views (the inflation win). The 3 includes guarantee the ~40 required text view ids. `TextStandaloneActivity` clones the text-relevant wiring of `StandalonePlayerActivity` (intent parse, `StandaloneFileOperationsHandler`, favorite via reused `StandalonePlayerViewModel`, `PlayerKeyboardHandler` - callback-based, no binding, reusable as-is, `TextViewerManager(root = binding.root)`), using the generated `ActivityStandaloneTextBinding`. **One more small decouple needed first:** `StandaloneFileOperationsHandler` is bound to `ActivityPlayerUnifiedBinding` but only touches `binding.btnDeleteCmd` (line 138) + `binding.btnRenameCmd` (301, 307) - convert to `root: View` + `PlayerBindingSafeViews` (btnRenameCmd exists in safeViews; add `btnDeleteCmdOrNull` or use `setVisibleIfPresent`). Then `-land` variant + manifest activity entry (routing wired in Phase 05).
> - [x] **D6 - compile-verified** (build SUCCESSFUL v2.60.6071.944). **On-device verification PENDING (owner):** open a `.txt` from an external app → should land in `TextStandaloneActivity`, render text, with copy/edit/translate/delete/share/info/rename/favourite working, and no ExoPlayer/Glide loaded. Internal-player text path also to be smoke-checked (shared `TextViewerManager` was decoupled).
>
> Other lanes (PhotoVideo, Audio, Document) repeat the pattern in later passes. The four-activity steps below are the end state; they execute after the per-lane decouple lands.

---

## Objective

Add four specialized standalone activities (photo+video, audio, document, text), each reusing the existing viewer managers (decoupled per-type from the unified binding), each with a trimmed layout that inflates only its own viewers. No viewer logic is rewritten - only its view-lookup seam is widened.

---

## Prerequisites

- [ ] Phase 01 ✅ Done, Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | New | ≤ 240 |
| `app_v2/src/main/res/layout/activity_standalone_photo_video.xml` | New | - |
| `app_v2/src/main/res/layout/activity_standalone_audio.xml` | New | - |
| `app_v2/src/main/res/layout/activity_standalone_document.xml` | New | - |
| `app_v2/src/main/res/layout/activity_standalone_text.xml` | New | - |
| `app_v2/src/main/res/layout-land/activity_standalone_*.xml` | New | - |

> **Landscape parity (MANDATORY):** every new `res/layout/activity_standalone_*.xml` gets a matching `res/layout-land/` counterpart created in the same step. System-bar safety: apply `View.applySystemBarInsetPadding()` to controls (Rule 18).

---

## Steps

### Step 04.1 - Photo+Video activity + layouts

**Files:** `.../standalone/PhotoVideoStandaloneActivity.kt`, `res/layout(-land)/activity_standalone_photo_video.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `PhotoVideoStandaloneActivity` handling images, GIF and video. Reuse the existing standalone collaborators by composition - the activity implements `PlayerHostCapabilities` directly (same as `TextStandaloneActivity` and the internal `PlayerActivity`, per ADR-1; there is no `StandalonePlayerHostController` - Phase 01 was skipped). Compose `StandalonePlayerViewModel`, `StandaloneFileOperationsHandler(root)`, `StandaloneViewManager`, `StandaloneVideoControlsManager`, `StandaloneVideoTouchDelegate`, `StandaloneFullscreenManager`, `PlayerKeyboardHandler`. First decouple the photo/video view-lookup of `StandaloneViewManager` (and any collaborator it drives) from `ActivityPlayerUnifiedBinding` to a `root: View` + `PlayerBindingSafeViews(root)` seam, mirroring the Text-lane D1-D5 decouple, keeping every internal `PlayerActivity` caller behavior-preserving. Trimmed layout contains only the Glide/ExoPlayer surfaces and the shared controls panel; inflate conditional surfaces via `<ViewStub>` (Rule 19). Create both portrait and landscape layouts. Preserve focus order, D-pad, keyboard, mouse, TalkBack (Rule 17) and apply `View.applySystemBarInsetPadding()` to controls (Rule 18).

**Verification:**

- `Glob` - activity + both layout files exist.
- `Grep` - `class PhotoVideoStandaloneActivity` once; implements `PlayerHostCapabilities`.
- `Grep` - no reference to `StandalonePlayerHostController` (`expected: 0 | actual: <record>`).
- Build: `/build` standardDebug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 4/4 PASS. Glob: `PhotoVideoStandaloneActivity.kt` + `activity_standalone_photo_video.xml` (+`-land`, id-set parity verified identical) exist. Grep: `class PhotoVideoStandaloneActivity` (`expected: 1 | actual: 1`); `PlayerHostCapabilities` (`expected: >=1 | actual: 3`); `StandalonePlayerHostController` (`expected: 0 | actual: 0`). Build: standardDebug `BUILD SUCCESSFUL in 44s`. Implementation: dual-mode decouple of `StandaloneViewManager` (constructor `root: View` + nullable `binding`, `safeViews = PlayerBindingSafeViews(root)`; image/gif/video/audio via `safeViews`, doc/epub/office paths `requireNotNull(binding)`), monolithic `StandalonePlayerActivity` call-site passes `root + binding` (behavior-preserving), `PlayerBindingSafeViews` gained typed `playerView`/`photoView`/`photoDualSurfaceContainerOrNull`. New activity composes the video-control subset (`StandaloneVideoControlsManager`/`StandaloneVideoTouchDelegate`/`StandaloneFullscreenManager`/`VideoTrackSelectionManager`/`StandalonePlayerSettingsManager`) via helpers `PhotoVideoStandaloneKeyboardManager` + `PhotoVideoStandaloneVideoHandle`; rejects audio/doc/binary with the unsupported toast. Fixed a Rule 15 leak the first pass introduced (`BuildConfig.SUPPORT_VIDEO` guard removed + unused import). Files: ui/player/standalone/PhotoVideoStandaloneActivity.kt (467), ui/player/helpers/PhotoVideoStandaloneKeyboardManager.kt (88), ui/player/helpers/PhotoVideoStandaloneVideoHandle.kt (46), res/layout(-land)/activity_standalone_photo_video.xml, ui/player/helpers/PlayerBindingSafeViews.kt (+3 accessors), ui/player/helpers/StandaloneViewManager.kt (dual-mode), ui/player/StandalonePlayerActivity.kt (call-site). Note: activity LOC 467 > soft budget 320 but < 1500 hard cap (PlayerHostCapabilities block + setupVideoControls); video playback-control dialog wired as no-op (parity follow-up). On-device verification deferred to `BlockNeedUserTest` round.

---

### Step 04.2 - Audio activity + layouts

**Files:** `.../standalone/AudioStandaloneActivity.kt`, `res/layout(-land)/activity_standalone_audio.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `AudioStandaloneActivity` handling music files, integrating with the existing background playback service. The activity implements `PlayerHostCapabilities` directly and reuses `StandalonePlayerViewModel` + `StandaloneFileOperationsHandler(root)` + `PlayerKeyboardHandler` by composition (no `StandalonePlayerHostController`). Layout has audio controls + cover-art only - no image/video/doc surfaces; views looked up via `PlayerBindingSafeViews(root)`. Create portrait + landscape layouts; apply `View.applySystemBarInsetPadding()` (Rule 18).

**Verification:**

- `Glob` - activity + both layouts exist.
- `Grep` - `class AudioStandaloneActivity` once.
- Build: `/build` standardDebug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Glob: `AudioStandaloneActivity.kt` + `activity_standalone_audio.xml` (+`-land`, id-set parity verified identical) exist. Grep: `class AudioStandaloneActivity` (`expected: 1 | actual: 1`). Build: standardDebug `BUILD SUCCESSFUL in 3m 6s`. Implementation: consume-only on the already-decoupled dual-mode `StandaloneViewManager` (`binding = null`), drives `show(file, AUDIO)`; `AudioServiceController` (background playback) feeds the root-based playerView. `updateAudioMediaItem` wired live (SAF rename of a playing track swaps MediaItem without interrupting playback). Rejects non-audio with the unsupported toast. No video-control subset. Static gates: BuildConfig flavor guard 0, Log.d 0, Sxxxx-in-log 0. Files: ui/player/standalone/AudioStandaloneActivity.kt (343), res/layout(-land)/activity_standalone_audio.xml. Note: activity LOC 343 > soft budget 280 but < 1500 (PlayerHostCapabilities contract block). On-device verification deferred to `BlockNeedUserTest` round.

---

### Step 04.3 - Document activity + layouts

**Files:** `.../standalone/DocumentStandaloneActivity.kt`, `res/layout(-land)/activity_standalone_document.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `DocumentStandaloneActivity` handling PDF, EPUB and office documents. The activity implements `PlayerHostCapabilities` directly and reuses `StandalonePlayerViewModel` + `StandaloneFileOperationsHandler(root)` + `PlayerKeyboardHandler` by composition. First decouple `PdfViewerManager` / `EpubViewerManager` / `OfficeDocumentViewerManager` (each ~1000 LOC, shared with the internal `PlayerActivity`) from `ActivityPlayerUnifiedBinding` to a `root: View` + `PlayerBindingSafeViews(root)` seam, mirroring the Text-lane D1-D5 decouple and keeping every internal caller behavior-preserving. Trimmed layout hosts only the document viewers + WebView and the shared controls panel. Create portrait + landscape layouts; apply `View.applySystemBarInsetPadding()` (Rule 18).

**Verification:**

- `Glob` - activity + both layouts exist.
- `Grep` - `class DocumentStandaloneActivity` once; references at least one document viewer manager.
- Build: `/build` standardDebug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Glob: `DocumentStandaloneActivity.kt` + `activity_standalone_document.xml` (+`-land`, id-set parity verified identical) exist. Grep: `class DocumentStandaloneActivity` (`expected: 1 | actual: 1`); document viewer manager refs (`expected: >=1 | actual: 12`). Build: standardDebug `BUILD SUCCESSFUL in 1m 23s`; noLegalDebug verification run separately (Office host noLegal impl changed). Implementation: full root-decouple of `PdfViewerManager` (107→0 binding refs), `EpubViewerManager` (68→0), `BaseDocumentViewerManager`, 7 collaborators (PdfTranslationCoordinator/PdfTextSelectionManager/PdfLinkAndSearchManager/PdfThumbnailSheet/EpubWebViewLifecycle/EpubTranslationOverlayHelper/EpubSearchAndTocPresenter), and the flavor Office host (`createViewerHost(root)` across standard/lite/photos/legacy/vrOnly/noLegal factories + noLegal `OfficeDocumentViewerManager`), all to `root: View` + `PlayerBindingSafeViews(root)`. `StandaloneViewManager` `binding` param fully removed (0 refs); monolithic `StandalonePlayerActivity` call-site dropped `binding =`. Internal `PlayerViewerFactory` pdf/epub/office now pass `root = activity.activityBinding.root` (behavior-preserving — verified the only compiler errors in the first build were an UNRELATED missing import in the owner's concurrent `WelcomeActivity.kt` WIP, zero from the 18 decouple files). New `PlayerBindingSafeViews` root accessors added for pdf/epub nav/zoom/font + media/overlay views. Static gates: BuildConfig 0, Log.d 0, Sxxxx-log 0, parity OK. Files: ui/player/standalone/DocumentStandaloneActivity.kt (479), res/layout(-land)/activity_standalone_document.xml + 18 decouple files. Notes: activity LOC 479 > soft budget 320 but < 1500; search-panel inline toggle (btnSearchPdf/Epub) is a v1 gap (SearchControlsManager not decoupled - same as Text-lane btnSearchTextCmd follow-up). On-device verification of internal-player + standalone document paths deferred to `BlockNeedUserTest` round.

---

### Step 04.4 - Text activity + layouts (no media libs)

**Files:** `.../standalone/TextStandaloneActivity.kt`, `res/layout(-land)/activity_standalone_text.xml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Create `TextStandaloneActivity` handling plain text via `TextViewerManager` only. Layout is a minimal text container. This activity must NOT reference ExoPlayer, Glide, PDF or WebView - this is the cold-start win path (strategic criterion 4). Create portrait + landscape layouts.

**Verification:**

- `Glob` - activity + both layouts exist.
- `Grep` - `class TextStandaloneActivity` once.
- `Grep` - no `exoplayer`, `glide`, `pdf`, `webkit` import in `TextStandaloneActivity.kt` (`expected: 0 | actual: <record>`).
- Build: `/build` standardDebug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 4/4 PASS. Glob: `TextStandaloneActivity.kt` + `activity_standalone_text.xml` (+`-land`) exist. Grep: `class TextStandaloneActivity` (`expected: 1 | actual: 1`); heavy-SDK imports exoplayer/glide/pdf/webkit (`expected: 0 | actual: 0`). Build: standardDebug `BUILD SUCCESSFUL in 1m 3s` (Text lane is part of the committed green baseline; D5/D6 landed v2.60.6071.944). Files: ui/player/standalone/TextStandaloneActivity.kt, res/layout(-land)/activity_standalone_text.xml. Implemented out-of-order (Text-first) per the Revised Approach; the `Depends on: Step 04.3` line is historically moot. Known v1 gap: `btnSearchTextCmd` toggle unwired (search-panel decouple follow-up). On-device verification deferred to the ticket's `BlockNeedUserTest` round.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build` (standardDebug).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Every new `res/layout/*.xml` has a `res/layout-land/*.xml` counterpart.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Four specialized activities exist and compile but are not yet reachable - no manifest entries/aliases point to them yet. Phase 05 wires routing and the default-player toggle.

---

## Rollback Plan

Revert phase commit(s). All files are additive and unreferenced by the manifest until Phase 05 - zero runtime effect on the existing single-activity player.

---

## Revision History

- **2026-06-08** - by `/spec-update` (`--tactical --phase 04`, `--force-locked`, focus: consistency, verifiability) - override reason: strategic status is `In Progress`; the cursor step 04.1 was non-executable because its prompt and Verification referenced `StandalonePlayerHostController`, a Phase 01 artifact that was never created (Phase 01 Skipped). Repaired the dangling reference to the real contract `PlayerHostCapabilities` (composition per ADR-1), and aligned steps 04.1-04.3 with the already-landed Text-lane decouple recipe (`root: View` + `PlayerBindingSafeViews(root)`, behavior-preserving for internal `PlayerActivity` callers; Rule 18 inset note). Step status not touched (owned by `/spec-dev`). Applied: 4. Proposed (DISCUSS): 0.
