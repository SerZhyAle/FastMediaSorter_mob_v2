# Phase 02 — SMB timeout entrypoint (Pillar B)

**Strategic spec:** [`../S0228_bugfix-smb-idle-disconnect-timer-race.md`](../S0228_bugfix-smb-idle-disconnect-timer-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Route SMB idle-expiry through one named callback helper in `SmbConnectionManager` so the shared-layer generation guard remains the single owner of stale suppression, while the SMB path exposes one stable cleanup entrypoint for later verification.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.2 is Resolved — stale-callback suppression is assigned to the chosen layer.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` is 1047 LOC — timestamped backup in `temp/` is required before edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 1090 |
| `temp/SmbConnectionManager.kt.2026-05-16__pre-S0228-phase02.bak` | New (backup) | n/a |

> File >500 LOC → backup step is mandatory (Step 02.0).

---

## Steps

### Step 02.0 — Snapshot the manager file

**Files:** `temp/SmbConnectionManager.kt.2026-05-16__pre-S0228-phase02.bak`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` to `temp/SmbConnectionManager.kt.2026-05-16__pre-S0228-phase02.bak` byte-for-byte. This is a mandatory safeguard before editing any file above the 500-LOC threshold.

**Verification:**

- `Glob` — `temp/SmbConnectionManager.kt.2026-05-16__pre-S0228-phase02.bak` exists.
- expected: SHA-256 of backup equals SHA-256 of current source | actual: <fill at execution>.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Files: `temp/SmbConnectionManager.kt.2026-05-16__pre-S0228-phase02.bak` (new backup). expected SHA-256 = actual SHA-256 = `0C8F23A526128DB720A3F5971A93CC55E6351C82FEA7E0B98C4E30C2FA740871`.

---

### Step 02.1 — Extract a single SMB idle-timeout helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.0

**Prompt for developer:**

> Add `private fun handleIdleTimeout(transportKey: String, key: ConnectionKey)` next to the existing idle-helper methods. The helper performs only local cleanup orchestration: log `IdleDisconnect: SMB timeout callback accepted (transport=%s)` at `Timber.d`, then call `pool.removeAndCloseAsync(key)`. Add a WHY-comment explaining that stale generations are filtered in `IdleDisconnectPolicyImpl`, so this helper must stay transport-cleanup only and must not add probes, retries, or generation checks of its own.

**Verification:**

- `Grep` — `private fun handleIdleTimeout` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` — `IdleDisconnect: SMB timeout callback accepted` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` — `pool.removeAndCloseAsync(key)` matches at least once in `SmbConnectionManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` (+6 LOC, single SMB idle-timeout helper extracted). Dev log recorded.

---

### Step 02.2 — Make `armIdleTransport` delegate through the helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update `armIdleTransport` so the `idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS)` callback delegates only to `handleIdleTimeout(transportKey, key)`. Leave `rememberTransportKey`, `touch`, `disarmIdleTransport`, and `disarmAllIdleTransports` semantically unchanged. Do not add `healthProbe`, `reachabilityGate`, `getClient`, `connect`, or reconnect logic anywhere in the new helper or callback path — strategic §3.2 forbids extra hot-path network work.

**Verification:**

- `Grep` — `handleIdleTimeout(transportKey, key)` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` — `idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS)` still matches exactly once in `SmbConnectionManager.kt`.
- `Grep` — `trackedTransportKeys.forEach(idleDisconnectPolicy::disarm)` still matches exactly once in `SmbConnectionManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` (+1 LOC net in callback body, `armIdleTransport` now delegates only through `handleIdleTimeout`). Dev log recorded.

---

### Step 02.3 — Keep the callback path singular and probe-free

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Audit the idle helper block after the extraction and remove any duplicate inline `pool.removeAndCloseAsync` callback body that would bypass `handleIdleTimeout`. The only SMB idle-timeout cleanup entrypoint after this phase must be `handleIdleTimeout`. If comments around `armIdleTransport` still imply best-effort duplicate firing, update them to reflect the new shared-layer ownership invariant without mentioning ticket ids.

**Verification:**

- `Grep` — `private fun handleIdleTimeout` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` — `IdleDisconnect: SMB timeout callback accepted` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` — `handleIdleTimeout(transportKey, key)` matches exactly once in `SmbConnectionManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` (audit-only closeout: singular callback path confirmed, no duplicate inline idle cleanup remained). No additional source edits required.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `./build-debug.PS1` PASS (`assembleStandardDebug`, 58s, 2026-05-16).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep` for `IdleDisconnect: SMB timeout callback accepted` returns exactly one match in `SmbConnectionManager.kt`.
- [x] `Grep` for `Log\.d\(` returns zero hits in `SmbConnectionManager.kt`.
- [x] Dev log entry added for `SmbConnectionManager.kt` via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 04.

---

## Handoff Notes to Next Phase

After Phase 02, SMB has one named timeout-cleanup entrypoint and no duplicate inline callback body. Phase 03 can now pin exact-once behaviour with unit tests and the SMB transport-key contract without guessing where cleanup starts.

---

## Rollback Plan

Restore `temp/SmbConnectionManager.kt.2026-05-16__pre-S0228-phase02.bak`, then revert the Phase 02 commit(s).