# Tactical Plan: S0766 - camera-photo-gps-geotag

**Strategic spec:** [`../S0766_camera-photo-gps-geotag.md`](../S0766_camera-photo-gps-geotag.md)
**Research inputs:** [`research/01__camera-geotag-integration.md`](research/01__camera-geotag-integration.md)
**Feature:** Optional GPS geotagging of in-app camera photos (opt-in, default off)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (code complete; device test gates Verified)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-permissions | - | ✅ Done | 1/1 | [PHASE_01__manifest-permissions.md](PHASE_01__manifest-permissions.md) |
| 02 | settings-persistence | - | ✅ Done | 3/3 | [PHASE_02__settings-persistence.md](PHASE_02__settings-persistence.md) |
| 03 | settings-ui-strings | 02 | ✅ Done | 4/4 | [PHASE_03__settings-ui-strings.md](PHASE_03__settings-ui-strings.md) |
| 04 | location-source | 01 | ✅ Done | 1/1 | [PHASE_04__location-source.md](PHASE_04__location-source.md) |
| 05 | capture-integration | 01,02,03,04 | ✅ Done | 4/4 | [PHASE_05__capture-integration.md](PHASE_05__capture-integration.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No unresolved research items. All strategic §6 open questions are Resolved:

- Permission strategy -> opt-in setting + request at toggle-enable in settings (mic precedent); camera never re-prompts.
- Location source -> platform `LocationManager` warmed while camera open; stamp last fix at shutter; no synchronous fresh-fix.
- No-permission/no-fix -> capture without GPS, shutter never blocked.
- Widget paths -> geotag runs inside `CameraCaptureActivity`, widgets inherit it.
- `uses-feature` GPS -> `required="false"` (no device-reach regression).

Residual external item (non-blocking for dev): Play Data Safety form update for precise location - owner/release.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited here; strategic §8 capability is recorded in `docs/ALL_FEATURES.jsonl` and surfaced by `/skill-release`.
- [ ] Settings docs regenerated (`docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`) - Rule 22 gate.
- [ ] `dev/CHANGELOG.md` has an entry for the ticket (batched).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0766` returns `Verified` (after device test).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: device test (real GPS + camera) -> `/spec-check S0766`.

---

## Blockers Log

- 2026-06-28 - Code complete, `assembleStandardDebug` PASS. Emulator smoke test: APK installs + app launches without crash (validates the new `SettingsRepository` injection + provider lifecycle). Full geotag verification (toggle -> permission -> capture -> GPS in EXIF, digital-zoom, widget path) deferred to a real GPS + camera device per the device-test handoff - emulator synthetic camera + mocked GPS cannot prove the real-world scenario. Status `BlockNeedUserTest`.

---

## Change Log

- 2026-06-28 - Initial tactical plan authored by `/spec-all` (F2).
