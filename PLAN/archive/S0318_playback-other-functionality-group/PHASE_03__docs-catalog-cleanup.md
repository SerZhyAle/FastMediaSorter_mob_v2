# Phase 03 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0318_playback-other-functionality-group.md`](../S0318_playback-other-functionality-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Close mechanical post-change steps: regenerate the class catalog and confirm dev-log/functionality-log/localization coverage.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (regenerated) | Modified | - |
| `dev/CATALOG/app_v2.md` (regenerated) | Modified | - |

---

## Steps

### Step 03.1 - Regenerate catalog and confirm localization audit

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the gitignored catalog after the Fragment change. Re-confirm `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_other_features"` exits 0.

**Verification:**

- `catalog_sync.ps1 -Module app_v2` exits 0.
- `check_strings_localized.ps1 -KeyPrefix "settings_category_other_features"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 2/2 PASS. catalog_sync app_v2 → 1520 records OK; strings audit → EN/RU/UK all OK. expected: both exit 0 | actual: catalog OK, strings OK.

---

### Step 03.2 - Dev log + functionality log

**Files:** (logs)
**Depends on:** Step 03.1

**Prompt for developer:**

> Ensure a `dev/CHANGELOG.md` entry exists for each modified file (via `add_to_dev_log.ps1`). Append one functionality-log line: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0318 -Op CHANGE -Description "Playback settings: moved camera-capture block and Black Screen button toggle into a new 'Other features' group"`. Do NOT touch `docs/FEATURES*.md` - strategic §8 declares no FEATURES change.

**Verification:**

- `Grep` - `S0318` present in `dev/FUNCTIONALITY.log`.
- `dev/CHANGELOG.md` contains entries for the modified layout/strings/Fragment files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 2/2 PASS. FUNCTIONALITY.log:187 has S0318 CHANGE line; dev/CHANGELOG.md has entries for all 6 modified files (recorded via post-change). expected: S0318 in func log + changelog entries | actual: present.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/FEATURES*.md` untouched (no new user-facing capability).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Ready for `/spec-check S0318` after on-device verification leaves `BlockNeedUserTest`.

---

## Rollback Plan

Catalog files are regenerated artifacts - no rollback needed. Log appends are additive.
