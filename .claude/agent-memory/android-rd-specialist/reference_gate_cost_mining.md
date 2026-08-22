---
name: gate-cost-mining
description: How to measure what the quality gates actually cost from transcripts, and the two traps that make the naive answer wrong
metadata:
  type: reference
---

Gate cost is recoverable from the session transcripts: `post-change.ps1` prints every step as
`  [<label>] <PASS|FAIL|SKIP> (<N> ms)`, so a regex over `~/.claude/projects/**/*.jsonl` yields runs,
failures and wall time per gate. Measured 2026-08-07 over three weeks: **8,562 runs, 555 failures,
2,803 minutes** of wall time.

**Trap 1 - do not count tool invocations by grepping for a script name.** A first pass counted
occurrences of `spec_catalog/`, `post-change.ps1`, `document_registry` and reported ~45,500 "tooling
invocations". Those are *mentions*: rule text, command drivers and prose in the transcript all contain
the strings. The verdict lines are the only records that mean a run happened - `document-registry`
really ran 86 times, not 6,188.

**Trap 2 - the printed ms includes waiting for `BUILD.LOCK`, not just the work.** `Invoke-Step` starts
its stopwatch before the child process, and a gradle-backed gate queues on the lock inside that window.
detekt averaged 152 s across 947 recorded runs, but a directly measured run on a warm daemon with the
configuration cache reused was **25 s**. Both numbers are true and they answer different questions:
scheduling cost versus compute cost. Say which one you mean.

**Why:** the conclusion flipped twice under these traps. The distribution is extremely skewed - detekt
alone was 86% of all gate wall time (2,409 of 2,803 minutes), and the thirteen gates that never fired
once in three weeks cost 133 minutes between them, 4.7%. "Delete the gates that never catch anything"
looks obvious and buys almost nothing while removing insurance.

**How to apply:** before proposing any gate be removed, weakened or reordered, produce the per-gate
table first and check where the mass actually is. Optimise the head of the distribution (caching,
skipping work whose inputs did not change, not queueing for a lock you do not need); leave the cheap
never-firing gates alone. See [[post-change-dev-log-first-file-only]] and
[[transcript-cost-mining]] for the dedup rules that apply to any transcript-derived count.

**2026-08-22: the journal now covers both runners, so stop mining transcripts for this.** S1937 wired
`Write-GateTelemetryRecord` into `post-change.ps1` (every PASS/FAIL/SKIP, including the skips the console
no longer prints), so `scripts/quality/measure-gate-frequency.ps1` finally reports what its own help
always claimed. Until that date only `assert-fast-gates.ps1` wrote records - ~11 runs a day against ~61
closures - so any report over the old journal answered a different question than the one asked. The same
ticket fixed the report counting `status -ne 'PASS'` as a failure, which would have turned every skip into
a failure the moment post-change started journalling.

Numbers measured that day over 2026-07-22..08-21 (transcripts, deduped by uuid), worth not re-deriving:

- `post-change` 1,837 runs, 21.9 h wall, median 22 s, p90 60 s. Verdicts 1,259 PASS / 374 advisories /
  204 FAIL (11.1%).
- `detekt-gate` alone is **738.6 min = 56%** of that wall (52 s average as scheduled, ~25 s of compute -
  the rest is `BUILD.LOCK`). The distribution has not changed shape since the first pass.
- **`..ps1 fg` costs 105 s per run, not the 14-21 s recorded earlier** - the batch grew to ~40 gates,
  each its own pwsh spawn (~0.35 s floor). 26 of its 46 gates never failed in a month, 40% of its wall.
- Removing every never-failing gate saves ~8% of closure wall and under 1% of tokens. Still not the lever.
- The never-failing gates are **not blind**: each prints what it examined (`3242 file(s) scanned`,
  `540 shared test file(s)`, `22 migration(s), 10 tested`). Check for that count before calling one useless -
  and note the inverse, `assert-migration-test-pairing` passes forever on 12 baselined untested migrations.
- Gate output costs **196 M cache-read tokens/month** (each block re-read by every later request in its
  session); 32% of that was per-step SKIP lines, now collapsed to one line by S1937.

**detekt: the gradle gate is NOT redundant with the scoped preflight - measured, do not re-propose.**
Co-occurrence in the same closure: `preflight FAIL -> gate SKIP` **50**, `preflight PASS -> gate FAIL`
**43**, `preflight FAIL -> gate FAIL` **0**. Zero false alarms and a real undercatch, so the cheap
preflight cannot replace the 87 s gate; closing the 43-case gap is the actual lever, parked as **S1938**.

**2026-08-22, same day: a THIRD runner exists now - `scripts/quality/assert-release-scope-gates.ps1`.**
Owner's call: a gate whose subject is the whole tree or a shipped artifact belongs to the release scope,
not to every changed file. Codified as CLAUDE.md Rule 33 with a four-part test, wired as step 0.4 of
`/spec-prerelease`, spec S1939. Moved: `assert-unreferenced-strings`, `assert-splash-brand-sync` (out of
`fg`), `assert-icon-inventory-sync`, `assert-doc-icons-sync`, `assert-device-profile-matrix` (out of
`post-change`). Measured effect on `fg`: **105 s -> 68 s, 8 red lines -> 5.**

Two things that fell out of doing it and are worth reusing:

- `assert-oss-notices` LOOKS like the same class (it ships inside the package) and does not pass the test:
  its own wiring comment records that both of its findings are attributable to the change that fired them.
  Applying the test honestly beats moving everything that superficially fits.
- Moving a gate breaks three things that no compiler catches: the predicate variable left behind
  (PSScriptAnalyzer flags it), an entry in `scripts/quality/gate-recovery-hints.psd1` that now names no
  label (`assert-gate-hints-sync` fails), and an assertion in `scripts/post-change.tests/Run-Tests.ps1`
  that pins the old wiring. Retarget that assertion at the new runner rather than deleting it - a gate
  that leaves one runner and is picked up by none is the exact hole the suite exists to catch.

**Still open after this (do not re-derive):** `fg` forwards `-ChangedFiles` to only 3 of the 18 gates that
accept it, which is why the 5 remaining red lines are almost all other sessions' in-flight drift rather
than the operator's own work.
