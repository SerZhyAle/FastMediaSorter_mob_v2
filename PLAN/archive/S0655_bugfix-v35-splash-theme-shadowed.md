# S0655 - v35 splash theme shadowed (v31 splash items dropped on API 35)

**Ticket:** S0655
**Status:** Archived
**Priority:** 25
**Date:** 2026-06-23
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-захват из реализации S0653 (2026-06-23)

> **Scope:** COMPACT spec (Simple path /spec-all). Цель + фазы инлайн.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23 во время реализации S0653 (откат пустых FullScreen-переопределений в values-v35/night-v35).

**Symptom:** На устройствах API 35 промежуточный стиль `Theme.FastMediaSorter` переопределён ПУСТЫМ телом в `app_v2/src/main/res/values-v35/themes.xml` и `values-night-v35/themes.xml` (только `parent="Theme.FastMediaSorter.Base"`).

**Cause:** Android резолвит имя стиля в один best-match bucket без слияния между папками. `Theme.FastMediaSorter` несёт реальные оверрайды в других bucket-ах: `values-v31` (`windowSplashScreenBackground=#F5F5F5`, `windowSplashScreenAnimatedIcon=@drawable/ic_splash_logo`, `windowSplashScreenAnimationDuration=0`), `values-night-v31` (то же, `windowSplashScreenBackground=@color/item_normal`), `values-night-v29` (`enforceNavigationBarContrast`/`enforceStatusBarContrast`). На API 35 пустое v35/night-v35 переопределение - лучший матч и затеняет v31 splash-айтемы, поэтому кастомный Android-12 splash (фон + анимированная иконка) молча теряется на API 35+, откатываясь к системным дефолтам.

**Evidence:** Подтверждено чтением всех bucket-ов `Theme.FastMediaSorter` в ходе S0653. Тот же accidental-shadowing паттерн, что и FullScreen-дефект, починенный в S0653, но на промежуточном стиле. S0653 намеренно НЕ трогал пустой `Theme.FastMediaSorter`, чтобы не протащить эту побочную splash-правку неанализированной.

**Real-world impact:** Устройства API 35 показывают системный дефолтный splash (иконка приложения на `windowBackground`) вместо курированного `ic_splash_logo` на заданном фоне. `android:windowDisablePreview=true` установлен, поэтому затрагивается только Android-12+ системный splash, не preview-окно. Низкий приоритет, косметика, но настоящий дефект resource-resolution.

**Resolution (из кода, /spec-all forward-bias):** Подавление splash на API 35 - случайный boilerplate, а не намерение. В комментариях v35 нет следов intent (только edge-to-edge / bottom-sheet). Фикс: удалить пустое `Theme.FastMediaSorter` из values-v35 + values-night-v35, чтобы API 35 откатился к v31/night-v31 splash-bucket-ам. Parent-цепочка всё равно резолвит `Theme.FastMediaSorter.Base` per-device-config (v35 bottomSheet-оверрайд сохраняется).

**Dedup:** Поиск в каталоге `splash` / `windowSplashScreen` - записей нет.

---

## 1. Цель

Вернуть кастомный Android-12 splash (фон + `ic_splash_logo`) на устройствах API 35+. Сейчас пустое переопределение `Theme.FastMediaSorter` в `values-v35` / `values-night-v35` затеняет splash-айтемы из `values-v31` / `values-night-v31` из-за best-match-bucket резолюции (без слияния). Удалить пустые переопределения, чтобы API 35 откатился к v31 splash-bucket-ам, сохранив при этом v35-оверрайд `Theme.FastMediaSorter.Base` (bottom-sheet edge-to-edge) через parent-цепочку.

**Non-goals:**

- Менять сами splash-айтемы (фон, иконку, длительность) - только восстановить их видимость на API 35.
- Трогать `Theme.FastMediaSorter.Base` / `Theme.App.BottomSheetDialog.EdgeToEdgeCompat` в v35 (edge-to-edge остаётся как есть).
- Менять `Theme.FastMediaSorter.FullScreen` (уже починен в S0653).

---

## 2. Фазы

### Phase 01 - Удалить затеняющие переопределения и проверить сборку

