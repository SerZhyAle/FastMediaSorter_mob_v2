# Phase 02 - Label wiring

**Strategic spec:** [`../S1365_image-player-draw-edit-distinction.md`](../S1365_image-player-draw-edit-distinction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Point every place that renders the draw / correct / edit-text labels at the Phase 01 keys, resolving the `EDIT` label per media type from one function instead of three copies of the same conditional, and delete the key left orphaned by the move.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `menu_edit_adjust` and `menu_edit_file_text` resolve in all three locales.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 910 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 330 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | ≤ 3 |
| `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/layout/activity_standalone_text.xml` | Modified | ≤ 1 |
| `app_v2/src/main/res/layout-land/activity_standalone_text.xml` | Modified | ≤ 1 |
| `app_v2/src/main/res/layout/activity_standalone_document.xml` | Modified | ≤ 1 |
| `app_v2/src/main/res/layout-land/activity_standalone_document.xml` | Modified | ≤ 1 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1 |

> Two files here are over the 500-LOC threshold and each needs a timestamped backup into `temp/S1365/` before it is edited (CLAUDE.md Rule 5): `CommandPanelLayoutPlanner.kt` (501) in Step 02.1 and `CommandPanelController.kt` (909) in Step 02.2. Check any other file in this table with `wc -l` before editing and back it up too if it crosses 500.
>
> Every `res/layout/*.xml` edit below lists its `res/layout-land/` counterpart (CLAUDE.md Rule 11). All of them exist; none is portrait-only.

---

## Steps

### Step 02.1 - Resolve the EDIT label per media type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up the file to `temp/S1365/CommandPanelLayoutPlanner.<timestamp>.kt` first - it is over 500 LOC.
>
> Add a function to a companion object on the `PlayerCommand` enum (create the companion if the enum has none):
>
> ```kotlin
> fun editTitleResFor(type: MediaType?): Int = when (type) {
>     MediaType.VIDEO, MediaType.AUDIO -> R.string.control
>     MediaType.PDF -> R.string.pdf_edit_title
>     else -> R.string.menu_edit_adjust
> }
> ```
>
> Give it a KDoc line stating why one static title cannot serve this command. Then change the two enum entries: `EDIT`'s `titleResId` from `R.string.edit` to `R.string.menu_edit_adjust`, and `EDIT_TEXT`'s from `R.string.edit` to `R.string.menu_edit_file_text`. Leave both `shortTitleResId` arguments alone - they belong to S1451.

**Why:**

Strategic §4.1 establishes that `EDIT` dispatches to a playback-control dialog for video and audio, a PDF export sheet for PDF, and the image correction dialog only for stills, so a single static title on the enum entry is wrong for two of the three cases; §11 criterion 6 makes that explicit as a done criterion.

**Verification:**

- `Glob` - `temp/S1365/CommandPanelLayoutPlanner.*.kt` matches at least one file.
- `Grep` - `fun editTitleResFor` matches exactly once in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `R.string.edit,` returns zero hits in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `R.string.menu_edit_adjust` and `R.string.menu_edit_file_text` each match in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 02.2 - Use the resolver for the overflow menu title

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Back up the file to `temp/S1365/CommandPanelController.<timestamp>.kt` first - it is 909 LOC.
>
> In the overflow-menu build loop, replace the inline `if (cmd == PlayerCommand.EDIT && isVideo) R.string.control else cmd.titleResId` branch with a call to `PlayerCommand.editTitleResFor(currentFile.type)` for the `EDIT` command, keeping `cmd.titleResId` for every other command. Remove the now-unused `isVideo` local if nothing else in the function reads it.

**Why:**

Strategic §4.1 records that the same video-only override is written out three times across two files, so the next label change would otherwise have to be repeated in three places and would silently diverge in whichever one was missed.

**Verification:**

- `Glob` - `temp/S1365/CommandPanelController.*.kt` matches at least one file.
- `Grep` - `editTitleResFor` matches in `CommandPanelController.kt`.
- `Grep` - `R.string.control` returns zero hits in `CommandPanelController.kt`.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 02.3 - Use the resolver for the command-bar content descriptions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Replace both `if (isVideo) R.string.control else R.string.edit` expressions - the one feeding `editLabel` and `updateBigButtonsTopPanelContentDescriptions`, and the one setting `btnEditCmd.contentDescription` on the regular path - with `PlayerCommand.editTitleResFor(currentFile.type)`. Leave the unconditional `R.string.control` inside the live-video-stream branch as it is: a stream is video by definition and that line carries no conditional to deduplicate.

**Why:**

Strategic §11 criterion 2 requires the correction command to read «Коррекция» on an image, and the button's content description is the label a TalkBack user hears for that same command, so leaving it on `R.string.edit` would satisfy the criterion by sight only.

**Verification:**

- `Grep` - `editTitleResFor` matches exactly twice in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - `R.string.edit\b` returns zero hits in that file.
- `Grep` - `R.string.control` matches exactly once in that file (the live-stream branch).
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 02.4 - Retitle the menu XML declarations

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`, `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `overflow_menu_player.xml` set `menu_edit`'s `android:title` to `@string/menu_edit_adjust` and `menu_edit_text`'s to `@string/menu_edit_file_text`. In `overflow_menu_standalone_player.xml` set `menu_edit_image`'s `android:title` to `@string/menu_edit_adjust` - that item opens the same `ImageEditDialog`. Leave `menu_draw_overlay` in both files pointing at its unchanged key.

**Why:**

Strategic §11 criterion 4 requires the embedded player and the separate window to show the same title for the same command, and §3 records that `overflow_menu_player.xml` is never inflated but must still be corrected so it does not mislead the next reader.

**Verification:**

- `Grep` - `@string/edit"` returns zero hits in `overflow_menu_player.xml` and `overflow_menu_standalone_player.xml`. Scoped to those two files deliberately: `resource_item_actions.xml` is the Browse menu that strategic §11 criterion 3 requires to keep `@string/edit`, so a repo-wide predicate here would demand the opposite of the spec.
- `Grep` - `@string/menu_edit_adjust` matches once in `overflow_menu_player.xml` and once in `overflow_menu_standalone_player.xml`.
- `Grep` - `@string/menu_edit_file_text` matches once in `overflow_menu_player.xml`.

**Status:** `[x]` done

---

### Step 02.5 - Retarget the layout content descriptions and drop the orphaned key

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`, `app_v2/src/main/res/layout-land/activity_player_unified.xml`, `app_v2/src/main/res/layout/activity_standalone_text.xml`, `app_v2/src/main/res/layout-land/activity_standalone_text.xml`, `app_v2/src/main/res/layout/activity_standalone_document.xml`, `app_v2/src/main/res/layout-land/activity_standalone_document.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.4

**Prompt for developer:**

> Set every `btnEditTextCmd` content description to `@string/menu_edit_file_text` in all six layouts, including the two landscape variants that currently read `@null`. Set `btnEditCmd`'s content description to `@string/menu_edit_adjust` in both `activity_player_unified.xml` variants - portrait currently reads `@string/edit_image`, landscape reads `@null`. Change no other attribute: no position, size, or visibility edit belongs to this ticket.
>
> Then remove the key left with no consumers:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key edit_image
> ```

**Why:**

Strategic §5.1 states that `edit_image` has exactly one consumer and is folded into `menu_edit_adjust` rather than left behind, which CLAUDE.md Rule 20 requires; the landscape variants are included because Rule 11 forbids a portrait-only edit where the landscape counterpart exists.

**Verification:**

- `Grep` - `@string/edit_image` returns zero hits across `app_v2/src/`.
- `Grep` - `name="edit_image"` returns zero hits across `app_v2/src/main/res/values*/`.
- `Grep` - `btnEditTextCmd` co-occurring with `@null` returns zero hits across `app_v2/src/main/res/layout*/`.
- `Grep` - `@string/menu_edit_file_text` matches in all six layout files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_edit"` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 (code + resources, 46s).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep` - `R.string.edit\b` returns zero hits across `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/`.
- [x] Dev log entry added for the change via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Step Log

- 2026-08-07 - Backups taken before any edit: `temp/S1365/CommandPanelLayoutPlanner.20260807-0101.kt`, `temp/S1365/CommandPanelController.20260807-0101.kt`. LOC survey of all 11 targets confirmed only `CommandPanelController.kt` (817) crosses 500; the planner measures 457, not the 501 the catalog reported.
- 2026-08-07 - Step 02.1 done. `editTitleResFor` added to a new `PlayerCommand` companion; `EDIT` retitled to `menu_edit_adjust`, `EDIT_TEXT` to `menu_edit_file_text`. Predicates: `fun editTitleResFor` ×1, `R.string.edit,` ×0, `Log.d(` ×0, backup present.
- 2026-08-07 - Step 02.2 done. Overflow title now calls the resolver; the `isVideo` local it existed to feed was removed (zero remaining references in the file). `R.string.control` ×0, `Log.d(` ×0.
- 2026-08-07 - Step 02.3 done. Both conditionals replaced (`editTitleResFor` ×2); `R.string.edit` ×0; the live-stream `R.string.control` left in place (×1, unconditional, nothing to deduplicate). The edit orphaned `applyBigButtonsLayout`'s `isVideo` parameter, so it and its call-site argument were removed per Rule 20.
- 2026-08-07 - Step 02.4 done, after correcting a defect in this plan. The written predicate demanded zero `@string/edit"` across all of `res/menu/`, which `resource_item_actions.xml` (Browse) legitimately fails - strategic §11 criterion 3 requires that file to keep `@string/edit`. Predicate rescoped to the two player menu files and the reason recorded inline; both now read 0.
- 2026-08-07 - Step 02.5 done. Six layouts repointed (including the two landscape variants that read `@null`, which were an accessibility gap, not just an inconsistency); `edit_image` removed with the tool reporting `Code references: none`. Predicates: `edit_image` refs ×0, `name="edit_image"` ×0, `btnEditTextCmd` + `@null` ×0, `menu_edit_file_text` present in all six, parity exit 0.
- 2026-08-07 - Debug probe tags inserted before the build (2): `CommandPanelLayoutPlanner.kt:252` logs the resolved label per media type, `CommandPanelController.kt:588` logs the media type each time the overflow menu opens. Both are `Timber.d("S1365: …")` and are removed when the ticket leaves `BlockNeedUserTest`.
- 2026-08-07 - Phase Done Criteria: `.\a.ps1 fc` exit 0 in 46s (single build validating code + tags); `catalog_sync.ps1 -Module app_v2` exit 0, 2529 records.
- 2026-08-07 - Phase-boundary audit. Layer 1: no business logic entered the UI layer (the new function is a pure resource-id mapping), all three files far under budget (466 / 818 / 338), KDoc states why not what, two dead items removed rather than left. Layers 2-3: no coroutine, listener, or lifecycle surface touched; the function retains nothing. Layer 4: Room untouched. One P3 noted and accepted: the probe in `editTitleResFor` fires once per availability update, which runs on file switch and panel toggle rather than per frame, and sits in a path that already logs `updateUI[..]` at the same density. No P0/P1.
- 2026-08-07 - UI phase gate (S1338): placement decision on record in `INDEX.md` "UI decision record" (owner wording ruling quoted from strategic §6; placement unchanged by construction, regrouping is S1364). Screenshot **deferred (no device)** - `device-ready.ps1` reported `no-device` at session start and this phase's own Done Criteria do not demand a shot. The visual check is carried by the `BlockNeedUserTest` device-test note.

---

## Handoff Notes to Next Phase

The EDIT label has exactly one resolution point, `PlayerCommand.editTitleResFor`. Any future per-type label lives there, not in the call sites. `@string/edit` is untouched and still serves Browse and scheduled operations.

---

## Rollback Plan

Revert the phase commit. No data migration; the only user-facing surface changed is menu and content-description text.
