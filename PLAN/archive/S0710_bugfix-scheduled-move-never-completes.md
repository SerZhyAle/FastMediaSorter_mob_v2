# Draft: S0710 - Scheduled background MOVE never completes (perpetual re-upload loop)

**Ticket:** S0710
**Status:** Archived
**Priority:** 75
**Date:** 2026-06-26
**Tier:** Ad-hoc (bugfix, moderate)
**Source:** Parked from log analysis of session `logs/fastmediasorter_20260625_015431.log` (the loop that fed the S0709 crash).

> Draft inbox - raw capture. Not yet researched/approved. Style gate exempt.

## 0. Raw finding (log evidence)

Запланированная операция `op=1` (MOVE из локального `Downloads` на SMB `192.168.1.112/down`) каждый достижимый час крутит один и тот же неудачный цикл по 193 файлам:
1. скан папки -> 193 файла;
2. каждый файл **успешно** заливается по SMB (`uploadToSmb: SUCCESS`);
3. при удалении локального оригинала -> `Batch delete permission required, throwing exception` -> `ScheduledOp[1] MOVE ERROR`.

Итог прогона: `ScheduledOperationsWorker: op=1 done - 0 files, errors=193`. Файлы не удаляются, остаются в `Downloads`, через час те же 193 файла **заливаются заново**. Часы без сети дают быстрый фейл `errors=1`. Цикл бесконечный (видно на всём ~24.5 ч отрезке лога), жжёт сеть/батарею и постоянно гоняет dataSync-FGS.

Два независимых дефекта внутри этого:

**A. Удаление не учитывает All-Files-Access.** В логе после краша `PermissionHelper: API 36 - MANAGE_EXTERNAL_STORAGE=true` (All-Files-Access выдан). Но `deleteViaMediaStore` уходит в прямой `File.delete()` только если выдан именно `MANAGE_MEDIA` (`LocalOperationStrategy.kt:166-190`); `Environment.isExternalStorageManager()` / `MANAGE_EXTERNAL_STORAGE` там не проверяется. Поэтому даже с выданным All-Files-Access путь сваливается в MediaStore-consent (`BatchDeletePermissionRequiredException`), который требует UI-подтверждения и в headless-воркере недостижим.

**B. Цикл не размыкается на неустранимой ошибке.** `BatchDeletePermissionRequiredException` в фоне обрабатывается как рядовая per-file ошибка: операция продолжает перебор, копит `errors`, перепланируется на следующий час и заново перезаливает уже выгруженные файлы. Нет ни остановки операции, ни нотификации «нужно разрешение», ни пропуска уже залитого.

## 1. Problem

Фоновая запланированная MOVE-операция с локальным источником и сетевым приёмником никогда не завершается: удаление оригинала в фоне невозможно (A), а воркер бесконечно перезаливает те же файлы вместо того, чтобы остановиться и попросить разрешение (B). Это впустую расходует сеть/батарею и держит dataSync-FGS под системным таймаутом (см. S0709).

## 2. Direction (rough)

- **A.** В `deleteViaMediaStore` (и связанном пути удаления) добавить ветку `Environment.isExternalStorageManager()` -> прямой `File.delete()`, по аналогии с уже существующей `MANAGE_MEDIA`-веткой. Тогда при выданном All-Files-Access фоновый MOVE завершается корректно. Заодно ускоряет и foreground-удаление (без диалога consent).
- **B.** При `BatchDeletePermissionRequiredException` в фоновом воркере не трактовать как per-file error: останавливать операцию, гасить нотификацией «требуется разрешение», не перезаливать уже выгруженные файлы (idempotent skip по наличию на приёмнике или по статусу).

Точную форму сигнала о требуемом пермишене и idempotent-skip определить в /spec-tech.

**Non-goals:** сам крэш FGS (инфраструктурный bump WorkManager) - это S0709.

## 3. Implementation

**A - delete honours All-Files-Access** (`LocalOperationStrategy.kt`)

- `deleteViaMediaStore` now routes through `canDeleteDirectly()` -> `directDeleteAndUnindex()` when the app holds MANAGE_MEDIA (API 31+) **or** All-Files-Access (`Environment.isExternalStorageManager()`, API 30+). Both bypass the unreachable MediaStore consent dialog.
- The MANAGE_MEDIA-only branch was folded into the shared `canDeleteDirectly()`/`directDeleteAndUnindex()` helpers (no behaviour change for MANAGE_MEDIA).
- `moveFile` no longer throws `TrashRenameUnavailableException` when All-Files-Access is granted (copy+delete fallback can remove the original).

**B - background loop breaks on unrecoverable permission wall** (`ExecuteScheduledOperationUseCase.kt`, `ScheduledOperationsWorker.kt`)

- A `PermissionRequired` / `AuthenticationRequired` per-file result is no longer swallowed by the generic `else` branch. It sets `permissionStop`, halts the batch (remaining files skipped via `if (permissionStop) return@forEach`), and records a clear cause via `permissionStopError()`.
- `ScheduledExecutionResult` carries `permissionRequired`; the worker posts a one-shot advisory notification (`PERMISSION_NOTIFICATION_ID`) whose tap opens the All-Files-Access settings page. This stops the perpetual re-upload of files the worker can never delete in the background.

New strings (EN/RU/UK): `scheduled_ops_notif_permission_title`, `scheduled_ops_notif_permission_text`.

**Device test focus:** background scheduled MOVE local -> SMB with All-Files-Access granted must now complete (originals deleted, no hourly re-upload); with no direct-delete permission it must halt after the first file and raise the advisory notification.

## Related

- S0709 - крэш `ForegroundServiceDidNotStopInTimeException`, который этот цикл провоцирует.

## Last Audit

Manual PARTIAL (device RFCR110NBQJ, 2026-06-27, defect A only): PASS - expected: local MOVE with All-Files-Access direct-deletes original, no BatchDeletePermissionRequiredException | actual: with MANAGE_MEDIA=deny + MANAGE_EXTERNAL_STORAGE(All-Files-Access)=allow, a foreground local->local MOVE that triggered the copy+delete fallback routed through `LocalOperationStrategy.deleteViaMediaStore` -> `canDeleteDirectly()` -> `directDeleteAndUnindex()`; logcat shows `S0710: direct delete permitted (MANAGE_MEDIA or All-Files-Access)` and `SUCCESS via copy+delete`; original removed from source, file present at destination; `BatchDeletePermissionRequired` count = 0 over the whole 137k-line capture. Defect B (scheduled MOVE local->SMB + advisory notification) NOT tested - needs owner NAS.

- To reach the fixed branch in the foreground, the same-volume rename fast-path had to be bypassed: enabled Settings -> Management -> "Overwrite existing file when moving" and pre-placed a same-named file at the destination, so `LocalMoveFileOperation` fell into copy+delete (overwrite=true). A plain same-volume move uses `File.renameTo()` and never calls `deleteViaMediaStore`, so it does not exercise this fix. Note: the foreground per-item DELETE uses a different class (`LocalDeleteFileOperation`) which does NOT consult `canDeleteDirectly()` and still unconditionally creates a batch-delete request - out of scope for defect A.
- Evidence: `temp/S0710_devtest/` (logcat_excerpt_defectA_pass.txt, screenshot_after_move.png, logcat_full.txt).
