---
name: spec-all-concurrent-red-tree
description: Owner runs /spec-all on sub-specs in parallel with a foreground /spec-dev; the shared src tree can go red from another ticket's half-written file
metadata:
  type: project
---

When the owner spins out sub-specs (e.g. S0348 -> S0349/S0350/S0351/S0353 widget tickets) and runs `/spec-all` on them, those runs execute CONCURRENTLY with whatever `/spec-dev` is doing in the foreground, mutating the SAME `app_v2/src` tree (flavor manifests, new `widget/` classes, FEATURES docs).

**Why:** `/spec-all` is unattended and edits shared files (e.g. `src/lite|photos/AndroidManifest.xml` gained `CaptureOcrPanelWidgetProvider`/`AudioNowPlaying` removals while S0348 owned them; `AudioNowPlayingSnapshotStore.kt` referenced a not-yet-created `AudioNowPlayingWidgetProvider`, making `compileStandardDebugKotlin` fail tree-wide).

**How to apply:**
- A whole-tree build failure during your ticket may be caused by a *different* ticket's in-flight file. Read the `a.ps1 bf` compiler-error paths: if every error is in a class/file you did not touch and that belongs to a sibling sub-spec, it is NOT your regression. Confirm your own code compiled at its last phase gate.
- Do not try to fix the sibling ticket's file (it is actively being written by `/spec-all` and you will conflict).
- Your manifest edits can coexist with the sibling's adjacent `tools:node="remove"` entries - the merger added both; no conflict.
- If finalizing to `BlockNeedUserTest`, state plainly that the device build is gated on the concurrent `/spec-all` tree going green; do not claim a green whole-tree build you could not produce.
- Ticket-log gate variant (seen 2026-07-11, S0960 vs S0961 in the same DiagnosticXrActivity.kt): a sibling session's `Timber.d("Sxxxx:` probe whose spec has not YET reached `BlockNeedUserTest` fails YOUR `post-change.ps1` at `ticket-log-audit` (reported as "stale probe"). Check the sibling id via `select.ps1` + look for its `CODE.LOCK` reason: recent `updated` timestamp / live lock = WIP, NOT stale - do not "remove on sight". Resolution: leave their probe, run the remaining post-change gates directly (`assert-neuroslop`, `assert-flavor-flags-not-growing`, `assert-deprecated-pm-flags`, `assert-listener-symmetry`, `assert-focus-highlight`, `assert-fgs-notifications`, `assert-detekt -ChangedFiles <your file>`), record the cause in the final report. The gate goes green once the sibling flips to BlockNeedUserTest.
