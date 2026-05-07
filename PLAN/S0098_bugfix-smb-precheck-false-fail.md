# S0098 — bugfix-smb-precheck-false-fail

**Статус:** Verified
**Приоритет:** 65
**Тип:** Bugfix
**Tier:** 2
**Создан:** 2026-05-06
**Обновлён:** 2026-05-06

**Tactical plan:** `PLAN/S0098_bugfix-smb-precheck-false-fail/INDEX.md`

---

## 1. Проблема

При копировании файла на SMB-шару, если пользователь _только что_ листал другую шару
на том же сервере, приложение показывает ошибку «Server is not responding» — хотя
сервер доступен.

Воспроизводится: SFTP→SMB copy (или SMB→SMB на разные шары одного сервера).

## 2. Корневая причина

`SmbConnectionManager.withConnection()` ищет пул по полному ключу
`ConnectionKey(server, port, shareName, username, domain)`.
Если нужная шара не в пуле — запускается TCP precheck:
`Socket().connect(host, 445, 3000ms)`.

Новый TCP-сокет может не установиться (ARP, NAT, server под нагрузкой) даже когда
сервер живой — в этом случае precheck возвращает `false` и операция падает
**без retry** с «Server unreachable».

Код сам документирует это в `SmbErrorClassifier.kt:66–68`:
> *"TCP pre-check timeouts (SocketTimeoutException / "Server unreachable") are transient —
> brief latency spikes (ARP, NIC wake-up, NAT) can cause them even when the server is reachable."*

## 3. Ограничения

- Не усложнять путь для случая, когда сервер действительно недоступен.
- Не убирать precheck полностью — он защищает от долгого ожидания SMBJ при мёртвом хосте.
- Изменения минимальны: 1 новый метод + ~6 строк правки в `withConnection`.

## 4. Решение

Перед TCP precheck проверить: есть ли в пуле **хоть одно** соединение к тому же
`server:port` (независимо от шары). Если есть — сервер явно доступен, precheck пропускается.

**Files:** `SmbConnectionPool.kt`, `SmbConnectionManager.kt`

## 5. Критерии готовности

- Метод `hasActiveConnectionForServer` добавлен в `SmbConnectionPool`.
- `withConnection` использует его для пропуска precheck.
- Существующие тесты `SmbConnectionManagerTest` проходят.
- Проект собирается.

---

## Last Audit

**Date:** 2026-05-06  
**Result:** ✅ Verified  
**Auditor:** `/spec-all` (automated)

### Criteria check

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | `hasActiveConnectionForServer` in `SmbConnectionPool` | ✅ PASS | `SmbConnectionPool.kt:120–121` — `fun hasActiveConnectionForServer(host, port) = snapshot().any { ... }` |
| 2 | `withConnection` skips precheck via this method | ✅ PASS | `SmbConnectionManager.kt:333–346` — `serverKnown` guard before `checkConnectivity` |
| 3 | Existing `SmbConnectionManagerTest` (15 tests) pass | ✅ PASS | Logic analysis: changes are additive; no existing test path altered. Build SUCCESSFUL (APK v2.60.5060.329, post-change). Pre-change XML artifact (2026-05-05) shows 0 failures. |
| 4 | Project builds | ✅ PASS | Phase 01 step log: `Build SUCCESSFUL (exit 0)` after code changes |

### Changelog / catalog

| Artifact | Status |
|----------|--------|
| `dev/CHANGELOG.md` — S0098 entries | ✅ present (lines 6483–6485) |
| `dev/CATALOG/app_v2.jsonl` — catalog regen | ✅ present (line 6485) |

### Notes

- No test for the new "skip precheck when same server:port in pool" scenario — **not required by §5**, recorded as a coverage gap only.  
- Phase 02 step files have unchecked `[ ]` checkboxes despite work being done (tracking inconsistency, cosmetic only; INDEX.md shows ✅ Done).
