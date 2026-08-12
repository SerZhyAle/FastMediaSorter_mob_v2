---
description: "Use to unstick one spec via multiple-choice Q&A - ask the owner only the decisions blocking the next transition, write answers in, advance one lifecycle level. Triggers: 'spec-quiz Sxxxx', 'this spec is stuck', 'decide the open questions'."
---

# Spec Quiz - Unstick a Spec via Multiple-Choice Q&A

Ask owner a focused set of multiple-choice questions about ONE stuck spec, write answers into the spec file, then advance it exactly one lifecycle level. Quiz elicits only decisions genuinely the owner's to make and currently blocking the next transition - nothing the architecture, codebase, or prior research already answers.

Interactive counterpart to `/spec` (auto-fills owner inputs by authoring) and `/spec-next` (auto-skips anything needing a human). Use `/spec-quiz` when a spec is parked precisely *because* it needs owner's call, and owner ready to give it.

## Usage

```text
/spec-quiz <Sxxxx>            # quiz the spec, advance one level, stop
/spec-quiz <Sxxxx> --chain    # after advancing, hand off to /spec-all to keep going
/spec-quiz <Sxxxx> --dry      # show questions that WOULD be asked, ask nothing, mutate nothing
```

Exactly one ticket id. No batch mode - a quiz is a focused human conversation about a single spec.

---

## What each status unlocks

Current status determines what blocks advancement, hence what quiz asks and where it advances to:

| Current status | What blocks advancement | Quiz asks about | Advances to |
|----------------|-------------------------|-----------------|-------------|
| `Draft` | §3.3 Owner inputs unfilled, §6 research `Open` | scope, priority, UX/flavor decisions, open research forks | `Approved` |
| `BlockQuestions` | clarifications captured in `**Status note:**` / §0 / §6 `Open` | exactly those captured questions | restore to inferred pre-block status |
| `BlockByOtherTask` | a dependency ticket | confirm dependency resolved; pick fallback if not | restore (if cleared) |
| `Approved` *(with `--here`)* | nothing hard; ready for `/spec-tech` | residual scope/granularity forks | `Tactical` via `/spec-tech` |
| `Tactical` / `In Progress` *(with `--here`)* | design forks left to owner | implementation forks | unchanged (decisions recorded) |

**Not quiz-resolvable** - report and redirect, mutate nothing:

- `BlockNeedUserTest` - needs a device run, not a conversation. Redirect to `/spec-test-device` or `/spec-sweep`.
- `BlockExternal` - waiting on external resource a quiz cannot supply. Report blocker from status note.
- `Implemented` - code exists; needs audit, not decisions. Redirect to `/spec-check`.
- `Verified`, `Archived` - terminal. Report and stop.

`--here` opts into quizzing a non-blocked spec (`Approved` / `Tactical` / `In Progress`) for residual design forks. Without it, those statuses reported as "not stuck" and skill stops.

---

## Process

### Stage 0 - Resolve (never infer status from filename)

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Read resolved `status` and `file`. Read the spec file. `status` not quiz-resolvable (table above) → print redirect and stop. Catalog row is authoritative; if file header disagrees, trust catalog and note mismatch.

### Stage 1 - Extract decision points

Read spec and pull concrete items blocking next transition:

- **`Draft`** - every §3.3 bullet whose value is unfilled bracketed placeholder or empty; every §6 research item with `Status: Open`; any §3/§4 scope ambiguity author flagged for owner.
- **`BlockQuestions`** - questions verbatim from `**Status note:**`, plus any §6 `Open` items and §0 captured text the block refers to.
- **`BlockByOtherTask`** - dependency id carried as a `Blocker: Sxxxx` token in §10 or the status note; a bare §10 mention is a relation, not a dependency (S1482).

For each candidate decision point, **research a recommended answer first** (codebase via `dev/CATALOG/scripts/query.ps1`, docs, existing tickets via `search.ps1`). Two outcomes:

- Architecture / codebase / prior decision already determines it → **do not ask**. Fill silently and list under "Resolved without asking" in report. (CLAUDE.md: never ask owner questions architecture already answers.)
- Genuine owner call (preference, scope boundary, priority, UX wording, flavor reach, build-vs-buy) → turn into quiz question with researched recommended default.

After this filter, zero questions remain → nothing here is owner's to decide. Report that, attempt advance anyway if only blocker was the (now silently filled) inputs, otherwise stop without mutating.

### Stage 2 - Ask (AskUserQuestion)

Present questions with `AskUserQuestion`:

- Each question: ≤12-char `header`, specific question ending in `?`, 2-4 options.
- Put researched recommendation **first** with `(Recommended)` appended to label; give every option a one-line `description` of its consequence.
- Use `multiSelect: true` only when choices genuinely combinable (e.g. which flavors a feature ships to).
- Max 4 questions per call - if more decision points remain, ask in successive batches, most-blocking first. Do not exceed what is needed to unblock.
- Use `preview` only for concrete artifacts worth comparing side by side (string wording variants, layout sketches, API-shape snippets).

`--dry`: print composed questions and their options, then stop. No `AskUserQuestion`, no writeback, no status change.

### Stage 3 - Write answers into the spec

Edit spec file directly (it is a `.md`, plain `Edit` is fine):

- Fill each answered §3.3 bullet with chosen concrete value.
- Flip each resolved §6 item `Status: Open` → `Status: Resolved` and append decision as one-line note on that item.
- Append a dated decisions block recording each Q and chosen answer, so rationale survives:

