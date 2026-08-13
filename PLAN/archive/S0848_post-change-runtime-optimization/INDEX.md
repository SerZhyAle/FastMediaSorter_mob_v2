# Tactical plan: S0848 - Speed up post-change without losing gate coverage

**Ticket:** S0848
**Strategic spec:** `PLAN/S0848_post-change-runtime-optimization.md`
**Status:** Tactical

Ordering follows the owner's priority list. Each phase is coverage-preserving in isolation and independently shippable. A phase is done only when its Verification predicates hold AND a before/after parity check confirms no gate coverage was lost.

## Progress

- Phase 01 **DONE** (2026-07-01): implemented + parity-validated. `scan.ps1` full-module scan on app_v2 dropped from ~39.8s to ~5.3s (git storm removed); incremental JSONL byte-identical to full baseline (2097 records). Flavor-variant paths (shared relative path, independent git history) correctly fall back to git.
- Phase 02 **DONE** (2026-07-01): detekt runs as a `Start-ThreadJob` concurrent with the lexical gates in `post-change.ps1`; joined at the end (verdict/exit identical), `try/finally` cleans up the job on a fail-fast `exit`. Integration: detekt-gate join ~3s / ~9ms (fully overlapped) instead of a standalone ~30-50s step.
- Phase 03 **DONE** (2026-07-01): `assert-neuroslop.ps1` runs the 8 detectors in-process via the call operator (`& $path`, which isolates a child `exit`) - zero edits to the detector files, coverage byte-identical. Runtime ~20.7s -> ~8.4s standalone. Fork-vs-in-process output `diff` clean; seeded violations caught on the exact dimensions.
- Phase 04 **PARTIAL** (2026-07-01): shared `-ChangedFiles` delta helper (`lib/changed-files-delta.ps1`) + wired into `assert-flavor-flags-not-growing.ps1` and `assert-deprecated-pm-flags.ps1` (FATAL real delta under `-ScopeToFile`, ~335/323ms vs ~2886/1758ms full scan). Neuroslop children + listener-symmetry delta deferred to **S0850** (stay advisory full-scan meanwhile - no regression).

## Phases

1. [01 - Incremental catalog scan](01_incremental_catalog_scan.md) - **DONE**. Drop per-file `git log`; reuse `lastTouched` from existing JSONL for unchanged files. Lowest risk, biggest non-Gradle win.
2. [02 - Parallel detekt](02_parallel_detekt.md) - run gradle detekt concurrently with the lexical gates in `post-change.ps1`; wall-clock ~= max(lexical, detekt).
3. [03 - One-process neuroslop](03_neuroslop_one_process.md) - run the 8 neuroslop detectors in a single process instead of forking `pwsh` per child.
4. [04 - ChangedFiles ratchet delta](04_changedfiles_ratchet_delta.md) - add a real `-ChangedFiles` delta mode to the ratchet gates; wire it from `post-change.ps1 -ScopeToFile`.

## Invariants (all phases)

- No gate removed or weakened - coverage stays 1:1 with the pre-change behaviour.
- Full `catalog_sync -Module <m>` without `-ChangedFiles` stays a complete refresh (release/CI path).
- JSONL format and catalog semantics unchanged.
- New parameters are additive and default to today's behaviour when omitted.
