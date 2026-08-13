# Phase 06 — settings-accounts-ui

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 05 (named-reauth)
**Blocks:** Phase 07
**Steps done:** 0 / 7
**Started:** —
**Completed:** —

---

## Objective

Replace the flat per-host session list in Settings with a per-host account group list. Each host row expands to show its accounts with operations: re-login, rename, delete. A "Add another account" action appears per-host. Migrate `AuthSessionsListViewModel` to `observeAccounts()`, introduce `AuthAccountGroupAdapter`, update `AuthSessionsListFragment`, add new layout files, and update landscape counterparts.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthAccountGroupAdapter.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/item_auth_host_group.xml` | New | ≤ 40 lines |
| `app_v2/src/main/res/layout/item_auth_account.xml` | New | ≤ 50 lines |
| `app_v2/src/main/res/layout/item_auth_session.xml` | Modified (soft-deprecate via comment) | unchanged |
| `app_v2/src/main/res/layout/fragment_auth_sessions_list.xml` | No change needed | — |
| `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml` | No change needed | — |

> `item_auth_session.xml` keeps the existing layout for backward compat but is no longer used by the new adapter; add a XML comment `<!-- S0155: replaced by item_auth_host_group.xml + item_auth_account.xml -->`.
> `fragment_auth_sessions_list.xml` and its landscape counterpart already have the RecyclerView with the correct ID (`rvAuthSessions`) — no layout changes needed.

**Landscape parity:** `fragment_auth_sessions_list.xml` has a landscape counterpart at `res/layout-land/fragment_auth_sessions_list.xml`. Neither file needs structural changes since both are simple RecyclerView containers. Confirmed — no landscape edits needed for this phase.

---

## Steps

### Step 06.1 — Update AuthSessionsListViewModel for multi-account

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the `sessions: Flow<List<AuthSessionDomain>>` property with a grouped structure for the Settings UI.
>
> Expose:
> - `val accountGroups: Flow<List<AuthHostGroup>>` — where `AuthHostGroup` is a local data class:
>   ```kotlin
>   data class AuthHostGroup(
>       val host: String,
>       val resource: KnownAuthResource?,  // null if host not in KnownAuthResources
>       val accounts: List<AuthAccountDomain>,
>   )
>   ```
>   Built from `repository.observeAccounts()` grouped by `host`, sorted by host alphabetically. `KnownAuthResources.matchHost(host)` provides the `displayName` for the host header.
>
> Add ViewModel methods:
> - `fun deleteAccount(host: String, accountId: String)` — calls `repository.deleteAccount(host, accountId)` in `viewModelScope`.
> - `fun updateDisplayName(host: String, accountId: String, newName: String)` — calls `repository.updateDisplayName(...)`.
> - `fun addAccount(host: String, loginUrl: String, onLaunchWebView: (url: String) -> Unit)` — calls `onLaunchWebView(loginUrl)` (UI side launches `WebViewAuthDialogFragment`).
>
> Keep the deprecated `val sessions` and `fun delete(host: String)` as deprecated stubs returning empty/no-op for any residual callers.

**Verification:**

- `Grep` — `val accountGroups: Flow<` present in `AuthSessionsListViewModel.kt`.
- `Grep` — `data class AuthHostGroup` present.
- `Grep` — `fun deleteAccount(host: String, accountId: String)` present.
- `Grep` — `fun updateDisplayName(host: String, accountId: String, newName: String)` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 06.2 — Create new layout files for per-account list

**Files:** `app_v2/src/main/res/layout/item_auth_host_group.xml`, `app_v2/src/main/res/layout/item_auth_account.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> **`item_auth_host_group.xml`** — host section header (non-clickable background, visual grouping):
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
>     xmlns:tools="http://schemas.android.com/tools"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:orientation="horizontal"
>     android:gravity="center_vertical"
>     android:paddingHorizontal="@dimen/margin_normal"
>     android:paddingTop="@dimen/margin_normal"
>     android:paddingBottom="@dimen/margin_small">
>
>     <TextView
>         android:id="@+id/tvAuthHostName"
>         android:layout_width="0dp"
>         android:layout_height="wrap_content"
>         android:layout_weight="1"
>         android:textAppearance="?attr/textAppearanceSubtitle2"
>         tools:text="instagram.com" />
>
>     <ImageButton
>         android:id="@+id/btnAddAccount"
>         android:layout_width="48dp"
>         android:layout_height="48dp"
>         android:background="?attr/selectableItemBackgroundBorderless"
>         android:src="@android:drawable/ic_input_add"
>         android:contentDescription="@string/s0155_add_account_label" />
> </LinearLayout>
> ```
>
> **`item_auth_account.xml`** — per-account row (indented under the host group):
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
>     xmlns:tools="http://schemas.android.com/tools"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:gravity="center_vertical"
>     android:minHeight="52dp"
>     android:orientation="horizontal"
>     android:paddingStart="@dimen/margin_xlarge"
>     android:paddingEnd="@dimen/margin_normal"
>     android:paddingVertical="@dimen/margin_small">
>
>     <LinearLayout
>         android:layout_width="0dp"
>         android:layout_height="wrap_content"
>         android:layout_weight="1"
>         android:orientation="vertical">
>
>         <TextView
>             android:id="@+id/tvAccountDisplayName"
>             android:layout_width="wrap_content"
>             android:layout_height="wrap_content"
>             android:textAppearance="?attr/textAppearanceBody1"
>             tools:text="@myusername" />
>
>         <TextView
>             android:id="@+id/tvAccountMeta"
>             android:layout_width="wrap_content"
>             android:layout_height="wrap_content"
>             android:textAppearance="?attr/textAppearanceCaption"
>             android:textColor="@color/text_color_secondary"
>             tools:text="3 cookies · 2026-05-09 · last used 2026-05-11" />
>     </LinearLayout>
>
>     <ImageButton
>         android:id="@+id/btnAccountRelogin"
>         android:layout_width="40dp"
>         android:layout_height="40dp"
>         android:background="?attr/selectableItemBackgroundBorderless"
>         android:src="@android:drawable/ic_menu_rotate"
>         android:contentDescription="@string/s0155_relogin_account_label" />
>
>     <ImageButton
>         android:id="@+id/btnAccountRename"
>         android:layout_width="40dp"
>         android:layout_height="40dp"
>         android:background="?attr/selectableItemBackgroundBorderless"
>         android:src="@android:drawable/ic_menu_edit"
>         android:contentDescription="@string/s0155_rename_account_title" />
>
>     <ImageButton
>         android:id="@+id/btnAccountDelete"
>         android:layout_width="40dp"
>         android:layout_height="40dp"
>         android:background="?attr/selectableItemBackgroundBorderless"
>         android:src="@android:drawable/ic_menu_delete"
>         android:contentDescription="@string/auth_sessions_delete_button_desc" />
> </LinearLayout>
> ```

