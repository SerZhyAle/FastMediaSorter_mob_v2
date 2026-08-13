# Specification: S1340 - Gate the expensive rules, compress the rest

**Ticket:** S1340
**Status:** Archived
**Priority:** 70
**Date:** 2026-07-31
**Tier:** 2
**Parent:** S1338
**Source:** `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` sections 4 (C2, C4) and 8 (Q3)
**Tactical plan:** `PLAN/S1340_agent-rules-gate-or-compress/INDEX.md`
**Implemented date:** 2026-08-01

---

## 1. Problem

Compliance in this repo splits cleanly by whether a rule has mechanical enforcement, and the gap is two orders of magnitude.

- Rules backed by a gate or hook hold at **~99%**. The Rule 2 debug-probe invariant: 79 of 80 clean.
- Rules that exist only as prose hold at **1-8%**:
  - `/ui-clarify` was invoked **once** in the whole window, while **33% of all owner corrections were about UI placement**.
  - The document-registry mandate is stated in **five** always-on places and obeyed at **~0.6-3%** of its own stated cadence.
  - Catalog-before-grep: **8.3%**.
  - The `temp/` layout rule is violated **200:1** while being re-read on every turn.
- Rule 17 (system-bar insets) has no gate, and the same defect **reached the owner twice**.

The second-order damage is worse than the first. Every turn the agent reads rules it is actively violating, which calibrates it that mandates are advisory. That calibration then leaks onto rules that do matter. An unenforced rule is not neutral; it is corrosive.

The comparison that settles the design question: the advice "read with offset/limit rather than whole files" already ships in the built-in Read tool schema **on every single turn**, and gets **22% compliance**. Repetition does not produce obedience. Enforcement does.

Cost is the secondary consideration. The always-on preamble is ~64k tokens per request, of which ~7.9k is CLAUDE.md; roughly 57% of CLAUDE.md restates something a gate, hook or harness attachment already enforces.

---

## 2. Owner decision

Asked whether to adopt strict gate-or-delete, to gate only the expensive rules and compress the rest, or to add gates without touching any text:

> Гейты для дорогих, остальное сжать

**Decision: write gates only where a defect actually reached the owner. Everything else gets compressed to one line plus a pointer, not deleted outright.**

This is deliberately narrower than gate-or-delete. Rules keep their documented home; what goes away is the always-on restatement.

---

## 3. Work

### 3.1 New gates - only where a defect reached the owner

Each of these is justified by a specific correction in the transcripts, not by principle.

- **`scripts/quality/assert-window-insets.ps1`** for Rule 17. Justification: the same insets defect reached the owner twice, and the repo has five or more standalone player hosts sharing the pattern. Implementation is a grep over layout and activity sources for the safe-bounds contract; runtime ~1 s. Fold into the S1338 package D single-pass runner rather than adding a fourteenth pwsh start.
- **UI-clarify enforcement.** Justification: 33% of corrections, one invocation. Do not add another prose mandate. Wire it into the pipelines that actually build UI: `/spec-tech` and `/spec-dev` must refuse to leave a phase that touches `res/layout*` or a UI class until either a `/ui-clarify` record exists in the spec or the spec records an explicit owner ruling. Add a screenshot step to the same boundary so a placement decision is visible before it ships.
- **Bugfix repro evidence.** Justification: "no completion claim without proof" has no gate at the point it matters, and 39 of 232 active tickets are bugfixes. Require a before/after repro record on any ticket typed `bugfix` before it may leave `Implemented`.
- **Unverified-backlog ceiling.** Justification: 87 `BlockNeedUserTest` against 39 `Verified`, a 6.9:1 ship-to-verify ratio, 38% of the catalog unproven, 154 live Timber probes in source. Enforced in the loop by S1339; the rule text lives here.

**Delivery status (tactical planning, 2026-08-01):** all four items above landed via S1338 package I before this ticket reached tactical planning - `scripts/quality/assert-window-insets.ps1` (Rule 17, baselined at 28 pre-existing sites, wired into `assert-fast-gates.ps1` via `assert-source-gates.ps1`), the ui-clarify refusal in `.claude/commands/spec-tech.md` step 5.5 and the "UI phase refusal (S1338)" block in `.claude/commands/spec-dev.md`, the "Bugfix repro refusal (S1338)" block also in `spec-dev.md`, and `unverified-backlog.ps1` publishing the backlog count S1339's loop stops on. §3.1 requires **no** tactical work in this ticket - the tactical plan covers only §3.2-§3.4, and the ticket must not add any new `assert-*` script (see §5 acceptance correction below).

