# Спецификация (compact bugfix): S1075 - dev/TECH_REQUIREMENTS.md разошёлся с Gradle-пинами

**Ticket:** S1075
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-16
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-16

**Текст:**

<нет текста - авто-захват по CLAUDE.md §3.1 во время добавления шага проверки зависимостей в `/spec-prerelease`>

`scripts/check-doc-vs-gradle.ps1` выходит с кодом 1 на чистом дереве. Все FAIL - в `dev/TECH_REQUIREMENTS.md`, документ отстал от реальных Gradle-пинов:

```text
FAIL | agp | gradle: 9.2.1 | dev/TECH_REQUIREMENTS.md: 9.2.0
FAIL | ksp | gradle: 2.3.8 | dev/TECH_REQUIREMENTS.md: 2.3.2
FAIL | navigation-safe-args | gradle:  | dev/TECH_REQUIREMENTS.md: 2.7.6
FAIL | lib.androidx.core:core-ktx | gradle: 1.13.0 | dev/TECH_REQUIREMENTS.md: 1.12.0
FAIL | lib.androidx.appcompat:appcompat | gradle: 1.7.1 | dev/TECH_REQUIREMENTS.md: 1.6.1
FAIL | lib.com.google.android.material:material | gradle: 1.14.0 | dev/TECH_REQUIREMENTS.md: 1.12.0
FAIL | lib.androidx.room:room-runtime | gradle: 2.7.0 | dev/TECH_REQUIREMENTS.md: 2.6.1
INCONSISTENT | lib.com.google.dagger:hilt-android | dev/TECH_REQUIREMENTS.md: 2.50 vs 2.57.2
SUMMARY | total: 90 | pass: 10 | fail: 7 | warn: 0 | skip: 61 | inconsistent: 1 | missing: 11
```

Отдельно: `navigation-safe-args` - пин не извлекается из Gradle вообще (`gradle: ` пусто), то есть это либо мёртвая запись в доке, либо дыра в парсере. `hilt-android` заявлен в одном документе двумя разными версиями (2.50 и 2.57.2).

Плюс 11 `MISSING` - записи, требующие упоминания пина в доке, которого там нет.

**Почему не чинилось на месте:** это не однострочник - 7 расхождений + 1 внутреннее противоречие + 11 пропусков, и часть из них ставит вопрос, что вообще является источником правды (`dev/TECH_REQUIREMENTS.md` как требование vs Gradle как факт), а `navigation-safe-args` требует отдельного разбора парсера. Гейт красный - значит либо он не запускается никем, либо его давно игнорируют; это тоже часть вопроса.

**Захвачено во время:** добавления шага 0.5 (проверка/обновление внешних зависимостей) в `.claude/commands/spec-prerelease.md`

---

## 1. Проблема / симптом

`pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` → exit 1 на чистом дереве, 7 FAIL + 1 INCONSISTENT + 11 MISSING (эвиденс - §0). Гейт задуман как PR-блокер (§5.4 стратегии S0271), но в текущем виде красный всегда, поэтому сигнала не несёт: новый реальный дрейф в нём неотличим от фонового шума.

Затронуто: `dev/TECH_REQUIREMENTS.md`, `docs/TECH_STACK.md`, `CLAUDE.md` (пины), парсер `scripts/doc-drift/`.

---

## 2. Корневая причина

Три подтверждённые ветки, все указывают на документ, а не на код:

- (а) Документ отстал при бампах Gradle: agp, ksp, core-ktx, appcompat, material, room и hilt-android в Gradle новее, чем зафиксировано в `dev/TECH_REQUIREMENTS.md`. Источник фактической версии - Gradle; документ обязан следовать за ним.
- (б) `navigation-safe-args` - мёртвая запись: плагина Safe-Args в репозитории нет, пин из Gradle не извлекается потому, что извлекать нечего. Это не дыра парсера, а устаревшая строка документа - её надо удалить.
- (в) 11 MISSING - записи, которые действующая политика per-doc pin'ов (`pins.psd1`, S0271) требует упомянуть, но в доке их нет; правится дополнением дока по этой политике, без изменения правил.

Внутреннее противоречие hilt-android (2.50 vs 2.57.2): актуальна 2.57.2, ссылка на 2.50 - устаревший остаток. Красный гейт держится потому, что чекер не подключён ни к одному блокирующему шагу (используется только в `/spec-prerelease`, шаг 0.5), поэтому дрейф копился незамеченным.

---

## 3. Исправление

- Привести 7 устаревших пинов в `dev/TECH_REQUIREMENTS.md` к фактическим версиям Gradle: agp 9.2.1, ksp 2.3.8, core-ktx 1.13.0, appcompat 1.7.1, material 1.14.0, room 2.7.0, hilt-android 2.57.2.
- Удалить мёртвую строку `navigation-safe-args` из документа.
- Свести hilt-android к единственной версии 2.57.2, убрав устаревшую 2.50.
- Закрыть 11 MISSING по действующей политике per-doc pin'ов (`pins.psd1`, S0271): добавить требуемые упоминания пинов.
- Вписать `scripts/check-doc-vs-gradle.ps1` в блокирующий gate (`.\a.ps1 fg` и `scripts/post-change.ps1`), чтобы после починки дрейф ловился на месте, а не копился до следующего prerelease.
- `dev/TECH_REQUIREMENTS.md` - registered-doc: правки идут на этапе реализации (не в этой спеке), с последующей регенерацией и валидацией через document-registry.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0271 (сам чекер doc-vs-gradle), S0268 (bootstrap-warning канал)
- **Decision (GateScope):** починить текущий дрейф И вписать `check-doc-vs-gradle.ps1` в блокирующий gate (`fg` / `post-change.ps1`), чтобы гейт нёс сигнал, а не оставался постоянно красным.

