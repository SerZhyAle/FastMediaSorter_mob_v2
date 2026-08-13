# Tactical Plan: S1330 - landscape-integers-dead-under-sw-qualifiers

**Strategic spec:** [`../S1330_landscape-integers-dead-under-sw-qualifiers.md`](../S1330_landscape-integers-dead-under-sw-qualifiers.md)
**Research inputs:** none - strategic §0 carries the measurement; three corrections to it are recorded below
**Feature:** Landscape column counts that actually apply
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Resource matching facts this plan relies on

Android resolves qualifiers in a fixed precedence order. The three that matter here rank:
`smallestWidth` (4th) > available width `wNNNdp` (5th) > `orientation` (12th).

The elimination is per resource NAME, not per file: a folder only competes for a key it actually
declares. Consequences used by every phase below:

- A key declared in `values-sw320dp` beats the same key in `values-land` and in `values-w600dp` on
  every device, because `sw320dp` matches every device with `sw >= 320dp`.
- A combined bucket (`values-sw320dp-land`) ties with `values-sw320dp` on smallestWidth and then
  wins on orientation, so it is the only way to restore a landscape value at that threshold.
- A combined bucket is eliminated in the smallestWidth round by any HIGHER threshold that declares
  the same key. `values-sw480dp-land` therefore cannot reach a tablet as long as `values-sw600dp`
  declares the key - and leaks straight to it when `values-sw600dp` does not. This is the trap that
  cost S1282 a phase-boundary audit fix.
- `values-w600dp` means "available width >= 600dp", not "landscape". It matches a tablet in
  **portrait** and it also matches an ordinary phone in landscape, while missing a narrow phone in
  landscape. It is a third, independent axis - not a synonym for either of the other two.

---

## Device classes used in every table below

| Class | Meaning | Example | Matches `w600dp`? |
|-------|---------|---------|:-----------------:|
| `Ph-P` | phone `sw320-479dp`, portrait | 411 x 914dp | no |
| `Ph-Ln` | phone `sw320-479dp`, landscape, narrow | 533 x 320dp | no |
| `Ph-Lw` | phone `sw320-479dp`, landscape, wide | 914 x 411dp | yes |
| `Md-P` | device `sw480-599dp`, portrait | 480 x 800dp | no |
| `Md-L` | device `sw480-599dp`, landscape | 800 x 480dp | yes |
| `Tb-P` | tablet `sw600dp+`, portrait | 800 x 1280dp | yes |
| `Tb-L` | tablet `sw600dp+`, landscape | 1280 x 800dp | yes |

`Ph-Ln` and `Ph-Lw` are split because `values-w600dp` separates them and `values-land` does not.

---

## Measured declaration matrix - every `integers.xml` in the tree (2026-07-31)

`-` = not declared in that bucket. Nine files declare integers; no `values-sw600dp-land` or
`values-sw720dp-land` bucket exists.

| Key | `values` | `sw320dp` | `sw320dp-land` | `sw480dp` | `sw480dp-land` | `sw600dp` | `sw720dp` | `land` | `w600dp` |
|-----|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `grid_column_count` | 3 | 3 | - | 3 | - | 4 | - | 3 | 3 |
| `welcome_feature_grid_columns` | 2 | 2 | - | 3 | - | 4 | 4 | 4 | 4 |
| `grid_column_count_landscape` | 3 | - | - | 2 | - | 5 | - | 6 | 6 |
| `grid_column_count_list` | 1 | 1 | - | - | - | 3 | - | 3 | 3 |
| `resource_grid_column_count` | 2 | 1 | 2 | 1 | 2 | 2 | - | 4 | 4 |
| `destinations_column_count` | 1 | - | - | - | - | 2 | - | 2 | 2 |
| `settings_send_commands_columns` | 1 | - | - | - | - | 2 | - | 2 | - |
| `settings_group_columns` | 1 | - | - | - | - | - | - | 2 | - |
| `statistics_card_span` | 2 | - | - | - | - | - | - | 4 | 4 |

---

## Per-key decision table (input to every phase)