```markdown
### Quiz decisions (<YYYY-MM-DD>)
- <question> → <chosen option> (<one-line rationale>)
```

Apply same answers to §3/§4 prose where they resolve a flagged ambiguity. Keep edits surgical - do not rewrite unrelated sections.

### Stage 4 - Advance one level

Per starting status:

- **`Draft` → `Approved`**: run gate, then flip.
  ```powershell
  pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id <Sxxxx>
  ```
  - Exit 0 → apply Draft→Approved hygiene to touched text (`..` not `...`, `ё`/`Ё`, lists over tables) and flip: `update.ps1 -Id <Sxxxx> -Status Approved` (rewrites file's first `**Status:**` line automatically).
  - Exit 1 → gate still reports blockers (e.g. a §3.3 bullet a question did not cover). List them, ask missing question(s) (back to Stage 2, one batch), retry gate once. Still failing → leave `Draft`, report residual blockers, stop.
- **`BlockQuestions` → restore**: infer pre-block status from artifacts - tactical folder + checked phase tracking → `In Progress`; tactical folder, no progress → `Tactical`; §3.3 filled, no folder → `Approved`; otherwise `Draft`. If two equally plausible, add one MC question to confirm. Then `update.ps1 -Id <Sxxxx> -Status <restored>` (leaving Block* status auto-clears its note).
- **`BlockByOtherTask` → restore**: only if dependency resolved to `Verified` (re-check via `select.ps1`) or owner chose a fallback that removes dependency. Otherwise keep block and report.
- **`Approved` → `Tactical`** (`--here`): record decisions, then hand to `/spec-tech <Sxxxx>`.
- **`Tactical` / `In Progress`** (`--here`): record decisions only; status unchanged.

Every transition INTO a `Block*` status (none happen here by default, but if a confirmation reveals a new external blocker) must carry `-StatusNote`. Advancing OUT of a `Block*` needs no note.

This skill advances **exactly one level** by default. Does not silently run builds, write code, or set `Implemented`/`Verified`.

### Stage 5 - Optional chain (`--chain`)

After successful advance, if `--chain` passed, hand ticket to `/spec-all <Sxxxx>` to continue pipeline from new status. Without `--chain`, stop after one level - the point is to unstick, then return control.

### Stage 6 - Catalog sync, dev log, report

```powershell
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status <new>   # if not already done in Stage 4
.\scripts\add_to_dev_log.ps1 "<spec file path>" "spec-quiz" "<Sxxxx>: <start> -> <end> via quiz (<N> questions)"
```

Print:

```text
spec-quiz <Sxxxx> <slug>
  Start: <status>   End: <status>
  Asked (<N>):
    <header> → <chosen option>
    ...
  Resolved without asking (<M>):
    <decision> → <value> (<why architecture/research decided it>)
  Next: <stop | /spec-tech queued | /spec-all chained | still blocked: <reason>>
```

---

## Hard rules

- **One spec, one level.** No batch, no multi-level climbing without `--chain`. Default contract is "unstick by exactly one transition".
- **AskUserQuestion is the only question vehicle.** Never ask free-form questions in chat prose when MC options can frame the decision. Always lead each question with a researched recommended default.
- **Never ask what the architecture already answers.** Filter every candidate question against codebase/docs first (Stage 1). Silently fill and report auto-resolved ones.
- **Never fabricate an owner answer.** Owner skips a question (AskUserQuestion returns no choice) → do not invent one - leave that input unfilled, do not advance past the gate that needs it, report it as outstanding.
- **Catalog writes.** Per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written; all status changes go through `update.ps1`, which also syncs the `.md` status header.
- **No code, no build.** A quiz collects decisions and advances status. Implementation belongs to `/spec-dev` (reached via `--chain` → `/spec-all`).
- **Debug-tag invariant untouched.** This skill never enters or leaves `BlockNeedUserTest`, so it neither inserts nor removes `Timber.d("Sxxxx:` tags.
- **`--dry` mutates nothing** - no questions asked, no file edits, no status change, no dev log.

---

## Spec Catalog hooks

- **Reads:** `select.ps1` (Stage 0 status, Stage 4 dependency re-check), the spec `.md`, `search.ps1` / `query.ps1` (Stage 1 research).
- **Gate:** `check-owner-inputs.ps1` (Draft→Approved, Stage 4).
- **Writes:** the spec `.md` (Stage 3 answers), `update.ps1 -Status` (Stage 4 transition), `add_to_dev_log.ps1` (Stage 6).
- **Forbidden:** writing to `spec-catalog.jsonl` directly; renaming the `Sxxxx_` prefix; advancing more than one level without `--chain`; creating tactical folders (that is `/spec-tech`).

---

## Examples

```text
/spec-quiz S0477
# Draft. §3.3 has 3 unfilled owner-input bullets, §6 has 1 Open research item.
# 2 of those are determined by the flavor hierarchy → filled silently.
# Asks 2 MC questions (default-player fallback behaviour; lite-flavor reach).
# Writes answers, gate passes → Draft -> Approved. Stops.

/spec-quiz S0312 --chain
# BlockQuestions. Status note: "confirm whether export keeps EXIF".
# Asks 1 MC question, restores to Tactical (tactical folder present, no progress),
# then chains /spec-all S0312.

/spec-quiz S0500 --dry
# Prints the 3 questions it would ask + options, mutates nothing.
```
