---
name: probe-predicate-names-timber-form
description: A phase Done Criterion forbidding debug probes must grep `Timber.d("Sxxxx`, never a bare `Sxxxx` - the bare form also matches legitimate rationale comments and makes the plan self-contradictory
metadata:
  type: feedback
---

When a tactical phase file writes a Done Criterion meant to keep debug probes out of a phase, the predicate
must be `Grep - Timber.d("Sxxxx` returns zero hits. Never a bare `Grep - Sxxxx returns zero hits`.

**Why:** the bare form also matches a rationale comment naming the ticket - `// S1314: plain classes on
purpose ..`, `* S1331 - FragmentManager rebuilds a restored dialog ..` - which is legitimate, encouraged by
the comment-discipline rule, and explicitly accepted by `assert-no-ticket-logs.ps1` (verified 2026-07-31:
the gate exits 0 with three such comments in the tree, because it inspects log calls, not comments). The
result is a plan that contradicts itself: one step orders a comment naming the ticket, and the phase's own
Done Criteria forbid the string. Hit twice in one day, in two independently authored plans (S1331 phases
01-05, S1314 phase 02), and in both cases an implementing agent stopped to report the contradiction rather
than resolve it - so it costs a round trip every time.

The bare form has a second failure mode even without comments: `/spec-dev` inserts the probes as its final
edit, so on the `BlockNeedUserTest` path the criterion is false the moment the pipeline does its job
correctly.

**How to apply:** when authoring a phase file in `/spec-tech`, or when reviewing one before `/spec-dev`
starts, rewrite any bare-`Sxxxx` zero-hit criterion to the `Timber.d("Sxxxx` form. If the phase genuinely
must forbid the ticket id everywhere - rare, and usually wrong - say so explicitly and say why, so the next
reader does not treat it as a typo. Related: [[blockneedusertest-status-before-gate]],
[[per-phase-debug-tags-break-ticket-log-gate]].
