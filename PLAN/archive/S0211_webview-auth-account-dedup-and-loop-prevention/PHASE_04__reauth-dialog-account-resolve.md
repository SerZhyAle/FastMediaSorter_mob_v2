# Phase 04 — Reauth Dialog Account Resolve

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

When `ReceiveShareActivity.offerAuthThenDownload` opens the auth dialog for a host that already has at least one active account, resolve that account's `displayName` and render the dialog title as "Вы вошли как %s. Войти снова?" (reuse the existing `s0155_reauth_*` string family — already trilingual, already passed §6 tone gate). The `auth dialog shown` log line carries the resolved name in place of `account=unknown`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (so the resolved displayName refers to a single deduped record, not one of several copies).
- [ ] Working tree clean or on the active DEBUG branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | (current ≈ 700; backup if projected ≥ 1500) |

> Backup required: file is over 500 LOC. Before the first edit, copy to `temp/ReceiveShareActivity.kt.<YYYYMMDD-HHmm>.bak`.

---

## Steps

### Step 04.1 — Backup `ReceiveShareActivity.kt`

**Files:** `temp/ReceiveShareActivity.kt.<timestamp>.bak`
**Depends on:** —

**Prompt for developer:**

> Per CLAUDE.md rule 5 (files > 500 LOC require a timestamped backup before edits), copy the current `ReceiveShareActivity.kt` into `temp/` with a timestamp suffix. Example PowerShell:
>
> ```powershell
> $stamp = Get-Date -Format 'yyyyMMdd-HHmm'
> Copy-Item app_v2\src\main\java\com\sza\fastmediasorter\ui\share\ReceiveShareActivity.kt `
>   "temp\ReceiveShareActivity.kt.$stamp.bak"
> ```

**Verification:**

- `Glob` — `temp/ReceiveShareActivity.kt.*.bak` matches at least one entry.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Backup created: `temp/ReceiveShareActivity.kt.20260515-2052.bak` (29 KB).

---

### Step 04.2 — Resolve existing account before showing the auth dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Modify `offerAuthThenDownload(url, host, resource, dialogType)` (currently around line 259).
>
> 1. Convert the body to launch a `lifecycleScope.launch { … }` so we can `await` the displayName lookup. The synchronous parts (computing `displayLabel`, `loginUrl`, setting `authOfferShown = true`) stay outside the launch — they don't need IO.
>
> 2. Inside the coroutine, resolve the existing display name:
>    ```kotlin
>    val existing = runCatching {
>        authSessionRepository.listAccountsForHost(host)
>            .filter { !it.isDismissed && it.cookieCount > 0 }
>            .maxByOrNull { it.lastUsedAt ?: java.time.Instant.MIN }
>    }.getOrNull()
>    val resolvedName = existing?.displayName?.trim()?.takeIf { it.isNotBlank() }
>    ```
>
> 3. Update the `Timber.i` log line at the start (currently line 269) to carry the resolved name:
>    ```kotlin
>    Timber.i(
>        "[S0166] auth dialog shown: type=%s account=%s host=%s",
>        dialogType, resolvedName ?: "none", host,
>    )
>    ```
>    Insert a `Timber.d("S0211: ReceiveShareActivity.offerAuthThenDownload resolvedName=%s host=%s", resolvedName ?: "<none>", host)` debug verification tag immediately after the resolution lookup (per CLAUDE.md "Debug Verification Tags"). The tag is owned by the spec status `BlockNeedUserTest`; `/spec-check` removes it on Verified.
>
> 4. When building the dialog, branch on `resolvedName`:
>    - `resolvedName != null` → reuse the existing strings:
>      - title: `getString(R.string.s0155_reauth_title, resolvedName)`
>      - message: `getString(R.string.s0155_reauth_message, resolvedName)`
>      - positive: `R.string.s0155_reauth_positive`
>    - `resolvedName == null` → keep the current strings (`auth_offer_dialog_title`, `auth_offer_dialog_message`, `auth_offer_dialog_add`).
>
> 5. The neutral (`auth_offer_dialog_skip`) and negative (`s0157_auth_offer_dismiss_always`) buttons keep their wording in both branches.
>
> 6. Move the `MaterialAlertDialogBuilder(...).show()` invocation onto the main thread — `lifecycleScope.launch` defaults to `Dispatchers.Main.immediate` for `LifecycleCoroutineScope`, so dialog construction is safe; only the repository call uses IO via its own `withContext`. No explicit dispatcher switch is required.
>
> 7. Communication-policy gate: title/message strings are reused from `s0155_reauth_*`, which already passed §6. No new strings introduced — no fresh tone audit needed. Confirm by grep that the same string IDs are used in the existing `presentSocialPreviewOnly`.

**Verification:**

- `Grep -n "[S0166] auth dialog shown" ReceiveShareActivity.kt` — exactly one match.
- `Grep -n "account=unknown" ReceiveShareActivity.kt` — zero matches (the `account=` field is now dynamic).
- `Grep -n "s0155_reauth_title" ReceiveShareActivity.kt` — at least one match.
- `Grep -n "Timber.d\(\"S0211:" ReceiveShareActivity.kt` — exactly one match (debug tag in the offer flow).
- `Grep -n "lifecycleScope.launch" ReceiveShareActivity.kt` — at least two matches (existing `enqueueLinkDownloadSilent` + new `offerAuthThenDownload`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS (multiline Timber.d on lines 279-282, log msg L284, no `account=unknown`, `s0155_reauth_title` L291, lifecycleScope.launch ≥9). Files: ReceiveShareActivity.kt (+~25 LOC net). Dev log recorded.

---

### Step 04.3 — Compile check

**Files:** —
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `/build` for `standardDebug`. The Activity is in `app_v2/src/main/`; standard flavor compiles all of main. Record `expected: BUILD SUCCESSFUL | actual: <result>` in chat.

**Verification:**

- `expected: BUILD SUCCESSFUL | actual: <result>` recorded.
- `ReceiveShareActivity.kt` final LOC is below 1500 (rule 2). `Glob -c` or `wc -l` confirmation in chat.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL — PASS`. File LOC: 614 (< 1500).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `standardDebug` PASS.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for `ReceiveShareActivity.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 is fully independent — touches worker code and string resources only. Can ship together with Phase 04 in the same DEBUG branch.

---

## Rollback Plan

Revert phase commit. Restore from the timestamped backup in `temp/` if the in-place edit needs to be undone before commit.
