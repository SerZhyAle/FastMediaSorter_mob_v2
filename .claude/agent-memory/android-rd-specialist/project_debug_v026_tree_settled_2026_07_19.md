---
name: debug-v026-tree-settled-2026-07-19
description: DEBUG-v026's ~220-file multi-epic WIP was committed (23 commits) on 2026-07-19 - what landed, what's still open, what to check next session
type: project
---

On 2026-07-19, on explicit owner request ("сначала закоммитить и устаканить дерево"), the
long-uncommitted `DEBUG-v026` working tree (~133 modified + ~90 untracked, accumulated
2026-07-17..19 across launcher/player/streams/docs epics) was organized into 23 commits and
landed locally (not pushed). Full breakdown: `dev/CHANGELOG.md` history + `git log --oneline`
from `3b0f751b..b600a230`.

**Why:** most of the deferred `/spec-next` backlog that session (S1009 schema-drift, S1060
libVLC, S1114/S1115 player-controls, the whole launcher-roadmap family) was blocked specifically
on this WIP landing - not on any remaining research or implementation work.

**How to apply:**
- The tree builds clean post-settle (`a.ps1 dq` BUILD SUCCESSFUL). S0404 (launcher epic) is
  `Verified` in the catalog and its code is now committed - S1009/S1114/S1115/launcher-family
  tickets that were skip-cached as "concurrent-wip" are very likely unblocked now; re-verify
  their skip-cache reasons before assuming, per [[dirty-tree-is-normal-wip]].
- `corex/androidx/core/content/ContextCompat.java` was deliberately left **uncommitted and
  untracked** - a vendored AndroidX file with zero code references and zero changelog mention.
  Flag it to the owner (delete, or explain why it exists) before it resurfaces as WIP again.
- Two known-incomplete items landed honestly labeled, not silently as "done": S1107 (onboarding
  HOME-role request) is committed but device-verified **Broken** (first-run Settings recreation
  storm); S1083 (playback-control dialog) landed phases 1-2 of 3, phase 3 (device-gated color
  lifecycle) intentionally not implemented.
- The R8 proguard-rules.pro hardening (commit `b4106200`) still needs verification on a
  **minified** release/target build per CLAUDE.md Rule 13 - not done as of this commit, only
  the source change landed.
- Nothing was pushed to `origin/DEBUG-v026` - local commits only, by design (kept as a smaller
  separate step).
- **Update 2026-07-20:** working branch is now `DEBUG-v027` (further commits landed since the v026
  settle; dev-log/post-change stamp `[branch: DEBUG-v027]`). A `/spec-next` loop this day cleanly
  touched `AppLaunchPanel*` (S1124 tile-icon tint) and the stream-player path (`StreamPlaybackHelper`
  / `VideoPlayerLifecycleHelper`, S1127) with no concurrent-wip collision - those launcher/player
  surfaces read as settled now. The many 2026-07-18 concurrent-wip skip-cache entries (launcher
  family S1087-S1103, player-controls S1083/S1114/S1115) are likely stale; the owner can
  `/spec-next --reset-skips` to force re-evaluation rather than wait for the 7-day TTL.
- Working method worth repeating for a similarly large future settle: `git status --porcelain
  -uall` + `git diff --stat` alone was NOT enough to group files correctly - the decisive move
  was reading `dev/CHANGELOG.md`'s own diff in full (it is a dated, ticket-tagged log of nearly
  every change already) and cross-checking file-by-file after each batch of commits, which
  caught 6 real misattributions/omissions the initial plan missed (S1081's layout-swap
  ecosystem, a reindex-settings feature, streams source-spec handoff docs, a camera-layout id
  pairing, a mis-filed onboarding fragment, and a second-pass doc refinement from a still-
  running background sweep). Trusting the first pass's file lists without re-deriving from
  `git status` after every few commits would have silently dropped or misfiled several of these.
