---
description: "Use to auto-pick and drive the top-priority open spec. Triggers: 'spec-next', 'what should I work on next', 'pick the next ticket'."
---

# Next-Spec Picker - Auto-Drive Top Priority

Pick highest-priority eligible spec from catalog, hand to `/spec-all`, then loop until no eligible spec remains. Zero-input automation for "just keep working on whatever is most important next."

**Mandate (loop mode).** **Drive every ticket - `Draft` included - as far as it can go: to readiness (`Implemented`/`Verified`) or to a *real* blocker.** A ticket that ends genuinely blocked on a human (a question, an external resource, a real-device-only check) is *reported*, not waited on. The goal is to keep the backlog moving, not to keep one session busy forever - N bounded rounds satisfy it as well as one unbounded one. The loop stops when nothing remains that the machine can advance alone (Stage 6), or when carried context crosses the round-boundary threshold (Stage 5b) - the threshold stop prints a resume handoff and is a report, not a question. Never ask the operator a question mid-loop; defer every human-gated item to the final report. Tempted to skip a `Draft` as too heavy -> "Mandate rationale" in `.claude/reference/spec-next.md`; the answer is no.

## Usage

```text
/spec-next                 # loop: pick top-priority eligible, run /spec-all, repeat until none left
/spec-next --once          # pick top, run /spec-all once, stop
/spec-next --dry           # print ranked candidate list and chosen spec, do NOT execute
/spec-next --plan          # print the full ordered release command-sequence (all open tickets), do NOT execute
/spec-next --plan --flavors "<f1,f2>"   # same, threading flavor scope into the /skill-release line
/spec-next --reset-skips   # clear temp/spec-next-skip-cache.json before running
/spec-next --resume         # continue a session stopped at the context threshold (Stage 5b)
/spec-next --threshold <n>  # override the default 300000-token round-boundary threshold
```

No positional arguments. Selection derived from `PLAN/RELEASE_QUEUE.md` (order) plus `PLAN/spec-catalog.jsonl` (status) and `temp/spec-next-skip-cache.json` (auto-pruned, 7-day TTL). Invocation form unclear -> "Examples" in `.claude/reference/spec-next.md`.

**`PLAN/RELEASE_QUEUE.md` is the selection authority.** It holds the owner's release assignment and hand-kept order; the catalog holds only status. The preflight ranker sorts by that file - release package ascending, then line order inside the package - and uses catalog priority only to break ties for a ticket the file does not list. The buckets after the numbered packages, in order: finished content awaiting its audit (`Implemented` rows in `PLAN/RELEASE_READY.md`), then queue lines parked at `--`, then anything in neither file. Never re-derive a pick from raw priority. Deviating from the order needs a stated reason in the round verdict; a silent deviation is a defect. The same authority governs any plain-language "what should I work on next" answer - quote the package and the line, do not invent an order.

---

## Eligibility

**Eligible** if catalog `status` is one of:

- `Draft`
- `Approved`
- `Tactical`
- `In Progress`
- `Implemented`
- `Partial`
- `Broken`
- `BlockByOtherTask` - **conditional**, preflight resolves it

**Device-conditional:** `BlockNeedUserTest` - not an impl candidate for `/spec-all`, but drainable in Stage 5.5 when a device is attached.

Everything else is excluded. Preflight applies eligibility itself; the loop never filters by hand. Explaining a candidate's presence or absence -> "Eligibility detail" in `.claude/reference/spec-next.md`.

---

## Process

### Stage 0 - Session init and device probe (once per session)

**Session state.** `--resume` present -> `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Resume`; seed the in-memory `processed` set from its `excludeCsv`. No `--resume` -> `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Init` (add `-Threshold <n>` if `--threshold` was passed) - a fresh round, per Hard rules "round memory is session-scoped".

**Parallel sessions are supported (S1437).** Two or three `/spec-next` or `/spec-do` sessions may run at once against one working tree: each gets its own round-state file, so `-Verb Init` no longer refuses. They take *different* tickets because each claims its pick (Stage 3.5 below) before working it. Own session after a threshold `/clear` -> the right verb was `--resume` all along; re-run with it.

Before the loop, detect whether on-device verification is available this run:

```powershell
pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json
```

