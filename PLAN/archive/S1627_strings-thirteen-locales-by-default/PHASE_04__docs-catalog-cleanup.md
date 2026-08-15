# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1627_strings-thirteen-locales-by-default.md`](../S1627_strings-thirteen-locales-by-default.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Write the new rule where the next agent will actually read it, and regenerate the indexes the new scripts belong to.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 40 added |
| `CLAUDE.md` | Modified | ≤ 8 added |
| `AGENTS.md` | Modified | ≤ 8 added |
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | n/a - generated |

---

## Steps

### Step 04.1 - Document the route and state the rule

**Files:** `docs/DEV_OPS.md`, `CLAUDE.md`, `AGENTS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a "Thirteen locales" section to `docs/DEV_OPS.md` describing the whole loop in order: the ten best-effort locales are filled in a batch, the pre-release stage produces the list, the translation task closes it, the gate refuses while it is non-empty, and the authoring hint tells you which locales a new key is missing. Name each command and its exit codes. Then add one line to the strict-rules list in `CLAUDE.md` stating that a new UI string reaches the release in all thirteen declared locales, gated by `assert-new-lexemes-translated.ps1` at the pre-release stage, and mirror that line into `AGENTS.md` per the shared-rules sync note at the top of `CLAUDE.md`.

**Why:**

Strategic §3.1 records that the rule must be mechanical because an ungated rule is followed 1-8% of the time; the gate from Phase 02 supplies the mechanism, and this step supplies the sentence that tells a reader the mechanism exists before they trip over it.

**Verification:**

- `Grep` - `assert-new-lexemes-translated` matches in `docs/DEV_OPS.md`, `CLAUDE.md` and `AGENTS.md`.
- `Grep` - `list-new-lexemes` matches in `docs/DEV_OPS.md`.
- `Grep` - the added `CLAUDE.md` line and the added `AGENTS.md` line are byte-identical apart from list numbering.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[x]` done - 2026-08-14. `docs/DEV_OPS.md` carries "Thirteen locales - S1627" in the string-tooling section, in that section's own shape: the commands with their exit codes, the three-step loop, then the three facts the commands do not state. `CLAUDE.md` Rule 30 and its `AGENTS.md` mirror added. Registry validate exit 0.

Verification adapted on one predicate: the two rule lines are identical in wording but not byte-identical, because `AGENTS.md`'s own convention prefixes every mirrored rule with `(CLAUDE.md Rule N)` and its list is bulleted rather than numbered. Matching the file's convention beats matching the predicate literally.

---

### Step 04.2 - Regenerate the script index and close

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the script cheatsheet with `scripts/utils/help.ps1 -Generate` so the two new scripts appear with their parameters, then run the closure facade over the whole changed set of this phase with `-ChangeType Doc -ScopeToFile`. Do not hand-edit the cheatsheet.

**Why:**

The cheatsheet is a render target, and the repository's rule is that a render target is regenerated from its source rather than edited - the sync gate in `post-change.ps1` fails the closure otherwise.

**Verification:**

- `Grep` - `list-new-lexemes.ps1` and `assert-new-lexemes-translated.ps1` each match in `docs/SCRIPT_CHEATSHEET.md`.
- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<this phase's files>" -Target S1627 -ChangeType Doc -ScopeToFile` - verdict `PASS`, exit code 0.

**Status:** `[x]` done - 2026-08-14. `help.ps1 -Generate` rewrote `docs/SCRIPT_CHEATSHEET.md` (324 scripts); both new scripts appear in it. This also clears the stale-cheatsheet advisory that every closure of phases 01-03 reported.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `dev/CHANGELOG.md` carries an entry for every file this plan touched.
- [ ] `.\a.ps1 fg` exit code recorded.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert the documentation edits and regenerate the cheatsheet. No executable behaviour depends on this phase.
