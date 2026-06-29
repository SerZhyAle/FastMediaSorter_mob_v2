---
name: rg-gitignore-skips-catalog-zone
description: bash rg silently skips files under the partially-gitignored dev/CATALOG zone; verify catalog facts with the Grep tool / --no-ignore / direct Read, never a bare bash rg
metadata:
  type: feedback
---

A bare `bash rg` (ripgrep honoring .gitignore) silently skips files under `dev/CATALOG/` because that zone is partially gitignored (`<module>.jsonl` / `<module>.md` are ignored and the rules shadow the subtree for default rg). A "no matches" result there is NOT proof of absence.

**Concrete incident (2026-05-31, S0311 decomposition):** `rg -l "hasTests" dev/CATALOG/` and `rg -rn "hasTests|Test-HasTests" dev/CATALOG/scripts/` both returned empty, so I asserted "the catalog has no `hasTests` field" and wrote that false fact into S0311 §3.3 and S0314. In reality `hasTests` is defined in `dev/CATALOG/scripts/scan.ps1` (`Test-HasTests`), filtered in `query.ps1` (`-Tests`/`-NoTests`), rendered in `render.ps1`, and documented in `dev/CATALOG/README.md`. A subagent caught it; the **Grep tool** (also ripgrep, but different ignore handling here) found every line the bash rg missed.

**Why:** ripgrep respects .gitignore by default. Inside a gitignored zone the empty result is an artifact of the ignore rules, not the codebase.

**How to apply:** To verify whether a class / field / function / token exists anywhere under `dev/CATALOG/` (or any gitignored path), do NOT trust bare `bash rg`. Use the **Grep tool**, or `rg --no-ignore`, or a direct `Read` of the specific script. Treat any strategic-spec "current repository facts" claim built on bare bash rg over a gitignored zone as unverified until reconfirmed. Mirror of [[feedback_verify_subagent_build_failures]] - that one is "don't over-trust the subagent"; this one is "the subagent may be right and YOU wrong - verify both sides against the live file".
