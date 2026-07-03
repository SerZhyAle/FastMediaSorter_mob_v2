---
model: sonnet
---

# Git Guide

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. Dry technical prose only - no filler.
> 2. Autonomy: silently fix minor/non-structural inaccuracies; block only for critical business-logic decisions.
> 3. Terse report: one dry statement of what was done and why.

Git workflow for FastMediaSorter v2 - branching, staging, committing, pushing, fix-release, diff research.

## Usage

```
/git [optional: specific question or action]
```

Examples:
- `/git` - full Git reference
- `/git what should I commit now?`
- `/git analyze current changes and suggest commit groups`
- `/git how do I see what changed in PlayerActivity.kt?`
- `/git show me the old version of build.gradle.kts`
- `/git prepare a fix-release`
- `/git merge DEBUG-v001 to main`

---

## Process

On `$ARGUMENTS`:
- **Step 1 - Parse.** Empty → output full reference. "Analyze current changes" → run `git status` + `git diff --stat`, group by feature/concern, suggest commit groups. Specific file/topic → focus there.
- **Step 2 - "Analyze changes" requests:**
  1. `git status` - all modified + untracked.
  2. `git branch --show-current` - confirm active branch.
  3. Group files by feature (e.g. Chromecast, HEIC/HEIF, settings, docs, infra).
  4. Identify files that should NOT be committed (exclusion list below).
  5. Suggest 2-4 logical commit groups + proposed messages.
  6. Show exact `git add` commands per group.
- **Step 3 - Answer from reference below** using exact commands - never guess.

---

## Branching Model

Two-tier model. Know it before touching git.

### Branch roles


| Branch | Purpose | Who commits here |
|--------|---------|-----------------|
| `main` | Release-stable only. All published builds come from here. | Fix-release commits only (see below). Never direct development. |
| `DEBUG-v001`, `DEBUG-v002`, … | Active development. Sequential numbering, no gaps. | All feature work, specs, refactors, experiments. |

### Rules

- **Before any task:** `git branch --show-current` - confirm expected branch.
- **Development goes to current DEBUG branch, never `main` directly.**
- `main` accepts only: merges from `DEBUG-v00N` after plateau verification; fix-release commits (fixes for previously working features, no new behavior).
- Keep at most **2 live DEBUG branches**: current (next-release candidate) + optional "future".
- "Future" DEBUG branch born from current DEBUG branch, not from `main`.

### Worktrees

Two permanent working directories coexist:

| Directory | Branch | Purpose |
|-----------|--------|---------|
| `P:/ANDROID/FastMediaSorter_mob_v2` | `DEBUG-v001` (or current) | Development |
| `P:/ANDROID/FastMediaSorter_release` | `main` | Release builds only |

Release builds (`.\a r`, `.\a vr`, `.\a nl`) run automatically from release worktree - no manual switching.

```bash
# One-time worktree setup (already done - reference only)
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
git branch --show-current        # should be DEBUG-v00N
git status
git diff --stat
```

### Commit on a DEBUG branch

```bash
git add path/to/file.kt
git commit -m "feat: description"
git push origin DEBUG-v001
```

---

## Fix-Release Flow

Publishes fixes for previously working features with **zero new behavior**. Commits directly to `main` (only legitimate reason outside a DEBUG merge cycle). After publishing, rebase all live DEBUG branches.

```bash
# 1. Move to release worktree (main)
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

# 6. Run release build (or .\a r from dev directory - it auto-pulls)
.\a.ps1 r

# 7. Back in dev directory - rebase DEBUG branch onto updated main
cd P:/ANDROID/FastMediaSorter_mob_v2
git fetch origin main
git rebase origin/main          # or: git rebase main (if local main is up to date)
```

**Rule:** fix-release = only regression fixes. Adding new string resource, menu item, or UI → stop. That belongs in DEBUG.

---

## Merge DEBUG to main (Plateau Release)

When DEBUG reaches stability (key specs Verified/Implemented, build stable):

```bash
# 1. In release worktree: pull main current
cd P:/ANDROID/FastMediaSorter_release
git pull --ff-only

# 2. Merge DEBUG into main (no fast-forward - preserve merge commit)
git merge --no-ff DEBUG-v001 -m "release: merge DEBUG-v001 into main"

# 3. Tag the new release
git tag release/v2.60.XXXX.XXX

# 4. Push main and tag
git push origin main
git push origin release/v2.60.XXXX.XXX

# 5. Open next DEBUG branch from fresh main
cd P:/ANDROID/FastMediaSorter_mob_v2
git checkout main
git pull
git checkout -b DEBUG-v002
git push -u origin DEBUG-v002

# 6. Worktree stays on main (already there - no action)
cd P:/ANDROID/FastMediaSorter_release
```

---

## Cherry-pick Hotfix to DEBUG

After fix-release commit lands on `main`, bring it into current DEBUG:

```bash
# Find the fix commit hash
git log --oneline origin/main -5

# Cherry-pick into DEBUG branch
cd P:/ANDROID/FastMediaSorter_mob_v2
git cherry-pick <HASH>

# Or rebase whole DEBUG branch onto updated main (cleaner)
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

# Only changed file names (summary)
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

# Stage interactively - choose hunks within a file
git add -p app_v2/build.gradle.kts

# Verify what will be committed
git diff --cached
git status
```

---

## Commit

```bash
# Single-line message
git commit -m "feat: add HEIC/HEIF support via HeifSupportUtils"

# Multi-line message (heredoc)
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
| `fix:` | Bug fix (DEBUG branch; on `main` only for fix-release) |
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

# Restore a single file to a past commit (careful - overwrites working copy)
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

Multiple features in progress → split into logical commits:


**Group 1 - Feature work**
```bash
git add PLAN/S0NNN_feature-name.md
git add app_v2/src/main/java/com/sza/fastmediasorter/...
git commit -m "feat: description"
```

**Group 2 - Docs & changelog**
```bash
git add dev/CHANGELOG.md
git add docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md
git commit -m "docs: update feature list and changelog"
```

**Group 3 - Shared infra** (build.gradle, strings, settings)
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
