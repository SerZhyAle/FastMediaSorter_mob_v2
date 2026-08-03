---
name: no-redundant-flavor-compile
description: Don't run noLegal/other-flavor compile (fkn) for src/main-only changes; standard fc already proves it
metadata:
  type: feedback
---

For a change that lives entirely in `src/main/` with no flavor source-set edit and no `BuildConfig.IS_*`/flavor-gated code path, do NOT run a second per-flavor compile (`a.ps1 fkn` noLegal, etc). Standard `a.ps1 fc` is sufficient proof.

**Why:** All flavors compile the SAME `src/main` Kotlin - flavors differ only in `BuildConfig` fields, resources, and `src/<flavor>/` source sets. If standard compiles shared `src/main` code, noLegal compiles byte-identical source and can reveal nothing new. Owner pushed back sharply (2026-07-01) on running `fkn` for S0808/S0809/S0810 - all pure `src/main` streams-panel/menu work - calling it obvious wasted work. Ties to the broader "stop over-verifying" signal (cf. the redundant-check habit).

**How to apply:** After a `src/main`-only edit, run `fc` (standard) and stop. Reach for `fkn`/other-flavor builds ONLY when the diff touches `src/<flavor>/java|res/`, adds/removes a flavor-gated path, changes a dependency that a flavor minifies differently, or deletes something a specific flavor depends on. "Streams/feature X also ships in noLegal" is NOT a reason - the source is shared. See [[flavor-isolation-strict]].
