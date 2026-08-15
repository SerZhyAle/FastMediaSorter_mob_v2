# Компактная спецификация: S1367 - Поле role в каталоге классов пустое почти везде

**Ticket:** S1367
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-02
**Tier:** Simple - catalog tooling only
**Roadmap entry:** release 30 - engineering quality

---

## 0. Approval Gate (owner input)

- **Goal:** Provided by user - run the complete specification pipeline for S1367.
- **Scope:** Delegated by user - /spec-all auto-approval. Define and verify a review-first bulk-role workflow for the existing `app_v2` and `wear` catalogs; do not mass-apply generated text without human review.
- **Done signal:** Delegated by user - /spec-all auto-approval. Both modules can generate review TSV drafts, a dry run proves that only non-empty reviewed rows would be applied, and the catalog documentation describes the workflow.
- **Related tickets:** S1344 (sector lookup consumes role descriptions), S1338 (parent process overhaul).

---

## 1. Проблема

Поле `role` почти пусто: на 2026-08-03 в `app_v2` заполнено 74 из 2414 записей, в `wear` - 1 из 82. Из-за этого секторная карта S1344 обычно сообщает только путь и технические признаки, но не назначение класса. Автоматически записывать сгенерированные формулировки небезопасно: 754 из 2396 подготовленных черновиков получены синтезом имени и функций, а не KDoc.

## 2. Цели

1. Сохранить воспроизводимый двухшаговый путь: генерация черновиков отдельно от изменения JSONL.
2. Давать черновик из class-level KDoc, когда он есть, и помечать остальные как synthesized для человеческой проверки.
3. Применять только непустые строки из отредактированного TSV и только в пока пустые `role`; существующие ручные описания не перезаписывать.
4. Покрыть оба поддерживаемых модуля и задокументировать операционный путь.

## 3. Пожелания и ограничения

### 3.1 Жёсткие ограничения

- `role` остаётся ручным семантическим полем; сканер сохраняет его, а не выводит как авторитетное значение из KDoc.
- Массовое применение требует явной проверки TSV владельцем. Пустой `draftRole` означает пропуск записи.
- Изменения ограничены инструментами `dev/CATALOG`; Android-код, схема Room, Hilt, UI и flavor source sets вне scope.
- Каталог остаётся синхронным: после реального применения перерисовывается Markdown-представление модуля.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1344 (потребитель семантического поля `role`), S1338 (родитель).

## 4. Контекст текущей архитектуры

`scan.ps1` создаёт структурные поля каталога и сохраняет ручные `role`, `status`, `noFlavors` и описания функций. `generate-role-drafts.ps1` читает JSONL и Kotlin source roots, пишет TSV для проверки и не меняет каталог. `apply-role-drafts.ps1` читает этот TSV, заполняет только пустые роли и вызывает `render.ps1`. Это разделяет вероятностную генерацию и обратимое решение человека.

## 5. Подход

Проверить существующий review-first механизм на полном наборе обоих модулей. Артефакты генерации хранятся только в `temp/S1367/`, не коммитятся и не применяются в этой автоматической сессии. Применение остаётся последующей ручной операцией: владелец редактирует TSV, очищает неподходящие строки и запускает apply без `-DryRun`.

## 6. Исследование

1. **Заполненность каталога - Resolved.** `app_v2`: 2340 пустых из 2414; `wear`: 81 пустая из 82. Проблема не ограничена сектором player.
2. **Источник значений - Resolved.** `scan.ps1` всегда создаёт пустой `role` и сохраняет только ранее введённый непустой manual value. `generate-role-drafts.ps1` сначала извлекает class-level KDoc, иначе synthesises текст из имени класса и функций.
3. **Окупаемость и безопасность - Resolved.** Полный прогон подготовил 2315 строк для `app_v2` (1596 KDoc, 719 synthesized) и 81 для `wear` (46 KDoc, 35 synthesized). Поэтому автоматическое применение всех 2396 строк не допускается; review TSV даёт практичный поэтапный путь без потери существующих ролей.

## 7. Риски

- Синтезированная роль может быть неточной. Митигация: владелец правит или очищает строку до apply.
- Повторный запуск может перезаписать знание. Митигация: apply меняет только пустое `role`, а scan сохраняет непустые manual fields.
- Непроверенный TSV может содержать неправильную строку. Митигация: `-DryRun` показывает набор изменений до записи JSONL.

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений - это инструментарий разработки, не пользовательская функция приложения.

## 9. Архитектурное решение

Разделить генерацию и применение: черновики являются входом для review, а не данными каталога. KDoc приоритетнее synthesis; synthesis допустим только как редактируемая подсказка.

## 10. Связи с другими спеками

- S1344 использует `role` для содержательной карты сектора, но не владеет наполнением поля.
- S1338 владеет родительской процессной программой.

## Phases

### Phase 01 - Verify review-first draft generation

- [x] Run `generate-role-drafts.ps1 -IncludeAll` for `app_v2` and `wear` into `temp/S1367/`.
- [x] Confirm each TSV carries `path`, `class`, `layer`, `source`, and `draftRole`, and reports both KDoc and synthesized sources.
- **Verification:** generation exits 0 for both modules; the TSVs contain 2315 and 81 draft rows respectively.

### Phase 02 - Prove non-destructive application

- [x] Run `apply-role-drafts.ps1 -DryRun -NoRender` against the generated `app_v2` TSV.
- [x] Confirm the run reports prospective changes without modifying catalog files.
- **Verification:** exit 0 and output reports `would apply 2315 role(s)`; `git diff` contains no catalog JSONL/Markdown changes from the dry run.

### Phase 03 - Preserve the operator contract

- [x] Verify the CATALOG README documents generate, TSV review, dry-run and apply for both modules.
- [x] Record the implementation evidence and audit outcome in this spec.
- **Verification:** README names both scripts and states that blank `draftRole` skips a record and apply only fills empty roles.

## 11. Критерии готовности

1. Двухшаговый review-first путь проверен на обоих модулях без изменения каталога.
2. Документация описывает безопасное применение и повторный запуск.
3. Статический аудит подтверждает отсутствие автоприменения в генераторе и защиту от перезаписи в apply.

## Implementation Evidence

- `generate-role-drafts.ps1 -IncludeAll` completed for both modules: app_v2 produced 2315 rows (1596 KDoc, 719 synthesized); wear produced 81 rows (46 KDoc, 35 synthesized).
- The TSV header is `path`, `class`, `layer`, `source`, `draftRole` for both modules.
- `apply-role-drafts.ps1 -DryRun -NoRender` completed for app_v2 and reported 2315 prospective role updates. It did not write the catalog.
- Static inspection confirms generator source labels KDoc versus synthesis, and apply skips a record whose `role` is already non-empty before its dry-run/write branch.

## Last Audit

**Date:** 2026-08-03
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Owner review: edit `temp/S1367/*-role-drafts.tsv`, blank rejected drafts, run `apply-role-drafts.ps1` without `-DryRun` module by module. This operational rollout is intentionally outside the automated ticket because synthesis is not authoritative.
