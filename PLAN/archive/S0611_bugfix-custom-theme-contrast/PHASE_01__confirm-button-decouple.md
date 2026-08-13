# Phase 01 - Decouple the dialog confirm button from success_color

**Strategic spec:** [`../S0611_bugfix-custom-theme-contrast.md`](../S0611_bugfix-custom-theme-contrast.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** none
**Steps done:** 2 / 2

---

## Objective

Give `Widget.FastMediaSorter.Button.DialogConfirm` its own day/night-aware fill+text tokens so the OK button is readable
in dark mode (current night `success_color = #81C784` + white text = ~1.9:1). `success_color` stays unchanged for its
status-indicator consumers. Affects every night dialog (plain Dark + Auto-night + all DARK_* themes).

Exact values: research artifact 01, section "Confirm button".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | n/a (resources) |
| `app_v2/src/main/res/values-night/colors.xml` | Modified | n/a (resources) |
| `app_v2/src/main/res/values/themes.xml` | Modified | n/a (resources) |

---

## Steps

### Step 01.1 - Add the confirm-button tokens (day + night)

**Files:** `values/colors.xml`, `values-night/colors.xml`

**Prompt for developer:**

> In `values/colors.xml` add `confirm_button_bg = #FF2E7D32` and `confirm_button_on = #FFFFFFFF`, grouped near `success_color`
> with a comment that this is the decoupled DialogConfirm fill (S0611). In `values-night/colors.xml` add the night overrides
> `confirm_button_bg = #FF81C784` and `confirm_button_on = #FF0A2E0A`. Do NOT change `success_color` in either bucket.

**Verification:**

- `Grep` - `confirm_button_bg` present in both `values/colors.xml` and `values-night/colors.xml`.
- `Grep` - `confirm_button_on` present in both buckets.
- `Grep` - `success_color` value lines in both buckets are byte-identical to before (unchanged).

**Status:** `[x]` done

---

### Step 01.2 - Repoint the DialogConfirm style to the new tokens

**Files:** `values/themes.xml`

**Prompt for developer:**

> In `Widget.FastMediaSorter.Button.DialogConfirm`, change `backgroundTint` from `@color/success_color` to
> `@color/confirm_button_bg` and `android:textColor` from `@color/white` to `@color/confirm_button_on`. Leave `minHeight`
> and the parent unchanged. Do not touch `DialogDestructive` or `DialogCancel`.

**Verification:**

- `Grep` - `Widget.FastMediaSorter.Button.DialogConfirm` block uses `@color/confirm_button_bg` and `@color/confirm_button_on`.
- `Grep` - no remaining `@color/success_color` inside the `DialogConfirm` style block.
- Neuroslop gate: no hardcoded hex introduced in `themes.xml` (tokens only).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Both steps `[x] done`.
- [ ] `success_color` untouched (status consumers unaffected).
- [ ] `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` passes for touched files (no new hex in layout/themes).

---

## Rollback Plan

Revert the three token edits + the two style attribute swaps. No persistent state.
