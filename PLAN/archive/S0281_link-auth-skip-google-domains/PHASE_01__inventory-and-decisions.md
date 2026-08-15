# Phase 01 - Inventory and decisions

**Strategic spec:** [`../S0281_link-auth-skip-google-domains.md`](../S0281_link-auth-skip-google-domains.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Run two static inventories and freeze two tactical decisions (strategic §6 Q1 and Q3) inside this phase file. No code changes.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none).
- [ ] Strategic §6 research items blocking this phase are Resolved (Q1 and Q3 are resolved by this phase itself).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0281_link-auth-skip-google-domains/PHASE_01__inventory-and-decisions.md` | Modified | ≤ 250 |

This phase writes only into its own file (the Decision Log section appended at the bottom).

---

## Steps

### Step 01.1 - Inventory: who reads `KnownAuthResources` outside link-auto-download

**Files:** none (read-only grep)
**Depends on:** - start of phase

**Prompt for developer:**

> Run a project-wide grep for `KnownAuthResources` (the object itself), `KnownAuthResource(` (the data class constructor invocation), and any reference to the `YouTube` entry by display name or by host string `youtube.com` / `music.youtube.com`. Exclude paths `temp/`, `DOWNLOADS/`, `.venv/`, `logs/`, `.kotlin/`, `node_modules/`, `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`. Record each call site with file path + line number in the Decision Log section of this phase file. The goal is to resolve §6 Q1 (whether the YouTube entry can be removed without breaking unrelated consumers).

**Verification:**

- `Grep` - `Decision Log` section in this phase file exists and contains a sub-heading `### Q1 inventory - call sites`.
- `Grep` - that sub-heading is followed by a non-empty list of file paths (or the explicit literal sentence `No call sites outside link-auto-download.`).
- `Grep` - the final line of the §Q1 block contains `Decision Q1: A` OR `Decision Q1: B` OR `Decision Q1: C` (per the strategic §6 Q1 option set).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Inventory recorded in Decision Log §Q1. Decision Q1 = A locked.

---

### Step 01.2 - Inventory: empty `AuthAccountDomain` records for google-OAuth-only hosts

**Files:** none (read-only read + grep)
**Depends on:** Step 01.1

**Prompt for developer:**

> Read `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` and `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` to determine: (a) what is the underlying storage backend (Room table / SharedPreferences / EncryptedSharedPreferences / DataStore); (b) is there an existing query path that returns all records, or do reads require a host parameter; (c) what does "empty record" mean in the data model - zero cookies, null token, dismissed flag, or the absence of any record at all. Record the findings in the Decision Log section under `### Q3 inventory - storage shape`. Based on (a)-(c), resolve §6 Q3: either (A) one-shot idempotent cleanup is meaningful and implementable, or (B) leave-as-is because there are no such records possible by construction. Do not write code yet - only document the inventory and lock the decision.

**Verification:**

- `Grep` - the Decision Log section contains a sub-heading `### Q3 inventory - storage shape`.
- `Grep` - that sub-heading is followed by three numbered findings labeled `(a)`, `(b)`, `(c)`.
- `Grep` - the final line of the §Q3 block contains `Decision Q3: A` OR `Decision Q3: B`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Storage shape recorded in Decision Log §Q3. Decision Q3 = B locked - empty active records impossible by API construction; Phase 03 will be ⏭️ Skipped.

---

### Step 01.3 - Surface §6 Q2 prompt for the operator

**Files:** none
**Depends on:** Step 01.2

**Prompt for developer:**

> §6 Q2 (whether to show a one-shot explanatory message when a google-OAuth-only URL is shared) is an owner decision, not a research item. Append a short Decision Log entry `### Q2 status` stating that the question remains open and will gate Phase 04. Do NOT attempt to answer it. If the operator has provided an answer in chat by the time this step runs, record their literal answer alongside the date.

**Verification:**

- `Grep` - the Decision Log section contains a sub-heading `### Q2 status`.
- `Grep` - that sub-heading is followed by a line starting with `Owner decision:` (value is either `<answer>` or the literal `Open - pending operator response`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Operator answered Q2 = "Once per failed extract" via AskUserQuestion. Phase 04 will implement the "once-per-failed-extract" variant per Step 04.2 / 04.3 branch.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - no code changed, but run `/build` only if the developer touched anything outside this file.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for this phase file via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: not applicable - this phase makes no code changes.

---

## Handoff Notes to Next Phase

Decision Q1 (A/B/C) determines whether Phase 02 includes the KnownAuthResources YouTube-entry edit. Decision Q3 (A/B) determines whether Phase 03 is `Done` with code changes or `⏭️ Skipped` with documented reason. §6 Q2 owner answer (if provided) determines whether Phase 04 is `Done` with code changes or `⏭️ Skipped`.

---

## Rollback Plan

Revert this phase file to a clean placeholder - no code or data was touched.

---

## Decision Log

### Q1 inventory - call sites

Production code references to `KnownAuthResources` (excluding tests, docs, CHANGELOG, read-only zones):

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` - lines 28, 216, 246, 379, 482 (link-auto-download core).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` - lines 15, 151 (link-auto-download result-presentation: builds re-auth `loginUrl` on SocialPreviewOnly).
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` - lines 6, 264 (link-auto-download extraction pipeline: gates `isPreviewSensitiveHost`).
- `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` - lines 14, 128, 556, 558 (link extraction: `isPreviewSensitiveHost` + `supportsEmbeddedJson`).
- `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` - lines 4, 151, 218 (link extraction: same gates).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListViewModel.kt` - lines 6, 34 (settings/auth UI: maps host -> resource for display).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` - lines 29, 140, 145 (settings/auth UI: reads `KnownAuthResources.all` for the "+" picker).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthAccountGroupAdapter.kt` - line 112 (settings/auth UI: resolves loginUrl per host).
- `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResourcesTest.kt` - lines 32-35 (asserts YouTube matching).

Side-effects per option:

- **Q1 = A (delete YouTube entry):** YouTube vanishes from settings/auth `+` picker - consistent, because the auth path for YouTube was a dead-end there too; `LinkAutoDownloadResultPresenter.kt:151` falls back to `result.originalUrl` which still routes through `GoogleDomainBrowserLauncher` (i.e. CCT) - functionally identical to today. Tests at lines 32-34 of `KnownAuthResourcesTest.kt` must be removed; line 35 (`isPreviewSensitiveHost("youtube.com") == false`) remains true and keeps passing because `matchHost` now returns `null`. The test edit is a necessary corollary of Step 02.5 under Decision A and will be executed in the same step.
- **Q1 = B (add `oauthOnly` flag):** requires additional UI filter in settings picker and changes the data-class surface; larger blast radius for the same end result.
- **Q1 = C (no change):** leaves the settings/auth `+` picker offering a YouTube entry that leads nowhere; inconsistent with the spec intent.

Decision Q1: A

### Q3 inventory - storage shape

(a) Storage backend: `EncryptedCookieStore` (under `data/link/cookie/`) wrapped by `AuthSessionRepositoryImpl`. Each stored entry carries a `type` field with values `TYPE_ACTIVE` or `TYPE_DISMISSED` (constants on `EncryptedCookieStore`). Active entries also carry `cookieCount: Int`, `savedAt: Instant`, `lastUsedAt: Instant?`, `displayName`, `accountId`.

(b) All-hosts query path exists: `store.listAllAccounts(): List<Pair<host, AccountEntry>>` is already used internally by `refreshFlows()` (line 223) and the eTLD+1 fallback inside `listAccountsForHost` (line 106). No new query needed to enumerate hosts.

(c) "Empty record" interpretation:
- `TYPE_ACTIVE` with `cookieCount == 0` is architecturally impossible. `saveSession(host, accountId, displayName, cookies, ...)` rejects `cookies.isEmpty()` at line 47 with `return` and a Timber `skipped empty account save` log; `saveSessionFromWebView(host, displayName, cookies, ...)` rejects it at line 64 with `return null` and `skipped empty webview save`. The only public write paths gate empty cookies out before the store is touched. Under the current broken google-domain flow, no cookies ever return from CCT to the app, so neither write path fires for these hosts - the store never sees a record.
- `TYPE_DISMISSED` entries can exist for google-OAuth-only hosts if a user historically pressed the "Don't ask" negative button on the auth-offer dialog. These records are tiny (one entry per host) and harmless. The Phase 02 short-circuit (`GoogleDomainMatcher.isGoogleAuthHost`) fires before any dismissal check, so the historical "Don't ask" state is irrelevant after Phase 02 ships; the records stay but never get read.

No empty active records possible by construction; dismissed records are not a regression surface.

Decision Q3: B

### Q2 status

Owner decision: Once per failed extract (recorded 2026-05-21 via AskUserQuestion).

Toast fires inside `handleNoMediaFoundEscalation` when the new google-OAuth-only guard from Phase 02 Step 02.3 triggers - i.e. only when the extractor returned `NoMediaFound` / `SocialPreviewOnly` for a google-domain URL. The existing `authOfferShown` guard ensures the toast fires at most once per Activity instance. No session-scoped companion flag needed. Phase 04 Step 04.2 takes the "once-per-failed-extract" branch (no `s0281NoteShown` flag); Step 04.3 emits the toast in the escalation handler before `cleanupAndFinish()`.
