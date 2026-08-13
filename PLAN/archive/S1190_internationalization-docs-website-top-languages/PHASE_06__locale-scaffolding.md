# Phase 06 - Locale scaffolding

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Bring the ten new locales into existence with a defined first tranche of strings, and leave a repeatable way to add the next tranche.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the locales are declared.
- [ ] Phase 03 is ✅ Done - the string tool accepts them.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-{zh-rCN,hi,es,fr,ar,bn,pt,ur,de,it}/strings_welcome.xml` | New | ≤ 120 each |
| `app_v2/src/main/res/values-{..}/strings_settings_general.xml` | New | ≤ 200 each |
| `scripts/utils/seed-locale-tranche.ps1` | New | ≤ 160 |

> **Tranche boundary (decided here, 2026-07-27):** the first tranche is the first-run and language-choice surface only - the Welcome screen and the general settings screen. Rationale is strategic ADR-6: an untranslated key falls back to English, so a partial locale is a shipped state, not a defect. The remaining ~4 200 keys per locale are bulk work with its own cost and belong to follow-up tranches, one ticket per tranche, not to this phase.
>
> Machine translation is the accepted quality level (strategic §6.2). No proof-reading gate.

---

## Steps

### Step 06.1 - A repeatable tranche seeder

**Files:** `scripts/utils/seed-locale-tranche.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a script that takes a source `values/<file>.xml` and a locale, and produces the same file under `values-<locale>/` containing every translatable key with translated values supplied on stdin or from a side-car map - preserving key order, `plurals`, `string-array`, and escaping exactly as the byte-preserving string tool does. It must refuse to invent text: a key with no supplied translation is omitted, not copied from English (an English copy would silently defeat the resource fallback). Declare exit codes in the header.

**Verification:**

- `Glob` - `scripts/utils/seed-locale-tranche.ps1` exists.
- Running it with an empty translation map produces a file with zero `<string` elements and exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Script exists; `-SourceFile strings_setup.xml -Locale de -KeyPrefix welcome_` with `{}` as the map reported `eligible 95 | written 0 | rejected 0`, wrote a file whose `<string` count is 0, exit 0.
- Extra probe beyond the stated predicates, because the refuse-to-invent contract is the whole point of the script and an empty map does not exercise it: a two-key map with one good translation and one carrying a `%1$s` the English source does not have returned exit 3, named `welcome_profile_other_desc (placeholder mismatch)`, omitted it, and still wrote the good key into a valid file. A rejected key costs a gap that falls back to English; a silently accepted one would have crashed at format time.
- Design notes. `-KeyPrefix` exists because the source file the phase assumed does not: there is no `values/strings_welcome.xml`, the 95 `welcome_*` keys live inside `strings_setup.xml` next to 74 unrelated `sysinfo_*` keys. Without a prefix filter the seeder could not express this phase's own tranche boundary. Escaping duplicates `ConvertTo-XmlText` from `set-android-string.ps1` rather than sharing it - that tool keeps its helpers private in its body, and hoisting them into a module would rewrite a file this ticket does not otherwise touch; both copies are cross-referenced in the headers. `AUDIT-P3: two copies of the escaping contract` - worth a shared module the next time either file is opened for other reasons.
- Locale directories come from `Get-LocaleResourceDir` (Phase 03) rather than a literal list, so Chinese resolves to `values-b+zh+Hans` - the spelling Android accepts - instead of the `values-zh-rCN` this phase's `Files Touched` table guessed.

---

### Step 06.2 - Seed the ten locales

**Files:** `app_v2/src/main/res/values-*/strings_welcome.xml`, `.../strings_settings_general.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Produce the first-tranche files for all ten new locales through the seeder. Chinese uses the resource qualifier matching the `zh-Hans` declaration; Arabic and Urdu are plain `ar` and `ur`. Placeholders (`%1$s`, `%d`) and their order must survive translation - a reordered placeholder crashes at format time, which is why the format contract is checked rather than eyeballed.

**Verification:**

- `Glob` - both tranche files exist under all ten new `values-*` directories (20 files).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` reports the ten new locales as partial without failing (two-level strictness from Phase 03).
- `.\a.ps1 fr` exits 0 - every new resource file parses.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. All 20 tranche files exist (10 locales × `strings_setup.xml` + `strings_settings.xml`), each carrying 95 and 246 `<string>` elements plus one `<plurals>`; `rejected 0` on every one of the 20 seeder runs. `check_strings_localized.ps1` with no prefix exits 0 and lists the ten new locales under `best-effort locales (reported, not fatal)` at `4135 of 4476 key(s) not translated` - partial without failing, which is the two-level strictness Phase 03 built and ADR-6 requires. `.\a.ps1 fr` exit 0 with `processStandardDebugResources` executed, so all twenty files parse. 341 keys per locale now translated.
- Two seeder defects surfaced on the very first locale and were fixed in the tool rather than worked around (CLAUDE.md Rule 13). **(1) A hand-built map silently under-covers.** The first German map was assembled from an ad-hoc `Select-String` dump and came back `written 94 | eligible 95`: `welcome_default_player_hint` had been missed. The seeder grew `-DumpSource`, which emits the eligible key set using the very regex the write path matches with, so map and source cannot diverge. Every locale after that reported `written == eligible`. **(2) Inline markup would have been escaped.** That same key carries `<b>Always</b>`; plain XML escaping turns it into visible angle brackets. `ConvertTo-ResourceBody` now restores Android's inline styling tags (`b`, `i`, `u`, `small`, `big`, `br/`) after escaping - an allowlist, not a hole, so everything else stays escaped. Confirmed in `values-fr`: `<b>Toujours</b>` emitted as markup.
- Placeholder handling proved itself on real content rather than only on the synthetic probe: Hindi and Bengali reorder `%1$d`/`%2$d` inside `prefetch_a11y_format` because the sentence order differs, and the seeder accepted it - the check compares the sorted token multiset, which is exactly what positional arguments exist to allow. A dropped or retyped token would still have been refused.
- Plural categories were supplied per language rather than copied: Chinese gets `other` only, Arabic all six CLDR categories, the rest `one`/`other`. A plurals entry with the wrong categories reads as broken grammar, which is worse than the English fallback ADR-6 permits.
- 2026-08-05 - **Source correction before any seeding.** The step's `Files Touched` names `values/strings_welcome.xml` and `values/strings_settings_general.xml`. Neither exists and neither ever did. The Welcome screen's 95 keys live inside `strings_setup.xml` beside 74 unrelated `sysinfo_*` keys, and the settings copy lives in `strings_settings.xml` (244 keys covering every settings screen, not only the general one). This is why Step 06.1's seeder grew `-KeyPrefix`: without it the tranche boundary this phase decided could not be expressed against the files that actually exist. Chinese resolves to `values-b+zh+Hans` via `Get-LocaleResourceDir`, not the `values-zh-rCN` the table guessed - Android rejects that spelling.
- 2026-08-05 - Welcome half seeded: all ten new locales carry 95/95 `welcome_*` keys, `rejected 0` on every run, and `.\a.ps1 fr` exits 0 with `processStandardDebugResources` executed, so every new file parses. Spot-checked escaping in `values-fr`: apostrophe emitted as `\'`, quotes as `&quot;`, `&` as plain text where the source had `&amp;`, and `<b>Toujours</b>` kept as markup rather than escaped.

