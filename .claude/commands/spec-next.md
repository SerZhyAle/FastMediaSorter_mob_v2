# Next-Spec Picker - Auto-Drive Top Priority

Pick the highest-priority eligible specification from the catalog, hand it to `/spec-all`, then loop until no eligible spec remains. Zero-input automation for "just keep working on whatever is most important next."

## Usage

```text
/spec-next                 # loop: pick top-priority eligible, run /spec-all, repeat until none left
/spec-next --once          # pick top, run /spec-all once, stop
/spec-next --dry           # print the ranked candidate list and the chosen spec, do NOT execute
/spec-next --reset-skips   # clear temp/spec-next-skip-cache.json before running
```

No positional arguments. Selection is fully derived from `PLAN/spec-catalog.jsonl` plus `temp/spec-next-skip-cache.json` (auto-pruned, 7-day TTL).

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
- `BlockByOtherTask` - **conditional**: include only if the blocker (named in §10 of the spec file) is currently `Verified`. If §10 missing or blocker id unresolvable → skip.

**Excluded** (always):

- `Verified`, `Archived` - terminal
- `BlockNeedUserTest` - waiting on the operator's device run; not a candidate
- `BlockQuestions`, `BlockExternal` - waiting on a human / external resource
- Any unknown / malformed `status` value - skip and continue down the ranked list

---

## Process

### Stage 1 - Query the catalog

```powershell
pwsh -File scripts/spec_catalog/search.ps1 -Format json
```

Parse the JSON. For each record:

1. Apply eligibility filter above.
2. For `BlockByOtherTask`: blocker resolution is now performed by `preview.ps1` in Stage 3, so this stage does not pre-filter by blocker. Pass `BlockByOtherTask` records through to Stage 2.

### Stage 2 - Rank, apply persistent skip-cache

Sort the eligible set by:

1. `priority` **descending** (100 → 0)
2. `updated` **descending** (newest first) - tiebreak
3. `id` ascending - final tiebreak

**Persistent skip-cache.** Before picking the top candidate, load `temp/spec-next-skip-cache.json`:

```powershell
pwsh -File scripts/spec_catalog/skip-cache.ps1 -Action list
```

Each entry has shape `{ "Sxxxx": { "reason": "...", "expires": "<iso>" } }`. Entries with `expires` in the past are auto-pruned by the script on every call. **Treat every active entry as already in `processed`** for the current session - exclude from the ranked list, log one line `[skip-cache] Sxxxx - <reason>` for transparency. TTL is 7 days by default; a manual `/spec-next --reset-skips` clears the cache.

The top of the ranked list (minus session `processed` and skip-cache) is **the chosen spec**.

If the eligible set is empty → final report (see Stage 6) and stop.

### Stage 3 - One-shot preview (status sync + auto-skip + drift detection)

Replace the previous 4-command bash boilerplate (head/grep/ls/grep) with a single `preview.ps1` call:

```powershell
pwsh -File scripts/spec_catalog/preview.ps1 -Id <Sxxxx> -Format json
```

The JSON gives:

- `status`, `frontmatter.Status` - for file/catalog sync
- `tactical_folder`, `last_audit_present`, `timber_tags_kt` - for resume routing
- `depends_on[]` - pre-resolved blocker statuses (replaces manual §10 walk for `BlockByOtherTask`)
- `auto_skip` ∈ { `tier-5-epic` | `owner-gate` | `blocker-not-verified` | `research-heavy` | `null` }

**3a - Auto-skip predicates (no user prompt).** If `auto_skip` is non-null → record the skip in the persistent cache and drop the candidate:

```powershell
pwsh -File scripts/spec_catalog/skip-cache.ps1 -Action add -Id <Sxxxx> -Reason "<auto_skip>"
```

Return to Stage 2 with the next ranked candidate. These predicates close the deterministic skip cases (Tier 5 epic-containers, explicit §12 owner-gate, unverified blocker chains, ≥3 unresolved §6 research items on Draft/Approved specs) without spending a turn on `AskUserQuestion`.

**3b - File/catalog sync check.** If `record.status` (catalog) differs from `frontmatter.Status` (file):

- Default: trust the file. Sync catalog:
  ```powershell
  pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status <file-status>
  ```
- Log a one-line note in chat: `Sync: <Sxxxx> catalog <old> → <file-status> (file authoritative).`
- If the new status is no longer eligible → drop this candidate, return to Stage 2 (do NOT re-query the catalog).

**3c - Drift detection (`Sxxxx` already in code).** For specs in `Draft`, `Approved`, `Tactical`, or `Broken` status:

```powershell
pwsh -File scripts/spec_catalog/drift-check.ps1 -Id <Sxxxx>
```

Exit code 1 (`DRIFT`) means git commits with the spec id marker exist AND/OR inline `// Sxxxx:` markers are present in `app_v2/src/`. In that case the spec is likely already (partially) implemented and `/spec-all` would re-discover this expensively. Mark as **drift candidate**: insert a one-line note in the round verdict, then either:

- If the spec already has §10 "Implementation State" or a `## Last Audit` block - proceed to Stage 4 (delegate to `/spec-all`, which will resume at the right stage).
- If neither block exists - defer: skip-cache the spec with `Reason "drift-needs-review"` (TTL 3 days), surface in Stage 6 final report under "Drift detected - needs manual review".

