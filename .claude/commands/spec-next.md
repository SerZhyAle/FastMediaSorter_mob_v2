# Next-Spec Picker — Auto-Drive Top Priority

Pick the highest-priority eligible specification from the catalog, hand it to `/spec-all`, then loop until no eligible spec remains. Zero-input automation for "just keep working on whatever is most important next."

## Usage

```text
/spec-next            # loop: pick top-priority eligible, run /spec-all, repeat until none left
/spec-next --once     # pick top, run /spec-all once, stop
/spec-next --dry      # print the ranked candidate list and the chosen spec, do NOT execute
```

No positional arguments. Selection is fully derived from `PLAN/spec-catalog.jsonl`.

---

## Eligibility

A spec is **eligible** if its catalog `status` is one of:

- `Draft`
- `Approved`
- `Tactical`
- `In Progress`
- `Implemented`
- `Partial`
- `Broken`
- `BlockByOtherTask` — **conditional**: include only if the blocker (named in §10 of the spec file) is currently `Verified`. If §10 missing or blocker id unresolvable → skip.

**Excluded** (always):

- `Verified`, `Archived` — terminal
- `BlockNeedUserTest` — waiting on the operator's device run; not a candidate
- `BlockQuestions`, `BlockExternal` — waiting on a human / external resource
- Any unknown / malformed `status` value — skip and continue down the ranked list

---

## Process

### Stage 1 — Query the catalog

```powershell
pwsh -File scripts/spec_catalog/search.ps1 -Format json
```

Parse the JSON. For each record:

1. Apply eligibility filter above.
2. For `BlockByOtherTask`: read the spec file at `record.file`, find §10 / "Blocked by" / "Blocking ticket" section. Extract the blocker `S\d{4}`. Resolve it: `pwsh -File scripts/spec_catalog/select.ps1 -Id <blocker> -Format json`. Include the candidate only when blocker `status == "Verified"`. On any resolution failure (no §10, multiple blockers with mixed status, blocker missing from catalog) → skip the candidate.

### Stage 2 — Rank

Sort the eligible set by:

1. `priority` **descending** (100 → 0)
2. `updated` **descending** (newest first) — tiebreak
3. `id` ascending — final tiebreak

The top of the sorted list is **the chosen spec**.

If the eligible set is empty → final report (see Stage 6) and stop.

### Stage 3 — File/catalog sync check

Before delegating, reconcile catalog status with the spec file's `Status:` header:

1. Read the chosen spec file (`record.file`). Locate the line `**Status:** <value>` (or `Status: <value>`) near the top.
2. Compare with `record.status`.
3. If they match → continue to Stage 4.
4. If they differ:
   - The **file** is authoritative when the discrepancy is structural (file shows `Implemented` but catalog still `Tactical` — implementation completed without the catalog hook firing).
   - The **catalog** is authoritative when the file shows a regression that contradicts journal history (rare; only when the file was hand-edited backward).
   - Default: trust the file. Sync catalog:
     ```powershell
     pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status <file-status>
     ```
   - Log a one-line note in chat: `Sync: <Sxxxx> catalog <old> → <file-status> (file authoritative).`
5. If the new status is no longer eligible (e.g. file already shows `Verified`) → drop this candidate, return to Stage 2 with the rest of the ranked list (do NOT re-query the catalog — use the in-memory ranking minus the just-synced entry).

### Stage 4 — Delegate to `/spec-all`

Hand the chosen ticket id to `/spec-all`:

```text
/spec-all <Sxxxx>
```

`/spec-all`'s Resume Map already selects the correct stage for every status (`Draft` → F1, `Approved` → F2, `Tactical` → F3, `In Progress` → F3 resume, `Implemented` → F5, `Partial`/`Broken` → F5 fix loop, `BlockByOtherTask` with Verified blocker → continues from last stage). All hard-stops, build gates, defer-first behaviour, and debug-tag lifecycle handling come from `/spec-all` — do NOT reimplement them here.

While `/spec-all` runs, do not start another spec. One spec per delegation.

### Stage 5 — Inspect outcome and loop

When `/spec-all` returns, re-read the chosen spec's catalog row:

