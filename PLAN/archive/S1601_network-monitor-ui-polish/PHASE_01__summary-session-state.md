# Phase 01 - Summary Session State

**Strategic spec:** [`../S1601_network-monitor-ui-polish.md`](../S1601_network-monitor-ui-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

## Objective

Expose an explicit-check external address only in process memory and render the compact active-connection summary.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/ExternalIpSessionStore.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/summary/NetworkMonitorSummaryViewModel.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/summary/NetworkMonitorSummaryFragment.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/sections/InternetSectionViewModel.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout/fragment_network_monitor_summary.xml` | Modified | ≤ 350 |
| `app_v2/src/main/res/layout-land/fragment_network_monitor_summary.xml` | Modified | ≤ 350 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1500 |

## Steps

### Step 01.1 - Share explicit external-address result in memory

**Files:** `ExternalIpSessionStore.kt`, `InternetSectionViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an injectable in-memory state holder for the most recent successful explicit external-IP operation. Update it only after the Internet action succeeds; clear it on unavailable result and do not persist or log its value.

**Why:**

The strategic privacy constraint permits the summary to display an address only after the user explicitly requested it in the current session.

**Verification:**

- `Grep` - `class ExternalIpSessionStore` matches exactly once.
- `Grep` - `ExternalIpResult.Resolved` updates the session state in the Internet view model.
- `Grep` - `SharedPreferences` has zero matches in the new store.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS: session store is in-memory and external resolution updates it without persistence.

### Step 01.2 - Compose summary state with both addresses

**Files:** `NetworkMonitorSummaryViewModel.kt`, `NetworkMonitorSummaryFragment.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Combine the monitor snapshot and session address state. Render one active-connection status string and address text that shows both known addresses or only the available address without an empty label.

**Why:**

The first screen must show the active connection and Internet reachability compactly while keeping a sole mobile address legible.

**Verification:**

- `Grep` - the summary UI state contains an external-address field.
- `Grep` - the fragment no longer renders reachability in a separate summary view.
- `Grep` - `Log.d(` returns zero matches in modified Kotlin files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS: summary state carries external IP and compact status is rendered without a separate reachability view.

### Step 01.3 - Align summary layouts and localized copy

**Files:** portrait and landscape summary layouts; EN, RU and UK strings
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the two-line active status with one text surface and align the address presentation in both orientations. Add only concise localized labels required by the new address format, checking `docs/COMMUNICATION_POLICY.md` sections 2 and 6.

**Why:**

The compact summary is not complete if a rotation restores the old arrangement or a locale falls back to an unrelated label.

**Verification:**

- `Grep` - `networkMonitorActiveInternet` returns zero matches in both summary layouts.
- `Grep` - every new `network_monitor_` key exists in EN, RU and UK string files.
- `Grep` - `scripts/check_strings_localized.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS: both summary layouts use the compact status and the IP-address label is localized in EN, RU and UK.

## Phase Done Criteria

- [ ] Every Step 01.* is `[x] done`.
- [ ] `a.ps1 fc` passes.
- [ ] Phase-boundary audit has no unresolved P0/P1 finding.
