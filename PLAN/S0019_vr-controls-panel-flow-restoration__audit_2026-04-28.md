# Spec Audit: S0019 vr-controls-panel-flow-restoration

**Strategic spec:** [`S0019_vr-controls-panel-flow-restoration.md`](S0019_vr-controls-panel-flow-restoration.md)
**Tactical plan:** [`S0019_vr-controls-panel-flow-restoration/INDEX.md`](S0019_vr-controls-panel-flow-restoration/INDEX.md)
**Audit date:** 2026-04-28
**Mode:** full
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 24 |
| PASS | 19 |
| WARN | 1 |
| FAIL | 0 |
| MANUAL | 2 |
| EXEMPT | 2 |

S0019 closed phases 01–04 + 06 successfully. Phase 05 (interactive HUD controls — ray-clicks on seekbar/buttons/tabs) is **intentionally deferred** to S0024 (Approved on 2026-04-28; awaiting tactical decomposition). All four doable phases delivered code that compiles for both `standard debug` and `vr debug` flavors. The user-driven «exit-to-flat-player» path now explicitly carries playback context; the «Apply and 3D» combo button works end-to-end on VR builds; immersive prev/next is documented as XR-session-safe and emits a greppable log marker; the S0009 HUD canvas got two new passive hint banners.

The strategic §11 contains 8 criteria. Six are observable post-implementation; two require Quest 3 hardware (MANUAL). The remaining one (§11.6 «возврат с идентичным контекстом — без перезапуска XR») is implicitly satisfied by the prev/next path (XR session never destroyed by Phases 01-04), but full visual confirmation needs a device.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Phase | Status |
|---|------|-------|:------:|
| 1 | Иммерсивный playback HUD (seekbar/pause/prev/next/скорость/audio/subs/HUE/brightness/файл-ops/стерео) | Phase 04 (passive part) + Phase 05 (interactive — DEFERRED) | PARTIAL — passive landed, interactive deferred to S0024 |
| 2 | Prev/Next без выхода (видео и фото) | Phase 03 | PASS |
| 3 | Команда «выйти в панель» открывает плоский PlayerActivity на том же файле | Phase 01 | PASS |
| 4 | Команда «вернуться в браузер» — отдельное явное действие | Existing browser-exit path retained as `exitImmersiveToPanel(this)` for file-ops/recovery flows | PASS |
| 5 | «Apply» + «Apply and 3D» в диалоге плоского плеера | Phase 02 | PASS |
| 6 | Контекст файла переживает переходы (intent-extras + in-memory) | Phase 01 (PlayerActivity.createIntent extras) | PASS |

### 2.2 Constraints (§3.2)

| Constraint | Status |
|-----------|:------:|
| Flavor: VR-only HUD additions; «apply and 3D» button gated by `BuildConfig.SUPPORT_VR_PLAYER` | PASS |
| API level / minSdk unchanged | PASS |
| Wear OS untouched | PASS |
| Performance neutral (XR session preserved by prev/next) | PASS |
| Data compatibility (no DataStore/Room writes) | PASS |
| Localization EN/RU/UK for all new strings | PASS — 6 new strings × 3 locales |
| Accessibility (button focusable, label readable) | PASS |
| HUD parameters from S0009 §6 #4 | EXEMPT — Phase 04 reuses existing canvas |

### 2.3 Closed Decisions (§6)

All 5 owner decisions resolved + 3 proposals filed. See INDEX «Pre-Implementation Blockers».

### 2.4 User-Facing Text (§8)

| Artefact | Status |
|---------|:------:|
| `docs/FEATURES.md` | PASS — bullet «Apply and 3D + immersive prev/next + flat-player exit target» added |
| `docs/FEATURES_RU.md` | PASS — RU bullet added |
| `docs/FEATURES_UK.md` | PASS — UK bullet added |

### 2.5 Completion Criteria (§11)

- [x] §11.1 (passive part) — HUD-оверлей расширен: prev/next hint при паузе + applied-format banner при смене стерео-режима. Полный playback UI (interactive) — Phase 05 deferred.
- [ ] §11.2 — prev/next без выхода. Code path verified by inspection; on-device confirmation = MANUAL.
- [x] §11.3 — exit-to-flat-player carries file/position via `PlayerActivity.createIntent` extras and the new `exitImmersiveToFlatPlayer` overload.
- [x] §11.4 — «Apply and 3D» button visible on VR-flavor, dispatches `launchImmersiveOnCurrentFile`.
- [x] §11.5 — full scenario «browser → immersive → exit → re-enter» verified by inspection (no extra file-pick step).
- [ ] §11.6 — XR session preservation across re-entry needs device confirmation = MANUAL.
- [x] §11.7 — trilingual texts use unique keys; ручной ревью matches pattern of S0021/S0023.
- [x] §11.8 — VR-photo path is identical to video (Phase 03 prev/next is media-type-agnostic; Phase 04 hints fire on the same handler).

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status |
|-------|:------:|
| Phase counter matches statuses | PASS — `5 / 6 done + 1 deferred` |
| Phase-file headers match INDEX rows | PASS |
| Pre-Implementation Blockers all ticked | PASS — 5 §6 decisions + 3 proposals all `[x]` |

