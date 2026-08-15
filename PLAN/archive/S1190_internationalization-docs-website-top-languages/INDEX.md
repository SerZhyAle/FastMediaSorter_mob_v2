# Tactical Plan: S1190 - internationalization-docs-website-top-languages

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Research inputs:** none - strategic §4 carries the AS-IS survey
**Feature:** App interface on thirteen languages, driven by data rather than by hardcoded language lists
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-08-05

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.
>
> **Split note (2026-07-27):** strategic goals §2.2 and §2.3 - documentation and website on thirteen languages - moved to **S1211**. They share no artifact with the app beyond the language set and carry their own unresolved questions (file-naming convention, page multiplication). This plan covers the app only; strategic criterion §11.5 is verified there, not here.
>
> **Out of scope here:** the `wear` module is not wired to `localeConfig` at all (strategic §3.2) - restoring that symmetry is not part of these phases and needs its own ticket if wanted.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | language-registry | - | ✅ Done | 5/5 | [PHASE_01__language-registry.md](PHASE_01__language-registry.md) |
| 02 | language-picker | 01 | ✅ Done | 4/4 | [PHASE_02__language-picker.md](PHASE_02__language-picker.md) |
| 03 | localization-tooling | 01 | ✅ Done | 3/3 | [PHASE_03__localization-tooling.md](PHASE_03__localization-tooling.md) |
| 04 | rtl-layout-hygiene | - | ✅ Done | 2/2 | [PHASE_04__rtl-layout-hygiene.md](PHASE_04__rtl-layout-hygiene.md) |
| 05 | play-language-split | 01 | ✅ Done | 4/4 | [PHASE_05__play-language-split.md](PHASE_05__play-language-split.md) |
| 06 | locale-scaffolding | 01, 03 | ✅ Done | 3/3 | [PHASE_06__locale-scaffolding.md](PHASE_06__locale-scaffolding.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - every strategic §6 item is Resolved (owner decisions 2026-07-25).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; the capability is recorded in `docs/ALL_FEATURES.jsonl` and the showcase is release-owned.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1190` returns `Verified`. **Blocked on the device gates below** - the ticket sits at `BlockNeedUserTest`, and `/spec-check` runs after those pass.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Manual gates (cannot close without a device)

- RTL pass over the screens on Arabic or Urdu (strategic §11.6) - layout edits are static-verified in Phase 04, the visual run is device work.
- Play language split download and its failure path (strategic §11.7) - needs a Play-installed build, not a debug APK.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1190`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-27 - Initial tactical plan authored by `/spec-tech`; documentation and website goals split out to S1211.
- 2026-07-27 - Phase 01 done: `locales_config.xml` is the only declaration, read through `UiLanguageCatalog`; `localeFilters` removed.
- 2026-08-05 - Phase 07 done, ticket to `BlockNeedUserTest`. Settings annotation for the language row rewritten (the sync gate was green but the text described neither the searchable picker nor the download gate - freshness is not accuracy), capability recorded in `docs/ALL_FEATURES.jsonl` as a second record alongside the Welcome one, probe tags placed at the two changed flow entries and validated by a single build. All seven phases done; what remains is device work only.
- 2026-08-05 - Phase 06 done: ten new locales carry a real first tranche - 341 keys each (95 `welcome_*` plus the whole settings file), seeded by the new `scripts/utils/seed-locale-tranche.ps1`. The phase's named source files (`strings_welcome.xml`, `strings_settings_general.xml`) do not exist; the keys live in `strings_setup.xml` and `strings_settings.xml`, which is why the seeder grew `-KeyPrefix`. Two seeder defects were found and fixed during the first locale rather than shipped: a hand-built map silently missed a key (now `-DumpSource`), and inline `<b>` markup would have been escaped (now an allowlist). Remaining 4135 keys per locale ticketed as **S1420**.
- 2026-08-05 - Phase 05 done: the language switch now asks Play for the split first and refuses the switch with a reason when it cannot be fetched. The step's file list was one file short - the helper is constructed by hand, so the installer had to be injected into `GeneralSettingsFragment` as well - and the follow-system sentinel needed a guard, because `"system"` parses as a well-formed language subtag and would otherwise have been sent to Play as a split name. Recorded `AUDIT-P2`: the download shows no progress.
- 2026-07-27 - Phase 02 done: both language selectors replaced by one searchable picker. The plan's file list was corrected during execution (the Welcome switch lives in `page_welcome_enhanced.xml`, not `activity_welcome.xml`) and the settings row changed widget type, which pulled Rule 22 into this phase. Parked S1214 (picker callback lost on host recreate) from the phase-boundary audit.
