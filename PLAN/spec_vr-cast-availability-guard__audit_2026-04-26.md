# Spec Audit: vr-cast-availability-guard

**Strategic spec:** [`spec_vr-cast-availability-guard.md`](spec_vr-cast-availability-guard.md)
**Tactical plan:** [`spec_vr-cast-availability-guard/INDEX.md`](spec_vr-cast-availability-guard/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 27 |
| PASS | 21 |
| WARN | 3 |
| FAIL | 0 |
| MANUAL | 2 |
| EXEMPT | 1 |

All 4 implementation phases are fully in place and both standard + vr debug builds pass. Three WARN items: §6 research questions remain `Status: Open` in the strategic spec, though all three were resolved and documented in the tactical INDEX.md. No functional failures.

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
| 2 | No Android API forks | No `Build.VERSION` or API branching in changes | PASS | compile-time BuildConfig only | — |
| 3 | Wear OS not touched | No wear/ files modified | PASS | diff contains only app_v2/ files | — |
| 4 | Heavy logic in player helper / capability layer, not Activity | Changes in CastMediaManager (helper), CommandPanelLayoutPlanner (helper) | PASS | — | — |
| 5 | Timber only; one capability message max once per process | Single `Timber.i` in CastMediaManager.init() early-return; no Timber.w path executed | PASS | CastMediaManager.kt:116 | — |

### 2.3 Open Research Items (§6)

- **WARN** — §6.1 "Где хранить capability verdict?" still `Status: Open` in strategic spec. Resolved in INDEX.md: `BuildConfig.SUPPORT_CAST` is compile-time process-level verdict.
- **WARN** — §6.2 "Нужно ли скрывать UI-команду полностью?" still `Status: Open` in strategic spec. Resolved in INDEX.md: hidden entirely (same pattern as SUPPORT_VR_PLAYER).
- **WARN** — §6.3 "Жёсткий flavor guard vs runtime guard" still `Status: Open` in strategic spec. Resolved in INDEX.md: strict flavor gate — vr/vrUnlicensed always lack Google Play Services.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| §8 states "No FEATURES doc update required" | EXEMPT | Confirmed: platform guard, not a user feature | — |

### 2.5 Completion Criteria (§11)

- [MANUAL] Quest 3 за процесс — не более одного сообщения о недоступности Cast runtime. (device verification)
- [PASS] Player bootstrap не делает повторных попыток: CastMediaManager.init() early-return when SUPPORT_CAST=false — `CastContext.getSharedInstance` unreachable on vr flavor.
- [MANUAL] На supported Android Cast flow продолжает работать. (device verification; SUPPORT_CAST=true for non-VR, no logic change)

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| All 4 phase files exist | PASS | Glob confirmed phase_1..4 present | — |
| All phases marked `[x] done` | PASS | All 4 phase files updated | — |
| INDEX Status: Approved | PASS | INDEX.md:3 | — |

### 3.2 Phase 1 — Add SUPPORT_CAST BuildConfig Flag

**Outcome:** Verified

#### 3.2.1 Files Touched

| File | Expected | Exists? | Status |
|------|---------|:-------:|:------:|
| `app_v2/build.gradle.kts` | modified | ✓ | PASS |

#### 3.2.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 1 | standard: SUPPORT_CAST=true | Grep hit | PASS | build.gradle.kts:131 |
| 2 | lite: SUPPORT_CAST=true | Grep hit | PASS | build.gradle.kts:156 |
| 3 | photos: SUPPORT_CAST=true | Grep hit | PASS | build.gradle.kts:181 |
| 4 | legacy: SUPPORT_CAST=true | Grep hit | PASS | build.gradle.kts:210 |
| 5 | vr: SUPPORT_CAST=false | Grep hit | PASS | build.gradle.kts:267 |
| 6 | vrUnlicensed: SUPPORT_CAST=false | Grep hit | PASS | build.gradle.kts:314 |

#### 3.2.3 Dev Log

| File | Status | Evidence |
|------|:------:|----------|
| `app_v2/build.gradle.kts` | PASS | CHANGELOG.md:4033 |

---

### 3.3 Phase 2 — Guard App-Level CastContext Init

**Outcome:** Verified

#### 3.3.1 Files Touched

| File | Expected | Exists? | Status |
|------|---------|:-------:|:------:|
| `FastMediaSorterApp.kt` | modified | ✓ | PASS |

#### 3.3.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 1 | CastContext init wrapped in `if (BuildConfig.SUPPORT_CAST)` | Grep `SUPPORT_CAST` in FastMediaSorterApp | PASS | FastMediaSorterApp.kt:156 |
| 2 | CastContext.getSharedInstance inside the if-block | Grep hit | PASS | FastMediaSorterApp.kt:158 |

#### 3.3.3 Dev Log

| File | Status | Evidence |
|------|:------:|----------|
| `FastMediaSorterApp.kt` | PASS | CHANGELOG.md:4034 |

---

### 3.4 Phase 3 — Guard Player-Level Cast Init

**Outcome:** Verified

#### 3.4.1 Files Touched

| File | Expected | Exists? | Status |
|------|---------|:-------:|:------:|
| `CastMediaManager.kt` | modified | ✓ | PASS |

#### 3.4.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 1 | Early-return guard `!BuildConfig.SUPPORT_CAST` at top of init() | Grep hit | PASS | CastMediaManager.kt:115 |
| 2 | Single Timber.i message before return | Grep hit | PASS | CastMediaManager.kt:116 |
| 3 | No Log.d() usage | Grep: no matches | PASS | — |
| 4 | CastContext.getSharedInstance remains inside try/catch (not reached on vr) | Grep hit inside try | PASS | CastMediaManager.kt:120 |

#### 3.4.3 Dev Log

| File | Status | Evidence |
|------|:------:|----------|
| `CastMediaManager.kt` | PASS | CHANGELOG.md:4035 |

---

### 3.5 Phase 4 — Hide Cast Button on Unsupported Flavors

**Outcome:** Verified

#### 3.5.1 Files Touched

| File | Expected | Exists? | Status |
|------|---------|:-------:|:------:|
| `CommandPanelLayoutPlanner.kt` | modified | ✓ | PASS |
| `CommandPanelController.kt` | modified | ✓ | PASS |

#### 3.5.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 1 | CommandPanelLayoutPlanner: `BuildConfig.SUPPORT_CAST &&` before CAST condition | Grep hit | PASS | CommandPanelLayoutPlanner.kt:160 |
| 2 | CommandPanelController: `BuildConfig.SUPPORT_CAST &&` on btnCastCmd.isVisible | Grep hit | PASS | CommandPanelController.kt:356 |

#### 3.5.3 Dev Log

| File | Status | Evidence |
|------|:------:|----------|
| `CommandPanelLayoutPlanner.kt` | PASS | CHANGELOG.md:4036 |
| `CommandPanelController.kt` | PASS | CHANGELOG.md:4037 |

---

## 4. Cross-Reference Checks

- Goal §2.1 (single verdict) ↔ Phase 3 (Timber.i once, no Timber.w on vr) — PASS.
- Goal §2.2 (no repeated init) ↔ Phase 3 (early-return in init()) — PASS.
- Goal §2.4 (standard not degraded) ↔ Phase 1 (SUPPORT_CAST=true for 4 non-vr flavors) + Phase 2/3 (guarded by flag, not unconditional) — PASS.
- ADR-1 (capability decision not repeated try/catch) ↔ Phase 3 (flag-based skip at compile time) — PASS.

---

## 5. Manual Acceptance Signals

- [ ] Quest 3 logcat for one full VR session contains exactly one cast-related message: `CastMediaManager: cast not supported on this platform — init skipped`
- [ ] On a standard Android device with Chromecast: cast button visible for images/video when WiFi connected; cast dialog opens normally.

---

## 6. Action Items (FAIL + WARN, priority order)

1. [FOLLOW-UP] **[WARN §2.3 — §6.1]** `Status: Open` in strategic spec for "Где хранить capability verdict?" — Update to `Status: Resolved` with resolution: `BuildConfig.SUPPORT_CAST` (compile-time flag).
2. [FOLLOW-UP] **[WARN §2.3 — §6.2]** `Status: Open` in strategic spec for "Нужно ли скрывать UI-команду полностью?" — Update to `Status: Resolved` with resolution: hidden entirely via BuildConfig gate.
3. [FOLLOW-UP] **[WARN §2.3 — §6.3]** `Status: Open` in strategic spec for "Жёсткий flavor guard vs runtime guard" — Update to `Status: Resolved` with resolution: strict flavor gate chosen.
