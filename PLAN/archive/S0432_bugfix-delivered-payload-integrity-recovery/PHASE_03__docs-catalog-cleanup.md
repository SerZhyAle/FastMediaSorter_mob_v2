# Phase 03 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0432_bugfix-delivered-payload-integrity-recovery.md`](../S0432_bugfix-delivered-payload-integrity-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Regenerate the class catalog for the new public exception, record dev-changelog entries for every touched file, and confirm string-locale parity. No `docs/FEATURES` change (strategic §8).

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

> Catalog indexes are gitignored local artifacts - regenerate, do not expect a git commit.

---

## Steps

### Step 03.1 - Regenerate catalog and set role for the new class

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan + render. Then set `role` + `status` for the new `DeliveredPayloadCorruptException` via `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` (role: typed exception signalling delivered-payload integrity failure; status: active). It is a `src/main` class (all flavors), so no `-NoFlavors` hint.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*DeliveredPayloadCorruptException*"` returns the record.

**Status:** `[ ]` not done

---

### Step 03.2 - Dev changelog + functionality log

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `dev/CHANGELOG.md` entry (via `.\scripts\add_to_dev_log.ps1`) for every file changed in Phases 01–02: the new exception, `DeliveredNativeLibraryLoader.kt`, `RecognitionBackend.kt`, and the three `strings.xml` locale files. Add one `dev/FUNCTIONALITY.log` FIX entry (via `scripts/add_to_functionality_log.ps1 -Op FIX`) describing the user-visible fix: corrupted OCR data now self-recovers (Extensions offers reinstall) instead of looping on a generic error. Run the functionality-log script last/standalone (it leaves a non-zero exit code on success).

**Verification:**

- `Grep` - `DeliveredNativeLibraryLoader.kt` appears in a recent `dev/CHANGELOG.md` entry.
- `Grep` - `S0432` (or the fix description) appears in `dev/FUNCTIONALITY.log`.

**Status:** `[ ]` not done

---

### Step 03.3 - String locale audit

**Files:** (validation only)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ocr_engines_damaged"`. Exit 1 = a locale is missing the key; fix before completing the phase. `docs/FEATURES*.md` is NOT updated (strategic §8: "Без изменений в docs/FEATURES").

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ocr_engines_damaged"` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] String locale audit exits 0.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next gate is `/spec-check S0432` (after device verification).

---

## Rollback Plan

Documentation/catalog only - re-run `scripts/catalog_sync.ps1 -Module app_v2` to restore the catalog; changelog/functionality entries are append-only history.