"Winner today" is the bucket that actually resolves on a phone in landscape.

| Key | `land` says | Winner today (phone landscape) | Dead where | Action | Phase |
|-----|---:|---|---|---|:--:|
| `grid_column_count_list` | 3 | `sw320dp` = 1 | `land`, `w600dp` | **delete the key from all five buckets** - zero consumers repo-wide | 01 |
| `grid_column_count` | 3 | `sw320dp` = 3 | `land`, `w600dp` | prune both - the value is identical, restoring changes nothing | 01 |
| `resource_grid_column_count` | 4 | `sw320dp-land` = 2 | `land`, `w600dp` | prune both - combined buckets already own phone landscape | 01 |
| `welcome_feature_grid_columns` | 4 | `sw320dp` = 2 | `land`, `w600dp` | restore per owner answer, then prune both | 02 |
| `grid_column_count_landscape` | 6 | `land`/`w600dp` = 6, but `sw480dp` = 2 in the 480-599 band | nowhere - live below 480dp | restore at the `sw480dp` band per owner answer | 03 |
| `destinations_column_count` | 2 | `land` = 2 (`w600dp` = 2 on wide) | nowhere | leave - live on `Ph-Ln` | - |
| `settings_send_commands_columns` | 2 | `land` = 2 | nowhere | leave - live on every phone landscape | - |
| `settings_group_columns` | 2 | `land` = 2 | nowhere | leave - no sw bucket declares it | - |
| `statistics_card_span` | 4 | `land` = 4 (`w600dp` = 4 on wide) | nowhere | decide its `w600dp` copy - it is the only key that changes what a tablet sees in portrait | 04 |

Three rows carry a reason that is not "the sw value wins":

- `grid_column_count_list` is read by nothing. It is declared in five buckets and referenced by no
  Kotlin, no layout and no `getIdentifier` call; the base file files it under the comment "Default
  values for missing resources (lint fix)". Restoring a landscape value for a key nobody reads
  would be inventing behaviour, so it is deleted outright (CLAUDE.md Rule 20, dead-weight hygiene).
- `grid_column_count` declares the same 3 in `values`, `values-sw320dp`, `values-sw480dp`,
  `values-land` and `values-w600dp`. aapt2 has already deduplicated four of those five out of the
  shipped table - proof the prune cannot move a pixel.
- `grid_column_count_landscape` is the one key the gate cannot see. `values-sw320dp` does not
  declare it, so the gate's narrow rule never fires; it dies only inside the `sw480-599dp` band.

---

## Before / after resolution per device class

Only keys this plan changes appear. **Bold** = a value that moves.

### `grid_column_count_list` (phase 01 - deleted outright)

| | `Ph-P` | `Ph-Ln` | `Ph-Lw` | `Md-P` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 1 | 1 | 1 | 1 | 3 | 3 | 3 |
| after | n/a | n/a | n/a | n/a | n/a | n/a | n/a |

No consumer reads the key, so "n/a" is the whole behavioural story. `Md-L` resolves 3 today via
`values-w600dp` because no sw bucket declares the key at 480.

### `grid_column_count` (phase 01 - pruned from `land` + `w600dp`)

| | `Ph-P` | `Ph-Ln` | `Ph-Lw` | `Md-P` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 3 | 3 | 3 | 3 | 3 | 4 | 4 |
| after | 3 | 3 | 3 | 3 | 3 | 4 | 4 |

### `resource_grid_column_count` (phase 01 - pruned from `land` + `w600dp`)

| | `Ph-P` | `Ph-Ln` | `Ph-Lw` | `Md-P` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 1 | 2 | 2 | 1 | 2 | 2 | 2 |
| after | 1 | 2 | 2 | 1 | 2 | 2 | 2 |

Phase 01 moves nothing on any device class. That is the point of doing it first.

### `welcome_feature_grid_columns` (phase 02 - **decided 2026-08-02: outcome D**)

