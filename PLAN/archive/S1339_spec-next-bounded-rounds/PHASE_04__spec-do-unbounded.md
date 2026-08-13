# Phase 04 - `/spec-do`, the unbounded variant

**Strategic spec:** [`../S1339_spec-next-bounded-rounds.md`](../S1339_spec-next-bounded-rounds.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Create `.claude/commands/spec-do.md`: same process as `/spec-next`, minus the threshold stop. Route it in `CLAUDE.md` section 3, beside `/spec-next`, satisfying a placeholder comment already left there for this exact ticket.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done - `spec-next-session.ps1`'s verb set is complete.
- [ ] Phase 03 need not be done first (no forward reference - `/spec-do` calls the same script Phase 01/02 produced; it references `/spec-next`'s *stage structure*, which Phase 03 is rewriting, but a link to a file, not a line number, so ordering with Phase 03 does not matter for correctness). List as depending only on 01/02 to allow parallel authorship; verify against the *post-Phase-03* text before flipping this phase Done, since the "Differences from /spec-next" section below names Stage 5b, which only exists after Phase 03 lands.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/spec-do.md` | New | ≤ 60 |
| `CLAUDE.md` | Modified | ≤ 190 (from 187) |

---

## Steps

### Step 04.1 - Write `.claude/commands/spec-do.md`

**Files:** `.claude/commands/spec-do.md`
**Depends on:** - start of phase (content-verify against Phase 03's landed Stage 5b before marking this step done)

**Prompt for developer:**

> Follow the repo's established alias pattern - `.claude/commands/arc.md` delegates its full process to `/spec-arc` with a "Full process: [link]" line and states only what differs. `/spec-do` is not a pure alias (it has one substantive control-flow delta plus two presentation requirements), so state the deltas explicitly rather than leaving them to inference. Frontmatter `description` must trigger on "spec-do", "run unbounded", "don't stop for context" (mirrors the description-driven routing every other command uses).
>
> Body, in order:
>
> 1. **Loud identity paragraph**, first thing after the title - strategic §4.5: "the command must state at start that it is the unbounded variant and that context will grow without limit". Literal sentence: this is the unbounded variant of `/spec-next`; context grows without limit until the backlog is exhausted or a genuine blocker is hit; use `/spec-next` for the bounded default.
> 2. **Full process: `.claude/commands/spec-next.md`.** State every stage, hard rule, and eligibility rule is identical; the differences are exactly the three below.
> 3. **Difference 1 - Stage 5b never stops.** `-Verb CheckContext` still runs after every `-Verb Record` (identical call). Its `tokens`/`threshold` JSON is printed in the round verdict every round, even under threshold (strategic §4.5: "print the running context at every round boundary"). Exit 3 does not call `-Verb Handoff` and does not stop the loop here - log `[unbounded] context <tokens>k / threshold <n>k - continuing` and proceed straight to the next Stage 1 call.
> 4. **Difference 2 - loud start banner.** Before Stage 0, print exactly: `/spec-do: UNBOUNDED - context will grow without limit until the backlog is exhausted or a genuine blocker is hit. Use /spec-next for the bounded default.` Not optional, not skippable.
> 5. **Difference 3 - usage forms.** `/spec-do` (fresh, `-Verb Init`), `/spec-do --resume` (`-Verb Resume` - works regardless of whether the prior session was `/spec-next` or `/spec-do`, same state file per strategic §4.5), `/spec-do --once`, `/spec-do --dry`, `/spec-do --plan`, `/spec-do --threshold <n>` (accepted, reported, never halts anything here).
> 6. **Closing line** naming the S1338 package I ceiling as future-scope ("once landed, `-CheckContext` gains a second stop reason there - not yet active") and repeating strategic §4.5's own warning against adding a bypass flag to `/spec-next` instead of using this named command.

**Verification:**

- `Glob` - `.claude/commands/spec-do.md` exists.
- `Grep -n "UNBOUNDED"` in the file returns at least one hit.
- `Grep -n "Verb CheckContext"` and `Grep -n "Verb Handoff"` - `CheckContext` present, and `Handoff` appears only inside a sentence saying it is *not* called (confirm by reading the matched line, not just presence).
- `Grep -n "spec-next.md"` returns at least one hit (the "Full process" link).
- `Grep -n "\-\-resume"` returns at least one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 5/5 PASS. `Verb Handoff` occurrence confirmed inside "does **not** trigger `-Verb Handoff`" - the not-called sentence, as required. The harness registered the new command immediately (surfaced in the available-skills listing right after the file write). Files: `.claude/commands/spec-do.md` (new, 21 lines). `post-change.ps1 -ChangeType Doc -RegistryAck 'repository-rules'` PASS.

---

### Step 04.2 - Route `/spec-do` in `CLAUDE.md` section 3

**Files:** `CLAUDE.md`
**Depends on:** Step 04.1 (the file must exist before it is routed)

**Prompt for developer:**

> `CLAUDE.md` line 23 already carries a placeholder left for this exact moment:
> ```
> <!-- `/spec-do` - the unbounded loop variant defined in S1339 §4.5 - is routed here as an explicit opt-in beside `/spec-next` the moment S1339 lands `.claude/commands/spec-do.md`. Not listed before the file exists: a routed command that does not exist is worse than one that is not yet routed. -->
> ```
> Its condition is now met. Delete that comment line and, immediately after line 28 (`` - `/spec-next`: pick and drive the next ticket autonomously, ordered by `PLAN/RELEASE_QUEUE.md` (see section 4). The default answer to "what should I work on next". ``), insert:
> ```
> - `/spec-do`: identical to `/spec-next`, minus the context-threshold stop - the explicit opt-in for an unbounded session. See `.claude/commands/spec-do.md`.
> ```
> Do not touch the other placeholder comment on the preceding line (S1338/S1340 ownership note) - it governs the whole section, not just this entry, and stays live for future routing decisions.

**Verification:**

- `Grep -n "moment S1339 lands"` in `CLAUDE.md` returns zero hits (placeholder removed).
- `Grep -n "spec-do.*identical to.*spec-next"` in `CLAUDE.md` returns one hit.
- The new line appears directly after the `/spec-next` line (confirm by reading the file region, not just presence anywhere).

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS (placeholder gone, routing line present, appears directly after `/spec-next` at lines 27-28). Files: `CLAUDE.md` (+1 routing line, -1 satisfied placeholder comment). `post-change.ps1 -ChangeType Doc -RegistryAck 'repository-rules'` PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Re-verified Step 04.1 against the actual landed Phase 03 text: `spec-do.md` was written *after* Phase 03 closed, so its "Stage 5b never stops" and `-Verb Record`/`-Verb CheckContext`/`-Verb Handoff` references already match the real Stage 5b section verbatim - no drift to reconcile.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entries added for `.claude/commands/spec-do.md` and `CLAUDE.md` - `post-change.ps1`'s `[dev-log]` gate logged one line per step.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Both files are documentation-only additions; no code, no broad/empty catch, no silent-failure surface introduced.

---

## Handoff Notes to Next Phase

Both commands exist and share `spec-next-session.ps1`. Final phase is documentation/catalog bookkeeping only.

---

## Rollback Plan

Revert phase commit(s) - `spec-do.md` is a new file with no other consumers; the `CLAUDE.md` change is a one-line routing addition plus a comment deletion, trivially revertible.
