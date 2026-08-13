# Phase 06 - Settings UI

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 05
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Add the mode's switch to the launcher settings "Top bar" group, gate it on the status-area replacement,
and make the tray switch below it read as deliberately unavailable while the mode is on.

---

## Prerequisites

- [x] Phases 01 and 05 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 265 |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified (the plan said this file did not exist - it does) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 340 |
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |

> Corrected during execution: `dialog_launcher_settings.xml` DOES have a `res/layout-land/` counterpart,
> and the file itself says why - one ViewBinding covers both, so an id present in only one variant is a
> null field in the other orientation. The row was mirrored into it in step 06.2. The instruction to
> confirm with `Glob` first is what caught this; the enumeration covered every `layout*` qualifier
> directory (`layout`, `layout-land`, `layout-sw480dp`, `layout-sw720dp`, `layout-w600dp`) and only the
> first two carry this file.

---

## Steps

### Step 06.1 - Add the three string keys in all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three keys in one lockstep call each via
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>`:
> `launcher_settings_top_status_strip_title` (the switch label), `launcher_settings_top_status_strip_summary`
> (one sentence saying the clock and indicators move to the top bar and the Start panel gains room), and
> `launcher_settings_tray_moved_hint` (the reason shown under the disabled tray switch). Check every string
> against `docs/COMMUNICATION_POLICY.md` §2 for the message formula and §6 for tone before writing it.

**Why:**

Strategic §3.2 makes EN/RU/UK parity mandatory for the new switch and its explanation, and the owner's
ruling in §4.3 requires the disabled tray row to carry a reason rather than simply going grey.

**Verification:**

- `Grep` - each of the three keys matches exactly once in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_t"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

---

### Step 06.2 - Add the switch row to the Top bar group

**Files:** `app_v2/src/main/res/layout/dialog_launcher_settings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a `SettingsToggleRow` with id `rowLauncherTopStatusStrip` inside `containerLauncherTopBar`
> (line 152), immediately after `rowLauncherReplaceStatusArea` (lines 161-166), copying that row's
> attribute shape and pointing `app:str_title` at `launcher_settings_top_status_strip_title` and
> `app:str_subtitle` at `launcher_settings_top_status_strip_summary`. Use no hardcoded hex colour.

**Why:**

Strategic §3.3 places the switch in the "Top bar" group next to the setting that gates it, so the
dependency between the two is visible where the user meets it.

**Verification:**

- `Grep` - `@+id/rowLauncherTopStatusStrip` matches exactly once in `dialog_launcher_settings.xml`.
- `Grep` - the new row appears between `rowLauncherReplaceStatusArea` and the close of
  `containerLauncherTopBar`.
- `Grep` - `="#` returns zero hits in `dialog_launcher_settings.xml`.

**Status:** `[x] done`

---

### Step 06.3 - Wire the switch and gate it on the replacement setting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Wire `rowLauncherTopStatusStrip` following the shape at lines 127-132 - guard on `isUpdatingFromSettings`,
> then `viewModel.updateSettings(viewModel.settings.value.copy(launcherTopStatusStripMode = isChecked))` -
> and mirror its read where the other rows are populated around line 235. Enable the row only while
> `launcherReplaceSystemStatusArea` is on, and turn the mode off when the replacement setting is switched
> off so no unreachable mode stays stored as on.

**Why:**

Strategic §11 criterion 1 requires the switch to be available only when the status area is replaced, and
the strategic risk row "режим включён, а замещение выключено" is what clearing the mode on that edge
prevents.

**Verification:**

- `Grep` - `rowLauncherTopStatusStrip` matches at least three times in the fragment (listener, read,
  enablement).
- `Grep` - `launcherTopStatusStripMode = false` appears on the replacement-off path.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 06.4 - Disable the tray row with its reason while the mode is on

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> While `launcherTopStatusStripMode` is on, set the tray switch row (`rowLauncherShowTray`, line 88)
> disabled and put `launcher_settings_tray_moved_hint` in its `str_subtitle`; restore its enabled state
> and its original subtitle when the mode goes off. Do not change the row's stored checked value in
> either direction.

**Why:**

Strategic ADR-5 rejected removing the row because a vanished row does not tell the user where the
indicators went, and rejected rewriting its value so strategic §11 criterion 9 can restore it unchanged.

**Verification:**

