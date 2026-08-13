# Phase 04 - Strings, docs and closure

**Strategic spec:** [`../S1223_vr-immersive-controls-discoverability.md`](../S1223_vr-immersive-controls-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Give every legend caption a value in all three locales, make the public controls reference say what the headset now says, record the capability, and park the ticket for its Quest verification.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] `CODE.LOCK` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/res/values/strings.xml` | Modified | - |
| `app_v2/src/vr/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/vr/res/values-uk/strings.xml` | Modified | - |
| `docs/VR_CONTROLS.md` | Modified | - |
| `docs/VR_CONTROLS_RU.md` | Modified | - |
| `docs/VR_CONTROLS_UK.md` | Modified | - |

---

## Steps

### Step 04.1 - Legend strings in three locales

**Files:** `app_v2/src/vr/res/values/strings.xml`, `app_v2/src/vr/res/values-ru/strings.xml`, `app_v2/src/vr/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> `scripts/utils/set-android-string.ps1` only operates on `src/main/res`, so these three files are hand-edited. Add one commented block per file, tagged `S1223`, immediately after the existing `vr_hud_*` block, with the same key order in all three so a later diff stays readable.
>
> Keys, with the EN values:
>
> - `vr_hud_help` - "HELP"
> - `vr_legend_title` - "Controls"
> - `vr_legend_footer` - "Pull the trigger to continue. The HELP button on the panel shows this again."
> - `vr_legend_input_trigger` / `vr_legend_action_click` - "Trigger" / "Press the control the ray points at"
> - `vr_legend_input_trigger_hidden` / `vr_legend_action_summon` - "Trigger, panel hidden" / "Bring the panel back"
> - `vr_legend_input_stick_x` / `vr_legend_action_seek` - "Thumbstick left / right" / "Seek 10 seconds"
> - `vr_legend_input_grip_stick_x` / `vr_legend_action_file_step` - "Grip + thumbstick left / right" / "Previous / next file"
> - `vr_legend_input_stick_y` / `vr_legend_action_zoom` - "Thumbstick up / down" / "Zoom the image"
> - `vr_legend_input_grip` / `vr_legend_action_move_panel` - "Grip, hold" / "Move the panel"
> - `vr_legend_input_ax` / `vr_legend_action_exit` - "A or X" / "Leave the headset view"
> - `vr_legend_input_panel_buttons` / `vr_legend_action_panel_buttons` - "HIDE / EXIT on the panel" / "The same two actions as buttons"
>
> RU and UK carry real translations, not the EN text. `vr_hud_help` must stay short enough for the 300 px button at 48 px bold - check the RU and UK words against that budget rather than assuming.
>
> House style applies to these user-visible strings: `..` never `...`, plain hyphen, Russian `ё` where grammatical.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_legend"` - exit 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_hud_help"` - exit 0.
- `Grep` - each new key matches exactly three times repo-wide (one per locale).
- `.\a.ps1 fkn` passes.

**Status:** `[x]` done

---

### Step 04.2 - Sync the public controls reference

**Files:** `docs/VR_CONTROLS.md`, `docs/VR_CONTROLS_RU.md`, `docs/VR_CONTROLS_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> In "Where things stand today", add one bullet describing the legend: it appears by itself on the first immersive entry, any controller input closes it, and the HELP button on the strip brings it back. Keep it in the same voice as the surrounding bullets.
>
> Two corrections in the same edit, both stale statements this page already carries and both about the surface this ticket is documenting:
>
> - The sentence listing a visible seek bar as "still missing in the headset" is wrong - S1239 shipped it, and the same page's HIDE/EXIT bullet already assumes the strip it lives on.
> - The Troubleshooting entry claiming only the aiming ray and trigger are wired, and that almost everything else exits, contradicts the seek and grip bullets three sections above it.
>
> The "cheatsheet auto-appears for 4 seconds, brought back with a long-press of Y or F1" line stays under the target-design heading, but must not read as describing what ships: the shipped legend has no timer and no Y or F1 binding. Say so where the line sits rather than deleting it.
>
> Apply the same three edits to the RU and UK mirrors. All three files are registered documents; changing one and not the others is the failure this rule exists to prevent.

**Verification:**

- `Grep` - the legend bullet's distinguishing phrase matches once in each of the three files.
- `Grep` - "Still missing in the headset" no longer claims a seek bar in any of the three.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.

**Status:** `[x]` done

---

### Step 04.3 - Capability record and catalog

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.2

**Prompt for developer:**

> Record the capability through `scripts/all_features/add.ps1`, EN only. Area is the VR/immersive one already used by the S0964 and S1232 records - read it off those rows rather than inventing a new area name.
>
> `-FeatFlavors` must be read off the actual gate, never off a sibling record. `src/vr/` is mounted by the `vr` flavor and borrowed by `noLegal`, but whether the immersive player is reachable in `vr` is decided by `BuildConfig.SUPPORT_VR_PLAYER` in `app_v2/build.gradle.kts` - read that block and let it decide the value.
>
> Then run `scripts/catalog_sync.ps1 -Module app_v2` once and set `role` plus `status` for the three new classes with `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `Grep` - `S1223` matches in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Legend*" -Module app_v2` lists all three new classes with a non-`unknown` role.

**Status:** `[x]` done

---

### Step 04.4 - Gates, dev log and device-test handover

**Files:** repository-wide
**Depends on:** Step 04.3

**Prompt for developer:**

> Confirm the three `Timber.d("S1223: ..)` probes from Phases 02 and 03 are present and are the only ones - one per changed flow entry, no per-phase extras, which is what the ticket-log gate checks.
>
> Route closure through the facade: `scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile` for the touched files, then `scripts/spec_catalog/close-and-log.ps1` with `-Status BlockNeedUserTest` and a `-StatusNote` naming what the owner must verify on Quest - first entry shows the legend, a trigger pull closes it without pressing anything, a thumbstick deflection closes it without seeking, the second entry goes straight to the strip, and HELP brings it back.
>
> The tree is always dirty here with other tickets' work, so `-ScopeToFile` is required rather than optional.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` - reports no new findings in the touched files.
- `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -ChangedFiles <every touched .kt> -Gate` - PASS.
- `.\a.ps1 fkn` passes as the final build, with the probes already in the tree.
- `scripts/spec_catalog/select.ps1 -Id S1223 -Format json` shows `BlockNeedUserTest` with a non-empty `statusNote`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `.\a.ps1 fkn` passes.
- [x] `.\a.ps1 fk` passes - proof that nothing leaked into `src/main`.
- [x] Dev log entry added for the change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

None - final phase. The ticket sits in `BlockNeedUserTest` until a Quest 3 session runs the five checks in the status note; `/spec-check` then converts that evidence into `Verified` and removes the three probes.

---

## Rollback Plan

Strings and docs revert independently of the code; reverting them alone leaves the legend rendering with unresolved resource references, so revert Phase 03 and 02 first if the whole ticket is rolled back.
