# Phase 05 - Accumulated debt pass

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Clear the 440 measured violations in the ten bulk-translated locales and the single authored key, using the Phase 04 fixer, and prove the resources still parse.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-*/strings*.xml` | Modified | value text only |
| `app_v2/src/vr/res/values-*/strings*.xml` | Modified | value text only |
| `app_v2/src/noLegal/res/values-*/strings*.xml` | Modified | value text only |
| `wear/src/main/res/values-*/strings*.xml` | Modified | value text only |
| `app_v2/src/main/res/values/strings.xml` | Modified | 1 value |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | 1 value |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | 1 value |

> No layout is touched, and no key is added, removed or renamed - only the text of existing values changes. No UI placement decision is in scope.

---

## Steps

### Step 05.1 - Fix the one authored violation

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the en dash in the `hint_access_pin` range with a plain hyphen in all three authored locales, through `scripts/utils/set-android-string.ps1 -Action set` with `-ExpectedOldValue`, one call per locale. The values are `PIN Code (4–6 digits)`, `PIN-код (4–6 цифр)` and `PIN-код (4–6 цифр)`.

**Why:**

Strategic §2 goal 2 requires the authored surface to be clean so that no ratchet baseline is needed, and this key is the entire authored debt.

**Verification:**

- `Grep` - `[–—―]` returns zero hits in the three authored `strings.xml` files.
- `Grep` - `hint_access_pin` still present in all three, with the digits `4` and `6` intact.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "hint_access_pin"` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - hint_access_pin fixed in en/ru/uk via set-android-string with -ExpectedOldValue. Fixer applied to 94 resource files, then 2 more after tightening the path exclusion to printable ASCII (a Chinese sentence containing a slash had read as a file path). Debt 317 ellipsis + 123 dashes -> 0 and 0. Keys 4469 unchanged, string-format delta 0, dq and fr both exit 0.

---

### Step 05.2 - Run the fixer over every locale

**Files:** `app_v2/src/*/res/values-*/strings*.xml`, `wear/src/*/res/values-*/strings*.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `scripts/utils/fix-house-style.ps1 -Area ResourceValue` in dry-run first and read the report, then re-run with `-Apply`. Exclude the `ё` rule for every locale except `ru`. Read the dry-run report before applying and confirm no value is changed inside a format placeholder or a URL; if one is, fix the library's exclusion in Phase 01 rather than hand-editing the resource.

**Why:**

Strategic §5.4 requires the accumulated 440 violations to be cleared by the same normalizer rather than by a separate one-off script, so the fix and the prevention cannot diverge.

**Verification:**

- The dry-run report was read before applying; its verdict is recorded in this phase Step Log.
- Re-running `evidence/measure-house-style-debt.ps1` reports 0 ellipsis and 0 long-dash occurrences in `strings*.xml`.
- `Grep` - the count of `<string name=` per changed file is unchanged from before the pass.
- `pwsh -NoProfile -File scripts/quality/assert-string-format.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - hint_access_pin fixed in en/ru/uk via set-android-string with -ExpectedOldValue. Fixer applied to 94 resource files, then 2 more after tightening the path exclusion to printable ASCII (a Chinese sentence containing a slash had read as a file path). Debt 317 ellipsis + 123 dashes -> 0 and 0. Keys 4469 unchanged, string-format delta 0, dq and fr both exit 0.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` -> `standard debug` (`.\a.ps1 dq`, exit 0). A normalizer that corrupted an escape sequence surfaces only at resource-compile time, and strategic §3.2 puts thirteen locales across three source sets in scope.
- [x] `.\a.ps1 fr` - exit 0 (resources and manifest).
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1` - exit 0.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Strategic §11 criteria 4 and 5 both measurable as satisfied.
- [x] Dev log entry added for the changed resource set via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both mandatory domains measure zero. Any later violation is a regression of Phase 02 or Phase 03, not residue.

---

## Rollback Plan

Revert the phase commit - only value text changed, no key was added, removed or renamed, so no locale falls back to English on revert.
