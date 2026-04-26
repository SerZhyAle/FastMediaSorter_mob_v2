# Spec Audit: browse-thumbnail-reliability

**Strategic spec:** [`spec_browse-thumbnail-reliability.md`](spec_browse-thumbnail-reliability.md)
**Tactical plan:** [`spec_browse-thumbnail-reliability/INDEX.md`](spec_browse-thumbnail-reliability/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full
**Flags:** —
**Outcome:** Verified

---

## 1. Summary

| Metric | Count |
| ------ | ----: |
| Checks total | 32 |
| PASS | 30 |
| WARN | 0 |
| FAIL | 0 |
| MANUAL | 2 |
| EXEMPT | 1 |

All code and tracking checks pass. The three WARNs from the first audit (INDEX status drift, step checkbox drift, line budget annotation) were resolved by `spec-fix`. Two checks are MANUAL (device verification required). FEATURES docs are EXEMPT per strategic §8.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
| --- | ---- | ---------------------- | :----: | ------ |
| 1 | Cache hits on repeat browse | Phase 01 — DiskCacheStrategy unification | PASS | — |
| 2 | Frame extraction classified + stable fallback | Phase 01 pre-check + Phase 03 persistent cache | PASS | — |
| 3 | Heavy MKV/DV: failure cached, deterministic fallback | Phase 01 + 03 | PASS | — |
| 4 | Diagnostics distinguish real miss vs blind spot | Phase 02 — GlideCacheStats repo counter | PASS | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
| --- | ---------- | ------------ | :----: | -------- | ------ |
| 1 | No Activity logic | All changes in glide/browse pipeline | PASS | data/network/glide/, ui/browse/ helpers only | — |
| 2 | No API level fork | SharedPreferences (API 1+) | PASS | VideoExtractionFailurePersistence | — |
| 3 | Wear OS not touched | No wear/ files in diff | PASS | git diff | — |
| 4 | Timber only | Grep `Log.d(` = 0 in all 4 modified files | PASS | 0 hits | — |

### 2.3 Open Research Items (§6)

All three items `Resolved`. No `Status: Open` remains. PASS.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
| -------- | :----: | -------- | ------ |
| `docs/FEATURES.md` | EXEMPT | §8 explicit: no update required | — |
| `docs/FEATURES_RU.md` | EXEMPT | same | — |
| `docs/FEATURES_UK.md` | EXEMPT | same | — |
| No accidental entry added | PASS | Grep `browse-thumbnail-reliability` in FEATURES = 0 | — |

### 2.5 Completion Criteria (§11)

- [x] §11.1 — `GlideCacheStats.recordThumbnailRepoHit()` added; warning condition `disk==0 && repo==0`. PASS.
- [x] §11.2 — `VideoExtractionFailurePersistence` (SharedPrefs, TTL 7d) wired into `markVideoAsFailed`. PASS.
- [x] §11.3 — `isVideoFailed()` pre-check in `loadVideo()` returns deterministic placeholder. PASS.
- [x] §11.4 — `DiskCacheStrategy.DATA` removed from `AdapterThumbnailLoader`; 0 hits. PASS.

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
| ----- | :----: | -------- | ------ |
| Phase counter | PASS | `4 / 4 done` | — |
| Phase rows show ✅ Done | PASS | All 4 rows ✅ Done | — |
| Phase file headers match INDEX | PASS | All 4 phase files: `**Status:** ✅ Done` | — |
| INDEX Status | PASS | `Done` | — |
| Pre-Implementation Blockers | PASS | "None — all §6 resolved" | — |

### 3.2 Phase 01 — adapter-thumbnail-hardening

**Outcome:** Verified

| # | Check | Status | Evidence |
| --- | ----- | :----: | -------- |
| Status header | PASS | `✅ Done` | — |
| Step checkboxes | PASS | All `[x]` done | — |
| Backup exists | PASS | `temp/AdapterThumbnailLoader_*.kt.backup` found | — |
| DiskCacheStrategy.DATA = 0 | PASS | Grep: 0 hits | — |
| isVideoFailed(file.path) = 1 | PASS | Grep: 1 hit | — |
| Log.d( = 0 | PASS | Grep: 0 hits | — |
| Lines vs budget (625 / ≤625) | PASS | 625 lines | — |
| Dev log entry | PASS | CHANGELOG ≥2 hits for AdapterThumbnailLoader.kt | — |

### 3.3 Phase 02 — honest-diagnostics

**Outcome:** Verified

| # | Check | Status | Evidence |
| --- | ----- | :----: | -------- |
| Status header | PASS | `✅ Done` | — |
| Step checkboxes | PASS | All `[x]` done | — |
| thumbnailRepoCacheHits = 5 hits | PASS | Grep: 5 hits | — |
| recordThumbnailRepoHit declaration = 1 | PASS | Grep: 1 hit | — |
| disk==0 && repo==0 condition = 1 | PASS | Grep: 1 hit | — |
| GlideCacheStats.recordThumbnailRepoHit() in NetworkVideoFrameDecoder = 1 | PASS | Grep: 1 hit | — |
| GlideCacheStats import = 1 | PASS | Grep: 1 hit | — |
| Lines GlideCacheStats (118 / ≤130) | PASS | 118 lines | — |
| Lines NetworkVideoFrameDecoder (362 / ≤365) | PASS | 362 lines | — |
| Dev log entries | PASS | CHANGELOG ≥4 hits for GlideCacheStats.kt | — |

### 3.4 Phase 03 — persistent-failure-cache

**Outcome:** Verified

| # | Check | Status | Evidence |
| --- | ----- | :----: | -------- |
| Status header | PASS | `✅ Done` | — |
| Step checkboxes | PASS | All `[x]` done | — |
| VideoExtractionFailurePersistence.kt exists | PASS | Glob match | — |
| object VideoExtractionFailurePersistence = 1 | PASS | Grep: 1 hit | — |
| fun loadAll = 1 | PASS | Grep: 1 hit | — |
| fun persistFailure = 1 | PASS | Grep: 1 hit | — |
| fun clearAll = 1 | PASS | Grep: 1 hit | — |
| Log.d( = 0 in new file | PASS | Grep: 0 hits | — |
| Backup NetworkFileModelLoader exists | PASS | `temp/NetworkFileModelLoader_*.kt.backup` found | — |
| ensurePersistenceLoaded ≥3 hits | PASS | Grep: 3 hits | — |
| VideoExtractionFailurePersistence.persistFailure = 2 hits | PASS | Grep: 2 hits (markVideoAsFailed + markThumbnailAsFailed) | — |
| VideoExtractionFailurePersistence.clearAll = 1 | PASS | Grep: 1 hit | — |
| Log.d( = 0 in NetworkFileModelLoader | PASS | Grep: 0 hits | — |
| Lines VideoExtractionFailurePersistence (66 / ≤120) | PASS | 66 lines | — |
| Lines NetworkFileModelLoader (758 / ≤760) | PASS | 758 lines | — |
| Catalog regenerated | PASS | VideoExtractionFailurePersistence in app_v2.jsonl | — |
| Dev log entries | PASS | CHANGELOG has both new files | — |

### 3.5 Phase 04 — docs-catalog-cleanup

**Outcome:** Verified

| # | Check | Status | Evidence |
| --- | ----- | :----: | -------- |
| Status header | PASS | `✅ Done` | — |
| Step checkboxes | PASS | All `[x]` done | — |
| VideoExtractionFailurePersistence in app_v2.jsonl | PASS | 1 hit | — |
| VideoExtractionFailurePersistence in app_v2.md | PASS | 2 hits | — |
| browse-thumbnail-reliability in CHANGELOG ≥2 | PASS | 3 hits | — |
| browse-thumbnail-reliability NOT in FEATURES.md | PASS | 0 hits | — |

---

## 4. Cross-Reference Checks

- ADR-1 (single scope) ↔ all 4 strategic pillars addressed across phases 01–03. PASS.
- ADR-2 (diagnostics-first, no new extractor) ↔ Phase 02 implements diagnostics only; no extractor added. PASS.
- §3.2 constraint (no Activity logic) ↔ all changes in `data/network/glide/` and `ui/browse/` manager class. PASS.

---

## 5. Manual Acceptance Signals

- [ ] §11.1 — On device: `GlideCacheStats.logStats()` does not fire "Zero disk cache hits" warning on repeated browse of cached video folder.
- [ ] §11.2 — On device: MKV/DV/HDR files that failed thumbnail extraction do not retry after app restart.

---

## 6. Action Items

None — all checks PASS, MANUAL, or EXEMPT.
