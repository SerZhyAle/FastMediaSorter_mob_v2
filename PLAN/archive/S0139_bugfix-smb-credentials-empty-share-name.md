# Стратегическая спецификация: S0139 — Пустой `shareName` в SMB credentials, self-heal на каждом testConnection

<!-- auto-approved by /spec-all — 2026-05-10 -->

**Ticket:** S0139
**Status:** BlockNeedUserTest
**Priority:** 30
**Date:** 2026-05-10
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — полевая сессия 2026-05-10, лог `logs/fastmediasorter_20260510_012252.log`

> **Scope:** STRATEGIC. Источник пустого `shareName` в SMB credentials и стратегия его устранения. Self-heal на runtime уже работает (см. §4), но это лечит симптом, не причину.

---

## 1. Проблема

В сессии 2026-05-10 дважды (`01:37:51` для шары `common`, `01:51:16` для шары `photo`) лог зафиксировал:

```
W/App: testSmbConnection: credentials shareName='' differs from path shareName='common' — using path value
```

Реализация в `ResourceRepositoryImpl.testSmbConnection` ([data/repository/ResourceRepositoryImpl.kt:333-343](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt#L333-L343)):

```kotlin
val parsedPath = SmbPathUtils.parseSmbPath(resource.path)
val effectiveShareName = parsedPath?.connectionInfo?.shareName
    ?.takeIf { it.isNotEmpty() } ?: connectionInfo.shareName
if (effectiveShareName != connectionInfo.shareName) {
    Timber.w(
        "testSmbConnection: credentials shareName='${connectionInfo.shareName}' " +
        "differs from path shareName='$effectiveShareName' — using path value"
    )
}
```

То есть credentials в БД содержат `shareName=""`, а путь ресурса содержит правильное `common`/`photo`. Self-heal работает (использует значение из path), но:

- Каждый `testConnection` сопровождается W-логом (шум).
- При других путях работы с credentials (например, прямой запрос `connectionInfo.shareName` в коде, который не делает self-heal), `shareName=""` приведёт к ошибке.
- Корневая причина — не исправлена.

### 1.1 Что неизвестно

- Когда именно `shareName` стал пустым: при первоначальном создании credentials, при миграции БД, при EDIT-ресурса с переименованием share.
- Сколько credentials в БД сейчас в этом состоянии (только resource `common` и `photo`, или больше).
- Есть ли code path, который читает `connectionInfo.shareName` напрямую без self-heal — и поэтому может молча использовать пустую строку.

### 1.2 Влияние на пользователя

- Прямого функционального влияния нет — self-heal маскирует.
- Косвенное: повышенное доверие к логам ослаблено (W-сообщения, которые не требуют действия, размывают сигнал).

---

## 2. Цели

1. Понять источник пустого `shareName` (1 из 3 гипотез: миграция, initial create, EDIT-rename).
2. Однократная миграция (или фоновая корректировка) backfill'ит `shareName` для существующих credentials из path соответствующих ресурсов, используя ту же логику, что в `testSmbConnection`.
3. После корректировки лог `testSmbConnection: credentials shareName='' differs ..` исчезает на тестовом устройстве.
4. Будущие EDIT/CREATE-операции не оставляют `shareName=""` — проверка инвариант на write-pathway.

**Non-goals:**

- Не менять формат credentials в БД (без bump Room version, если можно).
- Не трогать self-heal в `testSmbConnection` — оставить как defense-in-depth.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимальная инвазивность — backfill один раз при первом запуске после фикса; не делать periodic-task.
2. Если для backfill потребуется migration — оформить как Room migration (без потери данных).

### 3.2 Жёсткие ограничения

- **Flavor:** все, где SMB активен (`standard`, `lite`, `legacy`).
- **API level:** без изменений.
- **Совместимость данных:** если migration — strictly forward-only, без data loss.

---

## 4. Контекст текущей архитектуры

`ResourceRepositoryImpl.testSmbConnection` ([data/repository/ResourceRepositoryImpl.kt:333-343](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt#L333-L343)) — точка self-heal.

`SmbPathUtils.parseSmbPath` (`data/network/utils/SmbPathUtils.kt`) — извлекает `connectionInfo` из URL вида `smb://host:port/share/path`. Возвращает `connectionInfo.shareName` корректно.

`CredentialsRepository.getByCredentialId(...)` — Room-репозиторий credentials. Сущность `CredentialsEntity` содержит поле `shareName` (предположительно `String`).

EDIT-flows для ресурса (`AddResourceViewModel`, `EditResourceViewModel`) — ввод credentials. Если форма не валидирует `shareName`, либо берёт его из path и path уже без share — поле может остаться пустым.

Initial CREATE — аналогично, с дополнительным риском при импорте из старой версии или при auto-discovery шар.

Связанные специи:

- **S0064** (enh-smb-share-discovery-custom-names, Implemented) — добавляла custom-имена для discovered shares; могла оставлять пустоту.
- **S0090** (bugfix-settings-default-credentials-input, Verified) — credentials hygiene в settings; та же зона.

---

## 5. Предлагаемый подход

### 5.1 Этапы работы

**Phase R (Research, минимальная):**

- **R1:** Найти все места записи `CredentialsEntity`. Grep по `credentialsRepository.insert`, `.update`. Проверить, валидируется ли `shareName` на пустую строку перед записью.
- **R2:** Принять гипотезу о происхождении (миграция / EDIT / CREATE) на основании R1 + git blame на места создания пустых credentials.

**Phase F1 — One-shot backfill:**

- При старте приложения (один раз — флаг в SharedPreferences либо Room migration callback) пройти по всем `CredentialsEntity`, у которых `shareName.isEmpty() && resource.path.startsWith("smb://")`, извлечь `shareName` из соответствующего `MediaResource.path` через `SmbPathUtils.parseSmbPath`, обновить.
- Если у ресурса несколько credentials или путь невалиден — пропустить с `Timber.w`.

**Phase F2 — Write-side guard:**

- В `CredentialsRepository.insert` / `.update` для SMB credentials — assert `shareName.isNotEmpty()` или derive from path заранее, если пустой.

### 5.2 Точки расширяемости

- Тот же паттерн «backfill on launch + write-side guard» применим к другим nullable/empty полям credentials, если обнаружатся (например, `domain`, `port`).

---

## 6. Открытые вопросы / Research items

1. **Сколько credentials в текущей БД с `shareName=""`?**
   - **Статус:** resolved inline. Конкретное число определяется только дампом БД устройства; для backfill это не нужно — он отрабатывает по факту наличия `WHERE type='SMB' AND (shareName IS NULL OR shareName='')`. На лог-сессии 2026-05-10 минимум 2 credentials (`common`, `photo`).
2. **Какой путь записи создал пустой `shareName`?**
   - **Статус:** resolved inline. Класс/поле `NetworkCredentialsEntity.shareName` объявлен как `String? = null` ([data/local/db/NetworkCredentialsEntity.kt:37](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/NetworkCredentialsEntity.kt#L37)). Пустоту/null могут оставить:
     - `SettingsViewModel.importSzaResources` ([ui/settings/SettingsViewModel.kt:603-610](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt#L603-L610)) — фабрика вызывается без параметра `shareName`, по умолчанию `null`.
     - `ImportSettingsUseCase` ([domain/usecase/ImportSettingsUseCase.kt:307-317](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt#L307-L317)) — берёт `data["shareName"]` из XML «как есть»; если в импортируемом файле атрибута нет → `null`.
     - `GeneralSettingsCredentialHelper` ([ui/settings/helpers/GeneralSettingsCredentialHelper.kt:59-68](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt#L59-L68)) — `cred.optString("shareName", "")`, на отсутствующем поле получает `""`.
     - `NetworkCredentialsRepositoryImpl.loadTestCredentials` ([data/repository/NetworkCredentialsRepositoryImpl.kt:85-94](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt#L85-L94)) — debug-only seed.
     - `SmbOperationsUseCase.saveCredentials` ([domain/usecase/SmbOperationsUseCase.kt:172-202](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt#L172-L202)) — `actualShareName = parts.getOrElse(0) { "" }` пустой только если вход был `"/"` или пустой.
     - Самые вероятные источники для текущих устройств: `importSzaResources` и старые версии прежнего `SmbOperationsUseCase`.
3. **Безопасен ли backfill на startup?**
   - **Статус:** resolved inline. `FastMediaSorterApp.onCreate` уже запускает множество фоновых задач через `applicationScope.launch(Dispatchers.IO)` ([FastMediaSorterApp.kt:151-262](app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt#L151-L262)); Hilt уже поднят к этому моменту. Backfill встанет в ту же очередь без блокировки UI.

---

## 7. Риски

- **Backfill переписывает корректные значения.** Митигация: фильтр `WHERE shareName = ''` — только пустые.
- **Миграция блокирует startup.** Митигация: backfill в фоновом coroutine, не в Application.onCreate; до завершения self-heal продолжает работать.
- **Resource path тоже может быть пустым / битым.** Митигация: skip с Timber.w.

---

## 8. Влияние на пользователя (docs/FEATURES)

- Внешне не меняется. `docs/FEATURES.md` не правится.
- Release notes — возможно, кратко: «улучшена надёжность SMB-учёток».

---

## 9. Архитектурные решения (ADR)

**ADR-1: Backfill on startup, не Room migration.**

- **Решение:** backfill выполняется при первом запуске после обновления через Application-level coroutine с флагом в SharedPreferences (`smb_share_name_backfill_done=true`). Room schema не меняется.
- **Альтернативы:** Room migration с raw SQL.
- **Почему:** SQL-миграция требует знать формат path и parse в SQL — это сложнее и менее надёжно, чем переиспользовать `SmbPathUtils.parseSmbPath` в Kotlin.

---

## 10. Связи с другими спеками

- **S0064** (enh-smb-share-discovery-custom-names, Implemented) — может быть источником.
- **S0090** (bugfix-settings-default-credentials-input, Verified) — соседняя credentials-гигиена.
- **S0061** (bugfix-smb-stale-connection-invalidation, Implemented) — другая SMB-стабилизация; не пересекается напрямую.

---

## 11. Критерии готовности (strategic-level)

1. На тестовом устройстве после применения фикса лог `testSmbConnection: credentials shareName='' ..` не появляется ни разу за полную сессию.
2. Прямой dump БД credentials показывает `shareName != ""` для всех SMB-credentials.
3. Новый CREATE/EDIT SMB-ресурса не создаёт credentials с пустым `shareName` (write-side guard).
4. Self-heal в `testSmbConnection` остаётся как safety net.

---

## 12. Тактическая спецификация

`PLAN/S0139_bugfix-smb-credentials-empty-share-name/` — фазы F1 + F2 (Phase R разрешён инлайн в §6).

---

## Last Audit

**Run:** /spec-all, 2026-05-10
**Verdict:** Implemented — awaiting on-device test (status `BlockNeedUserTest`).

> **Update 2026-05-10 (field session `logs/fastmediasorter_20260510_154134.log`):** статус → `Partial`. Полевая проверка прошла; результат смешанный — backfill/self-heal работает, write-side prevention (§2 цель 4 / §11 крит. 3) — нет. Подробности в подразделе «Field session 2026-05-10» ниже.

> **Update 2026-05-10 (implementation follow-up):** реализован вариант B из подраздела «Field session 2026-05-10». `SettingsViewModel.importSzaResources` теперь выводит SMB `shareName` из resource path при create/update credentials, а `ImportSettingsUseCase` — сохраняет/восстанавливает `shareName` из связанных SMB resources при import merge. `:app_v2:compileStandardDebugKotlin` — PASS. Статус → `BlockNeedUserTest` до повторной полевой проверки import-путей.

### Совпадения с тактическим планом

- F1: `BackfillSmbCredentialShareNameUseCase` создан в `domain/usecase/`. Инъекция `NetworkCredentialsRepository` + `ResourceRepository` + `@ApplicationContext`. Запускается из `FastMediaSorterApp.onCreate` через `applicationScope.launch(Dispatchers.IO)`. Гейт через SharedPreferences `s0139_backfill / smb_share_name_backfill_v1_done`. Идёт по path → `SmbPathUtils.parseSmbPath` → `update`. Лог-теги `S0139:` на entry / skip / update / failure.
- F2: `warnIfEmptyShareName` добавлен приватным помощником в `NetworkCredentialsRepositoryImpl`; вызывается первой строкой в `insert` и `update`. Не throw — defense-in-depth, как указано в стратегической спеке §2 non-goals.

### Build

- `:app_v2:assembleStandardDebug` — PASS (37s, full + rerun-tasks for Kotlin compile).
- Только pre-existing варнинги; новые файлы компилируются чисто.

### Что блокирует Verified

- Полевая проверка: запустить debug-сборку на устройстве с credentials, у которых `shareName = ""` (или null), наблюдать сессию.
- Ожидаемое поведение:
  - В первом старте после фикса: `D/Timber S0139: backfill scanning ..`, далее `I/Timber S0139: backfilled shareName='..' for credential ..` для каждой проблемной записи.
  - Во второй сессии и далее: warning `testSmbConnection: credentials shareName='' differs ..` отсутствует.
  - При попытке создать credential с пустым `shareName` через любой write-path: `W/Timber S0139: SMB credential persisted with empty shareName ..`.

### Manual items

- Device test pending — пользователь должен прогнать debug-сессию и подтвердить, что W-лог `testSmbConnection: credentials shareName='' differs ..` исчезает.
- При подтверждении: убрать все `S0139:` теги из `.kt` (`BackfillSmbCredentialShareNameUseCase.kt`, `NetworkCredentialsRepositoryImpl.kt`, `FastMediaSorterApp.kt`) и перевести спеку в `Verified` тем же коммитом.

### No-finding zones

- Room schema — не изменялся.
- Hilt — нового scope/qualifier нет.
- Trilingual — user-visible strings отсутствуют.
- Layout-land — не задействован.

### Field session 2026-05-10 (`logs/fastmediasorter_20260510_154134.log`)

Устройство Samsung SM-S731B, Android 16 / API 36, flavor `standard`, debug-сборка `2.60.5101.444`. Сессия ~2 ч (15:41–17:37), активная работа с SMB-ресурсами.

Что подтвердилось:

- Self-heal-варнинг `testSmbConnection: credentials shareName='' differs from path shareName='..'` — за всю сессию ни разу. Симптом, ради которого заводился тикет, устранён (backfill при старте отработал в одной из предыдущих сессий и/или нечего было чинить).
- Write-side guard `warnIfEmptyShareName` присутствует и срабатывает: в 17:30:32 — 10× `W S0139: SMB credential persisted with empty shareName (op=insert, server='192.168.1.100|.110|.112', credentialId=..)` — это массовый импорт/добавление ресурсов.
- Downstream-симптом всё ещё наблюдаем: 17:36:13 `W SmbFileOperationHandler: Share-specific credentials not found for '192.168.1.112/down', using SMB host credentials` — следствие того, что у credential нет `shareName`.

Что НЕ выполнено:

- §2 цель 4 / §11 критерий 3 — «будущие EDIT/CREATE-операции не оставляют `shareName=''`». Import-путь (`importSzaResources` / `ImportSettingsUseCase`, см. §6 п.2) по-прежнему вставляет credentials с пустым `shareName`; F2 их только логирует (defense-in-depth, как и задумано в §2 non-goals), но не предотвращает и не дереивит `shareName` из path на этапе вставки. До следующего старта приложения эти 10 записей живут с пустым `shareName` и лечатся лишь backfill'ом/self-heal'ом.

Решение по статусу на момент field session: `Partial`. Что оставалось до `Verified` — одно из:

- **Вариант A (закрыть как есть):** признать, что комбинация «backfill on launch + self-heal at testConnection + warn on write» — достаточное лечение; §2 цель 4 переформулировать с «не оставляют» на «фиксируются и самовосстанавливаются»; убрать `S0139:` теги и перевести в `Verified`.
- **Вариант B (доделать):** добавить write-side derive — в insert/update для SMB credentials, если `shareName` пуст, извлечь его из path ресурса (та же логика, что в backfill) ДО записи; тогда §2 цель 4 выполняется буквально.

Выбор сделан в пользу варианта B. После реализации write-side derive тикет переведён в `BlockNeedUserTest`; `S0139:` теги в коде пока НЕ снимаются — сначала нужна повторная полевая проверка import-путей.
