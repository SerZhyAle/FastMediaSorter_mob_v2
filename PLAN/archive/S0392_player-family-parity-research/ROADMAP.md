# S0392 - Catch-up Roadmap: standalone family → in-app parity

**Phase B deliverable.** Derived from [`MATRIX.md`](MATRIX.md). Ordered by value × cost; the host-seam fundamental comes first because it unblocks the whole binding-coupled (B1) class at once.

Cost legend: **S** small · **M** medium · **L** large. Each item lists the hosts it touches, the matrix rows it closes, and the blocker class it clears.

---

## Tier 0 - Fundamental (enabling)

### R0. Binding-agnostic host-seam + capability adoption — cost L  → **spec S0393** (Draft, 2026-06-10)
- **Why first:** B1 (binding-coupled helper) is the dominant blocker (MATRIX §10). One seam collapses "rewrite glue per host" into "wire once".
- **Scope:**
  - Define a host-seam interface the per-file managers consume instead of `PlayerActivity`/`ActivityPlayerUnifiedBinding`: current-file flow, root view, overlay mount points (`mediaContentArea`/`photoDualSurfaceContainer`), reload hook, dialog host, writable-path resolver.
  - Refactor the binding-coupled delegates (`PlayerCropDelegate`-style) to consume the seam; in-app `PlayerActivity` implements the seam with zero behaviour change (regression-guard with tests + target-variant builds).
  - Make `DocumentStandaloneActivity` + `TextStandaloneActivity` implement `PlayerHostCapabilities` (MATRIX §9.1) so the shared pipeline can bind to them.
- **Closes:** unblocks R3-R7. **Clears:** B1 (structurally).
- **Risk:** touches the in-app hot path - seam must be behaviour-preserving.

---

## Tier 1 - Cheap wins (no full seam needed)

