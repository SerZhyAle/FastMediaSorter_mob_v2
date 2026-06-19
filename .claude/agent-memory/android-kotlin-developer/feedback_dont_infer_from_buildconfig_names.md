---
name: feedback_dont_infer_from_buildconfig_names
description: BuildConfig field names can be misleading - some are dead/legacy; grep usage before treating as gate
metadata:
  type: feedback
---

Don't infer routing/dispatch architecture from a `BuildConfig.<NAME>` field name alone. Always grep for actual reads before treating it as a behavioral gate.

**Why:** In S0250 (2026-05-19) I claimed `BuildConfig.PLAYER_ACTIVITY_CLASS` controlled which Activity launches by default, and built an entire argument on top of it. Grep across `src/` showed exactly ONE hit - a KDoc-comment mention in `PlayerActivity.kt:1350`. The field is dead. The actual Activity dispatch is runtime/user-driven. The user caught the error and the spec direction had to be corrected mid-analysis.

**How to apply:**
- Before writing code that branches on `BuildConfig.<NAME>` or relies on its existence to "select an implementation", run `Grep "BuildConfig.<NAME>" app_v2/src/` and confirm at least one **real read site** (not just KDoc / comments) exists outside `BuildConfig.java`.
- If the only hits are KDoc, dead-code comments, or the field declaration itself, the gate is fictional - go find the actual dispatch path (likely Hilt binding, runtime preference, or user setting).
- Particularly suspicious: fields that look like "magic class-name strings" (`PLAYER_ACTIVITY_CLASS`, `DEFAULT_BACKEND_CLASS`) - those almost never get reflectively instantiated in Kotlin code in this project.
- For code-archeology while implementing a spec: always grep usage of the named symbol before designing the implementation around it - especially before adding a new `if (BuildConfig.X)` branch that mirrors a presumed-existing one.
