---
name: research-subagent-may-lack-web-tools
description: A research subagent can lack WebSearch/WebFetch even when the parent has them, so its platform claims may be trained knowledge only - measure on device before believing them
metadata:
  type: feedback
---

A subagent spawned to research external platform behaviour may have no web tools at all, even when
the parent session does. Its answer then rests on trained knowledge, and it can be confidently and
exactly wrong while reading as a sourced report.

**Why:** On 2026-08-14 (S1202) an `android-solution-researcher` was asked whether
`WindowInsets.isVisible(systemBars())` reports `true` for transient bars on API 30+, with explicit
instructions to search developer.android.com and AOSP. It had only Read/Grep/Glob/Bash, said so in
a caveat, and answered "my recollection is the platform deliberately reports transiently-shown types
as visible .. which would mean the answer is **yes** to both halves", naming a plausible AOSP method.
Measurement on a real Galaxy S21 (API 35) that same hour showed the opposite: bars visibly on screen
while the probe read `sys=false` and no inset dispatch arrived at all. Had the report been trusted,
the fix would have kept the dead code path it was written to replace.

**How to apply:**
- For a question about how a platform actually behaves, prefer one instrumented device run over any
  amount of delegated reading. A `Timber.d` probe plus a streamed logcat answers in one build.
- When a subagent's report carries a tooling caveat, treat every claim past that caveat as a lead,
  not a fact - the caveat is usually accurate and usually ignored.
- Give a research subagent an explicit `tools:` allowlist that includes what its prompt requires; a
  prompt that says "use web search aggressively" against a tool set without it wastes the whole run.
- The same round also refuted the report's second claim by reading the AndroidX source out of the
  Gradle cache (`~/.gradle/caches/modules-2/files-2.1/androidx.core/core/<v>/*-sources.jar`) - local
  library sources are a cheap, authoritative substitute when browsing is unavailable.

Related: [[feedback_verify_subagent_build_failures]], [[feedback_documented_invariant_is_a_claim]].
