# Phase 05 — Editor action icons and dirty-state indicator

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 04
**Blocks:** Phase 06, 07
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Replace the existing two-button edit panel (`btnSaveText` + `btnCancelEdit`) with the five-action panel mandated by §6.3:

- Save
- Save & Close
- Save & Send (Android share sheet → `ACTION_SEND text/plain`)
- Send to Keep (no save, no close) — `setPackage("com.google.android.keep")`
- Cancel (close without save)

Plus a dirty-state visual indicator: the command panel background tint changes when there are unsaved edits. The "Send to Keep" button is hidden when Google Keep is not installed or cannot resolve `ACTION_SEND text/plain`.

This phase wires the UI and intents. The actual save logic (filename dialog, conflict suffix, network upload) is owned by Phase 06.

---

## Prerequisites

- [ ] Phase 04 Done.
- [ ] Identify current edit-mode UI container in `activity_player_unified.xml` (search for `btnSaveText` / `btnCancelEdit`); record landscape variant existence.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | will likely cross 500 → backup mandatory |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | landscape parity (CLAUDE.md Rule 12) |
| `app_v2/src/main/res/drawable/ic_text_save.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_text_save_close.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_text_save_send.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_text_send_keep.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_text_cancel.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | Modified | currently 1372 LOC → MUST refactor before edit (CLAUDE.md Rule 2) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorActionPanelManager.kt` | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorDirtyStateTracker.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/GoogleKeepAvailabilityChecker.kt` | New | ≤ 60 |

---

## Steps

### Step 05.1 — Refactor `TextViewerManager` to extract the action-panel logic

**Files:** `TextViewerManager.kt` (1372 LOC → must shrink), `TextEditorActionPanelManager.kt` (new)

**Prompt for developer:**

> `TextViewerManager.kt` is at 1372 LOC — close to the 1500 LOC limit (CLAUDE.md Rule 2). Before adding the new action buttons, extract a new helper `TextEditorActionPanelManager` that owns:
> - Setup of edit-mode buttons (save / save-close / save-send / send-keep / cancel).
> - Dirty-state tracker invocation.
> - Keep availability check delegation.
>
> The new manager is constructed from `TextViewerManager.setupControls()` and the corresponding `btn*` click handlers are migrated. `TextViewerManager` retains the high-level `enterEditMode()` / `exitEditMode()` orchestration; the action-panel manager handles the buttons themselves.
>
> Before editing `TextViewerManager.kt`: create a timestamped backup in `temp/` (Rule 5). After extraction the file must be ≤ 1500 LOC; record final LOC count: `expected: ≤1500 | actual: <N>`.

**Verification:**

- Glob — `TextEditorActionPanelManager.kt` exists.
- Bash — `wc -l app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` returns a number ≤ 1500.
- Glob — `temp/TextViewerManager.kt.*.bak` exists.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

### Step 05.2 — Add string keys (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Prompt for developer:**

> Add the following keys, applying COMMUNICATION_POLICY §6 tone checklist:
>
> English:
> - `text_editor_action_save` → `Save`
> - `text_editor_action_save_close` → `Save & close`
> - `text_editor_action_save_send` → `Save & send`
> - `text_editor_action_send_keep` → `Send to Keep`
> - `text_editor_action_cancel` → `Cancel`
> - `text_editor_keep_unavailable` → `Google Keep isn't installed.`
>
> Russian (`ё`/`Ё`, `..`):
> - `text_editor_action_save` → `Сохранить`
> - `text_editor_action_save_close` → `Сохранить и закрыть`
> - `text_editor_action_save_send` → `Сохранить и отправить`
> - `text_editor_action_send_keep` → `В Keep`
> - `text_editor_action_cancel` → `Отменить`
> - `text_editor_keep_unavailable` → `Google Keep не установлен.`
>
> Ukrainian:
> - `text_editor_action_save` → `Зберегти`
> - `text_editor_action_save_close` → `Зберегти й закрити`
> - `text_editor_action_save_send` → `Зберегти й надіслати`
> - `text_editor_action_send_keep` → `У Keep`
> - `text_editor_action_cancel` → `Скасувати`
> - `text_editor_keep_unavailable` → `Google Keep не встановлено.`

**Verification:**

- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "text_editor_"` → exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist (`expected: pass | actual: pass`).

**Status:** `[ ]` not done

---

### Step 05.3 — Add drawables for the five actions

**Files:** `drawable/ic_text_save.xml`, `ic_text_save_close.xml`, `ic_text_save_send.xml`, `ic_text_send_keep.xml`, `ic_text_cancel.xml`

**Prompt for developer:**

> Create five 24dp Material-style vector drawables (white fill, null tint). Acceptable paths:
> - Save: floppy / checkmark glyph.
> - Save & close: floppy + `x` glyph in lower right.
> - Save & send: floppy + arrow-right glyph.
> - Send to Keep: Keep's lightbulb glyph OR a generic `send` arrow with a "K" badge (Google Keep brand asset must not be embedded if licensing is unclear — use a neutral glyph and rely on the localised button label for identification).
> - Cancel: `x` / cross glyph.
>
> Each file must declare `width="24dp" height="24dp" viewportWidth="24" viewportHeight="24"` and contain at least one `<path>`.

**Verification:**

- Glob — all five files exist.
- Grep — `viewportWidth="24"` appears in each.

**Status:** `[ ]` not done

---

### Step 05.4 — Add the five buttons in the player layouts (portrait + land)

**Files:** `res/layout/activity_player_unified.xml`, `res/layout-land/activity_player_unified.xml`

**Prompt for developer:**

> 1. Locate the current edit-mode action row that hosts `btnSaveText` and `btnCancelEdit`. Replace with a five-button row:
>    - `@+id/btnTextEditorSave`        — `@drawable/ic_text_save`
>    - `@+id/btnTextEditorSaveClose`   — `@drawable/ic_text_save_close`
>    - `@+id/btnTextEditorSaveSend`    — `@drawable/ic_text_save_send`
>    - `@+id/btnTextEditorSendKeep`    — `@drawable/ic_text_send_keep`  (initial `android:visibility="gone"` — programmatically shown only when Keep is available)
>    - `@+id/btnTextEditorCancel`      — `@drawable/ic_text_cancel`
> 2. Keep `btnSaveText` and `btnCancelEdit` IDs as aliases pointing at `btnTextEditorSave` and `btnTextEditorCancel` if any external code still references them, OR fully rename and update call sites (preferred — fewer aliases mean cleaner code).
> 3. The container of these five buttons must be `@+id/textEditorActionPanel` (LinearLayout horizontal). The dirty-state indicator changes this container's background tint.
> 4. **Landscape parity (Rule 12)**: same edit applied to `res/layout-land/activity_player_unified.xml`. Record absence of land variant as failure if missing — the player has a landscape variant per the existing convention.
> 5. Backup both files in `temp/` before editing (likely >500 LOC each).

**Verification:**

- Grep — `@+id/textEditorActionPanel` matches once in each layout file (2 hits total).
- Grep — all five new button ids (`btnTextEditor*`) match once in each layout file (10 hits across both).
- Glob — `temp/activity_player_unified.xml.*.bak` exists.
- Glob — `temp/activity_player_unified.xml.*.bak` for the land variant exists.
- Build: `assembleStandardDebug` compiles (view binding regenerates).

**Status:** `[ ]` not done

---

### Step 05.5 — Add `GoogleKeepAvailabilityChecker`, `TextEditorDirtyStateTracker`, wire `TextEditorActionPanelManager`

**Files:** `GoogleKeepAvailabilityChecker.kt`, `TextEditorDirtyStateTracker.kt`, `TextEditorActionPanelManager.kt`

**Prompt for developer:**

> 1. `GoogleKeepAvailabilityChecker`: `@Singleton class GoogleKeepAvailabilityChecker @Inject constructor(@ApplicationContext private val context: Context)`. Public method `fun resolveTargetPackage(): String?` returning the first package name from the candidate list whose `ACTION_SEND text/plain` intent resolves, or `null` if none. Candidate list (constant in companion object): `listOf("com.google.android.keep", "com.google.android.keep.notes")` — Google has shipped Keep under both ids historically, so probing both is mandatory. Algorithm: for each candidate, build `Intent(ACTION_SEND).apply { type = "text/plain"; setPackage(candidate) }`, call `context.packageManager.queryIntentActivities(intent, 0)`; return the first candidate whose result is non-empty. Cache the resolved value for the lifetime of the process (recheck only on app restart). Add a `fun isKeepAvailable(): Boolean = resolveTargetPackage() != null` convenience method. Add `Timber.d("S0189: GoogleKeepAvailabilityChecker -> $resolvedPackage")`.
> 2. `TextEditorDirtyStateTracker`: small helper that observes an `EditText` and exposes `val isDirty: StateFlow<Boolean>`. On `addTextChangedListener`, compare current text vs the snapshot taken at `markClean(initialText: String)`. Public methods: `markClean(initialText: String)`, `markDirty()`, `attach(editText: EditText)`, `detach()`. Add `Timber.d("S0189: TextEditorDirtyStateTracker dirty=$isDirty")` on every transition.
> 3. `TextEditorActionPanelManager` (from Step 05.1):
>    - Holds references to the five buttons + the panel container + dirty-state tracker + Keep checker.
>    - `fun setup(callbacks: ActionPanelCallbacks)`: wires click listeners — Save → `callbacks.onSave()`; Save & Close → `callbacks.onSaveAndClose()`; Save & Send → `callbacks.onSaveAndSend()`; Send to Keep → `callbacks.onSendToKeep()`; Cancel → `callbacks.onCancel()`.
>    - In `setup`: query `keepChecker.isKeepAvailable()`; set `btnTextEditorSendKeep.isVisible = available`.
>    - In `attach(editText)`: bind the dirty tracker; observe its flow on the panel's coroutine scope; on `isDirty=true` set `panel.background = R.drawable.bg_text_editor_action_panel_dirty` (or a tinted color via `setBackgroundColor` to keep the work minimal — drawable optional for v1). On `isDirty=false` reset background. Use a clearly distinguishable tint (e.g. `?attr/colorErrorContainer` or a custom warm color) — record the chosen color: `expected: clearly different from default panel | actual: <hex or attr>`.
>    - `ActionPanelCallbacks`: interface with `onSave()`, `onSaveAndClose()`, `onSaveAndSend()`, `onSendToKeep()`, `onCancel()`.

**Verification:**

- Glob — `GoogleKeepAvailabilityChecker.kt`, `TextEditorDirtyStateTracker.kt`, `TextEditorActionPanelManager.kt` all exist.
- Grep — `fun resolveTargetPackage(): String?` matches once.
- Grep — `"com.google.android.keep"` AND `"com.google.android.keep.notes"` both present in `GoogleKeepAvailabilityChecker.kt`.
- Grep — `fun isKeepAvailable(): Boolean` matches once.
- Grep — `class TextEditorDirtyStateTracker` matches once.
- Grep — `class TextEditorActionPanelManager` matches once.
- Grep — `interface ActionPanelCallbacks` (or similar) matches once.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

### Step 05.6 — Implement intent payloads and Send-to-Keep / Save-and-Send flows in `TextViewerManager`

**Files:** `TextViewerManager.kt`

**Prompt for developer:**

> In `TextViewerManager`, implement the `ActionPanelCallbacks`:
> - `onSave()` → call into Phase 06's `saveFlow.commitCurrent()` (stub the call for now if Phase 06 not implemented; emit `Timber.d("S0189: TextViewerManager.onSave deferred to Phase 06")`).
> - `onSaveAndClose()` → `onSave()` then `exitEditMode()` + close viewer.
> - `onSaveAndSend()` → `onSave()` then launch share sheet:
>   ```
>   val intent = Intent(Intent.ACTION_SEND).apply {
>       type = "text/plain"
>       putExtra(Intent.EXTRA_TEXT, currentText)
>   }
>   context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
>   ```
> - `onSendToKeep()` → launch Keep-targeted intent (no save). Use the resolved package id from `GoogleKeepAvailabilityChecker.resolveTargetPackage()` — supports both `com.google.android.keep` and `com.google.android.keep.notes`:
>   ```
>   val keepPackage = keepChecker.resolveTargetPackage() ?: run {
>       Toast.makeText(context, R.string.text_editor_keep_unavailable, Toast.LENGTH_SHORT).show()
>       return@onSendToKeep
>   }
>   val intent = Intent(Intent.ACTION_SEND).apply {
>       type = "text/plain"
>       putExtra(Intent.EXTRA_TEXT, currentText)
>       setPackage(keepPackage)
>   }
>   try { context.startActivity(intent) }
>   catch (_: ActivityNotFoundException) {
>       Toast.makeText(context, R.string.text_editor_keep_unavailable, Toast.LENGTH_SHORT).show()
>   }
>   ```
> - `onCancel()` → drop unsaved edits, `exitEditMode()`, close viewer (reuse the existing `btnCancelEdit` logic).
>
> Add `Timber.d("S0189: TextViewerManager.<onX> currentTextLen=${currentText.length}")` to each callback.

**Verification:**

- Grep — `keepChecker.resolveTargetPackage()` matches once in `TextViewerManager.kt`.
- Grep — `Intent.ACTION_SEND` referenced in `onSaveAndSend` and `onSendToKeep` paths.
- Grep — `Timber.d("S0189: TextViewerManager.onSave` present.
- Manual smoke (LOCAL note): edit text → dirty state changes panel color; press Send-to-Keep with Keep installed → Keep app opens with the text pre-filled.
- Manual smoke (Keep absent): Send-to-Keep button is hidden (not just disabled).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes.
- [ ] Strings audit clean (`check_strings_localized.ps1` exit 0 for `text_editor_` prefix).
- [ ] `TextViewerManager.kt` LOC ≤ 1500 after refactor.
- [ ] Manual smoke results recorded for Save & Send and Send-to-Keep.
- [ ] `add_to_dev_log.ps1` invoked for each touched file.
- [ ] `scan.ps1` + `render.ps1` for `app_v2`.

---

## Handoff Notes to Next Phase

- The Save / Save & Close / Save & Send buttons delegate `onSave()` to Phase 06's commit logic. Until Phase 06 lands, `onSave()` is a no-op stub with a `Timber.d` marker.
- Send-to-Keep is independent of save and is fully functional after this phase.
- The action panel's dirty-state tint will be re-used as a hint in Phase 06 when conflict resolution writes a `-ss`-suffixed name.

---

## Rollback Plan

- Revert this phase's commits. Layouts revert to the two-button edit panel. `TextEditorActionPanelManager` extraction is harmless if reverted alongside layouts. `GoogleKeepAvailabilityChecker` left dangling has no consumers and can be removed in a follow-up.
