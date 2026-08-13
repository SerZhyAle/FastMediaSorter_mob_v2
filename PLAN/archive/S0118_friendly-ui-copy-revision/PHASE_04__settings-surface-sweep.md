# Phase 04 - Settings Surface Sweep

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05, Phase 06, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Rewrite settings, import/export, and support-related copy so validation, success, and failure feedback in the settings area all use the S0118 tone and the shared next-step routing.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Shared support routing is available to settings helpers.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | <= 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt` | Modified | <= 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt` | Modified | <= 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt` | Modified | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt` | Modified | <= 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 220 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 220 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 220 |

> Several touched files already exceed 500 lines. Back up each such file to `temp/` before editing.

---

## Steps

### Step 04.1 - Replace hardcoded settings validation strings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt`, locale `strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the remaining hardcoded validation, file-picker, and reset-failure user messages in settings helpers with localized resource strings. Rewrite them in the S0118 tone: short primary message, clear next action when the user can fix it, and no raw protocol or stack language.

**Verification:**

- `Grep` - `Cache size must be between 512 and 16384 MB` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `Failed to launch file picker|File is empty or could not be read|Failed to import:` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt`.
- `Grep` - `Failed to reset SMB connections:|Failed to reset settings:` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Replaced 8+ hardcoded strings across 3 helpers; added 12 new EN/RU/UK keys. Dev log recorded.

---

### Step 04.2 - Rewrite import, export, and sync feedback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt`, locale `strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Rewrite the import/export, save-log, and sync result strings so success feedback is brief and failure feedback tells the user what to try next. Preserve the current control flow and error branching; change only the copy contract and the resource wiring.

**Verification:**

- `Grep` - `Unknown error` returns zero user-facing fallback hits in `GeneralSettingsImportExportHelper.kt`.
- `Grep` - `sync_failed` remains present in `GeneralSettingsObserversHelper.kt` with resource-backed messaging.
- `Grep` - new or updated settings feedback keys exist in EN, RU, and UK locale files.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Replaced all "Unknown error" fallbacks with R.string.settings_unknown_error. sync_failed remains resource-backed in observers helper. Dev log recorded.

---

### Step 04.3 - Align settings help and fallback actions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt`, locale `strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Update browser-not-found, no-email-client, and related settings fallback copy so each surface offers one clear follow-up action. Use the shared support factory and avoid exposing raw URLs or generic technical wording to the user.

**Verification:**

- `Grep` - `No browser found to open documentation|No browser found to open Privacy Policy` returns zero hits in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `No email client found` is resource-backed and still reachable from `GeneralSettingsLogHelper.kt`.
- `Grep` - `SupportIntentFactory` present in both touched helpers.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Replaced "No browser found" hardcoded fallbacks (3 sites). no_email_client + SupportIntentFactory both still wired in helpers. Dev log recorded.

---

### Step 04.4 - Run a residual settings hardcode sweep

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/*.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Run a residual sweep for hardcoded user-facing strings inside `ui/settings/helpers`. Replace any remaining hits that are visible to end users, then stop once the directory is resource-backed except for debug-only or non-user-facing developer text.

**Verification:**

- `Grep` - `Toast\.makeText\(.*"` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/**` for production-visible messages.
- `Grep` - `setMessage\("|setTitle\("` returns zero production-visible hardcoded dialog text hits in the same directory.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 2/2 PASS. No hardcoded `Toast.makeText(.., "..")` or `setMessage("/setTitle("` left in settings/helpers. (DialogUtils log titles "Application Log"/"Current Session Log" remain intentional dev-tooling labels — not production-visible end-user copy.) Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL in 33s.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after the touched `.kt` files change.

---

## Handoff Notes to Next Phase

Settings helpers now speak one tone and no longer own obvious hardcoded user copy, so feature flows can be swept next without redoing support and validation wording.

---

## Rollback Plan

Revert the Phase 04 commit(s). This phase changes copy and resource wiring only.