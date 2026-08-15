# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1458_bash-pwsh-leading-slash-mangled.md`](../S1458_bash-pwsh-leading-slash-mangled.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Write the rule where the other guard rules are written, keep the generated script sheet honest, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 03.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 15 |
| `AGENTS.md` | Modified | ≤ 15 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 04.1 - Write the rule in CLAUDE.md and AGENTS.md

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the rule to the Strict Rules list in `CLAUDE.md` in the form rules 24-26 use - what is refused, the hook that refuses it, and the two accepted replacements - and state the one difference from its siblings: this guard is registered by the project settings, so it is scoped to this repository and travels with the checkout. Mirror the same rule into `AGENTS.md`, as the file's own header requires for shared rules.

**Why:**

Strategic §11 criterion 8 requires the rule to be written in both files in the form of rules 24-26, and `CLAUDE.md`'s own header states that a change to a shared rule must be synchronised into `AGENTS.md`.

**Verification:**

- `Grep` - `guard-bash-slash-arg` matches in `CLAUDE.md`.
- `Grep` - `guard-bash-slash-arg` matches in `AGENTS.md`.
- `Grep` - the `CLAUDE.md` entry names both replacements.

**Status:** `[x]` done - `CLAUDE.md` Rule 27 and the matching `AGENTS.md` bullet; both name the doubled leading slash and `MSYS2_ARG_CONV_EXCL`, and both state that this guard is registered by the project settings rather than globally.

---

### Step 04.2 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` to regenerate the cheatsheet after the new scripts landed, then confirm the drift gate agrees. If the generator does not reach `.claude/hooks/`, record that in the step log and leave the sheet untouched rather than hand-editing it.

**Why:**

The drift gate fails any run that added a script without regenerating the sheet, and a generated artifact is never hand-kept - the canon states a render target is regenerated from its source of truth rather than edited.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1`; exit code equals 0.
- Either `docs/SCRIPT_CHEATSHEET.md` names the new hook, or the step log records that the generator's scan roots exclude `.claude/hooks/`.

**Status:** `[x]` done - second branch. `help.ps1 -Generate` exit 0 (294 scripts), `assert-script-cheatsheet-sync.ps1` exit 0 (`script-cheatsheet: in sync`). The sheet does not name the hook and must not: `Get-ScriptFiles` scans exactly `scripts/` and `dev/CATALOG/scripts/`, so `.claude/hooks/` is outside its roots by construction. The sheet was regenerated, not hand-edited, and came out byte-identical.

---

### Step 04.3 - Close through the post-change facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Close the ticket with one `post-change.ps1` call naming the whole changed set through `-Files`, `-ChangeType Mixed` and `-ScopeToFile`, and read its verdict line. Do not write `dev/CHANGELOG.md` by hand.

**Why:**

The repository's closure contract routes mechanical closure through the facade so the gates run and the change is journalled in one verdict, and a per-file closure re-runs those gates against an unchanged tree.

**Verification:**

- Run the facade; its final line contains `post-change: PASS` and the exit code equals 0.
- `Grep` - `S1458` matches in `dev/CHANGELOG.md`.

**Status:** `[x]` done - one facade call over the whole set of five (`-ChangeType Mixed -ScopeToFile`). First run returned `PASS WITH ADVISORIES (1)`, the sole advisory being the `repository-rules` registry record; the record's sibling paths were read and each decided (see Step log below), and the re-run with `-RegistryAck "repository-rules"` returned `post-change: PASS`, exit 0. The dev-log's recent-duplicate guard skipped the second row, so `dev/CHANGELOG.md` carries exactly one entry for this change (7 `S1458` matches total across the ticket's history).

**Step log - registry sibling decisions (`repository-rules`):**

- `CLAUDE.md`, `AGENTS.md` - edited; carry Rule 27.
- `GEMINI.md` - unchanged, correctly. It is a pointer file that defers to `AGENTS.md`/`CLAUDE.md`/`.github/copilot-instructions.md` and carries no rule list of its own.
- `.github/copilot-instructions.md` - unchanged. It carries a numbered rules digest, but that digest already stops at CLAUDE.md Rule 24 (its item 18) and omits Rules 25 and 26; mirroring only Rule 27 into it would be arbitrary. Pre-existing gap, out of scope, parked as its own ticket.
- `.github/prompts/*.prompt.md`, `.claude/agents/*.md`, `.claude/commands/*.md`, `.claude/reference/*.md`, `.claude/templates/*.md`, `.claude/skills/*/SKILL.md` - unchanged. These are task drivers, not rule digests. Several do document literal `-Reason "/spec-next"`-shaped calls, and the guard refuses exactly those when they are issued from the Bash tool - which is the designed behaviour: strategic goal 2 puts the fix in the refusal text so the caller needs no doc lookup, and pre-emptively rewriting every documented example is the caller-must-remember model ADR-1 rejects.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via the facade's `-Files` set.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - skipped as doc-only per `/spec-dev` "Phase-boundary audit".

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the two documentation edits and regenerate the cheatsheet - the guard itself is rolled back by phase 03's plan.
