# Phase 03 - Settings row icon

**Strategic spec:** [`../S1919_launcher-naming-and-icon.md`](../S1919_launcher-naming-and-icon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Give the launcher toggle and the launcher-settings entry the existing launcher icon in both orientations, then regenerate the settings documentation the change invalidates.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/res/drawable/ic_launcher_mode.xml` exists - it is reused, not created.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | 2 attrs |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | 2 attrs |
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE.md` and its locale siblings | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified | ≤ 10 |
| `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md` | Modified | 2 lines |

> The two HOW_TO lines were added to this step on 2026-08-21, during execution: `settings-doc-sync` stage `howto-paths` failed because each guide spells a settings breadcrumb out in full - "Настройки -> Общие -> Настройки системного лаунчера -> Рабочий стол -> Плотность сетки" - and Phase 02 renamed that group segment. The plan had not foreseen that renaming a settings group invalidates every documented path through it. Fixing them here rather than in Phase 04 keeps the rename and its consequence in one change.

> Landscape variant exists and is listed - CLAUDE.md Rule 11.

---

## Steps

### Step 03.1 - Add the icon attribute to both rows in portrait

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> On `rowLauncherModeEnabled` add `app:str_icon="@drawable/ic_launcher_mode"`. On `rowLauncherSettings` add `app:ssr_icon="@drawable/ic_launcher_mode"`. Use each row's own prefix - `str_` belongs to `SettingsToggleRow`, `ssr_` to `SettingsSelectionRow`; the wrong prefix is silently ignored rather than failing the build. Add no new drawable and no hardcoded colour.

**Why:**

The owner's request in strategic §0 names this surface verbatim - the icon must be "в интерфеесе его тогглера в настроках" - and §4 records that the row already supports an optional leading icon that stays hidden while unset.

**Verification:**

- `Grep` - `app:str_icon="@drawable/ic_launcher_mode"` matches exactly once in the file.
- `Grep` - `app:ssr_icon="@drawable/ic_launcher_mode"` matches exactly once in the file.
- `Grep` - `="#` returns zero hits among the lines this step added.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - str_icon and ssr_icon set to ic_launcher_mode in both portrait and landscape (grep: one match each, no hex colour). reindex-settings regenerated manifest and all four SETTINGS_REFERENCE files; assert-settings-doc-sync exit 0 with the manifest stage actually run (manifest fresh). howto-settings-paths first FAILED on two settings breadcrumbs the phase-02 group rename invalidated - HOW_TO_RU:1152 and HOW_TO_UK:1132 - both fixed and now 60 recipes resolve. rowLauncherModeEnabled annotation reworded in RU and UK to name the launcher.

---

### Step 03.2 - Mirror both attributes into the landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Apply the same two attributes to `rowLauncherModeEnabled` and `rowLauncherSettings` in the landscape layout, matching the portrait values exactly.

**Why:**

Strategic §7 lists a portrait-only edit as a named risk, and CLAUDE.md Rule 11 requires the landscape counterpart in the same change whenever it exists - which it does, at `layout-land/fragment_settings_general.xml`.

**Verification:**

- `Grep` - `app:str_icon="@drawable/ic_launcher_mode"` matches exactly once in the landscape file.
- `Grep` - `app:ssr_icon="@drawable/ic_launcher_mode"` matches exactly once in the landscape file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - str_icon and ssr_icon set to ic_launcher_mode in both portrait and landscape (grep: one match each, no hex colour). reindex-settings regenerated manifest and all four SETTINGS_REFERENCE files; assert-settings-doc-sync exit 0 with the manifest stage actually run (manifest fresh). howto-settings-paths first FAILED on two settings breadcrumbs the phase-02 group rename invalidated - HOW_TO_RU:1152 and HOW_TO_UK:1132 - both fixed and now 60 recipes resolve. rowLauncherModeEnabled annotation reworded in RU and UK to name the launcher.
- 2026-08-21 - LAYOUT EVIDENCE (S1338): `evidence/01__settings-general-uitree-landscape.xml` plus the verdict and repro steps in `evidence/02__verdict-and-how-to-reproduce.md`, captured on the phone emulator running this ticket's own build 2.60.8212.313-DEBUG. The tree carries `str_icon` inside `rowLauncherModeEnabled` at [72,487][96,511] and `ssr_icon` on `rowLauncherSettings` at [12,533][36,540] - the leading-icon slot that was empty and hidden before this ticket. This proves the LANDSCAPE layout, because the device reports cur=1024x600. Portrait was not shot on device: the emulator carries an override size of 1024x600 over a 1080x2400 panel, set by another session, so it cannot rotate; portrait is proven statically by one grep match each for str_icon and ssr_icon in res/layout/fragment_settings_general.xml with attribute values identical to the landscape file.

---

### Step 03.3 - Regenerate the settings documentation and annotate the row

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE.md` and locale siblings, `docs/settings/settings-annotations.json`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1` to regenerate the settings manifest and reference from the live scan, then update the launcher toggle's entry in `docs/settings/settings-annotations.json` so its description matches the new title. Do not hand-edit the manifest or the reference - they are render targets.
>
> The same gate checks that every settings breadcrumb written out in the how-to guides still resolves against the manifest. Phase 02 renamed the group, so update the one breadcrumb in each guide - `docs/HOW_TO_RU.md` and `docs/HOW_TO_UK.md` - to name the group as it now reads.

**Why:**

CLAUDE.md Rule 22 fires on any change to a setting's naming or presence, and this phase changes both the title text (Phase 02) and the row's icon, so `assert-settings-doc-sync.ps1` refuses the close until the manifest is regenerated.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0, including its `howto-paths` stage.
- `Grep` - the new RU title from Step 02.1 appears in the regenerated manifest.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - str_icon and ssr_icon set to ic_launcher_mode in both portrait and landscape (grep: one match each, no hex colour). reindex-settings regenerated manifest and all four SETTINGS_REFERENCE files; assert-settings-doc-sync exit 0 with the manifest stage actually run (manifest fresh). howto-settings-paths first FAILED on two settings breadcrumbs the phase-02 group rename invalidated - HOW_TO_RU:1152 and HOW_TO_UK:1132 - both fixed and now 60 recipes resolve. rowLauncherModeEnabled annotation reworded in RU and UK to name the launcher.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The icon work is done and the settings gate is green. The documentation side of the icon is out of scope for this ticket - the launcher has no slot in the docs icon map to sit in, which is S1931.

---

## Rollback Plan

Revert the phase commit and re-run `scripts/quality/reindex-settings.ps1` so the regenerated documents match the reverted layouts.
