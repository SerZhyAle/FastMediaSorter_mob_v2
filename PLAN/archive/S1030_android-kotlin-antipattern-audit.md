# S1030 - Android/Kotlin antipattern audit (umbrella)

**Ticket:** S1030
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-13
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Owner request (2026-07-13)

"поискать в интернете общепринятые примеры говнокодинга для андроид (котлин) и на основе найденного произвести аудит/исправления и правила."

---

## 1. Audit result (2026-07-13)

Swept `app_v2/src/main` for the canonical Android/Kotlin antipatterns and cross-checked each against
the project's 24 `assert-*.ps1` gates. **The codebase is already clean on almost every canonical
antipattern** - most are gated (Rule 19 neuroslop family: GlobalScope, non-Timber logging, empty
catch, lifecycle-unsafe Flow collect, hardcoded hex colors, TODO/stub, typographic dashes, deprecated
PM flags, listener asymmetry, dialog-cancel style) and several are simply absent.

Clean (no findings): RxJava/`AsyncTask` residue (none); Fragment view-binding not nulled in
`onDestroyView` (0/34 - all null correctly); risky `lateinit var` holding Context/View past lifecycle
(none - all are Hilt field injection); static/companion Context/View leak (only `FastMediaSorterApp`
holds Application context with `private set` - the safe idiom).

Genuine UNGATED findings -> decomposed into focused child tickets:

- **S1031** - mechanical gate banning public mutable reactive state (`Mutable{StateFlow,LiveData,SharedFlow}`
  without `private`). Only 1 offender today (`WearSyncEvents.kt`); ratchet gate = the "rules" deliverable.
- **S1032** - `!!` non-null-assertion cleanup + ratchet gate (139 occurrences / 90 files; detekt
  `UnsafeCallOnNullableType` currently disabled).
- **S1033** - replace `Thread.sleep()` with `delay()` in SMB retry/backoff (3 sites; needs suspend-seam
  + on-device SMB verification).
- **S1034** - thread-safe date formatting in the logging path (shared `SimpleDateFormat` in
  `FileLoggingTree` + a `@Singleton` use-case; concurrency correctness).

Noted but NOT ticketed: direct `Dispatchers.*` references in the `ui/` layer (439 occurrences) - too
diffuse to gate mechanically and largely follows the project's `viewModelScope.launch(Dispatchers.IO)`
convention; flagged here for awareness only.

---

## 2. Disposition

Audit complete; no direct code change under this umbrella. The four actionable items are ticket-ized
(S1031-S1034) so each gets its own research/execution/verification. Archived as the audit record.

---

## 10. Связи с другими спеками

- Children: S1031, S1032, S1033, S1034.
- S1028 (network-path dedup) - adjacent code-quality consolidation.
