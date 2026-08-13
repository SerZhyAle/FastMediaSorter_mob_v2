# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0335_settings-system-info-dialog.md`](../S0335_settings-system-info-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Regenerate the class catalog, document the new user-visible feature in `docs/FEATURES*`, and finalize logs.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Modified | n/a |

---

## Steps

### Step 04.1 - Regenerate the catalog and set the new class metadata

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan + render. Then set role/status for the new class via `set.ps1` for `GatherSystemInfoUseCase` (role: domain use case gathering device/system summary; status: active). The class lives in `src/main` and applies to all flavors - no `-NoFlavors` hint needed.

**Verification:**

- `Grep` - `GatherSystemInfoUseCase` present in `dev/CATALOG/app_v2.jsonl` (expected: ≥ 1 | actual: record).
- Catalog sync command exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. catalog_sync ran (1598 records); set.ps1 role/status=new applied; GatherSystemInfoUseCase present in app_v2.jsonl (expected ≥1 | actual 2). render exit 0.

---

### Step 04.2 - Document the feature (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise bullet under the Settings / Diagnostics feature area describing the System info action (view + copy + share a technical device/app summary). Mirror the sentence in all three files. This is a public standard-flavor feature - do NOT touch `docs/FEATURES_noLegal*`.

**Verification:**

- `Grep` - the new bullet (e.g. "System info") present in each of the three FEATURES files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS (EN=1, RU=1, UK=1). Bullet added under Diagnostic logs in §16. Dev log recorded.

---

### Step 04.3 - Finalize logs and advance ticket

**Files:** `dev/CHANGELOG.md` (via script), functionality log (via script), journal
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every modified source/layout/string file (via `add_to_dev_log.ps1`). Append a functionality-log line: `.\scripts\add_to_functionality_log.ps1 -Id S0335 -Op ADD -Description "Settings: System info dialog (view/copy/share device summary)"`. Then advance the ticket: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0335 -Status BlockNeedUserTest`. The `S0335:` probe tag inserted in Phase 03 stays in code until the ticket leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` - functionality log contains an `S0335` ADD line.
- `select.ps1 -Id S0335 -Format json` shows `status: BlockNeedUserTest` (expected: BlockNeedUserTest | actual: record).

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. FUNC_LOG S0335 ADD written; journal status BlockNeedUserTest (expected BlockNeedUserTest | actual BlockNeedUserTest). Note: add_to_functionality_log.ps1 leaves a non-zero exit code, which broke the chained run - update.ps1 + dev log were run separately.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/FEATURES*.md` trilingual bullet added.
- [ ] Dev log entry added for every modified file.
- [ ] Ticket status is `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After on-device verification, `/spec-check S0335` removes the `S0335:` probe tag and advances to `Verified`.

---

## Rollback Plan

Docs/catalog/log only - revert the doc commits; catalog regenerates from source on next sync.
