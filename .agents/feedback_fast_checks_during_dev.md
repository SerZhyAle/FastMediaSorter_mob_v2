---
name: fast-checks-during-dev
description: During development default to the fast checkers (a.ps1 fk/fr/fc/fu), not full APK builds; reserve d/dav for packaging/install proof
metadata:
  type: feedback
---

During active development, validate code with the **fast checkers**, not a full APK build. Full `assemble`/packaging is only for when you actually need an installable artifact or packaging/install proof.

**Why:** user explicitly flagged on 2026-06-12 that "it is important to use the fast way to check the code during development". The build system was refactored that day so the default debug path is a fast reusable build and dedicated fast checkers exist; the slow timestamped-APK path moved behind `-AutoVersion`. Fast checks reuse the configuration cache and finish in ~2..8s vs minutes for a full build. He wants this as a standing default for skills/agents, not a per-turn reminder.

**How to apply:**
- Compile-only / symbol changes → `.\a.ps1 fk` (`:app_v2:compileStandardDebugKotlin`).
- Resource / manifest changes → `.\a.ps1 fr` (`processStandardDebugResources`).
- Mixed code + resources → `.\a.ps1 fc`.
- Unit suite → `.\a.ps1 fu` (`testStandardDebugUnitTest`; note pre-existing broken tests, see [[build-pre-existing-test-failures]]).
- Only escalate to `.\a.ps1 d` (fast reusable APK) or `dav` (timestamped artifact) when you need packaging/install/device proof.
- `check-standard-fast.ps1` is the engine behind fk/fr/fc/fu (modes Code/Resources/CodeAndResources/Unit/Assemble); it passes `--configuration-cache` + `-Pchaquopy.enabled=false` so the CC survives repeat runs.
- This mirrors the CLAUDE.md "Validation Ladder" - prefer fk for compile checks, escalate only when the touched area needs packaging proof. See also [[pwsh-efficiency]].
