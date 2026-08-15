---
name: sandbox-tests-carry-a-dependency-manifest
description: Adding a dot-source to a script silently breaks its sandbox-based Pester tests until the sandbox's copy list names the new library
metadata:
  type: feedback
---

A repo-script test that builds a throwaway sandbox copies its subject's library dependencies **by
name**, one `Copy-Item` per lib. Add a `. (Join-Path ... 'newlib.ps1')` to the script under test and
every case fails at load until that lib is added to the same copy list.

**Why:** measured on S1544. `scripts/quality.tests/set-android-string-remove.Tests.ps1` went 16/0 ->
6/10 the moment `set-android-string.ps1` gained one dot-source, and the failures read as unrelated
behaviour breakage ("both dead keys removed | expected: 0 | actual: 2"), not as a missing file. The
sandbox exists precisely so the tool resolves paths from its own location, which is what makes the
omission invisible: the script is found, its dependency is not, and the process dies with exit 1
before any test logic runs.

**How to apply:** when a change adds a dot-source, an import or any new file dependency to a script
that has tests, grep those tests for `Copy-Item` and add the new file to the manifest in the same
change. When a test suite fails right after such a change, baseline it first - swap the pre-change
copy back in and re-run - before debugging the failures as logic bugs; the delta tells you instantly
whether it is a manifest gap or a real regression. Related: [[feedback_build_pre_existing_test_failures]].
