---
name: visibility-condition-is-not-the-action
description: A control's visibility condition says nothing about what its handler does - read the handler before describing behaviour
metadata:
  type: feedback
---

Never infer what a UI control *does* from the condition that decides whether it is *shown*. Read the click handler.

**Why:** on 2026-08-15 (S1675) I wrote into a spec, and told the owner, that the camera's lens-switch button "only toggles front and rear, so there is no way back to the main rear lens". I had read its visibility condition - `canSwitchLens = availableLensFacings.size > 1`, which really is about facings - and stopped there. Its actual handler, `CameraCaptureSessionManager.switchCamera()`, cycles the **whole** lens list including the rear sub-lenses, and has since S0753. The defect was real but its shape was wrong: not "no way back" but "no predictable way back". A `/spec-quiz` correction had to undo my paragraph.

**How to apply:** when a spec claims "the only control here does X", open the handler and confirm X. Visibility, enablement and action are three separate predicates in this codebase and they routinely disagree - `canSwitchLens` gates on facings while `switchCamera()` walks lenses. The same caution applies to a control whose label implies a scope its handler does not honour.

Related: [[documented-invariant-is-a-claim]] and [[resolved-research-item-may-be-inference]] - same failure family, an assertion carried forward without being measured.
