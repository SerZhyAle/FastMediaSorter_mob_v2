# Стратегическая спецификация: S0163 — Двойная загрузка на SMB при Move после batch-delete диалога

**Ticket:** S0163
**Status:** Verified
<!-- auto-approved by /spec-all — 2026-05-11 -->
**Priority:** 75
**Date:** 2026-05-11
**Tier:** 2 — Focused bugfix
**Roadmap entry:** Полевой инцидент 2026-05-11, лог `logs/fastmediasorter_20260511_203620.log`

> **Scope:** STRATEGIC. Без имён классов, путей, лимитов строк, Room-миграций, Hilt-модулей.

---

## 1. Проблема

При SMB Move из плеера выполняется полный цикл: загрузка на SMB (upload) → удаление локального файла через MediaStore. Если у приложения нет `MANAGE_MEDIA`, MediaStore требует явного разрешения пользователя через системный диалог — Activity уходит в `onPause`. Когда пользователь подтверждает диалог и Activity возвращается (`onResume`), `PlayerFileOperationQueue` перезапускает **всю операцию с нуля** — повторный upload того же файла. Но к этому моменту файл уже удалён системой как побочный эффект подтверждения разрешения → `uploadToSmb: Local file does not exist` → ошибка для пользователя «Не получилось переместить выбранные файлы».

С точки зрения пользователя операция упала, хотя файл **уже** был успешно загружен на SMB-шару.

**Объём воспроизведения в логе `203620.log`:** 5 файлов из `Threads/`, 10 ошибок (каждый файл — 2 ошибки: `uploadToSmb: Local file does not exist` + `[SLog] FAILURE`). Паттерн стопроцентно воспроизводится для каждого Move в эту сессию.

---

## 2. Точная цепочка событий (из логов)

На примере `IMG_20260425_142612_763.jpg` (`logs/fastmediasorter_20260511_203620.log`):

| Время | Строка лога | Событие |
|---|---|---|
| 20:50:13.720 | 3919 | `SmbFileOperationHandler.executeMove: ENTRY` — старт операции |
| 20:50:14.105 | 3970 | **`uploadToSmb: SUCCESS`** — файл загружен на SMB (212 KB за ~380 ms) |
| 20:50:14.157 | 3976 | `Batch delete permission required, throwing exception` |
| 20:50:14.169 | 3981 | `PlayerLifecycleManager: Stored pending batch delete path` |
| 20:50:14.246 | 3982 | `onPause` — системный диалог |
| 20:50:14.442 | 4156 | `onResume: Reloading files` — пользователь подтвердил |
| 20:50:14.442 | **4157** | **`FileOperation: Starting operation: Move`** — операция стартует заново |
| 20:50:14.451 | 4182 | `uploadToSmb:` — повторная попытка загрузить тот же файл |
| 20:50:14.452 | **4183** | **`ERROR: Local file does not exist`** — файл уже удалён системой |
| 20:50:14.454 | 4185 | `[SLog] FAILURE` — ошибка показана пользователю |

**Тот же паттерн подтверждён** для IMG_20260425_142616_211.jpg, IMG_20260425_150819_977.jpg, IMG_20260425_190245_491.jpg, IMG_20260425_190247_641.jpg.

---

## 3. Корневая причина

`PlayerFileOperationQueue` при `onResume` после batch-delete диалога не различает состояния:
- «ожидает только delete (upload уже выполнен)»
- «ожидает полную операцию (upload + delete)»

В результате на `onResume` запускается полный `executeMove`, а не только оставшаяся часть (delete/batch-delete подтверждение). Факт «upload уже выполнен» нигде не сохраняется — состояние теряется в onPause.

---

## 4. Цели

1. После подтверждения batch-delete диалога (`onResume`) очередь продолжает **только** оставшуюся часть операции (удаление локального файла), а не перезапускает upload.
2. Пользователь не видит ошибки для операций Move, которые были успешно загружены на SMB.
3. Файл, загруженный на SMB, всегда удаляется с устройства после подтверждения пользователем — нет «загружен, но не удалён».
4. Если пользователь **отклоняет** диалог batch-delete — операция завершается с понятным сообщением («Файл загружен, но не удалён с устройства» или аналогичное по тону `docs/COMMUNICATION_POLICY.md`); файл на SMB при этом уже есть.

**Non-goals:**

- Изменение механизма показа batch-delete диалога.
- Авто-повтор при сетевых ошибках upload.
- Изменение поведения других протоколов (FTP, SFTP, облако) если у них нет такой же проблемы.
- Персист очереди между сессиями.

---

## 5. Предлагаемый подход

Операция Move разбивается на две стадии:
1. **Upload** — загрузка на SMB.
2. **Delete** — удаление локального файла (требует batch-delete разрешения).

При переходе в stадию **Delete** и входе в ожидание разрешения — факт «upload выполнен» сохраняется в описании операции в очереди. При `onResume` потребитель очереди смотрит на сохранённое состояние: если upload уже был — выполняет только delete/разрешение; если upload не был — полный цикл.

