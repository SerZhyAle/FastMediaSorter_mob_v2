# Tactical Plan: S1282 - landscape-dimens-dead-under-sw-qualifiers

**Strategic spec:** [`../S1282_landscape-dimens-dead-under-sw-qualifiers.md`](../S1282_landscape-dimens-dead-under-sw-qualifiers.md)
**Research inputs:** none - strategic §0 and §0.1 carry the full measurement
**Feature:** Landscape dimension buckets that actually apply
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 4 / 5 done - 04 blocked on the owner decision below
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Resource matching facts this plan relies on

Android resolves qualifiers in a fixed precedence order. The three that matter here rank:
`smallestWidth` (4th) > available width `wNNNdp` (5th) > `orientation` (12th).

Consequences used by every phase below:

- A key declared in `values-sw320dp` beats the same key in `values-land` and in `values-w600dp` on
  every device, because `sw320dp` matches every device with `sw >= 320dp`.
- A combined bucket (`values-sw320dp-land`) ties with `values-sw320dp` on smallestWidth and then
  wins on orientation, so it is the only way to restore a landscape value at that threshold.
- `values-w600dp` means "available width >= 600dp", not "landscape". It also matches a tablet in
  **portrait**, which is why the copy there has an effect the landscape file never had.

---

## Per-key decision table (input to Phase 01 and Phase 02)

`land` = value declared in `values-land/dimens.xml`. `sw320` / `sw480` = value that actually wins on
a phone in landscape today. Restore column = target combined bucket.

| Key | land | sw320 | sw480 | Restore to sw320dp-land | Restore to sw480dp-land | Prune from values-land | Prune from values-w600dp |
|-----|-----:|------:|------:|:---:|:---:|:---:|:---:|
| `empty_state_padding` | 24dp | 16dp | 20dp | 24dp | 24dp | yes | yes |
| `dialog_padding_large` | 20dp | 16dp | 14dp | 20dp | 20dp | yes | yes |
| `player_controls_padding` | 6dp | 4dp | 4dp | 6dp | 6dp | yes | yes |
| `item_padding_vertical` | 8dp | 8dp | 8dp | no - equal | no - equal | yes | yes |
| `padding_xxlarge` | 16dp | absent | 18dp | no - no phone value at this threshold | 16dp | **no - still live** | yes |
| `welcome_page_padding` | 16dp | 10dp | 14dp | 16dp | 16dp | yes | yes |
| `welcome_icon_size` | 31dp | 31dp | 40dp | no - equal | 31dp | yes | yes |
| `welcome_icon_margin_top` | 4dp | 8dp | 10dp | 4dp | 4dp | yes | yes |
| `welcome_title_margin_top` | 6dp | 6dp | 8dp | no - equal | 6dp | yes | yes |
| `welcome_title_text_size` | 20sp | 15sp | 17sp | 20sp | 20sp | yes | yes |
| `welcome_description_margin_top` | 4dp | 6dp | 6dp | 4dp | 4dp | yes | yes |

Resulting file contents: `values-sw320dp-land/dimens.xml` gets 7 keys, `values-sw480dp-land/dimens.xml`
gets 10 keys, `values-land/dimens.xml` keeps `padding_xxlarge` plus the 13 keys no sw bucket declares,
`values-w600dp/dimens.xml` keeps those same 13 keys until Phase 04 decides their fate.

Two rows carry a reason that is not "the sw value wins":

- `item_padding_vertical` - the landscape value equals the phone sw value, so restoring it would
  change nothing. Strategic §6 answer 1 sends such keys to deletion, not to a combined bucket.
- `padding_xxlarge` - no `values-sw320dp` declaration exists, so on a phone whose landscape width is
  below 600dp the `values-land` line is the one that wins today. It is not dead and must stay.

