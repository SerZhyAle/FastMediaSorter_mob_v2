# S1250 - Triage three unit-test failures that were invisible until the suite stopped truncating

**Status:** Archived
**Priority:** 55

## 0. Raw capture

Surfaced 2026-07-28 by **S1244**, which raised the test worker's heap so the suite ran to the end for the first time. Three failures that had been hiding past the truncation point:

```
IconInventoryExportTest > committed icon inventory is fresh FAILED
ExecuteScheduledOperationUseCaseTest > move executes native move operation FAILED
CameraRuntimeCapabilitiesTest > digital cap extends presets beyond the lens optical max FAILED
```

Reproduce: `.\a.ps1 fu`, or per class via `check-standard-fast.ps1 -Mode Unit -Tests "*IconInventoryExportTest"`.

## 1. Why one ticket and not three

Because the work here is **triage**, and it has not been done. Each of these is either a stale expectation or a real defect, and which one it is decides everything - the severity, the fix, and whether it deserves its own ticket at all. Splitting them now would mean writing three tickets whose entire content is "unknown", and inventing three separate priorities for problems nobody has looked at.

Split them once they are diagnosed. Anything that turns out to be a real defect gets its own ticket with its own evidence; anything that turns out to be a stale expectation is closed here.

## 2. First read of each, explicitly not a diagnosis

Offered as a starting point, not a conclusion. Verify before acting on any of it.

- `IconInventoryExportTest > committed icon inventory is fresh` - the name suggests a generated artifact (`docs/` icon inventory) has drifted from its source. That class of test usually fails because the committed file was not regenerated, which makes it a *reporting* failure, not a product defect. Check whether the export toggle (`icon.inventory.generate`, wired through `testOptions`) is what governs it.
- `ExecuteScheduledOperationUseCaseTest > move executes native move operation` - a scheduled-operation use case failing on the move path is the one of the three most likely to be a genuine defect: it touches real file operations. Treat as the priority of the three until shown otherwise.
- `CameraRuntimeCapabilitiesTest > digital cap extends presets beyond the lens optical max` - zoom-preset arithmetic against a lens maximum. Could be either a changed preset table or a real off-by-one at the boundary.

## 3. The reason they went unnoticed

Nothing to do with these three. `testStandardDebugUnitTest` ran on Gradle's default 512 MB worker heap and the JVM died around `data.remote.ftp.*`, so `domain.*`, `ui.*` and `util.*` never executed - while Gradle still printed a normal-looking "946 tests completed, 1 failed". See **S1244**, which fixed the heap and added `scripts/quality/assert-test-suite-complete.ps1` so a truncated run cannot pass as a finished one again.

## 4. Related

- **S1244** - the truncation fix that exposed these.
- **S1245**, **S1246**, **S1249** - the other failures from the same run, each already parked with a diagnosis, which is why they are not in this ticket.

## 5. Triage result (2026-07-28, spec-next loop)

Each of the three dispatched per §1's rule - own ticket if real, closed here if stale/covered:

- `IconInventoryExportTest > committed icon inventory is fresh` - stale committed artifact, as
  §2 guessed. Already ticketed as **S1194** (`icon-inventory-stale-settings-header-entry`,
  Draft): same root - `docs/icons/icon-inventory.json` behind the sources; the red test and the
  advisory `icon-inventory-sync` gate failure are two projections of it. Regeneration via the
  test's generate mode is S1194's own scope.
- `ExecuteScheduledOperationUseCaseTest > move executes native move operation` - already
  ticketed as **S1204** (`broken-test-move-executes-native-move`, Draft). §2's "most likely a
  genuine defect" call stays recorded there; nothing to duplicate here.
- `CameraRuntimeCapabilitiesTest > digital cap extends presets beyond the lens optical max` -
  **no longer failing**. Targeted run (`ui.cameracapture.model.CameraRuntimeCapabilitiesTest`,
  16:31): BUILD SUCCESSFUL; the complete forkEvery-validated suite run of 15:56 (410 reports,
  ratio 1) also shows it green. Resolved by intervening work between the 02:47 capture and now;
  no residual action.
- Note for the record: today's full runs carry a different red set (CameraCaptureSaver - closed
  by **S1246**; the two above; `StreamLogoAtlasSlicerTest` x3 - ticketed **S1245**). No
  unticketed red remains.

expected: every §0 failure ends ticketed or green | actual: S1194, S1204, one green - PASS.

## Last Audit

**Date:** 2026-07-28. **Verdict:** Verified.

- Triage-only ticket: all three §0 failures dispatched (two pre-existing tickets confirmed to
  cover them, one resolved meanwhile with fresh targeted + full-suite evidence).
- No code changed here; the fix work lives in S1194/S1204 by design (§1).
