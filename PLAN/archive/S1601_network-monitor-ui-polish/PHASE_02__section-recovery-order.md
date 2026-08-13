# Phase 02 - Section Recovery and Order

**Strategic spec:** [`../S1601_network-monitor-ui-polish.md`](../S1601_network-monitor-ui-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

## Objective

Offer permission recovery beside denied Monitor data and establish one priority order for every detail screen.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/NetworkMonitorPermissionManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/*SectionFragment.kt` | Modified | ≤ 400 each |
| `app_v2/src/main/res/layout/fragment_network_monitor_{wifi,mobile,bluetooth,gnss,internet}.xml` | Modified | ≤ 500 each |
| `app_v2/src/main/res/layout-land/fragment_network_monitor_{wifi,mobile,bluetooth,gnss,internet}.xml` | Modified | ≤ 500 each |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1500 |

## Steps

### Step 02.1 - Add permission recovery manager

**Files:** `NetworkMonitorPermissionManager.kt`, detail fragments
**Depends on:** Phase 01

**Prompt for developer:**

> Register lifecycle-safe runtime permission launchers in each Monitor detail fragment through a shared UI helper. For a requestable denial offer the matching Android dialog and record the request; for a permanent denial open application settings. Re-render after returning to the fragment.

**Why:**

An unavailable section caused by a permission must offer a direct recovery action, while hardware and offline states must not promise an action that cannot help.

**Verification:**

- `Grep` - `canRequestPermission` is used by the permission helper.
- `Grep` - `markPermissionRequested` is used before every launched runtime request.
- `Grep` - `ACTION_APPLICATION_DETAILS_SETTINGS` is the permanent-denial fallback.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS: shared helper uses askability and marker APIs, then opens app settings after permanent denial.

### Step 02.2 - Place recovery controls at the top of eligible sections

**Files:** detail fragments and portrait/landscape layouts
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a visible direct action only for `NoPermission` availability and keep it before the section summary. Hide it for available, no-hardware and no-network states, retaining the written reason in each case. Check new strings against `docs/COMMUNICATION_POLICY.md` sections 2 and 6.

**Why:**

The user must be able to act at the point of failure without mistaking an unavailable device capability for a missing permission.

**Verification:**

- `Grep` - `NoPermission` drives the recovery control visibility.
- `Grep` - `network_monitor_permission_` keys exist in EN, RU and UK.
- `Grep` - `scripts/check_strings_localized.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS: permission recovery is visible only for NoPermission and its two localized actions have EN, RU and UK parity.

### Step 02.3 - Reorder applicable detail cards in both orientations

**Files:** all five portrait and five landscape detail layouts
**Depends on:** Step 02.2

**Prompt for developer:**

> Reorder existing blocks as controls and permission recovery, summary, signal chart, parameters and details, then position. Preserve ids, focus navigation and existing behavior; omit categories a screen does not own rather than adding placeholders.

**Why:**

Detailed parameters currently displace the operational status and graph, making routine diagnosis slower on every orientation.

**Verification:**

- `Grep` - all five portrait and five landscape layouts retain their existing root ids.
- `Grep` - `nextFocus` attributes still target existing ids.
- `Grep` - no modified layout contains a raw hex colour.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - XML parse, root-id and focus-target probes passed for all ten orientation layouts; a.ps1 fc passed (expected: 0 | actual: 0).

## Phase Done Criteria

- [x] Every Step 02.* is `[x] done`.
- [x] `a.ps1 fc` passes.
- [x] Phase-boundary audit has no unresolved P0/P1 finding.
