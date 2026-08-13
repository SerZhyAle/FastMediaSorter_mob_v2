# Phase 05 - docs-catalog-cleanup

**Strategic spec:** [`../S0536_unify-ui-togglers.md`](../S0536_unify-ui-togglers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Lock the recommended toggler form in the architecture doc (canonical class + help-icon placement), then regenerate the catalog and record the dev log.

---

## Prerequisites

- [ ] Phases 01-04 ✅.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended (via script) | n/a |

---

## Steps

### Step 05.1 - Reinforce the recommended form in ARCHITECTURE.md

**Files:** `docs/ARCHITECTURE.md`

**Depends on:** - start of phase

**Prompt for developer:**

> In the "UI Patterns - Trigger Row" section (Pattern A): (1) update the canonical class to Material3 `com.google.android.material.materialswitch.MaterialSwitch` everywhere `SwitchMaterial` is shown in the Pattern A reference snippet and rules, since the component now embeds `MaterialSwitch`. (2) Make explicit, as a rule bullet, that the help icon sits INLINE immediately after the title (a weighted spacer fills the rest of the title line) and is NOT pinned to the right edge - the right edge is the optional trailing action slot, not the helper. (3) State that any switch left outside `SettingsToggleRow` must be a `MaterialSwitch` inheriting the project `materialSwitchStyle`. Keep wording terse; do not restate unchanged rules.

**Verification:**

- `Grep` - `materialswitch.MaterialSwitch` present in the Pattern A section of `ARCHITECTURE.md`.
- `Grep` - a bullet stating the help icon is inline / not right-pinned is present.
- `Grep` - `materialSwitchStyle` referenced in the Pattern A section.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. `docs/ARCHITECTURE.md` Pattern A: canonical class updated to `materialswitch.MaterialSwitch` in the reference snippet (line 55) + a new trigger-class rule bullet (line 96); help-icon rule made explicit as inline-after-title with a weighted spacer, never right-pinned (line 97), and a bullet clarifying the right edge is the trailing action slot; Reusable-component section now states the component embeds `MaterialSwitch` and that any switch left outside it must be a `MaterialSwitch` inheriting `materialSwitchStyle` (lines 116-117).

---

### Step 05.2 - Regenerate catalog and record dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`

**Depends on:** Step 05.1

**Prompt for developer:**

> Regenerate the app_v2 catalog (`SettingsToggleRow` internal field type changed; several dialog binding field types changed) and append batched dev-log entries for the ticket. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Add one dev-log entry per phase via `scripts/add_to_dev_log.ps1` (or one batched `close-and-log.ps1 -DevLogs` call). Do NOT touch `docs/FEATURES*.md` - strategic §8 is "Без изменений". Record the delivered capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` only if the team treats toggler unification as an inventory-worthy capability; otherwise skip (internal consistency change).

**Verification:**

- `Bash` - `scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `dev/CHANGELOG.md` contains an entry referencing S0536.
- `Grep` - `docs/FEATURES.md` unchanged (no S0536 entry).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS. Finalized via `close-and-log.ps1 -Id S0536 -Status Implemented -SkipFuncLog -CatalogModule app_v2`: status In Progress -> Implemented; 6 batched dev-log entries (spec + one per phase); catalog scan (34.6s) + render (3.6s) exit 0. `-SkipFuncLog` per strategic §8 (internal unification, no FEATURES capability). `docs/FEATURES*.md` untouched.

---

## Phase Done Criteria

- [x] Both `Step 05.*` are `[x] done`.
- [x] `scripts/catalog_sync.ps1 -Module app_v2` exits 0 (scan + render via close-and-log).
- [x] `dev/CHANGELOG.md` has entries for every modified file (batched per phase) - 6 entries.
- [x] `/spec-check S0536` is ready to run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, run `/spec-check S0536` to advance the strategic spec to `Verified`.

---

## Rollback Plan

Revert the doc/catalog commit - documentation-only; the catalog is a regenerated local index.
