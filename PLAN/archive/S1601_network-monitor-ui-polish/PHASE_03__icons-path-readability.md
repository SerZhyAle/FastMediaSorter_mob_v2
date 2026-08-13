# Phase 03 - Icons and Path Readability

**Strategic spec:** [`../S1601_network-monitor-ui-polish.md`](../S1601_network-monitor-ui-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

## Objective

Use familiar network symbols and make Internet path nodes readable as single-line values.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|------------:|
| `app_v2/src/main/res/drawable/ic_network_monitor.xml` | Modified | ≤ 80 |
| `app_v2/src/main/res/drawable/ic_network_*.xml` | New / Modified | ≤ 80 each |
| `app_v2/src/main/res/layout/fragment_network_monitor_summary.xml` | Modified | ≤ 350 |
| `app_v2/src/main/res/layout-land/fragment_network_monitor_summary.xml` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/NetworkPathDiagramView.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/InternetSectionFragment.kt` | Modified | ≤ 400 |

## Steps

### Step 03.1 - Apply conventional symbols to Monitor entry and tiles

**Files:** monitor and network-icon drawables; summary layouts
**Depends on:** Phase 02

**Prompt for developer:**

> Replace the steering-wheel-like Monitor vector with a conventional network-monitor symbol and add conventional Wi-Fi, mobile, Bluetooth, location, Internet and history symbols to the corresponding summary tiles. Preserve visible text labels and content descriptions.

**Why:**

The Monitor must be recognisable at a glance without making icons the sole carrier of meaning.

**Verification:**

- `Grep` - each summary tile references an `ic_` drawable.
- `Grep` - `ic_network_monitor.xml` no longer contains the prior circular steering-wheel path.
- `Grep` - every interactive tile retains a text title.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Six labelled tiles in each orientation reference conventional icons; the former Monitor path is absent. Path nodes now use one normal-size ellipsized draw call while the full fragment content description and vertical fallback remain.

### Step 03.2 - Render path nodes as one normal-size line

**Files:** `NetworkPathDiagramView.kt`, `InternetSectionFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Combine each path node's label and value into one ellipsized text line at normal size. Preserve the vertical topology fallback and full content description so narrow displays and TalkBack retain all diagnostic information.

**Why:**

The path diagram is a diagnostic aid only if its inner text remains readable rather than shrinking into two tiny lines.

**Verification:**

- `Grep` - the path view draws one text line per node.
- `Grep` - `contentDescription` still joins every path node.
- `Grep` - path layout still selects a vertical fallback below its width threshold.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Six labelled tiles in each orientation reference conventional icons; the former Monitor path is absent. Path nodes now use one normal-size ellipsized draw call while the full fragment content description and vertical fallback remain.

### Step 03.3 - Validate resource and accessibility integration

**Files:** all Phase 03 files
**Depends on:** Step 03.2

**Prompt for developer:**

> Run resource compilation and inspect both orientations to ensure icons, labels, focus order and path content descriptions remain intact.

**Why:**

Icons and custom drawing cross resource and accessibility boundaries that compilation alone cannot express.

**Verification:**

- `a.ps1 fr` exits 0.
- `Grep` - `android:contentDescription` remains on `internetPathDiagram`.
- `Grep` - every summary tile remains `focusable` and `clickable` through its style.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - a.ps1 fr passed (expected: 0 | actual: 0); both Internet layouts retain internetPathDiagram content descriptions and all six clickable, focusable tile cards retain text titles beside their decorative icons.

## Phase Done Criteria

- [x] Every Step 03.* is `[x] done`.
- [x] `a.ps1 fc` passes.
- [x] Phase-boundary audit has no unresolved P0/P1 finding.
