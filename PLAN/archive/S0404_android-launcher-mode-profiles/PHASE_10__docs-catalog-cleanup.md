# Phase 10 - Docs, Catalog, Cleanup

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done 2026-07-18 (mechanical closure; epic parked at BlockNeedUserTest for the on-device pass)
**Depends on:** Phases 01-08 (Phase 09 descoped to S1102 on 2026-07-18 - no longer a prerequisite)
**Blocks:** - (final phase)
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Close the epic mechanically: capability record, catalog hygiene with flavor hints, release-manifest proof, debug probes + status transition to on-device verification.

---

## Prerequisites

- [ ] Phases 01-08 are ✅ Done. (Phase 09 descoped to S1102 on 2026-07-18 - not a prerequisite.)

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `scripts/all_features/add.ps1` only) | +1 record |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via scripts only) | - |
| probe `Timber.d("S0404: ...")` lines in `.kt` entry points | New (temporary) | ~6 lines |

---

## Steps

### Step 10.1 - ALL_FEATURES capability record

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` (never hand-edit): area "Settings & Navigation" or a more fitting existing area (check existing `area` values first), name "Launcher mode (device home screen)", description covering: opt-in home-screen role, Windows-style desktop of shortcut cells (folder-in-a-view-mode, playlist, stream channel, any app) and live gadgets (clock, playlist, streams, folder preview), always-visible taskbar with Start menu / recents / pinned / tray, per-profile starter desktop, independent portrait/landscape layouts, guaranteed exit. `spec: "S0404"`. **Flavors: exactly `["standard","noLegal"]` - read from the Phase 01 gradle mounts, never copied from a sibling record; read the record back after writing and verify the flavor list.** Run `scripts/all_features/validate.ps1` → exit 0.

**Verification:**

- `Grep` - `"spec":"S0404"` present in `docs/ALL_FEATURES.jsonl`; flavors field equals `["standard","noLegal"]`.
- `validate.ps1` → exit 0.

**Result 2026-07-18:** record `settings-navigation.launcher-mode` added; readback flavors=`standard,noLegal`, spec=`S0404`, status=`active`. `validate.ps1` PASS (547 records, exit 0).

**Status:** `[x]` done

---

### Step 10.2 - Catalog sync + roles + flavor hints

**Files:** `dev/CATALOG/app_v2.jsonl` (generated)
**Depends on:** Step 10.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Fill `role` + `status` via `dev/CATALOG/scripts/set.ps1` for every new class of this epic (repositories, use cases, activity, managers, gadgets, pickers). For every class under `src/launcherEnabled/java` declare `-NoFlavors "lite,photos,legacy,vr"`; for `src/launcherDisabled` classes declare `-NoFlavors "standard,noLegal"`. `set.ps1` aborts on first error - fix and re-run.

**Verification:**

- Catalog query for `LauncherHomeActivity` returns a row with filled role and the NoFlavors hint.

**Result 2026-07-18:** `catalog_sync -Module app_v2` (2245 records). role+status=`new` set on 43 S0404 classes; `src/launcherEnabled` classes carry `-NoFlavors lite,photos,legacy,vr`. `LauncherHomeActivity` query row: status=`new`, role=`Launcher UI: HOME activity ..`, noFlavors=`[lite,photos,legacy,vr]`. (Unrelated `*Launcher*` helpers - MenuScreenshot/SettingsIntent/GoogleDomainBrowser/BrowseLauncherManager/ScreenCapture - left untouched.)

**Status:** `[x]` done

---

### Step 10.3 - Release-manifest proof (strategic §11.13)

**Files:** - (validation only)
**Depends on:** - parallel

**Prompt for developer:**

> Under the build lock, run the release manifest merge for standard (e.g. `.\gradlew.bat :app_v2:processStandardReleaseManifest` via a wrapper that honours `temp/BUILD.LOCK`; if the exact task name differs, list tasks and pick the standardRelease manifest-processing task). Then grep the merged output under `app_v2/build/intermediates/merged_manifests/standardRelease*/**/AndroidManifest.xml`: `QUERY_ALL_PACKAGES` → zero hits; `android.intent.category.HOME` → present once (the launcherEnabled overlay landed). Record `expected: 0|1 | actual: ...`.

**Verification:**

- Both grep predicates recorded with PASS.

**Result 2026-07-18:** `:app_v2:processStandardReleaseManifest` BUILD SUCCESSFUL under BUILD.LOCK. Merged `standardRelease/.../AndroidManifest.xml`: `QUERY_ALL_PACKAGES` expected 0 | actual 0; `android.intent.category.HOME` expected 1 | actual 1. Both PASS.

**Status:** `[x]` done

---

### Step 10.4 - Debug probes + status transition

**Files:** probe lines in `.kt`; spec catalog
**Depends on:** Steps 10.1-10.3

**Prompt for developer:**

> Order matters: the ticket-log gate treats `S0404:` probes as legal ONLY while the spec is in `BlockNeedUserTest`, so flip the status BEFORE any gated closure runs.
> 1. `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0404 -Status BlockNeedUserTest -StatusNote 'On-device: enable launcher mode (Settings and Welcome paths), verify seeded desktop per profile, assemble/edit cells incl. every shortcut kind and all four gadgets, taskbar+Start menu, rotation independence, exit mode, reboot-into-desktop on the live target device (vendor gate, strategic §6.14)'`.
> 2. Insert `Timber.d("S0404: <flow>")` probes at the changed-flow entry points - launcher home opened (`LauncherHomeActivity` start path), cell command executed (`ExecuteLauncherCommandUseCase.launch`), mode enabled / disabled (`LauncherRoleManager`), desktop seeded (`SeedLauncherDesktopUseCase`), welcome toggle applied. One probe per flow, lines ≤120 chars.
> 3. Final `.\a.ps1 dav` build + install on the test device.
> 4. Batch dev-logs via `scripts/spec_catalog/close-and-log.ps1 -DevLogs '[...]'`.
> Probes stay until the ticket leaves `BlockNeedUserTest` (removed by `/spec-check` on Verified).

**Verification:**

- `Grep` - `Timber.d("S0404:` count ≥ 5 across `.kt` (probes in, wrapped-line variant `"S0404:` checked too).
- `select.ps1 -Id S0404` shows `BlockNeedUserTest` with the status note.

**Result 2026-07-18:** S0404 → `BlockNeedUserTest` (status note set, folds tactical 07.6/08.7 + vendor gate 6.14). 6 `Timber.d("S0404: ..")` probes inserted at: LauncherHomeActivity.setupViews (home opened), ExecuteLauncherCommandUseCase.launch (cell command), LauncherRoleManager.enableMode + disableMode (mode on/off), SeedLauncherDesktopUseCase.invoke (seed), WelcomeActivity (Welcome toggle). Grep `Timber.d("S0404:` = 6 (≥5). `.\a.ps1 dav` BUILD SUCCESSFUL (v2.60.7180.315-DEBUG); installed on emulator-5554; app launches to MainActivity with no FATAL in logcat.

**Status:** `[x]` done (mechanical closure; on-device launcher walkthrough is the BlockNeedUserTest owner task)

---

## Phase Done Criteria

- [x] Every `Step 10.*` above is `[x] done`.
- [x] INDEX Completion Gate items checked except `/spec-check` (runs after user test).
- [x] All CODE.LOCK / BUILD.LOCK released.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After owner's device pass (incl. the §6.14 vendor gate on the live target device) run `/spec-check S0404`; probe removal happens on leaving BlockNeedUserTest, never before.

---

## Rollback Plan

Not applicable - bookkeeping phase; code rollback is per-phase.
