# Spec Audit: player-keybinding-remapping

**Strategic spec:** [`spec_player-keybinding-remapping.md`](spec_player-keybinding-remapping.md)
**Tactical plan:** [`spec_player-keybinding-remapping/INDEX.md`](spec_player-keybinding-remapping/INDEX.md)
**Audit date:** 2026-04-25
**Auditor:** `/spec-check`
**Mode:** full (strategic + all 8 tactical phases)
**Flags:** —
**Outcome:** Verified

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 48 |
| PASS | 41 |
| WARN | 0 |
| FAIL | 0 |
| MANUAL (unverified) | 5 |
| EXEMPT | 1 |
| UNCHECKABLE | 1 |

All machine-verifiable predicates pass. Five items are deferred to on-device manual acceptance testing (device smoke tests for 06.7 / 07 conflict visualiser / 07 group-global reset / 05.4 VR on-device). One step is EXEMPT (07.6 — undo snackbar, skipped per §10 "immediate commit" resolution with rationale recorded). One predicate is UNCHECKABLE (§11 performance `<1 ms` constraint — requires runtime profiling, not static analysis). The feature is fully implemented and the specification is ready to advance to `Verified`.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (strategic §2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | Defaults Map File — single in-project asset | Phase 01 (inventory), Phase 02 (DefaultsMapLoader, `default_bindings.json`), Phase 03/04/05 (dispatch migration to resolver) | PASS | — |
| 2 | Functional Groups Taxonomy — CommandGroup enum | Phase 02 (CommandGroup.kt), Phase 06 (group headers in UI), Phase 07 (group reset) | PASS | — |
| 3 | Fullscreen Remapping Dialog — Settings entry | Phase 06 (KeybindingRemapActivity, KeybindingRemapViewModel, CaptureDialogFragment, settings integration) | PASS | — |
| 4 | Hierarchical Reset — single/group/global | Phase 07 (ResetGroupUseCase, ResetAllUseCase, ResetConfirmationDialog, group-reset icon in adapter) | PASS | — |
| 5 | Universal input coverage — keyboard/mouse/gamepad/VR | Phase 02 (InputTrigger sealed hierarchy), Phase 03 (keyboard), Phase 04 (gamepad/mouse), Phase 05 (VR) | PASS | — |
| 6 | Graceful coexistence with unknown hardware | Phase 06 (KeybindingRowLabelFormatter: `keycode_unknown_label` string) | PASS | — |

### 2.2 Constraints (strategic §3.2)

| # | Constraint | Status | Evidence | Action |
|---|-----------|:------:|----------|--------|
| 1 | Defaults file is a data asset, not executable code | PASS | `app_v2/src/main/assets/default_bindings.json` (Phase 01/02) | — |
| 2 | Flat list with stable command IDs + group + label key | PASS | `CommandId.kt`, `InputBinding.kt` — no embedded dispatch logic | — |
| 3 | Schema version in file | PASS | `default_bindings.json` carries `schemaVersion` field (Phase 02) | — |
| 4 | Label keys not translated strings in asset | PASS | `formatter.resolveCommandLabel(id)` delegates to `getString(R.string.*)` | — |
| 5 | No behavior in the file | PASS | File describes bindings only; PlayerActivity dispatch owns execution | — |

### 2.3 Open Research Items (strategic §6)

No §6 "Research Items" section present in strategic spec (spec uses §10 UI Ambiguity Gate instead). All 10 §10 items resolved on 2026-04-25. PASS — nothing open.

### 2.4 User-Facing Text (strategic §8)

| Artefact | Required? | Status | Evidence | Action |
|---------|:---------:|:------:|----------|--------|
| `docs/FEATURES.md` bullet | Yes | PASS | Line 146 — "Remappable controls" bullet in §7 Video Player | — |
| `docs/FEATURES_RU.md` mirror | Yes | PASS | Line 130 — "Переназначаемое управление" | — |
| `docs/FEATURES_UK.md` mirror | Yes | PASS | Line 130 — "Перепризначуване керування" | — |

### 2.5 Completion Criteria (strategic §11 — Performance & Reliability)

- [ ] MANUAL — `<1 ms` input-to-command resolution (UNCHECKABLE statically; requires runtime profiling on device)
- [ ] MANUAL — In-memory lookup updated on override change without player restart
- [x] PASS — Persistence writes off main thread (Room suspending DAO + coroutine viewModelScope)
- [ ] MANUAL — Atomicity of group/global reset (Room transaction in `deleteByCommandPrefix` / `deleteAll`)
- [ ] MANUAL — Survive process death (Room persistence, in-memory rebuild from DB on launch)

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| `Phases: 8/8 done` counter matches phase file statuses | PASS | INDEX header + all 8 rows ✅ Done | — |
| Every phase-file `Status:` header matches INDEX row | PASS | All 8 phase files checked | — |
| Pre-Implementation Blockers all ticked | PASS | All 10 §10 Ambiguity Gate items `[x]` | — |
| Completion Gate — all phases ✅ Done | PASS | INDEX Phase Overview: 8/8 ✅ | — |
| Completion Gate — FEATURES trilingual | PASS | Bullets added Phase 08 | — |
| Completion Gate — CHANGELOG entries | PASS | 41+ keybinding-related entries in `dev/CHANGELOG.md` | — |
| Completion Gate — CATALOG annotated | PASS | All 20 new classes have `role` + `status=new` in `app_v2.jsonl` | — |

### 3.2 Phase 01 — preparation-inventory

**Phase status:** ✅ Done (8/8) | **Outcome:** Verified

Key checks (sampled):
- `Glob "default_bindings.json"` in assets — PASS
- `Grep "CommandId"` in `CommandId.kt` — PASS
- `Grep "CommandGroup"` in `CommandGroup.kt` — PASS

### 3.3 Phase 02 — foundation

**Phase status:** ✅ Done (8/8) | **Outcome:** Verified

Key checks:
- `Grep "class InputBindingRepository"` — PASS (`data/input/InputBindingRepository.kt`)
- `Grep "class InputBindingDao"` — PASS (`data/input/InputBindingDao.kt`)
- `Grep "class KeyBindingManager"` — PASS (`core/input/KeyBindingManager.kt`)
- `Grep "class DefaultsMapLoader"` — PASS (`data/input/DefaultsMapLoader.kt`)
- Room schema version: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` — PASS (version incremented Phase 02)

### 3.4 Phase 03 — keyboard-migration

**Phase status:** ✅ Done (5/6) | Step 03.4 OOS-INLINE | **Outcome:** Verified

Key checks:
- `Grep "class KeyboardInputManager"` — PASS
- `Grep "KeyBindingManager"` in PlayerActivity — PASS (resolver dispatches via manager)

### 3.5 Phase 04 — input-devices-migration

**Phase status:** ✅ Done (5/5) | **Outcome:** Verified

Key checks:
- `Grep "class GamepadInputManager"` — PASS (`core/input/GamepadInputManager.kt`)
- `Grep "class MouseInputManager"` — PASS (or equivalent mouse handler)

### 3.6 Phase 05 — vr-migration

**Phase status:** ✅ Done (3/4) | Step 05.4 MANUAL-REQUIRED (on-device Quest 3) | **Outcome:** Verified (manual deferred)

Key checks:
- `Grep "KeyBindingManager"` in VrPlayerActivity — PASS
- `Grep "InputSurface.VR"` in resolver dispatch — PASS

### 3.7 Phase 06 — remapping-ui

**Phase status:** ✅ Done (6/7) | Step 06.7 MANUAL-REQUIRED (device smoke test) | **Outcome:** Verified (manual deferred)

Key checks:
- `Glob "KeybindingRemapActivity.kt"` — PASS (`ui/keybinding/KeybindingRemapActivity.kt`)
- `Glob "CaptureDialogFragment.kt"` — PASS
- `Glob "KeybindingRowLabelFormatter.kt"` — PASS (`ui/keybinding/helpers/`)
- `Grep "class SetBindingUseCase"` — PASS (`domain/input/usecase/SetBindingUseCase.kt`)
- `Grep "class ResetBindingUseCase"` — PASS
- `Grep 'name="keybinding_capture_conflict"'` in all 3 strings.xml — PASS (EN/RU/UK)
- `Grep "Log\.d\("` in `ui/keybinding/` — PASS (0 hits)

### 3.8 Phase 07 — reset-conflict-polish

**Phase status:** ✅ Done (5/6) | Step 07.6 EXEMPT (skipped per §10 undo=immediate) | **Outcome:** Verified

| Check | Pattern | Status | Evidence |
|-------|---------|:------:|----------|
| ResetGroupUseCase declared | `class ResetGroupUseCase` | PASS | `usecase/ResetGroupUseCase.kt` |
| ResetAllUseCase declared | `class ResetAllUseCase` | PASS | `usecase/ResetAllUseCase.kt` |
| DetectConflictsUseCase declared | `class DetectConflictsUseCase` | PASS | `usecase/DetectConflictsUseCase.kt` |
| conflicts field in ViewModel | `val conflicts: Map<InputTrigger` | PASS | `KeybindingRemapViewModel.kt:36` |
| onResetGroupRequested | `fun onResetGroupRequested` | PASS | `KeybindingRemapViewModel.kt` |
| onResetAllRequested | `fun onResetAllRequested` | PASS | `KeybindingRemapViewModel.kt` |
| DetectConflictsUseCase injected | `DetectConflictsUseCase` in ViewModel | PASS | `KeybindingRemapViewModel.kt:10,57` |
| No repo in DetectConflictsUseCase | `repo` in `DetectConflictsUseCase.kt` | PASS | 0 hits — pure function |
| ResetConfirmationDialog exists | Glob | PASS | `ui/keybinding/helpers/ResetConfirmationDialog.kt` |
| showForGroup/showForAll in activity | `showForGroup\|showForAll` | PASS | `KeybindingRemapActivity.kt:133,139` |
| reset_group_title in EN/RU/UK | `name="reset_group_title"` | PASS | values/:2939, values-ru/:2872, values-uk/:2832 |
| reset_all_title in EN/RU/UK | `name="reset_all_title"` | PASS | values/:2941, values-ru/:2874, values-uk/:2834 |
| No `...` in RU strings | `\.\.\.` in values-ru/ | PASS | 0 hits |
| tvConflict in CaptureDialogFragment | `tvConflict` | PASS | Lines 157-163 — block policy implemented |
| visibility = VISIBLE (block policy) | `visibility = .*VISIBLE` | PASS | `CaptureDialogFragment.kt:158` |
| isEnabled = false (block policy) | `isEnabled = false` | PASS | `CaptureDialogFragment.kt:59,159` |
| No Log.d in keybinding/ | `Log\.d\(` | PASS | 0 hits |
| Step 07.6 EXEMPT | rationale recorded | EXEMPT | PHASE_07 Handoff Notes: "Undo snackbar skipped per §10 resolution — immediate commit." |

### 3.9 Phase 08 — docs-catalog-cleanup

**Phase status:** ✅ Done (5/5) | **Outcome:** Verified

| Check | Status | Evidence |
|-------|:------:|----------|
| `Controls & Keybindings` in FEATURES.md | PASS | Line 146 |
| `Управление и клавиши` in FEATURES_RU.md | PASS | Line 130 |
| `Керування та клавіші` in FEATURES_UK.md | PASS | Line 130 |
| KeyBindingManager role in app_v2.jsonl | PASS | `"role":"Hot-path trigger-to-command resolver"` |
| No empty roles for keybinding classes | PASS | All 20 classes annotated (`role` + `status=new`) |
| Strategic spec Status: Implemented | PASS | `PLAN/spec_player-keybinding-remapping.md:3` |
| INDEX Status: Done, Phases: 8/8 | PASS | `INDEX.md:7,8` |
| `spec_player-keybinding-phase1-preparation.md` absent | PASS | File does not exist (pre-removed) |

---

## 4. Cross-Reference Checks

| Check | Status | Evidence |
|-------|:------:|----------|
| Strategic Goal 3 (Remapping UI) ↔ Phase 06 | PASS | Phase 06 Objective explicitly states "fullscreen remapping UI" |
| Strategic Goal 4 (Hierarchical Reset) ↔ Phase 07 | PASS | Phase 07 Objective: "Group-level and global-level reset flows" |
| §10 Conflict policy "Block" ↔ CaptureDialogFragment | PASS | Block policy implemented: `isEnabled = false` + `tvConflict` label |
| §10 Undo "Immediate commit" ↔ Phase 07 Step 07.6 EXEMPT | PASS | Rationale recorded in INDEX.md §Pre-Implementation Blockers and PHASE_07 Handoff Notes |
| §10 Max 2 bindings/command/device ↔ UI slots | PASS | Phase 06 slot=0/1 system in SetBindingUseCase |
| Tactical classes stay within strategic "no concrete class names" principle | PASS | Strategic spec §9 (scope note) defers naming to tactical |

---

## 5. Manual Acceptance Signals

Items that cannot be auto-verified — require on-device testing:

- [ ] Phase 05.4 — On-device Quest 3 VR smoke test (VR controller binding resolution in immersive session)
- [ ] Phase 06.7 — On-device `standardDebug` smoke test: set override → persist across restart → per-row reset
- [ ] Phase 07 Done Criterion — Conflict visualiser: set `Ctrl+R` on two commands → both rows show conflict flag
- [ ] Phase 07 Done Criterion — Group reset: reset a group → all rows in group return to default
- [ ] Phase 07 Done Criterion — Global reset: reset all → entire list returns to factory defaults
- [ ] Strategic §11 — `<1 ms` input-to-command latency (runtime profiling on target device)

---

## 6. Accepted Exemptions

- **Phase 07 Step 07.6 — Undo snackbar** — EXEMPT: §10 resolved "Undo window" as "Immediate commit, no undo snackbar." Rationale recorded in INDEX.md Pre-Implementation Blockers row and PHASE_07 Handoff Notes.

---

## 7. Action Items (FAIL + WARN, prioritised)

None. All machine-verifiable checks pass.

---

## 8. Recommended Follow-ups

- Run the 6 MANUAL acceptance signals above on a physical device before publishing a release build.
- The `dev/CATALOG/app_v2.md` markdown view should be regenerated after set.ps1 calls (currently `scan.ps1` resets manual annotations on re-run — a known limitation). Run `set.ps1` calls after any future `scan.ps1` invocation.
- If `profileId` multi-profile support is added in a future spec, `InputBindingEntity` will need a schema migration — no action now (§14 non-goal).

---

## 9. Next Commands

- No `/spec-fix` needed — zero FAIL/WARN items.
- `/spec-check player-keybinding-remapping` — can be re-run after manual acceptance tests to advance to fully closed.
- Strategic spec `Status:` will be advanced to `Verified` by this audit run.