### Stage 4 - Delegate to `/spec-all`

Hand the chosen ticket id to `/spec-all`:

```text
/spec-all <Sxxxx>
```

`/spec-all`'s Resume Map already selects the correct stage for every status (`Draft` → F1, `Approved` → F2, `Tactical` → F3, `In Progress` → F3 resume, `Implemented` → F5, `Partial`/`Broken` → F5 fix loop, `BlockByOtherTask` with Verified blocker → continues from last stage). All hard-stops, build gates, defer-first behaviour, and debug-tag lifecycle handling come from `/spec-all` - do NOT reimplement them here.

While `/spec-all` runs, do not start another spec. One spec per delegation.

### Stage 5 - Inspect outcome and loop

When `/spec-all` returns, re-read the chosen spec's catalog row:

```powershell
pwsh -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Record the final status. Possible terminations for one round:

| New status | Round verdict | Loop action |
|------------|---------------|-------------|
| `Verified` | Closed ✅ | Continue loop |
| `Implemented` | Audit deferred / max iterations hit | Continue loop (won't re-pick - Stage 1 includes `Implemented`, but `/spec-all` already ran F5 on it; if priority still tops the list, the audit was likely capped - skip via "round memory" below) |
| `Partial` / `Broken` | Audit incomplete | Continue loop (same skip rule as `Implemented`) |
| `BlockNeedUserTest` | Manual gate set | Continue loop |
| `BlockByOtherTask` | Blocked by new dependency | Continue loop |
| `BlockExternal` / `BlockQuestions` | Hard external block | Continue loop |
| `Archived` | Aborted as archived | Continue loop |
| Unchanged from start | `/spec-all` made no progress | Skip via round memory, continue loop |

**Round memory.** Maintain an in-memory `processed` set of ticket ids touched during this `/spec-next` invocation. After Stage 5, add the just-handled id to `processed`. In the next loop iteration's Stage 2, exclude any id in `processed` from the eligible set - this prevents infinite re-selection of the same spec whose status `/spec-all` could not advance.

**`--once` mode.** Skip the loop. After Stage 5 print the final report and exit.

### Stage 6 - Final report

When Stage 2 has no eligible candidates left (either truly empty or all remaining are in `processed`), print:

```text
spec-next: session complete

Processed this run:
  Sxxxx <slug> - <start-status> → <end-status>   [✅ Verified | ⚠️ Partial | 🛑 Blocked | ⏱️ Incomplete]
  Syyyy <slug> - <start-status> → <end-status>   [...]

Skipped (in eligibility filter but not advanced):
  Sxxxx <slug> - <status>: <reason>

Waiting on human (not picked):
  Sxxxx <slug> - BlockNeedUserTest
  Syyyy <slug> - BlockQuestions
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

- **Never edit `PLAN/spec-catalog.jsonl` directly** - only via `update.ps1`, `select.ps1`, `search.ps1`.
- **Do not duplicate `/spec-all` logic** - every progress decision delegates to it. This skill's responsibility is *selection*, not *execution*.
- **No user prompts in loop mode.** If a stage detects ambiguity it cannot resolve, the spec is skipped via round memory + persistent skip-cache and the loop continues. The final report names all skipped specs. `AskUserQuestion` MUST NOT be invoked from any stage of `/spec-next` - auto-skip predicates (Stage 3a) replace every previous owner-gate / tier-5 / VR-child / research-heavy prompt.
- **Spec status sync is one-way per run.** If Stage 3 syncs catalog from file, do not later flip it back from the catalog side mid-run.
- **No spec file rewrites here.** Sync touches the journal, not the `.md`. If the `.md` itself is malformed (no `Status:` line, missing §10 for `BlockByOtherTask`), skip the spec and list it under "Skipped" in the final report with the parse error.
- **Round memory is session-scoped.** It resets on every fresh `/spec-next` invocation. Crashes / interruptions do not persist it.
- **Branch awareness.** Do not switch git branches. The user controls the active branch; `/spec-next` runs on whatever branch is checked out.

---

## Spec Catalog hooks

- **Reads:** `search.ps1` (rank source), `preview.ps1` (Stage 3 combined sync + auto-skip + blocker resolution), `drift-check.ps1` (Stage 3c code-vs-spec drift), `skip-cache.ps1 -Action list` (persistent skip), `select.ps1` (post-`/spec-all` status check).
- **Writes:** `update.ps1 -Status` only when Stage 3b detects a file/catalog mismatch. `skip-cache.ps1 -Action add` on every auto-skip and on `drift-needs-review`. `skip-cache.ps1 -Action reset` on `--reset-skips`.
- **Indirect writes:** all status transitions during execution come from `/spec-all` and its sub-skills (`/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-fix`). This skill never sets `Implemented`, `Verified`, `Partial`, `Broken`, or any `Block*` directly.
- **Forbidden:** writing to `PLAN/spec-catalog.jsonl` directly; writing to `temp/spec-next-skip-cache.json` directly (use `skip-cache.ps1`); renaming spec files; creating audit / fix files in `PLAN/`.

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
