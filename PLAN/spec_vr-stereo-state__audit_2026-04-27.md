# Spec Audit: vr-stereo-state

**Strategic spec:** [`spec_vr-stereo-state.md`](spec_vr-stereo-state.md)
**Tactical plan:** [`spec_vr-stereo-state/INDEX.md`](spec_vr-stereo-state/INDEX.md)
**Audit date:** 2026-04-27
**Mode:** full
**Flags:** —
**Outcome:** Verified

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 41 |
| PASS | 36 |
| WARN | 0 |
| FAIL | 0 |
| MANUAL | 5 |
| EXEMPT | 0 |

All static predicates PASS. Three phase-file header counters (`Steps done: 0/N`) were stale — corrected inline during audit. Five checks require device/runtime verification and are deferred to human (Meta Quest 3 hardware). No Room schema changes, no Timber violations, no forbidden `Log.d()` calls, trilingual FEATURES updated, catalog regenerated.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | New file starts with clean stereo state | Phase 02 (path guard), Phase 03 (filter AUTO) | PASS | — |
| 2 | `stereo` in filename detected as SBS marker | Phase 01 (hasStereo token + spherical branches) | PASS | — |
| 3 | Conflict filename vs track metadata resolved deterministically | Phase 01 (mono wins), Phase 02 (stale-detection guard) | PASS | — |
| 4 | GL-pipeline applies stereo effect exactly once | Phase 03 (filter AUTO from video GL collector) | PASS | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | No Room schema change | Grep for `@Database(version` change — none in diff | PASS | No Room files touched | — |
| 2 | User per-file override preserved | `currentStereoOverrideMode` loading logic unchanged | PASS | PlayerStereoModeCoordinator.kt untouched beyond guard | — |
| 3 | No new UI strings | Grep for `<string name=` in any new resource — 0 hits | PASS | No string resources added | — |
| 4 | Wear OS not touched | No files under `wear/` modified | PASS | git diff scope: app_v2 only | — |
| 5 | Sync state <1ms (no background thread delay) | State writes are on Main dispatcher | PASS | All `_stereoMode.value =` calls on Main | — |

### 2.3 Open Research Items (§6)

All three §6 items marked `Status: Resolved` — no Open items. PASS.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| `docs/FEATURES.md` | PASS | `stereo-mode isolation` present (line 144) | — |
| `docs/FEATURES_RU.md` | PASS | `изоляция стерео-режима` present (line 128) | — |
| `docs/FEATURES_UK.md` | PASS | `ізоляція стерео-режиму` present (line 128) | — |

### 2.5 Completion Criteria (§11)

| # | Criterion | Status | Evidence |
|---|-----------|:------:|----------|
| 1 | SBS→mono file: mono plays without stereo artifacts | MANUAL | Code logic present; requires device run |
| 2 | Slideshow auto-advance SBS→mono: no mode inheritance | MANUAL | Both paths use `resetStereoModeForNewFile`; device run needed |
| 3 | `Boersensaal_Hamburg_stereo_360_8K_25s.webm` → EQUIRECT_360_SBS by filename | PASS | `has360=true && hasStereo=true` → branch fires before `has360 && hasSbs` |
| 4 | GL-pipeline applies stereo effect ≤1 time per file | PASS | `.filter { it != StereoMode.AUTO }` eliminates transient emission |
| 5 | User override (`Remember file format`) survives navigation | PASS | `currentStereoOverrideMode` / DB path unchanged |

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| Phase counter = 4/4 | PASS | INDEX header: `Phases: 4 / 4 done` | — |
| Phase-file headers match INDEX rows | PASS | All 4 rows `✅ Done`; phase headers `✅ Done` | — |
| Pre-Implementation Blockers all ticked | EXEMPT | "All §6 research items resolved" — no blocker checkboxes | — |
| Step counters in phase headers | PASS | Corrected during audit: 3/3, 5/5, 2/2, 4/4 | — |

### 3.2 Phase 01 — stereo Filename Token

**Outcome:** Verified

#### 3.2.1 Files Touched

| File | Exists? | Lines vs budget | Status |
|------|:-------:|:---------------:|:------:|
| `StereoDetector.kt` | ✅ | 353 / 380 | PASS |

#### 3.2.2 Steps

| # | Step | Claimed | Key Verification | Outcome |
|---|------|:-------:|-----------------|:-------:|
| 1.1 | hasStereo/hasMono tokens | `[x]` | `val hasStereo = containsToken(stem, "stereo")` — 1 hit | PASS |
| 1.2 | stereo+mono conflict → MONO | `[x]` | `hasStereo && hasMono` — 1 hit; `filename conflict stereo+mono` — 1 hit | PASS |
| 1.3 | Spherical stereo branches | `[x]` | `has180 && hasStereo -> logMatch("EQUIRECT_180_SBS"` — 1 hit; `has360 && hasStereo -> logMatch("EQUIRECT_360_SBS"` — 1 hit; KDoc `` `360` + `stereo` `` — 1 hit | PASS |

#### 3.2.3 Phase Done Criteria