### 3.2 Compress - one line plus a pointer

For each rule below, keep a single imperative line in CLAUDE.md naming its enforcement, and move the explanation to the document that owns it.

- The twelve rules that restate an existing gate or hook (Rules 19, 21, 22, 23, 24, 25 and their worked examples). 309 recorded hook blocks prove the hook deters and the prose does not.
- Section 13 (Code Audit Protocol), a lossy copy of a 22 KB document that was opened 8 times in 347 sessions. Keep the trigger list, drop the copy.
- Section 3 (skill routing). The harness already injects every skill description on every turn; the table is a second copy and is stale by six commands. Replace with the routing decisions the harness cannot make - the owner's aliases and the tier rules - and delete the rest.
- Section 4's release-queue essay, which describes four scripts that already enforce the ordering.
- The timestamp rule, currently stated in three prose homes while a UserPromptSubmit hook already supplies the time. Compliance is 47.6% either way, so the prose is not what produces it.

### 3.3 Fix, do not compress

These are wrong rather than verbose, and compressing a wrong rule preserves the error.

- **CLAUDE.md section 12** describes `-ScopeToFile` behaviour the script has not had since S0848/S0850. It is why the facade's verdict is trusted. Correct it as part of S1338 package E and keep the two in sync thereafter.
- **CLAUDE.md line 77** authorises behaviour the owner banned and that Rule 23 blocks mechanically. A self-contradiction in always-on text.
- **CLAUDE.md section 7** teaches PowerShell batching syntax without saying it is PowerShell-tool-only; 137 interop failures trace to it.
- **Rule 24** bans `find` outright while `guard-find-command.ps1` only requires `-maxdepth`. 134 blocks in a month with no decay, because the rule and the hook disagree about what is allowed. Align the text to the hook.
- **The house-style rule's own worked example** was normalised away by the very style it documents, so it now demonstrates the opposite of what it says.
- **The document-registry mandate** - scope it to the pipelines where it is real and trigger it from `post-change.ps1`, rather than stating it in five always-on places at a cadence nothing follows.

