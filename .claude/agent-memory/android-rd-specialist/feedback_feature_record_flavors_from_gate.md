---
name: feature-record-flavors-from-gate
description: ALL_FEATURES flavors must be read off the BuildConfig gate, never copied from a sibling record or left to a default
metadata:
  type: feedback
---

When recording a capability in `docs/ALL_FEATURES.jsonl`, derive `flavors` from the **actual gate** - the `BuildConfig` flag in `app_v2/build.gradle.kts`, or the source set the code lives in. Never copy a sibling record, never accept a tool default.

**Why:** the field is a factual claim about which builds ship the feature, and `/skill-release` builds the public showcase (`docs/FEATURES*.md`) from the inventory diff - a wrong list puts a feature in the wrong build's showcase. Nothing downstream catches it: `validate.ps1` accepts any legal flavor list, and `["standard"]` is legal and usually plausible. Cost so far, both on 2026-07-16:
- S1066: the phase file claimed `standard,noLegal,vr`; the camera package has no gate at all and lives in `src/main`, so the truth was `standard,lite,photos,legacy`.
- S1061: `close-and-log` defaulted the record to `["standard"]`; `SUPPORT_STREAMS` is true in `standard,legacy,noLegal,vr` and false in `lite,photos`. Caught only by reading the record back.

Sibling records are not a shortcut: for the same streams panel, S0777 records `standard,legacy,noLegal` while S0782 records `standard,legacy,noLegal,vr`. At least one is wrong, so copying either spreads the error.

Repeated twice more on 2026-07-28, both times by reasoning about the feature instead of reading the gate, both caught only by reading the record back:
- S1220: the slicers live in `src/main`, so I wrote all five flavors. `SUPPORT_STREAMS` is false in `lite`/`photos` and true in `vr` - "lives in `src/main`" does **not** mean "every flavor ships it".
- S1229: stereo detection sounds VR-only, and `SUPPORT_VR_PLAYER` is true only in `noLegal`, so I wrote a narrow list. `AppSettings.kt` says the opposite in a comment right above the fields: "Detection-source flags below are flavor-independent (flat stereo exists on every flavor)" - the answer was **all six**.

So the gate is not always a `BuildConfig` field. It can be a comment stating the intended scope next to the setting, or the capability wrapper the code actually calls. Find the one that governs *this* code path.

**How to apply:** before writing any capability record, grep the gate (`grep -n SUPPORT_<X> app_v2/build.gradle.kts`, or find the `BuildConfig.` read behind the capability wrapper) and map the flag to flavor names. If the code sits in `src/main` with no gate, every flavor compiling `src/main` ships it. Then read the written record back - `grep '"spec":"Sxxxx"' docs/ALL_FEATURES.jsonl` - and check `area`, `name` and `flavors` with your own eyes. See [[capability-inventory-all-features]].
