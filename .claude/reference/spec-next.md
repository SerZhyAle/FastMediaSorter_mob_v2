# /spec-next - Reference

On-demand companion to the driver `.claude/commands/spec-next.md`. Nothing here is read unconditionally - the driver names the section and the condition at the point of use. Nothing here is needed to rank, select, or start a round.

Two output shapes:
- **Loop / `--once`** - *execution*: advance one spec at a time via `/spec-all`, then (device attached) drain the device-verifiable `BlockNeedUserTest` backlog via `/spec-sweep`.
- **`--plan`** - *planning*: print full ordered release command-sequence covering every open ticket (incl. every `Draft`/`Approved`), ending in release tail. Executes nothing. See [`--plan` mode](../spec-next.md#--plan-mode-release-command-sequence) in the driver.

Sections:

1. Mandate rationale (loop mode)
2. Eligibility detail - conditional and excluded (Eligibility)
3. Preflight payload field contract (Stage 1)
4. Stage 0, 3, 4 and 5.5 notes
5. Context management (mid-loop reset) (Stage 5)
6. Round-outcome table (Stage 5)
7. Stage 5b - threshold stop detail
8. Final report format (Stage 6)
9. `--dry` mode output format
10. `--plan` mode phase catalogue
11. Spec Catalog hooks
12. Examples

---

## Mandate rationale (loop mode)

Keep the machine busy producing *every* piece of work that can be done **without the human**. Bias toward release-blocking tickets first (they rank highest), but the real goal is to exhaust the autonomous backlog: implementation, audits, drift reconciliation, research that resolves from the codebase, and - when a device/emulator is attached - on-device verification of `BlockNeedUserTest` tickets.

`Draft` is a first-class candidate, not a thing to fear: hand it to `/spec-all` and let that skill push it through research/approval/tech/dev; only when it hits a genuinely human-gated question does it land in `BlockQuestions` - which is the correct "driven to a blocker" outcome, reported at the end. Do **not** pre-emptively skip a `Draft` because its research section looks heavy; research that resolves from the codebase is autonomous work, and only `/spec-all` (not this picker) decides a question truly needs the human.

Stage 2 persists the `auto_skipped[]` entries preflight produced. These close deterministic skip cases (Tier 5 epic-containers, §12 owner-gate, unverified blocker chains) with no `AskUserQuestion`. A heavy research section is **not** among them - `Draft`/`Approved` tickets always go to `/spec-all`, which drives them to readiness or a real blocker.

---

## Eligibility detail - conditional and excluded

**Conditional - the two special cases.**

`BlockByOtherTask` - **conditional**: included by preflight, then auto-skipped (`reason: blocker-not-verified`) unless the named blocker is currently `Verified`. The blocker is read only from a channel that states the direction - a `**Depends on:**` line, or a `Blocker: Sxxxx` / `Блокер: Sxxxx` token in §10 or in the statusNote. A ticket merely listed in §10 prose is a relation, not a blocker (S1482); a spec that records none gets `reason: blocker-unresolvable`, which means "fix the spec", not "wait".

**Device-conditional** (the autonomous-verification backlog): `BlockNeedUserTest` - **not** an impl candidate for `/spec-all`, but **is** drainable autonomous work *when a device/emulator is attached*. The impl loop ignores these; the post-impl **Stage 5.5 device drain** routes the whole `BlockNeedUserTest` backlog to `/spec-sweep`, which runs each ticket's device test and flips it to `Verified`/`Partial`/`Broken`. With no device attached they stay parked (listed under "Waiting on human" in the final report). Tickets whose `statusNote` says a **real device** is required (not an emulator) are attempted by `/spec-sweep`, which reports them back as still-blocked when the emulator cannot satisfy the check - those remain human-gated.

**Excluded** (always):

- `Verified`, `Archived` - terminal
- `BlockQuestions`, `BlockExternal` - waiting on human / external resource
- Any unknown / malformed `status` - skip and continue down ranked list

Auto-skip predicates: they replace every previous owner-gate / tier-5 / VR-child prompt, which is why no stage of `/spec-next` invokes `AskUserQuestion`.

---

## Stage 1 - preflight payload field contract

`spec-next-preflight.ps1` returns a single JSON blob:

- `ranked[]` - eligible set (statuses above), already sorted by the release plan: queue package asc -> queue line order asc -> `priority` desc -> `updated` desc -> `id` asc, with active persistent skip-cache and `-Exclude` round-memory set already removed. Each row carries `release` (its package, `--` when parked, `null` when in neither file) and `side` (`queue` = work left, `ready` = finished content awaiting audit).
- `skip_cache` / `skip_cached_ids` - active persistent skips and which ranked ids they removed (informational; no action needed).
- `auto_skipped[]` - candidates preflight previewed and rejected while walking down to selection. Each `{ id, reason, detail }`, `reason ∈ { tier-5-epic | owner-gate | blocker-not-verified }`. These are structural / human gates only; preflight never auto-skips a `Draft`/`Approved` for a heavy research section - `research_open_count` stays informational **for auto-skip**. It is not merely advisory to a reader, though: since S1621 it is the closing gate's own number, and its companion `research_uncarried_count` names the open items carrying no `Carrier: Sxxxx` - a non-zero value predicts that `Implemented` / `Verified` will be refused.
- `leased_ids[]` - candidates dropped because a **live sibling session** holds a ticket lease on them (S1437). Each `{ id, sessionId, host, reason, last_seen_minutes }`; `last_seen_minutes` is `null` when that session's transcript is unreachable. Own-session leases are never listed and never exclude. Empty array when running alone. Report these verbatim when `selected_none_reason` is `all-leased` - the holder is the answer to "why is there nothing to do".
- `selected_none_reason` - `null` when a ticket was selected. Otherwise one of:
  - `queue-exhausted` - no eligible ticket existed at all. The backlog is done.
  - `all-leased` - eligible tickets exist and every one is held by a live sibling. The queue is **busy, not finished**; re-running later picks one up. Never wait or poll on this - a free ticket is not guaranteed to appear.
  - `no-candidate` - eligible tickets existed but none survived skip-cache, `-Exclude`, auto-skip or the `-MaxScan` walk. Same handling as `queue-exhausted`.
- `selected` - chosen ticket's full `preview.ps1` payload (`status`, `frontmatter`, `sections`, `tactical_folder`, `last_audit_present`, `timber_tags_kt`, `depends_on`) plus `drift` (`drift-check.ps1` verdict object) and `status_mismatch` (`{catalog,file}` or `null`). `null` when nothing was selected - read `selected_none_reason` for which of the three cases it was.

The call itself replaces the previous `search.ps1` + manual rank + `skip-cache.ps1 -Action list` + per-candidate `preview.ps1` + `drift-check.ps1` chain.

---

## Stage 0, 3, 4 and 5.5 notes

**Stage 0 - device probe.** This is a single read-only probe; never blocks the loop. Runs every invocation, including `--resume` - a resumed process is a fresh probe, not a continuation, since the device may have changed while the session was stopped. The result is persisted via `-Verb Device` so Stage 5.5's `DEVICE_ONLINE` check never depends on in-memory state surviving a reset.

**Stage 3 - the drift verdicts (S1429, narrowed by S1634).** Two live values, because the check reads only the working tree:

- `CLEAN` - no inline marker carries the id.
- `DRIFT` - inline markers exist in `app_v2/src/`. The fix is likely already (partly) in code and `/spec-all` would re-discover it expensively, unless something accounts for it.
- `COMMIT_ONLY` - **retired**, never returned. It meant "a commit mentions the id", which in a one-developer, one-branch repository where the whole tree is committed under a timestamp subject was true of nearly every ticket; it produced six of the `drift-needs-review` skip-cache entries standing on 2026-08-09, each explaining in its own words that the finding was false. The field stays in the payload as an empty list so an older caller still parses.

The third proof against `DRIFT` is `selected.tactical_index.fresh`: a tactical plan last written no earlier than the sources carrying this ticket's markers. A Tier-3 ticket records progress in its plan's phase counters, not in the strategic spec, so reading only `Last Audit` and `Implementation State` called every in-flight tactical ticket unaccounted. Freshness is a comparison rather than mere existence, so a plan abandoned before the code was written cannot vouch for it - but both sides of the comparison are file write times in the tree, never commit dates. `plan_written_at` and `marked_written_at` are reported next to the verdict so the answer can be read without re-deriving it. The commit-date version deferred the two top tickets of release package 32 in consecutive rounds on 2026-08-14, both false.

**Stage 4 handoff - what `/spec-all` skips.** `/spec-all` trusts this context and skips its own opening `select.ps1` / catalog re-query for this ticket (its Resume Map keys off handed `status`). It does NOT re-run `preview.ps1` / `drift-check.ps1` for same ticket.

**Stage 5.5 - what the stage is for.** This is the "no urgent tickets left -> now verify what the machine can verify itself" step. It never blocks: a `/spec-sweep` ticket that goes inconclusive on the emulator stays `BlockNeedUserTest` and is reported, not retried in a loop. Step 2 is a single delegation because `/spec-sweep` owns device-test batching, evidence harvest, and the `Verified`/`Partial`/`Broken` transition + debug-tag removal per ticket.

---

**Stage 5 - why round memory exists.** Passing the whole `processed` set to the next Stage 1 call via `-Exclude` prevents infinite re-selection of a spec whose status `/spec-all` could not advance.

**`--plan` - phase letters and generator ownership.** A = implementation, B = dependency chains, C = verification, D = release. The generator owns ranking, status→command map, dependency ordering, phase grouping.

---

## Context management (mid-loop reset)

The loop is designed to run for many rounds and will accumulate context - that is expected. It no longer self-judges when to reset: `-Verb CheckContext` (Stage 5b) is the sole trigger, on the fixed threshold (default 300000, `-Threshold`/`--threshold` overridable). On a threshold stop, `-Verb Handoff` recommends `/clear` rather than `/compact` - round state (`processed`, tally, `DEVICE_ONLINE`, `selectedDevice`) already lives on disk in `temp/spec-next-session.<sessionId>.json`, so a `/compact` summary would only re-carry what the state file already holds at zero cost. After `/clear`, `/spec-next --resume` reads that file back (`-Verb Resume`) and continues at Stage 1 with the restored `-Exclude` set - nothing is re-derived, nothing is reprocessed.

The state file is **per session** (S1437): `temp/spec-next-session.<sessionId>.json`, so two or three pickers run side by side without sharing one file. `-Verb Init` no longer refuses - the exit-4 refusal it used to carry (S1396) existed to stop two sessions duplicating tickets, and the ticket lease now prevents that properly one layer up. A legacy single-file `temp/spec-next-session.json` is adopted into the per-session path on the first `Init`/`Resume`, reported as `adoptedLegacy` in `Resume`'s JSON, so a session mid-round across the upgrade keeps its round. `-Verb Resume` still adopts ownership and prints the displaced owner as `previousOwner`. `-Verb Record` is keyed by ticket id: a ticket recorded twice in one session (`advanced` when the impl lands, `verified` once the audit passes) updates its own row and the tally is recomputed, so `processed` counts tickets rather than status changes.

---

## Stage 5 - round-outcome table

| New status | Round verdict | Loop action |
|------------|---------------|-------------|
| `Verified` | Closed ✅ | Continue loop |
| `Implemented` | Audit deferred / max iterations hit | Continue loop (add to `processed`; `/spec-all` already ran F5) |
| `Partial` / `Broken` | Audit incomplete | Continue loop (add to `processed`) |
| `BlockNeedUserTest` | Device verification pending | Continue loop; ticket is collected for **Stage 5.5** drain (handled there if `DEVICE_ONLINE`, else parked for human) |
| `BlockByOtherTask` | Blocked by new dependency | Continue loop |
| `BlockExternal` / `BlockQuestions` | Hard external block | Continue loop |
| `Archived` | Aborted as archived | Continue loop |
| Unchanged from start | `/spec-all` made no progress | Add to `processed`, continue loop |
| (threshold crossed) | Stage 5b handoff printed | Loop stops - operator runs one of the three recommended commands |

---

## Stage 5b - threshold stop detail

**Record-before-check ordering.** `-Verb Record` always runs before `-Verb CheckContext` (strategic S1339 §4.2: "so a reset can never lose a completed ticket"). If the order were reversed, a threshold crossing detected before the just-finished ticket's outcome was persisted would print a handoff whose "what just happened" omits the very ticket that triggered the check.

**Exit-2 fallback.** `CheckContext` returning 2 ("cannot verify" - no session id in environment, no transcript file for the session, or no assistant usage record yet) is not treated as a stop and not treated as "definitely under threshold" - it is logged in the round verdict and the loop continues to the next Stage 1 call. The very next round's `-Verb Record` -> `-Verb CheckContext` pair re-checks, so a single unreadable transcript line or a momentarily-missing env var cannot silently turn into an unbounded session, nor can it wrongly halt a session that is actually fine.

**Handoff section order is fixed.** Strategic S1339 §4.4 fixes the order: what just happened, why it stopped, what is next in the queue, the three recommended commands, what needs the human. `-Verb Handoff` generates this - the driver presents its output verbatim and never composes a replacement in prose, the same discipline `--plan` mode already applies to `release-plan.ps1`'s output.

---

## Stage 6 - final report format

```text
spec-next: session complete

Processed this run:
  Sxxxx <slug> - <start-status> -> <end-status>   [✅ Verified | ⚠️ Partial | 🛑 Blocked | ⏱️ Incomplete]
  Syyyy <slug> - <start-status> -> <end-status>   [...]

Skipped (in eligibility filter but not advanced):
  Sxxxx <slug> - <status>: <reason>

Waiting on human (not advanced):
  Sxxxx <slug> - BlockNeedUserTest (real device required - emulator insufficient)
  Syyyy <slug> - BlockQuestions
  Szzzz <slug> - BlockExternal
  ...
```

`BlockNeedUserTest` entries appear here only when they remain blocked after the run:
- `DEVICE_ONLINE` was true -> Stage 5.5 already ran `/spec-sweep`; list only the tickets it reported as still-blocked (real-device-only checks), each with that reason. Do **not** append the attach-a-device tip - a device was attached.
- `DEVICE_ONLINE` was false -> the whole `BlockNeedUserTest` backlog is parked; append one line: `Tip: attach a device and re-run /spec-next (or /spec-sweep) to drain the BlockNeedUserTest backlog.`

---

## `--dry` mode output format

```text
spec-next: dry run

Eligible candidates (ranked):
  Sxxxx <pri> <status> <updated> <slug>
  Syyyy <pri> <status> <updated> <slug>
  ...

Would auto-skip: Szzzz (<reason>), ...
Would run: /spec-all Sxxxx
```

---

## `--plan` mode - phase catalogue

Why the mode exists: the loop advances ONE top-priority spec per `/spec-all` delegation and never reaches release step; `--plan` instead enumerates **whole active catalog** - including every `Draft` and `Approved` present - into a phased, dependency-ordered, copy-pasteable command block. The point of `--plan` is full coverage - generator annotates epics/owner-gates instead of silently deferring them, which is why no heavy `Draft`/`Approved` item may be dropped from the printed block. If operator then wants to *execute* plan, they run listed commands (or `/spec-next` to auto-drive loop-eligible subset of Phase A/B).

Phases generator produces (status → command map is fixed):

- **A - Implementation** (`Draft`→`/spec-all`; `Approved`→`/spec-tech`+`/spec-dev`; `Tactical`/`In Progress`→`/spec-dev`; `Partial`/`Broken`→`/spec-fix`+`/spec-check`) for specs with no in-plan prerequisite. Ordered by `PLAN/RELEASE_QUEUE.md` (package, then line order), priority only as a tiebreak. Trailing command of each pair is prior skill's own auto-chain (`/spec-tech`→`/spec-dev`, `/spec-fix`→`/spec-check`), kept explicit so sequence stays complete on PRIMITIVE / blocked / `--dry-run` branches where chain does not fire.
- **B - Dependency chains** - any `BlockByOtherTask` spec, plus any impl spec whose `statusNote` names an in-plan blocker. Ordered after blocker (annotated `(after Sxxxx)`) in same global topological order as Phase A, so a blocker always precedes its dependent across A/B boundary. `BlockByOtherTask` rows emit `unblock first` comment (`update.ps1 -Id <id> -Status <pre-block>`): `/spec-tech` and `/spec-dev` both hard-abort while status is `Block*`, so restore must run before listed commands.
- **C - Verification** (`BlockNeedUserTest` collapsed into ONE `/spec-sweep`; each `Implemented`→`/spec-test-device`+`/spec-check` - device step needs device online, else skip to static `/spec-check`).
- **D - Release** (`/spec-prerelease` → `/skill-release`). Run from a `DEBUG-v00N` branch.
- **Deferred** (`BlockExternal`/`BlockQuestions`) - listed as comments, no command: cannot be driven from catalog (external / human gate).

---

## Spec Catalog hooks

- **Session start:** `session-bootstrap.ps1` (Stage 0, one call). It composes four children and reports each as its own block: `session` - `spec-next-session.ps1 -Verb Init|Resume|Device`, **writes** the round-state file; `device` - `device-ready.ps1`, read-only; `selection` - `spec-next-preflight.ps1`, read-only; `lease` - `ticket-lease.ps1 -Verb Claim`, **writes**, and this skill never enables it because the drift gate must run first. The package itself derives nothing: ranking, skip-cache policy, release-queue order and the drift verdict all come out of the children unchanged.
- **Reads:** `spec-next-preflight.ps1` (Stage 1 on every iteration after the first - the first one's payload arrives inside the bootstrap; rank + skip-cache consume + per-candidate preview + drift, read-only), `select.ps1` (post-`/spec-all` status check in Stage 5), `search.ps1 -Status BlockNeedUserTest` (Stage 5.5 backlog list, read-only), `release-plan.ps1` (single `--plan` call: whole-catalog phased release sequence, read-only).
- **Writes:** `skip-cache.ps1 -Action add` for each `auto_skipped[]` entry and on `drift-needs-review`; `update.ps1 -Status` only when preflight reports `status_mismatch`; `skip-cache.ps1 -Action reset` on `--reset-skips`.
- **Delegations:** impl rounds -> `/spec-all` (Stage 4); device-verification drain -> `/spec-sweep` (Stage 5.5). This skill selects; the delegated skills execute and own their catalog transitions.
- **Indirect writes:** all status transitions during execution come from `/spec-all`, `/spec-sweep`, and their sub-skills (`/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-test-device`). This skill never sets `Implemented`, `Verified`, `Partial`, `Broken`, or any `Block*` directly.

The **Forbidden** list lives in the driver, not here.

---

## Examples

```text
# Full session (device attached)
/spec-next
# -> Stage 0 device probe: emulator-5554 online -> DEVICE_ONLINE
# -> preflight selects S0142 (pri 90, In Progress), runs /spec-all S0142 -> Verified
# -> preflight (-Exclude S0142) selects S0156 (pri 85, Tactical) -> BlockNeedUserTest
# -> preflight (-Exclude S0142,S0156) selects S0200 (pri 80, Draft) -> Implemented
# -> preflight returns selected=null; impl backlog exhausted
# -> Stage 5.5: BlockNeedUserTest backlog (incl. S0156) -> /spec-sweep
#    -> emulator-verifiable tickets flip to Verified/Partial; real-device-only ones reported back
# -> final report: processed + swept + the few real-device-only items left for the human.

# One round only
/spec-next --once
# -> picks top eligible, delegates once, stops with report.

# Preview without execution
/spec-next --dry
# -> prints ranked list + auto-skips + chosen, no mutations.

# Full release command-sequence (planning, no execution)
/spec-next --plan
# -> runs release-plan.ps1, prints phased command block:
#    Phase A (impl, every Draft/Approved) -> B (dependency chains) ->
#    C (/spec-sweep + Implemented verify) -> D (/spec-prerelease -> /skill-release),
#    plus a Deferred (BlockExternal/BlockQuestions) comment list. No mutations.
/spec-next --plan --flavors "standard,vr"
# -> same, with the trailing release line rendered as `/skill-release standard,vr`.
```
