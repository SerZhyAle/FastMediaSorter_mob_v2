# Specification: S1341 - Two-tier model routing for agents

**Ticket:** S1341
**Status:** Archived
**Priority:** 68
**Date:** 2026-07-31
**Tier:** 2
**Parent:** S1338
**Source:** `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` sections 2 (L4) and 8 (Q5)
**Tactical plan:** `PLAN/S1341_agent-model-routing-tiers/INDEX.md`

---

## 1. Problem

The repo already installed a routing mechanism, and it is provably inert.

- `model: sonnet` appears in the frontmatter of **14 command files**, and across **115 invocations of those commands the session model was kept every single time**. Command frontmatter does not route. Agent frontmatter and the Workflow `opts.model` field do.
- `.claude/agents/android-rd-specialist.md:4` and `.claude/agents/android-kotlin-developer.md:4` are both `model: inherit`, which in this workspace means Opus. Only `android-solution-researcher` and `friendly-android-doc-writer` carry `model: sonnet`.
- `android-rd-specialist` took **138 spawns, ~89% on Opus**, and roughly **130 of them were reading, research, and driving the emulator** - work with no architectural judgement in it.
- Roughly **30% of main-session requests are mechanical**: running `post-change`, the catalog CLI, the assert gates, the document-registry query, replaying device taps.
- Output tokens by model across the window: opus-4-8 134.7 M (71%), opus-5 25.1 M, fable-5 16.6 M, sonnet-5 12.1 M.

This axis was never properly measured by the audit, which is itself the finding: the workspace has been paying the top tier for mechanical work for a month with no instrumentation on it, and a dead mechanism sitting in 14 files creating the false impression that routing exists.

---

## 2. Owner decision

Asked whether to split by tier including a Haiku mechanic, to split only device work, to measure first, or to leave it:

> только OPUS = интеллект + Sonnet = механика. Fable и Haiku только руками

**Decision: exactly two automated tiers. Opus for intelligence, Sonnet for mechanics. Fable and Haiku are invoked by hand only and must never appear in any agent frontmatter, command frontmatter, or workflow default.**

This narrows the audit's proposal, which had suggested a Haiku mechanic tier. Haiku is out.

---

## 3. Routing table

The classification rule: **does the task require judgement that would be wrong if it were merely plausible?** If yes, Opus. If the task is "execute a known procedure and report what happened", Sonnet.

### Opus - intelligence

- Implementation of product code in `app_v2/` and `wear/`.
- Architecture decisions, layering judgements, Clean/MVVM boundary calls.
- Writing and refining strategic specifications and tactical plans.
- Code review, the phase-boundary audit, and anything under `docs/CODE_AUDIT_PROTOCOL.md` at P0/P1 severity.
- Diagnosing a crash, ANR, race or leak from evidence.
- Any decision that will be written into a spec as a fact.

### Sonnet - mechanics

- Running scripts whose output is a verdict: `post-change.ps1`, the `assert-*` gates, `catalog_sync.ps1`, the spec-catalog CLI, `document_registry/query.ps1`.
- Read-only research: locating a class, mapping a call chain, summarising existing behaviour, answering "where does X live".
- Driving the emulator: taps, swipes, screenshots, launching and clearing the app, harvesting logcat.
- Log reading and triage against a known pattern.
- Dev-log entries, changelog rows, catalog record maintenance, `ALL_FEATURES` records.
- Mechanical spec fixes after `/spec-check` where the finding already names the edit.
- Batch device-test sweeps of already-specified verification steps.

### Never automated

Fable and Haiku. No frontmatter, no workflow default, no fallback. Owner invokes them directly or not at all.

---

## 4. Work

### 4.1 Fix the mechanism before using it

- Remove `model: sonnet` from all 14 command files. It has never had an effect and its presence is actively misleading - it reads as routing that exists. Removing it is part of the S1340 compression pass; the decision belongs here.
- Record in `docs/AGENT_COST_PLAYBOOK.md` that command frontmatter does not route, so the mistake is not repeated. Evidence: 115 of 115 invocations kept the session model.

### 4.2 Split the agent roster

- Keep `android-kotlin-developer` on Opus, and pin it explicitly with `model: opus` rather than `inherit`. Implementation is the class of work where a demoted model ships a P0/P1 that only surfaces at the next phase-boundary audit, costing a full phase of rework. Explicit beats inherited so a session-model change cannot silently demote it.
- Keep `android-rd-specialist` on Opus, but narrow its charter to the Opus column above. Its description must stop attracting research and device work.
- Add `android-device-operator` on `model: sonnet`: drives the emulator, harvests logs, reports what it saw, never edits product code. This absorbs the largest single block of misrouted work - ~95 device-flavoured spawns that were 27% of spawns and 40% of subagent cache_read while returning a median of 1,453 characters.
- Add `repo-mechanic` on `model: sonnet`: runs the closure facade, the gates, the catalog and registry CLIs, and reports verdicts verbatim. Never interprets a failure into a fix; it hands the verdict back.
- `android-solution-researcher` already carries `model: sonnet` and already matches the Sonnet column. Leave it, and route research to it rather than to `rd-specialist`.
- Set `enable_mcp_tools: false` on every new agent except `android-device-operator`, per CLAUDE.md section 6.

