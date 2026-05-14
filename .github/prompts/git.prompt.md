---
mode: agent
description: "Use when: asked about git commits, how to stage files, what to commit, git diff analysis, pushing changes, researching old file versions, branch operations, fix-release, worktree, or asked to run /git command. Triggers on: git, commit, push, diff, stage, branch, merge, rebase, worktree, fix-release, what changed."
---

# Git Guide

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** No fluff, no conversational filler, dry technical prose only.
> 2. **AUTONOMY OVER BUREAUCRACY:** DO NOT prompt the user for minor or non-structural specification inaccuracies. Silently fix them, make reasonable technical decisions, and proceed. Only block for critical business-logic decisions.
> 3. **TERSE REPORTING:** NO verbose summaries or time tracking. After executing this skill, output ONLY a single dry, concise statement of what was done and why.

Git workflow for FastMediaSorter v2 — branching model, staging, committing, pushing, fix-release, and diff research.

## Usage

```
/git [optional: specific question or action]
```

Examples:
- `/git` — show full Git reference
- `/git what should I commit now?`
- `/git analyze current changes and suggest commit groups`
- `/git how do I see what changed in PlayerActivity.kt?`
- `/git show me the old version of build.gradle.kts`
- `/git prepare a fix-release`
- `/git merge DEBUG-v001 to main`

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse the request.**
If `$ARGUMENTS` is empty, output the full Git reference below.
If `$ARGUMENTS` asks to analyze current changes, run `git status` and `git diff --stat` and group the changes by feature/concern, then suggest commit groups.
If `$ARGUMENTS` asks about a specific file or topic, focus the answer on that.

**Step 2 — For "analyze changes" requests:**
1. Run `git status` to see all modified and untracked files.
2. Run `git branch --show-current` to confirm which branch is active.
3. Group files by feature (e.g. Chromecast, HEIC/HEIF, settings, docs, infra).
4. Identify files that should NOT be committed (see exclusion list below).
5. Suggest 2–4 logical commit groups with proposed commit messages.
6. Show the exact `git add` commands for each group.

**Step 3 — Answer from the reference below.**
Do not guess — use exact commands from this reference.

---

## Branching Model

This project uses a two-tier branching model. Know it before touching git.

### Branch roles

| Branch | Purpose | Who commits here |
|--------|---------|-----------------|
| `main` | Release-stable only. All published builds come from here. | Fix-release commits only (see below). Never direct development. |
| `DEBUG-v001`, `DEBUG-v002`, … | Active development. Sequential numbering, no gaps. | All feature work, specs, refactors, experiments. |

### Rules

- **Before any task:** `git branch --show-current` — confirm you are on the expected branch.
- **Development work goes to the current DEBUG branch, never to `main` directly.**
- `main` accepts only:
  - Merges from `DEBUG-v00N` after plateau verification.
  - Fix-release commits (fixes for previously working features, no new behavior).
- Keep at most **2 live DEBUG branches** at a time: current (next-release candidate) + optional "future".
- "Future" DEBUG branch is born from the current DEBUG branch, not from `main`.

### Worktrees

Two permanent working directories exist simultaneously:

| Directory | Branch | Purpose |
|-----------|--------|---------|
| `P:/ANDROID/FastMediaSorter_mob_v2` | `DEBUG-v001` (or current) | Development |
| `P:/ANDROID/FastMediaSorter_release` | `main` | Release builds only |

Release builds (`.\a r`, `.\a vr`, `.\a nl`) run automatically from the release worktree — no manual switching needed.

```bash
# One-time worktree setup (already done — reference only)
git worktree add ../FastMediaSorter_release main

# List worktrees
git worktree list

# Remove worktree (only when truly decommissioning)
git worktree remove ../FastMediaSorter_release
```

---

## Daily Development Flow

### Start a session

```bash
# Always confirm where you are
git branch --show-current        # should be DEBUG-v00N

# See what's changed
git status
git diff --stat
```

### Commit on a DEBUG branch

```bash
# Stage and commit as usual — everything goes to DEBUG-v00N
git add path/to/file.kt
git commit -m "feat: description"

# Push to remote
git push origin DEBUG-v001
```

