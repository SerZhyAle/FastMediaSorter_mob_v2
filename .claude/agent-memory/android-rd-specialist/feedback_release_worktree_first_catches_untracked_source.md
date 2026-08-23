---
name: release-worktree-first-catches-untracked-source
description: A local debug build proves nothing about whether a source file is committed - the release worktree builds from committed state and is the first thing that fails on an untracked file
metadata:
  type: feedback
---

A green local build is **not** evidence that a source file reached version control. The dev checkout compiles
whatever is on disk, tracked or not; the release worktree at `P:/ANDROID/FastMediaSorter_release` is a
separate checkout of `main`, so it only ever sees committed files. That makes it the first place an
untracked source file surfaces - as a bare `Unresolved reference` in a variant that "compiled fine
yesterday".

**Why:** on 2026-08-22 the v033 release build died on `Unresolved reference 'LauncherWidgetToken'` in five
`src/main` files. `fk`, `fw`, `fr` and the whole `/spec-prerelease` sweep had all passed. Root cause was
`.gitignore`, not code: a blanket `*token*` pattern written to pre-empt credential leaks was also matching
`LauncherWidgetToken.kt` and its test, so `git add .` silently skipped them for as long as they had
existed. `a.ps1 c` reports success while adding nothing - an ignored file is not an error to git.

**How to apply:** when a release or worktree build fails on a symbol that resolves locally, suspect
tracking before suspecting code. `git check-ignore -v <path>` names the exact `.gitignore` line, and
`git status --porcelain --ignored=matching app_v2/src wear/src | grep '^!!'` lists every ignored file under
the source trees in one shot - run that whenever a new file lands in a directory whose name could collide
with a secrets pattern. The fix is a negation scoped to code (`!**/src/**/*.kt`, `!**/src/**/*.java`), never
widening it to `*.xml`: real secrets here (`google_oauth.xml`, `msal_config.json`) are XML/JSON under `src`
and are protected by explicit path rules that a broad negation would undo.
