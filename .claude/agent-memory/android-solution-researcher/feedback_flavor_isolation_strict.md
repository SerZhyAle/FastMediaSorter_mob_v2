---
name: flavor-isolation-strict
description: When researching flavor-gated behaviour, expect new code under src/<flavor>/java/; a BuildConfig.SUPPORT_* guard in src/main is legacy tech debt - report it as such, not as design
metadata:
  type: feedback
---

When researching flavor-gated behaviour (VR / noLegal / lite / photos / legacy), the canonical pattern is: interface in `src/main/java/`, No-Op default in `src/main/` (or `src/standard/`), real impl in `src/<flavor>/java/`, Hilt binding in a flavor-local `@Module`. CLAUDE.md Rule 15 forbids any new `BuildConfig.SUPPORT_*` / `BuildConfig.IS_*` flavor guard inside `src/main/java/`.

**Why:** Audit on 2026-05-14 found 169 such guards across 45 files in `src/main/` (24 VR/noLegal-sensitive). These compile, but they represent the wrong direction: the codebase is migrating away from `if (BuildConfig.SUPPORT_VR_PLAYER)` toward DI-bound flavor source sets. A research report that describes a legacy gate as "the design" misleads the spec author into reproducing the antipattern.

**How to apply:**
- When grepping for flavor logic, expect the real code under `src/<flavor>/java/`. Cite that path in the Current Architecture table.
- When a `BuildConfig.<flavor-flag>` reference is found inside `src/main/java/`, do NOT describe it as the intended design in the report. Cite it under "Risks Identified" with severity Low (existing tech debt) and note that CLAUDE.md Rule 15 forbids new instances.
- When citing the canonical pattern, point to one of these on-disk references: `src/vr/java/.../vr/di/VrModule.kt`, `src/noLegal/java/.../di/NoLegalLinkDownloadModule.kt`, `src/main/java/.../ui/player/entry/VrTaskTransition.kt` (transitional no-op shim).
- The valid flavor set for catalog citation is `standard,lite,photos,legacy,vr,noLegal` (vrUnlicensed archived in S0250 - see [[vr-inclusion-hierarchy]]).
- A pseudo-`vr/render` package leaking into `src/main` is tracked separately (see spec history); cite the leak when found but do not propose a fix - that is a writer-agent decision.
- Layout/string/manifest assets for a flavor live under `src/<flavor>/res/` or `src/<flavor>/AndroidManifest.xml`. A flavor-only drawable found in `src/main/res/` (e.g. `ic_vr_*.xml`) is misplaced - report it under Risks.
