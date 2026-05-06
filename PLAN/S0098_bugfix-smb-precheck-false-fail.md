# S0098 — bugfix-smb-precheck-false-fail

**Статус:** Approved → Tactical
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