| | `Ph-P` | `Ph-Ln` | `Ph-Lw` | `Md-P` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 2 | 2 | 2 | 3 | 3 | 4 | 4 |
| after - **outcome D (`sw320dp-land` = 3) - CHOSEN** | 2 | **3** | **3** | 3 | 3 | 4 | 4 |
| after - outcome A (`sw320dp-land` = 4) - rejected | 2 | **4** | **4** | 3 | 3 | 4 | 4 |
| after - outcome B (both combined buckets = 4) - rejected | 2 | **4** | **4** | 3 | **4** | 4 | 4 |
| after - outcome C (accept, prune only) - rejected | 2 | 2 | 2 | 3 | 3 | 4 | 4 |

**Outcome D - why 3 and not the 4 this plan first recommended.** The recommendation of outcome A
assumed the grid spans the screen width in landscape. It does not. `layout-land/page_welcome_enhanced.xml`
caps the content block at `welcome_content_max_width`, which is **400dp** on every `sw320-479dp` phone
(`values-sw320dp/dimens.xml`), and inside that 400dp the hero icon column and its margin come first.
Measured budget for the grid on a phone in landscape:

- 400dp content cap, minus 2 x 16dp `welcome_page_padding` (`values-sw320dp-land`) = 368dp
- minus 31dp `welcome_icon_size` and 12dp `margin_large` (`values-sw320dp`) = **~325dp**

Against that budget, and with `welcome_feature_card_margin` = 3dp per side:

- 3 columns - cell ~108dp, tile ~102dp, ~90dp of label width at 10sp. Six tiles = two even rows.
- 4 columns - cell ~81dp, tile ~75dp, ~63dp of label width at 10sp, and a ragged 4 + 2 layout.

The tile list in `WelcomeActivity` is authored in two rows of three and says so in its own comments
("Row 1 in 3-col grid" / "Row 2 in 3-col grid"). Outcome D also lands the phone-landscape value on the
same 3 that `Md-P` / `Md-L` already resolve, so the key reads as a clean size progression - phone 2,
phone-in-landscape and mid 3, tablet 4 - which is the S1282 §6 principle applied to a column count.
Landscape is wider only in the axis this layout does not spend on the grid, so the outcome-A
assumption never held.

**Tablet-leak check.** `values-sw600dp` declares this key (4) and `values-sw720dp` declares it (4),
so a `sw600dp+` device eliminates every `sw320dp*` and `sw480dp*` bucket in the smallestWidth round
before orientation is ever consulted. `Tb-P` and `Tb-L` cannot move under any outcome. This is the
exact check S1282 skipped for `padding_xxlarge` - there the tablet buckets were silent on the key
and the phone value leaked.

### `grid_column_count_landscape` (phase 03 - owner answer decides)

| | `Ph-P` | `Ph-Ln` | `Ph-Lw` | `Md-P` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 3 | 6 | 6 | 2 | 2 | 5 | 5 |
| after - restore (`sw480dp-land` = 6) | 3 | 6 | 6 | 2 | **6** | 5 | 5 |
| after - accept | 3 | 6 | 6 | 2 | 2 | 5 | 5 |

**Tablet-leak check.** `values-sw600dp` declares this key (5), so `values-sw480dp-land` is
eliminated at `sw600dp+`. `Tb-L` stays 5. `Md-P` stays 2 because a combined `-land` bucket does not
apply in portrait - a square 480x480 screen reports portrait and keeps its 2 columns, which is what
`values-sw480dp`'s own header ("480x480 SCREEN CONFIGURATION") was written for.

**Read-configuration caveat.** The table above is what the resource system *resolves*. The only
consumer, `MainLayoutChromeManager.updateLayoutManagerForScreenSize()`, reads this key only when
`Configuration.isWideLayout()` is true - landscape OR available width >= 600dp
(`core/orientation/WideLayout.kt`), otherwise it reads `grid_column_count`. So the `Ph-P` and `Md-P`
cells resolve but are never read, and `Tb-P` *is* read despite being portrait. Phase 03 works from
the read set, not the resolve set.

### `statistics_card_span` (phase 04 - owner answer decides)

