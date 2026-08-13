# Стратегическая спецификация: S0653 - v35 FullScreen theme shadowed (window attrs dropped on API 35)

**Ticket:** S0653
**Status:** Archived
**Priority:** 25
**Date:** 2026-06-23
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-захват из ревью edge-to-edge (2026-06-23)
**Tactical spec:** `PLAN/S0653_bugfix-v35-fullscreen-theme-shadowed/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-23

**Захвачено во время:** работа по откату Android 15 edge-to-edge (без активного Sxxxx; /quick-класс задача).

**Текст:**

Symptom: On API 35 devices, Theme.FastMediaSorter.FullScreen silently loses windowActionBar=false / windowNoTitle=true / android:windowFullscreen=true.

Cause: app_v2/src/main/res/values-v35/themes.xml and values-night-v35/themes.xml redefine the styles `Theme.FastMediaSorter` and `Theme.FastMediaSorter.FullScreen` with EMPTY bodies (only parent=), and Android resolves each style name to a single best-match qualifier bucket without merging across folders - so on API 35 the empty v35 FullScreen shadows the full values/themes.xml definition (lines ~207-211) and its three window items are dropped.

Affected activities (AndroidManifest): PlayerActivity + the five StandalonePlayer/*StandaloneActivity hosts use Theme.FastMediaSorter.FullScreen.

Evidence: confirmed via adversarial review (dimension theme-resource-correctness) - mechanism verified against Android resource-resolution docs; values/ and values-night/ buckets carry the full FullScreen definition, only the v35/night-v35 buckets are empty.

Real-world impact assessed as near-zero (windowFullscreen is the legacy FLAG_FULLSCREEN, deprecated since API 30 and inert under Android-15 edge-to-edge enforcement where content already draws behind a transparent status bar; windowActionBar/windowNoTitle are redundant because the App parent is *.NoActionBar; true immersive bar-hiding is driven at runtime by WindowInsetsController in StandaloneFullscreenManager/PlayerImmersiveModeManager) - hence low priority, but it is a genuine unintended theme-resource-correctness defect.

Suggested fix: delete the empty `Theme.FastMediaSorter` and `Theme.FastMediaSorter.FullScreen` redefinitions from both values-v35 and values-night-v35 themes.xml so API 35 falls back to the values/ definitions (parent chain still resolves Base->App per device config, preserving the v35 bottomSheet override and bar behavior); then verify on an API-35 emulator that PlayerActivity and the standalone hosts still open fullscreen.

Pre-existing (not introduced by the 2026-06-23 enableEdgeToEdge revert). No existing catalog ticket (dedup-searched FullScreen/windowFullscreen/v35 - none).

**Вложения:**

Вложений нет.

---

## 1. Проблема

На устройствах API 35 тема `Theme.FastMediaSorter.FullScreen` молча теряла `windowActionBar=false`, `windowNoTitle=true`, `android:windowFullscreen=true`. Оверлеи `values-v35` и `values-night-v35` переопределяли стиль FullScreen пустым телом, а Android резолвит имя стиля в один best-match bucket по квалификаторам без слияния между папками - поэтому пустое v35-переопределение затеняло полное определение из `values/` и роняло три window-item'а. Затронуты полноэкранные плеер-хосты (PlayerActivity и отдельные standalone-плеер-активности). Реальный эффект близок к нулю (на API 35 эти три атрибута инертны/избыточны под системным edge-to-edge), но это подлинный дефект корректности темо-ресурсов.

---

## 2. Цели

1. На API 35 (день и ночь) `Theme.FastMediaSorter.FullScreen` снова несёт `windowActionBar=false`, `windowNoTitle=true`, `android:windowFullscreen=true` через откат к `values/` + `values-night/`.
2. v35/night-v35 оверрайд `Theme.FastMediaSorter.Base` (bottomSheet / edge-to-edge) и поведение системных баров остаются нетронутыми.
3. Никаких сторонних изменений в других темо-эффектах на API 35.

**Non-goals:**

- Восстановление splash-screen/contrast-оверрайдов на API 35 (пустой промежуточный `Theme.FastMediaSorter` в v35 затеняет v31-splash - отдельная находка, запаркована).
- Рантайм-логика иммерсивного режима (по-прежнему ведётся через `WindowInsetsController`).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S0498 (Material 1.14.0 statusbar deprecation, archived), S0221 (deprecated window color apis, archived); same theme family `Theme.FastMediaSorter.*`.

---

## 4. Контекст текущей архитектуры

<1-2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Удаление пустого `Theme.FastMediaSorter` (как предлагал §0) подтянуло бы v31/night-v31/night-v29 splash/contrast-оверрайды на API 35 | Средняя | Изменение splash-экрана на API 35 вне объёма тикета | Удалён только пустой `Theme.FastMediaSorter.FullScreen`; промежуточный `Theme.FastMediaSorter` оставлен нетронутым |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

1. Сборка `processStandardDebugResources` проходит после удаления v35/night-v35 переопределений FullScreen.
2. На API 35 PlayerActivity и standalone-плеер-хосты открываются без action bar / заголовка и в полноэкранном режиме - идентично pre-35 устройствам.

---

## 12. Ссылка на тактическую спецификацию

Тактическая декомпозиция не потребовалась: фикс - удаление двух XML-строк. Реализован напрямую (Tier 2 ad-hoc).

---

## 13. Реализация (2026-06-23)

**Изменение:** удалено пустое переопределение `Theme.FastMediaSorter.FullScreen` из `app_v2/src/main/res/values-v35/themes.xml` и `values-night-v35/themes.xml`. На его месте - комментарий с инвариантом резолюции. Промежуточный `Theme.FastMediaSorter` и оверрайд `Theme.FastMediaSorter.Base` (bottomSheet) в обоих файлах оставлены нетронутыми.

**Отклонение от §0 (suggested fix):** §0 предлагал удалить из v35/night-v35 *оба* пустых стиля (`Theme.FastMediaSorter` и `Theme.FastMediaSorter.FullScreen`). Адверсариальная проверка перед правкой показала, что §0 не учёл: промежуточный `Theme.FastMediaSorter` переопределён ещё и в `values-v31` / `values-night-v31` (splash-screen items) и `values-night-v29` (bar-contrast items). Удаление пустого `Theme.FastMediaSorter` из v35 заставило бы API 35 подтянуть эти v31-оверрайды - изменение splash-экрана вне объёма S0653. Поэтому удалён только FullScreen, а промежуточный стиль сохранён, что даёт нулевой побочный эффект.

**Побочная находка (запаркована):** пустой `Theme.FastMediaSorter` в v35/night-v35 затеняет v31-splash-оверрайды на API 35 - вероятно латентный дефект (кастомный splash теряется на API 35). Вне объёма; см. parked-тикет в §10.

**Верификация:** `processStandardDebugResources` - BUILD SUCCESSFUL (merge/process ресурсов резолвит цепочку стилей без ошибок). Поведенческого различия на устройстве не ожидается: три window-item'а инертны на API 35 под системным edge-to-edge; правка лишь возвращает API 35 к тому же определению `values/`, что используют все pre-35 устройства.

## Last Audit

Verified 2026-06-24 (static resource-correctness audit; on-device check carries no signal here - the three window items are inert on API >= 35 under system edge-to-edge, and the attached emulator is API 37).

- **Code matches §13.** `app_v2/src/main/res/values-v35/themes.xml` and `values-night-v35/themes.xml` no longer redefine `Theme.FastMediaSorter.FullScreen`; in its place is the S0653 invariant comment (do-not-redefine, explains the single-best-match-bucket shadowing). The intermediate `Theme.FastMediaSorter` and the `Theme.FastMediaSorter.Base` (bottomSheet / edge-to-edge) override are intact, so API 35 resolves FullScreen from `values/` (carrying windowActionBar=false / windowNoTitle=true / android:windowFullscreen=true) while the Base override still wins per device config.
- **Acceptance §11.1:** `:app_v2:processStandardDebugResources` -> BUILD SUCCESSFUL (re-confirmed this run). §11.2 (fullscreen hosts resolve identically to pre-35) is satisfied by the corrected resolution chain.
- **Parked side-finding resolved.** The §13 splash shadowing note (empty intermediate `Theme.FastMediaSorter` shadowing the values-v31 splash override on API 35) was ticketed and fixed as **S0655** (Verified, `bugfix-v35-splash-theme-shadowed`) - its do-not-redefine comment now sits beside the S0653 one in both files. No open loose ends.

No `Timber.d("S0653:")` tags (resource-only fix, never entered BlockNeedUserTest). No `docs/FEATURES` / `ALL_FEATURES` impact (§8: без изменений).
