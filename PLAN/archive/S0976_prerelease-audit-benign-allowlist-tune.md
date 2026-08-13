# Стратегическая спецификация: S0976 - Донастроить benign-аллоулист prerelease log-audit

**Ticket:** S0976
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-07
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-07 (найдено при /spec-prerelease sweep)
**Tactical spec:** `PLAN/S0976_prerelease-audit-benign-allowlist-tune/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-07

**Захвачено во время:** /spec-prerelease sweep (device emulator-5554, API 37)

**Текст:**

`scripts/devtest/prerelease-log-audit.ps1` в прогоне 2026-07-07 классифицировал 110 кластеров как "actionable" и лишь 1 как benign (exit 1), хотя ручной триаж показал 0 дефектов приложения: все 110 - системный/эмуляторный/GMS/Maestro-шум. Так как Step 4.1 обязателен и по контракту "каждый actionable-кластер = finding", 110 ложных actionable делают сигнал аудита бесполезным (реальный дефект утонет среди шума). Нужно расширить benign-аллоулист аудита рекуррентными эмулятор/система/harness-тегами, чтобы actionable-список нёс только настоящие app-уровневые ошибки.

**Кандидаты в benign-аллоулист (из прогона, все не-app):**

- GMS/Play: `Finsky`, `GoogleApiManager`, `RoleControllerServiceImpl`, `MDDMetricsProcessor`, `DocsApplication`, `DefaultHttpIssuer`, `Dck`.
- Maestro-харнесс: `HCPackageInfoUtils` (dev.mobile.maestro), `PackageManager` (alignment dev.mobile.maestro), `.mobile.maestro` (epoll/grpc native).
- Эмулятор graphics/codec: `MESA`, `GFXSTREAM`, `Codec2-AIDL-BufferTypes`, `android.hardware.media.c2-service-goldfish`, `ConsumerBase` (abandonLocked), `mediaserver`, `LegacyGraphicsTracker`, `SurfaceSyncGroup`.
- Система/WM: `TransitionChain`, `WindowOrganizerController` (non-organized container), `libbinder.IPCThreadState`, `SystemServiceRegistry` (persistent_data_block), `FeatureFlagsImplExport` (android.xr), `NsdService`, `AppOps` (attributionTag), `PermissionService` (WRITE_EXTERNAL_STORAGE isn't requested - ожидаемо на scoped storage), `AtomicFile`, `ShortcutService`, `JobScheduler.JobStatus`, `libprocessgroup`, `Nl80211Native`, `ImeLatencyLogger`, `RemoteFillService`, `ClipboardService`, `NwpModelManager`.

**Осторожно:** несколько тегов, называющих `com.sza.fastmediasorter.debug` (WindowOrganizerController task-reorg, AppOps attributionTag, PermissionService WRITE_EXTERNAL_STORAGE, "Failed to query component interface for required system resources: 6"), - benign; аллоулист должен матчить по паре тег+сигнатура-сообщения, а не только по пакету, чтобы не проглотить будущую настоящую app-ошибку под тем же тегом.

---

## 1. Проблема

benign-аллоулист `scripts/devtest/prerelease-log-audit.ps1` ловил только Cast/Dynamite/WifiRequired/GPU-семейства, из-за чего рекуррентный эмулятор/система/GMS/Maestro-шум (110 кластеров в прогоне 2026-07-07) попадал в actionable и топил реальный сигнал (по контракту каждый actionable = /spec-draft-кандидат). Нужно расширить классификацию так, чтобы actionable нёс только настоящие app-уровневые ошибки, но без риска проглотить будущую app-ошибку под тем же тегом.

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- Не глушить настоящие app-уровневые ошибки; benign-матч только по устойчивой паре тег+сигнатура, не по всему E-level или по всему пакету.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** н/д (devtest-тулинг)
- **API level:** аллоулист не должен зависеть от конкретного API эмулятора
- **Wear OS:** не затрагивается
- **Производительность:** н/д
- **Совместимость данных:** н/д
- **Локализация:** н/д (внутренний скрипт)
- **Доступность:** н/д

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0484 (prerelease sweep - владелец аудита)
- **Sensitive-scope:** нет (внутренний devtest-скрипт, не затрагивает app-рантайм, flavor, данные, API).

---

## 4. Контекст текущей архитектуры

`prerelease-log-audit.ps1` парсит `-v time`/`-v threadtime` лог, сворачивает stack-frame в кластер, дропает системные теги через `$systemTagHint` (полностью, `continue`), кластеризует E/(W) по тег+нормализованная голова сообщения, помечает benign через плоский `$benignPatterns` (матч по подстроке `tag+msg`). Actionable = не-benign кластеры; каждый - /spec-draft-кандидат. `-v time` не несёт колонку пакета, поэтому фильтр по PID нашего процесса не универсален (Q2 §6).

---

## 5. Предлагаемый подход

Две новых сущности, зеркалящих существующие механизмы, без изменения app-рантайма:

- `$foreignTagPatterns` (drop-entirely, sibling `$systemTagHint`): не-anchored substring-матч по тегу для тегов, которые заведомо не наш процесс (GMS/Play, Maestro-харнесс, эмулятор graphics/codec, системные/WM-сервисы). Короткие/родовые теги (`Dck`, `MESA`, `mediaserver`, `JobStatus`) огорожены `\b`. Дроп безопасен - тег никогда не app.
- `$benignTagSignaturePairs` + `Test-BenignPair(tag,msg)`: для тегов, которые называют наш пакет (`WindowOrganizerController`, `AppOps`, `PermissionService`) или родовы (`PackageManager`, а также сигнатура «Failed to query component interface for required system resources» под любым тегом) - benign ТОЛЬКО когда совпали и тег, и сигнатура сообщения. Такой кластер остаётся в отчёте как benign (не дропается, не actionable). Гарантирует: будущая настоящая app-ошибка под тем же тегом, но другой сигнатурой, всё равно actionable.

`$isBenign = ($benignPatterns) -or (Test-BenignPair …)`; foreign-дроп добавлен рядом с `$systemTagHint`.

---

## 6. Открытые вопросы / Research items

Разрешены при реализации (из кода + owner-steer §0):

1. **Матч benign по паре (тег, сигнатура) vs отдельный system-bucket?** -> оба: заведомо-чужие теги дропаются как system-шум (`$foreignTagPatterns`), app-названные - benign по паре тег+сигнатура (`$benignTagSignaturePairs`). Никогда не глушим по всему тегу/пакету.
2. **Фильтр по PID нашего процесса vs аллоулист тегов?** -> не PID: скрипт по контракту поддерживает `-v time`, где нет колонки пакета/надёжного PID-парсинга; тег+сигнатура универсальнее и безопаснее.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слишком широкий аллоулист проглотит настоящую app-ошибку | Средняя | Ложный PASS на релизе | Матч по паре тег+сигнатура; app-процессные ошибки никогда не в общем benign |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - внутренний devtest-тулинг.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0484 (prerelease sweep) - владелец аудита, чей аллоулист донастраивается.

---

## 11. Критерии готовности (strategic-level)

1. Рекуррентный эмулятор/система/GMS/Maestro-шум не попадает в actionable-список аудита.
2. Настоящая app-уровневая ошибка (в т.ч. под тегом из benign-набора, но с другой сигнатурой) остаётся actionable.
3. Аллоулист не зависит от конкретного API эмулятора и работает и для `-v time`, и для `-v threadtime`.

---

## Last Audit

**Date:** 2026-07-10
**Outcome:** Verified
**Method:** static + synthetic-fixture run (`temp/S0976/fixture.log`, 15 представительных строк из прогона 2026-07-07).

- `$foreignTagPatterns` (drop) + `$benignTagSignaturePairs`/`Test-BenignPair` (tag+signature benign) добавлены; app-рантайм не тронут. Скрипт остаётся reporting-only (exit 0/1/2, каталог не мутирует).
- Fixture-прогон: 8/15 foreign-строк дропнуто, 5/15 benign по паре тег+сигнатура, 2/15 actionable. expected | actual совпали.
- Safety-guard (критерий 2): `E PackageManager : real app failure resolving our own content provider` - тег в benign-наборе, но сигнатура не `alignment|mobile.maestro` -> остался actionable. И `E FastMediaSorter : NullPointerException …` -> actionable. Подтверждает: benign по паре, не по тегу.
- Формат-независимость (критерий 3): benign-логика работает на распарсенных (tag,msg), общих для обоих regex (`-v time` / `-v threadtime`); PID-фильтр сознательно отвергнут (Q2 §6).

No action items.
