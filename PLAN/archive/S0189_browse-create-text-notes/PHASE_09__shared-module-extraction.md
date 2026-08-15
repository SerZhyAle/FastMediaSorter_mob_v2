# Phase 09 — Shared-Module Extraction for S0191 Consumption

**Phase ID:** 09
**Spec:** S0189 (browse-create-text-notes)
**Authored:** 2026-05-17
**Reason:** S0191 §6.1 п.5 (closed 2026-05-17) made S0189 the owner of all 7 shared modules listed in S0191 §5.4. S0189's original 8 phases (PHASE_01..08) shipped without extracting those modules — every concern is implemented locally with `TextNote*` / `TextEditor*` naming and text-specific logic. This phase refactors the existing implementation so S0191 can consume the same building blocks without copy-paste.

**Out of scope:** behaviour change. Every refactored class must keep identical behaviour for text notes. No new strings, no new menu items, no new flows. Pure extract-and-rename.

---

## Extraction Map

| # | S0189 source (current) | Target shared name | Package (target) | Generalisation contract |
|---|------------------------|--------------------|------------------|-------------------------|
| 1 | `util/TextNoteFileNameProvider` | `FileNameDefaultProvider` | `core/files` | Constructor takes `extension: String`. Template `YY-MM-dd_hh-mm.<ext>` stays in shared. Old `TextNoteFileNameProvider` becomes a thin Hilt-wired `@Named("text-note") = ".txt"` instance. |
| 2 | `domain/usecase/TextNoteNameConflictResolver` | `FileNameConflictResolver` | `domain/files` | Method `resolve(parentPath: String, desired: String, resource: MediaResource): String` — content-type agnostic. Auto-suffix `-ss` lives here. S0189 wraps with text-specific helper that still passes through. |
| 3 | `ui/browse/managers/BrowseTextNoteCreateManager` | `BrowseCreateEntityCommand<T : CreateEntityRequest>` | `ui/browse/create` | Interface with `isAvailableFor(resource)`, `requestCreate(parent)`, `onCreated(file)`. S0189 keeps `BrowseTextNoteCreateManager` as the text-note implementation; the interface + entry-point binding pattern (button id, overflow registration, capability predicate) is the new shared piece. |
| 4 | `data/local/TextNoteStagingRegistry` + `data/local/TextNoteStagingDirectory` | `LocalStagingRegistry` + `StagingDirectoryProvider` | `data/local/staging` | Registry indexes by `LocalFile → (targetResourceId, targetParentPath, kind)`. `kind` becomes an open enum (`TEXT_NOTE`, `DRAWING`, …) so the player loader's deferred-bypass logic switches on kind. Directory provider exposes one Downloads-staging dir per kind to avoid cross-feature collisions. |
| 5 | `ui/player/helpers/TextEditorActionPanelManager` | `EditorActionPanel` (interface) + `EditorActionPanelBinder` (default impl) | `ui/editor/actions` | Action set is fixed: `save · saveAndClose · saveAndShare · sendShare · cancel`. Binder takes a `ViewGroup` + `EditorActionCallbacks` and wires the 5 button ids `btn_editor_save / btn_editor_save_close / btn_editor_save_send / btn_editor_send / btn_editor_cancel`. Resource ids are renamed from `btn_text_*` to `btn_editor_*` so the same layout fragment serves both editors. |
| 6 | `ui/player/helpers/TextEditorDirtyStateTracker` + tint application | `EditorDirtyStateTracker` + `DirtyToolbarTinter` | `ui/editor/dirty` | Tracker takes a `StateFlow<String>` (current source-of-truth content) and a `String` baseline; emits `isDirty: Boolean`. Tinter takes the toolbar `View` + clean/dirty colour pair and observes the flow. No text-specific assumptions. |
| 7 | text share + Keep export code (lives inside `TextViewerManager` / `TextEditorActionPanelManager`) | `SystemShareInvoker` | `core/share` | One entry point: `invoke(context, payload: SharePayload)` where `SharePayload` = `Text(content: String)` or `Image(uri: Uri, mime: String)`. Hides the Keep package-targeting + chooser fallback the user agreed on (Keep for text, generic chooser for images per S0191 п.16). |

