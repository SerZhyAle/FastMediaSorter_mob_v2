---
name: dont-infer-from-buildconfig-names
description: BuildConfig field names can be misleading - some are dead/legacy; grep usage before treating as gate
metadata:
  type: feedback
---

Don't infer routing/dispatch architecture from a `BuildConfig.<NAME>` field name alone. Always grep for actual reads before treating it as a behavioral gate.

**Why:** In S0250 (2026-05-19) I claimed `BuildConfig.PLAYER_ACTIVITY_CLASS` controlled which Activity launches by default, and built an entire argument on top of it. Grep across `src/` showed exactly ONE hit — a KDoc-comment mention in `PlayerActivity.kt:1350`. The field is dead. The actual Activity dispatch is runtime/user-driven. The user caught the error and the spec direction had to be corrected mid-analysis.

**How to apply:**
- Before claiming "flavor X behaves differently because BuildConfig.Y" — run `Grep "BuildConfig.Y" app_v2/src/` and confirm there's a real read site.
- KDoc / comment mentions don't count as a gate.
- Especially suspicious: fields that look like "magic class-name strings" (`PLAYER_ACTIVITY_CLASS`) — those almost never get reflectively instantiated in Kotlin code.
- For Sxxxx-tagged code-archeology: always grep usage of the named symbol before designing the spec around it.