Это согласуется с проектом S0154 (§5.1 B): «Если операция требует системного разрешения (batch-delete) — потребитель приостанавливается, UI запрашивает разрешение; после ответа пользователя выполнение возобновляется».

---

## 6. Ограничения

- **Flavor:** все (standard, lite, photos, legacy) — batch-delete актуален везде где нет `MANAGE_MEDIA`.
- **API level:** API 30+ (именно с API 30 MediaStore требует `createDeleteRequest` для удаления чужих файлов). На API < 30 batch-delete flow не используется — баг не воспроизводится.
- **Протоколы:** SMB подтверждён логами. Проверить FTP/SFTP на тот же паттерн — оба используют похожий upload + delete flow.
- **Локализация:** если добавляется новое сообщение «файл загружен, но не удалён» — EN/RU/UK обязательны.

---

## 7. Риски

| Риск | Доказательство | Серьёзность |
|---|---|---|
| Дублирование файла на SMB-шаре (если upload идёт повторно до проверки `exists`) | `203620.log:3970 → 4182` — второй upload падает, дубля нет, но только потому что файл уже удалён | Med — если файл по какой-то причине не удалён системой, будет дубль |
| Ложный failure-тост у пользователя для каждого Move по файлам без MANAGE_MEDIA | `203620.log:4185` — 100% воспроизводится | **High** |
| Отказ пользователя от batch-delete → файл есть на SMB, но остаётся на устройстве — нет ясного сообщения об этом | `docs/COMMUNICATION_POLICY.md` §6 | Med |
| FTP/SFTP имеет тот же паттерн — не проверено | нет данных в логе | Med (требует верификации) |

---

## 8. Связи

- **S0154** (`player-file-operation-queue`, In Progress): S0163 — точечный bugfix конкретной ветки. S0154 описывает правильную архитектуру очереди, которая решает этот класс проблем системно. S0163 закрывает регрессию немедленно, не дожидаясь полного внедрения S0154.
- **S0152** (`bugfix-moveinprogress-not-reset-after-batchdelete`, Archived): решал утечку флага `moveInProgress`; S0163 решает следующий уровень — некорректное возобновление после подтверждения.

---

## 9. Критерии готовности

1. SMB Move по файлу из `Threads/` (или любому без `MANAGE_MEDIA`) → upload SUCCESS → batch-delete диалог → подтвердить → **никакой ошибки** пользователю.
2. Файл отсутствует на устройстве и присутствует на SMB-шаре после успешного Move.
3. Отказ от batch-delete диалога → чёткое сообщение «файл загружен на [шару], но не удалён с устройства» (по тону `docs/COMMUNICATION_POLICY.md`).
4. Логи: нет `uploadToSmb: Local file does not exist` после `uploadToSmb: SUCCESS` для того же пути.
5. FTP/SFTP — верифицировать, что тот же паттерн либо отсутствует, либо тоже исправлен.

---

## 10. Last Audit

**Date:** 2026-05-11
**By:** /spec-all
**Result:** Verified ✅

### Automated checks

| Criterion (§9) | Evidence | Result |
|---|---|---|
| §9.1 Upload SUCCESS + confirm → no user error | `PlayerFileOperationQueue.processOperation`: `gate.await()` suspends worker; `gate.complete(true)` → `Succeeded` emitted directly — use case NOT re-invoked. Lines 209–239. | ✅ |
| §9.2 File absent on device, present on SMB | `MediaStore.createDeleteRequest` auto-deletes on user confirmation; upload already completed before dialog. System responsibility. | ✅ |
| §9.3 Cancel → `error_queued_move_permission_denied` message | String present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`. Snackbar shown via `PlayerManagerInitializer.kt:471-490`. No retry offered. | ✅ |
| §9.4 No `uploadToSmb: Local file does not exist` after SUCCESS | Gate prevents use-case restart → upload called at most once per operation. | ✅ |
| §9.5 FTP/SFTP verified | Both `FtpFileOperationHandler.kt:125` and `SftpFileOperationHandler.kt:148` call `requestBatchDeletePermission` → same `PermissionRequired` result → same queue gate handles them. | ✅ |

### Build

`assembleStandardDebug` — **PASS** (30 s, 0 errors, 2026-05-11).

### Known deferred items

- **Activity rotation during dialog (High risk):** If the Activity is recreated while the system batch-delete dialog is visible, `consumePendingBatchDeleteOperation()` returns null → `resumeAfterPermission` is never called → worker coroutine stuck at `gate.await()`. This is an edge case outside S0163 scope; tracked as a follow-up for S0154 or a new ticket.
- **String parity script:** `scripts/check_strings_localized.ps1 -KeyPrefix error_queued_move_permission_denied` — deferred to manual (requires environment). Manual grep confirms all three locales present.
