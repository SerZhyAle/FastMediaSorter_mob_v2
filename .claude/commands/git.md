# Git Guide

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** No fluff, no conversational filler, dry technical prose only.
> 2. **AUTONOMY OVER BUREAUCRACY:** DO NOT prompt the user for minor or non-structural specification inaccuracies. Silently fix them, make reasonable technical decisions, and proceed. Only block for critical business-logic decisions.
> 3. **TERSE REPORTING:** NO verbose summaries or time tracking. After executing this skill, output ONLY a single dry, concise statement of what was done and why.

Git workflow for FastMediaSorter v2 — staging, committing, pushing, and diff research.

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

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse the request.**
If `$ARGUMENTS` is empty, output the full Git reference below.
If `$ARGUMENTS` asks to analyze current changes, run `git status` and `git diff --stat` and group the changes by feature/concern, then suggest commit groups.
If `$ARGUMENTS` asks about a specific file or topic, focus the answer on that.

**Step 2 — For "analyze changes" requests:**
1. Run `git status` to see all modified and untracked files.
2. Group files by feature (e.g. Chromecast, HEIC/HEIF, settings, docs, infra).
3. Identify files that should NOT be committed (see exclusion list below).
4. Suggest 2–4 logical commit groups with proposed commit messages.
5. Show the exact `git add` commands for each group.

**Step 3 — Answer from the reference below.**
Do not guess — use exact commands from this reference.

---

## Git Reference

### Inspect Changes

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
```

---

### Stage Files

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

### Commit

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
| `fix:` | Bug fix |
| `refactor:` | Code restructure, no behavior change |
| `docs:` | Documentation only |
| `chore:` | Build, config, scripts, CI |
| `test:` | Tests only |

---

### Push

```bash
# Push current branch to origin
git push

# Explicit (same result on main)
git push origin main
```

---

### Research Old Versions

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

# Restore a single file to a past commit (careful — overwrites working copy)
git checkout 18945f1 -- app_v2/build.gradle.kts
```

---

### What NOT to Commit

Never stage these paths:

| Path | Reason |
|------|--------|
| `temp/` | Local artifacts, APK backups, pre-modification backups |
| `logs/` | Device logcat output |
| `app_v2/build/` | Gradle build output |
| `.gradle/` | Gradle cache |
| `*.apk`, `*.aab` | Binary build outputs |
| `local.properties` | Local SDK path (machine-specific) |
| `.claude/settings.local.json` | Personal Claude Code overrides |

Always commit:
- All source files (`app_v2/src/`)
- `app_v2/build.gradle.kts` (version bumps, dependency changes)
- `app_v2/proguard-rules.pro`
- `app_v2/src/main/AndroidManifest.xml`
- `PLAN/` specs and `docs/` documentation
- `dev/CHANGELOG.md` and `dev/PROJECT_OPERATIONS_INDEX.md`
- `.claude/commands/` (shared team skills)
- `gradle/libs.versions.toml`

---

### Typical Commit Grouping for This Project

When multiple features are in progress simultaneously, split into logical commits:

**Group 1 — Feature: HEIC/HEIF**
```bash
git add PLAN/spec_heic_heif_support.md
git add app_v2/src/main/java/com/sza/fastmediasorter/core/util/HeifSupportUtils.kt
git add app_v2/src/test/java/com/sza/fastmediasorter/core/util/HeifSupportUtilsTest.kt
# + any modified files whose diff shows only HEIC/HEIF changes
git commit -m "feat: add HEIC/HEIF format support"
```

**Group 2 — Feature: Chromecast**
```bash
git add PLAN/spec_cast-chromecast.md
git add app_v2/src/main/java/com/sza/fastmediasorter/core/cast/
git add app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt
# + manifest, gradle, strings, menu, layout changes related to cast
git commit -m "feat: Chromecast integration with CastMediaManager"
```

**Group 3 — Docs & changelog**
```bash
git add dev/CHANGELOG.md dev/PROJECT_OPERATIONS_INDEX.md
git add docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md docs/TECH_STACK.md
git commit -m "docs: update feature list and changelog for HEIC and Chromecast"
```

**Group 4 — Shared infra changes** (build.gradle, strings, settings, workers)
```bash
# Stage only if the diff for these files contains changes unrelated to the above features
git add app_v2/build.gradle.kts
git commit -m "chore: update dependencies and build config"
```

---

### Quick Reference Card

| Goal | Command |
|------|---------|
| See all changes | `git diff` |
| See one file | `git diff -- path/to/file` |
| Stage file | `git add path/to/file` |
| Stage parts of file | `git add -p path/to/file` |
| Check staged | `git diff --cached` |
| Commit | `git commit -m "message"` |
| Push | `git push` |
| Old file at commit | `git show HASH:path/to/file` |
| File history | `git log --oneline -- path/to/file` |
| Line-by-line blame | `git blame path/to/file` |
| Diff two commits | `git diff HASH1..HASH2` |

