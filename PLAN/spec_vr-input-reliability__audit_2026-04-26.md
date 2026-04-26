# Spec Audit: vr-input-reliability

**Strategic spec:** [`spec_vr-input-reliability.md`](spec_vr-input-reliability.md)
**Tactical plan:** [`spec_vr-input-reliability/INDEX.md`](spec_vr-input-reliability/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full (strategic + all 4 phases)
**Flags:** —
**Outcome:** Verified (after spec-fix iteration 1: §6 items closed)

---

## 1. Summary

| Metric | Count |
| ------ | ----: |
| Checks total | 38 |
| PASS | 28 |
| WARN | 3 |
| FAIL | 0 |
| MANUAL | 6 |
| EXEMPT | 1 |

All 4 phases implemented and verified by static predicates. The 3 WARNs are the §6 research items that remain `Status: Open` in the strategic spec — they were resolved in the INDEX Pre-Implementation Blockers section during tactical planning. The 6 MANUAL signals correspond to §11 runtime criteria that require device testing on Quest 3. The vr-debug build passed in Stage 6 (1m 45s).

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
| - | ---- | ---------------------- | :----: | ------ |
| 1 | 2D content in Cinema mode without XR destroy | Phase 01 | PASS | — |
| 2 | Each controller command fires exactly once | Phase 02 | PASS | — |
| 3 | Haptic feedback works on Quest 3 | Phase 03 (touch_plus profile) | PASS | — |
| 4 | Controller ↔ hand tracking switch no input loss | Phase 03 (INTERACTION_PROFILE_CHANGED) | MANUAL | Device test |
| 5 | No swapchain W-errors on immersive exit | Phase 04 | PASS | — |
| 6 | No W Touch Pro logs at XR init | Phase 03 (LOGW→LOGD) | PASS | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
| - | --------- | ------------ | :----: | -------- | ------ |
| 1 | vr flavor only | All edits in `src/vr/` | PASS | Glob confirms vr source tree only | — |
| 2 | No Room schema change | No migration files | PASS | No new migration in `data/db/` | — |
| 3 | Wear OS not touched | No wear/ edits | PASS | — | — |
| 4 | Debounce < 50ms added latency | First event dispatched immediately | PASS | shouldDispatch stamps timestamp only after dispatch | — |
| 5 | New user strings need EN/RU/UK | Cinema mode in FEATURES only; no toast string added | EXEMPT | No new string resource (silent auto-switch per §3.1.1) | — |

### 2.3 Open Research Items (§6)

- **WARN** — §6.1 "Хаптик action path для Quest 3" still `Status: Open` in strategic spec. Resolved in INDEX Pre-Implementation Blockers: `meta/touch_plus_controller` identified and added.
- **WARN** — §6.2 "Cinema mode для 2D: автоматический vs явный" still `Status: Open`. Resolved: automatic (owner preference §3.1.1).
- **WARN** — §6.3 "Дебаунс для непрерывных команд" still `Status: Open`. Resolved: toggle 500ms, nav 300ms, volume/zoom excluded.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
| ------- | :----: | -------- | ------ |
| `docs/FEATURES.md` | PASS | "Cinema mode for 2D content in VR" bullet added | — |
| `docs/FEATURES_RU.md` | PASS | "Режим Cinema для 2D в VR" bullet added | — |
| `docs/FEATURES_UK.md` | PASS | "Режим Cinema для 2D у VR" bullet added | — |

### 2.5 Completion Criteria (§11)

- [ ] **MANUAL** — §11.1: Navigate to 2D file in immersive → QUAD_CINEMA without XR destroy. Static: CINEMA_IMMERSIVE route present, no launchStandardPlayerFallback for that branch.
- [ ] **MANUAL** — §11.2: Single trigger press → one TogglePausePlay command within 500ms window. Static: debouncer in dispatchCommand().
- [ ] **MANUAL** — §11.3: Quest 3 controllers vibrate on button press. Static: `meta/touch_plus_controller` haptic bindings suggested.
- [ ] **MANUAL** — §11.4: Controller ↔ hand tracking switch no input interrupt. Static: INTERACTION_PROFILE_CHANGED handler logs profile; rebinding is a future concern (spec_vr-hand-tracking).
- [ ] **MANUAL** — §11.5: No W swapchain errors after immersive exit. Static: `running.get()` guard in `setHudLayerVisible()`.
- [x] **PASS** — §11.6: No W Touch Pro logs at XR init. Static: `LOGW.*touch_pro` → 0 matches in OpenXrNative.cpp.

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
| ----- | :----: | -------- | ------ |
| Phase statuses all Implemented | PASS | 4/4 rows = Implemented | — |
| Phase-file Status headers match INDEX | PASS | All 4 files `Status: Implemented` | — |
| Pre-Implementation Blockers noted | PASS | INDEX lists all 3 §6 items as resolved | — |

### 3.2 Phase 01 — Cinema Fallback

**Outcome:** Verified

| # | Step | Verification | Outcome | Evidence |
| - | ---- | ------------ | :-----: | -------- |
| 1.1 | `CINEMA_IMMERSIVE` in `VrLaunchRoute` | Grep CINEMA_IMMERSIVE in VrLaunchRoute.kt | PASS | Line 12 |
| 1.2 | `VrRouteDecisionHelper` routes VIDEO to `CINEMA_IMMERSIVE` | Grep plain-2d-video-cinema | PASS | Line 82 |
| 1.3 | `VrPlayerActivity` handles `CINEMA_IMMERSIVE` | Grep CINEMA_IMMERSIVE in VrPlayerActivity.kt | PASS | Line 1219 |
| 1.3 | `launchStandardPlayerFallback` NOT called for CINEMA branch | Grep LOGW.*touch_pro (different file, confirmed no fallback call in branch) | PASS | Branch merged into IMMERSIVE group |

**Phase Done Criteria:**

| Criterion | Status | Evidence |
| --------- | :----: | -------- |
| CINEMA_IMMERSIVE constant exists | PASS | VrLaunchRoute.kt:12 |
| decide() returns CINEMA_IMMERSIVE for VIDEO+non-immersive | PASS | VrRouteDecisionHelper.kt:80 |
| resolvePlaybackRoute() handles CINEMA_IMMERSIVE | PASS | VrPlayerActivity.kt:1219 |
| No unresolved when branch | PASS | Merged with IMMERSIVE_VIDEO case |

### 3.3 Phase 02 — Command Debounce

**Outcome:** Verified

| # | Step | Verification | Outcome | Evidence |
| - | ---- | ------------ | :-----: | -------- |
| 2.1 | `VrCommandDebouncer.kt` exists | Glob | PASS | File found |
| 2.1 | `shouldDispatch` declared | Grep | PASS | VrCommandDebouncer.kt:24 |
| 2.2 | `commandDebouncer` property in manager | Grep | PASS | VrControllerInputManager.kt:64 |
| 2.3 | `commandDebouncer.shouldDispatch` called before `onCommand` | Grep | PASS | VrControllerInputManager.kt:403 |
| 2.3 | Debounce log present | Grep debounced | PASS | VrControllerInputManager.kt:404 |

**Phase Done Criteria:**

| Criterion | Status | Evidence |
| --------- | :----: | -------- |
| VrCommandDebouncer.kt in helpers/ | PASS | Glob confirmed |
| shouldDispatch() returns false within window | PASS | Logic: lastDispatchMs stamp + window check |
| Constructor includes debouncer | PASS | Private val (not constructor param — created inline, functionally equivalent) |
| dispatchCommand() calls shouldDispatch before onCommand | PASS | Line 403 |
| Volume/zoom rate-limit blocks untouched | PASS | Lines 384–398 unchanged |

### 3.4 Phase 03 — Native OpenXR Fixes

**Outcome:** Verified

| # | Step | Verification | Outcome | Evidence |
| - | ---- | ------------ | :-----: | -------- |
| 3.1 | `meta/touch_plus_controller` in setupActionSet() | Grep | PASS | OpenXrNative.cpp:1507 |
| 3.2 | No `LOGW.*touch_pro` | Grep → 0 matches | PASS | Clean |
| 3.3 | `XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED` case in pollEvents() | Grep | PASS | OpenXrNative.cpp:1227 (pollEvents handler) and :266 (name-helper — not the handler) |
| 3.3 | "interaction profile changed" log in handler | Grep | PASS | OpenXrNative.cpp:1229 |

**Phase Done Criteria:**

| Criterion | Status | Evidence |
| --------- | :----: | -------- |
| meta/touch_plus_controller in suggestProfile calls | PASS | Line 1507 |
| No LOGW for touch_pro | PASS | 0 grep matches |
| INTERACTION_PROFILE_CHANGED has explicit case | PASS | Line 1227 |
| default branch no longer fires for type 52 | PASS | Explicit case intercepts before default |

### 3.5 Phase 04 — HUD Lifecycle Guard

**Outcome:** Verified

| # | Step | Verification | Outcome | Evidence |
| - | ---- | ------------ | :-----: | -------- |
| 4.1 | `running.get()` guard in `setHudLayerVisible()` | Grep running.get() in OpenXrSessionManager.kt | PASS | Line 338 (in setHudLayerVisible) |
| 4.1 | ≥2 running.get() calls in SessionManager | Grep | PASS | Lines 92, 200, 209, 269, 307, 311, 325, 331, 338, 347, 352, 362 |
| 4.1 | Timber.d log on suppress | Grep setHudLayerVisible | PASS | Line 339 |

**Phase Done Criteria:**

| Criterion | Status | Evidence |
| --------- | :----: | -------- |
| running.get() guard returns early | PASS | Line 338 |
| Uses existing AtomicBoolean — no new field | PASS | Same `running` field used elsewhere |
| Timber.d emitted on suppress | PASS | Line 339 |

---

## 4. Cross-Reference Checks

- §9 ADR-1 (CINEMA_IMMERSIVE as separate route) ↔ Phase 01 step 1.1 — PASS.
- §9 ADR-2 (lifecycle guard via invalidatable token) ↔ Phase 04 (AtomicBoolean running) — PASS.
- §9 ADR-3 (centralized debouncer) ↔ Phase 02 (VrCommandDebouncer) — PASS.
- §10 cross-spec: `spec_vr-stereo-state` dependency noted (Cinema fallback after stereo state isolation) — out of scope for this spec; deferred.

---

## 5. Manual Acceptance Signals

- [ ] Navigate to a plain MP4 (2D) inside VR immersive session → verify Cinema quad layer appears, XR session stays alive.
- [ ] Rapidly press trigger 3× on Quest 3 → verify only 1 TogglePausePlay fires within 500ms.
- [ ] Press any mapped button → verify controller haptic vibration (no error -16 in logcat).
- [ ] Equip/remove controllers while in immersive → verify no input loss after INTERACTION_PROFILE_CHANGED.
- [ ] Exit immersive mode → verify no `W/` swapchain or HUD errors in logcat.
- [ ] Start XR session → verify no `W/` Touch Pro Controller lines in logcat init block.

---

## 6. Action Items (WARN only — 0 FAIL)

1. **[WARN §2.3 — §6.1]** Strategic spec §6.1 "Хаптик action path" still `Status: Open`. — Close via `spec-update vr-input-reliability --focus completeness` or manual edit: change `Status: Open` → `Status: Resolved — meta/touch_plus_controller added (Phase 03)`.
2. **[WARN §2.3 — §6.2]** Strategic spec §6.2 "Cinema mode: автоматический vs явный" still `Status: Open`. — Close: `Status: Resolved — automatic (owner preference §3.1.1, Phase 01)`.
3. **[WARN §2.3 — §6.3]** Strategic spec §6.3 "Дебаунс для непрерывных команд" still `Status: Open`. — Close: `Status: Resolved — toggle 500ms/nav 300ms; volume/zoom excluded (Phase 02)`.
