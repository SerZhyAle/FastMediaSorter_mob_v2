# Spec Quiz - Unstick a Spec via Multiple-Choice Q&A

Ask the owner a focused set of multiple-choice questions about ONE stuck spec, write the answers into the spec file, then advance it exactly one lifecycle level. The quiz elicits only the decisions that are genuinely the owner's to make and that currently block the next transition - nothing the architecture, codebase, or prior research already answers.

This is the interactive counterpart to `/spec` (which auto-fills owner inputs by authoring) and `/spec-next` (which auto-skips anything needing a human). Use `/spec-quiz` when a spec is parked precisely *because* it needs the owner's call, and the owner is ready to give it.

## Usage

```text
/spec-quiz <Sxxxx>            # quiz the spec, advance one level, stop
/spec-quiz <Sxxxx> --chain    # after advancing, hand off to /spec-all to keep going
/spec-quiz <Sxxxx> --dry      # show the questions that WOULD be asked, ask nothing, mutate nothing
```

Exactly one ticket id. No batch mode - a quiz is a focused human conversation about a single spec.

---

## What each status unlocks

The current status determines what is blocking advancement, hence what the quiz asks and where it advances to:

| Current status | What blocks advancement | Quiz asks about | Advances to |
|----------------|-------------------------|-----------------|-------------|
| `Draft` | §3.3 Owner inputs unfilled, §6 research `Open` | scope, priority, UX/flavor decisions, open research forks | `Approved` |
| `BlockQuestions` | clarifications captured in `**Status note:**` / §0 / §6 `Open` | exactly those captured questions | restore to inferred pre-block status |
| `BlockByOtherTask` | a dependency ticket | confirm the dependency is resolved; pick fallback if not | restore (if cleared) |
| `Approved` *(with `--here`)* | nothing hard; ready for `/spec-tech` | residual scope/granularity forks | `Tactical` via `/spec-tech` |
| `Tactical` / `In Progress` *(with `--here`)* | design forks left to the owner | implementation forks | unchanged (decisions recorded) |

**Not quiz-resolvable** - report and redirect, mutate nothing:

- `BlockNeedUserTest` - needs a device run, not a conversation. Redirect to `/spec-test-device` or `/spec-sweep`.
- `BlockExternal` - waiting on an external resource a quiz cannot supply. Report the blocker from the status note.
- `Implemented` - code exists; needs audit, not decisions. Redirect to `/spec-check`.
- `Verified`, `Archived` - terminal. Report and stop.

`--here` opts into quizzing a non-blocked spec (`Approved` / `Tactical` / `In Progress`) for residual design forks. Without it, those statuses are reported as "not stuck" and the skill stops.

---

## Process

