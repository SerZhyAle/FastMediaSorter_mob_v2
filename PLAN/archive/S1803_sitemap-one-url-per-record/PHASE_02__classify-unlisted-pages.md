# Phase 02 - Classify every unlisted page

**Strategic spec:** [`../S1803_sitemap-one-url-per-record.md`](../S1803_sitemap-one-url-per-record.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Go through the addresses Phase 01 newly announced and decide, page by page, whether it stays announced or becomes an exclusion with a stated reason.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] The regenerated sitemap diff against `PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt` is available - it is the work list, since Phase 01 step 01.4 established that an unclassified page cannot exist and no inventory file is produced.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 8 record lines |
| `sitemap.xml` | Modified (generated) | n/a |
| `docs/DOCS_MAP.md` | Modified (generated) | n/a |

---

## Steps

### Step 02.1 - Decide the default for each group

**Files:** `PLAN/S1803_sitemap-one-url-per-record/evidence/classification.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Group the added addresses by registry record and decide the default per group before looking at individual pages: a group of user-facing guides announces everything by default, a group mixing audiences announces nothing by default. Write the per-group default and its one-sentence reason into `PLAN/S1803_sitemap-one-url-per-record/evidence/classification.md`.

**Why:**

Strategic §7 names search results filled with internal notes as the risk of expanding blindly; deciding the default per group first turns 52 individual judgements into a handful, and leaves only the genuine exceptions to argue about one at a time.

**Verification:**

- `Glob` - `PLAN/S1803_sitemap-one-url-per-record/evidence/classification.md` exists.
- Every record named in the diff has a default and a reason in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 02.1: PLAN/S1803_sitemap-one-url-per-record/evidence/classification.md written with a default and a one-sentence reason for each of the seven indexable records. Five announce everything (site-landing, user-guides, feature-showcase, wear-docs, legal-downloads, oss-notices); settings-reference announces nothing by default, because it holds three rendered pages beside three machine-readable JSON inputs and a noLegal variant - a group with both a page and its data source cannot take one blanket answer. The defaults were decided from a measured pass, not a guess: PLAN/S1803_sitemap-one-url-per-record/evidence/announced_scan.py walks each record's globs and asks whether the file's own front matter declares an address the generated sitemap carries (73 entries at the time). Eleven exception candidates were handed to step 02.2 rather than decided here, including one that matters - docs/SETTINGS_REFERENCE_noLegal.md already declares /docs/SETTINGS_REFERENCE_noLegal.html and the sitemap does not carry it, so today's state is an undocumented exclusion. Caveat recorded in the file: site-landing's four members are HTML and a root README, so the front-matter probe reports them silent; that is a limit of the probe and step 02.2 confirms them against the served site.

---

### Step 02.2 - Mark the exceptions

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 02.1

**Prompt for developer:**

> Walk the added-address list from the Phase 01 diff and add an exclusion entry for every page that departs from its group's default, each with a reason written for someone reading it a year later. Do not add an exclusion whose reason is "not needed" or "internal" alone - say what it is and who it is for.

**Why:**

Strategic §2.2 requires the reason to live beside the decision, and §7 makes an unusable reason the same failure as a missing one - the entry that cannot be re-judged later is what turns the list into a dumping ground the check cannot catch.

**Verification:**

- `Grep` - every exclusion entry in `docs/DOCUMENT_REGISTRY.jsonl` carries a non-empty reason longer than three words.
- `scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 02.2: ten exclusions added beside the one that already existed, eleven in total, each naming what the file is and who it is for rather than calling it internal. user-guides loses the release runbook HOW_TO_DEVELOP_AND_RELEASE_RU.md and the screenshot inventory howto/SCREENSHOTS.md; feature-showcase loses the three FEATURES_noLegal locales, on the same channel argument the pre-existing SETTINGS_REFERENCE_noLegal entry already made; settings-reference, oss-notices and legal-downloads lose their machine-readable inputs (settings-manifest.json, settings-annotations.json, howto-path-vocab.json, legal/oss-notices.json) and the packaged licence dump THIRD_PARTY_LICENSES.md, each pointing at the rendered page a reader should find instead. Classification corrected first: step 02.1 had settings-reference defaulting to announce-nothing, which sitemap_exclude cannot express - it only carves out - so the default became announce-every-rendered-page with the data files as carve-outs, which says the same thing and is checkable. Also corrected there: site-landing's members are not silent, the front-matter probe just cannot see HTML; the root and both index locales are in the sitemap. Verified: document_registry/validate.ps1 exit 0 (34 records); 11 exclusions, 0 with a reason of three words or fewer.