- Exit 0 (`ready:true`) -> `DEVICE_ONLINE = true`. The session will run **Stage 5.5 (device drain)** after the impl loop exhausts.
- Any other exit -> `DEVICE_ONLINE = false`. The `BlockNeedUserTest` backlog stays parked for the human; Stage 5.5 is a silent no-op.
- Either way, persist it: `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Device -Online <true|false> [-SelectedDevice <id>]`. This is what lets a future threshold-triggered reset restore device state without re-deriving it from a `Resume` payload that could be stale - always re-probe even on `--resume`, a resumed process may be reconnecting after the device was unplugged mid-session.

### Stage 1 - Preflight (rank + skip-cache + auto-skip + drift, one call)

```powershell
pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Exclude <processed-ids-csv>
```

One read-only call returns a single JSON blob with `ranked[]`, `skip_cache` / `skip_cached_ids`, `auto_skipped[]` and `selected`. `order_source` and `current_release` are echoed at the top of the payload - quote them when reporting the pick. A field's exact shape -> "Preflight payload field contract" in `.claude/reference/spec-next.md`; the stages below need no more than those four names.

`-Exclude` carries in-memory `processed` round-memory set (Stage 5) so each loop iteration gets next candidate in one call. First iteration: omit `-Exclude`.

`selected == null` -> branch on `selected_none_reason`, because "the work is finished" and "the work is taken" call for different next moves (S1437):

- `queue-exhausted` / `no-candidate` -> eligible set exhausted -> final report (Stage 6) and stop, as before.
- `all-leased` -> eligible tickets exist but every one is held by a live sibling session. Report each holder from `leased_ids` (`id`, `sessionId`, `last_seen_minutes`), state that the queue is busy rather than finished and that re-running later picks one up, then stop. **Do not wait or poll** - a free ticket is not guaranteed to appear, so blocking would hold the session to a timeout for an answer that is already final.

### Stage 2 - Persist preflight side effects (only mutations in selection)

Preflight is read-only by contract; this skill performs writes it implies:

1. **Persist auto-skips.** For each entry in `auto_skipped[]`, write to persistent cache and log one line `[auto-skip] <id> - <reason>`:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/skip-cache.ps1 -Action add -Id <id> -Reason "<reason>"
   ```
2. **Resolve status mismatch.** If `selected.status_mismatch` non-null, file is authoritative - sync catalog and log `Sync: <id> catalog <catalog> -> <file> (file authoritative)`:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <id> -Status <file-status>
   ```
   If synced status no longer eligible, add `<id>` to `processed` and re-run Stage 1 with updated `-Exclude`.

### Stage 3 - Drift gate

`selected.drift.verdict == DRIFT` (what that verdict means -> "Stage 3" note in `.claude/reference/spec-next.md`): note it in the round verdict, then:

- `selected.last_audit_present` is true OR spec has "Implementation State" block -> proceed to Stage 4 (`/spec-all` resumes at right stage).
- Neither -> defer: skip-cache spec with `Reason "drift-needs-review"` (TTL 3 days), surface in final report under "Drift detected - needs manual review", release any lease on it (`ticket-lease.ps1 -Verb Release -Id <id>` - this path never reaches Stage 5, where the normal release lives), add to `processed`, re-run Stage 1.

### Stage 3.5 - Claim the ticket (S1437)

Preflight ranks but never claims - it is read-only by contract, and a ranking call that claimed would take tickets just because someone looked at the queue. The claim is what actually arbitrates between two sessions that ranked the same top ticket:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id <selected.id> -Reason "/spec-next"
```

- Exit **0** -> the ticket is yours. Proceed to Stage 4.
- Exit **3** -> a sibling session claimed it first. Log one line `[claim-lost] <id> - held by <session>`, add the id to the in-memory `processed` set, and re-run Stage 1 with the updated `-Exclude`. **This is a normal outcome, not an error** - do not stop the round and do not report it as a failure.

Claim *after* the drift gate, never before: a ticket deferred for manual drift review must not be left leased, or no sibling can pick it up either.

### Stage 4 - Delegate to `/spec-all` (with preflight handoff)

Hand chosen ticket id to `/spec-all`, passing preflight `selected` payload as already-resolved context so `/spec-all` does not re-resolve it:

```text
/spec-all <Sxxxx>
preflight: status=<status> tier=<tier> tactical_folder=<bool> last_audit=<bool> timber_tags_kt=<n> drift=<verdict> sections=<count>; depends_on=<id(status),..>
```

All hard-stops, build gates, defer-first behaviour, and debug-tag lifecycle still come from `/spec-all` - do NOT reimplement here. Delegated run re-resolving the ticket -> "Stage 4 handoff" in `.claude/reference/spec-next.md`.

While `/spec-all` runs, do not start another spec. One spec per delegation.

### Stage 5 - Inspect outcome, record, and loop

When `/spec-all` returns, re-read chosen spec's catalog row (single authoritative read):

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

**Record before anything else** - so a threshold stop (Stage 5b, next) can never lose a just-completed ticket:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Record -Id <Sxxxx> -Outcome <advanced|verified|blocked|skipped> [-Note "<short free-text>"]
```

