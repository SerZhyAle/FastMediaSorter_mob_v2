---
name: spec-dedup-query-shape
description: Dedup before /spec-draft with single distinctive words, not multi-word phrases - spec_catalog/search.ps1 misses phrases and lets duplicates through
metadata:
  type: feedback
---

Before parking a finding with `/spec-draft`, dedup-check `scripts/spec_catalog/search.ps1` with **several single distinctive words**, one query each - never one multi-word phrase. Search the symptom's vocabulary, not your title for it.

**Why:** on 2026-08-07 I parked S1484 ("ARCHITECTURE.md has no launcher section") after dedup-searching `-Query "ARCHITECTURE launcher"`, which returned nothing. S1461, describing the identical symptom, had been captured 04:23 that same day. Re-testing afterwards: `-Query "launcher subsystem"` also returns **zero**, while `-Query "undocumented"` finds S1461 instantly. The spec-catalog search does not tokenize a phrase across fields the way `dev/CATALOG/scripts/query.ps1 -Search` does (that one was fixed for multi-word in 2026-07-15; this is a different script and did not get the fix). The duplicate cost a full research pass under the wrong number, then an archive-and-merge.

**How to apply:**
- Run 2-4 separate one-word queries drawn from different angles: the affected artifact (`ARCHITECTURE`), the defect word (`undocumented`, `stale`, `missing`), the subsystem (`launcher`), the library or class at the centre of it.
- A zero result from one query is not evidence of absence - it is evidence that word is not in the catalog's text. Try another word before concluding the finding is new.
- If a duplicate surfaces later anyway: the **earlier-captured** ticket survives. Archive yours, fold your research into the survivor, and write the reason for the miss into the archived file so the next reader sees why two existed.
- Same caution when checking whether a ticket already covers an area before starting work, not only when parking.

See [[catalog-search-coverage]] for the *class* catalog's search, which is a different script with different coverage.
