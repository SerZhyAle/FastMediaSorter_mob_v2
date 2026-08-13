# Phase 07 — Main Screen Integration

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04, Phase 05
**Blocks:** Phase 08
**Steps done:** 4 / 4
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Replace the static `iconRes` lookup in `ResourceAdapter` (both `ResourceViewHolder.bind` and `GridViewHolder.bind`) with `ResourceIconComposer.compose(context, resource)`. Preserve the existing quick-slideshow click behaviour from S0004 — only the visual changes.

---

## Prerequisites

- [ ] Phase 04 ✅ Done.
- [ ] Phase 05 ✅ Done.
- [ ] All existing resources in the dev DB have non-null `iconId` after backfill (verify via Database Inspector before manual smoke test).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 800 |

---

## Steps

### Step 07.1 — Replace list-row icon resolution

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the block in `ResourceViewHolder.bind` that computes `val iconRes = when { .. }` (around line 288–310). Replace the body so the chosen drawable becomes:
>
> ```kotlin
> val composedDrawable = ResourceIconComposer.compose(root.context, resource)
> ivResourceTypeIcon.setImageDrawable(composedDrawable)
> ```
>
> Remove the now-unused `iconRes: Int` local. Keep all surrounding state — `isClickable`, `foreground`, `contentDescription`, the `onIconClick` ripple from S0004 — untouched.

**Verification:**

- `Grep` — `ResourceIconComposer\.compose\(root\.context, resource\)` matches at least once in `ResourceAdapter.kt`.
- `Grep` — `setImageDrawable\(composedDrawable\)` matches at least once.
- `Grep -n "ivResourceTypeIcon\.setImageResource"` returns zero hits in `ResourceViewHolder.bind` (use surrounding context to confirm, since `GridViewHolder.bind` is updated in 07.2).

**Status:** `[x]` done

---

### Step 07.2 — Replace grid-cell icon resolution

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Repeat Step 07.1 for the `GridViewHolder.bind` block (around line 487–510). Same replacement, same comments.

**Verification:**

- `Grep -n "ResourceIconComposer\.compose"` returns exactly two hits in `ResourceAdapter.kt` (list + grid).
- `Grep -n "ivResourceTypeIcon\.setImageResource\("` returns zero hits in `ResourceAdapter.kt`.

**Status:** `[x]` done

---

### Step 07.3 — Preserve quick-slideshow ripple

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Steps 07.1, 07.2

**Prompt for developer:**

> Confirm that `setImageDrawable(composedDrawable)` does not displace `foreground = ContextCompat.getDrawable(.., R.drawable.ripple_icon_quick_slideshow)` from S0004. The S0004 logic checks `isQuickSlideshowEligible(resource)` — if eligible, ripple is applied; otherwise cleared. After this phase, ripple still appears on top of the new composite. Visually verify in `/build` smoke test that the ripple animation is still visible on tap.

**Verification:**

- `Grep` — `R\.drawable\.ripple_icon_quick_slideshow` matches twice in `ResourceAdapter.kt` (untouched from S0004).
- `Grep` — `isQuickSlideshowEligible` matches twice (one per ViewHolder).

**Status:** `[x]` done

---

### Step 07.4 — Build + manual smoke

**Files:** —
**Depends on:** Steps 07.1..07.3

**Prompt for developer:**

> Trigger `/build` (standard debug). Install on a device or emulator. Verify on the main screen:
>
> 1. Existing resources display with the connection-type indicator in the top-left corner and a themed icon centred.
> 2. Predefined virtual resources ("All audio", "All video", "All images", "All documents") show the same icon across re-installs.
> 3. Local resources without a connection type show no badge — only the centred themed icon.
> 4. Tapping a quick-slideshow-eligible icon still launches the slideshow (S0004 regression check).

**Verification:**

- `/build standard debug` exits with status PASS.
- Manual visual inspection of all four cases above (record observations in dev log entry).
- `Grep -n "Log\.d\("` returns zero hits in `ResourceAdapter.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] Dev log entry added for `ResourceAdapter.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] No public-API change here; catalog regen is optional but recommended.

---

## Handoff Notes to Next Phase

The feature is now end-to-end functional. Phase 08 finishes the documentation/catalog work and flips the spec status to `Implemented` so `/spec-check S0034` can verify.

---

## Rollback Plan

Revert phase commit. The previous `iconRes` resolution is captured in git history — single-file revert restores the prior behaviour without touching DB or other modules.
