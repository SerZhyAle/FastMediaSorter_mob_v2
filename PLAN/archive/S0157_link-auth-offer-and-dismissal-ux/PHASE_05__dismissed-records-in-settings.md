# Phase 05 — dismissed-records-in-settings

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-11

---

## Objective

Show dismissed records in the auth settings list so users can see and revoke permanent dismissals. Show `lastUsedAt` for every account row (date, or "not yet used"). Dismissed rows show a label instead of display name and only offer a delete button.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`observeAccountsAll()`, `AuthAccountDomain.isDismissed` available).
- [ ] Phase 03 is ✅ Done (strings_s0157.xml present with `s0157_dismissed_label`, `s0157_last_used_never`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthAccountGroupAdapter.kt` | Modified | ≤ 185 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | Modified | ≤ 215 |

> Landscape variants absent — these are settings fragments using standard RecyclerView layout; no layout-land counterpart exists for `fragment_auth_sessions_list.xml`.

---

## Steps

### Step 05.1 — `AuthSessionsListViewModel`: use `observeAccountsAll()`

**Files:** `ui/settings/auth/AuthSessionsListViewModel.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AuthSessionsListViewModel`, change `repository.observeAccounts()` to `repository.observeAccountsAll()` in the `accountGroups` flow definition. This includes dismissed records in the settings list. Dismissed records will appear as `AuthAccountDomain` entries with `isDismissed = true` and `accountId = EncryptedCookieStore.DISMISSED_ACCOUNT_ID` (accessible as a constant from the data layer, but the VM should not reference the store directly — use `account.isDismissed` in the UI layer instead).

**Verification:**

- `Grep` — `observeAccountsAll()` present in `AuthSessionsListViewModel.kt`.
- `Grep` — `observeAccounts()` does NOT appear as a standalone call in `accountGroups` definition (replaced).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. `observeAccountsAll()` present in `accountGroups` definition; `repository.observeAccounts()` 0 hits in `accountGroups`. Files: AuthSessionsListViewModel.kt (0 lines net).

---

### Step 05.2 — `AuthAccountGroupAdapter`: render dismissed rows and always show `lastUsedAt`

**Files:** `ui/settings/auth/AuthAccountGroupAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In `AccountRowViewHolder.bind(host, account)`:
>
> 1. **Display name**: if `account.isDismissed`, show `getString(R.string.s0157_dismissed_label)` in `tvAccountDisplayName` instead of `account.displayName`.
> 2. **Action buttons**: if `account.isDismissed`, set `reloginBtn.visibility = View.GONE` and `renameBtn.visibility = View.GONE`. Delete button remains visible — deleting the dismissed record revokes the dismissal.
> 3. **Last-used date**: always show `lastUsedAt`. Replace the current `account.lastUsedAt?.let { ... metaText.append(...) }` pattern with:
>    ```kotlin
>    val lastUsedText = account.lastUsedAt
>        ?.let { ctx.getString(R.string.s0157_last_used_fmt, DATE_FMT.format(it.atZone(ZoneId.systemDefault()))) }
>        ?: ctx.getString(R.string.s0157_last_used_never)
>    metaText.append(" · $lastUsedText")
>    ```
>    Add string key `s0157_last_used_fmt` = "last used %1$s" (EN) / "последний раз %1$s" (RU) / "останній раз %1$s" (UK) to `strings_s0157.xml` in all three locales. For dismissed rows, skip the cookie count and savedAt in the meta (show only the dismissed label — no meta text needed).
>
> For dismissed rows the `meta` field can show the savedAt as "Declined on …" for context, but this is optional. If you add it, use an `s0157_dismissed_meta` string.

**Verification:**

- `Grep` — `s0157_dismissed_label` referenced in `AuthAccountGroupAdapter.kt`.
- `Grep` — `isDismissed` referenced in `bind()`.
- `Grep` — `reloginBtn.visibility = View.GONE` in `bind()` (dismissal branch).
- `Grep` — `s0157_last_used_never` referenced.
- `Grep` — `Log\.d\(` returns zero hits in `AuthAccountGroupAdapter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 5/5 PASS. `s0157_dismissed_label`, `isDismissed`, `reloginBtn.visibility = View.GONE`, `s0157_last_used_never` present; `Log.d(` 0 hits. Files: AuthAccountGroupAdapter.kt (+12 lines net).

---

### Step 05.3 — String locale audit for Step 05.2 additions

**Files:** `values/strings_s0157.xml`, `values-ru/strings_s0157.xml`, `values-uk/strings_s0157.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add `s0157_last_used_fmt` to all three locale files if it was used in Step 05.2. Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0157_"` — must exit code 0.

**Verification:**

- `Grep` — `s0157_last_used_fmt` present in all three `strings_s0157.xml` variants (if added in Step 05.2).
- `Bash` — `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0157_"` exits code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. `s0157_last_used_fmt` present in all three locale files; `check_strings_localized.ps1 -KeyPrefix s0157_` exit 0 (5 keys × 3 locales). No strings.xml changes needed in this step.

---

### Step 05.4 — `AuthSessionsListFragment`: log dismissed screen open

**Files:** `ui/settings/auth/AuthSessionsListFragment.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> The fragment already collects `viewModel.accountGroups` and updates the adapter. No structural change needed. However, the Timber.d tag `Timber.d("S0155: settings multi-account screen opened")` in `onViewCreated` is stale — spec S0155 is in `BlockNeedUserTest` state, so the tag should remain if S0155 is still `BlockNeedUserTest` (check `scripts/spec_catalog/select.ps1 -Id S0155 -Format json`). If S0155 is no longer `BlockNeedUserTest`, remove the tag. Add `Timber.d("S0157: dismissed records visible in auth settings screen")` instead, which will be removed when S0157 leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` — stale S0155 or S0157 tags are consistent with the specs' current statuses.
- `Grep` — `Log\.d\(` returns zero hits in `AuthSessionsListFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. S0155 tag present (S0155 is BlockNeedUserTest — correct); no S0157 tag (S0157 is In Progress — correct); `Log.d(` 0 hits. No code changes required.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0157_"` exits code 0.
- [x] Dev log entries added for all modified files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Settings screen shows dismissed records alongside active accounts.
- Dismissed rows: label "(Not authorized — you declined)", no relogin/rename buttons, delete revokes dismissal.
- Every account row shows last-used date or "not yet used".
- `s0157_last_used_fmt` may be present in strings if added in Step 05.2; Phase 06 will verify the final locale parity.

---

## Rollback Plan

Revert phase commit(s). No data migration — UI-only changes to existing fragments and adapter.