**Counter-check:** every row above has a single-line "what becomes generic" sentence. If a row needs more — split it.

---

## Steps

### 09.1 — Snapshot current behaviour

> Run a clean assembleStandardDebug and capture a one-line summary of the existing S0189 flows (note create, note open, note save, send-to-Keep). Stored as `temp/s0189_baseline_phase09.md` for cross-checking after each extraction.

- Build: `.\a.ps1 d`. Verification: exit 0.
- Manually open one local writable folder → create note → save → re-open → send to Keep. Record outcome bullets in `temp/s0189_baseline_phase09.md`. Verification: file exists with ≥4 bullets.

### 09.2 — Extract #1 FileNameDefaultProvider

- Create `app_v2/.../core/files/FileNameDefaultProvider.kt` with constructor `(private val extension: String)` and method `defaultName(now: Long = System.currentTimeMillis()): String`. Template logic copied verbatim from `TextNoteFileNameProvider`.
- Replace `TextNoteFileNameProvider` internals: keep the class as a thin subclass / `@Inject` wrapper that passes `".txt"`. All callsites stay unchanged.
- Verification: `Grep` for `TextNoteFileNameProvider` callsites — list unchanged. Build passes.

### 09.3 — Extract #2 FileNameConflictResolver

- Move resolver logic to `domain/files/FileNameConflictResolver.kt` (interface + default impl). Old class either deleted or made into a `@Singleton` that delegates to the new one.
- Update all callsites in S0189 to reference the shared symbol.
- Verification: `Grep` for `TextNoteNameConflictResolver` returns 0 or 1 hit (only the wrapper if kept). Build passes. Local create-note conflict scenario still produces `name-ss.ext`.

### 09.4 — Extract #3 BrowseCreateEntityCommand pattern

- Create interface `ui/browse/create/BrowseCreateEntityCommand.kt`:
  ```
  isAvailableFor(resource: MediaResource): Boolean
  bindToolbarButton(button: View)        // sets icon, click, overflow priority
  showCreateDialog(parentPath: String)
  ```
- Make `BrowseTextNoteCreateManager` implement it. `BrowseManagerInitializer.btnCreateTextFile` wiring moves to a generic registrar that takes `List<BrowseCreateEntityCommand>`.
- Verification: `Grep` `BrowseCreateEntityCommand` returns ≥1 impl (`BrowseTextNoteCreateManager`). Button still shows in toolbar exactly where it did before. Build passes.

### 09.5 — Extract #4 LocalStagingRegistry + StagingDirectoryProvider

- Move `TextNoteStagingRegistry` to `data/local/staging/LocalStagingRegistry` keyed by `LocalFile`. Existing `kind` field is preserved; declared `enum class StagedKind { TEXT_NOTE, DRAWING }` in same package.
- Move `TextNoteStagingDirectory` to `data/local/staging/StagingDirectoryProvider` with method `directoryFor(kind: StagedKind): File`. Text-note callers pass `TEXT_NOTE`.
- Update `PlayerMediaFilesLoader.deferred-note bypass` to switch on `StagedKind` and pick the right viewer (text editor for `TEXT_NOTE`).
- Verification: `Grep` `TextNoteStagingRegistry` / `TextNoteStagingDirectory` returns 0 hits. Note create → staging → save → cleanup still works (baseline scenario). Build passes.

### 09.6 — Extract #5 EditorActionPanel

- Move `TextEditorActionPanelManager` button-wiring to `ui/editor/actions/EditorActionPanelBinder` and define `EditorActionPanel` interface + `EditorActionCallbacks` data class with 5 `() -> Unit` slots.
- Rename layout ids `btn_text_*` → `btn_editor_*` in `res/layout/text_editor_action_panel.xml` (and `layout-land` if exists). Update findViewById sites.
- `TextViewerManager.onSave / onSaveAndClose / onSaveAndSend / onSendToKeep / onCancel` lambdas pass into `EditorActionCallbacks`.
- Verification: `Grep` `btn_text_save` / `btn_text_save_close` / etc. returns 0 hits. `Grep` `btn_editor_save` returns exactly 1 declaration per layout file + N usage sites. Save / save+close / save+send / cancel still work in text editor. Build passes.

