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
- Repo: github.com/SerZhyAle/universal-agent-kit; local clone `p:\WEB\universal-agent-kit\`.
  Package = `kit/` (CLAUDE.md template + `.claude/commands|agents` + `docs/` + `memory/`),
  implementation prompt = `merge-prompt.txt` (3-step INVENTORY->PLAN->APPLY).
- **Distributables have no in-repo generator; regen procedure (agent-run 2026-07-19):**
  rebuild `universal-agent-kit.zip` = stage `kit/` as folder `universal-agent-kit/` + repo-root
  `merge-prompt.txt` at archive ROOT, then `Compress-Archive -Path <stage>/* -Force` (PS7 gives
  `/` separators, matches original). `index.html` (~183KB) is HAND-AUTHORED trilingual
  (`data-lang` en/ru/uk), NOT a render of `kit/`: embeds NO verbatim doc/merge-prompt/CLAUDE text
  (only a few woven taglines like "green can lie"), enumerates no doc/command list - so package
  edits do NOT make it stale and it needs no edit unless adding NEW marketing content (placement
  is owner's call, don't fabricate). Publish = `git commit` + `git push origin main` (Pages serves
  `main`, ~1 min deploy; direct-to-main is the owner's own pattern). Editing `kit/` leaves the zip
  stale until rebuilt.

**Why:** when the owner asks "should X go in the kit?", "review the kit", or "does the kit cover Y",
this is the artifact - and the bar is PORTABILITY (survives stripping Kotlin/Gradle/PowerShell/Android/
locale) plus net-signal vs the kit's deliberate leanness, not just "is it a good practice here".

**How to apply:** 2026-07-19 update (from an obra/superpowers cross-review) added THREE portable
disciplines to the kit: `docs/AUTHORING.md` (test-first-for-rules: observe the failure first,
rationalization tables, description-SDO), verification red-flags ("should/probably/seems -> stop and
run it") in `docs/VALIDATION.md` + `CLAUDE.md` §10, and filled trigger `description:` frontmatter on
ALL 18 commands (agents already had SDO descriptions; commands shipped empty - same defect that FMS
`.claude/commands/*` had). Superpowers verdict: don't install the plugin (git-worktree/branch model
+ TDD iron-law + trigger collisions); harvest the meta-disciplines only.
Already PRESENT (do NOT re-flag as gaps): COST.md + model-tier routing, ratchet-resurface trap
(CODE_QUALITY.md), dirty-tree closure policy + "a green can lie" (VALIDATION.md), the four 2026-06-25
omissions. Still-open portable candidates: fan-out budget gate, BNUT drain sweep, argue-then-obey,
systematic-debugging root-cause phases (kit has `/fix` but no debug discipline doc). The 6 dropped
candidates (post-change facade, dedup-by-symptom, deprecated-API wrapper, fix-tooling, N-phase
process, route-to-docs) stay dropped - don't re-add.
Verified NON-issues (don't flag again): `Bash(git *)` space-wildcards are valid Claude Code syntax;
top-level `"agent"` settings key is real and documented; repo/LICENSE does contain the CC BY 4.0
notice after the MIT text.
