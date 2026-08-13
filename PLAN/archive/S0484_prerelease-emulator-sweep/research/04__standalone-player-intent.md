# Research §6.4 - Standalone player launch + return to app

**Strategic item:** §6.4
**Status:** Resolved
**Date:** 2026-06-17

## Question

How to launch the standalone player for a file via intent and return into the app.

## Findings

- Specialized standalone hosts (`PhotoVideoStandaloneActivity`, `AudioStandaloneActivity`, `DocumentStandaloneActivity`, `TextStandaloneActivity`) are `exported=false`; reachable only via manifest activity-aliases (`.StandaloneVideoPlayer`, `.StandaloneImagePlayer`, etc.).
- Those aliases are `android:enabled="false"` by default; enabled at runtime only when the user turns on "Use as primary media player". For adb they can be force-enabled: `adb shell pm enable <pkg>/com.sza.fastmediasorter.StandaloneVideoPlayer`.
- Legacy `StandalonePlayerActivity` is `exported=true` unconditionally (deprecated under S0393) - the simplest direct adb target, no alias enablement needed.
- FileProvider authority `${applicationId}.fileprovider` covers all external storage; `file://` URIs work for direct `am start` from adb (adb does not trip FileUriExposedException).
- Return-to-app: overflow menu `R.id.btnOverflowMenu` → item `R.id.menu_open_in_fms` (title `@string/open_in_fms`) → `StandaloneFileOperationsHandler.openInFms()`. For a local file it launches `PlayerActivity` (in-app player) and finishes the standalone host; for unresolvable (network/SAF-only) it lands on `MainActivity`.
- Launch log markers (DEBUG): `StandalonePlayer[debug]: launch action=... type=...`, and `StandalonePlayer: incoming uri=... mime=... name=...`. No Timber marker on landing in `PlayerActivity` - confirm via `adb shell dumpsys activity top`.

## Decision

- Use the **legacy exported `StandalonePlayerActivity`** as the standalone-launch target for the sweep (no alias enablement, always reachable):

  ```
  adb shell am start -a android.intent.action.VIEW \
    -d "file:///sdcard/Download/FastMediaSorter_Test/<seeded-file>" \
    -t "video/mp4" \
    -n com.sza.fastmediasorter.debug/.ui.player.StandalonePlayerActivity
  ```

  (the seeded local file is always resolvable, so the `Open in FMS` path lands in `PlayerActivity`.)
- Roundtrip via mobile-mcp: tap `btnOverflowMenu`, tap the `open_in_fms` item, then confirm foreground = `PlayerActivity` via `dumpsys activity top`.
- Watch the `StandalonePlayer[debug]: launch` and `StandalonePlayer: incoming uri` markers to confirm standalone launch; use `dumpsys activity top` to confirm the return landing (no log marker exists).
- **S0393 risk:** `StandalonePlayerActivity` is slated for removal. If S0393 removes it before S0484 implements, switch to `pm enable` of the specialized alias and target that. Recorded as a dependency note, not a new ticket (S0393 already tracks the deprecation).

## Impact on plan

- Phase 05 scenario step uses the `am start` above for standalone launch and the overflow→`open_in_fms` roundtrip.
- Phase 05 references S0393 as a watch item for the launch target.
