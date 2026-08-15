# FastMediaSorter v2 - Product Complexity Assessment

**Snapshot Date**: July 17, 2026
**Audience**: Developer / product owner
**Purpose**: A compact current-state estimate of product implementation complexity based on the checked-in codebase and documented product surface.

## 1. Scope and Method

This assessment is based on the current repository snapshot in `DEBUG-v026`.

- Counts were taken from the live workspace on July 17, 2026.
- Kotlin LOC counts include all checked-in app source sets under `app_v2/src` and `wear/src`.
- Activity / Fragment counts use class-name pattern matching across Kotlin sources.
- Dialog-related metrics combine dialog / bottom-sheet classes, dialog-related XML layouts, and dialog signal lines in Kotlin/XML.
- The person-hour estimate is heuristic. It is **not** a timesheet reconstruction.

## 2. Current Size Snapshot

| Metric | Value | Notes |
|:--|--:|:--|
| Kotlin source files | 2,360 | All checked-in Kotlin under `app_v2/src` and `wear/src` |
| XML files | 934 | Layouts, manifests, menus, drawables, XML config |
| Kotlin LOC | 342,695 | Current repository snapshot |
| Production Kotlin files | 1,956 | Excludes `test` and `androidTest` |
| Production Kotlin LOC | 272,129 | Excludes `test` and `androidTest` |
| Activities | 55 | Pattern count of `*Activity` classes |
| Fragments | 36 | Pattern count of `*Fragment` classes |
| Dialog / bottom-sheet classes | 62 | Pattern count of dialog-bearing classes |
| Dialog-related XML files | 135 | Files whose names/content indicate dialog UI |
| Dialog signal lines | 868 | Lines matching dialog builders, message/title setters, confirm/cancel styles, or dialog controls |
| Dialog-bearing files | 199 | Kotlin/XML files containing dialog-related signals |
| Room DB version | 41 | Assessment baseline captured for this report |

## 3. Complexity Reading

FastMediaSorter v2 is no longer a small utility app. By current implementation shape it sits in the **large Android product** bracket:

- Multi-module surface: phone/tablet app plus Wear OS companion.
- Large feature breadth: local media, network protocols, cloud providers, documents, OCR, translation, widgets, automation, streams, VR/noLegal, and in-progress launcher mode.
- Broad UI surface: many screens, multiple player hosts, settings areas, and a high dialog density.
- Persistent data model: the report's Room schema version-41 baseline indicates long-running feature evolution and migration burden.
- Flavor and device branching: Standard / Lite / Photos / Legacy plus XR-specific source sets and capability gating.

## 4. Indicative Person-Hour Estimate

Estimated implementation effort for the currently visible Android product surface:

| Estimate band | Person-hours | Interpretation |
|:--|--:|:--|
| Conservative floor | 7,500 | Reuse-heavy implementation, limited refactoring counted |
| Most realistic range | 9,000 - 12,000 | Current best estimate for shipped product scope |
| Full lifecycle upper band | 14,000+ | Includes rework, release hardening, migration churn, QA/documentation drag |

What drives the estimate upward:

- Very high feature breadth for a mobile app.
- Multiple protocol stacks and cloud integrations.
- Custom player and document-viewer behavior.
- XR / VR branch complexity.
- Long-lived schema evolution.
- Heavy settings, dialog, and widget surface.

## 5. Practical Conclusion

For planning purposes, FastMediaSorter v2 should be treated as:

- a large Android codebase,
- a feature-rich product rather than a single-purpose app,
- and a product where feature work often carries cross-cutting cost in docs, flavor gates, migrations, and UI parity.

For product-history context, see `docs/PRODUCT_HISTORY.md`.