`-Outcome` maps from status: `Verified` -> `verified`; any `Block*` -> `blocked`; unchanged from start -> `skipped`; anything else (`Implemented`/`Partial`/`Broken`/still `In Progress`) -> `advanced`. Verdict wording per status -> "Round-outcome table" in `.claude/reference/spec-next.md`, read before writing the round verdict.

**Then release the lease (S1437)** - for *every* outcome, `advanced` / `verified` / `blocked` / `skipped` alike:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Release -Id <Sxxxx>
```

A blocked ticket must not stay leased, or no sibling can pick it up. A failed release is logged and does not stop the round: the lease expires on its own with the session's liveness, so a missed release costs a delay, not a stuck ticket.

**Round memory.** Maintain in-memory `processed` set of ticket ids touched during this `/spec-next` invocation (mirrors what was just written to disk - the in-memory set still feeds Stage 1's `-Exclude`, the state file is what survives a reset). After Stage 5, add just-handled id. Pass whole set to next Stage 1 call via `-Exclude`.

**Context reset happens only at Stage 5b, on the mechanical threshold - never earlier, never mid-`/spec-all`.** The loop does not self-judge context by feel; `-Verb CheckContext` decides. Read "Context management (mid-loop reset)" in `.claude/reference/spec-next.md` for what the handoff must preserve.

**`--once` mode.** Skip loop, Stage 5b and Stage 5.5. After Stage 5 print final report and exit.

### Stage 5b - Context-threshold stop

Immediately after `-Verb Record`, check carried context:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb CheckContext
```

- Exit 0 (under threshold) -> continue to the next Stage 1 call, as before.
- Exit 3 (threshold crossed) -> print the handoff and **stop the loop**:
  ```powershell
  pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Handoff
  ```
  Present its output verbatim - it is the deterministic artifact (do not compose a replacement in prose, per strategic S1339 §4.4). This is a **report, not a question** - it does not violate "never ask the operator". Do not proceed to Stage 1 after printing it; do not proceed to Stage 5.5 either - the handoff already told the operator what is next.
- Exit 2 (cannot verify) -> log the reason in the round verdict and continue to the next Stage 1 call as if exit 0. A sensor outage must not silently turn into an unbounded session, but a single unreadable check must not stop the loop either - the very next round re-checks.

**Never stop for any other reason.** The loop's only stop conditions are: nothing left in the eligible set (Stage 6), the context threshold (Stage 5b), or a genuine human-gated blocker recorded and carried to the final report. `/spec-do` is the sanctioned way to decline the threshold trade entirely.

### Stage 5.5 - Device-verification drain (full loop only, `DEVICE_ONLINE`)

Runs once, after Stage 1 first returns `selected == null` (impl backlog exhausted) and only when `DEVICE_ONLINE` (Stage 0).

1. List the `BlockNeedUserTest` backlog:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/search.ps1 -Status BlockNeedUserTest -Format json
   ```
   Empty -> skip straight to Stage 6.
2. Delegate the whole backlog to `/spec-sweep` in one delegation. Do **not** reimplement device-test logic here; `/spec-next` selects, `/spec-sweep` executes, exactly as `/spec-all` does for impl.
3. `/spec-sweep` returns a per-ticket verdict. Fold its results into the session tally: each `Verified` counts as closed; each ticket it reports as still-blocked because the check needs a **real device** (emulator insufficient, per `statusNote`) goes to the final report's "Waiting on human" list with that reason.

Stage 5.5 never asks the operator anything and never blocks. After it returns, proceed to Stage 6.

### Stage 6 - Final report

When Stage 1 returns `selected == null` (truly empty or all remaining excluded), print the session report. Read "Final report format" in `.claude/reference/spec-next.md` before printing - literal template plus the two `BlockNeedUserTest` cases (`DEVICE_ONLINE` true vs false).

Source "Processed this run" from disk, not only this segment's in-memory list - it spans every round since the last `-Verb Init`, including rounds from before a threshold-triggered `/clear` + `--resume`, which the in-memory `processed` set alone cannot:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb Report
```