| | `Ph-P` | `Ph-Ln` | `Ph-Lw` | `Md-P` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|---:|---:|
| before | 2 | 4 | 4 | 2 | 4 | 4 | 4 |
| after - drop the `w600dp` copy | 2 | 4 | 4 | 2 | 4 | **2** | 4 |
| after - keep and document | 2 | 4 | 4 | 2 | 4 | 4 | 4 |

`Tb-P` is the only cell that can move, which is why phase 04 is one key wide rather than thirteen.

---

## Evidence from the packaged artifact (2026-07-31)

`aapt2 dump resources` on the built standard-debug APK, kept at `temp/S1282/apk-resource-table.txt`.
Every `integers.xml` in the tree predates that dump, so it is authoritative for this ticket.

```text
integer/grid_column_count              () 3  (sw600dp) 4
integer/grid_column_count_landscape    () 3  (w600dp) 6  (sw480dp) 2  (sw600dp) 5  (land) 6
integer/grid_column_count_list         () 1  (w600dp) 3  (sw320dp) 1  (sw600dp) 3  (land) 3
integer/resource_grid_column_count     () 2  (w600dp) 4  (sw320dp) 1  (sw480dp) 1  (sw600dp) 2
                                       (land) 4  (sw320dp-land) 2  (sw480dp-land) 2
integer/welcome_feature_grid_columns   () 2  (w600dp) 4  (sw320dp) 2  (sw480dp) 3  (sw600dp) 4  (land) 4
integer/statistics_card_span           () 2  (w600dp) 4  (land) 4
```

What each line proves:

- `grid_column_count` ships with two configs although five files declare it. aapt2 deduplicates a
  value that equals what the device would resolve without it, so `sw320dp`, `sw480dp`, `land` and
  `w600dp` all collapsed into the default. Phase 01's prune of that key is provably inert.
- `grid_column_count_list` keeps its `(sw320dp) 1` entry even though it equals the default. It
  survived deduplication precisely because removing it would let `(land) 3` through - the shadowing
  is visible in the artifact as a surviving redundant-looking entry.
- `welcome_feature_grid_columns` ships `(sw320dp) 2` alongside `(land) 4`: on every phone the 2
  wins and the 4 is unreachable. `(sw720dp) 4` is absent from the dump although the file declares
  it - deduplicated against `(sw600dp) 4`. **Do not read a missing dump entry as a missing
  declaration**; check the source bucket.
- `statistics_card_span` ships `(w600dp) 4` against a base of 2, which is the phase 04 question
  standing in the artifact: a tablet in portrait matches `w600dp` and gets 4.

---

## Corrections to strategic §0

Recorded here rather than in the strategic spec, which this plan does not edit.

1. §0 lists `settings_send_commands_columns` and `destinations_column_count` among the keys "not
   declared in any sw bucket". Both ARE declared in `values-sw600dp` (2 each). No behaviour changes
   - the sw600dp value matches what `values-land` declares - so both stay untouched, but the "live,
   unshadowed" reason is wrong for them. Their real reason is "shadowed only at 600dp, and by an
   equal value".
2. §0 measures `values-land/integers.xml` only and never mentions `values-w600dp/integers.xml`,
   which exists and declares seven of the same keys. It is a near-copy of the landscape file minus
   `settings_send_commands_columns` and `settings_group_columns` - not byte-identical, unlike the
   `dimens.xml` pair S1282 §0.1 found. Four of the eight baselined gate entries live in it.
3. §0 calls `values-land/bools.xml` healthy because no key is declared in an sw bucket.
   `values-sw600dp/bools.xml` declares `is_resource_actions_inline` (true), the same value
   `values-land` declares. Harmless, and bools stay a §2 non-goal, but "no key in any sw bucket" is
   not accurate.

None of the three changes what this plan does. They matter because a future reader would otherwise
trust §0's "live" list.

### Strategic sections this plan could not draw on

`§1 Проблема`, `§2 Цели`, `§4 Контекст`, `§5 Предлагаемый подход` and `§11 Критерии готовности` are
still unfilled template placeholders in the strategic spec. Only `§0`, `§2 Non-goals`, `§3`, `§6`,
`§7`, `§8`, `§9` and `§10` carry content. This plan is therefore built from §0's measurement plus the
independent measurement above, and it substitutes an explicit mechanical completion signal - the
shadowing baseline reaching zero entries - for the missing §11. If §11 is later filled in, re-check
this plan's Completion Gate against it rather than assuming the two agree.

