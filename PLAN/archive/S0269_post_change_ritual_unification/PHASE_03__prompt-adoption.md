# Phase 03 - Prompt Adoption

**Strategic spec:** [`../S0269_post_change_ritual_unification.md`](../S0269_post_change_ritual_unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Move the live quick/spec-dev prompt instructions to the dispatcher-based closure flow so operators do not keep reconstructing the old post-change ritual by hand.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.1 is Resolved.
- [x] Strategic §6.2 is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.github/prompts/quick.prompt.md` | Modified | ≤ 140 |
| `.claude/commands/quick.md` | Modified | ≤ 160 |
| `.github/prompts/spec-dev.prompt.md` | Modified | ≤ 260 |
| `.claude/commands/spec-dev.md` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Replace quick-prompt manual closure with the dispatcher

**Files:** `.github/prompts/quick.prompt.md`, `.claude/commands/quick.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the standalone dev-log closure step in both quick-command files with the canonical `scripts/post-change.ps1` entry point. Keep `/quick` as a no-build fast path, preserve the small-change guardrails, and explain that `ChangeType` must match the actual touched artifact class.

**Verification:**

- `Grep` - `scripts/post-change.ps1` appears in `.github/prompts/quick.prompt.md`.
- `Grep` - `ChangeType` appears in `.github/prompts/quick.prompt.md`.
- `Grep` - `Catalog sync (`scan.ps1` / `render.ps1`)` returns zero hits in `.github/prompts/quick.prompt.md`.
- `Grep` - `scripts/post-change.ps1` appears in `.claude/commands/quick.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: .github/prompts/quick.prompt.md, .claude/commands/quick.md. Dev log recorded.

---

### Step 03.2 - Route both spec-dev prompts through post-change for per-file closure

**Files:** `.github/prompts/spec-dev.prompt.md`, `.claude/commands/spec-dev.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Update both spec-dev prompt files so the per-file mechanical closure step uses `scripts/post-change.ps1 -ChangeType <...>` instead of a dev-log-only instruction. Keep spec status ownership, build gates, `close-and-log.ps1`, and BlockNeedUserTest behaviour exactly as they are.

**Verification:**

- `Grep` - `scripts/post-change.ps1` appears in `.github/prompts/spec-dev.prompt.md`.
- `Grep` - `ChangeType` appears in `.github/prompts/spec-dev.prompt.md`.
- `Grep` - `Run dev log` returns zero hits in `.github/prompts/spec-dev.prompt.md`.
- `Grep` - `scripts/post-change.ps1` appears in `.claude/commands/spec-dev.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: .github/prompts/spec-dev.prompt.md, .claude/commands/spec-dev.md. Dev log recorded.

---

### Step 03.3 - Remove the last legacy scan/render fallback from the Claude spec-dev command

**Files:** `.claude/commands/spec-dev.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Keep the existing `close-and-log.ps1` flow in `.claude/commands/spec-dev.md`, but replace any remaining fallback wording that still names raw `scan.ps1` + `render.ps1` with the dispatcher/canonical wrapper terminology (`post-change.ps1` and `catalog_sync.ps1`). Preserve the current session-snapshot and finalization guidance already present in the file.

**Verification:**

- `Grep` - `scripts/post-change.ps1` or `catalog_sync.ps1` appears in `.claude/commands/spec-dev.md`.
- `Grep` - `scan.ps1` + `render.ps1` returns zero hits in `.claude/commands/spec-dev.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: .claude/commands/spec-dev.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.github/prompts/quick.prompt.md` and `.claude/commands/quick.md` use `scripts/post-change.ps1 -ChangeType` as the canonical closure path.
- [x] `.github/prompts/spec-dev.prompt.md` and `.claude/commands/spec-dev.md` use `scripts/post-change.ps1 -ChangeType` for per-file mechanical closure.
- [x] `.claude/commands/spec-dev.md` no longer names raw `scan.ps1` + `render.ps1` as the routine fallback path.
- [x] Dev log entry added for every file in "Files Touched" via `\.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After prompt adoption lands, the remaining work is a final static cleanup sweep plus the audit run.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.