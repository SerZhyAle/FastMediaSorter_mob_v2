# Next-Spec Picker - Auto-Drive Top Priority

Pick the highest-priority eligible spec from the catalog, hand it to `/spec-all`, then loop until no eligible spec remains. Zero-input automation for "just keep working on whatever is most important next."

## Usage

```text
/spec-next                 # loop: pick top-priority eligible, run /spec-all, repeat until none left
/spec-next --once          # pick top, run /spec-all once, stop
/spec-next --dry           # print ranked candidate list and chosen spec, do NOT execute
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
- `BlockByOtherTask` - **conditional**: included by the preflight, then auto-skipped (`reason: blocker-not-verified`) unless the blocker named in §10 of the spec file is currently `Verified`.

**Excluded** (always):

- `Verified`, `Archived` - terminal
- `BlockNeedUserTest` - waiting on operator's device run; not a candidate
- `BlockQuestions`, `BlockExternal` - waiting on human / external resource
- Any unknown / malformed `status` - skip and continue down the ranked list

---

## Process

### Stage 1 - Preflight (rank + skip-cache + auto-skip + drift, one call)

```powershell
pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Exclude <processed-ids-csv>
```

One read-only call replaces the previous `search.ps1` + manual rank + `skip-cache.ps1 -Action list` + per-candidate `preview.ps1` + `drift-check.ps1` chain. It returns a single JSON blob:

- `ranked[]` - eligible set (the statuses above), already sorted `priority` desc -> `updated` desc -> `id` asc, with the active persistent skip-cache and the `-Exclude` round-memory set already removed.
- `skip_cache` / `skip_cached_ids` - active persistent skips and which ranked ids they removed (informational; no action needed).
- `auto_skipped[]` - candidates the preflight previewed and rejected while walking down to the selection. Each `{ id, reason, detail }`, `reason ∈ { tier-5-epic | owner-gate | blocker-not-verified | research-heavy }`.
- `selected` - the chosen ticket's full `preview.ps1` payload (`status`, `frontmatter`, `sections`, `tactical_folder`, `last_audit_present`, `timber_tags_kt`, `depends_on`) plus `drift` (the `drift-check.ps1` verdict object) and `status_mismatch` (`{catalog,file}` or `null`). `null` when the eligible set is exhausted.

`-Exclude` carries the in-memory `processed` round-memory set (Stage 5) so each loop iteration gets the next candidate in one call. First iteration: omit `-Exclude`.

`selected == null` -> eligible set exhausted -> final report (Stage 6) and stop.

### Stage 2 - Persist preflight side effects (the only mutations in selection)

The preflight is read-only by contract; this skill performs the writes it implies:

1. **Persist auto-skips.** For each entry in `auto_skipped[]`, write it to the persistent cache and log one line `[auto-skip] <id> - <reason>`:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/skip-cache.ps1 -Action add -Id <id> -Reason "<reason>"
   ```
   These close the deterministic skip cases (Tier 5 epic-containers, §12 owner-gate, unverified blocker chains, >=3 unresolved §6 research items) with no `AskUserQuestion`.
2. **Resolve status mismatch.** If `selected.status_mismatch` is non-null, the file is authoritative - sync the catalog and log `Sync: <id> catalog <catalog> -> <file> (file authoritative)`:
   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <id> -Status <file-status>
   ```
   If the synced status is no longer eligible, add `<id>` to `processed` and re-run Stage 1 with the updated `-Exclude`.

### Stage 3 - Drift gate

`selected.drift.verdict == DRIFT` = git commits carrying the spec id marker AND/OR inline `// <id>:` markers exist in `app_v2/src/`. The fix is likely already (partly) in code and `/spec-all` would re-discover it expensively. Note it in the round verdict, then:

- `selected.last_audit_present` is true OR the spec has an "Implementation State" block -> proceed to Stage 4 (`/spec-all` resumes at the right stage).
- Neither -> defer: skip-cache the spec with `Reason "drift-needs-review"` (TTL 3 days), surface in the final report under "Drift detected - needs manual review", add to `processed`, re-run Stage 1.