---

## Fix-Release Flow

A fix-release publishes fixes for previously working features with **zero new behavior**. It commits directly to `main` (the only legitimate reason to do so outside a DEBUG merge cycle). After publishing, all live DEBUG branches must be rebased.

```bash
# 1. Move to the release worktree (main)
cd P:/ANDROID/FastMediaSorter_release

# 2. Make sure main is current
git pull --ff-only

# 3. Apply fix(es) directly on main
git add path/to/fixed/file.kt
git commit -m "fix: description of the fix"

# 4. Tag the new release version
git tag release/v2.60.XXXX.XXX

# 5. Update WHATS_NEW.md with a "Fix Release" subsection, commit
git add docs/WHATS_NEW.md
git commit -m "docs: WHATS_NEW for fix-release vX.X"

# 6. Run release build (or just use .\a r from the dev directory — it auto-pulls)
.\a.ps1 r

# 7. Back in the dev directory — rebase DEBUG branch onto updated main
cd P:/ANDROID/FastMediaSorter_mob_v2
git fetch origin main
git rebase origin/main          # or: git rebase main (if local main is up to date)
```

**Rule:** fix-release = only regression fixes. If you find yourself adding a new string resource, a new menu item, or new UI — stop. That belongs in DEBUG.

---

## Merge DEBUG to main (Plateau Release)

When the DEBUG branch reaches stability (all key specs Verified/Implemented, build stable):

```bash
# 1. In release worktree: pull main to make sure it is current
cd P:/ANDROID/FastMediaSorter_release
git pull --ff-only

# 2. Merge DEBUG into main (no fast-forward — preserve merge commit)
git merge --no-ff DEBUG-v001 -m "release: merge DEBUG-v001 into main"

# 3. Tag the new release
git tag release/v2.60.XXXX.XXX

# 4. Push main and the tag
git push origin main
git push origin release/v2.60.XXXX.XXX

# 5. Open the next DEBUG branch from fresh main
cd P:/ANDROID/FastMediaSorter_mob_v2
git checkout main
git pull
git checkout -b DEBUG-v002
git push -u origin DEBUG-v002

# 6. Update the worktree to stay on main
cd P:/ANDROID/FastMediaSorter_release
# (already on main — no action needed)
```

---

## Cherry-pick Hotfix to DEBUG

After a fix-release commit lands on `main`, bring it into the current DEBUG branch:

```bash
# Find the fix commit hash
git log --oneline origin/main -5

# Cherry-pick into DEBUG branch
cd P:/ANDROID/FastMediaSorter_mob_v2
git cherry-pick <HASH>

# Or rebase the whole DEBUG branch onto updated main (cleaner)
git rebase origin/main
```

---

## Inspect Changes

```bash
# Overview of all changed files
git status

# Full diff of all unstaged changes
git diff

# Diff of one specific file
git diff app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt

# Diff vs a specific past commit
git diff 945d22a -- app_v2/build.gradle.kts

# Only the file names that changed (summary)
git diff --stat

# What is already staged (after git add)
git diff --cached

# Recent commits
git log --oneline -10

# Recent commits on main from release worktree
cd P:/ANDROID/FastMediaSorter_release && git log --oneline -5
```

---

## Stage Files

```bash
# Stage a specific file
git add app_v2/src/main/java/com/sza/fastmediasorter/core/util/HeifSupportUtils.kt

# Stage an entire new directory
git add app_v2/src/main/java/com/sza/fastmediasorter/core/cast/

# Stage interactively — choose which hunks (chunks) within a file to include
git add -p app_v2/build.gradle.kts

# Verify what will be committed before committing
git diff --cached
git status
```

---

## Commit

```bash
# Single-line message
git commit -m "feat: add HEIC/HEIF support via HeifSupportUtils"

# Multi-line message (use heredoc)
git commit -m "$(cat <<'EOF'
feat: Chromecast cast integration

- Add CastMediaManager and core/cast/ module
- Wire CastButton in player overflow menu
- Register CastOptionsProvider in AndroidManifest

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

**Commit message prefixes:**

| Prefix | When to use |
|--------|------------|
| `feat:` | New user-facing feature |
| `fix:` | Bug fix (use on DEBUG branch; on `main` only for fix-release) |
| `refactor:` | Code restructure, no behavior change |
| `docs:` | Documentation only |
| `chore:` | Build, config, scripts, CI |
| `test:` | Tests only |
| `release:` | Merge commit of DEBUG → main |

---

## Push

```bash
# Push current DEBUG branch
git push origin DEBUG-v001

