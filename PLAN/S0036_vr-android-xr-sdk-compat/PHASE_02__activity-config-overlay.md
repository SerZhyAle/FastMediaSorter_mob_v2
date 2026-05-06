# Phase 02 — Activity configChanges Overlay

**Strategic spec:** [`../S0036_vr-android-xr-sdk-compat.md`](../S0036_vr-android-xr-sdk-compat.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Override the `android:configChanges` attribute of `SettingsActivity`, `WelcomeActivity`, and `MainActivity` for the `vr` flavor only, using `tools:replace` in `app_v2/src/vr/AndroidManifest.xml`. The expanded set lets Android XR Shell resize the virtual window without forcing activity recreation, eliminating the multi-`onCreate` cycle and black-tab artefact in Settings. No source-set Kotlin code is touched.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `xmlns:tools` namespace present on root `<manifest>` of `app_v2/src/vr/AndroidManifest.xml` (Phase 01 step 01.1).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/AndroidManifest.xml` | Modified | ≤ 200 |

> Combined Phase 01 + Phase 02 keeps the file ≤ 200 lines (currently 127). No backup required.

---

## Steps

### Step 02.1 — Overlay configChanges on `SettingsActivity`

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Inside the existing `<application>` block of `app_v2/src/vr/AndroidManifest.xml`, add an overlay `<activity>` declaration for `com.sza.fastmediasorter.ui.settings.SettingsActivity`. Use `tools:replace="android:configChanges"` so AGP overrides the value from `app_v2/src/main/AndroidManifest.xml`. The overlay must declare the full XR-friendly `configChanges` set and mark the activity as resizeable:
>
> ```xml
> <!--
>   Android XR Shell resizes the virtual window across screenSize / smallestScreenSize /
>   screenLayout / density / uiMode dimensions; without overriding the narrow main-manifest
>   configChanges set, every resize triggers activity recreation, leaving Settings as a
>   blank panel. This overlay is gated to the vr flavor — main manifest stays untouched
>   for standard / lite / photos / legacy.
> -->
> <activity
>     android:name="com.sza.fastmediasorter.ui.settings.SettingsActivity"
>     android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|navigation|uiMode|fontScale"
>     android:resizeableActivity="true"
>     tools:replace="android:configChanges,android:resizeableActivity" />
> ```
>
> Do **not** add any `<intent-filter>` or `<meta-data>` to this overlay — those remain in the main manifest and merge automatically.

**Verification:**

- `Grep` — `com.sza.fastmediasorter.ui.settings.SettingsActivity` matches exactly once in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — within 10 lines of that match, all of these tokens appear: `smallestScreenSize`, `screenLayout`, `density`, `uiMode`, `fontScale`.
- `Grep` — `tools:replace="android:configChanges,android:resizeableActivity"` present at least once in the same file.
- `Grep` — `android:resizeableActivity="true"` present at least once in the same file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: app_v2/src/vr/AndroidManifest.xml (+12 LOC). Dev log recorded.

---

### Step 02.2 — Overlay configChanges on `WelcomeActivity`

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a second overlay `<activity>` block for `com.sza.fastmediasorter.ui.welcome.WelcomeActivity` with the same configChanges set and `tools:replace` directive as Step 02.1. Place immediately after the SettingsActivity overlay. No comment needed — it's covered by the comment from Step 02.1.

**Verification:**

- `Grep` — `com.sza.fastmediasorter.ui.welcome.WelcomeActivity` matches exactly once in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — count of `tools:replace="android:configChanges,android:resizeableActivity"` in the file is exactly 2 (after this step).

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: app_v2/src/vr/AndroidManifest.xml (+5 LOC). Dev log recorded.

---

### Step 02.3 — Extend `configChanges` on existing `MainActivity` overlay

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> The `vr` manifest already declares an overlay for `com.sza.fastmediasorter.ui.main.MainActivity` (it carries `<layout>` and `com.oculus.intent.category.2D`). The main manifest's `configChanges` for MainActivity is `orientation|screenSize|screenLayout|keyboardHidden`, which is **wider** than Settings/Welcome but still missing `smallestScreenSize|density|navigation|uiMode|fontScale`.
>
> Add `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|navigation|uiMode|fontScale"`, `android:resizeableActivity="true"`, and `tools:replace="android:configChanges,android:resizeableActivity"` to the existing `<activity android:name="com.sza.fastmediasorter.ui.main.MainActivity">` element in `app_v2/src/vr/AndroidManifest.xml`. Do not touch the existing `<layout>` or `<intent-filter>` children — keep them untouched.

**Verification:**

- `Grep` — `com.sza.fastmediasorter.ui.main.MainActivity` still matches exactly once in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — count of `tools:replace="android:configChanges,android:resizeableActivity"` in the file is exactly 3.
- `Grep` — `<layout` element still present in `app_v2/src/vr/AndroidManifest.xml` (not removed).
- `Grep` — `com.oculus.intent.category.2D` still present in `app_v2/src/vr/AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: app_v2/src/vr/AndroidManifest.xml (+3 LOC). Dev log recorded.

---

### Step 02.4 — Verify merged manifest output

**Files:** none modified — verification step only
**Depends on:** Step 02.3

**Prompt for developer:**

> Run a `vrDebug` build and inspect the merged manifest at `app_v2/build/intermediates/merged_manifest/vrDebug/AndroidManifest.xml`. Confirm that the merger applied the overlays. If the merger logs any `tools:replace` conflicts, resolve them by reading the linked manifest-merger report in `app_v2/build/outputs/logs/manifest-merger-vrDebug-report.txt`.

**Verification:**

- `Glob` — `app_v2/build/intermediates/merged_manifest/vrDebug/AndroidManifest.xml` exists after the build.
- `Grep` — in the merged manifest, `com.sza.fastmediasorter.ui.settings.SettingsActivity` line contains all of: `smallestScreenSize`, `density`, `uiMode`.
- `Grep` — in the merged manifest, `com.sza.fastmediasorter.ui.welcome.WelcomeActivity` line contains `smallestScreenSize` and `density`.
- `Grep` — in the merged manifest, `com.sza.fastmediasorter.ui.main.MainActivity` line contains `smallestScreenSize` and `uiMode`.
- `Grep` — `app_v2/build/outputs/logs/manifest-merger-vrDebug-report.txt` contains zero lines starting with `ERROR`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 — Verification 5/5 PASS. vrDebug BUILD SUCCESSFUL, merged manifest confirmed, zero ERRORs in merger report. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles for the `vr` flavor — run `/build`.
- [ ] Project compiles for the `vrUnlicensed` flavor.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `app_v2/src/vr/AndroidManifest.xml` via `pwsh -File scripts/add_to_dev_log.ps1` (one entry covers both Phase 01 + 02 if they ship together).
- [ ] Manual smoke test on Android XR SDK emulator (when available): launch app → open Settings → switch tabs (General / Media / Playback / Operations) → resize XR window → confirm `Settings.onCreate` fires only once via logcat.
- [ ] Manual smoke test on Meta Quest 3: launch app → open Settings → switch tabs → resize panel → confirm no regression.

---

## Handoff Notes to Next Phase

After Phase 02 the `vr` manifest is feature-complete for the Android XR SDK fix. Phase 03 records the diagnostic decisions (rendernode artefact, EmbeddingMixedHandler residual noise, Q3 won't-fix) in dev/CHANGELOG and runs the standard cleanup sweep.

---

## Rollback Plan

Revert the Phase 02 commit on `app_v2/src/vr/AndroidManifest.xml`. The three overlay activities disappear and AGP merges the original main-manifest declarations. No data migration, no user-visible surface change.
