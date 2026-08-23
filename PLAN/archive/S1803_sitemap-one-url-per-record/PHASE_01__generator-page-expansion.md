# Phase 01 - The generator asks the page for its address

**Strategic spec:** [`../S1803_sitemap-one-url-per-record.md`](../S1803_sitemap-one-url-per-record.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Teach the sitemap builder to walk each indexable record's files and emit the address each page declares, honour a per-record exclusion list, and refuse when the declared set and the existing set disagree in either direction.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] A snapshot of today's `sitemap.xml` is captured before the first edit, for the diff in step 01.4.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/document_registry/generate.ps1` | Modified | ≤ 260 |
| `scripts/document_registry/validate.ps1` | Modified | ≤ 200 |
| `sitemap.xml` | Modified (generated - never hand-edited) | n/a |
| `docs/DOCS_MAP.md` | Modified (generated - never hand-edited) | n/a |

> No Kotlin, no resources, no app module. This phase is repository tooling.

---

## Steps

### Step 01.1 - Capture the current declared set

**Files:** `PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Record every `<loc>` in today's `sitemap.xml`, one per line, plus the total count, into `PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt`. Capture before any edit.

**Why:**

Strategic §7 names a record without new fields silently changing behaviour as a risk whose consequence is an unnoticed edit to groups this ticket never meant to touch; without the pre-change set there is nothing to prove that every address which disappeared was meant to.

**Verification:**

- `Glob` - `PLAN/S1803_sitemap-one-url-per-record/evidence/sitemap-baseline.txt` exists.
- The recorded count equals the `<url>` count in `sitemap.xml`.

**Status:** `[x]` done

---

### Step 01.2 - Emit one entry per page that declares an address

**Files:** `scripts/document_registry/generate.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> For each record with both publication flags set, resolve its path patterns to files, read each file's declared address from its front matter, and emit one sitemap entry per address found. A file whose front matter declares no address is skipped silently. Keep the record-level address as the group's entry point: it stays in the output and keeps its language alternates where the record declares them, so today's three-locale entries do not lose their `hreflang` pairing. A record that resolves to no page at all falls back to its declared address, which is how the hand-authored root pages keep working.

**Why:**

Strategic ADR-1 puts the address on the page because that is the only place it cannot drift from reality, and ADR-2 makes a missing declared address the marker of a non-page - which is what lets a group hold an authoring checklist beside its guides without that checklist reaching search.

**Verification:**

- `Grep` - the sitemap loop reads the record's path patterns, not only its address field.
- `Grep` - a front-matter address parse exists and returns nothing for a file without one.
- `scripts/document_registry/generate.ps1` exits 0.
- `Grep` - `scenario-` matches in `sitemap.xml` at least nine times.

**Status:** `[x]` done

---

### Step 01.3 - Accept and honour a per-record exclusion list

**Files:** `scripts/document_registry/generate.ps1`, `scripts/document_registry/validate.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Let a record carry an optional list of pages that are deliberately not announced, each entry naming the path and a reason. The generator skips those pages; the validator refuses an entry whose reason is empty and an entry naming a file that does not exist. A record without the field announces every page it resolves to.

**Why:**

Strategic §2.2 requires a deliberately unannounced page to carry its reason where the decision lives rather than in someone's memory, and §7 names the list turning into a dumping ground as the risk that a required reason plus an existence check is there to prevent.

**Verification:**

- `Grep` - the exclusion field name appears in both scripts.
- `scripts/document_registry/validate.ps1` exits 0 on the current registry, which carries no such field yet.
- A temporary record with an empty reason makes `validate.ps1` exit non-zero; revert it after checking.

**Status:** `[x]` done

---

### Step 01.4 - Refuse a hand-written address that resolves to nothing

**Files:** `scripts/document_registry/validate.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a check that fails when a record's hand-written address - its entry point or one of its translated siblings - matches no page under that record's own path patterns. Report every offender, not just the first. Prove each new refusal fires by running the validator against an isolated probe tree carrying one deliberately broken record per rule, then delete the probe.

**Why:**

Strategic §11.2 makes "every address leads to an existing file" a completion criterion, and a sitemap entry answering with an error is worse than an unannounced page. Expanded addresses need no such check because they are read off the page that owns them; only the hand-written ones can go stale, which is exactly what happened to the Wear group before S1801 moved its address.

> **Plan corrected during implementation.** This step originally asked for a check that a resolved page is "neither announced nor excluded", and for an inventory file listing the offenders for Phase 02. Both are unreachable by construction: after step 01.2 the generator announces every page a record resolves to unless the record excludes it, so an unclassified page cannot exist and the inventory would always be empty. The surviving useful half - the address that resolves to nothing - is what this step now asks for. Phase 02 therefore works from the regenerated sitemap diff rather than from an inventory file.

**Verification:**

- `scripts/document_registry/validate.ps1` exits 0 on the current registry.
- Against a probe tree, it exits 1 and names each broken record separately: an address matching no page, an exclusion whose reason is too thin, and an exclusion naming a missing file.
- `Glob` - `PLAN/S1803_sitemap-one-url-per-record/evidence/phase01-evidence.txt` records that probe run verbatim.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable: no application source is touched.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The generator announces every page a record resolves to, minus what the record withholds, and the validator refuses a hand-written address that resolves to nothing plus an exclusion that is unusable or names a missing file. The sitemap grew from 19 addresses to 73, and the diff of what was added is the work list for Phase 02.

The first exclusion was applied here rather than deferred, because expansion surfaced it immediately: `docs/SETTINGS_REFERENCE_noLegal.md` is the settings reference for the sideload-only flavor whose landing pages the registry already keeps out of the sitemap deliberately. Announcing it would have contradicted a decision the registry already records.

---

## Rollback Plan

Revert the phase commit and regenerate; both generated artifacts rebuild from the registry, which the phase did not modify.
