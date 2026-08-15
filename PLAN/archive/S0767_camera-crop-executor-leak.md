# Стратегическая спецификация: S0767 - Фоновый исполнитель кропа камеры не закрывается

**Ticket:** S0767
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-28
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - parked by /spec-all from S0765 (2026-06-28)
**Complexity:** Simple (compact spec, phases inline)

<!-- auto-approved by /spec-all - 2026-06-28 -->

> **Scope:** Compact spec - стратегическая цель + тактические фазы в одном файле.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-28

> Находка при реализации S0765 (research, /spec-draft candidate). Менеджер сессии камеры создаёт однопоточный исполнитель для off-thread JPEG-кропа цифрового зума, но никогда не завершает его при разрыве сессии.

**Текст:**

1. Однопоточный `Executors.newSingleThreadExecutor()` создаётся в конструкторе менеджера сессии камеры.
2. В `unbind()` он не закрывается (`shutdown()`), поэтому каждый цикл открытия/закрытия камеры оставляет висящий поток до GC.

**Симптом:** утечка потока на каждый цикл открытия/закрытия сессии камеры. Кроп «в полёте» после `unbind()` может завершиться нормально, но поток не освобождается детерминированно.

**Вложений нет.**

---

## 1. Проблема

`CameraCaptureSessionManager.cropExecutor` (`Executors.newSingleThreadExecutor()`) - поле, заданное при конструировании. Его рабочий поток спавнится при первом off-thread кропе цифрового зума (`capture(..)` -> `onImageSaved` -> `cropExecutor.execute { .. }`) и живёт вечно (бесконечный keep-alive у core-потока single-thread executor) до GC менеджера. `unbind()` его не закрывает.

`CameraCaptureActivity` создаёт новый `CameraCaptureSessionManager` в `setupViews()` и вызывает `unbind()` только в `onDestroy()` - то есть каждый запуск экрана камеры (включая каждый рекреэйт при смене ориентации) оставляет осиротевший поток воркера до недетерминированной сборки мусора.

Не однострочник: кроп может быть «в полёте» в момент `unbind()`, а его колбэк дописывает JPEG-файл и затем диспатчит `onSaved()` на main - грубое завершение порвёт запись файла.

---

## 2. Цели

- Освобождать рабочий поток кропа детерминированно при разрыве сессии камеры (`unbind()`), а не полагаться на GC.
- Не рвать кроп «в полёте»: уже запущенная задача должна дописать JPEG целиком.
- Не блокировать main-thread в `onDestroy()`/`unbind()`.

**Non-goals:**

- EXIF-логика кропа (закрыто в S0765).
- Изменение поведения цифрового зума/кропа (S0753).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Минимальная, локальная правка в пределах одного менеджера; без изменения публичного поведения экрана камеры.

### 3.2 Жёсткие ограничения

- **Flavor:** камера в `src/main`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** освобождение потока; без влияния на латентность спуска; без блокировки main-thread.
- **Совместимость данных:** без миграций.
- **Локализация:** строки не вводятся.
- **Доступность:** без изменений.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0765 (находка обнаружена там); S0753 (ввёл цифровой зум/кроп).

---

## 4. Контекст текущей архитектуры

- `CameraCaptureSessionManager` (`ui/cameracapture/helpers/`) владеет CameraX-сессией; конструируется хостом, живёт один цикл bind/unbind на экземпляр Activity.
- `cropExecutor` используется только на пути цифрового зума (`digitalZoomFactor > 1f`) внутри колбэка `ImageCapture.OnImageSavedCallback.onImageSaved`.
- `cropCenter(..)` декодирует регион, масштабирует, перезаписывает файл, восстанавливает EXIF (S0765), затем диспатчит `onSaved()` на main-executor.
- `unbind()` уже освобождает CameraX-ресурсы (`unbindAll`, обнуление capture/preview), но не трогает `cropExecutor`.

---

## 5. Предлагаемый подход

Сделать `cropExecutor` ленивым освобождаемым ресурсом, симметричным жизненному циклу сессии:

- Хранить как nullable `var` (`ExecutorService?`), создавать по требованию при первом кропе - поток не спавнится для съёмок без цифрового зума.
- В `unbind()` вызывать `shutdown()` (упорядоченное завершение: даёт задаче «в полёте» дописать файл, отклоняет новые задачи, неблокирующее) и обнулять ссылку, чтобы повторный `bind()`+кроп пересоздал исполнитель (reuse-safe).

Не использовать `shutdownNow()` (прервёт запись JPEG) и не вызывать `awaitTermination()` в `unbind()` (заблокирует main-thread в `onDestroy()`).

---

## 6. Открытые вопросы / Research items

1. ~~`shutdown()` vs `shutdownNow()`~~ - **решено:** `shutdown()`. `shutdownNow()` прервёт running-задачу через interrupt -> риск битого/незаписанного JPEG. `shutdown()` останавливает приём новых задач, завершает текущую и гасит поток после опустошения очереди, не блокируя вызывающий поток.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Закрытие исполнителя во время кропа | Низкая | Незаписанный/битый JPEG | `shutdown()` (не `shutdownNow()`); running-задача дописывает файл |
| Повторный кроп после `unbind()` на том же экземпляре | Низкая | `RejectedExecutionException` | Ленивое пересоздание исполнителя при следующем кропе |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (внутренняя гигиена ресурсов).

