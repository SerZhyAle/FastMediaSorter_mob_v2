# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1803_sitemap-one-url-per-record.md`](../S1803_sitemap-one-url-per-record.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Make the classification check standing rather than one-off, write down the rule for whoever adds the next page, and close the ticket through the facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] The validator reports zero unclassified pages.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/document_registry/README.md` or the registry's own documentation entry | Modified | ≤ 40 added lines |
| `.claude/skills/document-registry/SKILL.md` | Modified | ≤ 15 added lines |
| `dev/CHANGELOG.md` | Modified (script-owned - never hand-edited) | n/a |

---

## Steps

### Step 03.1 - Confirm the check runs where changes are closed

**Files:** `scripts/document_registry/validate.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Confirm the unclassified-page check runs as part of the registry validation the closure facade already invokes for a registered document, rather than only when someone runs the validator by hand. If it does not, wire it there. Do not add a second, parallel gate.

**Why:**

Strategic §2.3 says adding a page to an existing group must not require a registry edit for that page to be announced, which is only safe if the opposite case - a page that should have been excluded - is caught automatically; a check that runs only by hand catches nothing on the day it matters.

**Verification:**

- Adding a temporary page file under an existing group and running the facade on it reports the page as newly announced or unclassified; remove the temporary file afterwards.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0 on the clean tree.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.1: the unclassified-page check did not exist anywhere - validate.ps1 only checked the opposite direction, that a hand-written address resolves to a page - so it was wired into that same validator rather than added as a second gate, which is what the closure facade already invokes for a registered document. The rule mirrors the generator instead of inventing a parallel one: a file under an indexable record must declare its own permalink, be named in sitemap_exclude, or be the source of an address the record itself declares (the site root is backed by index.html or README.md, which is why site-landing's four members pass without front matter). The error names the fix rather than the fact. Verified on the real thing, not by reading: clean tree exit 0; a probe file docs/howto/zzz-s1803-probe.md dropped into an existing group made it exit 1 with 'user-guides: docs/howto/zzz-s1803-probe.md is neither announced nor excluded'; probe removed, exit back to 0, file confirmed gone. First probe attempt landed at docs/ZZZ_S1803_PROBE.md and was NOT caught - the group's globs are per-family (docs/HOW_TO*.md and friends), not docs/*.md, so the second attempt was placed inside a real glob.

---

### Step 03.2 - Write down the rule for the next author

**Files:** `scripts/document_registry/README.md`, `.claude/skills/document-registry/SKILL.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Describe the new model in the registry's own documentation: the page declares its address, the record decides publication for the group, a page that must not be announced is named in the record with a reason, and a file without a declared address is not a page. Add one sentence to the document-registry procedure pointing at that description, so the loop that everyone already runs mentions it.

**Why:**

Strategic §2.2 puts the reason for hiding a page beside the decision, but the rule that produces those decisions has to live where an author looks; without it the next person adds a page and guesses whether a registry edit is needed, which is the confusion this ticket exists to remove.

**Verification:**

- `Grep` - the exclusion field name appears in the registry documentation.
- `Grep` - the document-registry procedure references the new model in at least one sentence.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.2: scripts/document_registry/README.md written - it did not exist, so the rule had no home at all. Four sentences carry the model: the page declares its own address, the record decides publication for the group, a page that must not be announced is named in sitemap_exclude with a re-judgeable reason, and a file without a declared address is not a page. It also states what validate.ps1 enforces and what to do when adding a page. One paragraph added to .claude/skills/document-registry/SKILL.md pointing at it, so the loop everyone already runs names the model. Verified: sitemap_exclude appears 5 times in the registry README and once in the skill.

---

### Step 03.3 - Re-run the whole verification chain from clean

**Files:** `PLAN/S1803_sitemap-one-url-per-record/evidence/final-verification.txt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run validate, generate and generate with the freshness check in that order, plus the address-resolution pass from step 02.4, and record every exit code in `PLAN/S1803_sitemap-one-url-per-record/evidence/final-verification.txt`. Record the final counts: pages, announced, excluded.

**Why:**

Strategic §11.1 states the completion criterion as an equality between three counts, and the only way to claim it is to record all three from one run rather than from three runs taken at different moments.

**Verification:**

- `Glob` - `PLAN/S1803_sitemap-one-url-per-record/evidence/final-verification.txt` exists and records four exit codes, all zero.
- The recorded equality holds: announced equals pages minus excluded.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.3: PLAN/S1803_sitemap-one-url-per-record/evidence/final-verification.txt records four exit codes, all zero - validate, generate, generate -Check and the address-resolution pass - each read from the process rather than from a pipe. Final counts from one run: 85 files under the seven indexable records, 11 excluded with a reason, 70 announced through their own front matter, 73 sitemap entries. The equality is written out rather than asserted: 85 - 11 - 4 landing sources = 70, and 70 + the 3 addresses the site-landing record declares itself = 73. The four landing files are not a gap - README.md and index.html both source the root, which is exactly the case the validator's new check treats as accounted for. Address resolution: 73 entries, 0 unresolved. Note for the record: running resolve.py through the PowerShell tool returned 9009 with a bare Python banner; the same script from Bash exits 0, so the chain was run where the interpreter behaves and the anomaly is a tool-boundary artifact, not a script defect.

---

### Step 03.4 - Close the whole changed set through the facade

**Files:** every file this ticket touched
**Depends on:** Step 03.3

**Prompt for developer:**

> Close with `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<the whole set>" -ScopeToFile -Target "spec-all" -Description "S1803: sitemap announces every published page" -ChangeType Tooling`. Use `Tooling` because the set spans repository scripts and generated documentation. Read the verdict line rather than the exit code alone.

**Why:**

The repository requires mechanical closure through the facade, and naming the whole set with `-ScopeToFile` is what makes the scoped gates judge this ticket rather than other work in flight on the always-dirty tree.

**Verification:**

- `scripts/post-change.ps1` exits 0 and prints `post-change: PASS`, or names every advisory if it prints `PASS WITH ADVISORIES`.
- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.4: closure facade over the ticket's six-file set - the validator, its new README, the document-registry skill, the registry itself and both generated views - returned post-change: PASS (Tooling), exit 0, no advisories. -RegistryAck passed up front, since .claude/** is a registered surface and the run would otherwise have stopped on it. Script cheatsheet checked before the run rather than after: already in sync, so the new README and the changed param-free validator needed no regeneration.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable: no application source is touched.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the ticket via the closure facade.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit. Both generated artifacts rebuild from the registry and the scripts return to their previous behaviour, which is the single-address model - reverting cannot strand a page, only stop announcing it.