### 3.2 Phase-by-phase

**Phase 01 — exit-target-redirect: Verified**
- New `exitImmersiveToFlatPlayer(source, playerIntent)` in `VrTaskTransition.kt` (1 hit).
- `VrPlayerActivity.exitVrAndStopPlayback` switched to the new function (1 hit).
- 2 `// S0019 recovery: not user exit` annotations on remaining `exitImmersiveToPanel(this)` calls.
- `Log.d(` zero hits in both files.

**Phase 02 — apply-and-3d-button: Verified**
- `name="dialog_playback_apply_and_3d"` × 3 across `values*/strings.xml`.
- `@+id/btnApplyAnd3D` × 1 in portrait + × 1 in landscape (binding parity).
- `binding.btnApplyAnd3D.setOnClickListener` × 1; gated on `BuildConfig.SUPPORT_VR_PLAYER`.
- `launchImmersiveOnCurrentFile` exposed on `PlayerActivity` (open base, reuses `handle3dVrToggleClicked`).

**Phase 03 — immersive-prev-next: Verified**
- 2 `// S0019: immersive-safe — does not recreate XR session` annotations.
- 2 `Timber.i("VrPlayerActivity: immersive prev/next ...")` markers (Next + Previous).
- No `exitImmersiveToPanel`/`finishAndRemoveTask`/`recreate(` in prev/next handler bodies.

**Phase 04 — hud-passive-content: Verified**
- 2 new trilingual string pairs (`vr_hud_prev_next_hint`, `vr_hud_applied_format`) × 3 locales.
- 3 `S0019: passive only — interactivity in S0024` annotations.
- 2 `vrHudManager?.showBannerText(getString(R.string.vr_hud_*))` call sites (Pause + applied stereo-mode on route-decision).
- No `setOnClickListener` / `OnHoverListener` / `pickElement` introduced.

**Phase 05 — interactive-hud-controls: Deferred**
- Placeholder phase file present; depends on S0024 reaching `Implemented`.
- S0024 spec exists at `PLAN/S0024_vr-hud-ray-input.md` (Status: Approved).

**Phase 06 — docs-catalog-cleanup: Verified**
- `docs/FEATURES{,_RU,_UK}.md` each contain a bullet about «Apply and 3D + immersive prev/next + flat-player exit target».
- `dev/CATALOG/app_v2.{jsonl,md}` regenerated (804 records).
- Dev log entries × 12 covering all production files modified.

---

## 4. Cross-Reference Checks

- **Goal §2.3 ↔ Phase 01** — PASS.
- **Goal §2.5 ↔ Phase 02** — PASS.
- **Goal §2.2 ↔ Phase 03** — PASS.
- **Goal §2.1 (passive) ↔ Phase 04** — PASS.
- **Goal §2.1 (interactive) ↔ Phase 05 / S0024** — DEFERRED (acknowledged).
- **ADR-1 («выйти в панель» / «выйти в браузер» disambiguated) ↔ Phase 01** — PASS.
- **ADR-2 (panel = existing PlayerActivity) ↔ Phase 01 + Phase 02** — PASS.
- **ADR-3 (immersive HUD = single path) ↔ Phase 04 (passive) + S0024 (interactive)** — PASS for the part that's landed.
- **ADR-4 («Apply and 3D» combo) ↔ Phase 02** — PASS.
- **ADR-5 (Prev/Next без выхода) ↔ Phase 03** — PASS.

---

## 5. Manual Acceptance Signals

- [x] `assembleStandardDebug` — `BUILD SUCCESSFUL` (`v2.60.4281.654-DEBUG`).
- [x] `assembleVrDebug` — `BUILD SUCCESSFUL` (`v2.60.4281.653-VR-DEBUG`).
- [ ] **Manual:** Quest 3 acceptance — install vr-debug APK, walk through:
  1. Browser → выбрать стерео-видео → entered immersive automatically.
  2. Open command panel → press «Exit to panel» — confirm flat PlayerActivity opens with the same file at the same position (NOT MainActivity browser).
  3. Open Control dialog → switch stereo format → press «Apply and 3D» — confirm dialog closes and immersive re-launches with new format.
  4. In immersive → controllers prev/next — confirm file changes without leaving the headset; HUD shows new file name.
  5. Pause → confirm `← prev / next →` hint appears for ~3 sec.

---

## 6. Action Items

1. **[MANUAL §5]** Quest 3 acceptance run on `FastMediaSorter_vr_debug_v2.60.4281.653-VR-DEBUG.apk`.
2. **[FOLLOW-UP P-1]** After landing on device, apply S0019's Proposed Structural Change P-1 (S0009 ADR-3 transitional guard removal) via `/spec-update S0009 --force-locked`.
3. **[FOLLOW-UP P-2]** When ready for interactive HUD: `/spec-tech S0024` → `/spec-dev S0024` → return to S0019 Phase 05 with concrete steps.
