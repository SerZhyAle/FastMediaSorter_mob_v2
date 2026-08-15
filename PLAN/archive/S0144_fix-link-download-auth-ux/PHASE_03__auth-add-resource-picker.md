# Phase 03 — Known-resource picker in "Add authorization"

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

When the user taps the toolbar `+` action, first show a chooser listing the known resources from `KnownAuthResources.all` plus an "Enter manually" entry; picking a known resource opens the WebView-auth dialog straight at its login URL; "Enter manually" keeps the current URL-input dialog.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`KnownAuthResources` exists).
- [ ] Phase 02 ✅ Done (toolbar `+` action calls `promptForUrlAndOpenWebView()`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | Modified | ≤ 140 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +2 |

---

## Steps

### Step 03.1 — Add picker strings (trilingual)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two keys to all three `strings.xml` files: `auth_add_picker_title` — EN `Choose a resource`, RU `Выберите ресурс`, UK `Виберіть ресурс`; `auth_add_enter_manually` — EN `Enter address manually`, RU `Ввести адрес вручную`, UK `Ввести адресу вручну`. Follow `docs/COMMUNICATION_POLICY.md` §2 and the §6 tone checklist. Use `..` not `...`; keep `ё`/`Ё` in RU.

**Verification:**

- `Grep` — `auth_add_picker_title` and `auth_add_enter_manually` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — both keys present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — both keys present in `app_v2/src/main/res/values-uk/strings.xml`.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "auth_add_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification PASS; `check_strings_localized.ps1 -KeyPrefix "auth_add_"` exit 0 (both keys OK in EN/RU/UK). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 03.2 — Two-step add flow in `AuthSessionsListFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Change `promptForUrlAndOpenWebView()` so it first shows a `MaterialAlertDialogBuilder` single-choice / list dialog titled `R.string.auth_add_picker_title` whose items are the `displayName` of every `KnownAuthResources.all` entry followed by a final `R.string.auth_add_enter_manually` item. Selecting a known resource opens `WebViewAuthDialogFragment.newInstance(<that entry's loginUrl>).show(parentFragmentManager, "s0116_webview_auth")`. Selecting "Enter manually" shows the existing `TextInputEditText` URL-prompt dialog with the same `http(s)` validation and Snackbar-on-invalid behaviour as today. Extract the manual-entry dialog into a private `promptForManualUrl()` helper. Add `Timber.d("S0144: auth-add picker shown")` at the top of `promptForUrlAndOpenWebView()`.

**Verification:**

- `Grep` — `auth_add_picker_title` referenced in `AuthSessionsListFragment.kt`.
- `Grep` — `KnownAuthResources` referenced in `AuthSessionsListFragment.kt`.
- `Grep` — `promptForManualUrl` declared in `AuthSessionsListFragment.kt`.
- `Grep` — `WebViewAuthDialogFragment.newInstance(` still present in `AuthSessionsListFragment.kt`.
- `Grep` — `Timber.d("S0144:` present in `AuthSessionsListFragment.kt`.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS (picker_title 1, KnownAuthResources 2, promptForManualUrl 1, newInstance 2, S0144 tag 2, Log.d 0). Files: AuthSessionsListFragment.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2026-05-10).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The picker reuses `KnownAuthResources.all` and opens `WebViewAuthDialogFragment` with a resource's `loginUrl` — the same dialog whose redirect handling Phase 04 fixes.

---

## Rollback Plan

Revert phase commit(s) — Fragment + strings only, no data migration.
