# Phase 03 - Docs, catalog, capability, settings doc-sync

**Strategic spec:** [`../S0680_gesture-crop-screenshot-share.md`](../S0680_gesture-crop-screenshot-share.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

**Step Log:**

- 2026-06-25 - 03.1 PASS. `catalog_sync.ps1 -Module app_v2` exit 0 (2030 records).
- 2026-06-25 - 03.2 PASS. `all_features/add.ps1` -> `screen-capture.edge-gesture-crop-and-share` (standard,noLegal); `validate.ps1` PASS (412 records).
- 2026-06-25 - 03.3 PASS. `assert-settings-doc-sync.ps1` exit 0; no manifest/reference delta (enum option values are not manifest-tracked settings). expected: gate PASS | actual: PASS.
- 2026-06-25 - 03.4 PASS. Dev logs recorded per file across P01/P02; `assert-neuroslop.ps1` exit 0 (no regressions).

---

## Objective

Close out: regenerate the class catalog, record the new shippable capability, reconcile the settings docs for the new gesture-action value, and ensure dev-log coverage. No product behavior changes here.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | n/a |
| `docs/settings/settings-manifest.json` | Modified (via generator, if delta) | n/a |
| `docs/SETTINGS_REFERENCE*.md` | Modified (via generator, if delta) | n/a |
| `docs/settings/settings-annotations.json` | Modified (if delta) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | n/a |

---

## Steps

### Step 03.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. No new classes were introduced (only a new method + enum value), so no `set.ps1` role/status fill is needed; this just refreshes the index.

**Verification:**

- Script exits 0.

**Status:** `[x]` done

---

### Step 03.2 - Record the new capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - independent

**Prompt for developer:**

> Add the capability via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): a left-edge gesture action that captures the screen, opens the screenshot in a crop frame, overwrites with the cropped result, and opens the app's "Send to.." menu. Record the flavors consistently with the existing edge-gesture-action records (the action lives in `src/main`, reachable in standard with `fms.edgeGestureOverlay=on` and always in noLegal). Then run `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a new record mentioning crop + share gesture exists in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Reconcile settings docs for the new gesture-action value

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** Phase 02

**Prompt for developer:**

> The screenshot-gesture per-direction action is a tracked setting; its selectable values changed. Regenerate the settings manifest + reference and update the annotation as required by CLAUDE.md Rule 22, then confirm the gate. Run the project's settings-doc generator (see `scripts/quality/assert-settings-doc-sync.ps1` for the expected inputs) and `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. If the gate reports no delta (enum values not tracked in the manifest), record `expected: gate PASS | actual: PASS` and move on.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Dev-log coverage and post-change closure

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Steps 03.1-03.3

**Prompt for developer:**

> Ensure one dev-log entry per logical change exists for the S0680 work (Phase 01 orchestration, Phase 02 wiring/strings) via `.\scripts\add_to_dev_log.ps1` or `close-and-log.ps1 -DevLogs`. Do not hand-edit `dev/CHANGELOG.md`. Run the neuroslop + string gates over touched files (`scripts/post-change.ps1` facade is acceptable).

**Verification:**

- `Grep` - `S0680` (or the changed file paths) appears in recent `dev/CHANGELOG.md` entries.
- `scripts/quality/assert-neuroslop.ps1` exits 0 for touched files.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `/spec-check S0680` is ready to run.

---

## Handoff Notes to Next Phase

- Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. Next is on-device verification (`BlockNeedUserTest`) then `/spec-check S0680`.

---

## Rollback Plan

- Docs/catalog only - revert the doc commits; no code or user-facing impact.
