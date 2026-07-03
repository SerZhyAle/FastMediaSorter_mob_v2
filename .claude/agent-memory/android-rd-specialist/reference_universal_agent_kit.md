---
name: universal-agent-kit
description: Owner maintains a public stack-agnostic distillation of THIS project's method (rules/skills/roles/spec-lifecycle/memory) as a site + downloadable kit
metadata:
  type: reference
---

Owner publishes **Universal Agent Kit**: a portable, stack-agnostic distillation of THIS
project's working method (the constitution, skills pipeline, subagent roles, spec lifecycle,
persistent memory, validation ladder, anti-slop). MIT (kit) / CC BY 4.0 (prose).

- Site: <https://serzhyale.github.io/universal-agent-kit/> - trilingual RU/EN/UK, sections 00-12,
  `#old-ru`/`#new-ru` anchors are the existing/new-project adoption prompts.
- Repo: github.com/SerZhyAle/universal-agent-kit (zip + merge-prompt.txt).

**Why:** when the owner asks "should X go in the kit?", "review the kit", or "does the kit cover Y",
this is the artifact - and the bar is PORTABILITY (survives stripping Kotlin/Gradle/PowerShell/Android/
locale) plus net-signal vs the kit's deliberate leanness, not just "is it a good practice here".

**How to apply:** a 2026-07-02 full review (temp/uak_site_review/UAK_SITE_REVIEW.md, ephemeral;
93 findings, 89 verified) supersedes the 2026-06-25 gap review. All 4 omissions from that review
(ratchet baselines, delegation hazards, lifecycle gates, working-tree caveat) are NOW PRESENT in the
kit. Open portable gap candidates: cost discipline (COST.md), fan-out budget gate, verification-of-
findings discipline, ratchet resurface trap, model-tier routing, BNUT drain sweep, dirty-tree closure
policy, argue-then-obey. The 6 dropped candidates (post-change facade, dedup-by-symptom,
deprecated-API wrapper, fix-tooling, N-phase process, route-to-docs) stay dropped - don't re-add.
Verified NON-issues (don't flag again): `Bash(git *)` space-wildcards are valid Claude Code syntax;
top-level `"agent"` settings key is real and documented; repo/LICENSE does contain the CC BY 4.0
notice after the MIT text.
