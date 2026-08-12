---
name: trampolines-are-not-rule3-exempt
description: The activity-logic gate counts domain-layer field injection in ANY Activity, trampolines included - and a tactical plan may prescribe the shape that fails it
metadata:
  type: feedback
---

A no-UI trampoline Activity is **not** exempt from Rule 3: injecting domain-layer collaborators (`*UseCase`, `*Repository`) into it fails the mechanical `activity-logic` gate inside `post-change.ps1` with `new domain-layer field injection in an Activity`. Give the trampoline exactly one UI-layer collaborator - a `helpers/*Manager` holding the domain dependencies.

**Why:** observed 2026-08-08 on S1471. The tactical phase file explicitly instructed injecting `GetStreamSourceByUrlUseCase`, `SettingsRepository`, `CapabilityAvailability` and `NetworkContextAnalyzer` straight into `StreamPlayLaunchActivity`. It compiled, every step predicate passed, and `.\a.ps1 fk` was green - the violation surfaced only at closure, after the code was written. Note which collaborators counted: the two domain-layer ones (`+2`), not the `core.*` ones.

**How to apply:**
- Writing or reviewing a trampoline / launcher / widget Activity: put the domain dependencies in a `NounVerbManager` under `ui/<feature>/helpers/` and inject that. Sibling precedent for injecting into a trampoline at all is `CameraQuickCaptureActivity`, but it injects a repository, so precedent is not permission.
- A tactical plan step is not authority against a mechanical gate. When a step's prescribed shape fails a gate, patch the step (record the correction in the phase file, per `/spec-all` "Spec self-correction") rather than arguing the gate down or baselining it.
- The compile gates do not see this. Only `post-change.ps1` does, so a phase that skips closure ships the violation.
