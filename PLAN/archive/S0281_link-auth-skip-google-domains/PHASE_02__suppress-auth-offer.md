# Phase 02 - Suppress auth offer for google-OAuth-only hosts

**Strategic spec:** [`../S0281_link-auth-skip-google-domains.md`](../S0281_link-auth-skip-google-domains.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Route google-OAuth-only hosts (`google.com`, `accounts.google.com`, `youtube.com`, `music.youtube.com` and their subdomains) through the silent download path inside `ReceiveShareActivity`, bypassing the auth-offer dialog and the post-`NoMediaFound` escalation. Optionally remove the `YouTube` entry from `KnownAuthResources` if Phase 01 Decision Q1 = A.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01).
- [ ] Strategic §6 research items blocking this phase are Resolved (Q1 from Phase 01).
- [ ] Working tree is clean or on a feature branch.
- [ ] Phase 01 Decision Log contains explicit `Decision Q1: A | B | C`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/backup_ReceiveShareActivity_<timestamp>.kt` | New (backup) | snapshot |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 720 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | Modified (only if Decision Q1 = A or B) | ≤ 90 |

> `ReceiveShareActivity.kt` is 688 LOC before the change - backup step required per CLAUDE.md Strict Rule 5 (>500 LOC).

---

## Steps

### Step 02.1 - Back up `ReceiveShareActivity.kt` before edits

**Files:** `temp/backup_ReceiveShareActivity_<timestamp>.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The current file is 688 LOC, above the 500-LOC backup threshold. Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` to `temp/backup_ReceiveShareActivity_<YYYYMMDD_HHmmss>.kt` before any edit. Do not commit the backup.

**Verification:**

- `Glob` - `temp/backup_ReceiveShareActivity_*.kt` matches at least one file created today.
- `Grep` - the backup file contains the literal substring `class ReceiveShareActivity` (proves it is the right file).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Backup at `temp/backup_ReceiveShareActivity_20260521_012807.kt`.

---

### Step 02.2 - Short-circuit `maybeOfferAuthThenDownload` for google-OAuth-only hosts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inside `maybeOfferAuthThenDownload(url)`, after computing `host` from `Uri.parse(url).host.orEmpty()` and before any other branch (including the existing blank-host short-circuit), add a check: if `host` matches `GoogleDomainMatcher.isGoogleAuthHost(Uri.parse(url))` then route to `enqueueLinkDownloadSilent(url)` and `return`. Use the existing `GoogleDomainMatcher` object from `com.sza.fastmediasorter.data.browser` - do not duplicate the domain list. Add a `Timber.d("S0281: ReceiveShareActivity.maybeOfferAuthThenDownload skip auth for google host=$host")` at the entry of the new branch (per CLAUDE.md Debug Verification Tags - this ticket enters `BlockNeedUserTest` after Phase 05). The branch must run before `KnownAuthResources.matchHost` consults the known-social list, so a future change to that list cannot accidentally re-introduce the dialog for google-OAuth-only hosts. Preserve all other existing logic (dismissed-host check, account selection, `onSelected` / `onNoneAvailable` / `onCancelled` callbacks) for non-google hosts.

**Verification:**

- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` contains exactly one line matching `GoogleDomainMatcher.isGoogleAuthHost`.
- `Grep` - the file contains the literal `Timber.d("S0281: ReceiveShareActivity.maybeOfferAuthThenDownload skip auth for google`.
- `Grep` - the file still contains exactly one `private fun maybeOfferAuthThenDownload`.
- `Grep` - the file still contains exactly one `private fun enqueueLinkDownloadSilent`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Added `GoogleDomainMatcher` import + early return branch in `maybeOfferAuthThenDownload` routing google-OAuth-only hosts to `enqueueLinkDownloadSilent`.

---

### Step 02.3 - Suppress `handleNoMediaFoundEscalation` re-entry for google-OAuth-only hosts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Inside `handleNoMediaFoundEscalation(url, accountId, isAuthRetry)`, locate the existing guard that bails out when `hostForEscalation` is blank or matches `KnownAuthResources.matchHost`. Extend that same guard to also bail out when `GoogleDomainMatcher.isGoogleAuthHost(Uri.parse(url))` is true. If Phase 01 Decision Q1 = A (YouTube entry removed in Step 02.5 below), this extension is the only thing protecting google hosts from a stray escalation; if Decision Q1 = B or C, this extension is redundant but kept as defense-in-depth and a clear semantic marker. Insert a single Timber line `Timber.d("S0281: ReceiveShareActivity.handleNoMediaFoundEscalation skip google host=$hostForEscalation")` at the new branch entry.

