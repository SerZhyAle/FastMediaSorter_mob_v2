# Tactical Spec: S0051 — Network DataSource Pause/Cancel

**Status:** Tactical
**Strategic spec:** `PLAN/S0051_bugfix-network-datasource-pause-cancel.md`

---

## Research Findings (resolved §6 inline)

**Q1 — ExoPlayer behaviour with early END_OF_INPUT / IOException**
Resolved: neither END_OF_INPUT nor IOException is used. Blocking reads in DataSources are
never reached when ExoPlayer's `shouldContinueLoading` returns `false`. No seek-recovery
issue exists because the Loader thread never issues new reads while paused.

**Q2 — LoadControl: stopping buffering on pause**
Resolved: override `DefaultLoadControl.shouldContinueLoading(LoadControl.Parameters)`.
When `parameters.playWhenReady == false` return `false` immediately; otherwise delegate to
`DefaultLoadControl`. This is the documented, ExoPlayer-native mechanism (Media3 1.2.1).
No custom `LoadControl` re-implementation required — Kotlin interface delegation (`by`) handles
all other methods.

**Q3 — SMB / FTP / Cloud audit**
Resolved: all four network DataSources (SFTP, SMB, FTP, Cloud) share a single code path
through `PrefetchLoadControlFactory.build()`. Fixing the factory covers all sources at once.
SMB already has watchdog-based thread interruption for stuck reads; FTP/Cloud have no
interruption mechanism. With the LoadControl fix, ExoPlayer stops calling `read()` during
pause, so those paths are never reached.

---

## Architecture Decision

`PauseAwareLoadControl` — new class in `ui/player/helpers/`:
- Implements `LoadControl` via Kotlin delegation to a `DefaultLoadControl` instance
- Overrides only `shouldContinueLoading`: returns `false` when `!parameters.playWhenReady`
- `PrefetchLoadControlFactory.build()` wraps its result in `PauseAwareLoadControl`
- Change radius: 2 files, 0 DataSource changes

---

## Phases

- [x] [Phase 1 — PauseAwareLoadControl](phase1.md)
- [x] [Phase 2 — PrefetchLoadControlFactory update](phase2.md)
- [x] [Phase 3 — Register PauseAwareLoadControl as Player.Listener in network helpers](phase3.md)
