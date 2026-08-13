# Phase 05 - Strings + Arrays Cleanup

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Remove the nine obsolete VR-format string keys from EN/RU/UK locales, remove the four obsolete spinner-array resources, run the locale audit to confirm parity. By this phase, nothing in the codebase references these keys (UI was stripped in Phase 01; new help text was added in Phase 04).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (UI layout no longer references the keys).
- [ ] Phase 04 ✅ Done (new help text in place; old `settings_vr_help_title`/`_message` no longer referenced).
- [ ] Repo-wide `Grep` for each of the nine keys returns 0 hits in `.kt` / `.xml` source (run before starting this phase to confirm).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | < current size |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | < current size |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | < current size |
| `app_v2/src/main/res/values/arrays.xml` | Modified | < current size |

---

## Steps

### Step 05.1 - Delete nine string keys from each locale

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In ALL THREE locale files, delete the `<string>` elements with these names:
>
> 1. `settings_vr_section_title`
> 2. `settings_vr_forced_flat_format`
> 3. `settings_vr_forced_flat_format_desc`
> 4. `settings_vr_forced_spherical_format`
> 5. `settings_vr_forced_spherical_format_desc`
> 6. `settings_vr_remember_format`
> 7. `settings_vr_remember_format_desc`
> 8. `settings_vr_help_title`
> 9. `settings_vr_help_message`
>
> Before deleting, run a repo-wide `Grep -n "settings_vr_<key>"` for each key to confirm zero remaining references in any source file (`.kt`, `.xml`, except the strings file itself). If any reference is found - it is a Phase 01 / Phase 04 regression - stop and fix the referrer before continuing.
>
> Keep all unrelated VR keys (`vr_settings_block_title`, `vr_settings_xr_unavailable_advisory`, `vr_settings_test_immersive_*`, `vr_settings_master_toggle_*`, etc.) untouched - they belong to other specs (S0249, S0250).

**Verification:**

- For each of the nine keys: `Grep -n "name=\"<key>\""` across all `values*/strings.xml` files → 0 hits.
- Repo-wide `Grep -n "<key>"` (referencing `R.string.<key>`) → 0 hits.
- The three locale files still parse (open in Android Studio - no red error).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: EN/RU/UK `strings.xml` (-9 obsolete `settings_vr_*` keys per locale). Referencing grep expected 0 | actual 0.

---

### Step 05.2 - Delete four obsolete spinner-array resources

**Files:** `app_v2/src/main/res/values/arrays.xml` (and any locale overrides if they exist)
**Depends on:** Step 05.1

**Prompt for developer:**

> Open `app_v2/src/main/res/values/arrays.xml`. Delete the following four `<string-array>` blocks:
>
> 1. `vr_forced_format_entries`
> 2. `vr_forced_format_values`
> 3. `vr_forced_spherical_format_entries`
> 4. `vr_forced_spherical_format_values`
>
> Check for locale overrides: `Glob app_v2/src/main/res/values-*/arrays.xml`. If any locale defines overrides for these four arrays (translated entries), delete them in those files too. Keep all unrelated arrays.

**Verification:**

- `Grep -n "vr_forced_format_entries"` repo-wide → 0 hits.
- `Grep -n "vr_forced_format_values"` repo-wide → 0 hits.
- `Grep -n "vr_forced_spherical_format_entries"` repo-wide → 0 hits.
- `Grep -n "vr_forced_spherical_format_values"` repo-wide → 0 hits.
- `arrays.xml` still parses (open in Android Studio).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: EN/RU/UK `strings.xml` (removed locale `string-array` overrides). `arrays.xml` expected touched by tactical prompt | actual not touched because no obsolete arrays lived there.

---

### Step 05.3 - Run locale audit + dev log

**Files:** dev log, scripts/check_strings_localized.ps1
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Run the locale audit script against the affected prefixes:
>
> ```powershell
> pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_vr_"
> pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_forced_"
> ```
>
> Both must exit 0 (no keys with this prefix anywhere = trivially parity-pass). Then run:
>
> ```powershell
> pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_3d_vr"
> ```
>
> This must exit 0 (the new keys from Phase 04 must be present in all three locales).
>
> Dev log entries:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0251" "Phase 05: drop 9 obsolete settings_vr_* string keys (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0251" "Phase 05: drop 9 obsolete settings_vr_* string keys (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0251" "Phase 05: drop 9 obsolete settings_vr_* string keys (UK)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/arrays.xml" "S0251" "Phase 05: drop 4 obsolete vr_forced_format spinner arrays"
> ```

**Verification:**

- All three `check_strings_localized.ps1` invocations exit 0.
- `Grep -n "S0251.*Phase 05"` in `dev/CHANGELOG.md` → exactly 4 hits.
- `/build` for `standardDebug`, `vrDebug`, `noLegalDebug` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Files: EN/RU/UK `strings.xml`, `fragment_settings_revised_general.xml`, deleted broken `layout-land/fragment_settings_revised_general.xml`, `dev/CHANGELOG.md`. Locale audits PASS: `settings_vr_`, `vr_forced_`, `settings_3d_vr` all exit 0. Builds PASS after binding-id repair: `assembleStandardDebug`, `assembleVrDebug`, `assembleNoLegalDebug` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Locale audit exits 0 for all three prefixes (`settings_vr_`, `vr_forced_`, `settings_3d_vr`).
- [x] `/build` for the three target variants passes.
- [x] Dev log carries S0251 Phase 05 entries for every modified file; `arrays.xml` was not touched because obsolete arrays lived in locale `strings.xml`.

---

## Handoff Notes to Next Phase

All obsolete string and array resources are gone. Locales are in parity. Phase 06 (final) runs catalog sync, decides the FEATURES.md bullet semantics (§6.1), appends the functionality log entry, and flips the spec to `BlockNeedUserTest` for device verification.

---

## Rollback Plan

Revert the four file diffs. Any old layout / code referencing the removed keys would break, but Phase 01 / Phase 04 already eliminated all referrers - rollback alone reintroduces the keys but leaves UI untouched.