**Verification:**

- `Grep` - the file contains the literal `Timber.d("S0281: ReceiveShareActivity.handleNoMediaFoundEscalation skip google`.
- `Grep` - the file contains two distinct call sites of `GoogleDomainMatcher.isGoogleAuthHost` (one from Step 02.2, one from Step 02.3).
- `Grep` - the file still contains exactly one `private fun handleNoMediaFoundEscalation`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Added google-OAuth-only guard immediately after the existing blank/known-social guard in `handleNoMediaFoundEscalation`.

---

### Step 02.4 - Verify `handleReAuthFromNotification` consistency

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Inspect `handleReAuthFromNotification(url)`. This path is triggered when the user taps "Sign in" on a worker result notification. For a google-OAuth-only host, the auth-offer dialog must NOT be shown here either (same reason as the share path). Add an early branch: if `GoogleDomainMatcher.isGoogleAuthHost(Uri.parse(url))` is true, route to `processLinkAutoDownload(url, accountId = null)` and `return` - skip the auth-offer entirely. Insert a Timber line `Timber.d("S0281: ReceiveShareActivity.handleReAuthFromNotification skip google host=$host")` at the new branch entry.

**Verification:**

- `Grep` - the file contains the literal `Timber.d("S0281: ReceiveShareActivity.handleReAuthFromNotification skip google`.
- `Grep` - the file contains exactly three call sites of `GoogleDomainMatcher.isGoogleAuthHost` across all three methods modified in this phase.
- `Grep` - the file still contains exactly one `private fun handleReAuthFromNotification`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Added google-OAuth-only early branch at the top of `handleReAuthFromNotification` so the "Sign in" notification action also bypasses the dialog for these hosts.

---

### Step 02.5 - Apply Decision Q1 to `KnownAuthResources`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Read the Decision Q1 line in `PHASE_01__inventory-and-decisions.md`. Apply exactly one of three actions:
>
> - **Decision Q1: A** - Delete the `KnownAuthResource("YouTube", host = "youtube.com", loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube")` entry from the `all` list. Keep the file otherwise unchanged.
> - **Decision Q1: B** - Add a new constructor parameter `val oauthOnly: Boolean = false` to `data class KnownAuthResource` (defaulting to `false` to keep all existing call sites compatible) and flip the `YouTube` entry to `oauthOnly = true`. Add a helper `fun isOAuthOnly(host: String?): Boolean = matchHost(host)?.oauthOnly == true`.
> - **Decision Q1: C** - Make no changes to this file. Verification in this step then asserts the file is byte-identical to its pre-phase state.
>
> Whichever path is taken, no other code in the project may be modified to compensate - Steps 02.2-02.4 already guard `ReceiveShareActivity` independently via `GoogleDomainMatcher`.

**Verification:**

- For Decision Q1 = A: `Grep` for `"YouTube"` inside `KnownAuthResources.kt` returns zero matches.
- For Decision Q1 = B: `Grep` for `val oauthOnly` returns exactly one match in the file; `Grep` for `oauthOnly = true` returns exactly one match.
- For Decision Q1 = C: `Grep` for `displayName = "YouTube"` returns exactly one match (unchanged).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Decision Q1 = A applied. Verification PASS: 0 `"YouTube"` matches in `KnownAuthResources.kt`. Test file `KnownAuthResourcesTest.kt` updated as documented corollary (Phase 01 Decision Log §Q1 side-effect note): the `youtube and music youtube resolve to youtube entry` test was rewritten as `youtube and music youtube no longer resolve to known social entry` (assertNull). Necessary to keep unit tests green and strategic §11.7 satisfied.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (KnownAuthResource may gain a new field per Decision Q1 = B).

---

## Handoff Notes to Next Phase

After this phase, the auth-offer dialog will not appear for google-OAuth-only hosts in any of three entry points (share, notification re-auth, NoMediaFound escalation). The download worker will still run as before and report success / NoMediaFound / SocialPreviewOnly via the existing UI surface. Phase 03 deals with cleanup of any historically-created empty account records; Phase 04 (if unlocked by Q2) adds a one-shot explanatory message.

---

## Rollback Plan

Revert the commit(s) for this phase. `ReceiveShareActivity.kt` returns to its pre-phase state (backup in `temp/` provides a manual reference if needed). `KnownAuthResources.kt` returns to its pre-phase state. No data migration, no user-facing surface left in an inconsistent half-state.
