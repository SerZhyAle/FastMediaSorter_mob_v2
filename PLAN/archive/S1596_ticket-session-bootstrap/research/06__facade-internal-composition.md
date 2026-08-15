# S1596 research 06 - composition or inlining

**Resolves:** strategic §6 item 6, ADR-1
**Performed:** 2026-08-12, from research 00 and research 05.

## The three options, costed

Baseline today: **5 top-level pwsh launches, 5 turns**. `spec-next-preflight.ps1` additionally spawns 3 + N children (N = candidates walked, `-MaxScan` 25), so the true process count today is roughly 8 + N.

| Option | Top-level calls | Total processes | Turns | Duplication |
| --- | ---: | ---: | ---: | --- |
| Compose every component as a child | 1 | ~8 + N, unchanged | 1 | none |
| Compose the mutating ones, inline the reads | 1 | ~5 + N | 1 | ranking, skip-cache, drift logic duplicated |
| Inline everything | 1 | 1 | 1 | selection, lease and device logic all duplicated |

## Decision

**Compose every component as a child process.** Turns drop 5 to 1, which is the entire point; wall clock is unchanged, which satisfies strategic §3.2's "not worse than today"; nothing is duplicated.

Full inlining buys milliseconds and pays with a second implementation of ticket selection - the exact failure strategic §7 lists as "two different answers to what the next ticket is, drifting apart over time". Research 05 makes it worse: four of five components have live callers outside session start, so an inlined copy would not even replace them, it would sit beside them.

## Rejected refinement, and why it is recorded

Inlining only `device-ready.ps1` is tempting - it is read-only, has no catalog logic, and is the one component whose result immediately feeds another call. It is still rejected: `.claude/commands/spec-dev.md` calls it independently at the device-test gate, so an inlined copy is a second device-probe implementation that will drift from the first the next time adb behaviour changes.

## Consequence for the package's own contract

Because the package is a composition, it inherits its children's failure modes rather than replacing them. Each block therefore reports the child's own exit code and reason verbatim, and the package's own exit code says only how many blocks failed - never a reinterpretation of why. This is what strategic §3.2 "Отказоустойчивость" and §5.1's per-block status requirement mean in practice.
