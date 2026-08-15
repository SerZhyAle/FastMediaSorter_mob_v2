# Phase 04 - Network section

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add the Network section: transport type, metered, VPN, airplane mode, and local IP address of active interface(s) — using only the already-declared NORMAL permissions.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` already declared in `app_v2/src/main/AndroidManifest.xml` (verified present at authoring time - no manifest edit expected).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> If the use case projects >500 lines after edit, create a timestamped backup in `temp/` first.

---

## Steps

### Step 04.1 - Add Network section

**Files:** `GatherSystemInfoUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a Network section using `ConnectivityManager.getNetworkCapabilities(activeNetwork)`: transport type (Wi-Fi / Cellular / Ethernet / VPN / none via `hasTransport`), metered (`isActiveNetworkMetered`), VPN active (`hasTransport(TRANSPORT_VPN)` or absence of `NET_CAPABILITY_NOT_VPN`), airplane mode (`Settings.Global.getInt(cr, AIRPLANE_MODE_ON)`), and local IP address(es) from `NetworkInterface.getNetworkInterfaces()` for up, non-loopback interfaces (site-local / IPv4 first). Do NOT read SSID, BSSID, or MAC. No dangerous permission. Defensive `safeList`. If no active network, show `none`/`unknown`.

**Verification:**

- `Grep` - `getNetworkCapabilities` and `NetworkInterface` present.
- `Grep` - no `getSSID`, no `getBSSID`, no `getMacAddress` in the file.
- `Grep` - `sysinfo_section_network` referenced.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS (getNetworkCapabilities=1, NetworkInterface=2, section_network=1, getSSID/getBSSID/getMacAddress=0, Log.d=0). Compile gate at phase end.

---

### Step 04.2 - Add localized strings for Network (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `sysinfo_section_network` header and field labels (transport, metered, vpn, airplane mode, ip address). Real EN/RU/UK values; Author Style; §6 tone checklist.

**Verification:**

- `Grep` - `sysinfo_section_network` present in each of the three files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sysinfo_"` exits 0 (expected 0 | actual record).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. net field keys EN/RU/UK EXIT=0 (expected 0 | actual 0); section_network added. §6 pass. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] No forbidden identifier API (`getSSID`/`getBSSID`/`getMacAddress`) in the use case.
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Network section established with NORMAL-permission-only reads; no identifiers collected.

---

## Rollback Plan

Revert phase commit(s) - additive section only.
