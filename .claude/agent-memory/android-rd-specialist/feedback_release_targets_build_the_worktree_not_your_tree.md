---
name: release-targets-build-the-worktree-not-your-tree
description: a.ps1 r/nl/vr delegate into the release worktree and build ITS tree - your uncommitted dev-checkout edits are invisible to them, and they copy artifacts to DOWNLOADS/Google Drive.
metadata:
  type: feedback
---

Never use `a.ps1 r` / `nl` / `vr` to verify an uncommitted change in the dev checkout: those
targets delegate into `P:\ANDROID\FastMediaSorter_release` (a separate worktree) and build the
tree THERE, then copy the APK/ZIP into DOWNLOADS and the Google Drive sync folder.

**Why:** S1157 (2026-08-21) - a proguard-rules edit in the dev checkout was "verified" with
`a.ps1 nl`; the log path (`P:\ANDROID\FastMediaSorter_release\app_v2\build\..`) revealed the build
was compiling the release worktree's own tree, proving nothing about the edit, while heading for
an artifact copy into the owner's distribution folders. Stopped mid-minify.

**How to apply:** R8/minified-variant proof for a dev-tree change belongs to the release flow
(`/skill-release` / `/spec-prerelease`), not to a working session. If a ticket's acceptance needs
a minified build of the CURRENT tree, the honest move is to park the ticket on the next release
(BlockExternal) with the check written into its note - not to run a release target locally.
`a.ps1 releaseCommands = @('r','nl','vr')` is the authoritative list of delegating targets.
