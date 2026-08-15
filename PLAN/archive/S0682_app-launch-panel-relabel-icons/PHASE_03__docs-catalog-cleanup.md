# Phase 03 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0682_app-launch-panel-relabel-icons.md`](../S0682_app-launch-panel-relabel-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Close out the change: localization audit, catalog regen, dev log, and capability record - leaving the tree gate-clean.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (via script) | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | n/a |

> No `docs/ALL_FEATURES.jsonl` entry: this is UX polish of an existing capability (the panel itself is recorded under S0663), not a new shippable capability.

---

## Steps

### Step 03.1 - Localization audit of relabeled keys

**Files:** none (validation)
**Depends on:** - start of phase

**Prompt for developer:**

> Run the localization audit over the touched key families and fix any reported gap before proceeding: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_path_"` and again with `-KeyPrefix "app_launch_panel_picker_"`. Both must exit 0.

**Verification:**

- Both audit invocations exit 0 (record `expected: 0 | actual: <code>`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Both audits PASS (path_ family 4 keys, picker_ family 3 keys - all EN/RU/UK present).

---

### Step 03.2 - Run post-change facade (catalog + dev log + gates)

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/main/java/com/sza/fastmediasorter/core/panel/OsShortcutCatalog.kt" -Target "applaunchpanel" -Description "Distinct icons for OS shortcuts + relabel add-chooser items (S0682)" -ChangeType Mixed -Module app_v2`. This chains dev-log, catalog-sync, and the neuroslop/settings-doc/deprecated-PM gates. Resolve any gate failure in the touched files.

**Verification:**

- `post-change.ps1` exits 0.
- `Grep` - `S0682` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - dev-log + catalog-sync PASS; gates green (neuroslop delta 0, settings-doc OK, deprecated-pm delta 0, ticket-log 0 after BlockNeedUserTest flip). S0682 present in dev/CHANGELOG.md.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `docs/FEATURES.md` trilingual update skipped (strategic §8 = "Без изменений").
- [ ] Insert `Timber.d("S0682: ..")` probe at the add-chooser entry point before the build that precedes device test (CLAUDE.md Debug Verification Tags), since the ticket enters BlockNeedUserTest.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After build passes, advance S0682 to `BlockNeedUserTest` with a device-test note covering: open Edit panel, add a tile, verify the two renamed chooser items ("Приложение", "Настройки ОС Андроид") and that each OS setting in the picker shows a distinct icon - RU/UK/EN.

---

## Rollback Plan

Revert phase commit(s) - documentation/catalog only; no runtime effect.