**Delivery status (tactical execution, 2026-08-01):** `enable_mcp_tools` is not a real key - grepped all 4 existing `.claude/agents/*.md` files, none set it; the Agent tool's own parameter schema has no such field. Parked as **S1348** rather than silently implementing a no-op. Substituted with the mechanism this project actually demonstrates working: the `tools:` frontmatter key restricting the agent's tool surface (as `android-solution-researcher.md` already does: `tools: Read, Grep, Glob, Bash`, no MCP tools listed). `repo-mechanic` gets the same restricted `tools:` list; `android-device-operator` gets a wider one that includes device-driving tools.

### 4.3 Bound the spawns

- Give the device and mechanic agents an explicit turn budget in their prompts. **36% of spawns burn 72% of all subagent tokens**, and the device-flavoured whales return a median of 1,453 characters after a mean of ~20 M cached tokens - a burn-to-return ratio near 13,700:1.
- Give both a capped, structured report contract rather than free prose. Structure tracks the agent definition, so the cap belongs in the agent file.
- Forbid gradle inside subagents. Spawns that stalled waiting on background builds are pure loss, and the polling they generate is two of the top five redundant-read cases.

### 4.4 Make device work safe for the cheaper tier

Hard-coded adb paths are the main way a device task fails in a way that needs judgement to recover from. Route every device call through `scripts/devtest/adb.ps1`, which already auto-discovers adb, and gate the hard-coded paths. This is a prerequisite: it converts device work from "sometimes needs a smart recovery" into "runs a known procedure", which is what makes the Sonnet tier safe rather than merely cheaper.

---

## 5. Expected effect

- The device tier and the mechanical tier were each estimated at **~$2-2.6k/month** at a 5:1 Opus-to-Sonnet ratio, on a corrected month of ~$30.6k Opus-list equivalent. Treat both as unverified: this axis was never measured properly, which is why S1338 package A must land first and report per-model spend.
- No effect is claimed for 4.1. Removing dead frontmatter saves nothing; it removes a false belief.

---

## 6. Risks and the failure mode to watch

- **The demotion failure mode is specific:** a cheaper model produces a plausible answer to a question that needed judgement, and because it is plausible it is not caught until a later phase audit. That is why implementation and spec authorship stay on Opus regardless of how mechanical a particular instance looks.
- **Research is the boundary case.** "Locate the class" is mechanical; "decide which of three call sites is the real owner" is not. When a research task starts producing a recommendation rather than a map, it has crossed into Opus work. Encode this in the agent descriptions, because the routing decision is made from the description.
- **Device work degrades quietly.** A device agent that misreads a screen reports a false PASS. Mitigation: it reports observations and never verdicts; the verdict stays with the caller.
- **A session-model change can silently re-tier everything on `inherit`.** That is why 4.2 pins explicitly.

---

## 7. Acceptance

- No `model:` key remains in any `.claude/commands/*.md`.
- Every agent in `.claude/agents/` carries an explicit `model:` - never `inherit`.
- Re-measure with S1338 package A after two weeks and report spend per model and per agent, which no measurement in this workspace has ever done.
- Counter-metric: the rework rate. If tickets start needing a second round after a tier change, the boundary in section 3 is drawn in the wrong place. The subagent retry rate is **1%** today and is the pipeline's strongest measured property - it must not move.

---

## 8. Out of scope

- Changing the session model.
- Any use of Fable or Haiku in automation, now or later, without a new owner decision.
- Workflow-level `opts.model` defaults, which follow the same table but are set per invocation rather than in a file.

---

## Last Audit

**Date:** 2026-08-01
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 17 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Manual / on-device

- [ ] §7 bullet 3: re-measure with S1338 package A after two weeks, report spend per model and per agent.
- [ ] §7 bullet 4: watch the subagent retry rate (baseline 1%) as the counter-metric - a rise means the tier boundary is drawn wrong.

### Notes

- `enable_mcp_tools: false` (§4.2) was not applied - confirmed not a real key in this harness (grepped all agent files, checked the Agent tool's own schema). Substituted with the `tools:` frontmatter restriction, the mechanism this project actually demonstrates working. Parked as **S1348** for its own investigation/fix, not silently implemented as a no-op.
- Byte/count facts re-verified live at audit time, not trusted from Step Log: `Grep "^model:" .claude/commands/*.md` -> 0; `Grep "^model: inherit" .claude/agents/*.md` -> 0; agent file count -> 6; `Timber.d("S1341:` in `app_v2/src` -> 0 (correct, no on-device gate); `docs/SCRIPT_CHEATSHEET.md` in sync after the new `find-adb.ps1` script; `docs/AGENT_COST_PLAYBOOK.md` carries the S1341 note.
- `scripts/devtest/adb.ps1 -Verb devices` smoke-tested live after the `Find-Adb` extraction - exit 0, device listed - confirming the refactor is behavior-preserving, not just text-pattern-clean.