### R1. Standalone family consistency + dead-weight hygiene — cost S
- **Hosts:** AU, DOC, TXT (+ PV reference).
- **Scope:** unify overflow reachability (pin `btnOverflowMenu` outside a scroll wrapper, mirroring PV's S0389 fix) on AU/DOC/TXT; delete OR wire the present-but-inert command buttons in DOC/TXT layouts (Lens/OCR/copy/edit/text-translate with no listeners); make the translation model-download prompt non-inert (or hide the action until a model exists).
- **Closes:** MATRIX §9.7, §9.10, §9.11. **Clears:** B3 + dead-weight (Rule 20).

### R2. Keyboard / D-pad / TV on AU, DOC, TXT — cost S/M
- **Hosts:** AU, DOC, TXT.
- **Scope:** the keyboard parser (`PlayerKeyboardHandler`) is already GEN; supply a per-host callback (transport for AU, page/chapter nav for DOC, scroll for TXT) + `onKeyDown`/`dispatchGenericMotionEvent` overrides. Mirror the PV/LSA pattern.
- **Closes:** MATRIX §8 row 1, §9.2. **Clears:** B1 (parser already generic - cheap).

### R3. Picture-in-Picture for PhotoVideo — cost S
- **Hosts:** PV.
- **Scope:** PV already declares `supportsPictureInPicture` in the manifest but never wires `PictureInPictureManager` (declared-but-dead, MATRIX §9.4). Wire it (manager is MIXED, already shared with LSA).
- **Closes:** MATRIX §3 PiP row (PV). **Clears:** B1-cheap.

---

## Tier 2 - Per-file action waves (depend on R0 seam)

### R4. Draw overlay in standalone — cost M  *(= S0390 phase 06, already carved)*
- **Hosts:** PV. **Depends:** R0.
- **Scope:** standalone base-bitmap provider + draw toolbar stub (both orientations) + standalone draw save helper reusing `ImageDrawOverlayManager`/`MergeDrawOverlayUseCase`.
- **Closes:** MATRIX §4 draw row.

### R5. Image action wave — cost M/L
- **Hosts:** PV. **Depends:** R0.
- **Scope:** behind the seam, protract OCR image, Google Lens image, image-translation overlay, image edit dialog (rotate/flip/filters/adjust), print image. (These are the S0389 §10 / S0390 wave-C image items.)
- **Closes:** MATRIX §4 rows OCR/Lens/translate/edit-dialog/print.

### R6. Video action wave — cost M
- **Hosts:** PV (+ AU for sleep-timer). **Depends:** R0.
- **Scope:** un-stub the playback-control dialog in PV (real in LSA - reuse), save-frame, sleep-timer, black-screen overlay (GEN - cheap).
- **Closes:** MATRIX §5 rows playback-dialog/save-frame/sleep-timer/black-screen.

### R7. Audio action wave — cost M
- **Hosts:** AU. **Depends:** R0 (+ R2 for keyboard).
- **Scope:** lyrics, YouTube Music, sleep-timer, black-screen.
- **Closes:** MATRIX §6 rows lyrics/yt-music/sleep-timer/black-screen.

### R8. Document/Text action wave — cost L
- **Hosts:** DOC, TXT. **Depends:** R0 (DOC/TXT must implement capabilities first).
- **Scope:** wire PDF OCR/Lens, EPUB OCR, Office OCR/translate, text copy/edit/search/translate/reopen-encoding/markdown/reader-settings/read-aloud, WebView selection ActionMode (translate/search). Many buttons already exist in-layout (R1 decides delete-vs-wire).
- **Closes:** MATRIX §7 partial/inert rows.

---

## Explicit non-goals (do not chase in catch-up)

- **Cast** in standalone — B5, `supportsCast=false` contract decision.
- **List/playlist navigation, copy/move side panels, UNDO, open-in-separate-window, persistent background audio** — B2 structural: they need a registered resource/playlist + op-history that a single external file does not have. Folder paging (S0389) already gives the reachable subset; the rest stays in-app-only by design.
- **Open-in-VR** — B4, vr/noLegal flavor only; orthogonal to standalone parity.

## Sequencing summary

1. **R1 + R2 + R3** can land immediately (no seam) - cheap, fix the most visible inconsistencies (overflow, keyboard, dead buttons, PiP).
2. **R0** is the fundamental gate for the per-file waves.
3. **R4-R8** follow R0, ordered by user value: draw + image (R4/R5) → video (R6) → audio (R7) → document/text (R8).

## Owner decisions (2026-06-10, resolved)

- **Sequencing:** foundation-first. Build R0 (host-seam) before the per-file waves.
- **Scope:** catch up ALL four areas - image, video, audio, document/text.
- **Legacy `StandalonePlayerActivity`:** it is the most complete host (sole carrier of PiP, playback-control dialog, text-scroll keys, WebView ActionMode, full keyboard, full ViewManager). **Harvest all of its functionality first** into the seam + specialized hosts (the inheritance split started by S0380 is unfinished), THEN mark it `@Deprecated` with a `TODO` to delete once nothing routes to it. Do NOT delete before the harvest. → folded into R0's scope as the harvest source.

---

## Wave-C cost map (2026-06-10 research) — remaining type-specific actions

Seam (`PlayerActionHost`) + crop/draw proved the pattern. Remaining actions, classified CHEAP (wiring/visibility only - engine already host-agnostic) vs NEEDS-REFACTOR (decouple a PlayerActivity/binding-typed helper + grow the seam first):

**CHEAP — buttons exist, engines decoupled:**
- Document **PDF OCR** + **PDF Google Lens** — DONE (S0393 wave-C): wired `btnOcrPdfCmd`/`btnGoogleLensPdfCmd`, OCR shown in `ScrollableTextDialog`, Lens via generic FileProvider share.
- Document **EPUB OCR** — `btnOcrEpubCmd` → `extractTextFromCurrentChapter()` (needs an OCR result sink on EpubViewerCallback first).
- Text **copy / edit / translate / search** — buttons present (gone); `TextViewerManager.setupControls()` already binds copy/edit/translate; needs visibility flip + un-stub `showTranslationSettingsDialog`/`showEncodingDialog`; search needs a host click → `textViewerManager.searchText()`. Edit gated on writability (`displayText(isWritable=false)` today).
- PhotoVideo **image edit dialog** — `ImageEditDialog` is already `Context`-based; inject 5 use-cases, construct with `actionCurrentFile.path`, `onEditComplete → reloadCurrentImageInPlace()`; one new button.
- PhotoVideo **black screen** — `BlackScreenOverlayManager` is GENERIC (WeakReference<Activity>); add a button + a fullscreen toggler.

**NEEDS-REFACTOR — sized:**
- PhotoVideo **Google Lens image** (M): extract `shareFileToGoogleLens(File)` to Context-only; drive off `actionCurrentFile`. New button.
- PhotoVideo **OCR image** (L): decouple `ImageOcrManager` from binding; needs a real `displayedBitmap` seam source (null today) + a result sink. Gate `ENABLE_TRANSLATION`.
- PhotoVideo **image translation overlay** (L, large): decouple `PlayerImageTranslationManager` from PlayerActivity+binding; needs `translationLensOverlay` views in layout, `translationManager`+`settingsRepository`, seam overlay/progress hooks. Gate `ENABLE_TRANSLATION`.
- PhotoVideo **save frame** (L): decouple `SaveVideoFrameManager`; add a `Player`/`PlayerView` seam accessor (absent); skip player-only destination-resource branch. Gate `SUPPORT_VIDEO`.
- Document+PhotoVideo **print** (L): decouple `DocumentPrintManager` from PlayerActivity (pass NetworkFileManager+scope+Context); office branch degrades; `PlayerPrintFallbackManager` reusable. New buttons. Gate `SUPPORT_DOCUMENTS` for non-image.
- Audio **sleep timer** (M): `SleepTimerManager` GENERIC; needs vinyl ImageView+badge+button in audio layout + Player provider + local option dialog.
- Audio **YouTube Music** (M): generic intent; re-express current file via `actionCurrentFile`; new button.
- Audio **lyrics** (L): decouple `LyricsManager` from binding to `root: View`; add lyrics overlay container+content+button; inject `SearchLyricsUseCase`+translation-session.

**Seam-growth decisions these force:** add a `Player`/`PlayerView` accessor (save-frame, sleep-timer-video); a real `displayedBitmap`/image-drawable accessor in PhotoVideo (OCR-image, image-translate); overlay/progress hooks (image-translate, lyrics). Each new action also needs EN/RU/UK strings + per-flavor device-test (`ENABLE_TRANSLATION`/`SUPPORT_*` gates).