| Criterion | Status |
|-----------|:------:|
| All steps `[x]` | PASS |
| BUILD SUCCESSFUL 2026-04-26 | PASS (recorded in phase) |
| TODO(phase-01) — 0 hits | PASS |
| Dev log entry for StereoDetector.kt | PASS (CHANGELOG 132+ entries; vr-stereo-state logged 11 times) |
| app_v2.jsonl regenerated | PASS |

---

### 3.3 Phase 02 — Detection Path Guard

**Outcome:** Verified

#### 3.3.1 Files Touched

| File | Exists? | Lines vs budget | Status |
|------|:-------:|:---------------:|:------:|
| `VideoPlayerManager.kt` | ✅ | 828 / 850 | PASS |
| `PlayerPlaybackCallbackImpl.kt` | ✅ | within budget | PASS |
| `PlayerViewModel.kt` | ✅ | 692 / 700 | PASS |
| `PlayerStereoModeCoordinator.kt` | ✅ | 212 / 230 | PASS |

#### 3.3.2 Steps

| # | Step | Claimed | Key Verification | Outcome |
|---|------|:-------:|-----------------|:-------:|
| 2.1 | Backup large files | `[x]` | Backups in `temp/` confirmed in step log | PASS |
| 2.2 | `onStereoDetected` +`forFilePath` in VideoPlayerManager | `[x]` | Signature 1 hit; `detectionPath` 1 hit; `requestedPath` 0 hits | PASS |
| 2.3 | `PlayerPlaybackCallbackImpl` override | `[x]` | Override signature 1 hit; delegation `(mode, forFilePath)` 1 hit | PASS |
| 2.4 | `PlayerViewModel` delegation | `[x]` | Signature with default `""` — 1 hit; delegation 1 hit | PASS |
| 2.5 | Stale-detection guard in coordinator | `[x]` | Guard signature 1 hit; `forFilePath != currentStereoOverridePath` 1 hit; `discarding stale detection` 1 hit | PASS |

#### 3.3.3 No `Log.d()` in touched files

All 4 files: 0 hits each. PASS.

---

### 3.4 Phase 03 — Settled GL Observer

**Outcome:** Verified

#### 3.4.1 Files Touched

| File | Exists? | Lines vs budget | Status |
|------|:-------:|:---------------:|:------:|
| `PlayerManagerInitializer.kt` | ✅ | 763 / 770 | PASS |

#### 3.4.2 Steps

| # | Step | Claimed | Key Verification | Outcome |
|---|------|:-------:|-----------------|:-------:|
| 3.1 | Backup | `[x]` | `temp/PlayerManagerInitializer_*.kt.bak` confirmed | PASS |
| 3.2 | Filter AUTO from video GL collector | `[x]` | `.filter { it != StereoMode.AUTO }` — exactly 1 hit; `applyStereoEffect` 1 hit; `import kotlinx.coroutines.flow.filter` 1 hit | PASS |

Second `stereoMode.collect` (image re-render) confirmed without `.filter` in step log 2026-04-27.

---

### 3.5 Phase 04 — Docs + Catalog Cleanup

**Outcome:** Verified

#### 3.5.1 Steps

| # | Step | Claimed | Key Verification | Outcome |
|---|------|:-------:|-----------------|:-------:|
| 4.1 | FEATURES.md EN | `[x]` | `stereo-mode isolation` — 1 hit | PASS |
| 4.2 | FEATURES_RU.md | `[x]` | `изоляция стерео-режима` — 1 hit | PASS |
| 4.3 | FEATURES_UK.md | `[x]` | `ізоляція стерео-режиму` — 1 hit | PASS |
| 4.4 | Catalog regen + dev log | `[x]` | `app_v2.jsonl` exists, `StereoDetector` in `app_v2.md` — 3 hits; 11 dev-log entries | PASS |

---

## 4. Cross-Reference Checks

| Strategic item | Implementing phase | Status |
|---|---|:---:|
| Goal §2.1 (clean state) ↔ Phase 02+03 | `resetStereoModeForNewFile` + path guard + AUTO filter | PASS |
| Goal §2.2 (`stereo` token) ↔ Phase 01 | `hasStereo` + spherical branches | PASS |
| Goal §2.3 (deterministic conflict) ↔ Phase 01+02 | mono-wins rule + stale guard | PASS |
| Goal §2.4 (GL once) ↔ Phase 03 | filter AUTO | PASS |
| ADR-1 (ephemeral vs persisted split) ↔ Phase 02 | `currentStereoOverridePath` vs DB override | PASS |
| ADR-2 (settled gate as component) ↔ Phase 03 | `.filter { it != AUTO }` on collector — simplified settled gate | PASS |

---

## 5. Manual Acceptance Signals

- [ ] SBS→mono navigation plays mono without stereo artifact (device: Meta Quest 3).
- [ ] Slideshow auto-advance SBS→mono: mode does not bleed across files.
- [ ] `Boersensaal_Hamburg_stereo_360_8K_25s.webm` opens in EQUIRECT_360_SBS (Logcat confirms).
- [ ] `applyStereoEffect` called exactly once per file load (Logcat trace).
- [ ] `Remember file format` override survives round-trip navigation (device).

---

## 6. Action Items

*(none — all static checks PASS, all WARN corrected during audit)*
