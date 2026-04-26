# Spec Audit: network-smb-pooling

**Strategic spec:** [`spec_network-smb-pooling.md`](spec_network-smb-pooling.md)
**Tactical plan:** [`spec_network-smb-pooling/INDEX.md`](spec_network-smb-pooling/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full (strategic + all phases)
**Flags:** —
**Outcome:** Verified

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 28 |
| PASS | 26 |
| WARN | 0 |
| FAIL | 0 |
| MANUAL | 2 |
| EXEMPT | 0 |

All spec predicates pass. Two MANUAL items require device-level verification: browse/scanner regression (§11.5) and actual playback recovery behaviour on a real SMB session. Pre-existing `Thread.sleep()` calls in `readInternal()` (lines 436, 467) are out of spec scope — noted as a follow-up recommendation only.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Phase(s) | Status | Action |
|---|------|----------|:------:|--------|
| 1 | ExoPlayer uses only fresh/validated PLAYER connections | 01, 02 | PASS | — |
| 2 | After watchdog: one predictable recovery path | 02 | PASS | — |
| 3 | Retry doesn't reuse same stale SMBJ cache | 01, 03 | PASS | reopenConnection now invalidates before fresh connect |
| 4 | SMB errors classified retryable vs non-retryable | 04 | PASS | SmbPlaybackErrorCategory added |
| 5 | Logs distinguish failure types | 01, 04 | PASS | [SMB-PLAY] + state name in watchdog logs |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence |
|---|-----------|--------------|:------:|----------|
| 1 | LOC: SmbConnectionManager ≤ 1000 | wc -l | PASS | 998 LOC |
| 2 | Timber only, no Log.d | grep Log\.d in changed files | PASS | NONE |
| 3 | Changes in data/network/ only | file list | PASS | All 5 changed files in data/network/ |
| 4 | Browse/scanner not degraded | withConnection signature | PASS | Line 263 unchanged |
| 5 | Activity logic prohibited | — | EXEMPT | No Activity/Fragment touched |

### 2.3 Open Research Items (§6)

All three questions status CLOSED (resolved via code review 2026-04-26). No open items.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence |
|---------|:------:|----------|
| `docs/FEATURES.md` | EXEMPT | §8 explicitly states "No FEATURES doc update required" |
| `docs/FEATURES_RU.md` | EXEMPT | Same |
| `docs/FEATURES_UK.md` | EXEMPT | Same |

### 2.5 Completion Criteria (§11)

| # | Criterion | Status | Evidence |
|---|-----------|:------:|----------|
| 1 | SMB video open doesn't hang forever on stale pooled connection | PASS | fail-fast in open() + existing isConnectionValid + watchdog |
| 2 | After watchdog timeout: max one clean retry | PASS | isRecentWatchdog → immediate IOException on 2nd attempt |
| 3 | Second failure gives single clear error, not carousel of errorCode=2000 | PASS | IOException "SMB playback fail-fast" message at line 121 |
| 4 | Logs distinguish failure types | PASS | `[SMB-PLAY]` + `state=` context in watchdog logs |
| 5 | Browse/scanner SMB flow doesn't regress | MANUAL | withConnection unchanged; device test required |
| 6 | reopenConnection() uses pool manager instead of raw SMBClient | PASS | lines 514–515 confirmed |

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence |
|-------|:------:|----------|
| 4 phases declared, all files exist | PASS | phase-01..04.md confirmed |
| SmbConnectionManager.kt LOC ≤ 1000 | PASS | 998 |
| SmbDataSource.kt LOC < 1000 | PASS | 607 |
| SmbErrorClassifier.kt LOC < 200 | PASS | 137 |

### 3.2 Phase 01 — SmbPlaybackConnectionTracker

**Outcome:** Verified

| Predicate | Status | Evidence |
|-----------|:------:|----------|
| SmbPlaybackConnectionTracker.kt exists | PASS | File confirmed |
| `@Singleton` + `PlaybackConnectionState` enum | PASS | Lines 21, 23 |
| All required API methods present | PASS | Lines 32, 36, 46, 48, 58, 64 |
| SmbConnectionManager has tracker constructor param | PASS | Line 58 |
| resetAllConnections() calls clearAll() | PASS | Line 832 |
| handleNetworkReconnect() calls clearAll() | PASS | Line 979 |
| SmbClient.playbackConnectionTracker field | PASS | Line 66 |
| SmbConnectionManager.kt LOC ≤ 1000 | PASS | 998 |

### 3.3 Phase 02 — Fail-fast + State Integration

**Outcome:** Verified

| Predicate | Status | Evidence |
|-----------|:------:|----------|
| open() fails fast when isRecentWatchdog | PASS | Lines 120–121 |
| open() calls onConnectionCreated before future | PASS | Line 128, before line 130 |
| open() watchdog: recordWatchdog before invalidateExoPlayer | PASS | Line 134 < line 140 |
| read() watchdog: recordWatchdog before invalidateExoPlayer | PASS | Line 304 < line 310 |
| openInternal() success: clearWatchdog + onConnectionValidated | PASS | Lines 267–268 |
| Local isTransportOrBrokenPipe absent from companion | PASS | 0 grep hits |
| SmbDataSource.kt LOC < 1000 | PASS | 607 |

### 3.4 Phase 03 — Fix reopenConnection()

**Outcome:** Verified

| Predicate | Status | Evidence |
|-----------|:------:|----------|
| reopenConnection() has NO SMBClient( | PASS | 0 grep hits |
| reopenConnection() has NO SmbConfig.builder() | PASS | 0 grep hits |
| reopenConnection() has NO Thread.sleep() | PASS | Thread.sleep at lines 436, 467 is in readInternal(), not reopenConnection() |
| invalidateExoPlayerConnection before getConnectionForExoPlayer in reopenConnection | PASS | Line 514 < line 515 |
| resolveSmbPath() method exists | PASS | Line 541 |
| openInternal() uses resolveSmbPath() | PASS | Line 162 |

### 3.5 Phase 04 — SmbPlaybackErrorCategory + Log Tagging

**Outcome:** Verified

| Predicate | Status | Evidence |
|-----------|:------:|----------|
| SmbPlaybackErrorCategory enum in SmbErrorClassifier.kt | PASS | Line 19 |
| [SMB-PLAY] count in SmbDataSource.kt > 3 | PASS | 5 occurrences |
| SmbErrorClassifier.kt LOC < 200 | PASS | 137 |

---

## 4. Cross-Reference Checks

- ADR-1 (SMB spec separate from VR) → tactical phases contain no VR file references — PASS.
- ADR-2 (playback-specific fix, not global) → all changes scoped to `SmbDataSource` + `SmbConnectionManager` + new tracker — PASS.
- Goal §2.3 (retry not on same SMBJ cache) ↔ Phase 03 (invalidateExoPlayerConnection before getConnectionForExoPlayer in reopenConnection) — PASS.

---

## 5. Manual Acceptance Signals

- [ ] §11.5: Device test — browse/list SMB directory after playback watchdog fires, verify no regression.
- [ ] §11.2: Device test — trigger SMB watchdog on Quest 3, confirm no second 12-second hang on retry.

---

## 6. Observations (non-blocking)

- `readInternal()` lines 436, 467: pre-existing `Thread.sleep()` calls for retry backoff. These run on the watchdog executor thread (daemon), so they don't block the main thread. Not prohibited by spec but worth replacing with proper backoff in a future cleanup pass.