---

## 9. Архитектурные решения (ADR)

- **ADR-1:** Ленивое создание `cropExecutor` - рабочий поток спавнится только когда реально нужен off-thread кроп (цифровой зум), а не для каждого экземпляра менеджера.
- **ADR-2:** `unbind()` - точка освобождения (симметрична `bind()`-владению ресурсом, CLAUDE.md Rule 18 / Code Audit Protocol «release on real teardown»).

---

## 10. Связи с другими спеками

- S0765 - тикет, при работе над которым найдено.
- S0753 - ввёл цифровой зум и off-thread кроп.

---

## 11. Критерии готовности (strategic-level)

- Рабочий поток исполнителя кропа освобождается при `unbind()` детерминированно, без ожидания GC.
- Кроп «в полёте» завершается без потери/повреждения файла.
- main-thread не блокируется в `onDestroy()`/`unbind()`.
- Повторный `bind()`+кроп на том же экземпляре менеджера не падает.

---

## Phases (tactical, inline)

### Phase 1 - Lazy, releasable crop executor

1. В `CameraCaptureSessionManager` заменить поле:
   - было: `private val cropExecutor = Executors.newSingleThreadExecutor()`
   - стало: `private var cropExecutor: ExecutorService? = null`
   - Добавить импорт `java.util.concurrent.ExecutorService`.
   - Обновить KDoc поля: создаётся при первом кропе, освобождается в `unbind()` (S0767).
   - **Verification:** Grep подтверждает `private var cropExecutor: ExecutorService? = null` и импорт `ExecutorService`; `newSingleThreadExecutor()` в поле отсутствует.

2. В пути цифрового зума внутри `capture(..)` (`onImageSaved`, ветка `factor > 1f`) получать-или-создавать исполнитель перед `execute`:
   - `val executor = cropExecutor ?: Executors.newSingleThreadExecutor().also { cropExecutor = it }`
   - вызвать `executor.execute { .. }` (тело без изменений).
   - **Verification:** Grep подтверждает get-or-create перед `.execute {` в `capture`; прямого обращения к `cropExecutor.execute` нет.

3. В `unbind()` добавить освобождение исполнителя (после освобождения CameraX-ресурсов):
   - `cropExecutor?.shutdown()` затем `cropExecutor = null`.
   - Комментарий WHY (EN): `shutdown()` (не `shutdownNow()`) даёт кропу «в полёте» дописать JPEG и гасит поток детерминированно, без блокировки main-thread (S0767).
   - **Verification:** Grep подтверждает `cropExecutor?.shutdown()` и `cropExecutor = null` в `unbind()`; `shutdownNow`/`awaitTermination` отсутствуют.

4. Build gate: `standard debug` компилируется. (`src/vr/` не затронут -> VR-сборка не нужна.)
   - **Verification:** `.\a.ps1 dq` -> PASS.

---

## 12. Ссылка на тактическую спецификацию

Компактный спек - фазы выше (§ Phases). Реализовано в этом же прогоне `/spec-all`.

---

## Last Audit

**Дата:** 2026-06-28 (/spec-all, Simple path)
**Вердикт:** Verified

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`

Реализация против критериев §11:

- `cropExecutor` -> ленивый nullable `var ExecutorService?` (L95), `import java.util.concurrent.ExecutorService` (L36). Рабочий поток спавнится только при первом off-thread кропе.
- `capture(..)` цифровой зум: get-or-create перед `execute` (L302-303) - повторный `bind()`+кроп на том же экземпляре менеджера не падает.
- `unbind()`: `cropExecutor?.shutdown()` + `cropExecutor = null` (L381-382). Упорядоченный `shutdown()` (не `shutdownNow()`) даёт кропу «в полёте» дописать JPEG, гасит поток детерминированно, не блокирует main-thread.

Все 4 критерия §11 выполнены кодом.

**Сборка:** `.\a.ps1 fk` (compileStandardDebugKotlin) -> BUILD SUCCESSFUL (38s). Изменение compile-only, без ресурсов/манифеста; `src/vr/` не затронут.

**Detekt-гейт (post-change, project-wide):** FAIL, но **не из-за этой правки** - все нарушения предсуществующие/чужой WIP (S0753/S0765): `applyNightMode` ReturnCount (функция не редактировалась), MagicNumber в `CameraRuntimeCapabilities`, ImportOrdering в `CameraCapabilityProbe`/`LeakDetectionInstrumentationTest`. Diff S0767 detekt-нарушений не добавил; не ре-baseline и не правил чужие файлы (CLAUDE.md dirty-tree policy).

**Device-test:** не требуется - внутренняя гигиена ресурсов без видимого пользователю поведения; инвариант проверяется аудитом кода.
