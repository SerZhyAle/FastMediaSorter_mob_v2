# Phase 04 - Compact row and state indicator

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Rebuild the shared permission row into two lines with a state indicator and no separate status text line, so both screens fit roughly twice as many items and the state is readable without colour.

---

## Prerequisites

- [x] Phase 02 and Phase 03 are ✅ Done.
- [x] Owner placement contract in strategic §3.3 is the layout being built - indicator and title on line one, description on line two, action button on the right at vertical centre, status text line deleted.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_perm_state_granted.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_perm_state_missing.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_perm_state_blocked.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values/colors.xml` | Modified | ≤ 5 added |
| `app_v2/src/main/res/layout/item_permission_entry.xml` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt` | Modified | ≤ 150 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | ≤ 8 keys |

> **Landscape parity.** `item_permission_entry.xml` has no `res/layout-land` counterpart, and none exists in any other configuration bucket - verified across `layout`, `layout-land`, `layout-sw480dp`, `layout-sw720dp`, `layout-w600dp`. Landscape variant absent - not needed. The two host layouts do have landscape counterparts and are deliberately not touched by this phase.

---

## Steps

### Step 04.1 - Add the state indicator assets

**Files:** `app_v2/src/main/res/drawable/ic_perm_state_granted.xml`, `ic_perm_state_missing.xml`, `ic_perm_state_blocked.xml`, `app_v2/src/main/res/values/colors.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three 24dp vector drawables with a single white `fillColor`, following the shape-encodes-meaning pattern already used by the stream status icons: a filled circle with a check for granted, a hollow ring for not granted, a padlock for blocked. Add the three tint colours to `colors.xml` next to the existing `stream_status_*` entries. Do not add a fourth asset for "not required" - the research artifact records that state never reaches a row; render it by hiding the indicator.

**Why:**

Strategic §3.2 requires the state to be distinguishable by shape or glyph rather than colour alone, so the row stays readable for a colour-blind user and on a greyscale screenshot.

**Verification:**

- `Glob` - all three drawable files exist.
- `Grep` - each contains exactly one `android:fillColor`.
- `Grep` - three new colour names present in `values/colors.xml`.

**Status:** `[x]` done

---

### Step 04.2 - Rebuild the row layout

**Files:** `app_v2/src/main/res/layout/item_permission_entry.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the three-block vertical stack with a horizontal root: the state `ImageView` first, then a vertical text column holding the title on line one and the description on line two, then the action button pinned right and vertically centred. Delete `tv_perm_entry_status` and the horizontal wrapper that held it. Keep the ids `tv_perm_entry_title`, `tv_perm_entry_desc` and `btn_perm_action` so the adapter's bindings survive, and give the indicator the id `iv_perm_state`. Constrain the description to a single line with end ellipsis. Use `?attr` or `@color` for every colour - no literal hex.

**Why:**

Strategic §5.1 states the item is rebuilt into two lines with the status line removed, and §1 records that this stack is what makes one item about 137dp tall so only four or five fit on a phone screen.

**Verification:**

- `Grep` - `tv_perm_entry_status` returns zero hits across `app_v2/src`.
- `Grep` - `iv_perm_state`, `tv_perm_entry_title`, `tv_perm_entry_desc`, `btn_perm_action` all present in the layout.
- `Grep` - `="#` returns zero hits in this file.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 04.3 - Add the indicator content descriptions

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one content-description string per rendered state - granted, not granted, blocked - in EN, RU and UK with a single lockstep call to `scripts/utils/set-android-string.ps1 -Action add`. Phrase each as what the state means to the user, checked against `docs/COMMUNICATION_POLICY.md` §2 for the message formula and §6 for tone.

**Why:**

Strategic §3.2 requires the state to be announced to TalkBack, and an indicator carrying only a shape is silent without a content description.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_state_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 04.4 - Render the indicator and drop the status text

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt`
**Depends on:** Step 04.2, Step 04.3

**Prompt for developer:**

> In `bind()`, delete the status-label block that wrote `perm_current_status` into the removed view. Choose icon, tint and content description together per state, the way the stream status binder does, and hide the indicator for `NOT_APPLICABLE`. Keep the button label per state, with `NOT_YET_REQUESTED` labelled as a grant action rather than a settings action. Keep the description view's existing show-or-hide rule for entries without a description.

**Why:**

Strategic §5.1 states the indicator and the button label take over the meaning the deleted status line carried, and ADR-3 records that the line was removed because it duplicated the button and cost about a quarter of the item height.

**Verification:**

- `Grep` - `perm_current_status` returns zero hits across `app_v2/src`.
- `Grep` - `iv_perm_state` present in the adapter.
- `Grep` - `contentDescription` present in the adapter.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 04.5 - Remove the strings the deleted line used

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.4

**Prompt for developer:**

> Remove `perm_current_status` and every `perm_status_*` key in all three locales with `scripts/utils/set-android-string.ps1 -Action remove`, after confirming by grep that no Kotlin or XML file still references them. Do not touch the `perm_action_*` keys - the button still uses them.

**Why:**

CLAUDE.md Rule 20 requires the string keys orphaned by a deletion to go in the same change rather than accumulate as dead weight.

**Verification:**

- `Grep` - `perm_status_` and `perm_current_status` return zero hits across `app_v2/src`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The row is two lines and the description is clamped to one of them with an ellipsis, so any description longer than that line is now visibly truncated. Phase 05 rewrites the texts to fit.

---

## Rollback Plan

Revert the phase commit. The layout, the adapter and the three drawables revert together; no persisted data is involved.