- `Grep` - `launcher_settings_tray_moved_hint` referenced in the fragment.
- `Grep` - `rowLauncherShowTray` has an `isEnabled` assignment driven by the mode.
- `Grep` - no `launcherTaskbarShowTray =` assignment appears on the mode path.

**Status:** `[x] done`

---

### Step 06.5 - Regenerate the settings documentation artifacts

**Files:** `docs/settings/settings-manifest.json`, `docs/settings/settings-annotations.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 06.4

**Prompt for developer:**

> Regenerate the settings manifest and the settings reference, and add the new row's annotation to
> `settings-annotations.json`. Do not hand-edit the generated manifest or reference files.

**Why:**

CLAUDE.md Rule 22 requires the settings manifest, reference and annotation to be regenerated for any
change to a setting's presence, and `assert-settings-doc-sync.ps1` fails the closure otherwise.

**Verification:**

- `Grep` - `launcherTopStatusStripMode` or `rowLauncherTopStatusStrip` appears in
  `docs/settings/settings-manifest.json`.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fc` (code + resources) exit 0.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1` (which chains it).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The mode is now reachable end to end. Everything the owner must check on a device is in place, so the
final phase closes the books rather than adding behaviour.

---

## Rollback Plan

Revert phase commit(s), then re-run the settings manifest and reference generators so the docs match the
reverted surface.

---

## Step Log

- 2026-08-09 - Step 06.1 done. Three keys added in one lockstep call each via `set-android-string.ps1 -Action add`. Checked against `docs/COMMUNICATION_POLICY.md`: the hint under the disabled tray row states what happened AND the way back ("Turn it off to bring them back to the Start panel"), which is section 3's dead-end rule; `..` is unused, so the two-dot ellipsis rule does not bite. expected: `check_strings_localized.ps1 -KeyPrefix "launcher_settings_t"` exit 0 | actual: exit 0, all 11 keys present in en/ru/uk.
- 2026-08-09 - Step 06.2 done, and the plan's premise was wrong: `res/layout-land/dialog_launcher_settings.xml` exists. The row was added to BOTH variants with the same id - Rule 11, and the land file's own comment records the reason (one ViewBinding, so a one-sided id is a null field in the other orientation). expected: `@+id/rowLauncherTopStatusStrip` 1 per variant, `="#` 0 | actual: 1, 1, 0.
- 2026-08-09 - Step 06.3 done. The mode's own listener follows the neighbouring rows' shape. Switching the replacement OFF now also clears the mode in the same `copy(..)`, so an unreachable mode can never stay stored as on - that is strategic risk row 6 closed at the source rather than compensated for downstream. expected: `rowLauncherTopStatusStrip` >= 3, mode cleared on the replacement-off path | actual: 3 references, cleared in the same settings write.
- 2026-08-09 - Step 06.4 done. `renderTopStatusStripRows` gates the mode row on the replacement setting and disables the tray row with `launcher_settings_tray_moved_hint` as its subtitle while the mode is on, restoring a null subtitle when it goes off. Neither path writes `launcherTaskbarShowTray`. Extracted into its own function rather than inlined so `observeSettings` stays under detekt's LongMethod threshold. expected: hint referenced, `isEnabled` driven by the mode, no `launcherTaskbarShowTray =` on the mode path | actual: 1, yes, 0.
- 2026-08-09 - Step 06.5 done via `scripts/quality/reindex-settings.ps1` rather than by hand. First run exit 3 - `MISSING annotations for 1 manifest key(s): rowLauncherTopStatusStrip`, which is the gate doing its job: the manifest is generated, the annotation is authored. Added the EN/RU/UK annotation and re-ran: exit 2 (`DRIFT regenerated`), meaning manifest and all four references are now fresh and must be committed. expected: key in the manifest, `assert-settings-doc-sync` green | actual: manifest 1 hit, `settings-doc-sync: OK`, and the new row rendered into SETTINGS_REFERENCE.md / _RU / _UK.
- 2026-08-09 - Phase-boundary audit. Layer 1: the mode row's enablement and the tray row's disabled state are both derived from the settings emission, not held as separate state, so no combination can drift out of sync with storage. Rule 22 satisfied mechanically. No P0/P1 findings.
- 2026-08-09 - Closure. `post-change.ps1 -Files <9> -ScopeToFile -ChangeType Mixed -KeyPrefix launcher_settings_t`: `post-change: PASS`, exit 0 - including `assert-detekt: PASS [scoped]`, since this phase touched no file carrying the S1541 debt.
