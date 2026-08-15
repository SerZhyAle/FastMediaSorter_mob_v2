# Стратегическая спецификация: S0720 - Внедрение detekt + ktlint

**Ticket:** S0720
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - дочерний тикет S0714 (принятие Code Audit Protocol)
**Umbrella:** S0714

> **Scope:** STRATEGIC. Цели и объём внедрения тулинга. Конкретные правила/пороги/версии - на этапе `/spec-tech`.

---

## 0. Источник

Recommended next additions #3 протокола `docs/CODE_AUDIT_PROTOCOL.md` + механизация читаемости Layer 1. Проверено: в `app_v2/build.gradle.kts` и каталоге версий **нет ни `detekt`, ни `ktlint`** - главный отсутствующий тулинг для принуждения читаемого кода.

## 1. Проблема

Читаемость и сложность сейчас принуждаются только ревью и `measure-hotspots.ps1` (ответственность по прокси). Нет инструмента, дающего метрики сложности/вложенности/длины метода/code-smell (`detekt`) и детерминированного формата (`ktlint`). Без них Layer 1 опирается на человеческое внимание и дрейфует.

## 2. Цели

1. Подключить `detekt` к сборке: набор правил (сложность, вложенность, длинные методы, code-smells), отчёт, **ratchet-baseline** на текущее состояние.
2. Подключить `ktlint` (или detekt-formatting): детерминированный формат, baseline на текущее.
3. Привязать оба к локальному принуждению через `scripts/post-change.ps1` (и/или pre-commit), форма CI - по решению владельца (открытый вопрос зонтика).
4. Не ронять зелёную сборку: baseline замораживает существующие нарушения, гейт запрещает только рост.

**Non-goals:** одномоментная чистка всех текущих нарушений (это потоки S0718 и последующие); custom Android Lint (S0721); правила архитектуры уровня структуры.

## 3. Объём и ограничения

- Модули `app_v2/` и `wear/`.
- Ratchet-baseline обязателен - паттерн существующих гейтов `scripts/quality/`.
- Конфигурация detekt/ktlint версионируется; baseline-файлы рядом с конфигом.
- Без `BuildConfig.IS_*` и без влияния на рантайм.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0714, S0718, S0721, S0723
- **Build:** добавляет detekt + detekt-formatting (ktlint) в сборку обоих модулей как отдельный статический гейт; не входит в `assembleDebug`, не меняет рантайм/артефакт. CI-форма гейта - открытый вопрос зонтика S0714 (вне scope; локальное принуждение через `post-change.ps1`).

## 4. Критерии приёмки

- [x] `detekt` запускается локально (`:app_v2:detekt :wear:detekt`) и через `post-change.ps1` (`assert-detekt.ps1 -Gate`, ChangeType Kotlin/Mixed); даёт отчёт; baseline заморожен; новые нарушения роняют гейт.
- [x] detekt-formatting (ktlint-ruleset) подключён через `detektPlugins`; формат детерминирован; включён в общий baseline.
- [x] Текущая зелёная сборка остаётся зелёной: `:app_v2:detekt :wear:detekt` BUILD SUCCESSFUL на baseline; `.\a.ps1 fk` BUILD SUCCESSFUL (detekt - отдельный гейт, не в `assemble*`).
- [x] Конфиг (`config/detekt/detekt.yml`) и baseline (`baseline-app_v2.xml` 12706 / `baseline-wear.xml` 254) в дереве (не gitignored); запуск описан в `docs/DEV_OPS.md`.
- [x] Инженерная запись `tooling.static_analysis_detekt` в `docs/ALL_FEATURES.jsonl`.

## 5. Связанные тикеты

- S0714 (зонтик).
- S0718 (читаемость - использует detekt-метрики как ориентир).
- S0721 (механические гейты/custom Lint - комплементарный тулинг).
- S0723 (кодификация правил - ссылка на detekt/ktlint в CLAUDE.md).

## Last Audit

**Date:** 2026-06-26
**Mode:** full (strategic + impl)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0

detekt 1.23.8 + detekt-formatting (ktlint) внедрён как отдельный статический гейт:

- Плагин в root `build.gradle.kts` (`apply false`), применён per-subproject через `subprojects { }` с `DetektExtension` (`buildUponDefaultConfig`, `parallel`, `config.setFrom(config/detekt/detekt.yml)`, per-module `baseline`, `source.setFrom(files("src"))` - все source-set'ы вкл. flavor). Без type resolution (лексический проход).
- `config/detekt/detekt.yml` - на дефолтах + formatting-ruleset + пороги complexity. Ratchet-baseline заморожен: `baseline-app_v2.xml` (12706 findings), `baseline-wear.xml` (254). `:app_v2:detekt :wear:detekt` BUILD SUCCESSFUL.
- Локальное принуждение: `scripts/quality/assert-detekt.ps1 -Gate` (exit 1 на новых findings), включён в `scripts/post-change.ps1` для ChangeType Kotlin/Mixed. CI-форма - открытый вопрос зонтика S0714 (вне scope).
- Не в `assemble*`: `.\a.ps1 fk` BUILD SUCCESSFUL - рантайм/артефакт не затронут.
- Инженерная запись `tooling.static_analysis_detekt` в `docs/ALL_FEATURES.jsonl`; how-to в `docs/DEV_OPS.md`.

Реализация делегирована субагенту, верифицировано независимо (повторный detekt-прогон + fk-сборка + проверка проводки/файлов).
