# Phase 02 - Prominent disclosure gate

**Strategic spec:** [`../S0671_standard-mediaprojection-capture-suite.md`](../S0671_standard-mediaprojection-capture-suite.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-24
**Completed:** 2026-06-25

---

## Objective

Show an in-app prominent disclosure with affirmative consent before the first MediaProjection system consent request, treating captured screen content as personal/sensitive data. Persist acceptance so it gates the first capture, then the recurring system dialog handles per-session consent. Covers both the menu path and the future gesture path (shared consent activity).

---

## Prerequisites

- [x] Phase 01 ✅ Done (capture suite mounts in `standard`).
- [x] Strategic §6 item 1 (MediaProjection policy) Resolved - see `research/01__mediaprojection-play-policy.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +6 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +6 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +6 keys |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt` | Modified | ≤ 500 |

> The disclosure-accepted flag reuses the existing settings persistence (the same `ScreenshotSettingsStore` that already holds `copyScreenshotToClipboard`). Do NOT add a new DataStore/Room surface - extend the existing settings model.

---

## Steps

### Step 02.1 - Add a persisted "capture disclosure accepted" flag to settings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a boolean `screenCaptureDisclosureAccepted` (default `false`) to `AppSettings` and persist it in `ScreenshotSettingsStore`, mirroring exactly how `copyScreenshotToClipboard` is modelled and stored. Provide read + write through the existing settings flow (no new DataStore/Room surface). Grep `copyScreenshotToClipboard` in both files to mirror the pattern.

**Verification:**

- `Grep` - `screenCaptureDisclosureAccepted` matches in `AppSettings.kt` and `ScreenshotSettingsStore.kt`.
- `Grep -n "Log\.d\("` - zero hits in modified files.
- `/build` - `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

### Step 02.2 - Add disclosure strings (EN/RU/UK, lockstep)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add three keys via `scripts/utils/set-android-string.ps1 -Action add` (one lockstep call per key, EN/RU/UK parity enforced): `screen_capture_disclosure_title`, `screen_capture_disclosure_message` (state what is captured - the device screen, possibly other apps - that it is treated as personal data, and where it is saved/used), `screen_capture_disclosure_accept`. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist). Use `ё` where grammatical in RU.

**Verification:**

- `Grep` - each key present in all three `strings.xml` files (3 keys x 3 files).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_capture_disclosure"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.3 - Show disclosure before the system consent request

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `ScreenCaptureConsentActivity.onCreate`, before calling `createScreenCaptureIntent()`, check `screenCaptureDisclosureAccepted`. If not accepted, show a modal disclosure dialog (title/message/accept strings from 02.2) with an explicit accept action and a cancel that finishes without launching consent. On accept, persist the flag, then launch the system consent intent. If already accepted, launch consent directly (unchanged path). Make the activity Hilt-aware to read/write settings (`@AndroidEntryPoint` + injected settings access); keep all business logic out of the Activity per CLAUDE.md Rule 3 - delegate persistence to the settings layer.

**Verification:**

- `Grep` - `screen_capture_disclosure_title` referenced in `ScreenCaptureConsentActivity.kt`.
- `Grep` - `createScreenCaptureIntent` is called only after the disclosure-accepted branch (no path reaches it before disclosure when the flag is false).
- `Grep -n "Log\.d\("` - zero hits in the file.
- `/build` - `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

### Step 02.4 - Insert the BlockNeedUserTest debug probe

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add one `Timber.d("S0671: <entry-point description>")` at the disclosure-flow entry (e.g. when the consent activity decides to show or skip the disclosure). One tag for the changed flow, per CLAUDE.md Debug Verification Tags. This tag is removed when the ticket leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` - exactly one `Timber.d("S0671:` line in the file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (`assembleStandardDebug`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_capture_disclosure"` exits 0.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The capture path now shows a prominent disclosure before the first system consent and persists acceptance. Post-processing (clipboard / draw / OCR-translate / send-to-recipients / save-destination chain) already rides through `ScreenCaptureService` and needs no new code - Phase 03 records the capability and runs the doc/catalog sync.

---

## Rollback Plan

Revert the phase commit: the disclosure branch and the settings flag are additive; removing them restores the direct-to-consent path. No data migration. Remove the new strings via `set-android-string.ps1 -Action remove`.
