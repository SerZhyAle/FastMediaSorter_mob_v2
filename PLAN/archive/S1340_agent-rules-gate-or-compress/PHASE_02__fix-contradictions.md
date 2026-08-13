# Phase 02 - Fix contradictions

**Strategic spec:** [`../S1340_agent-rules-gate-or-compress.md`](../S1340_agent-rules-gate-or-compress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03 (sync-parallel-rule-files reads the final CLAUDE.md text)
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Fix the two genuine defects strategic §3.3 identified as still open (confirmed 2026-08-01; the other four §3.3 bullets are already correct and are left untouched, per the strategic spec's delivery-status note): the CLAUDE.md self-contradiction around direct `gradlew.bat` invocation bypassing Rule 23's `BUILD.LOCK`, and the document-registry mandate being fully restated in five-plus always-on homes instead of pointing at the one document that owns it.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - this is a foundation phase, runs independently of Phase 01)
- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic spec's §3.3 delivery-status note (added 2026-08-01) has been read - it confirms sections 7, 12, Rule 24, and the house-style worked example need no action; only the two steps below are in scope.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | no ceiling; must not grow |
| `.claude/agents/android-rd-specialist.md` | Modified | no ceiling |
| `.claude/agents/android-kotlin-developer.md` | Modified | no ceiling |
| `.claude/agents/friendly-android-doc-writer.md` | Modified | no ceiling |
| `.claude/agents/android-solution-researcher.md` | Modified | no ceiling |

---

## Steps

### Step 02.1 - Remove the gradlew.bat / BUILD.LOCK self-contradiction

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Section "## 9. Common Commands (a.ps1 launcher)", the "Tests" bullet, currently reads: "Tests: `.\a.ps1 fu` (full unit suite) or `.\gradlew.bat testStandardDebugUnitTest`." The second alternative invokes `gradlew.bat` directly, which per Rule 23 must acquire `temp/BUILD.LOCK` via `scripts/utils/agent-lock.ps1` before the call - but nothing routes a bare `.\gradlew.bat ...` invocation through that wrapper, so this line authorizes exactly the unlocked concurrent-gradle scenario Rule 23 exists to prevent. Remove the `or \`.\gradlew.bat testStandardDebugUnitTest\`` alternative; keep only `.\a.ps1 fu` (which already acquires the lock) plus the kapt-recovery pointer.

**Verification:**

- `Grep "gradlew.bat testStandardDebugUnitTest" CLAUDE.md` returns zero matches.
- `Grep "a.ps1 fu" CLAUDE.md` still matches (sanctioned wrapper retained).
- `Grep "recover-kapt-stall.ps1" CLAUDE.md` still matches (kapt-recovery pointer retained).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `Grep "gradlew.bat testStandardDebugUnitTest"` -> 0. `Grep "a.ps1 fu"` -> retained, now with an explicit "acquires BUILD.LOCK per Rule 23; never call gradlew.bat directly" clause. `Grep "recover-kapt-stall.ps1"` -> retained. Files: CLAUDE.md (line 97). Dev log recorded via post-change.ps1.

---

### Step 02.2 - Scope the document-registry mandate down to one home plus pointers

**Files:** `CLAUDE.md`, `.claude/agents/android-rd-specialist.md`, `.claude/agents/android-kotlin-developer.md`, `.claude/agents/friendly-android-doc-writer.md`, `.claude/agents/android-solution-researcher.md`
**Depends on:** - start of phase

**Prompt for developer:**

> `.claude/skills/document-registry/SKILL.md` already carries the complete loop (query by product area/trigger, read matches, state affected-vs-unchanged, close with `validate.ps1`/`generate.ps1 -Check`) and `scripts/post-change.ps1` already triggers it mechanically (confirmed landed via S1338 phase 05, `post-change.ps1:617-690`). CLAUDE.md section 5 item 5 currently restates the full loop in prose (3 sentences, script paths and all) instead of pointing at the skill; each of the four listed agent-persona files restates a shorter but still-duplicate version under their own "Core" section. Compress all five to one line each: "mandatory at task start / material scope change / phase boundary / before final response - see `.claude/skills/document-registry/SKILL.md`" (or equivalent one-clause phrasing per file's existing style), dropping the re-explained trigger mechanics and script call syntax from every restatement except the skill file itself.

**Verification:**

- `Grep "query.ps1 by product area and change trigger" CLAUDE.md` returns zero matches (detailed restatement removed from CLAUDE.md section 5).
- `Grep "document-registry/SKILL.md" CLAUDE.md` matches (pointer present).
- For each of the four agent files: `Grep "SKILL.md" <file>` matches (pointer present) AND the file's document-registry bullet is a single line (no multi-sentence restatement remains - spot-check by line count of that bullet).
- `.claude/skills/document-registry/SKILL.md` itself is unchanged (not edited by this step - it remains the one document that owns the full procedure).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS. `Grep "query.ps1 by product area and change trigger" CLAUDE.md` -> 0. `Grep "document-registry/SKILL.md" CLAUDE.md` -> line 57 pointer present. All four agent files spot-checked: `android-rd-specialist.md`/`android-kotlin-developer.md` compressed from 3-sentence restatements to one line each; `android-solution-researcher.md` compressed similarly, keeping its "read-only mode" nuance; `friendly-android-doc-writer.md` read and found already a single-line pointer - left untouched (no busywork edit). `SKILL.md` confirmed unedited. Files: CLAUDE.md, `.claude/agents/android-rd-specialist.md`, `.claude/agents/android-kotlin-developer.md`, `.claude/agents/android-solution-researcher.md` (4 of the 5 listed - `friendly-android-doc-writer.md` needed no change). Dev log recorded via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (2 batched entries, one per step).
- [x] If public API changed: skip - no app source touched.
- [x] Phase-boundary audit run - not applicable in the Kotlin-audit sense; confirmed the document-registry TRIGGER logic in `scripts/post-change.ps1:617-690` (when the mandate fires, what it requires) was not touched. One line inside that range (`$ackSet` construction, ~line 664) WAS edited mid-step 01.4 as an out-of-Files-Touched tooling fix (Rule 13) - it only fixed CSV-argument parsing for `-RegistryAck`, not the trigger condition itself; logged separately, not part of this phase's own scope.

---

## Handoff Notes to Next Phase

Phase 03 needs the final text of CLAUDE.md section 1 (Timestamps line, untouched by this phase) and the fully compressed section 5/9 from this phase, to check AGENTS.md / `.github/copilot-instructions.md` for consistency against it.

---

## Rollback Plan

Revert phase commit(s) - text-only change across CLAUDE.md and four agent-persona files, no data migration or user-facing surface changed. Reverting mid-way is safe: each file's document-registry pointer still resolves even if only some of the five are compressed.
