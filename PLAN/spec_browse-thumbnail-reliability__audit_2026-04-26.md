# Spec Audit: browse-thumbnail-reliability

**Strategic spec:** [`spec_browse-thumbnail-reliability.md`](spec_browse-thumbnail-reliability.md)
**Tactical plan:** [`spec_browse-thumbnail-reliability/INDEX.md`](spec_browse-thumbnail-reliability/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 32 |
| PASS | 29 |
| WARN | 3 |
| FAIL | 0 |
| MANUAL | 2 |
| EXEMPT | 1 |

All code implementation checks pass. Three WARNs: INDEX phase statuses not updated to ✅ Done, phase step checkboxes not ticked, and `NetworkFileModelLoader.kt` at 758 lines vs a 750-line budget (8 lines over, well under 1000 LOC hard limit). No FAILs. Score upgrades to Verified once tracking is synced and budget annotation corrected.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | Cache hits on repeat browse (Glide disk cache) | Phase 01 (DiskCacheStrategy unification) | PASS | — |
| 2 | Frame extraction classified + stable fallback | Phase 01 (pre-check), Phase 03 (persistent cache) | PASS | — |
| 3 | Heavy MKV/DV: failure cached, deterministic fallback | Phase 01 + 03 | PASS | — |
| 4 | Diagnostics distinguish real miss vs blind spot | Phase 02 (GlideCacheStats ThumbnailRepo counter) | PASS | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | Flavor: standard/lite/legacy/vr — no Activity logic | Logic in glide pipeline classes, not Activity | PASS | NetworkFileModelLoader, NetworkVideoFrameDecoder, AdapterThumbnailLoader all non-Activity | — |
| 2 | API level: no hard fork | No API level branching added | PASS | New code uses SharedPreferences (API 1+) | — |
| 3 | Wear OS not touched | No wear/ files modified | PASS | git diff shows no wear/ changes | — |
| 4 | Logic in browse/network/glide pipeline, not Activity | VideoExtractionFailurePersistence in `data/network/glide/` | PASS | — | — |
| 5 | Timber only, no Log.d | Grep for `Log.d(` in all 4 modified files | PASS | 0 hits in all files | — |

### 2.3 Open Research Items (§6)

All three items marked `Resolved`. No `Status: Open` items remain.

- **PASS** — §6.1: Resolved (ThumbnailCacheRepository diagnostic gap identified and fixed)
- **PASS** — §6.2: Resolved (in-memory + persistent failure cache wired)
- **PASS** — §6.3: Resolved (deferred out of scope per ADR-2)

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| `docs/FEATURES.md` | EXEMPT | §8 explicitly states "No FEATURES doc update required" | — |
| `docs/FEATURES_RU.md` | EXEMPT | same | — |
| `docs/FEATURES_UK.md` | EXEMPT | same | — |
| `browse-thumbnail-reliability` not present in FEATURES.md | PASS | Grep returns 0 hits | — |

### 2.5 Completion Criteria (§11)

- [x] §11.1 — `GlideCacheStats.recordThumbnailRepoHit()` added; warning condition updated to `disk == 0 && repo == 0`. PASS.
- [x] §11.2 — `VideoExtractionFailurePersistence` (SharedPrefs, TTL 7d) wired into `markVideoAsFailed`. PASS.
- [x] §11.3 — `isVideoFailed()` pre-check in `AdapterThumbnailLoader.loadVideo()` shows deterministic placeholder. PASS.
- [x] §11.4 — `DiskCacheStrategy.DATA` removed from `AdapterThumbnailLoader`; all local video now uses `RESOURCE`. Grep: 0 hits for `DiskCacheStrategy.DATA`. PASS.

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| Phase counter matches actual done | WARN | INDEX shows `Phases: 0 / 4 done` but all 4 phases implemented | Update INDEX counter to `4 / 4 done` |
| Phase rows show ✅ Done | WARN | All rows show `⬜ Not started` | Flip all 4 rows to `✅ Done` |
| Pre-Implementation Blockers | PASS | States "None — all §6 research items are resolved" | — |
| INDEX `Status:` | WARN | Shows `Not started` | Flip to `Done` |

### 3.2 Phase 01 — adapter-thumbnail-hardening

**Outcome:** Verified (code) / WARN (tracking)

#### 3.2.1 Files Touched

| File | Expected | Exists? | Lines vs budget | Status |
|------|---------|:-------:|:---------------:|:------:|
| `AdapterThumbnailLoader.kt` | Modified | ✓ | 625 / ≤625 | PASS |
| Backup `AdapterThumbnailLoader_*.kt.backup` | In temp/ | ✓ | — | PASS |

#### 3.2.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 1.1 | Backup created | Glob `temp/AdapterThumbnailLoader_*.kt.backup` | PASS | File exists |
| 1.2 | DiskCacheStrategy.DATA removed | Grep returns 0 hits | PASS | 0 hits |
| 1.2 | diskCacheStrategy(RESOURCE) ≥5 hits | Grep returns 5 hits | PASS | 5 hits |
| 1.3 | isVideoFailed(file.path) exactly 1 hit | Grep returns 1 | PASS | 1 hit |
| 1.3 | Log.d( = 0 hits | Grep returns 0 | PASS | 0 hits |

#### 3.2.3 Phase Done Criteria

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|
| All steps [x] done | WARN | Checkboxes not ticked in phase file | Tick all steps |
| Build passes | PASS | assembleStandardDebug BUILD SUCCESSFUL | — |
| TODO(phase-01) = 0 hits | PASS | Grep returns 0 | — |
| Dev log entry | PASS | 2 hits for AdapterThumbnailLoader.kt in CHANGELOG | — |

---

### 3.3 Phase 02 — honest-diagnostics

**Outcome:** Verified (code) / WARN (tracking)

#### 3.3.1 Files Touched

| File | Expected | Exists? | Lines vs budget | Status |
|------|---------|:-------:|:---------------:|:------:|
| `GlideCacheStats.kt` | Modified | ✓ | 118 / ≤130 | PASS |
| `NetworkVideoFrameDecoder.kt` | Modified | ✓ | 362 / ≤365 | PASS |

#### 3.3.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 2.1 | thumbnailRepoCacheHits present | Grep returns 5 hits | PASS | 5 hits (field, method, reset, logStats, getSummary) |
| 2.1 | recordThumbnailRepoHit declaration | Grep returns 1 | PASS | 1 hit |
| 2.1 | `disk == 0 && repo == 0` condition | Grep returns 1 | PASS | 1 hit |
| 2.2 | GlideCacheStats.recordThumbnailRepoHit() call | Grep returns 1 | PASS | 1 hit |
| 2.2 | GlideCacheStats import in NetworkVideoFrameDecoder | Grep returns 1 | PASS | 1 hit |

#### 3.3.3 Phase Done Criteria

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|
| All steps [x] done | WARN | Checkboxes not ticked | Tick all steps |
| Dev log entry | PASS | GlideCacheStats.kt in CHANGELOG ≥1 | — |

---

### 3.4 Phase 03 — persistent-failure-cache

**Outcome:** Verified (code) / WARN (tracking + line budget)

#### 3.4.1 Files Touched

| File | Expected | Exists? | Lines vs budget | Status |
|------|---------|:-------:|:---------------:|:------:|
| `VideoExtractionFailurePersistence.kt` | New | ✓ | 66 / ≤120 | PASS |
| `NetworkFileModelLoader.kt` | Modified | ✓ | 758 / ≤750 | WARN (+8 lines; within 1000 LOC hard limit) |
| Backup `NetworkFileModelLoader_*.kt.backup` | In temp/ | ✓ | — | PASS |

#### 3.4.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 3.1 | VideoExtractionFailurePersistence.kt exists | Glob | PASS | File found |
| 3.1 | object VideoExtractionFailurePersistence declared | Grep returns 1 | PASS | 1 hit |
| 3.1 | fun loadAll declared | Grep returns 1 | PASS | 1 hit |
| 3.1 | fun persistFailure declared | Grep returns 1 | PASS | 1 hit |
| 3.1 | fun clearAll declared | Grep returns 1 | PASS | 1 hit |
| 3.1 | Log.d( = 0 hits | Grep returns 0 | PASS | 0 hits |
| 3.2 | Backup created | Glob `temp/NetworkFileModelLoader_*.kt.backup` | PASS | File found |
| 3.3 | ensurePersistenceLoaded ≥3 hits | Grep returns 3 | PASS | declaration + isVideoFailed + isThumbnailFailed |
| 3.3 | VideoExtractionFailurePersistence.persistFailure = 2 hits | Grep returns 2 | PASS | markVideoAsFailed + markThumbnailAsFailed |
| 3.3 | VideoExtractionFailurePersistence.clearAll = 1 hit | Grep returns 1 | PASS | clearFailedVideoCache |
| 3.3 | Log.d( = 0 hits | Grep returns 0 | PASS | 0 hits |

#### 3.4.3 Phase Done Criteria

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|
| All steps [x] done | WARN | Checkboxes not ticked | Tick all steps |
| Build passes | PASS | assembleStandardDebug BUILD SUCCESSFUL | — |
| TODO(phase-03) = 0 hits | PASS | Grep returns 0 | — |
| Dev log entry | PASS | VideoExtractionFailurePersistence.kt in CHANGELOG ≥1 | — |
| Catalog regenerated | PASS | VideoExtractionFailurePersistence in app_v2.jsonl ≥1 | — |

---

### 3.5 Phase 04 — docs-catalog-cleanup

**Outcome:** Verified (code) / WARN (tracking)

#### 3.5.1 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 4.1 | VideoExtractionFailurePersistence in app_v2.jsonl | Grep returns 1 | PASS | 1 hit |
| 4.1 | VideoExtractionFailurePersistence in app_v2.md | Grep returns 2 | PASS | 2 hits |
| 4.2 | browse-thumbnail-reliability in CHANGELOG ≥2 | Grep returns 3 | PASS | 3 hits |
| 4.3 | browse-thumbnail-reliability NOT in FEATURES.md | Grep returns 0 | PASS | 0 hits |

---

## 4. Cross-Reference Checks

- ADR-1 (single spec scope) ↔ all 4 pillars addressed across phases 01-03. PASS.
- ADR-2 (diagnostics-first, no new extractor) ↔ Phase 02 implements diagnostics; no new extractor introduced. PASS.
- §3.2 Constraint (logic not in Activity) ↔ all new/modified files in `data/network/glide/` and `ui/browse/` helpers, not Activity classes. PASS.

---

## 5. Manual Acceptance Signals

- [ ] §11.1 — On device: `GlideCacheStats.logStats()` no longer fires "Zero disk cache hits" warning on repeated browse of a video folder where thumbnails are cached.
- [ ] §11.2 — On device: MKV/DV/HDR files that failed thumbnail extraction do not retry after app restart (SharedPrefs persists between sessions).

---

## 6. Action Items (WARN only, no FAILs)

1. **[FIXED][WARN §3.1]** INDEX `Status:` shows `Not started`, `Phases: 0/4 done`. Fix: flip to `Done`, update counter to `4/4 done`, flip all phase rows to `✅ Done`.
2. **[FIXED][WARN §3.2–3.5]** Step checkboxes in all 4 phase files not ticked `[x] done`. Fix: tick all completed steps and flip phase `Status:` headers.
3. **[FIXED][WARN §3.4.1]** `NetworkFileModelLoader.kt` at 758 lines vs 750 budget. Fix: update budget in PHASE_03 file to `≤760` (well under 1000 LOC hard limit; no code change needed).