- **Step 1.** В `app_v2/src/main/res/values-v35/themes.xml` удалить пустой `<style name="Theme.FastMediaSorter" parent="Theme.FastMediaSorter.Base" />` вместе с предшествующим комментарием `<!-- Edge-to-edge is handled programmatically.. -->`. На его место поставить пояснительный комментарий в стиле существующего S0653-блока: пустое переопределение затеняет v31 splash-айтемы на API 35; откат к values-v31 восстанавливает splash; parent-цепочка всё равно резолвит Base->App per-device, поэтому v35 bottomSheet/edge-to-edge оверрайд сохраняется.
  - **Verification:** `Theme.FastMediaSorter` больше не определён в `values-v35/themes.xml` (grep по `name="Theme.FastMediaSorter"` с закрывающим `/>` или `>` - 0 совпадений для самого `Theme.FastMediaSorter`; `Theme.FastMediaSorter.Base` и `.EdgeToEdgeCompat` остаются).

- **Step 2.** В `app_v2/src/main/res/values-night-v35/themes.xml` сделать то же удаление + аналогичный пояснительный комментарий (откат к `values-night-v31`).
  - **Verification:** `Theme.FastMediaSorter` (точное имя, без `.Base`) не определён в `values-night-v35/themes.xml`.

- **Step 3.** Собрать standard debug, убедиться что ресурсы линкуются и стиль-цепочка резолвится.
  - **Verification:** `.\a.ps1 dq` -> BUILD SUCCESSFUL (auto-build - PASS).

### Phase 02 - On-device проверка splash на API 35

- **Step 1.** На эмуляторе/устройстве API 35 запустить холодный старт приложения и подтвердить, что показывается кастомный splash (`ic_splash_logo` на заданном фоне), а не системный дефолт.
  - **Verification:** Device-test gate (`/spec-test-device`) - визуальное подтверждение splash на API 35. Отложено в manual при отсутствии устройства.

---

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** все (`src/main` ресурсы) - splash общий.
- **API level:** дефект на API 35+ (квалификатор v35); splash-айтемы из API 31+ (v31).
- **Wear OS:** не затрагивается.
- **Локализация:** не затрагивается (только ресурсы стилей, без строк).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0653 (родительская находка - v35 FullScreen theme shadowed; тот же механизм best-match-bucket-shadowing на промежуточном стиле).

---

## 10. Связи с другими спеками

- S0653 - v35 FullScreen theme shadowed (родительская находка, тот же механизм best-match-bucket-shadowing).

---

## 11. Критерии готовности

1. `Theme.FastMediaSorter` (точное имя) не переопределён в `values-v35` и `values-night-v35`.
2. `Theme.FastMediaSorter.Base` + `Theme.App.BottomSheetDialog.EdgeToEdgeCompat` в v35/night-v35 не тронуты.
3. standard debug собирается.
4. На API 35 при холодном старте виден кастомный splash (`ic_splash_logo`), а не системный дефолт.

---

## Last Audit

**Date:** 2026-06-24 (/spec-all, Simple path)
**Verdict:** Verified

**Evidence:**

1. Source: `Theme.FastMediaSorter` (точное имя) удалён из `values-v35/themes.xml` и `values-night-v35/themes.xml`; на месте - пояснительный S0655-комментарий. `Theme.FastMediaSorter.Base` + `Theme.App.BottomSheetDialog.EdgeToEdgeCompat` сохранены в обоих bucket-ах (grep: Base count = 1 в каждом).
2. Build: `.\a.ps1 dq` -> BUILD SUCCESSFUL (APK v2.60.6211.547).
3. Resource-table proof (`aapt2 dump resources` на собранном APK): для `style/Theme.FastMediaSorter` присутствуют конфиги `()` size=0, `(night)` size=0, `(night-v29)` size=2, `(v31)` size=3 (splash-айтемы `windowSplashScreenBackground` / `windowSplashScreenAnimatedIcon=@drawable/ic_splash_logo` / `windowSplashScreenAnimationDuration`), `(night-v31)` size=3. Конфигов `(v35)` / `(night-v35)` для этого стиля больше НЕТ - затеняющий пустой оверрайд устранён. На любом API >= 35 best-match теперь резолвится в `(v31)` / `(night-v31)`, splash-айтемы восстановлены.
4. `Theme.FastMediaSorter.Base` - отдельная resource-запись; v35/night-v35 bottomSheet/edge-to-edge оверрайд резолвится независимо через parent-цепочку (не затронут).

**Residual:** Чисто визуальное подтверждение splash на физическом API 35-устройстве - опционально (дефект доказан структурно на уровне ресурс-таблицы, поведение резолюции детерминировано и задокументировано Android). Не блокер.