### 09.7 — Extract #6 EditorDirtyStateTracker + DirtyToolbarTinter

- Generalise `TextEditorDirtyStateTracker`: parametrise the "current content" source (`StateFlow<String>`) and baseline (`String`). Move to `ui/editor/dirty/EditorDirtyStateTracker.kt`. Implementation logic untouched.
- Pull toolbar-tinting logic (currently inline inside `TextViewerManager` / `TextEditorActionPanelManager`) into `ui/editor/dirty/DirtyToolbarTinter.kt` with `attach(toolbar: View, isDirty: StateFlow<Boolean>, cleanColor: Int, dirtyColor: Int)`.
- Verification: `Grep` `TextEditorDirtyStateTracker` returns 0 hits in callsites (only the new shared class remains, or a thin wrapper). Dirty colour flip still occurs on first edit. Build passes.

### 09.8 — Extract #7 SystemShareInvoker

- Create `core/share/SystemShareInvoker.kt` and `core/share/SharePayload.kt` sealed class (`Text(content: String)`, `Image(uri: Uri, mime: String)`).
- `invoke(context, payload, preferredPackage: String? = null)` builds the `ACTION_SEND` intent. For `Text` + `preferredPackage = "com.google.android.keep"`, the Keep-targeting + chooser fallback from S0189's existing Keep flow moves in. For `Image`, no preferred package by default (per S0191 п.16).
- Replace inline share code in `TextViewerManager.onSendToKeep` and `TextEditorActionPanelManager` save+share path with calls into `SystemShareInvoker`. Existing `GoogleKeepAvailabilityChecker` is consulted before the call (no change to it — still text-specific signal).
- Verification: `Grep` `Intent.ACTION_SEND` inside `TextViewerManager` / `TextEditorActionPanelManager` returns 0 hits (all routed through `SystemShareInvoker`). Send-to-Keep with Keep installed and without Keep both work (baseline manual check).

### 09.9 — Catalog + dev_log + spec_catalog sync

- `pwsh dev/CATALOG/scripts/scan.ps1 -Module app_v2 ; pwsh dev/CATALOG/scripts/render.ps1 -Module app_v2`
- `pwsh scripts/check_strings_localized.ps1 -KeyPrefix "draw_"` — sanity check no draw_ key was accidentally added.
- `.\scripts\add_to_dev_log.ps1 "..." "S0189" "Phase 09: extracted 7 shared modules for S0191 consumption — no behaviour change for text notes."`
- `pwsh scripts/spec_catalog/update.ps1 -Id S0189 -Status BlockNeedUserTest`
- Verification: catalog regen produces no compile errors, journal status = `BlockNeedUserTest`.

### 09.10 — Reinsert S0189 verification tags

- For each refactored class (one per extraction), insert exactly one `Timber.d("S0189: <ClassName>.<entryMethod>")` at the entry path. Operator uses these to confirm the new shared classes are actually exercised on device.
- Verification: `Grep` `Timber\.d\("S0189:` returns exactly 7 lines (one per extraction).

---

## Phase Done Criteria

- [ ] All 7 extractions in the table above are complete and used by S0189.
- [ ] `assembleStandardDebug` passes.
- [ ] Existing text-note flows (create / save / save+close / save+send / cancel / Keep export) behave identically — manually verified against the baseline notes from 09.1.
- [ ] No new public strings, no new menu items, no new layouts beyond renamed ids.
- [ ] Spec catalog journal: `S0189` is back in `BlockNeedUserTest`.
- [ ] 7 `Timber.d("S0189:` tags present per 09.10.
- [ ] User confirms on device that text-note behaviour did not regress; status flips to `Verified` via `/spec-check`.
