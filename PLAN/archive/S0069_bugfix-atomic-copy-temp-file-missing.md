# Стратегическая спецификация: S0069 — Temp-файл исчезает после шага copy в AtomicFileOperationStrategy

**Ticket:** S0069
**Status:** Verified
**Priority:** 75
**Date:** 2026-05-03
**Tier:** 3 — Moderate
**Roadmap entry:** Ad-hoc — полевой инцидент 2026-05-03, лог `logs/fastmediasorter_20260503_180505.log`
**Tactical plan:** `PLAN/S0069_bugfix-atomic-copy-temp-file-missing/INDEX.md`
**Related:** S0061 (тот же SMB/copy path), S0055 (смежный cancel/log-noise), S0025 (transport fast-fail до copy path)

> **Scope:** STRATEGIC. Починить дефект, при котором atomic-copy path доходит до проверки temp-файла и обнаруживает, что временный файл уже отсутствует, хотя шаг copy только что считался завершённым.

---

## 1. Проблема

В логе `logs/fastmediasorter_20260503_180505.log` одна SMB-copy операция проходит через `AtomicFileOperationStrategy` и срывается на инварианте temp-файла:

```text
11:xx AtomicFileOperationStrategy: Temp file doesn't exist after copy!
11:xx AtomicFileOperationStrategy: Unexpected error during atomic copy
11:xx SmbFileOperations: Failed to download file from SMB
```

Эффект: операция завершается ошибкой, final rename не происходит, а пользователь видит непрозрачную смесь «отмена / SMB download / atomic copy» вместо одного чёткого исхода. Сам факт отсутствия temp-файла после завершения copy-шага означает, что владельцы temp-path и cleanup-path сейчас размазаны между несколькими слоями: atomic strategy, SMB transfer helper, cancellation handler.

Это уже не просто log-noise. Это функциональный дефект copy-path: стратегия атомарной записи больше не гарантирует основной контракт «сначала полностью собрать temp, потом переименовать».

---

## 2. Цели

1. После успешного завершения copy-step temp-файл гарантированно существует до момента atomic rename.
2. Пользовательская отмена операции не маскируется под `Unexpected error during atomic copy`; cancel-path логируется и завершается отдельно.
3. Владелец temp-файла ровно один: create / verify / rename / cleanup живут в одном слое, а delegate-стратегии только записывают данные.
4. Лог однозначно различает минимум три исхода: `cancelled`, `copy-failed`, `temp-missing-invariant`.
5. После сбоя не остаётся orphan temp-файлов и не теряется partially-copied final target.

**Non-goals:**

- Не добавлять resume / offset-copy для больших файлов.
- Не менять UI copy-dialog и тексты пользовательских сообщений сверх необходимости.
- Не перестраивать все file-operation strategies, если дефект локализован в network-to-local atomic path.
- Не оптимизировать throughput SMB copy itself.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Желательно одно место истины для temp-path: не допускать, чтобы SMB helper сам удалял temp-файл, созданный atomic strategy.
2. Желательно отдельная диагностика с `source`, `temp`, `target`, чтобы воспроизводимость была видна без дебага на устройстве.
3. Желательно трактовать user-cancel как штатное завершение, а не как error-level failure.

### 3.2 Жёсткие ограничения

- **Flavor:** все, где доступна операция copy/move с network-источниками; фактический reproducer сейчас SMB.
- **API level:** без специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** без double-copy и без дополнительного чтения всего файла после записи; допустима только дешёвая post-condition проверка `exists + length`.
- **Совместимость данных:** без миграций.
- **Локализация:** по возможности reuse существующих UI-строк; основная работа — в логике и логах.
- **Безопасность данных:** при любом сбое final target либо не создаётся, либо остаётся в предыдущем консистентном состоянии.

---

## 4. Контекст текущей архитектуры

`AtomicFileOperationStrategy` создаёт temp-file, делегирует фактическую copy/download нижнему transport-слою, затем проверяет temp и переименовывает его в final target. Для SMB path делегат идёт через `SmbOperationStrategy` / `SmbFileOperations`. Последний лог показывает, что между «copy finished» и «verify temp exists» файл уже отсутствует.

Сейчас неясно, где именно нарушается контракт:

- temp удаляет сам atomic strategy в premature cleanup path;
- temp удаляет SMB helper на отмене или ошибке, хотя владелец temp не он;
- copy helper вообще пишет не в тот path;
- cancel-path и success-path race'ятся между собой при завершении корутины.

Дополнительный сигнал из того же лога: user-cancel на верхнем уровне уже зафиксирован как штатное событие, но глубже по стеку всё ещё всплывают error-level записи `Failed to download file from SMB` и затем `Unexpected error during atomic copy`. Это означает, что semantic boundary между `cancelled` и `failed` сейчас размыта.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**A. Один владелец temp lifecycle.**
Temp-файл создаёт и удаляет только atomic strategy. Нижний SMB/FTP/SFTP helper получает уже готовый temp-path и никогда не удаляет его самостоятельно.

**B. Явный результат copy-step.**
Delegate copy-step возвращает не только success/fail, но и отдельный `cancelled` outcome. Atomic strategy принимает решение о cleanup/rename по этому outcome, не по косвенным исключениям.