# First push of a new DEBUG branch
git push -u origin DEBUG-v002

# Push main after a fix-release or merge (from release worktree)
cd P:/ANDROID/FastMediaSorter_release
git push origin main

# Push a release tag
git push origin release/v2.60.5130.151
```

**Never force-push `main`.**

---

## Research Old Versions

```bash
# Show a file as it was at a specific commit
git show 945d22a:app_v2/build.gradle.kts

# Full commit history of ONE file
git log --oneline -- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt

# Who changed each line and when
git blame app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt

# Full diff between two commits (all files)
git diff 18945f1..945d22a

# Diff between two commits for ONE file
git diff 18945f1..945d22a -- app_v2/build.gradle.kts

# Show all release tags
git tag --list "release/*" --sort=-version:refname

# What commit a tag points to
git rev-parse --short release/v2.60.5130.151

# Restore a single file to a past commit (careful — overwrites working copy)
git checkout 18945f1 -- app_v2/build.gradle.kts
```

---

## What NOT to Commit

Never stage these paths:

| Path | Reason |
|------|--------|
| `temp/` | Local artifacts, APK backups, pre-modification backups |
| `logs/` | Device logcat output |
| `app_v2/build/` | Gradle build output |
| `.gradle/` | Gradle cache |
| `DOWNLOADS/` | Built APK/AAB artifacts |
| `*.apk`, `*.aab` | Binary build outputs |
| `local.properties` | Local SDK path (machine-specific) |
| `.claude/settings.local.json` | Personal Claude Code overrides |
| `dev/CATALOG/*.jsonl`, `dev/CATALOG/*.md` | Auto-generated, gitignored |

Always commit:
- All source files (`app_v2/src/`, `wear/src/`)
- `app_v2/build.gradle.kts` (version bumps, dependency changes)
- `app_v2/proguard-rules.pro`
- `app_v2/src/main/AndroidManifest.xml`
- `PLAN/` specs and `docs/` documentation
- `dev/CHANGELOG.md` and `dev/PROJECT_OPERATIONS_INDEX.md`
- `.claude/commands/` (shared team skills)
- `gradle/libs.versions.toml`
- `CLAUDE.md`, `a.ps1`, `scripts/`

---

## Typical Commit Grouping

When multiple features are in progress simultaneously, split into logical commits:

**Group 1 — Feature work**
```bash
git add PLAN/S0NNN_feature-name.md
git add app_v2/src/main/java/com/sza/fastmediasorter/...
git commit -m "feat: description"
```

**Group 2 — Docs & changelog**
```bash
git add dev/CHANGELOG.md
git add docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md
git commit -m "docs: update feature list and changelog"
```

**Group 3 — Shared infra** (build.gradle, strings, settings)
```bash
git add app_v2/build.gradle.kts
git commit -m "chore: update dependencies and build config"
```

---

## Quick Reference Card

| Goal | Command |
|------|---------|
| Current branch | `git branch --show-current` |
| All worktrees | `git worktree list` |
| See all changes | `git diff` |
| See one file | `git diff -- path/to/file` |
| Stage file | `git add path/to/file` |
| Stage parts of file | `git add -p path/to/file` |
| Check staged | `git diff --cached` |
| Commit | `git commit -m "message"` |
| Push DEBUG branch | `git push origin DEBUG-v001` |
| Old file at commit | `git show HASH:path/to/file` |
| File history | `git log --oneline -- path/to/file` |
| Line-by-line blame | `git blame path/to/file` |
| Diff two commits | `git diff HASH1..HASH2` |
| All release tags | `git tag --list "release/*"` |
| Rebase DEBUG on main | `git rebase origin/main` |
| Cherry-pick fix | `git cherry-pick HASH` |
