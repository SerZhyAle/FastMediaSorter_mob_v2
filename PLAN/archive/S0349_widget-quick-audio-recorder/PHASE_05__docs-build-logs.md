# Phase 05 - Docs, build gate, catalog & logs

**Strategic spec:** [`../S0349_widget-quick-audio-recorder.md`](../S0349_widget-quick-audio-recorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Pending
**Depends on:** Phase 04

---

## Objective

Document the new user-facing widget, run the build gate across the relevant variants, sync the class catalog, and write the mechanical post-change logs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` + `_RU` + `_UK` | Modified | ≤ 6 each |

---

## Steps

### Step 05.1 - FEATURES docs

- Add a concise Smart Widgets bullet (EN/RU/UK) describing the Quick Recorder `1x1` widget (one-tap voice recording from the home screen).
- Use `/doc-update` conventions; keep trilingual mirrors in lockstep.

**Verification:**
- `Grep "Quick Recorder"` in `docs/FEATURES.md`; RU/UK have the mirrored bullet.

### Step 05.2 - Build gate

- `.\a.ps1 dq` (standardDebug) - expected BUILD SUCCESSFUL.
- `.\a.ps1 db` for `liteDebug` and `photosDebug` - manifest merge succeeds, widget receiver absent.

**Verification:**
- `expected: BUILD SUCCESSFUL ×3 | actual: <fill>`.
- Merged-manifest check from Step 04.3.

### Step 05.3 - Catalog sync + logs

- `scripts/catalog_sync.ps1 -Module app_v2`.
- Dev log entries for each touched file (or one summary per logical group).
- Functionality log: `add_to_functionality_log.ps1 -Id S0349 -Op ADD -Description "Quick Recorder home-screen widget: one-tap background voice recording"` (owned by /spec-dev on Implemented).

**Verification:**
- `dev/CATALOG/app_v2.jsonl` regenerated (new widget classes present).
- `Grep "S0349"` in `dev/FUNCTIONALITY.log` once.

---

## Phase Done Criteria

- `standardDebug` BUILD SUCCESSFUL; `liteDebug`/`photosDebug` merge OK.
- FEATURES EN/RU/UK updated.
- Catalog synced; dev + functionality logs written.
