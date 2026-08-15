# Phase 01 - Unified menu entry

**Strategic spec:** [`../S0681_send-to-menu-resource-picker.md`](../S0681_send-to-menu-resource-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Add a pinned, always-last «Select resource..» row to the single «Send to..» menu source (bottom sheet + overflow submenu), driven by an optional host-supplied `onPickResource` callback. No host wires the callback yet, so the entry stays hidden everywhere until Phases 02-03.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/layout/sheet_send_to.xml` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToBottomSheet.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt` | Modified | ≤ 230 |

> `sheet_send_to.xml` has no `res/layout-land/` counterpart - landscape variant absent, not needed.
> No file here exceeds 500 LOC after edits - no backup step required.

---

## Steps

### Step 01.1 - Add «Select resource..» string (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one trilingual string key `share_to_pick_resource` via a single parity-enforced call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key share_to_pick_resource -En "Select resource.." -Ru "Выбор ресурса.." -Uk "Вибір ресурсу.."`. The label names an in-app copy-to-resource action (it opens the recipient picker), not an external share target. Confirm the EN/RU/UK wording satisfies `docs/COMMUNICATION_POLICY.md` §2 (action-label formula) and §6 (tone checklist) before integration - keep it a short noun-phrase action label, use `..` not `...`.

**Verification:**

- `Grep` - `name="share_to_pick_resource"` matches once in `values/strings.xml`.
- `Grep` - `name="share_to_pick_resource"` matches once in `values-ru/strings.xml`.
- `Grep` - `name="share_to_pick_resource"` matches once in `values-uk/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "share_to_pick_resource"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.2 - Add the pinned row to the bottom-sheet layout

**Files:** `app_v2/src/main/res/layout/sheet_send_to.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Below the existing `rvSendToReceivers` RecyclerView, add a fixed row that visually matches a receiver row (`item_send_to_receiver.xml`: leading icon + label). Give it id `llPickResource`, `android:visibility="gone"` by default, `android:focusable="true"`, `android:clickable="true"`, and a TextView (id `tvPickResourceLabel`) bound to `@string/share_to_pick_resource` with a leading folder/destination glyph (reuse an existing vector such as `@drawable/ic_folder` if present, otherwise `@drawable/ic_share`). Use `?attr/` theme colors only - no hardcoded `#hex`. The row must sit after the RecyclerView so it renders below the full receiver list (after the system-share row).

**Verification:**

- `Grep` - `android:id="@+id/llPickResource"` matches once in `sheet_send_to.xml`.
- `Grep` - `@string/share_to_pick_resource` matches once in `sheet_send_to.xml`.
- `Grep` - `#` color literal does NOT appear on any `android:*Color`/`tint` line in `sheet_send_to.xml` (theme attrs / `@color` only).

**Status:** `[x]` done

---

### Step 01.3 - Render + wire the pinned row in the bottom sheet

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToBottomSheet.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a transient field `var onPickResource: (() -> Unit)? = null` set by `newInstance` the same way `content`/`settings` are (modal sheet, never restored). Extend `newInstance(content, settings)` to `newInstance(content, settings, onPickResource)`. In `onViewCreated`, if `onPickResource != null`, set `llPickResource` visible, set its click listener to invoke the callback then `dismiss()`, and make it the last D-pad stop (its `nextFocusDown`/`nextFocusForward` returns to the list top, list last row `nextFocusDown` points to it). Leave it `gone` when the callback is null. Do not add it to the receiver adapter - it is a fixed row, not a `ShareTarget`.

**Verification:**

- `Grep` - `onPickResource` matches at least twice in `SendToBottomSheet.kt` (field + newInstance param).
- `Grep` - `llPickResource` matches in `SendToBottomSheet.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SendToBottomSheet.kt`.

**Status:** `[x]` done

---

### Step 01.4 - Thread `onPickResource` through the menu manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `onPickResource: (() -> Unit)? = null` (default null - keeps every existing caller compiling) to both `show(activity, content, settings)` and `buildOverflowSubMenu(menu, order, content, settings, activity)`. In `show`: when `onPickResource != null`, always present the bottom sheet (skip the empty-return and the single-receiver direct-dispatch fast paths) and pass the callback into `SendToBottomSheet.newInstance(..)`, so the pinned row is always reachable; when null, keep the existing behavior exactly. In `buildOverflowSubMenu`: after the receivers loop, if `onPickResource != null`, append one final submenu item titled `R.string.share_to_pick_resource` whose click invokes the callback - making it the last item, after the system-share receiver. If both receivers are empty and the callback is null, keep the existing early return.

**Verification:**

- `Grep` - `onPickResource` matches in both `show(` and `buildOverflowSubMenu(` signatures in `SendToMenuManager.kt`.
- `Grep` - `share_to_pick_resource` matches in `SendToMenuManager.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SendToMenuManager.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the changed files via `.\scripts\add_to_dev_log.ps1`.

---

## Step Log

- 2026-06-25 - Step 01.1: Verification 4/4 PASS. `share_to_pick_resource` added EN/RU/UK via set-android-string; parity OK.
- 2026-06-25 - Step 01.2: Verification 3/3 PASS. Pinned `llPickResource` row (ic_folder + `?attr/` colors) added below RecyclerView in `sheet_send_to.xml` (no land variant).
- 2026-06-25 - Step 01.3: Verification 3/3 PASS. `SendToBottomSheet` got transient `onPickResource`, `newInstance` param, visibility + click + dismiss.
- 2026-06-25 - Step 01.4: Verification 3/3 PASS. `SendToMenuManager.show`/`buildOverflowSubMenu` take optional `onPickResource`; sheet always shown when set; pinned item appended last in overflow.
- 2026-06-25 - Phase build: `.\a.ps1 fc` BUILD SUCCESSFUL (37s). Neuroslop gate delta 0.

---

## Handoff Notes to Next Phase

`SendToMenuManager.show` / `buildOverflowSubMenu` now accept an optional `onPickResource` callback; the bottom sheet renders a pinned «Select resource..» row exactly when that callback is non-null. The pinned entry is invisible until a host supplies the callback (Phases 02-03). String key `share_to_pick_resource` exists in all three locales.

---

## Rollback Plan

Revert phase commit(s) - additive optional parameter and a hidden-by-default row; no data migration or default behavior change.
