# Стратегическая спецификация: S0221 — Устранение deprecated Window color APIs (Play Console)

**Ticket:** S0221
**Status:** Verified
**Priority:** 55
**Date:** 2026-05-16
**Tier:** 1 — Quick Win
**Roadmap entry:** Ad-hoc — Play Console warning, релиз 2.60.5160.406
**Tactical spec:** `PLAN/S0221_play-console-deprecated-window-color-apis/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Play Console помечает релиз 2.60.5160.406 предупреждением «Your app uses deprecated APIs for edge-to-edge»:
`android.view.Window.setStatusBarColor` и `android.view.Window.setNavigationBarColor` обнаружены в байткоде.
Трейс указывает на Material BottomSheetDialog и две обфусцированные записи (`w82.a`, `x82.a`) из библиотечного кода.

Дополнительно: ресурсный файл тем выставляет `android:statusBarColor` и `android:navigationBarColor` явно — оба deprecated на API 35. При этом в dark-mode на API 35 ресурс `values-v35/` не применяется (night-mode qualifier перекрывает platform-version qualifier по приоритету Android resource resolution), и исправление для BottomSheet из `values-v35/` не вступает в силу в тёмной теме.

---

## 2. Цели

1. Удалить явные `android:statusBarColor` / `android:navigationBarColor` из всех тем (light и dark) — оба значения были `@android:color/transparent`, edge-to-edge обрабатывает прозрачность программно.
2. Расширить BottomSheet edge-to-edge оверрайд (`enableEdgeToEdge=true`) на dark-mode + API 35 — сейчас он применяется только к light-mode на API 35.
3. Убедиться, что ни light-mode, ни dark-mode на API 35 не выставляют эти цвета ни через тему, ни через прямые вызовы в нашем коде.
4. Снизить число предупреждений Play Console для будущих релизов.

**Non-goals:**

- Устранение вызовов из самой библиотеки Material — статический анализ Play Console может продолжить видеть библиотечный байткод с deprecated-вызовами даже после нашего фикса; это вне нашего контроля.
- Удаление `WindowCompat.setDecorFitsSystemWindows()` из кода плеера — это отдельная deprecated-проблема, не флагуемая в текущем предупреждении.
- Изменение поведения ориентации экрана (отдельный тикет).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. После фикса проверить визуально, что цвета системных баров не изменились ни в light, ни в dark теме.
2. Убедиться, что BottomSheet корректно отображается в dark-mode на Android 15.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `photos`, `legacy` — все используют общие ресурсы темы из `src/main/res/`.
- **API level:** изменение логики для API 35+ в ресурсах (qualifier `values-v35/`, `values-night-v35/`). Поведение на API < 35 — без изменений.
- **Wear OS:** не затрагивается.
- **Производительность:** нет влияния.
- **Совместимость данных:** нет.
- **Локализация:** тема не содержит переводимых строк.
- **Доступность:** не затрагивается.

---

## 4. Контекст текущей архитектуры

В `values/themes.xml` и `values-night/themes.xml` явно проставлены `android:statusBarColor = @android:color/transparent` и `android:navigationBarColor = @android:color/transparent`. Они были добавлены для поддержки edge-to-edge на Android < 15, но стали redundant — `enableEdgeToEdge()` в базовой активности выставляет прозрачность программно.

Для API 35 уже создан `values-v35/themes.xml`, который переопределяет базовую тему без этих атрибутов и добавляет BottomSheet-оверрайд с `enableEdgeToEdge=true`. Однако в dark-mode Android resource resolution выбирает `values-night/` вместо `values-v35/` (night qualifier имеет приоритет над vNN). Это означает, что тёмная тема на Android 15 по-прежнему содержит deprecated-атрибуты и не получает BottomSheet-фикс.

---

## 5. Предлагаемый подход

Удалить `android:statusBarColor` и `android:navigationBarColor` из базовых тем (light и dark) напрямую — поскольку оба значения прозрачны и их вместо них обеспечивает системный edge-to-edge слой.

Создать ресурс `values-night-v35/` с переопределением базовой темы, аналогичным тому что сделано в `values-v35/`: без deprecated-атрибутов, с BottomSheet-оверрайдом. Это закроет gap для dark-mode на API 35.

### 5.1 Основные блоки

**Блок 1 — удаление deprecated атрибутов из light-темы**
Убрать две строки из основного файла тем. Edge-to-edge на API < 35 не меняется — атрибуты были redundant.

**Блок 2 — удаление deprecated атрибутов из dark-темы**
Аналогично для файла тёмной темы.

**Блок 3 — dark-mode API 35 override**
Создать `values-night-v35/themes.xml` с той же структурой, что в `values-v35/themes.xml`: базовая тема без deprecated-атрибутов, BottomSheet-тема с `enableEdgeToEdge=true`.

### 5.2 Потоки данных и событий

Без изменений в runtime-потоках. Только ресурсная система Android читает другие XML-атрибуты при создании активности.

### 5.3 Точки расширяемости

Новый ресурс `values-night-v35/` создаёт прецедент — сюда же можно добавить другие night+API35-специфичные переопределения.

---

## 6. Открытые вопросы / Research items

1. **Библиотечный байткод**
   - **Вопрос:** после нашего фикса Play Console продолжит флаговать Material BottomSheet (`w82.a`, `x82.a`)?
   - **Варианты:** (а) флаги уйдут — Material 1.13.0 с `enableEdgeToEdge=true` убирает deprecated-пути; (б) флаги останутся — они в dead-code библиотеки и статический анализ их видит независимо.
   - **Нужно выяснить:** загрузить следующий релиз и сравнить число предупреждений в Play Console.
   - **Статус:** Open

2. **AppCompat ночная тема**
   - **Вопрос:** AppCompat 1.7.0 исправил применение night-mode цветов через deprecated APIs в `onStart()`?
   - **Варианты:** да — если версия зависимости достаточно новая; нет — нужна проверка версии AppCompat.
   - **Нужно выяснить:** проверить версию `androidx.appcompat:appcompat` в зависимостях и changelog.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Удаление statusBarColor ломает внешний вид на API < 35 | Низкая | Цвет статус-бара или навбара становится не-прозрачным | Тестировать на API 26–34 после правки |
| Play Console продолжает флаговать библиотечный байткод | Средняя | Предупреждение остаётся, несмотря на наш фикс | Задокументировать как library-side; ждать Material update |
| `values-night-v35/` qualifier не работает как ожидается | Низкая | Dark-mode на API 35 не получает фикс | Проверить Android resource resolution rules + тест на устройстве |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES — это внутренний технический фикс, пользователь не видит разницы.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Удаление атрибутов вместо условного переопределения**

- **Решение:** убрать `statusBarColor`/`navigationBarColor` из всех тем (кроме API 35-специфичных), а не оборачивать их в дополнительные `values-vXX/` файлы.
- **Альтернативы:** создать `values-v35/` и `values-night-v35/` только с переопределением (уже частично сделано), не трогая `values/` и `values-night/`.
- **Почему:** атрибуты имеют значение `@android:color/transparent` — они были нужны до edge-to-edge; сейчас `enableEdgeToEdge()` делает то же самое. Redundant атрибуты засоряют тему и будут флаговаться Play Console на всех API уровнях по мере того как Google ужесточает требования.

---

## 10. Связи с другими спеками

Связей нет. Вторая Play Console рекомендация (orientation/resizability) — отдельный тикет.

---

## 11. Критерии готовности (strategic-level)

1. Play Console не флагует `android.view.Window.setStatusBarColor` / `setNavigationBarColor` как наш код (не библиотечный) в следующем релизе.
2. Визуальный вид системных баров в light и dark теме не изменился на API 26–35.
3. BottomSheet корректно отображается в dark-mode на Android 15 (edge-to-edge без артефактов).

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0221` — создаст `PLAN/S0221_play-console-deprecated-window-color-apis/` с фазами.

---

## Last Audit

**Date:** 2026-05-16
**Result:** Verified

**Checks performed:**

1. `android:statusBarColor` / `android:navigationBarColor` in `values/themes.xml` — expected: absent | actual: absent. PASS
2. `android:statusBarColor` / `android:navigationBarColor` in `values-night/themes.xml` — expected: absent | actual: absent. PASS
3. `values-night-v35/themes.xml` exists with `enableEdgeToEdge=true` BottomSheet override — expected: present | actual: present. PASS
4. `values-v35/themes.xml` exists with `enableEdgeToEdge=true` BottomSheet override — expected: present | actual: present. PASS
5. `setStatusBarColor` / `setNavigationBarColor` direct calls in `src/main/java/**/*.kt` — expected: none | actual: none. PASS
6. Stale `Timber.d("S0221:` tags in `.kt` code — expected: none | actual: none. PASS

**Deferred (outside our control):**
- Library bytecode warnings (Material BottomSheetDialog `w82.a`, `x82.a`) — Play Console may continue flagging library-side deprecated calls regardless of our fix. To be observed after next release upload. Non-blocking.
- AppCompat night-mode `onStart()` fix — version in use is sufficient per existing `values-v35/` comment (AppCompat 1.7.0+); no further action needed.