---

## Pre-Implementation Blockers

All three answered 2026-08-02 by `/spec-quiz`; the decisions are recorded in strategic §6 and in the
"Quiz decisions" block there. No blocker is left - phases 02, 03 and 04 are executable.

- [x] **Owner decision:** strategic §6 item 1 - for `welcome_feature_grid_columns`, restore the
      landscape 4 on phones or accept the sw value? **Answer: outcome D - restore, but as 3, not 4.**
      Outcomes A / B / C as written are all rejected; see "Outcome D" below the before/after table.
      Unblocks **Phase 02**.
- [x] **Owner decision:** strategic §6 items 1 and 2 - for `grid_column_count_landscape`, restore 6
      in the `sw480-599dp` band or accept 2? **Answer: restore 6.** Unblocks **Phase 03**.
- [x] **Owner decision:** new, raised by this plan - `values-w600dp/integers.xml` hands
      `statistics_card_span` = 4 to a tablet in **portrait**. Drop it back to the base 2, or keep
      and document? **Answer: keep and document.** Unblocks **Phase 04**.

**Phase 01 was blocked by none of them** and retires six of the eight baselined entries on its own.

---

## Criteria not owned by a phase

The visual judgement this ticket ends in cannot be a step - it is an observation. It is discharged by
the on-device check (`BlockNeedUserTest` -> `/spec-test-device`), and the screens to look at are the
ones the changed keys actually reach:

- `welcome_feature_grid_columns` - welcome wizard page 1 only, the "Powerful Extras" tile grid
  (`WelcomePagerAdapter.populateFeatureGrid`). Six tiles in `standard`; the grid sits inside the
  landscape page's scrolling right column, so a wrong column count costs scrolling, not clipping.
- `grid_column_count_landscape` - Main screen resource list in compact grid mode
  (`MainLayoutChromeManager.updateLayoutManagerForScreenSize`), and only on a `sw480-599dp` device.
- `statistics_card_span` - Statistics screen summary cards, tablet portrait only.

---

## Completion Gate

- [x] All phases show ✅ Done, or phases 02 / 03 / 04 are ⛔ Blocked on the owner decisions above
      with every other phase ✅.
- [x] `scripts/quality/qualifier-shadowing-baseline.txt` holds **zero** data lines - all eight
      entries retired. This is the ticket's mechanical completion signal.
- [x] `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0 reporting
      `0 baselined`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required: public API unchanged.
- [x] `/spec-check S1330` returns `Verified`.
- [x] Strategic spec advanced to `Verified` through the full audit.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dead-column-declarations | - | ✅ Done | 4/4 | [PHASE_01__dead-column-declarations.md](PHASE_01__dead-column-declarations.md) |
| 02 | phone-landscape-columns | 01 | ✅ Done | 2/2 | [PHASE_02__phone-landscape-columns.md](PHASE_02__phone-landscape-columns.md) |
| 03 | sw480-landscape-band | 02 | ✅ Done | 1/1 | [PHASE_03__sw480-landscape-band.md](PHASE_03__sw480-landscape-band.md) |
| 04 | width-copy-portrait | 02 | ✅ Done | 1/1 | [PHASE_04__width-copy-portrait.md](PHASE_04__width-copy-portrait.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1330`.

---

## Blockers Log

- 2026-07-31 - Phases 02, 03 and 04 blocked from the start on three owner decisions listed under
  Pre-Implementation Blockers. Phase 01 is unblocked and clears six of eight baseline entries.
- 2026-08-02 - All three answered via `/spec-quiz`. No blocker remains; every phase is executable.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-02 - `/spec-quiz` recorded the three owner decisions. Phase 02 gains outcome D (3 columns,
  not 4) after re-measuring the landscape grid budget against the content-width cap; phases 03 and 04
  keep their recommended outcomes and lose their alternatives.