**Verification:**

- `Glob` — `app_v2/src/main/res/layout/item_auth_host_group.xml` exists.
- `Glob` — `app_v2/src/main/res/layout/item_auth_account.xml` exists.
- `Grep` — `tvAuthHostName` present in `item_auth_host_group.xml`.
- `Grep` — `tvAccountDisplayName` present in `item_auth_account.xml`.
- `Grep` — `btnAddAccount` present in `item_auth_host_group.xml`.
- `Grep` — `btnAccountRelogin` present in `item_auth_account.xml`.
- `Grep` — `btnAccountDelete` present in `item_auth_account.xml`.

**Status:** `[ ]` not done

---

### Step 06.3 — Create AuthAccountGroupAdapter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthAccountGroupAdapter.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Create `AuthAccountGroupAdapter` as a `RecyclerView.Adapter` using a sealed `Item` type list:
>
> ```kotlin
> sealed interface Item {
>     data class HostHeader(val group: AuthHostGroup) : Item
>     data class AccountRow(val host: String, val account: AuthAccountDomain) : Item
> }
> ```
>
> `buildItems(groups: List<AuthHostGroup>): List<Item>` — for each group, emit one `HostHeader` + N `AccountRow` items.
>
> Use `view type` constants (`TYPE_HEADER = 0`, `TYPE_ACCOUNT = 1`).
>
> **HostHeader `ViewHolder`** — binds `tvAuthHostName` to `group.resource?.displayName ?: group.host`. `btnAddAccount` click → calls the `onAddAccount: (host: String, loginUrl: String) -> Unit` lambda (loginUrl from `group.resource?.loginUrl ?: ""`; if empty, launch the manual URL prompt fallback).
>
> **AccountRow `ViewHolder`** — binds:
> - `tvAccountDisplayName` → `account.displayName`
> - `tvAccountMeta` → cookie count + saved date + optional last-used date (use `auth_sessions_cookie_count` plural + date formatter as in existing `AuthSessionAdapter`)
> - `btnAccountDelete` → calls `onDelete: (host: String, accountId: String) -> Unit`
> - `btnAccountRename` → calls `onRename: (host: String, accountId: String, currentName: String) -> Unit`
> - `btnAccountRelogin` → calls `onRelogin: (host: String, accountId: String, loginUrl: String) -> Unit`
>
> Constructor lambdas: `onAddAccount`, `onDelete`, `onRename`, `onRelogin`.
>
> Use `DiffUtil.ItemCallback` on `Item` (compare by `Item::class` + primary key).
>
> Do NOT access `@ApplicationContext` or inject Hilt — all dependencies passed via constructor.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthAccountGroupAdapter.kt` exists.
- `Grep` — `class AuthAccountGroupAdapter` present exactly once.
- `Grep` — `sealed interface Item` present in the file.
- `Grep` — `data class HostHeader` present.
- `Grep` — `data class AccountRow` present.
- `Grep` — `TYPE_HEADER` constant present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 06.4 — Update AuthSessionsListFragment for multi-account adapter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Replace `AuthSessionAdapter` with `AuthAccountGroupAdapter` in `AuthSessionsListFragment`.
>
> Wire the new adapter lambdas:
>
> - `onAddAccount = { host, loginUrl → launchAddAccount(host, loginUrl) }` — new private method that opens `WebViewAuthDialogFragment.newInstance(loginUrl)` (for known URL) or calls `promptForManualUrl()` (if loginUrl is blank). After the fragment result (`RESULT_KEY`), log and do not auto-restart the download (user is in Settings, not the share flow).
>
> - `onDelete = { host, accountId → showDeleteConfirmation(host, accountId) }` — new private method that shows a `MaterialAlertDialogBuilder` with title `getString(R.string.s0155_delete_account_confirm, displayName)` and positive → `viewModel.deleteAccount(host, accountId)`.
>
> - `onRename = { host, accountId, currentName → showRenameDialog(host, accountId, currentName) }` — shows a `MaterialAlertDialogBuilder` with title `R.string.s0155_rename_account_title` and a `TextInputEditText` pre-filled with `currentName`; positive → `viewModel.updateDisplayName(host, accountId, newName)`.
>
> - `onRelogin = { host, accountId, loginUrl → launchRelogin(host, accountId, loginUrl) }` — opens `WebViewAuthDialogFragment.newInstance(loginUrl)`; the new account-naming dialog (Phase 03) fires after login and saves as a new account (not overwriting the existing one).
>
> Replace the `viewModel.sessions.collect` block with `viewModel.accountGroups.collect { groups → adapter.submitList(adapter.buildItems(groups)); empty.visibility = ... }`.
>
> Keep `promptForManualUrl()` and `promptForUrlAndOpenWebView()` for the "add manually" fallback path (menu `+` still adds a new top-level authorization for any host).

**Verification:**

- `Grep` — `AuthAccountGroupAdapter(` present in `AuthSessionsListFragment.kt`.
- `Grep` — `viewModel.accountGroups.collect` present.
- `Grep` — `showDeleteConfirmation(` present.
- `Grep` — `showRenameDialog(` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 06.5 — Mark item_auth_session.xml as deprecated

**Files:** `app_v2/src/main/res/layout/item_auth_session.xml`
**Depends on:** Step 06.4

**Prompt for developer:**

> Add an XML comment on the first line after the root element opening tag:
> `<!-- S0155: this layout is replaced by item_auth_host_group.xml + item_auth_account.xml; kept for git history only -->`.
> Do not delete the file — the layout is no longer inflated but preserving it avoids unnecessary git noise.

**Verification:**

- `Grep` — `S0155: this layout is replaced` present in `item_auth_session.xml`.

**Status:** `[ ]` not done

---

### Step 06.6 — Run string locale audit

**Files:** all `values*/strings_s0155.xml`
**Depends on:** Step 06.5

**Prompt for developer:**

> Run the locale audit for all S0155 keys:
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0155_"
> ```
> Exit code must be 0. If not: fix any missing keys before proceeding.

**Verification:**

- Script exits with code 0 (all EN/RU/UK keys present for the `s0155_` prefix).

**Status:** `[ ]` not done

---

### Step 06.7 — Dev log for Phase 06 files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.6

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt" "S0155 Phase 06" "Expose accountGroups flow; add deleteAccount, updateDisplayName, addAccount VM methods"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthAccountGroupAdapter.kt" "S0155 Phase 06" "New adapter: per-host account group list with delete/rename/relogin/add-account"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt" "S0155 Phase 06" "Switch to AuthAccountGroupAdapter; wire delete/rename/relogin/add-account flows"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/item_auth_host_group.xml" "S0155 Phase 06" "New layout: host group header row"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/item_auth_account.xml" "S0155 Phase 06" "New layout: per-account row with relogin/rename/delete buttons"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/item_auth_session.xml" "S0155 Phase 06" "Soft-deprecated: replaced by item_auth_host_group.xml + item_auth_account.xml"
```

**Verification:**

- `Grep` — `S0155 Phase 06` matches at least 6 lines in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched".
- [ ] String locale audit `check_strings_localized.ps1 -KeyPrefix "s0155_"` exits 0.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- Settings screen shows per-host account groups with full CRUD operations.
- `AuthSessionDomain` is no longer used by the live UI (only `AuthAccountDomain` flows through).
- `AuthSessionAdapter` is no longer attached to any `RecyclerView` — can be removed as dead code cleanup in Phase 07.
- Final phase: docs and catalog cleanup.

---

## Rollback Plan

Revert phase commit(s). Layout files and the new adapter are additive; the old adapter file (`AuthSessionAdapter.kt`) is untouched and can be restored as the active adapter by reverting `AuthSessionsListFragment.kt`.