Run dev log once for session:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec-catalog.jsonl" "spec-next" "Session: <N> processed, <M> verified, <K> blocked"
```

---

## `--dry` mode

Run Stage 1 (preflight read-only) only. Skip Stages 2..6. Use `ranked[]`, `auto_skipped[]`, and `selected` straight from preflight JSON. Do NOT mutate anything (no `skip-cache.ps1 -Action add`, no `update.ps1`, no `add_to_dev_log.ps1`). Printed layout -> "`--dry` mode output format" in `.claude/reference/spec-next.md`.

---

## `--plan` mode (release command-sequence)

Emit full ordered command-sequence driving **every** open ticket to releasable state, ending in release tail. Read-only: prints, executes nothing, mutates nothing.

One call does whole job - the generator, not this skill, owns every ordering decision:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/release-plan.ps1
# -Format json            # structured plan for programmatic use
# -Flavors "standard,vr"  # thread flavor scope into the trailing /skill-release line
```

Thread any flavor argument operator passed (`/spec-next --plan --flavors "..."`) into `-Flavors`. Present script's text output **verbatim** - it is the deterministic artifact. Do NOT re-derive, re-sort, or hand-edit sequence; do NOT drop heavy `Draft`/`Approved` items.

The generator emits phases A-D plus a Deferred comment list. Explaining or sanity-checking the block -> "`--plan` mode phase catalogue" in `.claude/reference/spec-next.md`; never read it to re-derive the block.

Skip Stages 1..6 entirely in this mode - no preflight, no skip-cache, no loop, no dev-log. Only action is running generator and presenting its block.

---

## Hard rules

- **The queue owns the order.** Selection follows `PLAN/RELEASE_QUEUE.md` (package, then line order) as returned by preflight. Never reorder by priority, never skip a higher line to reach a more interesting one, and never edit `rel` or the line order of that file from this skill - it is the owner's column. A justified deviation is reported in the round verdict; an unreported one is a defect.
- **Never edit `PLAN/spec-catalog.jsonl` directly** - only via `update.ps1`, `select.ps1`, `search.ps1`, `spec-next-preflight.ps1` (read-only).
- **Do not duplicate `/spec-all` logic** - every progress decision delegates to it. This skill's responsibility is *selection*, not *execution*.
- **No user prompts in loop mode.** A stage detecting unresolvable ambiguity -> skip spec via round memory + persistent skip-cache, continue loop. Final report names all skipped specs. `AskUserQuestion` MUST NOT be invoked from any stage of `/spec-next`.
- **Draft is not a blocker.** Never skip a `Draft`/`Approved` because it looks unresearched or heavy. Hand it to `/spec-all`; readiness or a real `Block*` is the only acceptable terminal for it.
- **Compact between rounds, never mid-delegation.** `/compact` is allowed (and expected on long sessions) only at a round boundary, preserving `processed` + session tally + `DEVICE_ONLINE`. Never compact while `/spec-all` or `/spec-sweep` is running.
- **Spec status sync is one-way per run.** If Stage 2 syncs catalog from file, do not later flip it back from catalog side mid-run.
- **No spec file rewrites here.** Sync touches journal, not `.md`. If `.md` malformed (preflight returns it under `malformed`), skip spec and list under "Skipped" in final report.
- **Round memory is session-scoped.** Resets on every fresh `/spec-next` invocation. Crashes / interruptions do not persist it.
- **Branch awareness.** Do not switch git branches. User controls active branch; `/spec-next` runs on whatever branch is checked out.
- **Forbidden:** writing to `PLAN/spec-catalog.jsonl` directly; writing to `temp/spec-next-skip-cache.json` directly (use `skip-cache.ps1`); writing to `temp/SPEC-TICKET.LEASES/` directly (use `ticket-lease.ps1` - a hand-written lease bypasses the atomic claim that keeps two sessions off one ticket); renaming spec files; creating audit / fix files in `PLAN/`.

Script call ownership (this skill vs a delegated one) -> "Spec Catalog hooks" in `.claude/reference/spec-next.md`.
