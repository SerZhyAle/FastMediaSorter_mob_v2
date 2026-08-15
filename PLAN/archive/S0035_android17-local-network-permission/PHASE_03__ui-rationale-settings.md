# Phase 03 — UI Rationale and Settings

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-05-04
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Add the trilingual local-network permission copy and make the Settings surface truthfully reflect the new permission state before protocol entrypoints start using it.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Continue-path from Phase 01 still holds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Audit only | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Audit only | n/a |

---

## Steps

### Step 03.1 — Add trilingual string resources

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the local-network permission strings to EN / RU / UK together. Minimum keys:
>
> - rationale title
> - rationale message
> - button label for `Open settings`
> - button label / state for `Grant local network permission`
> - granted state
> - denied / unavailable state used by Cast or Settings
>
> Do not land any user-facing local-network flow with missing translations.

**Verification:**

- `Grep` — `local_network_permission_` returns new keys in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — the same key set exists in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — the same key set exists in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS (6 keys × 3 locales). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 03.2 — Update Settings button state logic

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the current `INTERNET`-based state with `PermissionHelper.hasLocalNetworkPermission(...)`. The button must:
>
> - show a granted state when the permission is present;
> - request the permission when `isLocalNetworkRuntimePermissionExpected()` is true and permission is missing;
> - route to app settings otherwise;
> - stay non-disruptive on API < 37 and on the `photos` flavour where runtime network entrypoints are absent.

**Verification:**

- `Grep` — `hasLocalNetworkPermission` appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`.
- `Grep` — `requestLocalNetworkPermission` or `routeToLocalNetworkSettings` appears in the same file.
- `Grep` — `hasInternetPermission` no longer drives `btnNetworkPermission` state in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: GeneralSettingsPermissionsHelper.kt (+8 LOC). Dev log recorded.

---

### Step 03.3 — Audit portrait and landscape settings layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Confirm that the new button copy fits the existing layouts in portrait and landscape without introducing truncation or overflow. Modify layout files only if the current button width or text size becomes invalid after the string update.

**Verification:**

- `Grep` — `@+id/btnNetworkPermission` still appears once in each layout variant.
- `Grep` — `grant_network_permission|network_permission_granted|local_network_permission_` resolves from the same button in both layout variants.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Both portrait and landscape have btnNetworkPermission once; grant_network_permission resolves from both. layout_weight=1 / 10sp — no overflow, no layout file change needed.

---

### Step 03.4 — Run compile and resource validation

**Files:** none modified — verification only
**Depends on:** Step 03.3

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> Resource validation for all three locales must pass before Add Resource or Cast surfaces start using the new copy.

**Verification:**

- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every Step 03.* above is `[x] done`.
- [ ] EN / RU / UK contain the same local-network permission key set.
- [ ] Settings no longer treats `INTERNET` as the actionable runtime permission.
- [ ] Settings layout remains valid in portrait and landscape.

---

## Handoff Notes to Next Phase

Phase 04 reuses the Phase 03 copy for Add Resource rationale dialogs. Do not add Add Resource specific duplicate strings.

---

## Rollback Plan

Revert the strings and Settings helper together so the UI cannot point at missing permission states.