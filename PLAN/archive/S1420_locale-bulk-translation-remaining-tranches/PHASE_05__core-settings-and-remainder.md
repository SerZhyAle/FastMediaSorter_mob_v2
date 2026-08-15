# Phase 05 - Core strings: settings and remainder

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** -

---

## Objective

Finish `strings.xml` - the settings and resource prefixes plus the long tail of 315 small prefixes.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-{ar,bn,de,es,fr,hi,it,pt,ur}/strings.xml` | Modified | n/a - generated |
| `app_v2/src/main/res/values-b+zh+Hans/strings.xml` | Modified | n/a - generated |

---

## Tranche procedure

As defined in [PHASE_02 "Tranche procedure"](PHASE_02__setup-screen-completion.md#tranche-procedure).

The long tail is the one place the file cannot be split along a prefix that maps to a screen: 315 prefixes hold fewer than 15 keys each. Steps 05.3 and 05.4 therefore run without `-KeyPrefix` and rely on `-Merge` to fill whatever the earlier steps left, which keeps the "no half-translated screen" contract because by then every prefix that does back a screen is already done.

---

## Steps

### Step 05.1 - `settings_`, `setting_` and `reset_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix settings_` (47 keys), `-KeyPrefix setting_` (43) and `-KeyPrefix reset_` (31 - measured 32 when the plan was written; the seeder's `eligible` is the authority per INDEX). Match the wording already used in the locale's `strings_settings.xml`, which S1190 translated in full - a setting named one way on the settings screen and another way in a confirmation dialog reads as two different settings.

**Why:**

Strategic §7's top risk is a surface that looks inconsistent to the user, and these prefixes sit alongside the one file S1190 already completed in every locale.

**Verification:**

- Keys per locale: `settings_` 47, `setting_` 43, `reset_` 31. Actual must equal expected in all ten.
- Phase 03-04 prefixes unchanged.

**Status:** `[x]` done

**Outcome - 2026-08-11.** All ten locales: `eligible 47 | 43 | 31`, `rejected 0`, per-locale total 1362 -> 1483. Counted back out of the files rather than trusted: `settings_` 47, `setting_` 43, `reset_` 31 in every locale, and all twenty phase 03-04 prefixes still match their English eligible count. `.\a.ps1 fr` BUILD SUCCESSFUL, exit 0. Coverage gate 3068 -> 2947 untranslated per locale, identical in all ten, drop exactly 121 against a corpus that held still at 4844.

The prefix recompute also caught a 76th English `statistics_` key that phase 04 did not translate. Not drift: it carries `translatable="false"`, so the seeder's eligible set is still 75 and the locales are complete. Recorded because the raw element count alone reads like a regression.

---

### Step 05.2 - `resource_`, `activity_`, `app_`, `ext_` and `sort_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix resource_` (45 keys), `-KeyPrefix activity_` (6 of its 45 - see the carve-out below), `-KeyPrefix app_` (49), `-KeyPrefix ext_` (39) and `-KeyPrefix sort_` (30). Keep `Resource` and `Folder` distinct in the target language the same way the English source keeps them distinct - they are different concepts in this app, not synonyms.

**Why:**

Strategic §2 requires full coverage of `main`, and these five prefixes are the largest remaining named groups after settings.

**Verification:**

- Keys per locale: `resource_` 45, `activity_` 6, `app_` 49, `ext_` 39, `sort_` 30. Actual must equal expected in all ten.
- Step 05.1 prefixes unchanged.

**Status:** `[x]` done

**Carve-out - `activity_` is not user-facing text.** Of its 45 keys, 39 hold layout attribute literals: layout-manager class names (`androidx.recyclerview.widget.LinearLayoutManager`), attribute enums (`fit`, `fixed`, `scrollable`, `simple`), numeric weights and alphas (`0.60`, `1.2`), glyph button captions (▶ ◀ ☆ ℹ − +) and placeholder indicators (`1/1`, `0/0`, `3..`). Grepped 2026-08-11: none of them is referenced anywhere outside the `values*` string files themselves.

Those 39 are deliberately not seeded, and the decision is about blast radius rather than tidiness. A translated class name or a translated `fit` does not fail a build, a lint pass or any gate in this repo - it fails at layout inflation, and only for the user who has that language selected. The benefit on the other side of that risk is zero, because the value must stay byte-identical to English to work at all. Left absent, each key resolves from `values/` for every locale, which is both correct and what already happens today.

Seeded instead are the 6 keys in the prefix that really are text: both scan labels, both `btnDelete_text`, both `btnSlideShow_text`. Four are live; the two `_unified_` ones sit on a layout nothing inflates yet and were taken along so the pair does not split later.

Verified rather than assumed: all ten translators returned full 45-key maps, and all 390 literal values came back byte-identical to English - zero drift. The filtered map is derived from those returns, so nothing was re-translated to build it.

Consequence for strategic §11 criterion 1: `check_strings_localized.ps1` cannot reach zero for these ten locales while those 39 keys count as eligible. Closing that gap belongs to `S1550`, which marks layout-attribute strings `translatable="false"` and thereby removes them from the eligible set. This plan should not paper over it by shipping 390 duplicated literals.

**Outcome - 2026-08-11.** All ten locales: `eligible 45 | 49 | 39 | 30 | 45`, `rejected 0`, per-locale total 1483 -> 1652, of which `activity_` contributed 6. Counted back out of the files: `resource_` 45, `app_` 49, `ext_` 39, `sort_` 30, `activity_` 6 in every locale. `.\a.ps1 fr` BUILD SUCCESSFUL, exit 0. Coverage gate 2947 -> 2778 untranslated per locale, identical in all ten, drop exactly 169 against a corpus still at 4844.

Three of the step's planned counts were wrong and are corrected above from the seeder's `eligible`: `resource_` 46 -> 45, `sort_` 31 -> 30, and `activity_` 45 -> 6 by the carve-out.

---

### Step 05.3 - Long tail, first half

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Dump `strings.xml` with no `-KeyPrefix`, diff the dump against one locale file to list the keys still missing, and translate the first half of that list. Seed with `-Merge` and no `-KeyPrefix`. Work through the list in source order so a reviewer can find where the boundary fell.

**Why:**

Strategic §6.1 resolved that `strings.xml` splits by prefix, and this step handles the residue the resolution explicitly leaves - 315 prefixes carrying fewer than 15 keys each, none of which maps to a screen on its own.

**Verification:**

- Missing-key count per locale after the step: expected to have dropped by the size of the translated half, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.
- Every prefix from phases 03-05 still at its expected count.

**Measured 2026-08-11 and the boundary that follows from it.** The residue is 1566 keys per locale, identical in all ten. Subtracting the 39 `activity_` layout literals Step 05.2 carved out - the carve-out survives this step precisely because it runs with no `-KeyPrefix` and would otherwise pull them back in - leaves **1527**. Split in source order into twelve scratch dumps of 130 (the last 97), named `tail_01` .. `tail_12`; this step owns `tail_01` .. `tail_06` (780 keys), Step 05.4 owns `tail_07` .. `tail_12` (747). The dumps are regenerable rather than kept: `seed-locale-tranche.ps1 -DumpSource` reproduces them from the same source with the same regex the seeder matches with.

Twelve dumps rather than two, because the half is 7 800 individual translations and a session that ends inside it must leave a boundary a later session can find. A dump is the unit that is either seeded whole in all ten locales or not at all, which keeps the "no half-translated screen" contract at a granularity the volume actually permits.

Sub-tranche progress - each line is seeded in all ten locales or in none:

- [x] `tail_01` 130 - `poster_thumbnail_unavailable` .. `max_date` - seeded, 1652 -> 1782 per locale, rejected 0
- [x] `tail_02` 130 - `min_size_mb` .. `allow_rename` - seeded, 1782 -> 1912 per locale, rejected 0
- [x] `tail_03` 130 - `allow_delete` .. `restart` - seeded, 1912 -> 2042 per locale, rejected 0
- [x] `tail_04` 130 - `error` .. `ssh_key_loaded` - seeded, 2042 -> 2172 per locale, rejected 0
- [x] `tail_05` 130 - `review_smb_details` .. `tab_ftp_sftp_resources` - seeded, 2172 -> 2302 per locale, rejected 0
- [x] `tail_06` 130 - `tab_cloud_resources` .. `media_category_video` - seeded, 2302 -> 2432 per locale, rejected 0

**Status:** `[x]` done

**Outcome - 2026-08-11.** All six dumps seeded in all ten locales, `rejected 0` on every one of the sixty runs. Per-locale `strings.xml` 1652 -> 2432, counted back out of the files rather than trusted. Recomputed against the live source afterwards: 786 eligible keys still missing per locale, identical in all ten, and the missing set is **exactly** `tail_07` .. `tail_12` (747) plus the 39 carved-out `activity_` literals - zero keys missing that this step did not plan to leave. The corpus held at 3218 eligible across the whole step, the first phase in this plan where it did not move underneath the work.

`.\a.ps1 fr` BUILD SUCCESSFUL, exit 0. Coverage gate exit 0: 2000 of 4844 untranslated per locale, identical in all ten, `en`/`ru`/`uk` complete. The net drop reads 778 rather than 780 because two eligible keys appeared during the session in a file phases 06-07 own; `strings.xml` itself moved by exactly 780, which is the number this step is accountable for.

**Two escaping defects caught before they shipped, both invisible to the seeder.**

The seeder's escaping is asymmetric and nothing said so. It runs `SecurityElement::Escape` then maps `&apos;` back to `\'`, so an apostrophe must arrive **plain** - a map supplying `\'` gets `\\'`, an escaped backslash followed by a bare apostrophe, which AAPT2 refuses. A double quote is the opposite: `\"` passes through correctly and is **required** wherever English spells it, because `Escape` leaves the backslash alone and turns the quote into `&quot;`. Two of ten translators pre-escaped apostrophes on `tail_02` (26 values); six of ten dropped the escape from quotes on `tail_03`. Both classes are now refused by [`evidence/verify-maps.ps1`](evidence/verify-maps.ps1), which gained an apostrophe check and an escaped-quote parity check against the English value.

