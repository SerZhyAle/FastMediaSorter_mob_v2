# Tactical Plan: S1420 - locale-bulk-translation-remaining-tranches

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Research inputs:** none - strategic §6.1 was resolved by measurement recorded inline in the spec
**Feature:** Bulk translation of the remaining string corpus into the ten best-effort locales
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 45
**Status:** In Progress
**Phases:** 9 / 9 done (06 and 07 superseded by the bulk route, counted as closed)
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Target locales

Ten best-effort locales, identical in every tranche step: `ar`, `bn`, `de`, `es`, `fr`, `hi`, `it`, `pt`, `ur`, `zh-Hans`.
`en` / `ru` / `uk` are strict and are never written by this plan.

Resource directory for `zh-Hans` is `values-b+zh+Hans`; the other nine are `values-<tag>`. All resolution goes through `Get-LocaleResourceDir`.

---

## Measured baseline (2026-08-09)

- `src/main` corpus: 4663 keys across 20 `values/strings*.xml` files.
- Untranslated per locale: 4322 - identical for all ten locales.
- Already covered by S1190 tranche 1: `strings_settings.xml` complete, `strings_setup.xml` partial (95 of 169).
- Flavor sets outside `main`: `src/vr/res/values/strings.xml` 56 keys, `src/noLegal/res/values/` 17 keys in two files.

