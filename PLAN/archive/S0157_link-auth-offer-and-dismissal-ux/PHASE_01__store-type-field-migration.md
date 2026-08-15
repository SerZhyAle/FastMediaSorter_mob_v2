# Phase 01 — store-type-field-migration

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 8 / 8
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Extend `EncryptedCookieStore` with a `type` field ("active" | "dismissed") in `AccountEntry`, a `saveAsDismissed()` method for dismissed-record storage, and a one-time wipe migration that clears all existing `acct:` / `domain:` entries on first app run after S0157 ships.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(Foundation phase — no dependencies.)*
- [ ] Working tree is clean or on a feature branch.
- [ ] File `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` read before editing (done by implementer before touching).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` | Modified | ≤ 420 |

---

## Steps

### Step 01.1 — Add `type` field to `AccountEntry`

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `EncryptedCookieStore.AccountEntry`, add field `val type: String = "active"`. Add companion constant `const val TYPE_ACTIVE = "active"` and `const val TYPE_DISMISSED = "dismissed"` inside the `companion object` of `EncryptedCookieStore`. Make `DISMISSED_ACCOUNT_ID = "__dismissed__"` public (it was already declared or add it here).

**Verification:**

- `Grep` — `val type: String = "active"` present in `EncryptedCookieStore.kt`.
- `Grep` — `const val TYPE_ACTIVE = "active"` present.
- `Grep` — `const val TYPE_DISMISSED = "dismissed"` present.
- `Grep` — `const val DISMISSED_ACCOUNT_ID` present and accessible outside companion (no `private`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 4/4 PASS. `val type: String = TYPE_ACTIVE` in `AccountEntry`; `TYPE_ACTIVE`, `TYPE_DISMISSED`, `DISMISSED_ACCOUNT_ID` in public `companion object`. Files: EncryptedCookieStore.kt (+11 LOC).

---

### Step 01.2 — Read and write `type` in `loadAccountEntry()`

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `loadAccountEntry()`, after parsing existing JSON fields, add: `val type = root.optString("type", TYPE_ACTIVE)`. Pass it to `AccountEntry(... type = type)`. The default `TYPE_ACTIVE` ensures backward compat with entries written before S0157.

**Verification:**

- `Grep` — `optString("type", TYPE_ACTIVE)` present in `EncryptedCookieStore.kt`.
- `Grep` — `AccountEntry(` call site includes `type =` argument.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. `optString("type", TYPE_ACTIVE)` and `type = type` in `loadAccountEntry`. Files: EncryptedCookieStore.kt (+3 LOC).

---

### Step 01.3 — Write `type = TYPE_ACTIVE` in `saveForAccount()`

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `saveForAccount()`, add `.put("type", TYPE_ACTIVE)` to the `JSONObject` payload (after `"lastUsedAtEpochMillis"`). This stamps every new active session so the field is always present in newly written entries.

**Verification:**

- `Grep` — `.put("type", TYPE_ACTIVE)` present inside `saveForAccount` (content search in file, not just file match).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 1/1 PASS. `.put("type", TYPE_ACTIVE)` added to `saveForAccount` payload. Files: EncryptedCookieStore.kt (+1 LOC).

---

### Step 01.4 — Add `saveAsDismissed(host: String)` method

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add method `fun saveAsDismissed(host: String)` to `EncryptedCookieStore`. It must write key `keyForAccount(host, DISMISSED_ACCOUNT_ID)` with a minimal JSON payload: `accountId = DISMISSED_ACCOUNT_ID`, `displayName = ""`, `type = TYPE_DISMISSED`, `savedAtEpochMillis = System.currentTimeMillis()`, `lastUsedAtEpochMillis = 0`, `cookies = []`. No cookies array needed — set `put("cookies", JSONArray())`.

**Verification:**

- `Grep` — `fun saveAsDismissed(host: String)` present in `EncryptedCookieStore.kt`.
- `Grep` — `TYPE_DISMISSED` referenced in the method body.
- `Grep` — `DISMISSED_ACCOUNT_ID` referenced in the method body.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 3/3 PASS. `saveAsDismissed`, `TYPE_DISMISSED`, `DISMISSED_ACCOUNT_ID` all present. Files: EncryptedCookieStore.kt (+16 LOC).

---

### Step 01.5 — Add `hasDismissedRecord(host: String): Boolean`

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add `fun hasDismissedRecord(host: String): Boolean` — returns true if key `keyForAccount(host, DISMISSED_ACCOUNT_ID)` exists in prefs AND the loaded entry has `type == TYPE_DISMISSED`. Use `loadAccountEntry(host, DISMISSED_ACCOUNT_ID)?.type == TYPE_DISMISSED`.

**Verification:**

- `Grep` — `fun hasDismissedRecord(host: String): Boolean` present in `EncryptedCookieStore.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 1/1 PASS. `hasDismissedRecord` declared. Files: EncryptedCookieStore.kt (+3 LOC).

---

### Step 01.6 — Filter `listAccounts(host)` to `type=active` only

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> In `listAccounts(host: String)`, append `.filter { it.type == TYPE_ACTIVE }` before `sortedByDescending { it.savedAt }`. This prevents dismissed records from appearing in account selection dialogs, cookie lookups, and `hasAnySession()`.

**Verification:**

- `Grep` — `.filter { it.type == TYPE_ACTIVE }` present in `listAccounts` body.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 1/1 PASS. `.filter { it.type == TYPE_ACTIVE }` added to `listAccounts`. Files: EncryptedCookieStore.kt (+1 LOC).

---

### Step 01.7 — Ensure `listAllAccounts()` includes dismissed entries

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.6

**Prompt for developer:**

> `listAllAccounts()` must return ALL entries (including dismissed) so `AuthSessionRepositoryImpl.refreshFlows()` can handle them. Verify the existing implementation does NOT filter by type — it currently maps all `acct:` prefixed keys. If it does filter, remove that filter. No change needed if it already returns all entries. Add a KDoc comment: `// Returns all entries including dismissed (type=dismissed); callers must filter as appropriate.`

**Verification:**

- `Grep` — `// Returns all entries including dismissed` present in `EncryptedCookieStore.kt`.
- `Grep` — `listAllAccounts` does NOT reference `TYPE_ACTIVE` filter (would mean dismissed entries excluded).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. KDoc comment present; `listAllAccounts` body has no `TYPE_ACTIVE` filter. Files: EncryptedCookieStore.kt (+1 LOC).

---

### Step 01.8 — Add one-time wipe migration in `migrateIfNeeded()`

**Files:** `data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.7

**Prompt for developer:**

> S0157 migration: all existing auth records must be wiped once on first update (the feature was never released; no user data to preserve). In `migrateIfNeeded()`, BEFORE the S0155 legacy-key migration block, add:
>
> ```kotlin
> // S0157: one-time wipe of all auth records (feature never released; no user data to preserve).
> val metaPrefs = context.getSharedPreferences("link_download_cookies_meta", Context.MODE_PRIVATE)
> if (!metaPrefs.getBoolean("s0157_wiped", false)) {
>     val allKeys = prefs.all.keys.toList()
>     if (allKeys.isNotEmpty()) {
>         val editor = prefs.edit()
>         allKeys.forEach { editor.remove(it) }
>         editor.apply()
>         Timber.i("EncryptedCookieStore: S0157 one-time wipe removed %d key(s)", allKeys.size)
>     }
>     metaPrefs.edit().putBoolean("s0157_wiped", true).apply()
> }
> ```
>
> After the wipe runs, the S0155 migration block that follows will find no legacy keys — that is correct.

**Verification:**

- `Grep` — `"s0157_wiped"` present in `EncryptedCookieStore.kt`.
- `Grep` — `"link_download_cookies_meta"` present.
- `Grep` — `Log\.d\(` returns zero hits in `EncryptedCookieStore.kt` (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 3/3 PASS. `"s0157_wiped"`, `"link_download_cookies_meta"` present; zero `Log.d(` hits. Files: EncryptedCookieStore.kt (+15 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `EncryptedCookieStore.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `AccountEntry` now has `type: String`; `TYPE_ACTIVE` / `TYPE_DISMISSED` / `DISMISSED_ACCOUNT_ID` are public constants.
- `listAccounts(host)` returns only active entries — all callers that iterate accounts get active-only.
- `listAllAccounts()` returns active + dismissed — used only by `AuthSessionRepositoryImpl.refreshFlows()`.
- The one-time wipe runs in `migrateIfNeeded()` before any other migration; subsequent calls are no-ops (flag is set).

---

## Rollback Plan

Revert the single commit touching `EncryptedCookieStore.kt`. No schema migration, no user-data at risk (the wipe is intentional and one-shot).
