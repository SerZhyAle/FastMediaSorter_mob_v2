# Phase 02 - Setup screen completion

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 1 / 1
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Close `strings_setup.xml` in all ten locales - the only file currently sitting half translated.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `-Merge` proven by Step 01.4 - without it this phase would erase the 95 keys S1190 shipped.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-{ar,bn,de,es,fr,hi,it,pt,ur}/strings_setup.xml` | Modified | n/a - generated |
| `app_v2/src/main/res/values-b+zh+Hans/strings_setup.xml` | Modified | n/a - generated |

---

## Tranche procedure

Every tranche step in phases 02-08 runs the same four moves. Later phases reference this section rather than repeat it.

1. Dump the English source - `seed-locale-tranche.ps1 -SourceFile <file> [-KeyPrefix <p>] -DumpSource`, which writes `<file>.json` into the ticket's scratch directory. The dump is the only supported way to build a map: it uses the very regex the seeder later matches with, so the map cannot silently under-cover the source.
2. Translate every value into the ten locales, one map per locale, laid out as `maps/<locale>/<file>.json` inside the same scratch directory. Keep every format token (`%1$s`, `%d`, `%%`) identical to the English value and placed where the target language's word order puts it. Keep inline markup `<b> <i> <u> <small> <big> <br/>` exactly as it appears. Preserve the tone of the English source, which already satisfies `docs/COMMUNICATION_POLICY.md` §2 and §6 - a translation adds no exclamation mark the source lacks and drops no hedge the source carries.
3. Seed each locale with `-Merge`. Exit 3 means at least one supplied translation was rejected: fix the map and re-run rather than leaving the key untranslated.
4. Never write `values/`, `values-ru/` or `values-uk/`. Those three are strict and complete, and strategic §2 keeps them that way.

---

## Steps

### Step 02.1 - Translate the remaining `strings_setup.xml` keys

**Files:** `app_v2/src/main/res/values-*/strings_setup.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure for `strings_setup.xml` with no `-KeyPrefix`, so the map covers all 169 eligible keys. The 95 keys already present in each locale come back through `-Merge`; supply translations for the ~74 that are missing. Compare the dump against one locale file first to see which keys are actually absent, rather than re-translating what S1190 already shipped.

**Why:**

Strategic §7 names the half-translated screen as the top risk and lists whole-file tranches as its mitigation; `strings_setup.xml` is the only file in the tree currently in that state, at 95 of 169 keys.

**Verification:**

- Key count per locale file after seeding: expected 169, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0, actual must equal expected.
- The 95 pre-existing keys are unchanged - diff each locale file against its pre-phase copy and expect additions only, zero modifications.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` reports the per-locale untranslated count dropped by the number of keys this step added.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fr` (resources/manifest) BUILD SUCCESSFUL, exit 0. That is the ladder rung for a resource-only change: AAPT2 parsed and merged all ten locale files, which is exactly what these edits can break.
- [x] No file under `values/`, `values-ru/` or `values-uk/` modified.

---

## Outcome - 2026-08-09

The 74 missing keys turned out to be one coherent surface - the whole `sysinfo_*` system-information screen, sections and fields, with no format placeholders anywhere in the set. That made the tranche cleaner than planned: the file's remainder was already a single screen, so closing it closed a screen.

Per-locale result, all ten: `eligible 169 | written 169 | rejected 0`. Verified against a pre-seed snapshot rather than trusted - 95 pre-existing entries byte-identical, 74 added, 0 modified, in every locale.

Coverage gate afterwards reads 4278 of 4693 untranslated per locale. The corpus grew from 4663 to 4693 mid-run because another session was adding keys on the same tree, so the drop reads as 44 rather than 74; the arithmetic reconciles exactly - 4322 + 30 new - 74 translated = 4278.