```powershell
pwsh -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Record the final status. Possible terminations for one round:

| New status | Round verdict | Loop action |
|------------|---------------|-------------|
| `Verified` | Closed ✅ | Continue loop |
| `Implemented` | Audit deferred / max iterations hit | Continue loop (won't re-pick — Stage 1 includes `Implemented`, but `/spec-all` already ran F5 on it; if priority still tops the list, the audit was likely capped — skip via "round memory" below) |
| `Partial` / `Broken` | Audit incomplete | Continue loop (same skip rule as `Implemented`) |
| `BlockNeedUserTest` | Manual gate set | Continue loop |
| `BlockByOtherTask` | Blocked by new dependency | Continue loop |
| `BlockExternal` / `BlockQuestions` | Hard external block | Continue loop |
| `Archived` | Aborted as archived | Continue loop |
| Unchanged from start | `/spec-all` made no progress | Skip via round memory, continue loop |

**Round memory.** Maintain an in-memory `processed` set of ticket ids touched during this `/spec-next` invocation. After Stage 5, add the just-handled id to `processed`. In the next loop iteration's Stage 2, exclude any id in `processed` from the eligible set — this prevents infinite re-selection of the same spec whose status `/spec-all` could not advance.

**`--once` mode.** Skip the loop. After Stage 5 print the final report and exit.

### Stage 6 — Final report

When Stage 2 has no eligible candidates left (either truly empty or all remaining are in `processed`), print:

```text
spec-next: session complete

Processed this run:
  Sxxxx <slug> — <start-status> → <end-status>   [✅ Verified | ⚠️ Partial | 🛑 Blocked | ⏱️ Incomplete]
  Syyyy <slug> — <start-status> → <end-status>   [...]

Skipped (in eligibility filter but not advanced):
  Sxxxx <slug> — <status>: <reason>

Waiting on human (not picked):
  Sxxxx <slug> — BlockNeedUserTest
  Syyyy <slug> — BlockQuestions
  ...
```

Run dev log once for the session:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec-catalog.jsonl" "spec-next" "Session: <N> processed, <M> verified, <K> blocked"
```

---

## `--dry` mode

Skip Stages 3..6. After Stage 2, print:

```text
spec-next: dry run

Eligible candidates (ranked):
  Sxxxx <pri> <status> <updated> <slug>
  Syyyy <pri> <status> <updated> <slug>
  ...

Would run: /spec-all Sxxxx
```

Do NOT mutate anything (no `update.ps1`, no `add_to_dev_log.ps1`).

---

## Hard rules

- **Never edit `PLAN/spec-catalog.jsonl` directly** — only via `update.ps1`, `select.ps1`, `search.ps1`.
- **Do not duplicate `/spec-all` logic** — every progress decision delegates to it. This skill's responsibility is *selection*, not *execution*.
- **No user prompts in loop mode.** If a stage detects ambiguity it cannot resolve, the spec is skipped via round memory and the loop continues. The final report names all skipped specs.
- **Spec status sync is one-way per run.** If Stage 3 syncs catalog from file, do not later flip it back from the catalog side mid-run.
- **No spec file rewrites here.** Sync touches the journal, not the `.md`. If the `.md` itself is malformed (no `Status:` line, missing §10 for `BlockByOtherTask`), skip the spec and list it under "Skipped" in the final report with the parse error.
- **Round memory is session-scoped.** It resets on every fresh `/spec-next` invocation. Crashes / interruptions do not persist it.
- **Branch awareness.** Do not switch git branches. The user controls the active branch; `/spec-next` runs on whatever branch is checked out.

---

## Spec Catalog hooks

- **Reads:** `search.ps1` (rank source), `select.ps1` (blocker resolution, post-`/spec-all` status check).
- **Writes:** `update.ps1 -Status` only when Stage 3 detects a file/catalog mismatch.
- **Indirect writes:** all status transitions during execution come from `/spec-all` and its sub-skills (`/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-fix`). This skill never sets `Implemented`, `Verified`, `Partial`, `Broken`, or any `Block*` directly.
- **Forbidden:** writing to `PLAN/spec-catalog.jsonl` directly; renaming spec files; creating audit / fix files in `PLAN/`.

---

## Examples

```text
# Full session
/spec-next
# → picks S0142 (pri 90, In Progress), runs /spec-all S0142 → Verified
# → picks S0156 (pri 85, Tactical), runs /spec-all S0156 → BlockNeedUserTest
# → picks S0200 (pri 80, Draft), runs /spec-all S0200 → Implemented (audit deferred)
# → no more eligible; final report.

# One round only
/spec-next --once
# → picks top eligible, delegates once, stops with report.

# Preview without execution
/spec-next --dry
# → prints ranked list + chosen, no mutations.
```
