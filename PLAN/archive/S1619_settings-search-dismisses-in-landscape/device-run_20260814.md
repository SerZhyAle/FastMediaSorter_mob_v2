# S1619 - device test run (settings search dismisses in landscape)

**Date:** 2026-08-14
**Device:** RFCR110NBQJ - SM-G996U1, Android 15 (SDK 35), 1080x2400 @ 450dpi
**Package:** com.sza.fastmediasorter.debug, versionName 2.60.8112.319-DEBUG, flavor standard debug
**Per-app locale:** ru-RU (checked before the Maestro runs)
**Log capture:** streamed to a disposable sink, started 22:39:48, first UI action 22:40:2x, stopped after the last Maestro run. The raw 22 MB journal is deliberately not kept; the verdict-bearing lines are quoted in this file and reproduce with `pwsh -NoProfile -File scripts/devtest/adb.ps1 log -Tail 4000 -Grep "S1619|SettingsActivity"`

Build currency was proven positively, not assumed: the `S1619:` probe is a build-unique marker and it
fired 24 times in the captured window, so the installed APK carries the fix.

## Coverage map

- Criterion 1 (landscape search button opens the overlay, Settings stays) - automatable, exercised.
- Criterion 2 (overlay close button closes the overlay, not the screen) - automatable, exercised.
- Criterion 3 (portrait unchanged) - automatable, exercised.
- Criterion 4 (both Maestro flows in both orientations) - automatable, exercised.

## Run log

| # | Step | Orientation | Result | Evidence |
| --- | --- | --- | --- | --- |
| 1 | Lock landscape, launch app, open Settings | landscape | PASS | display cur=2400x1080, mCurrentRotation=ROTATION_90; ui_land_settings.xml |
| 2 | Read searchButton bounds | landscape | PASS | `[2085,68][2265,203]` - right edge 135px clear of the 2400px screen edge |
| 3 | Tap searchButton centre (2175,135) | landscape | PASS | searchOverlay + searchInput + searchCloseButton + searchResultsRecycler present; topResumedActivity still SettingsActivity; ui_land_overlay.xml, screens/land_01_overlay_open.png |
| 4 | Tap searchCloseButton centre (2180,287) | landscape | PASS | all four search elements gone, searchButton back, topResumedActivity still SettingsActivity; ui_land_closed.xml |
| 5 | Rotate portrait, tap searchButton centre (990,139) | portrait | PASS | overlay opens; ui_port_overlay.xml, screens/port_01_overlay_open.png |
| 6 | Tap searchCloseButton centre (989,293) | portrait | PASS | overlay gone, SettingsActivity still top; ui_port_closed.xml |
| 7 | maestro settings_search.yaml | portrait | PASS | exit 0, temp/settings_search_maestro_20260814_2242.log |
| 8 | maestro settings_search_navigates.yaml | portrait | FAIL | exit 3, fails at the final `rowLanguage` assertion only; temp/settings_search_navigates_maestro_20260814_2243.log |
| 9 | maestro settings_search.yaml | landscape | PASS | exit 0, rotation held at 2400x1080 across the run; temp/settings_search_maestro_20260814_2247.log |
| 10 | maestro settings_search_navigates.yaml | landscape | FAIL | exit 3, same final assertion, same cause; temp/settings_search_navigates_maestro_20260814_2248.log |

## Log findings

- Probe values: `S1619: side insets left=75 right=135` in landscape (10 lines), `left=0 right=0` in portrait (14 lines).
- The landscape right inset of 135px is not zero, so the system does report a side inset and the root
  cause named in the spec holds. 135px is exactly the gap now visible between the search button's right
  edge (2265) and the screen edge (2400) - the fix consumes the inset it measures.
- The landscape left inset of 75px is the display cutout, which sits on the left at ROTATION_90.
- `search-log.ps1 -Errors -Unique -AppOnly`: no matches. The only exception blocks in the capture are
  Samsung system-process `LoadedApk ... Looper.isPerfLogEnable()` NPEs from foreign pids.

## Why the `settings_search_navigates.yaml` failure is not this ticket

The flow's last action is `tapOn: id searchResultsRecycler, index 0, childOf searchOverlay`. That
selector matches the RecyclerView container itself, not a result row, so Maestro taps the container's
geometric centre and lands on whichever row happens to sit there. With the query `Lang` the list holds
four results and the centre falls on the fourth, "Subtitle language for streams" - so the flow navigates
to the streams section and `rowLanguage` is correctly absent.

S1619 does shorten the recycler in portrait, because the overlay now takes `overlayPadding + navBar.bottom`
at the bottom: the recycler ends at y=2242 instead of the full 2400, moving its centre from y≈1429 to
y≈1350. Both points were tapped by hand on this build and both land on the same fourth result and the
same destination section, so removing the S1619 padding would not make the assertion pass. The failure
is a flow-authoring defect that predates this ticket and is orientation-independent (identical step and
identical assertion fail in portrait and in landscape).

## Recommended follow-ups

- `/spec-draft` - `maestro/features/settings/settings_search_navigates.yaml` taps the results container
  instead of the first result row, so its `rowLanguage` assertion fails in both orientations regardless
  of S1619. Not parked by this run: the sweep brief forbids catalog mutation here.
