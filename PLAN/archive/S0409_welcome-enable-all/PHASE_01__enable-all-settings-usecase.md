# Phase 01 - Enable-all settings use case

**Strategic spec:** [`../S0409_welcome-enable-all.md`](../S0409_welcome-enable-all.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-12
**Completed:** 2026-06-12

---

## Objective

Introduce a domain use case that force-enables every immediately-toggleable function setting plus the
adjacent opt-ins, gated by injected capability flags; no UI, orchestration, or deliverable handling yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCase.kt` | New | ≤ 110 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCaseTest.kt` | New | ≤ 220 |

---

## Steps

### Step 01.1 - Create `ApplyEnableAllSettingsUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ApplyEnableAllSettingsUseCase` with `@Inject constructor` taking `SettingsRepository`,
> `MediaCapabilities`, and `@ApplicationContext Context`. Expose `suspend operator fun invoke()` that
> read-modify-writes the latest `AppSettings` snapshot (one `getSettings().first()` then
> `updateSettings(transform)`, mirroring `WelcomeFunctionalityController.persist`). The transform forces
> on every whitelisted flag, gated by capability so an unavailable feature is silently left untouched
> (strategic §6.4): `allFiles = true`; `supportAudio = true` only when `mediaCapabilities.supportsAudio`;
> `supportVideos = true` only when `supportsVideo`; `supportText`/`supportPdf`/`supportOfficeDocuments`
> `= true` and `supportEpub = supportsEpub` only when `supportsDocuments`; `enablePersistentAudioPlayback
> = true` only when `supportsAudio`; `acceptSharedFiles = true`; `isPrimaryMediaPlayer = true`. Do NOT
> touch `enableOcr`/`enableTranslation` here - deliverable-gated features are handled by the
> orchestrator (Phase 03) under the enable-only-after-install rule. Leave a one-line WHY comment that the
> capability gate implements the "silently skip unavailable" decision. Log one `Timber.i` summary line
> (no `Sxxxx` in it).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCase.kt` exists.
- `Grep` - `class ApplyEnableAllSettingsUseCase` matches once.
- `Grep` - `suspend operator fun invoke` present.
- `Grep` - `isPrimaryMediaPlayer = true` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification 5/5 PASS. Files: domain/usecase/ApplyEnableAllSettingsUseCase.kt (New, 49 LOC). Implemented with SettingsRepository + MediaCapabilities only (unused Context dropped as dead-weight). Dev log recorded.

---

### Step 01.2 - Unit-test the capability gating

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyEnableAllSettingsUseCaseTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a JUnit test with a fake/mock `SettingsRepository` (in-memory snapshot) and `MediaCapabilities`.
> Cover: (1) all-capabilities build forces every whitelisted flag on; (2) an audio-less capability set
> (`supportsAudio = false`) leaves `supportAudio` and `enablePersistentAudioPlayback` untouched while
> still enabling the available flags; (3) a documents-less set leaves the four document flags untouched.
> Assert against the written snapshot. Follow the style of `ApplyProfilePresetUseCaseTest`.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `class ApplyEnableAllSettingsUseCaseTest` matches once.
- Run `.\gradlew.bat testStandardDebugUnitTest --tests "*ApplyEnableAllSettingsUseCaseTest"` - the per-class XML report under `app_v2/build/test-results/` shows 0 failures.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification PASS: per-class XML tests="3" failures="0" errors="0". Files: domain/usecase/ApplyEnableAllSettingsUseCaseTest.kt (New, 99 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new public class).

---

## Handoff Notes to Next Phase

`ApplyEnableAllSettingsUseCase` is the single writer of the enable-all settings whitelist. The
orchestrator (Phase 03) calls it after the OTHER profile save and owns OCR/translation deliverable
handling separately.

---

## Rollback Plan

Revert phase commit(s) - new files only, no data migration or user-facing surface changed.
