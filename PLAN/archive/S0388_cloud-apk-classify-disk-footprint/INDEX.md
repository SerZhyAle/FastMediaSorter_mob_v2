# Tactical Plan: S0388 - Cloud APK classification disk footprint

**Strategic spec:** [`../S0388_cloud-apk-classify-disk-footprint.md`](../S0388_cloud-apk-classify-disk-footprint.md)
**Status:** Tactical
**Created:** 2026-06-09

---

## Goal

Снизить дисковый след классификации облачных APK до транзиентного. Скачивать APK в одну временную копию, читать манифест, удалять копию сразу после. Убрать постоянный диск-кэш, двойное копирование, утечку обрезков и каскад ретраев при нехватке места. Решения §6: транзиент (без диск-кэша), стоп-на-сессию при нехватке места, тихая деградация без новых строк.

---

## Phases

| # | Title | Source set | Status |
|---|-------|-----------|--------|
| 01 | [Eliminate cloud→local double copy (move not copy)](PHASE_01__shared-move-not-copy.md) | `src/main` (shared) | ✅ Done |
| 02 | [Transient classification copy + size validation + stale purge](PHASE_02__transient-resolver.md) | `src/noLegal` | ✅ Done |
| 03 | [Transient lifecycle + out-of-space session stop](PHASE_03__cache-lifecycle-and-stop.md) | `src/noLegal` | ✅ Done |
| 04 | [Build, docs, catalog, debug probe](PHASE_04__build-docs-probe.md) | docs / build | 🔄 Device-test gate |

---

## Dependencies

- Phase 02 introduces the `VrArchiveResolution` return type; Phase 03 updates the only caller (`VrApkClassificationCache`) and its test. Both must land before the Phase 04 build - the intermediate state does not compile.
- Phase 01 is independent (shared layer) and could land first or in parallel.

---

## Files Touched (all phases)

- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` (Phase 01)
- `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt` (Phase 02)
- `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt` (Phase 03)
- `app_v2/src/testNoLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCacheTest.kt` (Phase 03)

---

## Validation

- Build: `noLegalDebug` (the only variant compiling the noLegal source set). Phase 04 gate.
- Device: open a cloud folder with APK archives on Quest - badges appear, cache volume does not grow unbounded, no error cascade when the disk is artificially full. Phase 04 sets `BlockNeedUserTest`.