`padding_xxlarge` needs a third edit the other ten keys do not, found by the Phase 01 boundary audit.
It is the only restored key that no tablet bucket declares: `values-sw600dp` and `values-sw720dp` are
both silent on it, so a tablet in landscape resolves it through `values-sw480dp` (18dp). Adding it to
`values-sw480dp-land` gives that bucket the same 480dp smallestWidth score and a winning orientation,
which would hand tablets the phone value of 16dp - the exact outcome strategic §6 rules out. Phase 01
therefore also declares `padding_xxlarge` 18dp in `values-sw600dp`, freezing the value tablets
already resolve. This is not a new tablet landscape bucket, so §6 answer 2 still holds.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | phone-landscape-buckets | - | ✅ Done | 3/3 | [PHASE_01__phone-landscape-buckets.md](PHASE_01__phone-landscape-buckets.md) |
| 02 | prune-shadowed-declarations | 01 | ✅ Done | 2/2 | [PHASE_02__prune-shadowed-declarations.md](PHASE_02__prune-shadowed-declarations.md) |
| 03 | shadowing-gate | 02 | ✅ Done | 3/3 | [PHASE_03__shadowing-gate.md](PHASE_03__shadowing-gate.md) |
| 04 | portrait-width-copy | 02 | ⛔ Blocked | 0/1 | [PHASE_04__portrait-width-copy.md](PHASE_04__portrait-width-copy.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Owner decision:** strategic §6.1 item 4 - does `values-w600dp/dimens.xml` keep handing
      landscape-compact values to a tablet in portrait, or is that removed? Required before
      **Phase 04 only**. Phases 01-03 and 05 are unaffected and must not wait for it.

---

## Evidence from the packaged artifact (2026-07-31)

`aapt2 dump resources` on the built standard-debug APK, so this is what actually ships, not what the
source files say. Full dump kept at `temp/S1282/apk-resource-table.txt`.

```text
dimen/padding_xxlarge          () 32dp  (sw480dp) 18dp  (sw600dp) 18dp  (land) 16dp  (sw480dp-land) 16dp
dimen/empty_state_padding      () 32dp  (sw320dp) 16dp  (sw480dp) 20dp  (sw600dp) 48dp
                               (sw320dp-land) 24dp  (sw480dp-land) 24dp
dimen/welcome_title_text_size  () 24sp  (sw320dp) 15sp  (sw480dp) 17sp  (sw600dp) 32sp  (sw720dp) 42sp
                               (sw320dp-land) 20sp  (sw480dp-land) 20sp
dimen/settings_item_min_height () 36dp  (w600dp) 32dp  (land) 32dp
```

What each line proves:

- The `(land)` entry is gone from every restored key - the declaration that never applied is not merely
  overridden now, it is absent from the shipped resource table.
- `padding_xxlarge` keeps its `(land)` entry on purpose, and gained `(sw600dp) 18dp`. A tablet in
  landscape scores `sw600dp` above `sw480dp-land` and still resolves 18dp, so the phase-boundary audit
  fix holds in the artifact, not just in the reasoning.
- `welcome_title_text_size` resolves 20sp on a phone in landscape at both thresholds and keeps 32sp /
  42sp on tablets - phones restored, tablets untouched, which is strategic §6 in one line.
- `settings_item_min_height` still shows `(w600dp) 32dp` against a 36dp base. That is the open §6.1
  question standing in the artifact: a tablet in portrait matches `w600dp` and gets the compact value.

The emulator available this session (`emulator-5556`, 1080x2400 @420dpi) is `sw411dp`, so it exercises
the `sw320dp-land` bucket natively, with no `wm size` override needed.

---

## Criteria not owned by a phase

Strategic §11 criteria 2 and 3 - "tablet landscape matches the accepted decision" and "phone
landscape is not worse" - are observations, not edits. No phase can carry them without violating the
real-work filter. They are discharged by the on-device check this ticket ends in
(`BlockNeedUserTest` -> `/spec-test-device`), and the screens to look at are the ones the pruned
keys actually reach:

- `empty_state_padding` - Browse, Main, Streams and the three cloud folder pickers, empty state.
- `dialog_padding_large` - ten dialogs, among them delete, file info, rename multiple, filter resource.
- `player_controls_padding` - both player control bars.
- `welcome_*` - all seven pages of the welcome wizard. Highest-risk key is `welcome_title_text_size`: on an sw320 phone in landscape the title grows from 15sp to 20sp, the largest single jump this ticket makes.

---

## Completion Gate

- [ ] All phases show ✅ Done, or Phase 04 is ⛔ Blocked on the owner decision above with every other phase ✅.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, resources and scripts only.
- [ ] `docs/SCRIPT_CHEATSHEET.md` regenerated - Phase 03 adds a script.
- [ ] `/spec-check S1282` returns `Verified`.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1282`.

---

## Blockers Log

- 2026-07-31 - Phase 04 blocked from the start: strategic §6.1 item 4 is Open. Next: owner judges the settings screen on a tablet in portrait, then Phase 04 either prunes the 13 keys or documents them as deliberate.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
