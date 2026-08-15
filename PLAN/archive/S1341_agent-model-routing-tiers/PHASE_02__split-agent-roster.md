# Phase 02 - Split agent roster

**Strategic spec:** [`../S1341_agent-model-routing-tiers.md`](../S1341_agent-model-routing-tiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03 (needs the two new agent files to exist before hardening them)
**Steps done:** 4 / 4
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Pin `android-kotlin-developer` and `android-rd-specialist` to `model: opus` explicitly (never `inherit`), narrow `android-rd-specialist`'s description so it stops attracting pure research/device-driving work, and add two new Sonnet-tier agents (`android-device-operator`, `repo-mechanic`) that absorb that misrouted work.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - foundation phase)
- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic spec's §4.2 delivery-status note (added 2026-08-01) has been read - `enable_mcp_tools` is not applied (not a real key, parked as S1348); tool-surface restriction uses the `tools:` frontmatter key instead.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/agents/android-kotlin-developer.md` | Modified | 1 line changed |
| `.claude/agents/android-rd-specialist.md` | Modified | description rewritten, 1 frontmatter line changed |
| `.claude/agents/android-device-operator.md` | New | ≤ 60 |
| `.claude/agents/repo-mechanic.md` | New | ≤ 60 |

---

## Steps

### Step 02.1 - Pin `android-kotlin-developer` to explicit Opus

**Files:** `.claude/agents/android-kotlin-developer.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Change frontmatter `model: inherit` to `model: opus`. Implementation is Opus-column work per strategic §3 ("a demoted model ships a P0/P1 that only surfaces at the next phase-boundary audit"); `inherit` risks silent demotion if the session model ever changes. No other frontmatter or body change.

**Verification:**

- `Grep "^model: opus" .claude/agents/android-kotlin-developer.md` matches.
- `Grep "^model: inherit" .claude/agents/android-kotlin-developer.md` returns zero matches.
- `Grep "^name: android-kotlin-developer" .claude/agents/android-kotlin-developer.md` still matches (identity untouched).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. Files: `.claude/agents/android-kotlin-developer.md` (1 line). Dev log recorded via post-change.ps1.

---

### Step 02.2 - Pin `android-rd-specialist` to explicit Opus and narrow its description

**Files:** `.claude/agents/android-rd-specialist.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Change frontmatter `model: inherit` to `model: opus` (same rationale as 02.1). Rewrite the `description` field to remove the phrase "class-catalog navigation" and the trigger "locate where a class or feature lives" - these are pure read-only research, now routed to `android-solution-researcher` instead. Add a routing hint pointing pure research to `android-solution-researcher` and device-driving/log-harvesting work to `android-device-operator` (both created later in this phase - reference them by name, the routing hint is valid once they exist). Keep every other trigger (spec drafting, architecture review, flavor/BuildConfig diagnosis, end-to-end feature planning) - those stay Opus-column work per strategic §3.

**Verification:**

- `Grep "^model: opus" .claude/agents/android-rd-specialist.md` matches.
- `Grep "^model: inherit" .claude/agents/android-rd-specialist.md` returns zero matches.
- `Grep "class-catalog navigation" .claude/agents/android-rd-specialist.md` returns zero matches (removed from description).
- `Grep "android-solution-researcher" .claude/agents/android-rd-specialist.md` matches (routing hint added).
- `Grep "android-device-operator" .claude/agents/android-rd-specialist.md` matches (routing hint added).
- `Grep "^name: android-rd-specialist" .claude/agents/android-rd-specialist.md` still matches (identity untouched); body content below frontmatter untouched (spot-check `Grep "Senior Android architect" .claude/agents/android-rd-specialist.md` still matches).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 6/6 PASS. Files: `.claude/agents/android-rd-specialist.md` (model pinned, description narrowed with two routing hints). Dev log recorded via post-change.ps1.

---

### Step 02.3 - Create `android-device-operator` (Sonnet, device-driving only)

**Files:** `.claude/agents/android-device-operator.md`
**Depends on:** - start of phase

**Prompt for developer:**

> New agent file, frontmatter: `name: android-device-operator`, `description` triggering on driving the emulator/device - taps, swipes, screenshots, launching/clearing the app, harvesting logcat - explicitly stating it never edits product code and never renders a PASS/FAIL verdict (per strategic §6, "it reports observations and never verdicts; the verdict stays with the caller"), and explicitly preferring `android-rd-specialist`/`android-kotlin-developer` when the task needs judgement about *why* something happened, not just what was observed. `model: sonnet`. `tools:` restricted to `Bash, Read, Grep, Glob` (Bash carries `scripts/devtest/adb.ps1` and MCP device tools per this project's actual tool-restriction convention - see strategic §4.2 delivery-status note on why `enable_mcp_tools` is not used). Body: short persona paragraph (device operator, FastMediaSorter, reports what it saw not what it means), a note to prefer `scripts/devtest/adb.ps1 <verb>` over raw `adb`, and the document-registry loop pointer bullet matching the other agents' one-line form (`Mandatory document-registry loop: ... - see .claude/skills/document-registry/SKILL.md`).

**Verification:**

- `Glob .claude/agents/android-device-operator.md` matches (file exists).
- `Grep "^model: sonnet" .claude/agents/android-device-operator.md` matches.
- `Grep "^name: android-device-operator" .claude/agents/android-device-operator.md` matches.
- `Grep "never edits\|never edit" .claude/agents/android-device-operator.md -i` matches (scope boundary stated).
- `Grep "never.*verdict\|reports observations" .claude/agents/android-device-operator.md -i` matches (verdict-boundary stated, per strategic §6 risk mitigation).
- `Grep "document-registry/SKILL.md" .claude/agents/android-device-operator.md` matches.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 6/6 PASS. Files: `.claude/agents/android-device-operator.md` (new, 33 lines). Dev log recorded via post-change.ps1.

---

### Step 02.4 - Create `repo-mechanic` (Sonnet, closure/gates/catalog only)

**Files:** `.claude/agents/repo-mechanic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> New agent file, frontmatter: `name: repo-mechanic`, `description` triggering on running the closure facade (`post-change.ps1`), the `assert-*` gates, the spec-catalog CLI, `document_registry/query.ps1` - explicitly stating it reports each script's verdict verbatim and never interprets a failure into a fix (per strategic §4.2, "never interprets a failure into a fix; it hands the verdict back"). `model: sonnet`. `tools:` restricted to `Bash, Read, Grep, Glob` - no `Edit`/`Write`, since this agent only runs scripts and reports, never edits source (matches strategic §4.2's mechanic-tier boundary; also the practical substitute for the non-existent `enable_mcp_tools` control - no MCP tools listed here either). Body: short persona paragraph, and the document-registry loop pointer bullet matching the other agents' form.

**Verification:**

- `Glob .claude/agents/repo-mechanic.md` matches (file exists).
- `Grep "^model: sonnet" .claude/agents/repo-mechanic.md` matches.
- `Grep "^name: repo-mechanic" .claude/agents/repo-mechanic.md` matches.
- `Grep "^tools:" .claude/agents/repo-mechanic.md` matches and the line contains no `Edit` and no `Write` token (mechanic never edits source).
- `Grep "never interprets\|hands the verdict back" .claude/agents/repo-mechanic.md -i` matches (scope boundary stated).
- `Grep "document-registry/SKILL.md" .claude/agents/repo-mechanic.md` matches.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 6/6 PASS. Files: `.claude/agents/repo-mechanic.md` (new, 27 lines). Dev log recorded via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (batched, per CLAUDE.md journaling-granularity rule).
- [x] If public API changed: skip - no app source touched.
- [x] Phase-boundary audit run - not applicable in the Kotlin-audit sense; confirmed `Grep "^model: inherit" .claude/agents/*.md` -> 0 matches across all 6 files (4 pre-existing + 2 new).

---

## Handoff Notes to Next Phase

Phase 03 hardens `android-device-operator.md` and `repo-mechanic.md` (turn budget, structured report contract, no-gradle rule) - both files must exist first, hence the dependency.

---

## Rollback Plan

Revert phase commit(s) - two file edits plus two new files, no data migration or user-facing surface changed. Deleting the two new agent files is safe in isolation (nothing else references them yet before Phase 03).
