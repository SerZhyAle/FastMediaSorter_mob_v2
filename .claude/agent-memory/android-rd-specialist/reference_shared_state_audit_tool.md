---
name: shared-state-audit-tool
description: S0703 tool + pattern for detecting multi-layer / unsafe shared-state mutation (UI views + data carriers)
type: reference
---

Recurring bug class: one shared object (a view, or a data carrier) is mutated from several layers with no single authoritative writer -> last-write-wins races, dead/dimmed UI, redundant work. Seed: the browse list/grid toggle showed disabled instead of GONE because the semantic writer set GONE while the generic command-bar partition manager force-re-showed it via an indirect `view.isVisible =` (an `alwaysEligible` candidate). Fix pattern: a generic partition/layout manager must CONSUME eligibility, never override a semantic GONE - route every visibility decision through eligibility.

Detection tooling (S0703, Verified 2026-06-26):
- Stage 1 harvester: `scripts/quality/audit-shared-state-writers.ps1` (`-Surface ui|data|all`, `-Top`, `-MinWriters`, `-Json`). Regex pre-filter over app_v2/src + wear/src; groups writers by ownership domain (binding type / carrier), flags `generic-loop-writer` / `no-single-owner` / `cross-scope-write`, ranked report + JSON to temp/. Over-inclusive by design (same-named local carriers across files may merge).
- Stage 2 agent prompt: `scripts/quality/shared-state-audit-prompt.md` - authoritative for indirect writers + concurrency; refutes candidates, lists survivors as `/spec-draft`.
- Documented in `docs/DEV_OPS.md` under TEST & VERIFY.

**Why:** id-only grep misses the most dangerous writers (indirect: through a local/loop/`it` var, `apply{}`, helpers) - exactly the class behind the seed bug.

**How to apply:** before treating a multi-writer view/state as a bug, run stage 1, group by ownership domain (NOT name - cross-screen same-name ids are false positives), then adjudicate via stage 2. Known unadjudicated hotspots from first run: player `progressBar` (7 writers), data `_uiState`/`_state` (cross-scope). Verify the tool paths still exist before recommending - read live tree.