---

### Step 02.3 - Regenerate and diff against the baseline

**Files:** `sitemap.xml`, `docs/DOCS_MAP.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Regenerate both artifacts, then diff the sitemap against `PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt`. Confirm that every address present before is still present, and that every address added corresponds to a page classified as announced in step 02.1. An address that vanished is a defect of this ticket, not a decision.

**Why:**

Strategic §7 names a record without new fields quietly changing behaviour as a risk, and §11.5 requires an untouched record to announce all of its own pages and none of anyone else's; only the diff against the pre-change set can tell an intended addition from an accidental removal.

**Verification:**

- Every line of `PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt` appears in the regenerated `sitemap.xml`.
- The new entry count equals announced pages, and equals total pages minus exclusions.
- `scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 02.3: sitemap and DOCS_MAP regenerated; the diff against PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt shows all 19 baseline addresses still present and nothing removed - comm -23 over the sorted sets returns empty. Count went 19 to 73. The arithmetic closes, measured by PLAN/S1803_sitemap-one-url-per-record/evidence/arithmetic.py: 85 files under the seven indexable records, minus 11 excluded with a reason, minus site-landing's 4, leaves 70 announced through their own front matter; site-landing contributes the remaining 3 addresses (the root, index-ru.html, index-uk.html) through the generator's landing handling rather than front matter, and README.md is the root's source, not a fourth address. 70 + 3 = 73 = the sitemap's entry count. Worth recording honestly, measured by PLAN/S1803_sitemap-one-url-per-record/evidence/exclusion_effect.py: only 1 of the 11 exclusions actually withholds an address that would otherwise be announced (SETTINGS_REFERENCE_noLegal.md, which declares /docs/SETTINGS_REFERENCE_noLegal.html). The other 10 name files that declare no address at all - they are not withholding anything today, they are what turns silently-unannounced into deliberately-unannounced-with-a-reason, which is what Phase 03's standing check needs to be able to judge. Verified: generate.ps1 -Check exit 0.

---

### Step 02.4 - Confirm every announced address resolves

**Files:** `PLAN/S1803_sitemap-one-url-per-record/evidence/address-resolution.txt`
**Depends on:** Step 02.3

**Prompt for developer:**

> For each address in the regenerated sitemap, find the file that declares it and record the pairing in `PLAN/S1803_sitemap-one-url-per-record/evidence/address-resolution.txt`. An address with no declaring file is a defect to fix here.

**Why:**

Strategic §11.2 makes "every address leads to an existing file" a completion criterion, and an address that resolves to nothing is worse than an unannounced page - it tells a search engine to fetch a page that will answer with an error.

**Verification:**

- `Glob` - `PLAN/S1803_sitemap-one-url-per-record/evidence/address-resolution.txt` exists.
- Zero entries in that file are unresolved.
- The entry count equals the sitemap's `<url>` count.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 02.4: PLAN/S1803_sitemap-one-url-per-record/evidence/address-resolution.txt pairs each of the 73 sitemap addresses with the file that declares it; unresolved = 0, and the row count equals the sitemap's url count. Resolution is by declared address for the 70 front-matter pages and by the file itself for the three landing addresses, which are real HTML files rather than front-matter declarations - the same split step 02.3's arithmetic already recorded. Produced by PLAN/S1803_sitemap-one-url-per-record/evidence/resolve.py so the pass is repeatable rather than hand-listed. Verified: entries=73 unresolved=0; grep UNRESOLVED in the artifact returns 0; artifact row count 73 equals grep -c <loc> sitemap.xml.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable: no application source is touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Nothing is unclassified any more, and the sitemap states the real published surface. Phase 03 turns that state into a standing check so the next page added cannot silently fall out again.

---

## Rollback Plan

Revert the phase commit and regenerate; the registry returns to its previous records and the two generated artifacts rebuild from them.