Parity with English, not correctness in the abstract, is the rule the quote check enforces. `virtual_resource_added` carries a **bare** `"` in the English source, and eight locales had it escaped: escaping only those would have shown quotation marks in eight languages that English silently drops. Measured while fixing it: 14 English keys carry a bare unescaped quote. That is `S1567`'s territory, and its §1 now carries this evidence plus a correction - its §3.2 claims `-Merge` cannot repair already-written entries, but the map wins over the carried entry whenever it supplies the key (`seed-locale-tranche.ps1` ~262), proven here by re-seeding `tail_01` to repair it. Remediation is a re-seed from the saved maps, not 100 hand edits.

**A compliance gate fired on translated text.** `verifyNoPlatformNames` failed the resource build on eleven lines. Ten were `folder_instagram`, whose value is the Instagram media folder's own on-disk name - baselined for the ten locales exactly as `values/`, `values-ru/` and `values-uk/` already were. The eleventh was real and only reachable in translation: German capitalises nouns, so "parallele Threads" produced the denylisted literal `Threads` where the English lowercase "threads" does not match. Reworded to "gleichzeitiger Verbindungen" rather than baselined - a suppression there would have taught the gate to ignore a genuine platform name in German.

---

### Step 05.4 - Long tail, second half

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Repeat Step 05.3 for the remainder - dumps `tail_07` .. `tail_12`, 747 keys - then confirm the dump and each locale file cover the same key set. Re-derive the missing list against the live source first: the corpus moved twice inside Phase 04 alone, so keys added after 2026-08-11 will not appear in any `tail_NN` dump and belong to this step.

