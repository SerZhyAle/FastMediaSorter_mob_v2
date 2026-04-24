# Phase 08 — Docs · Catalog · Final Cleanup

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Close the feature. Publish trilingual user-facing docs, regenerate the class catalog, ensure dev-log parity, and hand off to `/spec-check` for the final audit.

---

## Prerequisites

- [ ] Phases 01 — 07 are `✅ Done`.
- [ ] Strategic spec §10 Ambiguity Gate: every item has a resolution line; no `?` / `TBD` tokens remain.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |
| `dev/CHANGELOG.md` | Modified (appended) | — |
| `../spec_player-keybinding-remapping.md` | Modified | — |
| `PLAN/spec_player-keybinding-phase1-preparation.md` | Removed (pending user confirmation — see Step 08.5) | — |

---

## Steps

### Step 08.1 — Update `docs/FEATURES.md` trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Invoke `/doc-update` skill (CLAUDE.md §Mandatory Skills) and add one concise bullet under the appropriate feature area (likely "Playback" or "Settings"). Suggested wording derived from strategic §8:
>
> - **EN:** "Remappable controls — every keyboard, mouse, gamepad and VR-controller binding for the player is user-assignable through `Settings → Controls & Keybindings`, with per-row / per-group / global reset to factory defaults."
> - **RU:** "Переназначаемое управление — все клавиши клавиатуры, кнопки мыши, геймпада и VR-контроллера в плеере настраиваются через `Настройки → Управление и клавиши`, со сбросом к заводским значениям на уровне команды, группы или целиком."
> - **UK:** "Перепризначуване керування — усі клавіші клавіатури, кнопки миші, геймпада та VR-контролера в плеєрі налаштовуються через `Налаштування → Керування та клавіші`, зі скиданням до заводських значень на рівні команди, групи або цілком."
>
> Apply Author Style: `..` not `...`; use `ё`/`Ё` in RU (`настройки`/`настройки` unaffected — check `Управление`, `сброс`, no `ё` required there).

**Verification:**

- `Grep "Controls & Keybindings" docs/FEATURES.md` matches exactly once.
- `Grep "Управление и клавиши" docs/FEATURES_RU.md` matches exactly once.
- `Grep "Керування та клавіші" docs/FEATURES_UK.md` matches exactly once.
- `Grep -R "\\.\\.\\." docs/FEATURES*.md` returns zero hits (no `...`; only `..`).

**Status:** `[ ]` not done

---

