# Tactical Spec: network-smb-pooling

**Strategic:** `PLAN/spec_network-smb-pooling.md`
**Status:** Verified

## Phases

| Phase | File | Summary | Status |
| ----- | ---- | ------- | ------ |
| 01 | [phase-01.md](phase-01.md) | `SmbPlaybackConnectionTracker` — state model + watchdog tracking | [x] |
| 02 | [phase-02.md](phase-02.md) | Fail-fast + state integration in `SmbDataSource` | [x] |
| 03 | [phase-03.md](phase-03.md) | Fix `reopenConnection()` — use pool manager | [x] |
| 04 | [phase-04.md](phase-04.md) | `SmbPlaybackErrorCategory` + `[SMB-PLAY]` log tagging | [x] |

## Files Touched

| File | Phase | Change |
| ---- | ----- | ------ |
| `data/network/SmbPlaybackConnectionTracker.kt` | 01 | CREATE |
| `data/network/SmbConnectionManager.kt` | 01 | EDIT — inject tracker, clearAll calls, fix garbled comment |
| `data/network/SmbClient.kt` | 01 | EDIT — add `playbackConnectionTracker` field |
| `data/network/datasource/SmbDataSource.kt` | 02, 03 | EDIT — fail-fast, watchdog recording, reopenConnection fix |
| `data/network/SmbErrorClassifier.kt` | 04 | EDIT — SmbPlaybackErrorCategory enum |

## LOC Budget (actual)

| File | Final |
| ---- | ----- |
| `SmbConnectionManager.kt` | 998 |
| `SmbClient.kt` | 955 |
| `SmbDataSource.kt` | 607 |
| `SmbErrorClassifier.kt` | 137 |
| `SmbPlaybackConnectionTracker.kt` | 69 |