**Why:**

Strategic §11 criterion 1 requires zero untranslated keys in `main`, and `strings.xml` is 65% of the corpus.

**Verification:**

- Keys per locale file: expected `eligible - 39`, recomputed against the live source at the time the step runs; 3218 eligible on 2026-08-11 gives 3179. The plan's original figure of 3050 predates both the corpus growth and the `activity_` carve-out. Actual must equal expected in all ten.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` shows the per-locale shortfall reduced to the keys owned by phases 06-07, plus the 39 carved-out `activity_` literals that only `S1550` can remove from the eligible set.

**Result 2026-08-14** - delivered by the bulk route rather than by `tail_07`..`tail_12`: the whole remainder of every file left in one export and returned per locale, so this step closed together with phases 06-08. `main/values/strings.xml` reports `eligible 2841`; each of the ten locales now holds 2804-2812 of them. The shortfall is 29-37 per locale and is entirely the deliberate carve-out plus the S1626 rejects - no key is missing for a reason this plan still owns. Corpus figures differ from the 3218 measured on 2026-08-11 because other tickets kept moving `strings.xml`; the live recompute is the authority, per the baseline rule in INDEX.md.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `strings.xml` complete in all ten locales at 3050 keys each.
- [ ] No file under `values/`, `values-ru/` or `values-uk/` modified.