Every key count in this plan was measured on 2026-08-09 with a plain element match, which does not subtract `translatable="false"`. The authority at run time is the `eligible N` figure the seeder itself prints, since that is the set it will actually write. Where a step's expected count and the seeder's `eligible` disagree, the seeder is right and the step's number is corrected in place rather than treated as a failed verification.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | seeder-merge-and-flavors | - | ✅ Done | 4/4 | [PHASE_01__seeder-merge-and-flavors.md](PHASE_01__seeder-merge-and-flavors.md) |
| 02 | setup-screen-completion | 01 | ✅ Done | 1/1 | [PHASE_02__setup-screen-completion.md](PHASE_02__setup-screen-completion.md) |
| 03 | core-launcher-and-messaging | 01 | ✅ Done | 4/4 | [PHASE_03__core-launcher-and-messaging.md](PHASE_03__core-launcher-and-messaging.md) |
| 04 | core-feature-surfaces | 03 | ✅ Done | 4/4 | [PHASE_04__core-feature-surfaces.md](PHASE_04__core-feature-surfaces.md) |
| 05 | core-settings-and-remainder | 04 | ✅ Done | 4/4 | [PHASE_05__core-settings-and-remainder.md](PHASE_05__core-settings-and-remainder.md) |
| 06 | player-and-viewer-files | 01 | ⏭️ Skipped | superseded | [PHASE_06__player-and-viewer-files.md](PHASE_06__player-and-viewer-files.md) |
| 07 | service-and-operations-files | 01 | ⏭️ Skipped | superseded | [PHASE_07__service-and-operations-files.md](PHASE_07__service-and-operations-files.md) |
| 08 | flavor-string-sets | 01 | ✅ Done | 2/2 | [PHASE_08__flavor-string-sets.md](PHASE_08__flavor-string-sets.md) |
| 09 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_09__docs-catalog-cleanup.md](PHASE_09__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase order follows strategic §5 - `strings.xml` first (phases 03-05), then players (06), then service files (07). Phase 02 is the one local exception: `strings_setup.xml` is the only file currently sitting in the half-translated state that §7 names as the top risk, so it is closed before the larger program starts.

---

## Pre-Implementation Blockers

- [x] **Research:** which language set `vr` and `noLegal` need - answered by the owner 2026-08-13, all ten, same set as `main`. Recorded in strategic §6.2. Phase 08 unblocked.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 states no new capability appears, only the completeness of an already-shipped one changes.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [x] `/spec-check S1420` returns `Verified` - 2026-08-14, PASS/WARN/FAIL 14/0/0.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1420`.

---

## Volume note

Phases 02-07 write roughly 43 000 individual translations - 4322 keys times ten locales. Strategic §3 fixes no coverage depth in advance and permits closing the ticket in tranches rather than in one pass, so a session that ends mid-plan leaves the ticket `In Progress` with the remaining steps unticked. That is the planned shape of this ticket, not a failure of a run.

---

## Blockers Log

- 2026-08-09 - Phase 08 blocked: strategic §6.2 open - owner has not decided whether `vr` and `noLegal` need the same ten languages. Next: owner ruling; phases 01-07 proceed meanwhile.
- 2026-08-13 - Cleared. Owner ruled all ten, and the flat-file route made the question cheap: the two sets are 76 lines on top of 1806 in the same export, so they cost no extra trip through the service.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-09 - Phases 01 and 02 done. Seeder gained `-Merge` and `-SourceSet`; `strings_setup.xml` closed at 169 keys in all ten locales. Remaining per locale: 4278 of 4693.
- 2026-08-09 - Phase 03 done. Eight `strings.xml` prefixes closed in all ten locales, 701 keys each. Seeder's `-DumpSource` fixed to round-trip `plurals` and `string-array`, without which the first plural in the corpus would have been dropped from every locale. Remaining per locale: 3628 of 4743 - the corpus grew by 50 keys mid-phase, so the drop reads as 650 rather than 701. Parked `S1550`: 81 keys carry layout attribute literals with no `translatable="false"`.
- 2026-08-11 - Step 04.3 done: `statistics_` 75, `gesture_` 53, `tooltip_` 66 closed in all ten locales. The recompute check fired for real and caught `launcher_` grown from 236 keys to 249 after Phase 03 closed - thirteen English keys with no translation in any locale, translated and merge-seeded in the same step rather than deferred. Per-locale total 1213, `missing=0` against every eligible key of all fifteen closed prefixes. Parked `S1568`: 396 of 3224 keys in the main `strings.xml` have no static reference anywhere in the tree, so this plan is translating each of them ten times.
- 2026-08-11 - Step 05.2 done: `resource_` 45, `app_` 49, `ext_` 39, `sort_` 30 closed in all ten locales, plus 6 of the 45 `activity_` keys. Per-locale total 1483 -> 1652, coverage 2947 -> 2778. The other 39 `activity_` keys are layout attribute literals - class names, `fit`/`scrollable`, weights, glyphs - referenced nowhere outside the string files, and are deliberately left unseeded: they have to stay byte-identical to English to work, so translating them buys nothing and risks an inflation failure visible only in that one language. All ten translators returned them byte-identical anyway, which is the evidence the carve-out loses nothing. Strategic §11 criterion 1 cannot reach zero on those 39 until `S1550` marks them `translatable="false"`.
- 2026-08-11 - Step 05.1 done: `settings_` 47, `setting_` 43, `reset_` 31 closed in all ten locales, per-locale total 1362 -> 1483, coverage 3068 -> 2947. `reset_` was planned as 32 and measured 31; corrected in the step per the baseline rule above.
- 2026-08-11 - Repaired in phase 05 what phase 03 shipped: eight `error_` values in each of the ten locales carried a real line break where the English spells `\n`. Android collapses unquoted whitespace, so every one of those paragraph breaks was already invisible to the user - 80 values, re-seeded from corrected maps. The scratch harness `verify-maps.ps1` gained a direct refusal of the character; its existing check only compared `\n` counts, which catches this solely while the English side spells `\n`. The same four keys are broken in the English source itself and in `ru`/`uk`, which this plan never edits - parked as `S1570`.
- 2026-08-11 - Step 05.3 done: the long tail split into twelve source-order scratch dumps (`tail_01` .. `tail_12`), of which this step closed the first six - 780 keys in all ten locales, per-locale `strings.xml` 1652 -> 2432, coverage 2778 -> 2000. Twelve dumps rather than the planned two halves, because 7 800 translations need a boundary a later session can find. The remaining missing set recomputes to exactly `tail_07`..`tail_12` plus the 39 `activity_` literals, zero unplanned. Two escaping defects caught before shipping: the seeder wants apostrophes plain but escaped quotes verbatim, and nothing documented the asymmetry - `verify-maps.ps1` now refuses both, with the quote check enforcing parity with English rather than escaping unconditionally. Evidence and a §3.2 correction added to `S1567`. `verifyNoPlatformNames` also fired: `folder_instagram` baselined for the ten locales as `values-ru`/`values-uk` already are, and a German capitalised "Threads" reworded rather than suppressed.
- 2026-08-14 - All ten locales landed. Per-locale untranslated: `ur` 89, `fr` 89, `es` 90, `it` 90, `pt` 92, `bn` 93, `de` 94, `ar` 96, `zh-Hans` 99, `hi` 100 - against 1887 each before the round. Of 18 820 line-slots the placeholder guard rejected 42, spread over 19 keys; the service reads a lone `%1$s` as a currency amount, and the three worst keys account for 20 of the 42. Parked as `S1626` - it is an English-copy fix, not a translation one. Owner asked the same day that new strings be born in all thirteen locales rather than three, parked as `S1627`. What remains per locale is 89-100 keys, of which about 89 were never exported by design: 88 symbol-only values plus one carrying escaped markup. Phases 05 and 08 are done on measured evidence; 06 and 07 are marked superseded rather than ticked, because their steps would now re-seed shipped text. Phase 09 is the only real work left.
- 2026-08-13 - First locale through the new route: Arabic. 1875 of 1882 lines landed across `main`, `vr` and `noLegal`; `ar` untranslated fell 1887 -> 96, and `.\a.ps1 fr` is BUILD SUCCESSFUL with the RTL corpus in place. The 96 are 88 symbol-only values and one markup value never exported, plus 7 lines the service returned with a broken format token - six of them read `%1$s` as a currency amount and rendered "1 دولار", one dropped the token outright. Those 7 keys stay English by fallback, which is the whole point of rejecting them per line rather than per file. The remaining nine locales are the same file and the same command. Two ergonomics fixes came out of the first real round trip: the service names its answer `all_texts_en.en.ar.txt`, so the locale is now read as the last declared tag in the stem, and the sidecar is looked for in the export directory when it is not beside the downloaded file.
- 2026-08-13 - Phase progress is now tracked per locale, not per file: one import closes every file of one language at once, so phases 05.4-08 advance together and complete only when the tenth locale lands.
- 2026-08-13 - Owner supplied an external translation route, and `scripts/utils/locale-bulk-export.ps1` + `locale-bulk-import.ps1` now serve it: the whole remainder leaves as one flat file of English lines and comes back per locale as the same file in that language. This replaces per-prefix hand translation for phases 05.4-07 - a step becomes an import, not a translation pass. The entire remaining corpus is 1806 exportable lines in one file (1891 missing keys, of which 88 are symbol-only values deliberately left English and one carries escaped markup that cannot round-trip through a line format). Line position is the only key binding, so import refuses a file whose line count differs and rejects any single line whose format-token set drifted from English; both refusals verified against a deliberately damaged file. Merge behaviour verified on `values-de/strings.xml`: 2168 shipped keys plus a 640-key map planned exactly 2808 written, nothing dropped.
- 2026-08-11 - Phase 04 done. Step 04.4 closed `share_` 32, `link_` 42, `browse_` 25, `wear_` 25, `companion_` 21 in all ten locales, and four more `launcher_gadget_search_*` keys that appeared mid-step. Per-locale total 1362 with `missing=0` and `extra=0` against all twenty closed prefixes; `.\a.ps1 fr` BUILD SUCCESSFUL, exit 0. Coverage gate afterwards: 3068 of 4844 untranslated per locale, identical in all ten, strict `en`/`ru`/`uk` complete, exit 0. The corpus is now 4844 keys against the 4693 Phase 02 measured, so the remainder falls by less than this phase translated. The corpus moved twice inside this one phase, both times in `launcher_`, because another ticket is adding launcher gadget strings on the same tree - the closing check has to be a recompute against the live source, and a stored count would have passed while the surface was reopening.
