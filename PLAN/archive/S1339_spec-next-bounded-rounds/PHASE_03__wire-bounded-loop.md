# Phase 03 - Wire the bounded loop into `/spec-next`

**Strategic spec:** [`../S1339_spec-next-bounded-rounds.md`](../S1339_spec-next-bounded-rounds.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Rewrite `.claude/commands/spec-next.md` so Stage 0 persists device facts and inits/resumes session state, and Stage 5 records every outcome then checks the threshold - stopping with a printed handoff on crossing it instead of running unbounded. Sync `.claude/reference/spec-next.md`'s "Context management" section and round-outcome table to match.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done - all seven verbs of `spec-next-session.ps1` exist and pass their verification.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-next.md` | Modified | ≤ 230 (from 188) |
| `.claude/reference/spec-next.md` | Modified | ≤ 220 (from 201) |

---

## Steps

### Step 03.1 - Mandate line, Usage block, Stage 0

**Files:** `.claude/commands/spec-next.md`
**Depends on:** - start of phase

**Prompt for developer:**

> **Line 9 (Mandate).** Current text ends: ".. The loop stops only when nothing remains that the machine can advance alone. Never ask the operator a question mid-loop; .." Rewrite to add the threshold as a second, distinct stop condition and reframe the goal per strategic §4.2 ("from 'keep the machine busy' to 'keep the backlog moving'"):
>
> > **Mandate (loop mode).** **Drive every ticket - `Draft` included - as far as it can go: to readiness (`Implemented`/`Verified`) or to a *real* blocker.** A ticket that ends genuinely blocked on a human (a question, an external resource, a real-device-only check) is *reported*, not waited on. The goal is to keep the backlog moving, not to keep one session busy forever - N bounded rounds satisfy it as well as one unbounded one. The loop stops when nothing remains that the machine can advance alone (Stage 6), or when carried context crosses the round-boundary threshold (Stage 5b) - the threshold stop prints a resume handoff and is a report, not a question. Never ask the operator a question mid-loop; defer every human-gated item to the final report. Tempted to skip a `Draft` as too heavy -> "Mandate rationale" in `.claude/reference/spec-next.md`; the answer is no.
>
> **Usage block.** After the `--reset-skips` line, add:
>
> ```text
> /spec-next --resume         # continue a session stopped at the context threshold (Stage 5b)
> /spec-next --threshold <n>  # override the default 300000-token round-boundary threshold
> ```
>
> **Stage 0 heading and body.** Rename to "Stage 0 - Session init and device probe (once per session)". Before the existing device-probe paragraph, add:
>
> > **Session state.** `--resume` present -> `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Resume`; seed the in-memory `processed` set from its `excludeCsv`. No `--resume` -> `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Init` (add `-Threshold <n>` if `--threshold` was passed) - a fresh round, per Hard rules "round memory is session-scoped".
>
> After the existing two probe-outcome bullets (`DEVICE_ONLINE = true` / `= false`), add a third bullet persisting the result - always re-probe even on `--resume`, a resumed process may be reconnecting after the device was unplugged mid-session:
>
> > - Either way, persist it: `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Device -Online <bool> [-SelectedDevice <id>]`. This is what lets a future threshold-triggered reset restore device state without re-deriving it from a `Resume` payload that could be stale.

**Verification:**

- `Grep -n "The goal is to keep the backlog moving"` in `.claude/commands/spec-next.md` returns one hit.
- `Grep -n "spec-next --resume"` in `.claude/commands/spec-next.md` returns at least one hit in the Usage block.
- `Grep -n "Verb Resume"` and `Grep -n "Verb Init"` in `.claude/commands/spec-next.md` Stage 0 both return at least one hit.
- `Grep -n "Verb Device"` in `.claude/commands/spec-next.md` returns at least one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS. Files: `.claude/commands/spec-next.md` (mandate line rewritten, usage block +2 lines, Stage 0 renamed + 2 new paragraphs). Also corrected `-Online <bool>` to `-Online <true|false>` in the new Stage 0 text to match the actual string-typed param from Phase 01/02 (same class of doc-drift caught there). `post-change.ps1 -ChangeType Doc` needed `-RegistryAck 'repository-rules'` on the second run - `.claude/commands/*.md` is a registered document (`docs/DOCUMENT_REGISTRY.jsonl`, confirmed at task start); first run correctly surfaced the advisory, second run PASS with dev-log deduped as identical.

---

### Step 03.2 - Stage 5 record-then-check, new Stage 5b

**Files:** `.claude/commands/spec-next.md`
**Depends on:** Step 03.1 (shares the file; sequential edits to avoid clobbering)

**Prompt for developer:**

> Current Stage 5 ("### Stage 5 - Inspect outcome and loop") reads the catalog row, says "Record final status. Every outcome continues the loop and adds the just-handled id to `processed`", then carries the "Round memory", "Context management (mid-loop `/compact`)" and "`--once` mode" notes before Stage 5.5. Restructure as follows:
>
> 1. Rename heading to "### Stage 5 - Inspect outcome, record, and loop".
> 2. Immediately after the `select.ps1 -Id <Sxxxx> -Format json` block, insert:
>    - A "**Record before anything else**" paragraph explaining a threshold stop must never lose a just-completed ticket, with the literal call:
>      ```powershell
>      pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Record -Id <Sxxxx> -Outcome <advanced|verified|blocked|skipped> [-Note "<short free-text>"]
>      ```
>    - The `-Outcome` mapping: `Verified` -> `verified`; any `Block*` -> `blocked`; unchanged from start -> `skipped`; anything else (`Implemented`/`Partial`/`Broken`/still `In Progress`) -> `advanced`.
>    - Keep the existing "Verdict wording per status -> Round-outcome table .." sentence.
> 3. Keep the existing "Round memory" paragraph as-is (in-memory set still feeds Stage 1's `-Exclude`; it now mirrors, not replaces, what was just written to disk).
> 4. Replace the "Context management (mid-loop `/compact`)" paragraph with a pointer to Stage 5b (written next) and to the reference doc's renamed section:
>    > **Context reset happens only at Stage 5b, on the mechanical threshold - never earlier, never mid-`/spec-all`.** The loop does not self-judge context by feel; `-Verb CheckContext` decides. Read "Context management (mid-loop reset)" in `.claude/reference/spec-next.md` for what the handoff must preserve.
> 5. Keep the "`--once` mode" line but extend its skip list: "Skip loop, Stage 5b and Stage 5.5. After Stage 5 print final report and exit."
> 6. Insert a new section directly after Stage 5, before Stage 5.5:
>    ```markdown
>    ### Stage 5b - Context-threshold stop
>
>    Immediately after `-Verb Record`, check carried context:
>
>    ```powershell
>    pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb CheckContext
>    ```
>
>    - Exit 0 (under threshold) -> continue to the next Stage 1 call, as before.
>    - Exit 3 (threshold crossed) -> print the handoff and **stop the loop**:
>      ```powershell
>      pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Handoff
>      ```
>      Present its output verbatim - it is the deterministic artifact (do not compose a replacement in prose, per strategic §4.4). This is a **report, not a question** - it does not violate "never ask the operator". Do not proceed to Stage 1 after printing it; do not proceed to Stage 5.5 either - the handoff already told the operator what is next.
>    - Exit 2 (cannot verify) -> log the reason in the round verdict and continue to the next Stage 1 call as if exit 0. A sensor outage must not silently turn into an unbounded session, but a single unreadable check must not stop the loop either - the very next round re-checks.
>
>    **Never stop for any other reason.** The loop's only stop conditions are: nothing left in the eligible set (Stage 6), the context threshold (Stage 5b), or a genuine human-gated blocker recorded and carried to the final report. `/spec-do` is the sanctioned way to decline the threshold trade entirely.
>    ```
> 7. In Stage 6 ("Final report"), before the existing dev-log block, add a line sourcing the cross-reset history from disk instead of only this segment's in-memory list (this is what makes the tally intact across a `--resume`):
>    ```powershell
>    pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Report
>    ```
>    > Use this as the source for "Processed this run" - it spans every round since the last `-Verb Init`, including rounds from before a threshold-triggered `/clear` + `--resume`, which the in-memory `processed` set alone cannot.

**Verification:**

- `Grep -n "Stage 5b"` in `.claude/commands/spec-next.md` returns at least 2 hits (heading + Stage 5's forward reference).
- `Grep -n "Verb Record"` in `.claude/commands/spec-next.md` returns at least one hit inside Stage 5.
- `Grep -n "Verb CheckContext"` and `Grep -n "Verb Handoff"` in `.claude/commands/spec-next.md` each return at least one hit inside Stage 5b.
- `Grep -n "Verb Report"` in `.claude/commands/spec-next.md` returns at least one hit inside Stage 6.
- `Grep -n "Never stop for any other reason"` returns one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification PASS (combined Grep count across all six patterns = 12, each individually >=1). Files: `.claude/commands/spec-next.md` (Stage 5 renamed + Record call inserted, Context-management paragraph replaced, `--once` scope extended; new Stage 5b section; Stage 6 gained the `-Verb Report` sourcing line). `post-change.ps1 -ChangeType Doc -RegistryAck 'repository-rules'` PASS.

---

### Step 03.3 - Sync `.claude/reference/spec-next.md`

**Files:** `.claude/reference/spec-next.md`
**Depends on:** Step 03.2 (must match the driver's new stage names/verbs)

**Prompt for developer:**

> 1. Rename "## Context management (mid-loop `/compact`)" to "## Context management (mid-loop reset)". Replace its body (which currently says "do **not** stop or cut the session short.. run `/compact` and continue the loop") with the mechanical version:
>    > The loop is designed to run for many rounds and will accumulate context - that is expected. It no longer self-judges when to reset: `-Verb CheckContext` (Stage 5b) is the sole trigger, on the fixed threshold (default 300000, `-Threshold`/`--threshold` overridable). On a threshold stop, `-Verb Handoff` recommends `/clear` rather than `/compact` - round state (`processed`, tally, `DEVICE_ONLINE`, `selectedDevice`) already lives on disk in `temp/spec-next-session.json`, so a `/compact` summary would only re-carry what the state file already holds at zero cost. After `/clear`, `/spec-next --resume` reads that file back (`-Verb Resume`) and continues at Stage 1 with the restored `-Exclude` set - nothing is re-derived, nothing is reprocessed.
> 2. In "## Stage 5 - round-outcome table", add a row above the closing table delimiter:
>    ```markdown
>    | (threshold crossed) | Stage 5b handoff printed | Loop stops - operator runs one of the three recommended commands |
>    ```
> 3. In "## Stage 0, 3, 4 and 5.5 notes", the "**Stage 0 - device probe.**" bullet currently says "Re-probe is unnecessary - a device attached at session start is assumed available for Stage 5.5." This is now wrong across a `--resume` boundary (a fresh process could reconnect to a different device or none). Replace with:
>    > **Stage 0 - device probe.** Runs every invocation, including `--resume` - a resumed process is a fresh probe, not a continuation, since the device may have changed while the session was stopped. The result is persisted via `-Verb Device` so Stage 5.5's `DEVICE_ONLINE` check never depends on in-memory state surviving a reset.
> 4. Add one new numbered section between "Stage 5 - round-outcome table" and "Stage 6 - final report format" titled "## Stage 5b - threshold stop detail", covering: why `-Verb Record` must run before `-Verb CheckContext` (strategic §4.2 - "so a reset can never lose a completed ticket"), the exit-2 fallback behaviour (continue, log, re-check next round), and a pointer back to strategic §4.4 for the handoff's fixed section order. Update the file's own numbered section index at the top to include this new section and renumber the ones after it.

**Verification:**

- `Grep -n "Context management (mid-loop reset)"` in `.claude/reference/spec-next.md` returns one hit; `Grep -n "Context management (mid-loop .compact.)"` returns zero hits (old heading fully replaced).
- `Grep -n "threshold crossed"` in `.claude/reference/spec-next.md` returns at least one hit inside the round-outcome table.
- `Grep -n "Runs every invocation, including .--resume."` returns one hit.
- `Grep -n "Stage 5b - threshold stop detail"` returns one hit, and it appears before "## Stage 6 - final report format" in file order.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS (old heading 0 hits, new heading present; round-outcome row present; Stage 0 note replaced; new Stage 5b section at line 107, before Stage 6 at line 117). Files: `.claude/reference/spec-next.md` (section index renumbered 1-12, Context-management section rewritten, round-outcome table +1 row, Stage 0 note replaced, new "Stage 5b - threshold stop detail" section). `post-change.ps1 -ChangeType Doc -RegistryAck 'repository-rules'` PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Both files still parse as valid Markdown (no unclosed code fences) - visually confirmed via Read/Grep during editing.
- [x] **Functional acceptance (strategic §7).** Simulated: `-Verb Init` -> `-Verb Record S0001 verified` -> `-Verb Record S0002 blocked` -> `-Verb Resume` returned `excludeCsv=S0001,S0002 round=2`; `-Verb Report` listed both ids with original outcomes and `tally.processed: 2` - the tally survives the simulated reset intact.
- [x] `Grep` for `TODO(phase-03)` returns zero hits (checked both files).
- [x] Dev log entries added for `.claude/commands/spec-next.md` and `.claude/reference/spec-next.md` - `post-change.ps1`'s `[dev-log]` gate logged one line per step.
- [x] Phase-boundary audit run - confirmed the rewritten Stage 5/5b sequencing has no forward reference (Record always precedes CheckContext in the new text, matching Phase 01/02's verb contracts). No broad/empty catch, no silent failure paths introduced (this phase is documentation-only, no new code). No unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`/spec-next` now bounds itself at the context threshold, resumable via `--resume`. Phase 04 builds `/spec-do` as a sibling command reusing the identical `spec-next-session.ps1` verb set, differing only in how it reacts to `CheckContext` exit 3 (never stop).

---

## Rollback Plan

Revert phase commit(s). This changes the live control flow of the loop this pipeline itself is running under - if a self-check step in this same run behaves unexpectedly after the edit, stop and re-read the diff before continuing to Phase 04 rather than pushing forward on a guess.
