# Phase 06 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Document the R3 row-pattern change, regenerate the class catalog for the new dropdown attribute, and close changelog / doc-sync gates.

---

## Prerequisites

- [x] Phases 03, 04, 05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | +3 lines |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CATALOG/app_v2.md` | Regenerated | - |

> `docs/FEATURES*` untouched (strategic §8 = "Без изменений"). `dev/CATALOG/*` are gitignored local indexes.

---

## Steps

### Step 06.1 - Document selection-row value adjacency (R3)

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "UI Patterns - Trigger Row" section, add a short rule that the `SettingsSelectionRow` value renders inline inside the title line (right after the title/help), and the chevron stays pinned at the row's right edge via the weighted text group (the value is never separated from its title by the full row width). One bullet, no rationale prose.

**Verification:**

- `Grep` - `ssr_value` (inline) rule present in `docs/ARCHITECTURE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. Added a "Selection/value row (`SettingsSelectionRow`)" bullet under UI Patterns - Trigger Row: value renders inline on the title line, chevron pinned right via the weighted text group.

---

### Step 06.2 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the `SettingsDropdownRow` inline-attribute surface change.

**Verification:**

- `scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. `catalog_sync.ps1 -Module app_v2` exit 0 (1973 records). No public SettingsDropdownRow attr surface changed this run, but catalog regenerated for the touched fragments.

---

### Step 06.3 - Changelog + doc-sync gate

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.2

**Prompt for developer:**

> Ensure a `dev/CHANGELOG.md` entry exists for the S0618 change set (batched via `add_to_dev_log.ps1` / `close-and-log.ps1 -DevLogs`). Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` and confirm green - landscape reflow does not add/remove/rename/reposition any setting, so the settings manifest must be unchanged; if the gate flags a false positive, regenerate the manifest and re-run.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an `S0618` or landscape-settings entry.
- `scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. `assert-settings-doc-sync.ps1` exit 0 (no manifest drift - layout reflow does not add/remove/rename/reposition any setting). `assert-neuroslop.ps1` exit 0 (all deltas 0). Dev log entry added via `close-and-log.ps1 -DevLogs` at ticket closure.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] `/spec-check S0618` ready to run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. On the final transition to `BlockNeedUserTest`, `/spec-dev` inserts one `Timber.d("S0618: ..")` tag per touched settings fragment's view-setup entry (General / Media subs / Playback / Streams / Other / Destinations); these are removed by `/spec-check` on `Verified`. Owner verifies all four tabs in landscape on device.

---

## Rollback Plan

Revert phase commit(s) - docs/catalog only, no source or user-facing surface changed by this phase.
