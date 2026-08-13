# Phase 08 — Docs, catalog, functionality log

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 01..07
**Blocks:** —
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Final cleanup. Update `docs/FEATURES.md` and its trilingual mirrors with the new capability bullet. Regenerate the catalog. Append a single `ADD S0189 ..` entry to the functionality log. Confirm every changed file appears in `dev/CHANGELOG.md`. Stage the spec for `/spec-check`.

---

## Prerequisites

- [ ] Phase 01..07 all Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +5 lines |
| `docs/FEATURES_RU.md` | Modified | ≤ +5 lines |
| `docs/FEATURES_UK.md` | Modified | ≤ +5 lines |
| `dev/CATALOG/app_v2.jsonl` | Modified | regenerated |
| `dev/CATALOG/app_v2.md` | Modified | regenerated |
| `dev/CHANGELOG.md` | Modified | indirectly (via `add_to_dev_log.ps1`) |
| `dev/FUNCTIONALITY.log` | Modified | +1 line |

---

## Steps

### Step 08.1 — Update `docs/FEATURES*.md` (EN + RU + UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`

**Prompt for developer:**

> Add a single bullet under the closest matching feature area (likely "Browse" / "File operations" section — search the file for the create-folder bullet from S0165 and append the new entry directly below). English source first, RU + UK mirrors. Tone per `docs/COMMUNICATION_POLICY.md` §6.
>
> English: `Create a text note in any writable folder, edit it on the spot with quick save / save & close / save & send / send to Keep, with auto-fit font and swipe to resize.`
>
> Russian: `Создание текстовой заметки в любой папке с правом записи, мгновенное редактирование с быстрыми «Сохранить / Сохранить и закрыть / Сохранить и отправить / В Keep», авто-подгоном шрифта и свайпом для изменения размера.`
>
> Ukrainian: `Створення текстової нотатки в будь-якій теці з правом запису, миттєве редагування з командами «Зберегти / Зберегти й закрити / Зберегти й надіслати / У Keep», авто-підгоном шрифту та свайпом для зміни розміру.`
>
> No tables. One bullet. Avoid duplicating existing Browse bullets.

**Verification:**

- Grep — the English sentence ("Create a text note in any writable folder") matches once in `docs/FEATURES.md`.
- Grep — the Russian sentence matches once in `docs/FEATURES_RU.md`.
- Grep — the Ukrainian sentence matches once in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 08.2 — Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Prompt for developer:**

> Run, in order:
> 1. `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`
> 2. `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`
>
> For each newly-introduced class in Phases 01..07, run `pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class <ClassName> -Role "<one-line description>" -Status new`. The classes are:
> - `CreateTextNoteUseCase` — domain UseCase for creating a new text note
> - `SaveTextNoteUseCase` — domain UseCase for committing a text note (LOCAL or via staging upload)
> - `TextNoteFileNameProvider` — utility producing the default `yy-MM-dd_HH-mm.txt` name
> - `TextNoteNameConflictResolver` — utility appending `-ss` suffix on name collisions
> - `TextNoteStagingDirectory` — resolver for the `Downloads/FastMediaSorter/notes/` staging directory
> - `TextNoteStagingRegistry` — in-memory registry of pending staged notes per resource
> - `BrowseTextNoteCreateManager` — Browse-level orchestrator for create-text-note action
> - `TextEditorActionPanelManager` — manages the five edit-mode action buttons + dirty-state indicator
> - `TextEditorDirtyStateTracker` — observes EditText changes and exposes dirty/clean state
> - `TextEditorAutoFitFontManager` — auto-shrinking + manual-override font policy for editor
> - `TextEditorSaveFlow` — orchestrates Save dialog + UseCase + outcome toasts
> - `TextNoteSaveDialog` — Material dialog object for filename confirmation on save
> - `GoogleKeepAvailabilityChecker` — caches whether Google Keep can resolve the share intent

**Verification:**

- Bash — exit codes for the two scripts and each `set.ps1` invocation: all 0.
- Grep — each new class name appears in `dev/CATALOG/app_v2.jsonl` exactly once.

**Status:** `[ ]` not done

---

### Step 08.3 — Dev changelog entries

**Files:** `dev/CHANGELOG.md` (indirectly)

**Prompt for developer:**

> For every file modified or created across Phases 01..07 (excluding `dev/CHANGELOG.md` itself), invoke:
> ```
> .\scripts\add_to_dev_log.ps1 "<path>" "spec-S0189-PHASE_NN" "<short description>"
> ```
> Each phase should already have written its own dev log entries as it completed; this step is the audit / catch-up. Run `git diff --name-only` against the phase's base commit to enumerate the file set.

**Verification:**

- Grep — `S0189` matches in `dev/CHANGELOG.md` for at least each major touched file (sample-check: `BrowseViewModel.kt`, `TextViewerManager.kt`, `menu_resource_ops.xml`).

**Status:** `[ ]` not done

---

### Step 08.4 — Functionality log

**Files:** `dev/FUNCTIONALITY.log`

**Prompt for developer:**

> Run:
> ```
> .\scripts\add_to_functionality_log.ps1 -Id S0189 -Op ADD -Description "Browse: create text note in current folder, instant edit, quick save/save&close/save&send/send-to-Keep, auto-fit font, dirty-state indicator, conflict auto-suffix, Downloads-staging for network resources"
> ```

**Verification:**

- Grep — `S0189` matches in `dev/FUNCTIONALITY.log`.
- Grep — `Browse: create text note` matches in `dev/FUNCTIONALITY.log`.

**Status:** `[ ]` not done

---

### Step 08.5 — Update spec catalog status and hand off to `/spec-check`

**Files:** none direct — runs the journal CLI

**Prompt for developer:**

> 1. Confirm `BlockNeedUserTest` was set after Phase 04 (the first phase whose smoke required device interaction). If `update.ps1 -Id S0189 -Status BlockNeedUserTest` was not yet run, run it now.
> 2. After operator confirms all smoke scenarios pass on-device, run `/spec-check S0189`. `/spec-check` is responsible for removing all `Timber.d("S0189:")` debug tags from `.kt` files and flipping the spec to `Verified`.

**Verification:**

- Bash — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0189 -Format json` returns `"status":"BlockNeedUserTest"` (until smoke is signed off) or `"Verified"` (after `/spec-check`). Record actual status.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 08.*` is `[x] done`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "action_create_text_file"` → exit 0.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "text_editor_"` → exit 0.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "text_note_save_"` → exit 0.
- [ ] Spec journal status is `BlockNeedUserTest` (test gate) or `Verified` (after `/spec-check`).

---

## Handoff Notes to Next Phase

- Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

- This phase is documentation-only; revert is harmless. Behaviour rollback is owned by the per-phase rollback plans of Phases 01..07.
