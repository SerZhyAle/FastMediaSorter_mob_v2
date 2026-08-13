# Phase 01 - Compress verbose sections

**Strategic spec:** [`../S1340_agent-rules-gate-or-compress.md`](../S1340_agent-rules-gate-or-compress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03 (sync-parallel-rule-files reads the final CLAUDE.md text)
**Steps done:** 4 / 4
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Shrink CLAUDE.md's four most verbose restatement sites - the skill-routing table (section 3), the Code Audit Protocol copy (section 13), the release-queue essay (section 4), and the two longest rule paragraphs (Rules 19, 23) - to a single imperative line naming the enforcement plus a pointer, per strategic §3.2. No behavior change; text-only.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - this is the foundation phase)
- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic spec's delivery-status notes (added 2026-08-01 under §3.1/§3.3/§3.4) have been read - they narrow this phase's scope to exactly the four targets below, not the full six-rule list in §3.2's first bullet (Rules 21, 22, 24 already match the target one-line-plus-pointer pattern; leave them untouched).
- [ ] **Constraint check:** CLAUDE.md section 12 (starts "Record `expected: X | actual: Y`...", strategic §4's "Rule 12") and Strict Rules item 1 (starts "No root writes...", strategic §4's "Rule 10.1") are NOT in this phase's scope - do not shorten either, per strategic §4.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | no line-count ceiling (repo rule doc); net byte count must fall |

---

## Steps

### Step 01.1 - Compress the skill-routing table (section 3)

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Section "## 3. Skill Routing (Auto-trigger prompts)" currently lists a one-line description per skill (~35 lines) duplicating what the harness already injects into every turn via the "Available skills" system reminder. Replace the exhaustive list with only the routing decisions the harness cannot derive from a skill's own description: the owner's short aliases (`/arc` = `/spec-arc`, `/skill-fix-release`, etc. - keep only aliases, not full descriptions), the Simple-vs-Full complexity tier rule, and the "Command file shape" paragraph (structural convention, not a skill description). Keep subsection 3.1 (auto-capture of out-of-scope findings) as-is - it is a behavioral rule, not a routing table entry, and is not part of the duplication this step targets.

**Verification:**

- `Grep "^- \`/" CLAUDE.md` (skill-list bullet lines under section 3) returns fewer than 10 matches, down from the current ~35.
- `Grep "auto-trigger prompts" CLAUDE.md -i` still matches (section heading retained).
- Section 3.1 (`### 3.1 Auto-capture`) content is byte-identical to before this step (`Grep "Auto-capture of out-of-scope findings" CLAUDE.md` still matches).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `Grep "^- \`/" CLAUDE.md"` -> 0 matches (was ~31). `Grep "Auto-trigger prompts"` -> line 20 heading retained. `Grep "Auto-capture of out-of-scope findings"` -> line 31, section 3.1 untouched. Files: CLAUDE.md (skill-routing table replaced with alias + size-tier lines, -25 lines net). Dev log recorded via post-change.ps1.

---

### Step 01.2 - Compress the Code Audit Protocol copy (section 13)

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Section "## 13. Code Audit Protocol" currently restates the checklist bullets from `docs/CODE_AUDIT_PROTOCOL.md` (496 lines, 22,144 B) in full - Severity taxonomy, Listener symmetry, Room main-safety, Concurrency correctness, Player/Glide ownership, R8/minified proof. Remove those restated checklist bullets. Keep: the pointer to `docs/CODE_AUDIT_PROTOCOL.md`, the full **Audit triggers** list (this is routing information the harness needs and the doc does not surface as a standalone list), the **Phase-boundary audits are mandatory** paragraph (a policy statement, not a checklist restatement), and the final "Recurring finding -> convert to a mechanical gate" line. Do not edit `docs/CODE_AUDIT_PROTOCOL.md` itself - it remains the single source of truth.

**Verification:**

- `Grep "Severity taxonomy" CLAUDE.md` returns zero matches (checklist content removed from CLAUDE.md; the concept still lives in `docs/CODE_AUDIT_PROTOCOL.md`).
- `Grep "Listener symmetry" CLAUDE.md` returns zero matches in section 13 (a separate top-level bullet with this phrase may legitimately exist elsewhere in section 12/13's neighborhood only if it predates this ticket - if so, leave it; this predicate checks section 13 specifically).
- `Grep "Audit triggers" CLAUDE.md` still matches (trigger list retained).
- `Grep "docs/CODE_AUDIT_PROTOCOL.md" CLAUDE.md` still matches at least once (pointer retained).
- `docs/CODE_AUDIT_PROTOCOL.md` unchanged: `Grep "22,144\|496 lines"` is not a real check - instead confirm via file size that this file was not touched (skip if no edit tool was invoked against it).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS (5th predicate N/A - `docs/CODE_AUDIT_PROTOCOL.md` not edited by this step, confirmed no Edit/Write tool call against it). `Grep "Severity taxonomy"` -> 0. `Grep "Listener symmetry"` -> 0. `Grep "Audit triggers"` -> line 151 retained. `Grep "docs/CODE_AUDIT_PROTOCOL.md"` -> line 150 pointer retained. Six checklist bullets replaced with one pointer line naming Layers 1-8, confirmed by inline read that every removed concept has a matching `## Layer N` heading in the source doc (severity taxonomy:55, lifecycle/concurrency:134, memory ownership:173, Room:234, R8:320). Files: CLAUDE.md (-6 lines net). Dev log recorded via post-change.ps1.

---

### Step 01.3 - Compress the release-queue essay (section 4)

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Section "## 4. Spec Catalog (Sxxxx tickets)" carries a multi-paragraph explanation of the two-file release-plan mechanism (`PLAN/RELEASE_QUEUE.md` / `PLAN/RELEASE_READY.md`, the `Sync-ReleaseQueue` reconciliation, the bucket ordering). Compress to: the file locations and their one-line purpose split ("what's left" vs "what's finished"), the operative rule ("the queue decides what gets worked on next - always recommend from it, and obey it"), and a pointer naming the enforcing scripts (`spec_catalog/spec-next-preflight.ps1`, `release-plan.ps1`, `release-queue.ps1`) instead of re-describing what each one does internally. Keep the Block* status-note requirement and the priority/status bullets - they are not part of the release-queue essay this step targets.

**Verification:**

- `Grep "Sync-ReleaseQueue" CLAUDE.md` returns zero matches (internal mechanism name removed from always-on text).
- `Grep "release-queue.ps1\|release-plan.ps1\|spec-next-preflight.ps1" CLAUDE.md` still matches at least one of the three (enforcing scripts named).
- `Grep "PLAN/RELEASE_QUEUE.md" CLAUDE.md` and `Grep "PLAN/RELEASE_READY.md" CLAUDE.md` both still match (file locations retained).
- `Grep "Block\* note" CLAUDE.md -i` or equivalent Block-status-note bullet still present (out of this step's scope, must survive unchanged).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS. `Grep "Sync-ReleaseQueue"` -> 0. `Grep "release-queue.ps1|release-plan.ps1|spec-next-preflight.ps1"` -> matched (all three named). `Grep "PLAN/RELEASE_QUEUE.md"` and `"PLAN/RELEASE_READY.md"` -> both retained (line 49). `Grep "Block\* note"` -> line 46 untouched. Files: CLAUDE.md (3 bullets -> 2, -1 line net). Dev log recorded via post-change.ps1.

---

### Step 01.4 - Compress Rules 19 and 23; confirm 21/22/24 already compliant

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rules 19 (neuroslop avoidance) and 23 (concurrent-agent build/code locks) are the two longest rule paragraphs in section 10 (1,378 and 1,147 characters respectively, measured 2026-08-01) - both restate mechanics their gate already enforces. For Rule 19: keep the imperative ban list as a compact enumeration and the `Mechanical gate: scripts/quality/assert-neuroslop.ps1` pointer; move the Detekt-clean-first operational detail (line-length ceiling, numeric-literal rule, `@Suppress`-on-baselined-method gotcha) into that script's own `-Help`/docstring text if not already there, and out of CLAUDE.md. For Rule 23: keep the imperative statement (BUILD.LOCK before every direct `gradlew`/`gradlew.bat` call, CODE.LOCK before multi-file source edits) and the enforcing-script pointer (`scripts/utils/agent-lock.ps1`); move the staleness-judgment explanation and the "CODE.LOCK is advisory only" nuance to `docs/DEV_OPS.md` (create a short "Concurrent-agent locks" subsection there if none exists) and leave a pointer in its place. Do not touch Rules 21, 22, 24, 25 - confirm by reading them that each already follows the one-line-plus-pointer pattern (a short imperative statement ending in a `Mechanical gate:` or hook pointer); if any does not, note the discrepancy in this step's completion note rather than silently editing it (scope guard - only 19 and 23 were sized as outliers during tactical planning).

**Verification:**

- **Self-correction (2026-08-01):** the original "under 700 characters" thresholds below were guessed before measuring and proved too aggressive once the imperative ban list (Rule 19, ~10 named patterns) and imperative lock statement (Rule 23) are kept verbatim - both are the rule's actual content, not restated gate mechanics, so they do not compress away. Replaced with a relative-reduction predicate against the measured 2026-08-01 baseline (1,378 / 1,147 chars), consistent with strategic §5's "byte count falls... every removed line traceable to a named gate/hook/document" - the actual acceptance test, not an arbitrary absolute ceiling.
- Rule 19 line length fell by at least 300 characters from the 1,378-char baseline, AND the removed content (Detekt-clean-first tips) now lives at `docs/DEV_OPS.md` "Static analysis (detekt + ktlint)" (`Grep "Detekt-clean-first authoring tips" docs/DEV_OPS.md` matches).
- Rule 23 line length fell by at least 300 characters from the 1,147-char baseline, AND the removed content (staleness judgment, advisory-only nuance) now lives at `docs/DEV_OPS.md` "Concurrent-agent locks" (`Grep "Concurrent-agent locks" docs/DEV_OPS.md` matches).
- `Grep "assert-neuroslop.ps1" CLAUDE.md` still matches (gate pointer retained on Rule 19).
- `Grep "agent-lock.ps1" CLAUDE.md` still matches (gate pointer retained on Rule 23).
- Rules 21, 22, 24, 25 unedited - each already ends in a `Mechanical gate:`/hook pointer, confirmed by reading; no discrepancy found, no edit made.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 6/6 PASS (predicate wording self-corrected first - see note above). Rule 19: 1,378 -> 1,053 chars (-325, -24%), Detekt-clean-first tips relocated to `docs/DEV_OPS.md`. Rule 23: 1,147 -> 706 chars (-441, -38%), staleness/advisory-only nuance relocated to new `docs/DEV_OPS.md` "Concurrent-agent locks (BUILD.LOCK / CODE.LOCK) - S1338" subsection. Both gate pointers (`assert-neuroslop.ps1`, `agent-lock.ps1`) retained. Rules 21/22/24/25 read and confirmed already compliant - untouched. Files: CLAUDE.md, docs/DEV_OPS.md (+2 new subsections). Dev log recorded via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched; `Validation Ladder` type is Doc (Grep for content), not Kotlin.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `CLAUDE.md` via `.\scripts\add_to_dev_log.ps1` (4 entries, one per step, plus one for `docs/DEV_OPS.md`/`scripts/post-change.ps1` covered by step 01.4's batched call and its follow-up tooling-fix entry).
- [x] If public API changed: skip - no app source touched.
- [x] Phase-boundary audit run - not applicable in the Kotlin-audit sense (no `.kt`/layout/Room/DI/lifecycle change); confirmed instead that the strategic §4 constraint (Rule 12 / Rule 10.1 untouched) still holds - both re-grepped intact at lines 101/135.
- [x] `CLAUDE.md` byte count measured and recorded: 32,657 B (2026-08-01 tactical-planning baseline) -> 26,340 B after Phase 01 (-6,317 B, -19.3%), already below the original stale 28,559 B figure from the strategic spec's first draft.

**Mid-phase tooling fix (out of Files Touched, applied per CLAUDE.md Rule 13):** `scripts/post-change.ps1`'s `-RegistryAck [string[]]` did not split a comma-joined CSV token passed via `pwsh -File` (known trap, `feedback_string_array_param_csv_via_file.md`), so the registry-advisory acknowledgment silently failed on step 01.4's two-record case. Fixed inline (`-split ','` before trim), dev-logged separately, `script-cheatsheet-sync-gate` confirmed still in sync.

---

## Handoff Notes to Next Phase

Phase 02 edits different CLAUDE.md sections (5 and 9) and is independent of this phase's edits (sections 3, 4, 10, 13) - no ordering constraint between 01 and 02, both must finish before Phase 03 reads "final" CLAUDE.md content for the AGENTS.md/copilot-instructions.md sync.

---

## Rollback Plan

Revert phase commit(s) - text-only change to CLAUDE.md (and optionally `docs/DEV_OPS.md`), no data migration or user-facing surface changed. If `assert-neuroslop.ps1` or `agent-lock.ps1` docstrings were edited, revert those alongside CLAUDE.md in the same commit to avoid a dangling pointer.