**Delivery status (tactical planning, 2026-08-01):** confirmed already correct, no fix needed - CLAUDE.md section 12 (matches `-ScopeToFile` behaviour since S1338 package E), section 7 (already states "PowerShell tool only" explicitly), Rule 24 (already describes the hook's actual two-shape behaviour, not an outright ban), and the house-style worked example (moot - section 1 now points at the canon instead of carrying an inline example, so there is nothing left to contradict). Confirmed still open, tactical work covers these two: the `.\gradlew.bat testStandardDebugUnitTest` alternative in section 9 (now at a shifted line number - bypasses Rule 23's `BUILD.LOCK` wrapper, self-contradiction still present), and the document-registry mandate (`post-change.ps1` already has the real trigger per S1338 phase 05 - the remaining fix is scoping CLAUDE.md's own restatement down to one line plus a pointer, across the five-plus always-on homes found: CLAUDE.md itself plus the four `.claude/agents/*.md` persona files that restate it in full).

### 3.4 Decide the fate of the parallel rule files

`AGENTS.md` (10,571 B) and `.github/copilot-instructions.md` are never loaded by Claude Code, have drifted from CLAUDE.md, and one of them contradicts it by pointing at hand-editing a render target - which the canon's own invariant 16 forbids.

Three options, and the middle one is not acceptable: sync them mechanically from a single source, or delete them. Leaving a drifting unsynced contradiction is worse than either.

**Delivery status (tactical planning, 2026-08-01):** the "delete" branch is foreclosed by CLAUDE.md's own top-of-file note ("Parallel rule set for non-Claude agents... When changing shared rules, sync AGENTS.md too") - the sync option is already the stated policy, just not yet executed or kept current. S1338 package J explicitly delegates the execution here: "sync them mechanically or delete them, but do not leave a contradicting unsynced copy." Confirmed contradiction: `.github/copilot-instructions.md:35` - "Feature docs: update `docs/FEATURES*.md` on new features" - directly contradicts CLAUDE.md §11 ("populated ONLY by `/skill-release` from the `ALL_FEATURES` diff... never edited per-spec"). `AGENTS.md` carries no such line. Given the sync is a low-frequency edit (both files are 71 lines, touched only when shared rules change) rather than an "expensive" defect that reached the owner, §5's constraint "the `assert-*` inventory grows by exactly the gates named in 3.1" rules out adding a new mechanical parity gate for this - the sync stays a manual pass driven by the existing prose reminder, done now and re-checked at the next shared-rule edit.

---

## 4. Constraint on the compression

Do **not** compress a rule that has no mechanical backing and no documented home, on the theory that it is short. Two rules in particular work only because they sit unconditionally in context and fail invisibly when broken:

- Rule 12 - no completion claim without fresh evidence.
- Rule 10.1 - `temp/` hygiene.

Neither has a gate among the ~40 `assert-*` scripts. Rule 12 in particular is the one that catches the failure mode this whole audit started from: circulating numbers that were never verified. It stays in full.

---

## 5. Acceptance

- CLAUDE.md byte count falls from 32,657 B (measured 2026-08-01 at tactical-planning time - the 28,559 B figure from 2026-07-31 is stale, grown by unrelated S1338 phases landed in the interim), with every removed line traceable to either a named gate, a named hook, a harness-injected attachment, or a named document.
- `/ui-clarify` invocation count over the following month rises from 1. If it does not, the wiring failed and prose was added instead of enforcement.
- Rule 17 has a gate that fires at least once on a deliberately broken layout in a test. Already satisfied - `scripts/quality/assert-window-insets.ps1` is baselined at 28 pre-existing sites (S1338 package I); this ticket adds no new test for it.
- No rule listed in section 4 is compressed.
- The `assert-*` inventory grows by exactly the gates named in 3.1 - which is **zero**, since all four already landed via S1338 before this ticket's tactical planning. This ticket must not add any new `assert-*` script.

Counter-metric: if the compressed rules start being violated where they were previously obeyed, the compression went too far and the line-plus-pointer was not enough. Watch the gates' fire rates, not the prose.

---

## 6. Out of scope

- Deleting command files for token savings. Measured at 0.137% of the per-turn floor; the routing-clarity case is made in S1338 package G on its own merits.
- The model-tier text, which belongs to S1341.
- The canon-level formulation of "gate or compress", which is S1342 - this ticket changes only this repo.

---

## Last Audit

**Date:** 2026-08-01
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 15 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 7

### Manual / on-device

- [ ] §5 bullet 2: `/ui-clarify` invocation count over the following month rises from 1 - a future-month metric, unverifiable at audit time. Re-check at the next audit cycle a month out.

### Notes

- §3.1 (4 gate items), §3.3 bullets 1/3/4/5 - EXEMPT, confirmed already correct/already delivered (S1338 package I, mostly) before this ticket's tactical planning; no S1340 tactical work targeted them, per the strategic spec's own delivery-status notes.
- §5 bullet 3 (Rule 17 gate fires in a deliberately-broken-layout test) - EXEMPT for S1340 specifically: no such test exists in the repo (confirmed via Glob/Grep sweep), but building it is S1338/S1347's contract, not S1340's - the strategic spec explicitly scoped §3.1 out of this ticket's tactical plan. Parked as **S1347** (window-insets-gate-regression-test) rather than blocking this audit.
- Byte counts (all fell): `CLAUDE.md` 32,657 -> 26,111 B (-20.0%), `AGENTS.md` 10,571 -> 10,223 B, `.github/copilot-instructions.md` 8,137 -> 8,002 B.
- Tactical: all 4 phases ✅ Done, INDEX/phase-header status consistent, every step Verification predicate re-confirmed live (not just trusted from Step Log) during this audit.
- Debug-tag invariant: 0 `Timber.d("S1340:` hits in `app_v2/src` - correct, ticket never entered `BlockNeedUserTest` (no on-device gate in scope).
- Mid-ticket tooling fix (Rule 13, out of Files Touched): `scripts/post-change.ps1`'s `-RegistryAck [string[]]` did not split a CSV token passed via `pwsh -File` - fixed inline, dev-logged separately.
- Phase 03 addendum (found during its own Done-Criteria re-read, fixed inline, same subject as this ticket's own §3.3 scope): `AGENTS.md`/`.github/copilot-instructions.md` each independently restated the document-registry mandate in full - missed during tactical planning because the original research grep was scoped to the timestamp sentence only. Compressed to the same one-line pointer used elsewhere.