**C. Post-condition как инвариант.**
После `success` atomic strategy выполняет дешёвую проверку: temp exists, size >= 0, path совпадает с ожидаемым. Если инвариант нарушен — лог отдельного класса `temp-missing-invariant` и controlled failure без попытки rename.

**D. Cleanup без race.**
Cleanup происходит в одном `finally`-контуре atomic strategy после того, как решение `rename / cancel / fail` уже принято. Никакой второй cleanup path в delegate быть не должно.

### 5.2 Поток событий

```text
atomic strategy creates temp
  → delegate copies source into temp
  → delegate returns Success | Cancelled | Failed
  → if Success:
        verify temp invariant
        rename temp → target
  → if Cancelled:
        delete temp quietly
        surface cancel
  → if Failed:
        delete temp if present
        surface real copy failure
```

### 5.3 Точки расширяемости

- Тот же контракт можно применить к другим network strategies, если окажется, что дефект не SMB-specific.
- `copy-step outcome` можно reuse для move/delete pipelines, где cancellation тоже должна быть штатной веткой.

---

## 6. Открытые вопросы / Research items

1. **Кто именно удаляет temp-файл?**
   - **Вопрос:** atomic strategy, SMB delegate или отдельный cancel cleanup?
   - **Нужно выяснить:** точку удаления по коду и/или дополнительному diagnostic log around temp path lifecycle.
   - **Статус:** Verified

2. **Дефект only-on-cancel или also-on-success?**
   - **Вопрос:** temp missing возникает только после user-cancel, или возможен и при nominal success path?
   - **Нужно выяснить:** reproducer без cancel, плюс unit/integration test на cancel boundary.
   - **Статус:** Verified

3. **SMB-only или любой network delegate?**
   - **Вопрос:** path нарушается только через `SmbFileOperations`, или та же проблема есть у FTP/SFTP/local delegates, если они работают через atomic strategy?
   - **Нужно выяснить:** какие delegates имеют собственный cleanup temp-path.
   - **Статус:** Verified

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Удалим eager cleanup в delegate, а orphan temp-файлы начнут копиться при crash | Средняя | Мусор в temp-dir | Cleanup остаётся в atomic strategy `finally`, плюс startup scavenging если уже существует |
| Неправильно разведём `cancelled` vs `failed` и скроем реальную SMB-ошибку | Средняя | Сложнее диагностировать реальные сетевые сбои | Явный outcome enum + log reason from delegate |
| Инвариант `temp exists` проверяется слишком рано и ловит ложный race записи | Низкая | Ложные negative failures | Verify only after delegate reports success and closes its streams |
| Фикс будет SMB-only, а аналогичный bug останется в других delegates | Низкая | Повтор проблемы на FTP/SFTP | Tactical audit всех delegates на предмет владения temp lifecycle |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без обновления `docs/FEATURES*`. Это bugfix надёжности file operations, не новая функция.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Temp lifecycle принадлежит atomic strategy, не delegate.**

- **Решение:** create / verify / rename / cleanup temp-файла централизованы в одном слое.
- **Альтернативы:** каждый delegate сам управляет своим temp-path — отвергнуто, потому что ломает atomic contract и создаёт race cleanup vs rename.
- **Почему:** atomic strategy единственная знает, когда temp ещё нужен для rename, а когда уже можно чистить.

**ADR-2: User-cancel — отдельный outcome, не exception alias для failure.**

- **Решение:** `cancelled` обрабатывается как штатная ветка.
- **Альтернативы:** трактовать cancel через generic exception path — отвергнуто, потому что именно это смешивает `Failed to download` и `Unexpected error during atomic copy` в одном логе.
- **Почему:** cancellation — ожидаемое действие пользователя, а не дефект copy pipeline.

---

## 10. Связи с другими спеками

- **S0061** — тот же SMB/copy ecosystem; восстановление соединения не решает temp lifecycle invariant.
- **S0055** — смежный diagnostic issue: user-cancel не должен всплывать как error cascade.
- **S0025** — fast-fail до начала transport path; не владеет багом temp-файла, но тот же user-visible сценарий «быстро и понятно завершить невозможную операцию».

---

## 11. Критерии готовности (strategic-level)

1. В reproducer-сценарии из `logs/fastmediasorter_20260503_180505.log` больше не появляется `Temp file doesn't exist after copy!`.
2. User-cancelled copy завершается по cancel-path без `Unexpected error during atomic copy`.
3. Успешный SMB-copy path всегда доходит до rename, если delegate вернул success.
4. После failed/cancelled path temp-файл удаляется детерминированно; final target остаётся консистентным.
5. Есть автоматическая проверка хотя бы на два сценария: `delegate returns success` и `delegate cancelled after partial write`.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0069` — создаст `PLAN/S0069_bugfix-atomic-copy-temp-file-missing/` с фазами.

---

## Last Audit

**Date:** 2026-05-03
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 18 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] Re-run the SMB cancel reproducer from `logs/fastmediasorter_20260503_180505.log` on-device/logcat to confirm runtime absence of `temp-missing-invariant` and `Unexpected error during atomic copy` in the real share flow.