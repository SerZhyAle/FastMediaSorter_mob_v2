# Phase 03 — device-sideload-test

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** none — independent research phase (parallel with 01 and 02)
**Blocks:** Phase 04 (apply-manifest-fixes)
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Sideload the current production APK on Panasonic MX700, verify whether the app launches and is functional, and capture any runtime incompatibility evidence from the device or Play Store.

---

## Prerequisites

- [ ] Access to Panasonic MX700 with ADB enabled (Settings → About → enable developer mode, then Settings → Device preferences → Developer options → USB debugging / Network debugging). **← MANUAL BLOCKER — requires physical device access.**
- [ ] Current production APK built (or use `standardRelease` / `standardDebug`).

> **Blocker note (2026-05-16):** Phase 03 requires physical access to the Panasonic MX700 TV. All steps are manual — cannot be automated by the build agent. Owner must perform Steps 3.1–3.4, record findings in `temp/panasonic-mx700-sideload-findings.txt`, and flip status to ✅ Done.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| `temp/panasonic-mx700-sideload-findings.txt` | New | Findings document — `temp/` only, not committed |

---

## Steps

### Step 3.1 — Build standardDebug APK for sideload

**Files:** _(build output — `app_v2/build/outputs/apk/standard/debug/`)_
**Depends on:** — start of phase

**Prompt for developer:**

> Run `/build` to produce a `standardDebug` APK. Locate the output APK path in `app_v2/build/outputs/apk/standard/debug/`. This APK will be used for sideload testing on the Panasonic MX700.

**Verification:**

- `Glob` — `app_v2/build/outputs/apk/standard/debug/*.apk` returns at least one file.

**Status:** `[ ]` not done

---

### Step 3.2 — Sideload APK on Panasonic MX700

**Files:** _(device — external)_
**Depends on:** Step 3.1

**Prompt for developer:**

> Connect to Panasonic MX700 via ADB (network or USB):
>
> ```
> adb connect <TV_IP>:5555
> adb devices
> adb install -r path/to/app-standard-debug.apk
> ```
>
> If ADB is unavailable, transfer the APK via USB drive and install using a file manager app on the TV. Record the install result (`Success` or specific error code from ADB).

**Verification:**

- Document: ADB `adb install` result — `Success` or error message recorded.
- Document: app icon appears in TV launcher after install.

**Status:** `[ ]` not done

---

### Step 3.3 — Verify app launch and basic operation on TV

**Files:** _(device — external)_
**Depends on:** Step 3.2

**Prompt for developer:**

> Launch the app from the TV launcher after sideload. Verify:
> (1) App launches without crash (main screen appears).
> (2) Navigation works via TV remote (D-pad / arrow keys).
> (3) Check logcat for any crash or compatibility errors: `adb logcat -s FastMediaSorter:* AndroidRuntime:*`.
>
> If the app crashes on launch, capture the full stack trace and document it in `temp/panasonic-mx700-sideload-findings.txt`.

**Verification:**

- Document: app launched successfully OR crash stack trace captured in `temp/panasonic-mx700-sideload-findings.txt`.

**Status:** `[ ]` not done

---

### Step 3.4 — Check Play Store page for incompatibility message on TV

**Files:** _(device — external)_
**Depends on:** Step 3.2

**Prompt for developer:**

> On the Panasonic MX700, open the Google TV Play Store and search for "Fast Media Sorter" (or `com.sza.fastmediasorter`). Record what the Play Store shows:
> - Option A: App not found in search results.
> - Option B: App found but shows "Not available for your device" with or without a reason.
> - Option C: App found and available for install.
>
> Additionally, on a phone or browser, open:
> `https://play.google.com/store/apps/details?id=com.sza.fastmediasorter`
> Log in with the Google account linked to the Panasonic MX700. If Play Store shows "This app is not compatible with your device" — click the info icon to see the exact incompatibility reason and record it.

**Verification:**

- Document: exact Play Store message / status on MX700 recorded in `temp/panasonic-mx700-sideload-findings.txt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step 3.* above is `[x] done`.
- [ ] `temp/panasonic-mx700-sideload-findings.txt` contains recorded findings for all 4 steps.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "PLAN/S0220_google-tv-availability-research/PHASE_03__device-sideload-test.md" "research" "S0220 Phase 03: device sideload test complete"`.
- [ ] No code changes in this phase — no build besides Step 3.1.

---

## Handoff Notes to Next Phase

If Step 3.4 captures a specific Play Store incompatibility message, that is the highest-priority fix input for Phase 04. Step 3.3 crash evidence (if any) identifies a secondary runtime issue to fix in Phase 04. If the app works perfectly via sideload but is absent from Play Store, the issue is purely Play Console / manifest filtering — Phase 04 focuses exclusively on manifest.

---

## Rollback Plan

No production code changes in this phase. Uninstall sideloaded APK after testing: `adb uninstall com.sza.fastmediasorter`.