---

## 4. Проверка

`pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` → exit 0 (expected: 0, fail: 0, inconsistent: 0, missing: 0).

### Quiz decisions (2026-07-18)

- Только починить дрейф, или ещё и подключить чекер к блокирующему гейту? -> И то, и другое: чинить дрейф И вписать `check-doc-vs-gradle.ps1` в `fg` / `post-change.ps1` (иначе красный гейт снова накопит дрейф незамеченным).

## Реализация (2026-07-19)

Живой прогон чекера подтвердил снапшот §0 без дальнейшего дрейфа, но фактические версии Gradle разошлись со снапшотом спеки: **hilt = 2.59** (не 2.57.2, спека устарела за 3 дня), **jsch = 0.2.26** (не 0.2.16). Синхронизировал по живому Gradle.

**Дрейф в `dev/TECH_REQUIREMENTS.md` (bump до Gradle-истины):**
- agp 9.2.1, ksp 2.3.8, core-ktx 1.13.0, appcompat 1.7.1, material 1.14.0, room-runtime/ktx/compiler/testing 2.7.0, hilt-android/-compiler 2.59, hilt-work/hilt-compiler(androidx) 1.2.0, jsch 0.2.26. Плюс §11: Kotlin-примечание KSP 2.3.8, AGP 9.2.1, Hilt 2.59; §6 AGP 9.2.1; шапка Last Updated.
- Удалена мёртвая строка `navigation-safe-args` (плагин Safe-Args в репозитории не применяется - подтверждено grep'ом).
- Свёл hilt-android к единственной версии (2.59), убрал внутреннее противоречие 2.50 vs 2.57.2. Строку hilt-android-testing выставил в текущую Gradle-истину 2.57.2 (её рассинхронизация с runtime 2.59 - отдельная находка, см. ниже).
- Ячейку `jsch` очистил от "(mwiede fork)" (ломала matcher), пометку перенёс в столбец Purpose.

**Баг в самом чекере (`scripts/doc-drift/Comparator.ps1`):** мой корректный фикс вскрыл латентный дефект. При двух ОДИНАКОВЫХ упоминаниях (`[2.59, 2.59]`) код делал `$unique[0]`, но `Select-Object -Unique` отдаёт скаляр-строку, и `[0]` брал её первый СИМВОЛ ("2") -> ложный FAIL. Раньше не проявлялся, т.к. hilt был `[2.50, 2.57.2]` (разные) и шёл в ветку INCONSISTENT. Починено (`@(@($unique)[0])`) + регрессионный тест в `Comparator.Tests.ps1` (набор 19/19 зелёный).

**Развязка двух перекрывающихся инструментов (решение владельца, Path A):** пины CLAUDE.md/TECH_STACK.md теперь генерируются `generate-toolchain-pins.ps1` (формат `Label: value`, гейт doc-pins-sync, дрейф структурно невозможен), а matcher'ы S0271 ждут другой формат -> 10 неустранимых руками MISSING + удаление safe-args оставляло MISSING. Изначальное "не менять правила" оказалось невыполнимо. По решению владельца: в `pins.psd1` те 10 упоминаний + dead safe-args переведены в `required=$false; matcher=$null`. S0271 теперь сторожит только `dev/TECH_REQUIREMENTS.md` - его уникальное покрытие (генератор эту таблицу не трогает). Покрытие не потеряно.

**Гейт (§3.3 GateScope):** `check-doc-vs-gradle.ps1` вписан в блокирующий гейт через тонкий wrapper `scripts/quality/assert-doc-pin-drift.ps1` (конвенция assert-*, exit-контракт S1070, чекер запускается в отдельном процессе - StrictMode не протекает). Зарегистрирован в `assert-fast-gates.ps1` (`fg`) и как шаг `doc-pin-drift` в `post-change.ps1` (ChangeType Config/Doc/Mixed, фатальный - как doc-pins-sync).

**Проверка:** `check-doc-vs-gradle.ps1` -> exit 0 (fail 0, inconsistent 0, missing 0); doc-drift тесты 19/19; `fg` и `post-change` показывают `doc-pin-drift PASS`. `dev/TECH_REQUIREMENTS.md` - registered-doc: registry validate PASS (23), generate/-Check актуальны.

**Запарковано (вне scope, CLAUDE.md 3.1):**
- S1119 - Gradle-внутренняя рассинхронизация hilt-android-testing 2.57.2 vs runtime 2.59.
- S1120 - Room schema version в доке противоречит сам себе (§4.6=19, §10=41) и обе устарели (фактически `AppDatabase.kt` version=42); это doc-vs-код, не gradle-пин.
