# Phase 04 - Seed `friendly-android-doc-writer`

**Strategic spec:** [../S0277_per_agent_memory_seeding.md](../S0277_per_agent_memory_seeding.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Populate `.claude/agent-memory/friendly-android-doc-writer/` with the minimal subset of universal ritual invariants required for the documentation profile. The role-specific Kotlin / build-trap records are deliberately excluded - the doc writer never compiles, builds, or edits Kotlin.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Phase 01 mapping table is frozen.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/agent-memory/friendly-android-doc-writer/MEMORY.md` | New | ≤ 25 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_features_nolegal.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_timestamp_in_chat.md` | New (U) | ≤ 40 |
| `.claude/agent-memory/friendly-android-doc-writer/project_functionality_log.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_no_backticks_in_bash_args.md` | New (U) | ≤ 50 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_no_owner_questions_when_architecture_already_answers.md` | New (U) | ≤ 80 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_pwsh_efficiency.md` | New (U) | ≤ 60 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_no_scaffolding_as_done.md` | New (U) | ≤ 70 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_pwsh_bash_dollar_escape_trap.md` | New (U) | ≤ 100 |
| `.claude/agent-memory/friendly-android-doc-writer/user_author_style.md` | New (user-ritual) | ≤ 30 |
| `.claude/agent-memory/friendly-android-doc-writer/feedback_pwsh_path.md` | New (user-ritual) | ≤ 30 |

> Doc writer profile total: 9 invariants + MEMORY.md = 10 files.

---

## Steps

### Step 04.1 - Write 9 invariants and the index

**Files:** every file in "Files Touched".
**Depends on:** - start of phase

**Prompt for developer:**

> For each row in Phase 01 mapping where the D column is `U`, create a file with:
>
> - Frontmatter (`name`, `description`, `metadata.type`).
> - Body verbatim from donor.
> - `**How to apply:**` re-targeted to documentation tasks (editing `FEATURES*.md`, polishing UI copy, rewriting `strings.xml`, writing release notes, mirroring EN/RU/UK).
>
> The `feature_nolegal` and `author_style` records are the most load-bearing for this profile - keep their adapted `How to apply:` precise.

**Verification:**

- `Glob` - all 10 files in "Files Touched" exist.
- `Grep` - every record contains `**How to apply:**` (or equivalent for `user_author_style`).
- `Grep` - zero hits for `Timber.d(` and `BuildConfig.` inside the target dir (doc writer must not be prompted to think about code-level constructs).

**Status:** `[x]` done

---

### Step 04.2 - Validate frontmatter shape

**Files:** `.claude/agent-memory/friendly-android-doc-writer/*.md` (excluding `MEMORY.md`).
**Depends on:** Step 04.1

**Prompt for developer:**

> Same frontmatter validation as Phase 02.2.

**Verification:**

- `Grep` - `name:` appears in every record.
- `Grep` - `metadata:` appears in every record.
- `Grep` - `{{` returns zero hits under the target dir.

**Status:** `[x]` done

---

### Step 04.3 - Dev-log every new file

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`).
**Depends on:** Step 04.2

**Prompt for developer:**

> Same dev-log procedure as Phase 02.3.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains at least 9 new lines tagged with `friendly-android-doc-writer` from 2026-05-21.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All steps `[x]`.
- [ ] `git diff --stat .claude/agent-memory/android-rd-specialist/` empty.
- [ ] No record file references `Timber.d`, `BuildConfig`, or `Kotlin` source-set paths (preserves doc-only role).

---

## Handoff Notes to Next Phase

All three target profiles seeded; Phase 05 runs the final invariants and closes the spec.

---

## Rollback Plan

`rm -rf .claude/agent-memory/friendly-android-doc-writer/*.md`. No code or harness config is affected.
