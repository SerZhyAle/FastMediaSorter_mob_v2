# S1216 research 01 - Device profile preset matrix: coverage inventory

Measured on 2026-07-27 against the working tree.

Sources:

- Matrix: `app_v2/src/main/assets/device_profile_presets.csv`
- Applier: `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
- Model: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
- Coverage checker: `scripts/check_device_profile_presets.ps1`
- Developer doc: `dev/DEVICE_PROFILE_PRESET_MATRIX.md`

---

## 1. Headline numbers

Command: `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1`

```
AppSettings fields : 229
CSV rows           : 192
DeviceProfileTypes : 11
CSV profile columns: 11
AppSettings fields MISSING from CSV rows (40)
CSV rows with NO matching AppSettings field (3)
=> INCONSISTENT
```

Derived by cross-referencing rows against applier `when` branches (159 branches):

| Bucket | Count | Meaning |
|--------|------:|---------|
| Rows with at least one non-empty cell | 110 | The matrix actually does something |
| Rows fully empty across all 11 profiles | 82 | Row exists, differentiates nothing |
| Fully-empty rows that the applier CAN apply | 49 | Fixable by editing the CSV alone |
| Fully-empty rows with no applier branch | 33 | Needs an applier branch first, or is deliberately non-presettable |
| Rows with values but NO applier branch | **0** | No silent data loss today |
| `AppSettings` fields with no row at all | 40 | Never reached the matrix |
| Rows naming a field that no longer exists | 3 | Stale |

Good news: bucket "values but no branch" is empty, so no owner-authored cell is being silently
discarded right now. The failure mode is purely **absence**, not corruption.

## 2. Root cause: the coverage gate is not wired anywhere

`scripts/check_device_profile_presets.ps1` is correct and already reports the drift precisely.
A repo-wide search for its name returns hits **only inside the script itself** - it is referenced by
no gate, no `a.ps1` target, no `post-change.ps1` step, no CI workflow. Its own doc
(`dev/DEVICE_PROFILE_PRESET_MATRIX.md` section 4.6) still says "Consider wiring it into CI / a
pre-commit check". It was never wired, so 40 fields drifted in unnoticed.

The exit contract is fine - verified directly:

```
pwsh -NoProfile -Command "& { & pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 > $null 2>&1; Write-Output ('REAL_EXIT=' + $LASTEXITCODE) }"
REAL_EXIT=1
```

(An earlier reading of `EXIT=0` was a measurement error - piping the script into `tail` made `$?`
report `tail`'s status, not the script's. The script itself is gate-ready as written.)

So there is nothing to fix in the script's contract - the only missing piece is the call site.

## 3. Fields with no CSV row at all (40)

### 3.1 Launcher family (9) - highest product value, zero coverage

| Field | Type / default |
|-------|----------------|
| `launcherDensityFactor` | Float, 1.0 |
| `launcherTaskbarShowRecents` | Boolean, true |
| `launcherTaskbarShowPinned` | Boolean, true |
| `launcherTaskbarShowTray` | Boolean, true |
| `launcherReplaceSystemStatusArea` | Boolean, false |
| `launcherDesktopLocked` | Boolean, false |
| `launcherWallpaperMode` | String token, `BRANDED` (from `AppSettings.LAUNCHER_WALLPAPER_MODES`) |
| `launcherRotationHintShown` | one-shot hint state - non-presettable |
| `launcherWallpaperImagePath` | pointer - non-presettable |

A TV box, a car head unit and a photo frame are exactly the devices a launcher profile should shape,
and none of them get a single launcher default today.

### 3.2 Screenshot gesture zones, post-S0847 (28)

- `screenshotGestureZone{LeftTop,LeftBottom,RightTop,RightBottom}Enabled` - Boolean, true (4)
- `screenshotGesture{Zone}{Down,Right,Up}` - `ScreenshotGestureAction`, default `SILENT_SCREENSHOT` (12)
- `screenshotGesturePayload{Zone}{Down,Right,Up}` - String payload, pointer-like (11-12)

S0847 split the single left strip into four zones. The matrix still carries only the three
pre-S0847 rows (see section 4) and none of the new per-zone fields.

### 3.3 Misc (3)

- `streamsDefaultAudioLanguage`, `streamsDefaultSubtitleLanguage` - `StreamTrackLanguage`
  (`DEFAULT|ENGLISH|RUSSIAN|UKRAINIAN`)
- `cameraAspectRatio` - Int, 0

## 4. Stale rows (3)

`screenshotGestureActionDown`, `screenshotGestureActionRight`, `screenshotGestureActionUp`.

No matching `AppSettings` field. The applier still keeps legacy branches that redirect them onto the
`LEFT_TOP` band. All three rows are fully empty, so the branches are dead code. The developer doc
still cites `screenshotGestureActionDown=SILENT_SCREENSHOT` as a live enum example - stale.

## 5. Fully-empty rows the applier already supports (49)

### 5.1 Deliberately non-presettable - keep empty, declare as such

`language` (owned by the first-run language step), `isCacheSizeUserModified`,
`fileOpsOverflowMenuHintShown`, `showPlayerHintOnFirstRun`, `vrPlayerEntryPromptDismissed`,
`copyPanelCollapsed`, `rendererMigrationEnabled`, `isPrimaryMediaPlayer`,
`translationSourceLanguage`, `translationTargetLanguage` (locale-derived),
`ocrEngineType`, `paddleOcrModel` (flavor-gated engine choice),
plus the 3 stale gesture rows from section 4.

### 5.2 Worth differentiating - CSV-only work

| Group | Fields |
|-------|--------|
| Reader | `textReaderTheme`, `pdfScrollMode`, `pdfColorMode`, `epubLineHeight`, `epubHorizontalMargin`, `textSizeMax`, `showTextLineNumbers`, `markdownRendered`, `syntaxHighlighting` |
| Remote storage | `smbEnabled`, `sftpEnabled`, `ftpEnabled`, `googleDriveEnabled`, `oneDriveEnabled`, `dropboxEnabled` |
| Link download | `linkAutoDownloadEnabled`, `linkAutoDownloadOpenInPlayer`, `linkDownloadMaxResolution`, `linkDownloadAudioOnly`, `linkDownloadLoginWallHeuristicEnabled` |
| Player interaction | `nineZoneGridEnabled`, `playerFollowSystemRotation`, `gestureOverlayEnabled`, `showDetailedErrors` |
| Clipboard / capture | `copyScreenshotToClipboard`, `cameraCaptureCopyToClipboard`, `videoFrameCopyToClipboard`, `videoSnapshotFormat` |
| Automation | `enableScheduledOperations` |
| Sharing | `enabledShareTargets`, `disabledShareTargets` |
| OCR / translate | `translationLensStyle`, `ocrDefaultFontSize`, `ocrDefaultFontFamily` |

## 6. Fully-empty rows with no applier branch (33)

### 6.1 Must stay non-presettable (privacy, credentials, session state)

Credentials `defaultUser` / `defaultPassword`; every `*ResourceId`, `*Uri`, `lastUsedResourceId`,
`lastSelectedLocalFolder` pointer; consent flags `screenCaptureDisclosureAccepted`,
`screenRecordingDisclosureAccepted`, `enableStatistics`, `cameraGeotagEnabled`; runtime state
`scheduledOperationsPaused`.

The applier's `else -> skip(..)` comment already states this intent - it is just not machine-readable,
so the coverage checker cannot distinguish "intentionally absent" from "forgotten".

### 6.2 Candidates for a new applier branch

| Field | Type / default | Why a profile should decide it |
|-------|----------------|--------------------------------|
| `streamsDefaultSort` | `NAME\|TOPIC\|LANGUAGE\|COUNTRY\|RECENT`, `NAME` | A TV box wants topic/country order, a phone wants name |
| `streamsDefaultMediaFilter` | `ALL\|AUDIO\|VIDEO`, `ALL` | Audio player opens on radio, video player on TV streams |
| `streamsCatalogRefreshPolicy` | `MANUAL\|ON_OPEN\|PERIODIC_WIFI`, `ON_OPEN` | Car head unit is metered -> `MANUAL` |
| `showStreamsPanelInMainWindow` | Boolean, false | TV / car / media player want it on the main screen |
| `showProgramsPanelInMainWindow` | Boolean, false | TV box front page |
| `screenRecordingEnabled` | Boolean, false | Feature-shaping, not consent (disclosure is separate) |
| `screenshotGestureZone*StripVisible` | Boolean | Photo frame / TV should not show capture strips |
| `resourceTypeTabCollapsed`, `programsPanelCollapsed`, `streamsPanelCollapsed` | Boolean | Initial screen density per profile |
| `secureSensitiveScreens` | Boolean, true | Open question - see section 8 |

## 7. Proposed differentiated values (draft, owner to confirm)

Empty cell = keep default. `Other` stays empty by contract.

### 7.1 Reader block

| Field | phone | tablet | tv | car | media | frame | video | audio | ebook | vr |
|-------|-------|--------|----|-----|-------|-------|-------|-------|-------|----|
| `textReaderTheme` | | | DARK | DARK | | DARK | | DARK | SEPIA | DARK |
| `pdfColorMode` | | | NIGHT | NIGHT | | NIGHT | | NIGHT | SEPIA | NIGHT |
| `pdfScrollMode` | TRUE | TRUE | FALSE | FALSE | | FALSE | | | FALSE | FALSE |
| `epubLineHeight` | 1.6 | 1.7 | 1.9 | 1.9 | | | | | 1.5 | 1.8 |
| `epubHorizontalMargin` | 16 | 32 | 48 | 40 | | | | | 24 | 40 |
| `syntaxHighlighting` | | | FALSE | FALSE | | FALSE | | FALSE | | FALSE |

### 7.2 Link download

| Field | phone | tablet | tv | car | media | frame | video | audio | ebook | vr |
|-------|-------|--------|----|-----|-------|-------|-------|-------|-------|----|
| `linkAutoDownloadEnabled` | | | | FALSE | | FALSE | | | FALSE | |
| `linkDownloadMaxResolution` | 720p | 1080p | best | 480p | best | 720p | best | 480p | 480p | best |
| `linkDownloadAudioOnly` | | | | | | | | TRUE | | |
| `linkAutoDownloadOpenInPlayer` | | | TRUE | TRUE | TRUE | | TRUE | TRUE | | TRUE |

`linkDownloadMaxResolution` allowed values are exactly `480p`, `720p`, `1080p`, `best`.

### 7.3 Player interaction

| Field | phone | tablet | tv | car | media | frame | video | audio | ebook | vr |
|-------|-------|--------|----|-----|-------|-------|-------|-------|-------|----|
| `nineZoneGridEnabled` | | | FALSE | FALSE | | FALSE | | FALSE | | FALSE |
| `playerFollowSystemRotation` | TRUE | TRUE | FALSE | FALSE | FALSE | FALSE | FALSE | FALSE | TRUE | FALSE |
| `showDetailedErrors` | | | FALSE | FALSE | | FALSE | | FALSE | | |

`nineZoneGridEnabled=FALSE` falls back to the simpler 3-zone tap layout (S0620) - the right default
for a remote-driven or driving context.

### 7.4 Streams (needs applier branches first)

| Field | tv | car | media | video | audio | frame | ebook |
|-------|----|-----|-------|-------|-------|-------|-------|
| `streamsDefaultMediaFilter` | ALL | AUDIO | ALL | VIDEO | AUDIO | | |
| `streamsDefaultSort` | COUNTRY | RECENT | | | RECENT | | |
| `streamsCatalogRefreshPolicy` | ON_OPEN | MANUAL | ON_OPEN | ON_OPEN | PERIODIC_WIFI | MANUAL | MANUAL |
| `showStreamsPanelInMainWindow` | TRUE | TRUE | TRUE | TRUE | TRUE | | |

Note: `enableStreams` is already `FALSE` for `photo_frame` and `ebook_reader`, so their stream rows
should stay empty for consistency.

### 7.5 Launcher (needs rows + branches)

| Field | tv | car | frame | tablet | vr |
|-------|----|-----|-------|--------|----|
| `launcherDensityFactor` | 1.3 | 1.2 | 1.2 | 1.0 | 1.2 |
| `launcherDesktopLocked` | | TRUE | TRUE | | |
| `launcherTaskbarShowRecents` | | FALSE | FALSE | | |
| `launcherTaskbarShowTray` | | TRUE | FALSE | | |
| `launcherReplaceSystemStatusArea` | TRUE | TRUE | | | |

### 7.6 Remote storage decluttering - REJECTED by the owner (2026-07-27)

Proposal was to set `sftpEnabled` / `ftpEnabled` to `FALSE` on the non-expert profiles.

**Owner decision: do not touch remote storage at all.** All six providers stay enabled on every
profile; all six rows stay empty. Rationale: a connection method that disappears reads as a missing
feature, not as a tidied list. Section kept for the record so the option is not re-proposed.

## 8. Owner decisions (all resolved 2026-07-27)

1. `secureSensitiveScreens` (default `true`) - **presettable in both directions.** It becomes an
   ordinary matrix row and gains an applier branch; `photo_frame` and `tv_media_box` may clear it for
   the sake of their own capture features.
2. Remote-storage decluttering - **rejected**, see section 7.6. All six providers stay enabled
   everywhere.
3. Re-apply destructiveness - **warn in a confirmation step** naming how many settings will be
   overwritten. The "apply only to untouched settings" variant was rejected: it would need a
   per-field "user has edited this" flag across all 229 fields.

   **Code check (important scope reduction).** The confirmation already exists.
   `ui/profile/DeviceProfilePickerDialogFragment.onTileClicked` shows a
   `MaterialAlertDialogBuilder` with `R.string.settings_profile_warning` when
   `warnOnApply && type != currentType && type != OTHER`. `GeneralSettingsProfileHelper` passes
   `warnOnApply = true`; the Welcome flow passes `false`, so first run is already exempt. The string
   exists in EN/RU/UK and currently reads: *"Warning: Changing the profile will overwrite some of
   your settings with the default values of the new profile. Are you sure you want to proceed?"*

   Remaining delta is only the number: replace "some of your settings" with the actual count of
   non-empty overrides for the chosen profile. Needs a plural-aware resource (hand-edited - the
   string tool does not manage `plurals`) and a way for the picker to obtain the count before apply.
4. Launcher availability - resolved by research, see section 10.

## 9. Verification commands used

- `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` - reports the 40/3 drift.
- Row-vs-branch cross reference - inline PowerShell over the CSV and the applier `when` block.
- Repo-wide search for `check_device_profile_presets` - hits only the script itself.

---

## 10. Launcher availability by flavor (resolves question 8.4)

The launcher family sits behind the S0404 capability seam
`domain/launcher/LauncherModeContract`, whose KDoc states: "Flavors that ship the home surface bind
the real implementation from `src/launcherEnabled`; the rest bind a no-op from
`src/launcherDisabled`, so `src/main` never guards on `BuildConfig`."

Confirmed source sets:

- `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/di/LauncherModeModule.kt`
- `app_v2/src/launcherDisabled/java/com/sza/fastmediasorter/di/LauncherModeModule.kt`

`LauncherModeContract.isAvailableInBuild` is documented as "True when this build compiles the
launcher-mode surface (standard / noLegal)".

Consequence for the matrix: launcher rows must be applied only when the seam reports the surface as
available. In `lite` / `photos` / `legacy` a launcher preset would write settings for a surface that
does not exist in that build - harmless but misleading, and it violates the "profile only narrows
what the flavor allows" rule. The applier already has the precedent for a capability-gated cell
(`allowSeparateWindow=true_if_capable`), so launcher rows should reuse that shape rather than invent
a new mechanism.
