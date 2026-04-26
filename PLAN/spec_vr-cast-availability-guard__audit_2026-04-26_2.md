# Spec Audit: vr-cast-availability-guard

**Strategic spec:** [`spec_vr-cast-availability-guard.md`](spec_vr-cast-availability-guard.md)
**Tactical plan:** [`spec_vr-cast-availability-guard/INDEX.md`](spec_vr-cast-availability-guard/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full
**Flags:** —
**Outcome:** Verified

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 27 |
| PASS | 24 |
| WARN | 0 |
| FAIL | 0 |
| MANUAL | 2 |
| EXEMPT | 1 |

All 4 implementation phases verified. §6 research items updated to Resolved. Both standard and vr debug builds pass. No open issues.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | Cast unavailable determined once per process on VR | Phase 2 + Phase 3 | PASS | — |
| 2 | No repeated Cast SDK init attempts after negative verdict | Phase 3 (early-return in CastMediaManager.init) | PASS | — |
| 3 | Single capability verdict log instead of repeated warnings | Phase 3 (Timber.i once, no Timber.w on VR) | PASS | — |
| 4 | Existing Cast flow on Android with Play Services unchanged | Phase 1 (SUPPORT_CAST=true for standard/lite/photos/legacy) | PASS | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | Primary effect for `vr`, compatible with standard/lite/legacy | `SUPPORT_CAST` in all 6 flavors | PASS | build.gradle.kts:131,156,181,210,267,314 | — |
| 2 | No Android API forks | No `Build.VERSION` or API branching | PASS | compile-time BuildConfig only | — |
| 3 | Wear OS not touched | No wear/ files modified | PASS | diff contains only app_v2/ files | — |
| 4 | Heavy logic in player helper / capability layer, not Activity | Changes in CastMediaManager + CommandPanelLayoutPlanner | PASS | — | — |
| 5 | Timber only; one capability message max once per process | Single `Timber.i` in CastMediaManager.init() early-return | PASS | CastMediaManager.kt:116 | — |

### 2.3 Open Research Items (§6)

- §6.1 `Status: Resolved` — PASS
- §6.2 `Status: Resolved` — PASS
- §6.3 `Status: Resolved` — PASS

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| §8 states "No FEATURES doc update required" | EXEMPT | Platform guard, not user feature | — |

### 2.5 Completion Criteria (§11)

- [MANUAL] Quest 3 за процесс — не более одного сообщения о недоступности Cast runtime.
- [PASS] Player bootstrap не делает повторных попыток: CastMediaManager.init() early-return when SUPPORT_CAST=false.
- [MANUAL] На supported Android Cast flow продолжает работать.

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| All 4 phase files exist | PASS | Confirmed | — |
| All phases marked `[x] done` | PASS | All 4 phase files | — |
| INDEX Status: Approved | PASS | INDEX.md:3 | — |

### 3.2 Phase 1 — Add SUPPORT_CAST BuildConfig Flag

**Outcome:** Verified

| Step | Outcome | Evidence |
|------|:-------:|----------|
| standard SUPPORT_CAST=true | PASS | build.gradle.kts:131 |
| lite SUPPORT_CAST=true | PASS | build.gradle.kts:156 |
| photos SUPPORT_CAST=true | PASS | build.gradle.kts:181 |
| legacy SUPPORT_CAST=true | PASS | build.gradle.kts:210 |
| vr SUPPORT_CAST=false | PASS | build.gradle.kts:267 |
| vrUnlicensed SUPPORT_CAST=false | PASS | build.gradle.kts:314 |
| Dev log | PASS | CHANGELOG.md:4033 |

### 3.3 Phase 2 — Guard App-Level CastContext Init

**Outcome:** Verified

| Step | Outcome | Evidence |
|------|:-------:|----------|
| `if (BuildConfig.SUPPORT_CAST)` wraps CastContext init | PASS | FastMediaSorterApp.kt:156 |
| CastContext.getSharedInstance inside guard | PASS | FastMediaSorterApp.kt:158 |
| Dev log | PASS | CHANGELOG.md:4034 |

### 3.4 Phase 3 — Guard Player-Level Cast Init

**Outcome:** Verified

| Step | Outcome | Evidence |
|------|:-------:|----------|
| Early-return `!BuildConfig.SUPPORT_CAST` in init() | PASS | CastMediaManager.kt:115 |
| Single Timber.i before return | PASS | CastMediaManager.kt:116 |
| No Log.d() usage | PASS | grep: no matches |
| CastContext.getSharedInstance inside try/catch (unreachable on vr) | PASS | CastMediaManager.kt:120 |
| Dev log | PASS | CHANGELOG.md:4035 |

### 3.5 Phase 4 — Hide Cast Button on Unsupported Flavors

**Outcome:** Verified

| Step | Outcome | Evidence |
|------|:-------:|----------|
| CommandPanelLayoutPlanner: `BuildConfig.SUPPORT_CAST &&` | PASS | CommandPanelLayoutPlanner.kt:160 |
| CommandPanelController: `BuildConfig.SUPPORT_CAST &&` on btnCastCmd.isVisible | PASS | CommandPanelController.kt:356 |
| Dev log (Planner) | PASS | CHANGELOG.md:4036 |
| Dev log (Controller) | PASS | CHANGELOG.md:4037 |

---

## 4. Cross-Reference Checks

- Goal §2.1 ↔ Phase 3 — PASS.
- Goal §2.2 ↔ Phase 3 — PASS.
- Goal §2.4 ↔ Phase 1 — PASS.
- ADR-1 ↔ Phase 3 — PASS.

---

## 5. Manual Acceptance Signals

- [ ] Quest 3 logcat for one full VR session contains exactly one cast-related message: `CastMediaManager: cast not supported on this platform — init skipped`
- [ ] On a standard Android device with Chromecast: cast button visible for images/video when WiFi connected; cast dialog opens normally.

---

## 6. Action Items (FAIL + WARN, priority order)

None.
