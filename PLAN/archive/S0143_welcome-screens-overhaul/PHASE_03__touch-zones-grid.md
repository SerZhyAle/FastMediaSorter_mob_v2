# Phase 03 — Touch-Zones Page: Inline 3×3 Grid Replaces the Bitmap Scheme

**Strategic spec:** [`../S0143_welcome-screens-overhaul.md`](../S0143_welcome-screens-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Replace the outdated `touch_zones_scheme.png` with an inline, fully scalable 3×3 labelled grid built in the page layout, give the page the compact-header + scrollable-details structure (portrait + new landscape variant), and delete the now-unreferenced bitmap.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (string keys `welcome_description_3_details` exists; `tvDetails` convention established).
- [ ] Current player touch-zone behaviour confirmed (which of the 9 zones map to which actions) so the cell labels are accurate.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/page_welcome_touch_zones.xml` | Modified | ≤ 180 |
| `app_v2/src/main/res/layout-land/page_welcome_touch_zones.xml` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 500 |

> Landscape variant `layout-land/page_welcome_touch_zones.xml` was **absent** — created in Step 03.2.
> Deviation: no new `welcome_touch_zone_*` strings (reuse existing `touch_zone_*`); `touch_zones_scheme.png` not deleted (still used by Settings → Playback).

---

## Steps

### Step 03.1 — Reuse the existing touch-zone label strings (no new strings)

**Files:** none — verification only

**Depends on:** — start of phase

**Prompt for developer:**

> The repo already ships short, fully-localized labels for all nine zones: `touch_zone_back`, `touch_zone_copy`, `touch_zone_rename`, `touch_zone_previous`, `touch_zone_move`, `touch_zone_next`, `touch_zone_command_panel`, `touch_zone_delete`, `touch_zone_slideshow` (3×3 order: BACK/COPY/RENAME · PREVIOUS/MOVE/NEXT · COMMAND PANEL/DELETE/SLIDESHOW — matches `touch_zone_1_back` .. `touch_zone_9_slideshow`). Do **not** create duplicate `welcome_touch_zone_*` keys; the grid layout in Step 03.2 references these existing strings directly. (Deviation from the original step 03.1: replaced "add 9 new keys" with "reuse 9 existing keys" — DRY, already localized.)

**Verification:**

- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "touch_zone_"` — exit code 0 (all existing keys present in EN/RU/UK).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (touch_zone_ → 20/20 EN/RU/UK, exit 0). Deviation noted: reuse existing `touch_zone_*` labels instead of adding `welcome_touch_zone_*` duplicates. No files changed.

---

### Step 03.2 — Rebuild the touch-zones page layout (portrait + landscape)

**Files:** `app_v2/src/main/res/layout/page_welcome_touch_zones.xml`, `app_v2/src/main/res/layout-land/page_welcome_touch_zones.xml`

**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `ivTouchZonesScheme` `ImageView` with an inline `GridLayout` (`@+id/gridTouchZones`, `columnCount="3"`, `rowCount="3"`) of nine equal cells; each cell is a small bordered/tinted block showing its zone label (`welcome_touch_zone_*`) and, optionally, a tiny direction/action icon. The grid keeps a roughly square aspect and is centred. Page structure: fixed header (`tvTitle`) + the `gridTouchZones` block + a `ScrollView` containing `@+id/tvDetails` (text from `welcome_description_3_details`) — the page itself never scrolls. Create the landscape variant `layout-land/page_welcome_touch_zones.xml` mirroring this with a two-column arrangement (grid on one side, header+details scroll on the other). Keep ids `tvTitle`, `tvDescription` if still used, add `gridTouchZones` and `tvDetails`; remove `ivTouchZonesScheme`.

**Verification:**

- `Grep -n "@+id/gridTouchZones"` in `layout/page_welcome_touch_zones.xml` — exactly once; same in `layout-land/page_welcome_touch_zones.xml`.
- `Grep -n "ivTouchZonesScheme"` in `layout/page_welcome_touch_zones.xml` — zero hits; same in the landscape file.
- `Grep -n "@+id/tvDetails"` in both files — exactly once each.
- `Glob` — `app_v2/src/main/res/layout-land/page_welcome_touch_zones.xml` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (gridTouchZones 1, ivTouchZonesScheme 0, tvDetails 1 in portrait + new landscape; land file exists). 3×3 GridLayout of MaterialCardView cells using existing touch_zone_* labels; cell attrs inlined (no new styles). Dev log recorded.

---

### Step 03.3 — Update view holder, wire page-3 details

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Depends on:** Step 03.2

**Prompt for developer:**

> In `TouchZonesViewHolder.bind()`: drop the `ivTouchZonesScheme` and `tvDescription` references and their `animateEntrance` calls (the new layout has neither); bind `tvDetails` from `page.detailDescriptionRes` (visibility rule as in Phase 02); keep the title binding; add `animateEntrance(binding.gridTouchZones, ...)`. In `WelcomeActivity.setupViewPager()` set `detailDescriptionRes = R.string.welcome_description_3_details` on the Touch Zones page entry. Add `Timber.d("S0143: touch-zones grid page bound")` once in `TouchZonesViewHolder.bind()`. (Deviation from the original step: `touch_zones_scheme.png` is **not** deleted — it is still referenced by `fragment_settings_playback.xml` in Settings → Playback, which is outside S0143's scope; only the Welcome page stops using it.)

**Verification:**

- `Grep -n "touch_zones_scheme" ` in `app_v2/src/main/res/layout/page_welcome_touch_zones.xml` and `layout-land/page_welcome_touch_zones.xml` — zero hits in each.
- `Grep -n "ivTouchZonesScheme"` in `WelcomePagerAdapter.kt` — zero hits.
- `Grep -n "tvDescription"` in `WelcomePagerAdapter.kt` `TouchZonesViewHolder` — the holder no longer references it (file-level grep may still show it for other holders; verify the TouchZonesViewHolder block specifically).
- `Grep -n "welcome_description_3_details"` in `WelcomeActivity.kt` — at least one hit.
- `Grep -n "Timber.d(\"S0143:"` in `WelcomePagerAdapter.kt` — at least one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (adapter ivTouchZonesScheme 0, Timber S0143 2, Log.d 0; activity welcome_description_3_details 1; welcome touch-zones layouts touch_zones_scheme 0; TouchZonesViewHolder binds tvTitle+tvDetails+gridTouchZones, no tvDescription/ivTouchZonesScheme). touch_zones_scheme.png kept (used by Settings → Playback). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — verified by the combined `build-debug.PS1` run after Phase 04 (Phases 03 & 04 touch the same files; one build covers both).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "touch_zone_"` exits 0 (deviation: reused existing keys instead of new `welcome_touch_zone_*`).
- [x] Dev log entry added for every file in "Files Touched" (no deletion — `touch_zones_scheme.png` kept).
- [x] No public API change in `WelcomePagerAdapter` — catalog regen deferred to Phase 05.

---

## Handoff Notes to Next Phase

- The inline-grid pattern (`GridLayout` populated from string labels, no bitmap) is the model the "powerful extras" page should follow for its block grid in Phase 04.
- No bitmap onboarding illustrations remain except `resource_types.png` — Phase 04 audits/replaces it.

---

## Rollback Plan

Revert phase commit(s); restore `touch_zones_scheme.png` from VCS history if the previous layout is reinstated. No persisted state touched.
