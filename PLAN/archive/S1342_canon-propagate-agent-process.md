# Specification: S1342 - Propagate the agent-process findings through the canon

**Ticket:** S1342
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-31
**Tier:** 2
**Parent:** S1338
**Blocked by:** S1338, S1339, S1340, S1341 - propagate only what has been proven here
**Source:** `dev/AGENT_PROCESS_AUDIT_2026-07-31.md`

---

## 1. Why

Owner instruction, 2026-07-31:

> После внедрения мне нужно распространить инновации через канон на мои прочие проекты

The audit measured one workspace, but almost nothing it found is Android-specific. Cost being `context x turns`, cache reads dominating spend, gated rules holding at 99% against 1-8% for prose, a measurement method that silently triples every token count - these are properties of working with Claude Code, not of this repo.

The canon at `p:\WEB\sza-unified-rules\` carries **11 contrib records**, so a correct propagation reaches roughly ten other projects. `rules/AI_USAGE.md` already has the right homes: section 3 "Cost & parallelism discipline", section 4 "Persistent memory", section 5 "Rules file & skill routing".

This repo is overlay B and the reference the core was extracted from, and its own CLAUDE.md states the direction of travel: universal rules are fixed in a canon session, not here. So this ticket is the bridge, and it deliberately runs **after** the local work, because the canon must receive proven practice rather than a hypothesis.

---

## 2. The portability bar

The canon's value is its leanness. The bar for admission is not "this was a good idea here" but "this survives stripping Kotlin, Gradle, PowerShell, Android and the Sxxxx lifecycle".

Apply it per finding, in three buckets.

### 2.1 Canon-grade - the principle is universal

- **The cost model.** Cost is accumulated context multiplied by turns; within one unbroken block it is quadratic. Cache reads dominate spend; output verbosity is a minor term. Any project whose agent sessions run long has this shape.
- **Session boundaries as the primary lever.** A threshold-triggered halt with a resume handle, and the constraint that an agent cannot execute `/clear` or `/compact` itself, so any design assuming self-reset is unbuildable. This constraint is a property of the harness and applies everywhere.
- **Gate or compress.** Measured 99% against 1-8%. State the measurement, not just the maxim - the numbers are what make it persuasive in another repo. Corollary worth stating plainly: an unenforced rule is not neutral, it teaches that rules are optional.
- **The 22% datum.** The advice to read with `offset`/`limit` ships in the built-in Read tool schema on every turn and gets 22% compliance. This is the single most useful number for settling any "should this be a rule or a hook" argument in any project.
- **Transcript measurement method.** Deduplicate by `requestId`; walk `<session-id>/subagents/**`; classify hard failures by `is_error` only and never by regex over a result body. Without these, every cost number in every project is inflated ~3x and every failure rate ~3.4x. This is the highest-value single item to propagate, because it is what makes all the others measurable.
- **Statusline reports magnitude, not window fraction.** On a large window a fraction hides the cost at exactly the moment it peaks.
- **Two-tier model routing.** Opus for judgement, Sonnet for procedure, with the boundary drawn at "would a merely plausible answer be wrong here". Also the mechanical fact that command frontmatter does not route - 115 of 115 invocations kept the session model - which will otherwise be re-discovered in every project.
- **Memory discipline.** A byte budget on the always-loaded index, an expiry keyed to work-item liveness, and a ban on memory restating the rules file. Backed by: 40% of memory bytes never read, written 2.3x more often than consulted, 55% anchored to dead work items.
- **Closure-facade invariants.** A verdict must cover every file in the change; PASS must mean every gate passed; a script must distinguish "found a defect" from "could not verify". The scripts differ per project; the invariants do not.
- **Read discipline as a hook with a mandatory escape hatch.** The pattern generalises: block the blind first read, always allow an explicit re-issue.

### 2.2 Overlay B only - stays in the contrib record

Android and this toolchain: kapt to KSP, detekt configuration cache, the flavor matrix, `a.ps1` targets, the specific `assert-*` inventory, the Sxxxx lifecycle mechanics, `post-change.ps1` parameters, the emulator harness.

These go to `rules/contrib/fastmediasorter_mob_v2.md` as deltas, not to `rules/`.

### 2.3 Explicitly not propagated

Everything in section 8 of S1338. Carrying a refuted recommendation into ten projects is worse than never having audited: it is a measured non-problem given permanent shelf space. In particular do not export prose trimming, re-read suppression, subagent-count tuning, the UserPromptSubmit pricing hook, or command-surface deletion on cost grounds.

---

## 3. Work

1. **Re-measure first.** Run S1338 package A over the corpus again after the local changes have been live two weeks. Propagate outcomes, not intentions. A canon rule sourced from an unmeasured estimate is exactly the failure this whole audit began with.
2. **Author the section 2.1 items into `rules/AI_USAGE.md`** in a canon session - sections 3, 4 and 5 are the homes. Follow `dev/RULE_AND_SKILL_AUTHORING.md`: observe the failure first, write minimal, close the rationalizations. Each rule carries its measurement, because the number is the argument.
3. **Decide whether any item is invariant-grade.** The canon's hard-invariant page is twenty lines and deliberately short - it holds things that are expensive, irreversible or outward-facing. Agent cost is none of those, so the default answer is no. The one candidate worth arguing is the measurement method, on the grounds that a wrong number propagates into every later decision.
4. **Ship the portable tooling as canon skills or scripts**, not as copied files. The transcript extractor and the statusline are stack-agnostic; the gate runner and the closure facade are not, but their invariants are. Prefer one skill that carries the method over ten copies of a script.
5. **Update `rules/contrib/fastmediasorter_mob_v2.md`** with the section 2.2 deltas and a "Spread-back applied" entry, matching the existing sections in that file.
6. **Re-stamp `.sza-canon.json`** with the new canon version and `coreDigest` after the canon changes land.
7. **Adopt into the other projects** with the `adopt-canon` skill, one at a time, checking each against its own shape. Ten repos with `contrib` records exist; not all of them run long agent sessions, and the session-boundary rule is close to inert in a repo whose sessions are short.
8. **Consider the Universal Agent Kit separately.** `p:\WEB\universal-agent-kit\` is the public distillation and has a different bar - portability plus net signal against deliberate leanness. Several section 2.1 items are strong candidates, particularly the 22% datum and the measurement method. This is the owner's call per item, not an automatic consequence of the canon change, and the kit's zip needs an explicit rebuild if `kit/` changes.

---

## 4. Sequencing constraint

Do not start before the local work is measured. The whole point of the canon being REFERENCE-model here is that this repo proves a practice before the portfolio adopts it. Propagating S1339's threshold before knowing whether 300,000 was the right number would push an unverified constant into ten repositories, where correcting it costs ten times as much.

---

## 5. Acceptance

- Every item in section 2.1 either appears in `rules/` with its measurement, or has a recorded reason for exclusion.
- No item from S1338 section 8 appears anywhere in `rules/`.
- `rules/contrib/fastmediasorter_mob_v2.md` carries the overlay-B deltas and a dated spread-back entry.
- `.sza-canon.json` in this repo re-stamped, and `adopt-canon` reports the stamp current.
- At least one other project has adopted and reported the result, before the remaining ones follow.

---

## 6. Risk

The main risk is propagating too much. The canon became unreadable once before, which is why the hard-invariant page exists and why the reference documents are structured the way they are. Nine findings that are individually true can still be collectively harmful if they turn a lean rule set into a second audit report.

Bias toward the smallest formulation that carries the measurement, and toward a skill that can be loaded on demand over a rule that must be read on every turn - which is, itself, the audit's central lesson applied to the canon.

---

## 7. Delivery (2026-08-02)

### 7.1 The sequencing constraint was overridden, on the owner's instruction

Section 3 item 1 and section 4 require the corpus to be re-measured after the local changes have been
live two weeks. S1341 reached `Verified` on 2026-08-01, so that window closes around **2026-08-15**.
The case for waiting was put to the owner - propagating S1339's threshold before knowing whether
300,000 was the right number pushes an unverified constant into ten repositories - and the owner chose
to propagate in full on 2026-08-02 anyway.

**What that costs, stated precisely rather than hand-waved.** The methods, invariants and observations
in section 2.1 were measured before this ticket started and do not depend on the missing window. The
tuned constants do. So the canon received the *shape* of the session-boundary rule - stop at a
threshold and hand back a resume handle, and note that an agent cannot reset its own context - and
deliberately **no number**. The one thing the window would have validated is the one thing that did not
travel. Re-measure after 2026-08-15 and correct `rules/contrib/fastmediasorter_mob_v2.md` if the local
result disagrees.

### 7.2 Re-measurement, such as it is

`scripts/metrics/agent-cost-report.ps1 -Since 2026-06-30 -Until 2026-08-02`: expected exit 0 | actual
**0**. Artifact `temp/S1338/transcript_metrics.json`.

| Metric | S1338 §6.1 baseline (07-31) | §6.2 (08-01) | Now (08-02) |
| --- | --- | --- | --- |
| Sessions | 1,153 | - | 885 |
| Unique requests | 64,698 | 65,882 | 64,061 |
| All-in cache_read | 14.26 G | 14.47 G | 14.82 G |
| Hard tool-failure rate | 2.71% | 2.72% | 2.78% |
| With the soft band | 5.81% | 5.82% | 5.94% |
| Compactions | 160 | 160 | 156 |
| Pre-compaction `preTokens` p50 | 389,197 | 389,197 | 391,161 |
| Pre-compaction `preTokens` p90 | - | 648,995 | 648,995 |

**Read this as a carry-forward, not an effect** - the same caution §6.2 carries, one day further on.
Two things in the table are worth naming rather than glossing:

- **Requests and sessions fell while cache_read rose.** 885 sessions against 1,153, and 64,061 requests
  against 65,882 the day before, on a window that is one day *longer*. That is corpus pruning, the same
  drift §6.1 already identified - transcripts are being removed faster than they accrue. It means the
  absolute totals are not comparable across runs, and only the rates and percentiles are.
- **The compaction median did not move: 389,197 -> 391,161.** S1339's threshold has been live for one
  day, which is far too short to appear in a p50 computed over five weeks. An unchanged number here is
  the expected result, not a failure - and it is the single metric the two-week window exists to move,
  which is the whole argument §7.1 records.

### 7.3 What landed

- **`rules/AI_USAGE.md`, five sections.** §1's one-sided backgrounding rule replaced with a two-sided
  threshold; §2 gained the three closure-facade invariants; §3 gained the cost model, session
  boundaries with the harness constraint, magnitude-not-fraction context reporting, two-tier model
  routing, explicit-range reading, and a pointer to the new skill; §4 gained a budget on the
  always-loaded index, liveness-keyed expiry and the written-more-than-read observation; §5 gained
  gate-or-compress, the 22% datum and the driver-plus-reference shape.
- **House voice: a narrow, deliberate break.** The file cited no measurements before today. Two numbers
  went in, because in those two cases the number *is* the argument - 99% against 1-8% for gated versus
  ungated compliance, and the 22% compliance on advice that already ships on every turn. Everything
  else was reduced to a principle.
- **`skills/agent-cost/SKILL.md` and `tools/mine-agent-transcripts.py`** in the canon. The extractor was
  written stack-agnostic from the start under S1338 §9, so this was a copy rather than a port - which
  is the portability constraint paying off exactly as intended.
- **Invariant-grade: no.** Section 3 item 3's default answer stands, and the page argued it first: its
  own "What is deliberately not here" paragraph already names *memory discipline* and *CI cost levers*
  as standing exclusions, and its admission criterion is expensive, irreversible or outward-facing. A
  wrong cost number is none of the three - nothing ships and the remedy is to measure again. The page
  is also exactly twenty lines by design, so a twenty-first means displacing a release or security
  invariant, and nothing here does.
- **Canon `2026.07.30 -> 2026.08.02`**, core digest `sha256:74832f28..` -> `sha256:6c247452..`.
- **All nine adopting repos re-stamped and each given a reconcile entry** naming what applies there.
  Two findings worth carrying: `Streams_Player` is the only other repo with a committed agent memory,
  so §4 lands live there rather than inert; and three repos use a per-user memory store, where the
  budget transfers but the liveness-keyed expiry has no work-item system to key against.

### 7.4 Verification

- `tools/check-rules.ps1`: expected exit 0 | actual **0** (19 core docs, 11 contrib docs), re-run after
  every append.
- `tools/check-compliance.ps1` per repo, all against canon 2026.08.02: FastMediaSorter_mob_v2 **0
  errors / 1 warning**, FastMediaSorter_Lite **0 / 10**, EPUB_2_HTML **0 / 11**, FileDo **0 / 4**,
  OneClickRunner **0 / 2**, Streams_Player **0 / 4**, internal_IP_manager **0 / 1**, hub **0 / 0**,
  CyrFlip **1 error / 5**.
- **CyrFlip's error is real, pre-existing and deliberately left open:** two em/en-dashes in
  `msix/store-listings.md`. It has nothing to do with this change, and it is outward-facing store copy -
  not something to silently rewrite from a session working on another project. Recorded in that repo's
  contrib entry so it is fixed from a CyrFlip session, where the listing text can be reviewed for what
  it says and not only for its typography.

### 7.5 Open, and why

- **The two-week re-measurement**, per 7.1. This is the only reason the ticket is not `Verified`.
- **Section 3 item 8, the Universal Agent Kit.** `p:\WEB\universal-agent-kit\` carries no
  `.sza-canon.json` and no rules file, so it is not an adopter and was not swept. The spec makes
  admission there an owner decision per item, not an automatic consequence of a canon change; the 22%
  datum and the measurement method are the two strongest candidates. Left for the owner.
