# S0958 - Dead BuildConfig.PLAYER_ACTIVITY_CLASS field across flavors

**Status:** Archived
**Priority:** 30
**Date:** 2026-07-05
**Tier:** 2 - Small

<!-- parked by S0905 audit sweep (Layer 7) - 2026-07-05 -->
<!-- auto-approved by /spec-all - 2026-07-06 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05, из S0905 Layer 7 R8 sweep.

Symptom: `BuildConfig.PLAYER_ACTIVITY_CLASS` is declared per-flavor but has zero runtime references after the S0241 Phase 03 refactor - only a historical KDoc mention remains. Dead-weight per CLAUDE.md Rule 20.

Evidence:
- Declaration: `app_v2/build.gradle.kts:324` (and mirrored in other flavor blocks).
- Only reference: KDoc note at `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt:1280` (no runtime consumer).

Severity: P3 (hygiene).

Scope note: remove the field from every flavor block in `build.gradle.kts` after confirming no flavor still consumes it; drop the stale KDoc mention.

## 1. Fix (2026-07-06)

- Removed the dead `PLAYER_ACTIVITY_CLASS` `buildConfigField` from all six flavor blocks in `app_v2/build.gradle.kts` (zero runtime references since the S0241 Phase 03/05 refactor). This is the substantive dead-weight - the generated `BuildConfig` field is gone.
- The `createPanelIntent` KDoc in `PlayerActivity.kt` still names `BuildConfig.PLAYER_ACTIVITY_CLASS` as historical context, but it already reads "after S0241 Phase 03 the per-flavor override is gone", so the note is accurate rather than misleading. It was deliberately left untouched: editing that one line surfaces a pre-existing, unrelated `ImportOrdering` stale-baseline finding on `PlayerActivity.kt`'s import block (imports drifted from the frozen detekt signature in an earlier change), and reordering ~90 imports in a 1500-LOC file is disproportionate churn and risk for a P3 dead-field removal.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0905 (audit source), S0241 (Phase 03/05 refactor that made the field dead), CLAUDE.md Rule 20.
- **Build / flavor scope:** removes one unused generated `BuildConfig` String field from every flavor block; no flavor gate, dispatch, or runtime behaviour changes because the field had zero consumers. `BuildConfig` simply regenerates without it.

## Last Audit

**Date:** 2026-07-06
**Verdict:** Verified (static; P3 dead-weight removal)

- **Deadness confirmed.** Repo-wide grep for `PLAYER_ACTIVITY_CLASS` returned only the six `build.gradle.kts` declarations and the one KDoc prose mention (plus historical CHANGELOG/agent-memory notes); no runtime consumer. After the change, the generated `BuildConfig` field no longer exists in any flavor; the sole remaining textual reference is the accurate historical KDoc note.
- **Build.** `.\a.ps1 fc` (re-run after repairing a newline splice from the multi-line removal) configures every flavor block - a broken flavor closure fails configuration - regenerates `BuildConfig` without the field, and compiles standard; PASS proves the six removals are syntactically valid and nothing referenced the field.
- **Gate.** Scoped detekt PASS - no new findings in `build.gradle.kts`.
- **Residual (parked, out of scope).** `PlayerActivity.kt` carries a pre-existing `ImportOrdering` stale-baseline finding (its import block drifted from the frozen detekt signature in an earlier change). Any edit to that file surfaces it in the scoped gate. It is unrelated to this dead-field removal; a `:app_v2:detektBaseline` re-freeze (or an import-order autoformat) is the appropriate maintenance fix, deferred to keep this P3 focused.
