# Phase 2 — Update PrefetchLoadControlFactory

## Goal

Wire `PauseAwareLoadControl` into all network (and local) ExoPlayer instances via the single
factory entry point.

## Steps

1. Edit `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt`
2. Build `DefaultLoadControl` as before
3. Return `PauseAwareLoadControl(defaultLoadControl)` instead of the raw `DefaultLoadControl`
4. Add import for `PauseAwareLoadControl`

## Verification

- [x] `PrefetchLoadControlFactory.build()` returns instance of `PauseAwareLoadControl`
- [x] Existing buffer duration parameters are unchanged (only the wrapper changes)
- [x] All 5 callers (SFTP, SMB, FTP, Cloud, Local) receive the wrapped control automatically
- [x] No callers need to be touched
