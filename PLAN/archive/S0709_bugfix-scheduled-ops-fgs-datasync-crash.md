# Draft: S0709 - Scheduled-ops dataSync FGS crashes (ForegroundServiceDidNotStopInTimeException)

**Ticket:** S0709
**Status:** Archived
**Priority:** 85
**Date:** 2026-06-26
**Tier:** Ad-hoc (bugfix, crash)
**Source:** Parked from log analysis of crash `logs/fastmediasorter_crash_20260626_022110.log` (session `logs/fastmediasorter_20260625_015431.log`, ~24.5 h uptime).

> Draft inbox - raw capture. Not yet researched/approved. Style gate exempt.

## 0. Raw finding (crash evidence)

Fatal, main thread, build `2.60.6250.150-NoLegal-DEBUG` (260625015), device API 36:

```
android.app.RemoteServiceException$ForegroundServiceDidNotStopInTimeException:
A foreground service of type dataSync did not stop within its timeout:
ComponentInfo{com.sza.fastmediasorter.debug/androidx.work.impl.foreground.SystemForegroundService}
```

Facts:
- `ScheduledOperationsWorker` поднимает FGS типа `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (`ScheduledOperationsWorker.kt:80-88`).
- Проект на WorkManager 2.9.0 (`app_v2/build.gradle.kts:1139` work-runtime-ktx, `:1291` work-multiprocess).
- На Android 14+/15+/16 система присылает FGS-таймаут типу `dataSync` через `Service.onTimeout()`. `SystemForegroundService` в 2.9.0 этот колбэк не переопределяет -> сервис не останавливается в grace-окне (таймаут пришёл на старте прогона 02:21:00, краш 02:21:10) -> система кидает фатал.
- Обработка `onTimeout` для foreground-воркеров добавлена в WorkManager 2.10.0.
- Воркер живёт в `src/main` -> крэш шипается во всех флейворах, не только в debug.

## 1. Problem

Любой пользователь с запланированной фоновой операцией на Android 14+ рискует получить фатальный `ForegroundServiceDidNotStopInTimeException`, потому что используемая версия WorkManager (2.9.0) не обрабатывает системный FGS-таймаут типа `dataSync`. Краш - вопрос времени работы процесса.

## 2. Direction (rough)

Поднять WorkManager до 2.10.x (синхронно `work-runtime-ktx` и `work-multiprocess`), где `SystemForegroundService` штатно гасит FGS по `onTimeout`. Проверить, что 2.10.x не ломает существующее поведение воркеров (периодические задачи, Hilt-фабрика, multiprocess в debug). Детали и регресс-проверки - в /spec-tech.

**Non-goals:** логику самой запланированной операции тут не трогаем (см. S0710).

## Related

- S0710 - вечный цикл фонового MOVE, который и держит dataSync-FGS под таймаутом (корневая причина экспозиции этого крэша).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Direction fully realised in code: `work-runtime-ktx` and `work-multiprocess` both at `2.10.1` (build.gradle.kts:1141 / :1293), in sync, marked `(S0709)`. WorkManager 2.10.x `SystemForegroundService` handles the `dataSync` `onTimeout` callback internally - no app-code change required. No leftover `work:2.9.0`, no stale `Timber.d("S0709:` tags.

### Manual / on-device

- [ ] Long-uptime FGS `dataSync` timeout no longer fatal - root cause now library-handled; not practically reproducible on emulator (needs 24h+ uptime to trigger the system timeout).