---

### Step 06.3 - Record the remaining tranches

**Files:** `PLAN/` (new spec via `insert.ps1`)
**Depends on:** Step 06.2

**Prompt for developer:**

> Allocate one follow-up ticket for the bulk translation of the remaining keys (all locales, ~4 200 keys each, plus the 32 `vr` and 17 `noLegal` keys per locale named in strategic §3.2) and reference it from S1190 §10. This is the single planned exception to the no-`PLAN`-edits rule: the tranche boundary above is only honest if the remainder is on the board.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <new id> -Format json` returns the ticket.
- `Grep` - the new id appears in `PLAN/S1190_internationalization-docs-website-top-languages.md` §10.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. `search.ps1` found no existing ticket covering bulk locale translation, so this is not a duplicate. `next-id.ps1` allocated **S1420** (`locale-bulk-translation-remaining-tranches`, Draft, priority 45, tier 4); `select.ps1 -Id S1420` returns the record. The id is referenced from S1190 §10 with the exact remainder it owns: 4135 of 4476 `main` keys per locale, plus the 32 `vr` and 17 `noLegal` keys from strategic §3.2.
- The new spec carries the real numbers this phase measured rather than the plan's estimate. The step text says "~4 200 keys each"; the measured remainder is 4135, because the first tranche turned out to be 341 keys (95 `welcome_*` + 246 settings) rather than the two files the plan assumed.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - run `/build`. `.\a.ps1 dq` exit 0, APK `v2.60.8041.533-DEBUG` produced; `.\a.ps1 fr` exit 0 twice, with `processStandardDebugResources` executed after the seeding - which is what proves all twenty new files parse rather than merely exist.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1 -ChangeType Xml`. Three closures: 18 resource files, then the two `values-b+zh+Hans` files, then the amended seeder as `-ChangeType Script`. Split because a closure certifies exactly the set it is handed - naming 18 while changing 21 would have certified 18.
- [x] Phase-boundary audit run - resource and script layers only.

## Phase-boundary audit - 2026-08-05

Scope is resources and one script; no Kotlin, no lifecycle, no coroutines, no Room, so Layers 2-4 do not apply.

- **Resource integrity.** `string-format-gate` reports `baseline 0 | actual 0 | delta 0` over `app_v2/src/main`, so no placeholder was dropped or retyped across 3410 translated values. That is a mechanical check over the whole source set, not a spot check - the stronger evidence, because a single bad `%1$s` compiles fine and crashes at format time.
- **Escaping.** Verified in the emitted files rather than assumed: apostrophe as `\'`, quotes as `&quot;`, `&` from raw text, and `<b>..</b>` preserved as markup. The seeder is the single place this is decided, so the check covers all twenty files.
- **Locale directory naming.** Directories come from `Get-LocaleResourceDir`, so Chinese landed in `values-b+zh+Hans` and aapt accepted it. Had the phase's own `Files Touched` table been followed literally (`values-zh-rCN`), Android would have rejected the qualifier.
- **Script layer.** `assert-exit-contract` PASS - every declared exit code of the seeder is reachable and documented. `docs/SCRIPT_CHEATSHEET.md` regenerated and back in sync after the `-DumpSource` amendment. The escaping duplication against `set-android-string.ps1` stays recorded as `AUDIT-P3` in Step 06.1.
- **Consequence for the manual gate.** Arabic and Urdu resources now exist, so the RTL device pass listed in the INDEX under "Manual gates" changes from theoretical to actionable: before this phase the app had no RTL strings to render, and a device run would have proved nothing. Phase 04 fixed the layouts blind; this is the first point at which that work can actually be observed.
- **Dead weight (Rule 20).** No orphaned resources: every emitted key exists in the English source by construction, since the seeder writes only keys it matched there. Translation maps live under `temp/S1190/` as scratch, not in the repo tree.

---

## Handoff Notes to Next Phase

Ten locales exist with a real first tranche; everything else falls back to English by design. The bulk remainder is ticketed rather than implied.

---

## Rollback Plan

Delete the new `values-*` files - the app returns to English for those locales with no code change, because nothing reads them by name.