### Stage 0 - Resolve (never infer status from filename)

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json
```

Read the resolved `status` and `file`. Read the spec file. If `status` is not quiz-resolvable (table above), print the redirect and stop. The catalog row is authoritative; if the file header disagrees, trust the catalog and note the mismatch.

### Stage 1 - Extract decision points

Read the spec and pull the concrete items blocking the next transition:

- **`Draft`** - every §3.3 bullet whose value is an unfilled bracketed placeholder or empty; every §6 research item with `Status: Open`; any §3/§4 scope ambiguity the author flagged for the owner.
- **`BlockQuestions`** - the questions verbatim from `**Status note:**`, plus any §6 `Open` items and §0 captured text the block refers to.
- **`BlockByOtherTask`** - the dependency id named in §10 / status note.

For each candidate decision point, **research a recommended answer first** (codebase via `dev/CATALOG/scripts/query.ps1`, docs, existing tickets via `search.ps1`). Two outcomes:

- The architecture / codebase / a prior decision already determines it → **do not ask**. Fill it silently and list it under "Resolved without asking" in the report. (CLAUDE.md: never ask owner questions the architecture already answers.)
- It is a genuine owner call (preference, scope boundary, priority, UX wording, flavor reach, build-vs-buy) → turn it into a quiz question with a researched recommended default.

If, after this filter, zero questions remain: nothing here is the owner's to decide. Report that, attempt the advance anyway if the only blocker was the (now silently filled) inputs, otherwise stop without mutating.

### Stage 2 - Ask (AskUserQuestion)

Present the questions with `AskUserQuestion`:

- Each question: a ≤12-char `header`, a specific question ending in `?`, and 2-4 options.
- Put the researched recommendation **first** with `(Recommended)` appended to its label; give every option a one-line `description` of its consequence.
- Use `multiSelect: true` only when choices are genuinely combinable (e.g. which flavors a feature ships to).
- Max 4 questions per call - if more decision points remain, ask in successive batches, most-blocking first. Do not exceed what is needed to unblock.
- Use `preview` only for concrete artifacts worth comparing side by side (string wording variants, layout sketches, API-shape snippets).

`--dry`: print the composed questions and their options, then stop. No `AskUserQuestion`, no writeback, no status change.

### Stage 3 - Write answers into the spec

Edit the spec file directly (it is a `.md`, plain `Edit` is fine):

- Fill each answered §3.3 bullet with the chosen concrete value.
- Flip each resolved §6 item `Status: Open` → `Status: Resolved` and append the decision as a one-line note on that item.
- Append a dated decisions block recording each Q and the chosen answer, so the rationale survives:

```markdown
### Quiz decisions (<YYYY-MM-DD>)
- <question> → <chosen option> (<one-line rationale>)
```

Apply the same answers to §3/§4 prose where they resolve a flagged ambiguity. Keep edits surgical - do not rewrite unrelated sections.

### Stage 4 - Advance one level

Per starting status:

- **`Draft` → `Approved`**: run the gate, then flip.
  ```powershell
  pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id <Sxxxx>
  ```
  - Exit 0 → apply Draft→Approved hygiene to touched text (`..` not `...`, `ё`/`Ё`, lists over tables) and flip: `update.ps1 -Id <Sxxxx> -Status Approved` (it rewrites the file's first `**Status:**` line automatically).
  - Exit 1 → the gate still reports blockers (e.g. a §3.3 bullet a question did not cover). List them, ask the missing question(s) (back to Stage 2, one batch), retry the gate once. Still failing → leave `Draft`, report the residual blockers, stop.
- **`BlockQuestions` → restore**: infer the pre-block status from artifacts - tactical folder + checked phase tracking → `In Progress`; tactical folder, no progress → `Tactical`; §3.3 filled, no folder → `Approved`; otherwise `Draft`. If two are equally plausible, add one MC question to confirm. Then `update.ps1 -Id <Sxxxx> -Status <restored>` (leaving the Block* status auto-clears its note).
- **`BlockByOtherTask` → restore**: only if the dependency resolved to `Verified` (re-check via `select.ps1`) or the owner chose a fallback that removes the dependency. Otherwise keep the block and report.
- **`Approved` → `Tactical`** (`--here`): record decisions, then hand to `/spec-tech <Sxxxx>`.
- **`Tactical` / `In Progress`** (`--here`): record decisions only; status unchanged.

Every transition INTO a `Block*` status (none happen here by default, but if a confirmation reveals a new external blocker) must carry `-StatusNote`. Advancing OUT of a `Block*` needs no note.

This skill advances **exactly one level** by default. It does not silently run builds, write code, or set `Implemented`/`Verified`.

### Stage 5 - Optional chain (`--chain`)

After a successful advance, if `--chain` was passed, hand the ticket to `/spec-all <Sxxxx>` to continue the pipeline from the new status. Without `--chain`, stop after one level - the point is to unstick, then return control.

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

- **One spec, one level.** No batch, no multi-level climbing without `--chain`. The default contract is "unstick by exactly one transition".
- **AskUserQuestion is the only question vehicle.** Never ask free-form questions in chat prose when MC options can frame the decision. Always lead each question with a researched recommended default.
- **Never ask what the architecture already answers.** Filter every candidate question against the codebase/docs first (Stage 1). Silently fill and report the auto-resolved ones.
- **Never fabricate an owner answer.** If the owner skips a question (AskUserQuestion returns no choice), do not invent one - leave that input unfilled, do not advance past the gate that needs it, report it as outstanding.
- **Never hand-edit `PLAN/spec-catalog.jsonl`.** All status changes go through `update.ps1`. The status header in the `.md` is synced by the mutator, not by hand.
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
