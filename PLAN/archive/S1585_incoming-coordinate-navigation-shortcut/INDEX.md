# S1585 - incoming-coordinate-navigation-shortcut (Tactical Plan)

**Ticket:** S1585
**Strategic spec:** `PLAN/S1585_incoming-coordinate-navigation-shortcut.md`
**Feature:** Route-ready launcher shortcut from a shared Google Maps place
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Module:** app_v2
**Flavors:** standard, noLegal (launcher-carrying only)

---

## Scope

Fix the behaviour shipped by S1175: a share consisting only of a short Maps link produces a
search-style shortcut instead of a route. Resolve the link to coordinates at creation time, then
open turn-by-turn navigation on tap.

---

## Research inputs

None - strategic §6 item 1 resolved by owner decision (2026-08-13), item 2 left Open and explicitly
non-blocking. Redirect shape confirmed live against the owner-supplied link on 2026-08-13:
`https://maps.app.goo.gl/..` answers `302` with `Location: https://www.google.com/maps/place/<lat>,<lon>/data=..`.

---

## Phase Overview

| Phase | Title | Depends on | Produces |
| ----- | ----- | ---------- | -------- |
| 01 | `PHASE_01__link-resolver.md` | - | `MapsShortLinkResolver` seam, `HttpMapsShortLinkResolver`, Hilt binding, unit tests |
| 02 | `PHASE_02__route-intake.md` | 01 | Reordered parse priority, `NAVIGATION` action on tap, resolver wired into intake, unit tests |
| 03 | `PHASE_03__docs-catalog-cleanup.md` | 02 | Catalog sync, dev log, ALL_FEATURES record |

---

## Pre-Implementation Blockers

- [x] Owner decision on failed resolution - answered 2026-08-13: degrade to a place shortcut.
- [x] Owner decision on network wait budget - answered 2026-08-13: 5 seconds.
- [x] UI placement - not applicable: no new screen, layout, or settings surface. Existing strings
      (`launcher_share_place_added_route` / `_added_place`) already cover both outcomes.

None outstanding - implementation may proceed.

---

## Invariants

- No Room schema change: the geographic command keeps its existing `geo:` TEXT encoding.
- No new user-visible strings: both outcomes already have keys in `values/strings.xml`.
- Network access happens only while creating a shortcut, never while tapping one.
- Previously created cells keep working; no migration of existing cells is performed.
