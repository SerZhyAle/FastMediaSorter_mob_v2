# Phase 02 - Prune shadowed declarations

**Strategic spec:** [`../S1282_landscape-dimens-dead-under-sw-qualifiers.md`](../S1282_landscape-dimens-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Remove the declarations that can no longer win anywhere, so the two landscape-flavoured files stop
promising behaviour they do not deliver. Every removal in this phase is behaviour-neutral by
construction.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the combined buckets already carry the values being removed here.
- [ ] The per-key decision table in [`INDEX.md`](INDEX.md) governs which keys go; do not extend it by eye.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-land/dimens.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/values-w600dp/dimens.xml` | Modified | ≤ 30 |

> Both files are `values-*` resource buckets, not `layout*` - CLAUDE.md Rule 11 does not apply.

---

## Steps

### Step 02.1 - Drop the ten dead keys from values-land

**Files:** `app_v2/src/main/res/values-land/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the ten keys the decision table marks "prune from values-land": `empty_state_padding`,
> `dialog_padding_large`, `player_controls_padding`, `item_padding_vertical`, `welcome_page_padding`,
> `welcome_icon_size`, `welcome_icon_margin_top`, `welcome_title_margin_top`,
> `welcome_title_text_size`, `welcome_description_margin_top`. Each is declared in `values-sw320dp`,
> which matches every device, so the line here has never won and cannot start winning.
> **Keep `padding_xxlarge`** - no `values-sw320dp` declaration exists for it, so on a phone whose
> landscape width stays under 600dp this line is the one in force; removing it would drop the value
> from 16dp to the base 32dp. Keep the whole `settings_*` block, both `player_cmd_padding_*` keys and
> `dialog_file_info_min_width` untouched - no sw bucket declares them. Leave a short comment above
> `padding_xxlarge` recording why it survived the prune while its neighbours did not.

**Verification:**

- `Grep` - `name="empty_state_padding"` returns zero hits in `values-land/dimens.xml`.
- `Grep` - `name="welcome_title_text_size"` returns zero hits in `values-land/dimens.xml`.
- `Grep` - `name="padding_xxlarge">16dp<` still present in `values-land/dimens.xml`.
- `Grep` - `name="dialog_file_info_min_width">560dp<` still present in `values-land/dimens.xml`.
- `Grep` - the file contains exactly 14 `<dimen name=` lines.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS. `values-land/dimens.xml` 24 -> 14 dimens. `padding_xxlarge` kept with the reason comment; the `settings_*` block, both `player_cmd_padding_*` and `dialog_file_info_min_width` untouched.

---

### Step 02.2 - Drop the eleven redundant keys from values-w600dp

**Files:** `app_v2/src/main/res/values-w600dp/dimens.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete all eleven keys the decision table marks "prune from values-w600dp" - the ten from step 02.1
> plus `padding_xxlarge`. Ten of them lose to `values-sw320dp` on every device, exactly as in
> `values-land`. `padding_xxlarge` is the one exception worth understanding: this file does win it on
> a phone in landscape wider than 600dp, but it declares 16dp and `values-land` declares the same
> 16dp, so after the removal the landscape file supplies the identical value and nothing shifts.
> Keep the remaining thirteen keys - Phase 04 decides those. Replace the file's opening comment: it
> currently claims to be about landscape, which is wrong for a width qualifier that also matches a
> tablet in portrait.

**Verification:**

- `Grep` - `name="empty_state_padding"` returns zero hits in `values-w600dp/dimens.xml`.
- `Grep` - `name="padding_xxlarge"` returns zero hits in `values-w600dp/dimens.xml`.
- `Grep` - `name="settings_item_min_height">32dp<` still present in `values-w600dp/dimens.xml`.
- `Grep` - the file contains exactly 13 `<dimen name=` lines.
- Files `values-land/dimens.xml` and `values-w600dp/dimens.xml` no longer hash-identical.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS. `values-w600dp/dimens.xml` 24 -> 13 dimens; SHA256 now `E11A3332..` against land `F6E7AD41..`, so the byte-identical clone recorded in strategic §0.1 is gone. Leading comment rewritten: the qualifier is available width, not orientation.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 dq` BUILD SUCCESSFUL in 9s (`temp/build_debug_20260731_104747.log`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] No layout references a dimension that now resolves nowhere - all 11 pruned keys verified present in `values/dimens.xml`.
- [x] Dev log entry added for every file in "Files Touched" - both via `post-change.ps1`, PASS.
- [x] Phase-boundary audit run - no P0/P1. One P2: step 03.2 named only `values-land/integers.xml` as the expected gate failure while `values-w600dp/integers.xml` shadows the same way; step text corrected in-phase.

---

## Handoff Notes to Next Phase

After this phase the only landscape-flavoured declarations left are ones that actually win somewhere.
That is the precondition Phase 03 needs: the new gate is written against a tree that already passes
it, so its first run proves the fix rather than reporting the backlog.

---

## Rollback Plan

Restore the deleted lines in both files. No consumer changed, so resolution returns to its previous state.
