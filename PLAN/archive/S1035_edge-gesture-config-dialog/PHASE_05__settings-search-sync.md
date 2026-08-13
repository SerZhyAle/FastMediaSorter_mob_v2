# Phase 05 - Settings-search + docs manifest sync

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Re-point settings-search so gesture detail rows are no longer indexed as Operations-tab rows; index the entry point (master toggle + "Configure gestures" button) instead, and regenerate the settings docs manifest/reference/annotations (Rule 22).

---

## Prerequisites

- [ ] Phase 04 is ✅ Done (rows relocated, launcher wired).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/RawSettingsSearchEntry.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGate.kt` | Modified | ≤ 500 |
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/SETTINGS_REFERENCE.md` (+ `_RU`, `_UK` if present) | Modified (generated) | n/a |

---

## Steps

### Step 05.1 - Re-point gesture search entries at the entry point

**Files:** `ui/settings/search/RawSettingsSearchEntry.kt`, `ui/settings/search/SettingsSearchCapabilityGate.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inspect the existing gesture-related settings-search entries (grep `gesture` in both files). Replace the per-zone/per-direction detail entries with a single entry pointing at the Operations-tab gesture entry point (master toggle + `btnOpenEdgeGestureConfig`); keep the same capability gate (present only where the gesture overlay capability exists). Do NOT deep-link into a specific zone (owner deferred deep-link, §6.6). Ensure the entry's target still resolves (section id / row id) after the relocation.

**Verification:**

- `Grep` - gesture detail row ids (e.g. `rowGestureLeftTopUp`) return zero hits in `RawSettingsSearchEntry.kt`.
- `Grep` - an entry referencing the gesture entry point (`btnOpenEdgeGestureConfig` or the gesture master/section) present.
- Project compiles - `/build` standard debug.
- `SettingsSearchCapabilityGateTest` still passes if it references gesture entries (`.\gradlew.bat testStandardDebugUnitTest --tests *SettingsSearchCapabilityGateTest*`).

**Status:** `[ ]` not done

---

### Step 05.2 - Regenerate settings docs manifest (Rule 22)

**Files:** `docs/settings/settings-manifest.json`, `docs/settings/settings-annotations.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Regenerate the settings manifest + reference and update annotations to reflect that the gesture detail settings now live behind the "Configure gestures" dialog (position/presence changed). Run the settings-doc-sync generator used by the project (see `scripts/quality/assert-settings-doc-sync.ps1` for the expected inputs/outputs) and let the gate validate parity.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - exit 0.
- `Grep` - the new `setting_edge_gesture_config_button` / dialog surface reflected in `settings-manifest.json`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] Settings-doc-sync gate passes.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Search + docs are consistent with the relocation. Phase 06 does catalog regen, feature inventory, and the final dev-log batch.

---

## Rollback Plan

Revert phase commit(s) - search entries + generated docs only; no runtime behaviour change.
