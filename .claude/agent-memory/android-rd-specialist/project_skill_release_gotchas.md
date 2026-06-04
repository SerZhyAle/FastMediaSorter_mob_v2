---
name: skill-release-gotchas
description: /skill-release operational traps - version skew (tag vs build artifact), DEBUG-not-rebased merge conflicts, gitignored PLAN breaks Step 12a git-diff
metadata:
  type: project
---

Operational traps hit while running `/skill-release` (plateau release pipeline). See also [[project_build_gotchas]].

**1. Version skew: tag/notes version != built artifact version.**
- `/skill-release` computes `$NEW_VERSION` once at Step 2 (start of pipeline) and uses it for the git tag + WHATS_NEW/README.
- `a.ps1 r` (Step 12) re-runs `build-with-version.ps1`, which stamps a FRESH build-time version. So the AAB/APK that ships to Play has a LATER version than the tag (skew = pipeline wall-clock, ~20 min observed: tag `.424` vs artifact `.446`).
- Plus the merge-commit's committed `build.gradle.kts` version is whatever you picked during conflict resolution (a third value, `.408`).
- **Why:** the skill never passes the Step-2 version into `a.ps1 r`; the build owns its own timestamp.
- **How to apply:** the artifact version is the store-truth. Surface the skew as a follow-up; do NOT silently rewrite the pushed tag (operator decides). Consider improving the skill to pass a fixed version to the build.

**2. Merge conflict from DEBUG branch not rebased after a mid-cycle main rebuild.**
- The git model says: after a fix-release / `.NNN` rebuild commits directly to `main`, rebase all live DEBUG branches onto updated `main`. This step gets skipped.
- Result: at Step 8 `git merge --no-ff DEBUG-vNNN`, conflicts appear in exactly the release-doc/version files both sides touched: `README.md`, `app_v2/build.gradle.kts`, `dev/CHANGELOG.md`, `docs/WHATS_NEW*.md`. Everything else auto-merges.
- **How to apply:** this is a HARD BLOCKER per the skill (ABORT). Diagnose with `git log DEBUG-vNNN..main` (commits on main not in DEBUG). Resolution that worked: README -> theirs (new version supersedes); WHATS_NEW* -> `checkout --ours` (main's correct published version + full history) then re-prepend the new block with baseline = main's last published version; build.gradle.kts version is throwaway (build re-stamps); CHANGELOG -> union both sides (it is oldest-at-top, so put main's block first to keep the newest entry at the bottom). Strip only the 3 conflict-marker lines by exact line number (re-grep them first; `checkout -m -- <file>` regenerates markers if you botch it).

**3. PLAN/ is gitignored -> Step 12a git-diff is always empty.**
- Step 12a tells you to `git diff $PREV_TAG..HEAD -- PLAN/spec-catalog.jsonl`. `.gitignore` has `PLAN/`, so this yields nothing - spec catalog + spec files are local-only.
- **How to apply:** do the func-log cross-check against the LOCAL working copy instead: parse `PLAN/spec-catalog.jsonl`, filter `updated >= <plateau start date>` and status in Verified/BlockNeedUserTest/Implemented, then grep `dev/FUNCTIONALITY.log` for `[Sxxxx]`. BlockNeedUserTest specs legitimately may lack a func-log entry (entry is added at verification), so most "misses" are not real.
