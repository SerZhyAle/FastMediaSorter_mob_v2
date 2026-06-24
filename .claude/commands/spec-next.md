# Next-Spec Picker - Auto-Drive Top Priority

Pick highest-priority eligible spec from catalog, hand to `/spec-all`, then loop until no eligible spec remains. Zero-input automation for "just keep working on whatever is most important next."

Two output shapes:
- **Loop / `--once`** - *execution*: advance one spec at a time via `/spec-all`.
- **`--plan`** - *planning*: print full ordered release command-sequence covering every open ticket (incl. every `Draft`/`Approved`), ending in release tail. Executes nothing. See [`--plan` mode](#--plan-mode-release-command-sequence).

## Usage

```text
/spec-next                 # loop: pick top-priority eligible, run /spec-all, repeat until none left
/spec-next --once          # pick top, run /spec-all once, stop
/spec-next --dry           # print ranked candidate list and chosen spec, do NOT execute
/spec-next --plan          # print the full ordered release command-sequence (all open tickets), do NOT execute
/spec-next --plan --flavors "<f1,f2>"   # same, threading flavor scope into the /skill-release line
/spec-next --reset-skips   # clear temp/spec-next-skip-cache.json before running
```

No positional arguments. Selection derived from `PLAN/spec-catalog.jsonl` plus `temp/spec-next-skip-cache.json` (auto-pruned, 7-day TTL).

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
- `BlockByOtherTask` - **conditional**: included by preflight, then auto-skipped (`reason: blocker-not-verified`) unless blocker named in §10 of spec file is currently `Verified`.

**Excluded** (always):

- `Verified`, `Archived` - terminal
- `BlockNeedUserTest` - waiting on operator's device run; not a candidate
- `BlockQuestions`, `BlockExternal` - waiting on human / external resource
- Any unknown / malformed `status` - skip and continue down ranked list

---

## Process

### Stage 1 - Preflight (rank + skip-cache + auto-skip + drift, one call)

```powershell
pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Exclude <processed-ids-csv>
```

One read-only call replaces previous `search.ps1` + manual rank + `skip-cache.ps1 -Action list` + per-candidate `preview.ps1` + `drift-check.ps1` chain. Returns single JSON blob:

- `ranked[]` - eligible set (statuses above), already sorted `priority` desc -> `updated` desc -> `id` asc, with active persistent skip-cache and `-Exclude` round-memory set already removed.
- `skip_cache` / `skip_cached_ids` - active persistent skips and which ranked ids they removed (informational; no action needed).
- `auto_skipped[]` - candidates preflight previewed and rejected while walking down to selection. Each `{ id, reason, detail }`, `reason ∈ { tier-5-epic | owner-gate | blocker-not-verified | research-heavy }`.
- `selected` - chosen ticket's full `preview.ps1` payload (`status`, `frontmatter`, `sections`, `tactical_folder`, `last_audit_present`, `timber_tags_kt`, `depends_on`) plus `drift` (`drift-check.ps1` verdict object) and `status_mismatch` (`{catalog,file}` or `null`). `null` when eligible set exhausted.

`-Exclude` carries in-memory `processed` round-memory set (Stage 5) so each loop iteration gets next candidate in one call. First iteration: omit `-Exclude`.

`selected == null` -> eligible set exhausted -> final report (Stage 6) and stop.

### Stage 2 - Persist preflight side effects (only mutations in selection)

Preflight is read-only by contract; this skill performs writes it implies:

1. **Persist auto-skips.** For each entry in `auto_skipped[]`, write to persistent cache and log one line `[auto-skip] <id> - <reason>`:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/skip-cache.ps1 -Action add -Id <id> -Reason "<reason>"
   ```
   These close deterministic skip cases (Tier 5 epic-containers, §12 owner-gate, unverified blocker chains, >=3 unresolved §6 research items) with no `AskUserQuestion`.
2. **Resolve status mismatch.** If `selected.status_mismatch` non-null, file is authoritative - sync catalog and log `Sync: <id> catalog <catalog> -> <file> (file authoritative)`:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <id> -Status <file-status>
   ```
   If synced status no longer eligible, add `<id>` to `processed` and re-run Stage 1 with updated `-Exclude`.

### Stage 3 - Drift gate

`selected.drift.verdict == DRIFT` = git commits carrying spec id marker AND/OR inline `// <id>:` markers exist in `app_v2/src/`. Fix is likely already (partly) in code and `/spec-all` would re-discover it expensively. Note in round verdict, then:

- `selected.last_audit_present` is true OR spec has "Implementation State" block -> proceed to Stage 4 (`/spec-all` resumes at right stage).
- Neither -> defer: skip-cache spec with `Reason "drift-needs-review"` (TTL 3 days), surface in final report under "Drift detected - needs manual review", add to `processed`, re-run Stage 1.

### Stage 4 - Delegate to `/spec-all` (with preflight handoff)

Hand chosen ticket id to `/spec-all`, passing preflight `selected` payload as already-resolved context so `/spec-all` does not re-resolve it:

```text
/spec-all <Sxxxx>
preflight: status=<status> tier=<tier> tactical_folder=<bool> last_audit=<bool> timber_tags_kt=<n> drift=<verdict> sections=<count>; depends_on=<id(status),..>
```

`/spec-all` trusts this context and skips its own opening `select.ps1` / catalog re-query for this ticket (its Resume Map keys off handed `status`). It does NOT re-run `preview.ps1` / `drift-check.ps1` for same ticket. All hard-stops, build gates, defer-first behaviour, and debug-tag lifecycle still come from `/spec-all` - do NOT reimplement here.

While `/spec-all` runs, do not start another spec. One spec per delegation.

### Stage 5 - Inspect outcome and loop

When `/spec-all` returns, re-read chosen spec's catalog row (single authoritative read):

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Record final status. Possible terminations for one round:

| New status | Round verdict | Loop action |
|------------|---------------|-------------|
| `Verified` | Closed ✅ | Continue loop |
| `Implemented` | Audit deferred / max iterations hit | Continue loop (add to `processed`; `/spec-all` already ran F5) |
| `Partial` / `Broken` | Audit incomplete | Continue loop (add to `processed`) |
| `BlockNeedUserTest` | Manual gate set (no device attached this round) | Continue loop |
| `BlockByOtherTask` | Blocked by new dependency | Continue loop |
| `BlockExternal` / `BlockQuestions` | Hard external block | Continue loop |
| `Archived` | Aborted as archived | Continue loop |
| Unchanged from start | `/spec-all` made no progress | Add to `processed`, continue loop |

**Round memory.** Maintain in-memory `processed` set of ticket ids touched during this `/spec-next` invocation. After Stage 5, add just-handled id. Pass whole set to next Stage 1 call via `-Exclude` - prevents infinite re-selection of a spec whose status `/spec-all` could not advance.

**`--once` mode.** Skip loop. After Stage 5 print final report and exit.

### Stage 6 - Final report

When Stage 1 returns `selected == null` (truly empty or all remaining excluded), print:

```text
spec-next: session complete

Processed this run:
  Sxxxx <slug> - <start-status> -> <end-status>   [✅ Verified | ⚠️ Partial | 🛑 Blocked | ⏱️ Incomplete]
  Syyyy <slug> - <start-status> -> <end-status>   [...]

Skipped (in eligibility filter but not advanced):
  Sxxxx <slug> - <status>: <reason>

Waiting on human (not picked):
  Sxxxx <slug> - BlockNeedUserTest
  Syyyy <slug> - BlockQuestions
  ...
```

If "Waiting on human" contains any `BlockNeedUserTest` entries, append one line: `Tip: attach a device and run /spec-sweep to drain the BlockNeedUserTest backlog.`

Run dev log once for session:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec-catalog.jsonl" "spec-next" "Session: <N> processed, <M> verified, <K> blocked"
```

---

## `--dry` mode

Run Stage 1 (preflight read-only) only. Skip Stages 2..6. Print:

```text
spec-next: dry run

Eligible candidates (ranked):
  Sxxxx <pri> <status> <updated> <slug>
  Syyyy <pri> <status> <updated> <slug>
  ...

Would auto-skip: Szzzz (<reason>), ...
Would run: /spec-all Sxxxx
```

Use `ranked[]`, `auto_skipped[]`, and `selected` straight from preflight JSON. Do NOT mutate anything (no `skip-cache.ps1 -Action add`, no `update.ps1`, no `add_to_dev_log.ps1`).

---

## `--plan` mode (release command-sequence)

Emit full ordered command-sequence driving **every** open ticket to releasable state, ending in release tail. Loop advances ONE top-priority spec per `/spec-all` delegation and never reaches release step; `--plan` instead enumerates **whole active catalog** - including every `Draft` and `Approved` present - into a phased, dependency-ordered, copy-pasteable command block. Read-only: prints, executes nothing, mutates nothing.

One call does whole job - generator owns ranking, status→command map, dependency ordering, phase grouping:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/release-plan.ps1
# -Format json            # structured plan for programmatic use
# -Flavors "standard,vr"  # thread flavor scope into the trailing /skill-release line
```

Thread any flavor argument operator passed (`/spec-next --plan --flavors "..."`) into `-Flavors`. Present script's text output **verbatim** - it is the deterministic artifact. Do NOT re-derive, re-sort, or hand-edit sequence; do NOT drop heavy `Draft`/`Approved` items (point of `--plan` is full coverage - generator annotates epics/owner-gates instead of silently deferring them).

Phases generator produces (status → command map is fixed):

- **A - Implementation** (`Draft`→`/spec-all`; `Approved`→`/spec-tech`+`/spec-dev`; `Tactical`/`In Progress`→`/spec-dev`; `Partial`/`Broken`→`/spec-fix`+`/spec-check`) for specs with no in-plan prerequisite. Priority-ordered. Trailing command of each pair is prior skill's own auto-chain (`/spec-tech`→`/spec-dev`, `/spec-fix`→`/spec-check`), kept explicit so sequence stays complete on PRIMITIVE / blocked / `--dry-run` branches where chain does not fire.
- **B - Dependency chains** - any `BlockByOtherTask` spec, plus any impl spec whose `statusNote` names an in-plan blocker. Ordered after blocker (annotated `(after Sxxxx)`) in same global topological order as Phase A, so a blocker always precedes its dependent across A/B boundary. `BlockByOtherTask` rows emit `unblock first` comment (`update.ps1 -Id <id> -Status <pre-block>`): `/spec-tech` and `/spec-dev` both hard-abort while status is `Block*`, so restore must run before listed commands.
- **C - Verification** (`BlockNeedUserTest` collapsed into ONE `/spec-sweep`; each `Implemented`→`/spec-test-device`+`/spec-check` - device step needs device online, else skip to static `/spec-check`).
- **D - Release** (`/spec-prerelease` → `/skill-release`). Run from a `DEBUG-v00N` branch.
- **Deferred** (`BlockExternal`/`BlockQuestions`) - listed as comments, no command: cannot be driven from catalog (external / human gate).

Skip Stages 1..6 entirely in this mode - no preflight, no skip-cache, no loop, no dev-log. Only action is running generator and presenting its block. If operator then wants to *execute* plan, they run listed commands (or `/spec-next` to auto-drive loop-eligible subset of Phase A/B).

---

## Hard rules

- **Never edit `PLAN/spec-catalog.jsonl` directly** - only via `update.ps1`, `select.ps1`, `search.ps1`, `spec-next-preflight.ps1` (read-only).
- **Do not duplicate `/spec-all` logic** - every progress decision delegates to it. This skill's responsibility is *selection*, not *execution*.
- **No user prompts in loop mode.** A stage detecting unresolvable ambiguity -> skip spec via round memory + persistent skip-cache, continue loop. Final report names all skipped specs. `AskUserQuestion` MUST NOT be invoked from any stage of `/spec-next` - preflight's auto-skip predicates replace every previous owner-gate / tier-5 / VR-child / research-heavy prompt.
- **Spec status sync is one-way per run.** If Stage 2 syncs catalog from file, do not later flip it back from catalog side mid-run.
- **No spec file rewrites here.** Sync touches journal, not `.md`. If `.md` malformed (preflight returns it under `malformed`), skip spec and list under "Skipped" in final report.
- **Round memory is session-scoped.** Resets on every fresh `/spec-next` invocation. Crashes / interruptions do not persist it.
- **Branch awareness.** Do not switch git branches. User controls active branch; `/spec-next` runs on whatever branch is checked out.

---

## Spec Catalog hooks

- **Reads:** `spec-next-preflight.ps1` (single Stage 1 selection call: rank + skip-cache consume + per-candidate preview + drift, read-only), `select.ps1` (post-`/spec-all` status check in Stage 5), `release-plan.ps1` (single `--plan` call: whole-catalog phased release sequence, read-only).
- **Writes:** `skip-cache.ps1 -Action add` for each `auto_skipped[]` entry and on `drift-needs-review`; `update.ps1 -Status` only when preflight reports `status_mismatch`; `skip-cache.ps1 -Action reset` on `--reset-skips`.
- **Indirect writes:** all status transitions during execution come from `/spec-all` and its sub-skills (`/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-fix`). This skill never sets `Implemented`, `Verified`, `Partial`, `Broken`, or any `Block*` directly.
- **Forbidden:** writing to `PLAN/spec-catalog.jsonl` directly; writing to `temp/spec-next-skip-cache.json` directly (use `skip-cache.ps1`); renaming spec files; creating audit / fix files in `PLAN/`.

---

## Examples

```text
# Full session
/spec-next
# -> preflight selects S0142 (pri 90, In Progress), runs /spec-all S0142 -> Verified
# -> preflight (-Exclude S0142) selects S0156 (pri 85, Tactical) -> BlockNeedUserTest
# -> preflight (-Exclude S0142,S0156) selects S0200 (pri 80, Draft) -> Implemented
# -> preflight returns selected=null; final report.

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
