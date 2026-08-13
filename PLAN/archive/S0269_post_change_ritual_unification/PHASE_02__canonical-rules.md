# Phase 02 - Canonical Rules

**Strategic spec:** [`../S0269_post_change_ritual_unification.md`](../S0269_post_change_ritual_unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Make the main operator-facing rule files describe the dispatcher as the canonical post-change entry point and remove catalog guidance that contradicts gitignore reality.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.3 is Resolved.
- [x] Strategic §6.4 is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 340 |
| `.github/copilot-instructions.md` | Modified | ≤ 380 |
| `.github/agents/android-rd-specialist.agent.md` | Modified | ≤ 120 |
| `.claude/agents/android-rd-specialist.md` | Modified | ≤ 120 |
| `.claude/commands/quick.md` | Modified | ≤ 120 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Collapse the canonical post-change rule to one command

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite `CLAUDE.md` `Post-Change Steps` so the mandatory mechanical action is one `scripts/post-change.ps1 -ChangeType <...>` command. Keep feature docs, functionality log, and spec catalog sync as skill-owned decisions, and correct the catalog note to say the generated indexes are local gitignored files.

**Verification:**

- `Grep` - `scripts/post-change.ps1 -File "<path>" -Target "<target>" -Description "<english description>" -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed>` appears in `CLAUDE.md`.
- `Grep` - `local gitignored indexes` appears in `CLAUDE.md`.
- `Grep` - `Commit updated `dev/CATALOG/<module>.jsonl` + `<module>.md`` returns zero hits in `CLAUDE.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: CLAUDE.md. Dev log recorded.

---

### Step 02.2 - Align active agent guidance with catalog_sync and the new router

**Files:** `.github/copilot-instructions.md`, `.github/agents/android-rd-specialist.agent.md`, `.claude/agents/android-rd-specialist.md`, `.claude/commands/quick.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update the active GitHub and Claude agent guidance so catalog refresh points at `scripts/catalog_sync.ps1`, catalog outputs are described as gitignored local indexes, and the quick helper text refers to `post-change.ps1 -ChangeType` rather than the legacy split ritual.

**Verification:**

- `Grep` - `scripts/catalog_sync.ps1 -Module <app_v2|wear>` appears in `.github/copilot-instructions.md`.
- `Grep` - `gitignored indexes` appears in `.github/agents/android-rd-specialist.agent.md`.
- `Grep` - `gitignored indexes` appears in `.claude/agents/android-rd-specialist.md`.
- `Grep` - `-ChangeType Kotlin` appears in `.claude/commands/quick.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: .github/copilot-instructions.md, .github/agents/android-rd-specialist.agent.md, .claude/agents/android-rd-specialist.md, .claude/commands/quick.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No touched file instructs agents to commit `dev/CATALOG/<module>.jsonl` or `<module>.md`.
- [x] Touched catalog guidance files point at `scripts/catalog_sync.ps1` instead of the old scan/render pair.
- [x] Dev log entry added for every file in "Files Touched" via `\.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The canonical operator surface is aligned; the remaining work is to migrate the live quick/spec-dev prompts away from their manual closure wording.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.