### Stage 4 - Delegate to `/spec-all` (with preflight handoff)

Hand the chosen ticket id to `/spec-all`, passing the preflight `selected` payload as already-resolved context so `/spec-all` does not re-resolve it:

```text
/spec-all <Sxxxx>
preflight: status=<status> tier=<tier> tactical_folder=<bool> last_audit=<bool> timber_tags_kt=<n> drift=<verdict> sections=<count>; depends_on=<id(status),..>
```

`/spec-all` trusts this context and skips its own opening `select.ps1` / catalog re-query for this ticket (its Resume Map keys off the handed `status`). It does NOT re-run `preview.ps1` / `drift-check.ps1` for the same ticket. All hard-stops, build gates, defer-first behaviour, and debug-tag lifecycle still come from `/spec-all` - do NOT reimplement them here.

While `/spec-all` runs, do not start another spec. One spec per delegation.

### Stage 5 - Inspect outcome and loop

When `/spec-all` returns, re-read the chosen spec's catalog row (single authoritative read):

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Record the final status. Possible terminations for one round:

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

**Round memory.** Maintain an in-memory `processed` set of ticket ids touched during this `/spec-next` invocation. After Stage 5, add the just-handled id. Pass the whole set to the next Stage 1 call via `-Exclude` - prevents infinite re-selection of a spec whose status `/spec-all` could not advance.

**`--once` mode.** Skip the loop. After Stage 5 print the final report and exit.

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

Run dev log once for the session:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec-catalog.jsonl" "spec-next" "Session: <N> processed, <M> verified, <K> blocked"
```

---

## `--dry` mode

Run Stage 1 (preflight is read-only) only. Skip Stages 2..6. Print:

```text
spec-next: dry run

Eligible candidates (ranked):
  Sxxxx <pri> <status> <updated> <slug>
  Syyyy <pri> <status> <updated> <slug>
  ...

Would auto-skip: Szzzz (<reason>), ...
Would run: /spec-all Sxxxx
```

Use `ranked[]`, `auto_skipped[]`, and `selected` straight from the preflight JSON. Do NOT mutate anything (no `skip-cache.ps1 -Action add`, no `update.ps1`, no `add_to_dev_log.ps1`).

---

## Hard rules

- **Never edit `PLAN/spec-catalog.jsonl` directly** - only via `update.ps1`, `select.ps1`, `search.ps1`, `spec-next-preflight.ps1` (read-only).
- **Do not duplicate `/spec-all` logic** - every progress decision delegates to it. This skill's responsibility is *selection*, not *execution*.
- **No user prompts in loop mode.** A stage detecting unresolvable ambiguity -> skip the spec via round memory + persistent skip-cache, continue the loop. Final report names all skipped specs. `AskUserQuestion` MUST NOT be invoked from any stage of `/spec-next` - the preflight's auto-skip predicates replace every previous owner-gate / tier-5 / VR-child / research-heavy prompt.
- **Spec status sync is one-way per run.** If Stage 2 syncs catalog from file, do not later flip it back from the catalog side mid-run.
- **No spec file rewrites here.** Sync touches the journal, not the `.md`. If the `.md` is malformed (preflight returns it under `malformed`), skip the spec and list it under "Skipped" in the final report.
- **Round memory is session-scoped.** Resets on every fresh `/spec-next` invocation. Crashes / interruptions do not persist it.
- **Branch awareness.** Do not switch git branches. The user controls the active branch; `/spec-next` runs on whatever branch is checked out.

---

## Spec Catalog hooks

- **Reads:** `spec-next-preflight.ps1` (the single Stage 1 selection call: rank + skip-cache consume + per-candidate preview + drift, read-only), `select.ps1` (post-`/spec-all` status check in Stage 5).
- **Writes:** `skip-cache.ps1 -Action add` for each `auto_skipped[]` entry and on `drift-needs-review`; `update.ps1 -Status` only when the preflight reports `status_mismatch`; `skip-cache.ps1 -Action reset` on `--reset-skips`.
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
```