### Step 08.2 — Regenerate `dev/CATALOG/app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the catalog scan:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> ```
>
> For every new class introduced by Phases 02 — 07 (listed below), set `role` and `status` via `set.ps1`:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class CommandId -Role "Key-binding command namespace" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class InputTrigger -Role "Sealed hierarchy of input triggers" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class InputBinding -Role "Command-to-trigger association" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class CommandGroup -Role "Taxonomy of player commands" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class KeyBindingManager -Role "Hot-path trigger-to-command resolver" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class DefaultsMapLoader -Role "Loads default bindings from assets JSON" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class InputBindingRepository -Role "Merges defaults with user overrides" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class InputBindingDao -Role "Room DAO for user binding overrides" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class InputBindingEntity -Role "Room entity for persisted overrides" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class KeybindingRemapActivity -Role "Fullscreen remapping UI host" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class KeybindingRemapViewModel -Role "State holder for remapping UI" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class KeybindingListAdapter -Role "RecyclerView adapter for grouped rows" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class CaptureDialogFragment -Role "Capture mode dialog" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class KeybindingRowLabelFormatter -Role "Trigger-to-label formatter" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class SetBindingUseCase -Role "Apply user override" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ResetBindingUseCase -Role "Single-command reset" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ResetGroupUseCase -Role "Group-level reset" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ResetAllUseCase -Role "Global reset" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class DetectConflictsUseCase -Role "Conflict detection pure function" -Status stable
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ResetConfirmationDialog -Role "Destructive-action confirmation" -Status stable
> ```
>
> If `UndoSnackbarController` was implemented in Phase 07 Step 07.6, add it. If the migration renamed `InputAction` or similar, confirm `role` / `status` stayed intact.

**Verification:**

- `Grep "KeyBindingManager" dev/CATALOG/app_v2.jsonl` matches ≥ 1 line with populated `role`.
- `Grep "\"role\":\"\"" dev/CATALOG/app_v2.jsonl` returns zero hits for any of the classes in the command list above.
- `dev/CATALOG/app_v2.md` has been regenerated (modification timestamp newer than pre-phase).

**Status:** `[ ]` not done

---

### Step 08.3 — Dev-log sweep

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 08.1, 08.2

**Prompt for developer:**

> For every file modified in Phase 08 and every file that earlier phases missed in their own dev-log sweep, invoke `.\scripts\add_to_dev_log.ps1`. Audit:
>
> 1. Grep `dev/CHANGELOG.md` for each of the files in Phase 02 — 07 "Files Touched" tables. Any missing file gets a retroactive log entry now with `<target>` = `spec-tech-phase-sweep`.
> 2. Add entries for the three `docs/FEATURES*` updates.
> 3. Add an entry for catalog regeneration (`dev/CATALOG/app_v2.jsonl`).

**Verification:**

- `Grep -c "PLAN/spec_player-keybinding-remapping" dev/CHANGELOG.md` returns ≥ 1.
- For each file in the union of "Files Touched" tables across Phases 02 — 07, `Grep -c "<file>" dev/CHANGELOG.md` returns ≥ 1.
- `Grep -c "docs/FEATURES" dev/CHANGELOG.md` returns ≥ 3 (EN + RU + UK entries).

**Status:** `[ ]` not done

---

### Step 08.4 — Move strategic spec to `Implemented`

**Files:** `../spec_player-keybinding-remapping.md`
**Depends on:** Steps 08.1 — 08.3

**Prompt for developer:**

> Edit the strategic spec header:
>
> - `**Status:** Tactical` → `**Status:** Implemented`
> - Add one line under the status header: `**Implemented on:** YYYY-MM-DD (tactical plan: ../spec_player-keybinding-remapping/INDEX.md)`
>
> Also update `INDEX.md` top-level `Status:` from `In Progress` → `Done` and `Phases: N/N done`.

**Verification:**

- `Grep "^**Status:** Implemented" PLAN/spec_player-keybinding-remapping.md` matches exactly once.
- `Grep "Status: Done" PLAN/spec_player-keybinding-remapping/INDEX.md` matches exactly once.
- `Grep "Phases: 8 / 8 done" PLAN/spec_player-keybinding-remapping/INDEX.md` matches exactly once.

**Status:** `[ ]` not done

---

### Step 08.5 — Remove or redirect the obsolete standalone phase-1 file

**Files:** `PLAN/spec_player-keybinding-phase1-preparation.md`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> This file predates the tactical folder and duplicates content now in `PHASE_01__preparation-inventory.md`. Its own Proposal P-1 calls for relocation. Two acceptable endings, choose one with the user:
>
> **Option A — delete.** `git rm PLAN/spec_player-keybinding-phase1-preparation.md` and log the removal via `add_to_dev_log.ps1`.
>
> **Option B — redirect stub.** Replace file content with:
>
> ```markdown
> # Superseded
>
> This file is superseded by [PLAN/spec_player-keybinding-remapping/PHASE_01__preparation-inventory.md](spec_player-keybinding-remapping/PHASE_01__preparation-inventory.md). Retained as a redirect for any external links. Safe to remove once no references remain.
> ```
>
> Confirm with the user which option applies before acting (destructive action per CLAUDE.md).

**Verification (Option A):**

- `Glob` — `PLAN/spec_player-keybinding-phase1-preparation.md` does not exist.
- Dev-log entry records the removal.

**Verification (Option B):**

- `Grep "Superseded" PLAN/spec_player-keybinding-phase1-preparation.md` matches exactly once.
- File LOC ≤ 5.
- Dev-log entry records the redirect.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 08.*` is `[x] done`.
- [ ] Trilingual docs carry the new feature bullet (EN/RU/UK).
- [ ] Catalog regenerated and every new class has non-empty `role` + `status`.
- [ ] Strategic spec status is `Implemented`.
- [ ] `INDEX.md` reports `Phases: 8 / 8 done` and `Status: Done`.
- [ ] `PLAN/spec_player-keybinding-phase1-preparation.md` is either removed or is a redirect stub.
- [ ] `/spec-check player-keybinding-remapping` reports `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate.

After `/spec-check` returns `Verified`, the strategic spec `Status:` advances to `Verified` and the feature is closed. Any future binding additions (new `CommandId`, new default trigger) do not require re-running this tactical plan — they are in-place edits to `default_bindings.json` + one line in `CommandId.kt`.

---

## Rollback Plan

- Doc changes are additive — revert the doc commit if the wording needs refinement.
- Catalog regeneration is idempotent; if it picked up incidental changes, re-run `scan.ps1` after fixes.
- Strategic spec status change is a single-line edit — trivially reversible.
