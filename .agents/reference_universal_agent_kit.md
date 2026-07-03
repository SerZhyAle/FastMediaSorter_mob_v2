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

**How to apply:** a 2026-06-25 gap review (temp/uak_review/UAK_REVIEW.md, ephemeral) found the kit's
biggest portable omissions vs the live project: ratchet baselines (adopt a rule on a dirty base by
gating new violations only), delegation hazards (parallel writers clobber via shared VCS; re-verify
subagent reports; subagents truncate the final phase), lifecycle-as-enforced-gates (mutator-only
ledger, promotion preconditions, mandatory block notes), and the working-tree-is-truth caveat vs the
kit's git-as-source-of-truth framing. The 6 dropped candidates (post-change facade, dedup-by-symptom,
deprecated-API wrapper, fix-tooling, N-phase process, route-to-docs) are already covered - don't re-add.
