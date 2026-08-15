# Phase 03 - Cleanup, catalog, dev log, debug tags

**Strategic spec:** [`../S0254_settings-grid-to-interface.md`](../S0254_settings-grid-to-interface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** -
**Steps done:** 5 / 5
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Close the spec: resolve the two §6 cleanup decisions (orphan string `settings_category_grid_view`, orphan SharedPreferences key `section_grid_view_expanded`), record the change in `dev/FUNCTIONALITY.log`, regenerate the class catalog, run the strings audit, and insert the `Timber.d("S0254: ..")` device-verification tags so the ticket can transition into `BlockNeedUserTest` for manual on-device confirmation.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Build succeeds: `.\a.ps1 dq` exit 0.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified (conditional) | ≤ existing |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified (conditional) | ≤ existing |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified (conditional) | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 600 |
| `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` | Modified (regen) | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |
| `dev/FUNCTIONALITY.log` | Modified (via script) | n/a |

---

## Steps

### Step 03.1 - Audit and resolve orphan string `settings_category_grid_view`

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Audit whether the string key `settings_category_grid_view` is referenced anywhere in the project after Phases 01-02. Run:
> ```powershell
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_grid_view"
> ```
> Then run a code-side grep:
> ```powershell
> # via the Grep tool (not bash grep):
> Grep -pattern "settings_category_grid_view" -path "P:/ANDROID/FastMediaSorter_mob_v2/app_v2"
> ```
> If the only matches are inside the three `strings.xml` files (EN/RU/UK), the key is orphaned - remove it from all three locale files in lockstep. If any source file (`.kt` or `.xml` other than strings.xml) still references it, keep it and append a comment `<!-- S0254: retained, in use by <consumer> -->` next to the EN definition.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_grid_view"` exit code is 0 (parity check passes either way: all three locales removed it, OR all three locales still define it).
- If removed: `Grep` for `settings_category_grid_view` across `app_v2/src/main/` returns zero matches.
- If retained: `Grep` for `settings_category_grid_view` matches at least one non-strings-xml consumer (justification trail present).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Audit: pre-removal grep showed only 3 hits (EN/RU/UK strings.xml). Removed all three. Post-removal `settings_category_grid_view` count in `app_v2/src/main`: 0. `check_strings_localized.ps1 -KeyPrefix "settings_category_grid_view"`: "No keys matching .. found in any locale." (parity achieved by removing from all three locales). Decision matches strategic §6.1 option (a). Files: `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (-1 LOC each).

---

### Step 03.2 - Record orphan-key decision in dev/CHANGELOG.md

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 `
>   "PLAN/S0254_settings-grid-to-interface.md" `
>   "decision" `
>   "S0254: leave SharedPreferences key 'section_grid_view_expanded' in 'playback_sections_state' as a harmless orphan; no migration added"
> ```
>
> This records the strategic §6.2 decision so future maintainers know the orphan key is intentional, not a forgotten cleanup.

**Verification:**

- `Grep` - the new line "S0254: leave SharedPreferences key" matches exactly once in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Decision recorded in `dev/CHANGELOG.md` via `add_to_dev_log.ps1` with target "decision" (target name "S0254 Phase03.2"). Line text "S0254: leave SharedPreferences key section_grid_view_expanded.." present in changelog. Matches strategic §6.2 chosen policy.

---

### Step 03.3 - Record functionality log entry

**Files:** `dev/FUNCTIONALITY.log` (via `scripts/add_to_functionality_log.ps1`)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_functionality_log.ps1 `
>   -Id S0254 `
>   -Op CHANGE `
>   -Description "Settings UI: relocate all Grid View controls (icon size + grid mode + hide grid action buttons + file ops overflow menu) from Playback tab into the Interface block of the General tab; portrait + landscape; behaviour unchanged"
> ```

**Verification:**

- `Grep` - `S0254 .* CHANGE .* Settings UI: relocate all Grid View` matches exactly once in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Functionality log line written: `[S0254] [CHANGE] Settings UI: relocate all Grid View controls (icon size + grid mode + hide grid action buttons + file ops overflow menu) from Playback tab into the Interface block of the General tab; portrait + landscape; behaviour unchanged`. Grep count in `dev/FUNCTIONALITY.log`: 1.

---

### Step 03.4 - Regenerate class catalog for app_v2

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> Run the catalog sync wrapper:
> ```powershell
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
> ```
> The wrapper chains `scan.ps1` and `render.ps1` in one process (per CLAUDE.md Rule B/C). Commit the regenerated `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` together with the code changes from Phases 01-02.

**Verification:**

- `scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `git status --short dev/CATALOG/app_v2.jsonl dev/CATALOG/app_v2.md` shows modified (or shows clean if no API change was detected - both are acceptable; the predicate is "regen ran without error").

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. `catalog_sync.ps1 -Module app_v2` returned: Scanned 1124 files / 1366 records → `dev/CATALOG/app_v2.jsonl`; Rendered 1366 records → `dev/CATALOG/app_v2.md`; OK (app_v2). Exit 0.

---

### Step 03.5 - Insert S0254 debug-verification tags and transition to BlockNeedUserTest

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags": insert one `Timber.d("S0254: ..")` tag at the entry point of each changed flow that the device test must exercise. Three tags total, one per moved switch's listener (the icon-size and tooltip flows piggyback on the grid-mode flow visually):
>
> 1. Inside `binding.switchGridMode.setOnCheckedChangeListener { _, isChecked -> ... }` (added in Phase 01.5):
>    ```kotlin
>    Timber.d("S0254: General tab grid mode switch toggled isChecked=$isChecked")
>    ```
> 2. Inside `binding.switchHideGridActionButtons.setOnCheckedChangeListener { _, isChecked -> ... }`:
>    ```kotlin
>    Timber.d("S0254: General tab hide grid action buttons toggled isChecked=$isChecked")
>    ```
> 3. Inside `binding.switchFileOpsOverflowMenu.setOnCheckedChangeListener { _, isChecked -> ... }`:
>    ```kotlin
>    Timber.d("S0254: General tab file ops overflow menu toggled isChecked=$isChecked")
>    ```
>
> One tag per flow entry, NOT per modified line. Do NOT insert tags inside observers (they fire on every settings emit, polluting logcat). Use Timber, never `Log.d`.
>
> Then transition the ticket:
> ```powershell
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0254 -Status BlockNeedUserTest
> ```
>
> The tags remain in code until `/spec-check S0254` flips the ticket out of `BlockNeedUserTest` (on `Verified` it grep-and-deletes all `Timber.d("S0254:` lines, per CLAUDE.md). Do NOT remove the tags as part of this phase.

**Verification:**

- `Grep` - `Timber.d("S0254: General tab grid mode switch toggled` matches exactly once in `GeneralSettingsFragment.kt` (or its helper, if delegation was used).
- `Grep` - `Timber.d("S0254: General tab hide grid action buttons toggled` matches exactly once.
- `Grep` - `Timber.d("S0254: General tab file ops overflow menu toggled` matches exactly once.
- `Grep` - `Timber.d("S0254:` matches exactly three times across `app_v2/src/main/` (no stray copies).
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0254 -Format json` returns `"status":"BlockNeedUserTest"`.
- Build: `.\a.ps1 dq` returns exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. 3 Timber.d("S0254:") tags inserted in `GeneralSettingsViewSetupHelper.kt` setupSwitches() - one per listener entry. Total grep count across `app_v2/src/main`: 3. Status transitioned `In Progress -> BlockNeedUserTest` via `update.ps1`. Final `.\a.ps1 dq` BUILD SUCCESSFUL in 45s. Tags remain in code until `/spec-check S0254` flips out of BlockNeedUserTest.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Spec catalog status for S0254 is `BlockNeedUserTest`.
- [ ] Three `Timber.d("S0254: ..")` tags present in code.
- [ ] `dev/CATALOG/app_v2.{jsonl,md}` regenerated.
- [ ] `dev/CHANGELOG.md` has the orphan-key decision line.
- [ ] `dev/FUNCTIONALITY.log` has the S0254 CHANGE entry.
- [ ] FEATURES files **not** touched (strategic §8 says "Без изменений").

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After on-device verification confirms the three tags fire when the user toggles each switch, run `/spec-check S0254` to flip to `Verified`; the tag-removal step happens automatically there.

---

## Rollback Plan

Revert the tag-insertion commit and the `update.ps1 -Status BlockNeedUserTest` transition; the cleanup decisions (orphan-string audit, orphan-key changelog line, functionality log entry, catalog regen) are independently revertible per their own commits.
