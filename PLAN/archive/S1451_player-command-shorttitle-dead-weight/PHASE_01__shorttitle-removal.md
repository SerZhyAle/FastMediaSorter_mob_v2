# Phase 01 - shortTitleResId removal

**Strategic spec:** [`../S1451_player-command-shorttitle-dead-weight.md`](../S1451_player-command-shorttitle-dead-weight.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none - only phase
**Steps done:** 6 / 6
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Remove the unread `shortTitleResId` property from `PlayerCommand`, every argument passed to it, the orphaned `big_btn_short_*` string keys in all declared locales, and the analyzer-baseline entries that name them.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (both are, 2026-08-08).
- [ ] `CommandPanelLayoutPlanner.kt` is 526 LOC - take a timestamped backup under `temp/S1451/` before editing (CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 526 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `config/detekt/baseline-app_v2.xml` | Modified | n/a |
| `config/detekt/baseline-app_v2.ids` | Modified | n/a |
| `app_v2/lint-baseline.xml` | Modified | n/a |

---

## Steps

### Step 01.1 - Drop the sixth constructor argument from every `PlayerCommand` entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove the trailing sixth argument from all 36 `PlayerCommand` entries that pass one - the 35 `R.string.big_btn_short_*` arguments plus `STREAM_INFO`, which passes `R.string.stream_info_menu_title` into the same slot. Leave `priority`, `menuItemId`, `barCapable`, `titleResId` and `iconResId` untouched, and preserve the existing `Timber.d("S1365: ..")` probe in the companion object - S1365 is still `BlockNeedUserTest`, so that tag is live, not stale.

**Why:**

Strategic §5 fixes this order: arguments come off before the property, because the reverse order leaves references to deleted resources and fails the resource build instead of the compile, where the error reads worse.

**Verification:**

- `Grep` - `big_btn_short_` returns zero hits in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `R.string.stream_info_menu_title` appears exactly once in the `STREAM_INFO` entry (its `titleResId`), not twice.
- `Grep` - `Timber.d("S1365:` still present exactly once.

**Status:** `[x]` done

---

### Step 01.2 - Remove the `shortTitleResId` property declaration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the `val shortTitleResId: Int = 0` parameter from the `PlayerCommand` constructor together with the comment line above it that describes it as the Big Buttons Mode short label. Leave the enum KDoc's descriptions of the remaining five properties in place.

**Why:**

Strategic §1 states the property misleads the next reader into believing Big Buttons Mode renders short labels, which it does not - the misleading declaration is the defect, so it must go rather than be kept for later use (§5).

**Verification:**

- `Grep` - `shortTitleResId` returns zero hits across the repository outside `PLAN/` and `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

### Step 01.3 - Delete the orphaned `big_btn_short_*` string keys in every locale

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Remove all 22 `big_btn_short_*` keys with `scripts/utils/set-android-string.ps1 -Action remove -Key <key>`, one call per key. The tool sweeps every locale present on disk, so a single call per key covers EN, RU and UK together. Include `big_btn_short_playback_order`, which no enum entry ever referenced. Then run the localization audit over the prefix.

**Why:**

Strategic §2 requires that these keys stop demanding a translation for every new language, and §3.2 requires all three locales lose the key in the same change, because a partial removal fails the string audit on the mismatch.

**Verification:**

- `Grep` - `big_btn_short_` returns zero hits under `app_v2/src/main/res/`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "big_btn_short_"` exits 0.

**Status:** `[x]` done

---

### Step 01.4 - Reconcile both analyzer baselines

**Files:** `config/detekt/baseline-app_v2.xml`, `config/detekt/baseline-app_v2.ids`, `app_v2/lint-baseline.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Drop every baseline entry naming a removed symbol: 32 in the detekt baseline XML, its 32 companions in the `.ids` sidecar, and 2 in the lint baseline. Regenerate rather than hand-edit where the project provides a regeneration path, since a hand-edited detekt baseline is ignored while the daemon holds a stale copy.

**Why:**

Strategic §7 rates stale baseline entries a high-probability risk whose consequence is the gates failing after the deletion, and §11 makes both baselines converging a readiness criterion.

**Verification:**

- `Grep` - `big_btn_short_` returns zero hits in `config/detekt/` and `app_v2/lint-baseline.xml`.
- `Grep` - `shortTitleResId` returns zero hits in either baseline.

**Status:** `[x]` done

---

### Step 01.5 - Re-wrap the enum entries ktlint now flags

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Dropping the sixth argument left many entries split across two lines with several arguments per line, the one shape `ArgumentListWrapping` rejects, and the old baseline signatures no longer match them. Join each such entry onto a single line where it fits the 120-char budget and expand the rest to one argument per line. Where a trailing `// icon replaced asynchronously` comment pushes an otherwise-fitting entry over the budget, lift the comment above the entry and leave a blank line before it, which `SpacingBetweenDeclarationsWithComments` requires.

**Why:**

Strategic §5 chooses deletion over retention, and CLAUDE.md Rule 19 requires the touched file to be detekt-clean rather than re-baselined, so the formatting churn the deletion causes is fixed in the code instead of frozen as new debt.

**Verification:**

- `assert-detekt -ScopeToFile` reports no new finding among the changed files.
- No line in the file exceeds 120 characters.

**Status:** `[x]` done

---

### Step 01.6 - Name the OFFICE_TEXT_SETTINGS priority so both control files converge

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`, `config/detekt/baseline-app_v2.xml`, `config/detekt/baseline-app_v2.ids`
**Depends on:** Step 01.5

**Prompt for developer:**

> The absorption gate fails on `MagicNumber:..OFFICE_TEXT_SETTINGS$393`, which S1406 froze in the detekt baseline without re-seeding the ID snapshot - a pre-existing divergence, since that ID names neither removed symbol and so cannot have been touched by this ticket's pruning. Replace the literal with a named top-level constant the way S0995, S1364 and S1474 did, drop the now-stale baseline entry, and re-seed the snapshot through `assert-detekt-baseline-absorption.ps1 -Update -Reason` rather than by hand, as that file's own header demands.

**Why:**

Strategic §11 makes both analyzer baselines converging a readiness criterion, and the gate blocks closure until they do, so this divergence is inside this ticket's scope even though another ticket introduced it.

**Verification:**

- `assert-detekt-baseline-absorption.ps1 -Module app_v2 -Gate` exits 0.
- The `.ids` header records the re-seed reason.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` passed.
- [x] Resource build passes - the deleted keys leave no dangling `R.string` reference.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed, since none of the removed strings ever reached the screen.
