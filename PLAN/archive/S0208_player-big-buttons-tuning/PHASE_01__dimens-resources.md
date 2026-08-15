# Phase 01 — Dimens Resources

**Strategic spec:** [`../S0208_player-big-buttons-tuning.md`](../S0208_player-big-buttons-tuning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add the three resource dimens that the rest of the refactor reads: unified big-button height, minimum slot width for adaptive density, and big-button top-panel label text size. Resource-only — no Kotlin touched.

---

## Prerequisites

- [ ] Strategic spec §6 research items are Resolved (they are).
- [ ] `app_v2/src/main/res/values/dimens.xml` is reachable and not locked by another phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ 700 |

Landscape parity: `app_v2/src/main/res/values-land/dimens.xml` exists but holds layout-specific landscape overrides, not player big-buttons sizing. New dimens default-only — no `values-land` override needed in this phase. Future override under `values-sw600dp` / `values-sw720dp` (§6.2) is optional and not required for v1.

---

## Steps

### Step 01.1 — Add `player_big_button_height`

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Inside the `<!-- Player Activity -->` block in `values/dimens.xml`, immediately after the `player_cmd_button_size` entry, add a new dimen:
> ```xml
> <dimen name="player_big_button_height">100dp</dimen>
> ```
> This is the unified height for both the top command panel and the bottom playback row when Big Buttons Mode is active (strategic §2 goal 2). Do not replace `player_cmd_button_size` — it stays as the base command button width.

**Verification:**

- Grep — `<dimen name="player_big_button_height">100dp</dimen>` matches exactly once in `app_v2/src/main/res/values/dimens.xml`.
- Grep — `<dimen name="player_cmd_button_size">40dp</dimen>` is still present, untouched.

**Status:** `[ ]` not done

---

### Step 01.2 — Add `player_big_button_min_slot_width`

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add immediately after the dimen from step 01.1:
> ```xml
> <dimen name="player_big_button_min_slot_width">50dp</dimen>
> ```
> Starting value. Rationale: with a 411dp portrait panel this gives `411 / 50 = 8` total slots → matches the current `BIG_BUTTONS_TOP_PANEL_SLOT_COUNT = 8` and satisfies the §6.4 regress floor (≥ 7). A 1240dp wide panel gives `1240 / 50 = 24` slots → clamps to the 10 ceiling (§3.1.1). Final value will be confirmed by device-verify in Phase 03 — adjust this number in place if visual review on 320dp/411dp/600dp/1240dp shows label truncation or wasted space. The XML doc-comment above the entry must say exactly: `Big Buttons Mode: starting value — adjust per S0208 §6.1 / §6.4 device-verify.`

**Verification:**

- Grep — `<dimen name="player_big_button_min_slot_width">50dp</dimen>` matches exactly once.
- Grep — `Big Buttons Mode: starting value` appears once in `values/dimens.xml`.

**Status:** `[ ]` not done

---

### Step 01.3 — Add `player_big_button_top_label_text_size`

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add immediately after the dimen from step 01.2:
> ```xml
> <dimen name="player_big_button_top_label_text_size">10sp</dimen>
> ```
> Sub-icon label text size for Big Buttons Mode top-panel command wrappers. Smaller than the previous hard-coded `11sp` so the label fits in the 15% slice of a 100dp button at 85/15 ratio without truncating the longest uk translations (strategic §3.1.2). Do not introduce a separate dimen for the bottom row — bottom row drops the label (§3.1.2 / §5.1.2).

**Verification:**

- Grep — `<dimen name="player_big_button_top_label_text_size">10sp</dimen>` matches exactly once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Grep` for the three new dimen names returns exactly one match each in `values/dimens.xml`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly). Standard debug must pass: this phase touches only XML and must not break resource references.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/dimens.xml" "S0208" "<msg>"`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.

---

## Handoff Notes to Next Phase

Three new dimen names are now available for Kotlin and layout XML to reference:

- `R.dimen.player_big_button_height` — unified panel height for big-buttons mode.
- `R.dimen.player_big_button_min_slot_width` — divisor for the adaptive slot-count formula.
- `R.dimen.player_big_button_top_label_text_size` — small label under the icon in top-panel wrappers.

Phase 02 reads `player_big_button_min_slot_width`. Phase 03 reads `player_big_button_height` and `player_big_button_top_label_text_size`.

---

## Rollback Plan

Revert the three dimen entries. No data migration, no behaviour change until Phase 02 reads them.
