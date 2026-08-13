# Phase 04 - Carry the portable half into the canon

**Strategic spec:** [`../S1604_codify-agent-skills-rules-canon.md`](../S1604_codify-agent-skills-rules-canon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-12

---

## Objective

Author, in the canon working copy and without publishing, the portfolio-generalizable half of the batch's lessons plus the guard S1594 left without a carrier.

---

## Prerequisites

- [ ] Phase 01 and Phase 03 are ✅ Done - the portable material is written down locally first.
- [ ] The canon working copy is fast-forwarded: `git -C ~/.claude/plugins/marketplaces/sza-unified-rules rev-list --left-right --count HEAD...origin/main` reports `0 0`.
- [ ] Another session's uncommitted work in that clone is left untouched - stage only this ticket's files.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `~/.claude/plugins/marketplaces/sza-unified-rules/rules/AI_USAGE.md` | Modified | ≤ 40 |
| `~/.claude/plugins/marketplaces/sza-unified-rules/hooks/guard-bash-unavailable-command.ps1` | New | ≤ 200 |
| `~/.claude/plugins/marketplaces/sza-unified-rules/hooks/hooks.json` | Modified | ≤ 15 |
| `~/.claude/plugins/marketplaces/sza-unified-rules/hooks/README.md` | Modified | ≤ 15 |
| `~/.claude/plugins/marketplaces/sza-unified-rules/rules/contrib/fastmediasorter_mob_v2.md` | Modified | ≤ 30 |

---

## Steps

### Step 04.1 - Record the measured hook mechanics in the canon

**Files:** `rules/AI_USAGE.md` (canon working copy)

**Depends on:** - start of phase

**Prompt for developer:**

> Extend the canon's `rules/AI_USAGE.md` section 5 with the mechanics the batch established experimentally, each stated as a portable principle rather than a repository anecdote. A `PreToolUse` hook can modify the tool input, not only allow or deny, so a guard can correct instead of refusing - the refusal costs a turn, the correction costs nothing; the rewrite must carry the full input object, and only `additionalContext` reaches the model while `permissionDecisionReason` does not, which makes a silent rewrite a correctness hazard because the model then reasons about content it only partly saw. A bash pre-filter is part of the hook and can silently disarm it, and an unreachable hook is indistinguishable from one that allows everything, so a guard's registered pattern is tested against both must-reach and must-skip commands rather than only the hook's own exit codes. Making a name work beats refusing it: a missing interpreter is cheaper to shim onto the PATH than to guard, because no hook can fix and retry a failed command. An observer that speaks only when the widened re-run finds something cannot produce a false positive by construction, which is what lets it run on a high-frequency tool. Add the scoped-analyser principle to the testing material: when a cheap preflight approximates an expensive checker, run the real checker over the changed scope instead of reimplementing its rules lexically - the reimplementation was measured to cover 13.9% of failures and cannot express size rules at all.

**Why:**

Strategic §1 records that the canon predates the batch entirely, and strategic §2 goal 4 requires the portable half to reach it so a sibling repository gets the lessons without re-deriving them.

**Verification:**

- `Grep` - `updatedInput` and `pre-filter` both match in the canon's `rules/AI_USAGE.md`.
- `Grep` - `permissionDecisionReason` matches.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Canon AI_USAGE mechanics, guard registration and contrib record authored; expected: canon smoke and guard contract pass, stamp unchanged | actual: smoke 20/20 PASS, guard block=2 allow=0, stamp unchanged

---

### Step 04.2 - Ship the guard into the canon's hooks folder

**Files:** canon `hooks/guard-bash-unavailable-command.ps1`, `hooks/hooks.json`, `hooks/README.md`

**Depends on:** Step 04.1

**Prompt for developer:**

> Copy `~/.claude/hooks/guard-bash-unavailable-command.ps1` into the canon's `hooks/` folder, register it in `hooks/hooks.json` beside the existing guards using the same pre-filter shape, and list it in `hooks/README.md`. Keep the machine-local registration in `~/.claude/settings.json` in place and unchanged: the installed plugin cache lags the working copy, so removing the hand-wired registration now would disarm the guard until the plugin refreshes.

**Why:**

Strategic §10 records that S1594 left the propagation of this guard without a carrier and that this ticket becomes it; the canon's own section 5 states that a hook living in one repository protects one repository.

**Verification:**

- `Glob` - the file exists under the canon's `hooks/`.
- `Grep` - `guard-bash-unavailable-command` matches in the canon's `hooks/hooks.json` and `hooks/README.md`.
- `Grep` - `guard-bash-unavailable-command` still matches in `~/.claude/settings.json`.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Canon AI_USAGE mechanics, guard registration and contrib record authored; expected: canon smoke and guard contract pass, stamp unchanged | actual: smoke 20/20 PASS, guard block=2 allow=0, stamp unchanged

---

### Step 04.3 - Update this repository's contrib record

**Files:** canon `rules/contrib/fastmediasorter_mob_v2.md`

**Depends on:** Step 04.2

**Prompt for developer:**

> Record in this repository's contrib file what the batch contributed upward and what stayed local: the hook mechanics and the scoped-analyser principle went into the core, the hook inventory and its sync gate are a repository-local delta, and the refuted-approaches index is a repository-local delta whose portable entries are already reflected in the core. Add a line stating the core edit is authored but unpublished. Leave the adoption stamp alone while doing so: do not bump `CANON_VERSION`, do not recompute `coreDigest`, do not modify `.sza-canon.json`, and commit and push nothing in either repository. Stage only this file and the other files this phase touched, leaving the foreign uncommitted `rules/contrib/streams_player.md` untouched.

**Why:**

CLAUDE.md section 1 declares the contrib record the home for this repository's deltas and channel matrix against the canon, so a core change authored here is incomplete until the record says which half stayed local. Strategic ADR-4 rules the canon edits authored but unpublished because publication is a one-way outward operation requiring the owner's explicit ask, and a stamp bumped against a digest that exists on no other machine would claim an adoption no sibling repository can resolve.

**Verification:**

- `Grep` - `AGENT_HOOKS` and `REFUTED_APPROACHES` both match in the contrib file.
- `git -C ~/.claude/plugins/marketplaces/sza-unified-rules status --porcelain` lists only this phase's files plus the pre-existing foreign `rules/contrib/streams_player.md` modification.
- `git -C ~/.claude/plugins/marketplaces/sza-unified-rules diff --name-only` does not list `.sza-canon.json`, and the repository's own `.sza-canon.json` is unmodified.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Canon AI_USAGE mechanics, guard registration and contrib record authored; expected: canon smoke and guard contract pass, stamp unchanged | actual: smoke 20/20 PASS, guard block=2 allow=0, stamp unchanged

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Nothing is committed or pushed in the canon working copy.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the repository-side files this phase touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The canon carries an unpublished delta. Anyone running a canon release later must know it is there; Phase 05 records that in the ticket's own closure rather than in the stamp.

---

## Rollback Plan

Revert the canon working copy with `git -C ~/.claude/plugins/marketplaces/sza-unified-rules checkout -- <this phase's files>`, touching no other path so the foreign uncommitted contrib file survives. Nothing was published, so no external state needs undoing.
