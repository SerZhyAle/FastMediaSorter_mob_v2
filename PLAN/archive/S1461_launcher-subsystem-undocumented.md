# S1461 - Подсистема лаунчера не описана ни в одном архитектурном документе

**Status:** Archived
**Priority:** 45
**Tier:** 3 - Moderate (ad-hoc)
**Date:** 2026-08-07

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07 при S1424.

**Текст:**

The launcher subsystem - 100+ classes under `ui/launcher/`, plus `domain/*/launcher/`, plus flavor-gated build wiring and its own `src/launcherEnabled` / `src/launcherDisabled` source sets - has zero mentions in `docs/ARCHITECTURE.md` and zero in `docs/FLAVOR_MATRIX.md`. `Grep "Launcher"` against both returns nothing. `docs/DOCUMENT_REGISTRY.jsonl` has no `launcher` product area at all across its 27 areas, so the mandatory document-registry loop routes launcher work to `architecture` and `ui`, and neither says anything about it.

That is out of line with how comparable subsystems are treated: VR/OpenXR is flavor-restricted in the same way and has its own ARCHITECTURE.md section. The launcher is more restricted than VR, not less - it needs a flavor that mounts `src/launcherEnabled` (standard, noLegal only) AND the user to hand the app the Android HOME role, which no code can take programmatically. Both constraints are load-bearing for anyone planning launcher work and both are currently discoverable only by reading `build.gradle.kts` and `LauncherRoleManager`.

Cost observed while researching S1424: the flavor gating and the HOME-role constraint had to be re-derived from the build file and the role manager, because no document states them. Every launcher ticket pays that again.

Scope: an ARCHITECTURE.md section for the launcher, a `launcher` row in the flavor matrix doc if the generator supports it, and a `launcher` product area in the document registry so the registry loop routes launcher work somewhere real.

**Поправка к захвату (2026-08-07):** одно утверждение неверно. `docs/FLAVOR_MATRIX.md:25` **содержит**
строку `SUPPORT_LAUNCHER` (`[+]` для standard и noLegal). Файл генерируется из `productFlavors`
скриптом `generate-flavor-matrix.ps1`, поэтому появился там сам. Треть исходного объёма уже выполнена;
остаются секция в `ARCHITECTURE.md` и продуктовая область в реестре.

**Дубликат:** S1484 заведён тем же днём при `/spec-quiz` с тем же симптомом и архивирован в пользу
этого тикета. Исследование, проведённое под тем номером, перенесено сюда.

---

## 1. Проблема

Восстановить устройство лаунчера можно только чтением примерно сорока файлов в четырёх пакетах плюс
одной архивной спеки. Эту цену платит каждый тикет лаунчера, а их сейчас пять живых.

Подсистема - 161 запись каталога. Для сравнения: у Immersive VR/OpenXR 94 записи и своя секция, у
Desktop Companion Config 20 записей и своя секция.

---

## 2. Цели

1. `docs/ARCHITECTURE.md` содержит секцию про лаунчер, устроенную как соседние секции подсистем.
2. Реестр документов имеет продуктовую область `launcher`, чтобы обязательный цикл реестра приводил
   работу по лаунчеру к чему-то существующему.
3. Секция не устаревает от пяти живущих тикетов - см. решение в §4.

**Non-goals:**

- Не описывать каждый класс: 161 запись в каталоге, каталог для того и есть.
- Не заводить строку в `docs/FLAVOR_MATRIX.md` - она уже есть и генерируется.
- Не чинить покрытие тестами - вынесено в S1498.

---

## 3. Объём и ограничения

### 3.1 Форма секции задаётся соседями, а не изобретается

Прочитаны обе модельные секции. Общее у них:

- Один плоский заголовок `##`, без вложенных, без диаграмм, без блоков кода.
- Имена классов в обратных кавычках внутри прозы, никогда таблицей.
- Флейворный гейт назван явно.
- Обе отказываются повторять то, что есть в другом месте: Desktop Companion прямо пишет «Do not
  restate the field list here - it drifts», VR закрывается строкой «Related specs» и отсылкой к
  каталогу классов.

Две допустимые подформы: список пунктов с жирным зачином (Desktop Companion, 12 строк) или несколько
плотных абзацев с жирным зачином (Immersive VR, 15 строк). Берётся вторая - у лаунчера больше связного
механизма, чем перечислимых свойств.

### 3.2 Ограничения

- `docs/ARCHITECTURE.md` ведётся на английском.
- Правка `docs/DOCUMENT_REGISTRY.jsonl` закрывается через `validate.ps1` и `generate.ps1`; сгенерированные
  `DOCS_MAP.md` и `sitemap.xml` руками не трогаются.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1424 - тикет, при исследовании которого пробел обнаружен; S1484 - дубликат,
  архивирован; S1498 - нулевое покрытие тестами того же пакета; S1178, S1421, S1422, S1428, S1441 -
  живые тикеты, правящие эту подсистему.
- **UI scope:** не затрагивается, тикет документарный.
- **Flavor scope:** описываемая подсистема живёт под `SUPPORT_LAUNCHER` (standard, noLegal). Новых
  флейворных флагов не вводится.
- **Data / permissions scope:** без изменений.

---

## 4. Решение по объёму: механизм описывается, экземпляры - нет

Пять живых тикетов правят именно эту подсистему: S1178 (в работе) добавляет четыре гаджета в тот самый
реестр, S1421 занимает верхнюю полосу, S1422 перегруппировывает настройки, S1428 добавляет группы
ярлыков, S1441 учит клетки показывать состояние радио. Секция, перечисляющая сегодняшние гаджеты и
состав трея, устареет в пределах жизни этого тикета.

Отсюда правило: **описывается механизм, не перечисляются экземпляры.** Реестр гаджетов описывается как
открытый на расширение, а какие гаджеты в нём сегодня - нет. Ровно так поступает секция про VR, которая
не перечисляет элементы HUD.

Что от этих тикетов не зависит и потому описывается: способ получения роли HOME, флейворный шов,
модель хранения с двумя независимыми раскладками ориентаций, ADR-9 (сетка - свой `ViewGroup`, не
`RecyclerView`), ADR-5 (только свои представления, никогда чужие `AppWidget`), единая воронка запуска
команды, правило подписки индикаторов трея.

---

## 5. Найдено попутно

- **KDoc интерфейса противоречит реализации.** `LauncherDesktopRepository.moveCell` в комментарии
  утверждает «Deliberately does NOT swap with the occupant», а `LauncherDesktopRepositoryImpl.moveCell`
  меняет местами клетки равного размера - решение владельца, помеченное в коде датой 2026-07-17.
  Секция обязана описать фактическое поведение, поэтому KDoc правится здесь же, иначе документ и
  комментарий разойдутся на глазах.
- **Реестр ADR живёт в архиве.** Десять пронумерованных решений подсистемы записаны только в
  `temp/done/S0404_android-launcher-mode-profiles.md`, а `temp/` - каталог для черновиков и артефактов.
  Ссылаться туда из постоянного документа нельзя, поэтому решения пересказываются в секции своими
  словами.
- Нулевое покрытие тестами `ui/launcher/**` вынесено в S1498.

---

## Phase 01 - Write the ARCHITECTURE.md section

**Status:** ✅ Done
**Steps done:** 2 / 2

### Steps

#### Step 01.1 - Add the Launcher Mode section

**Files:** `docs/ARCHITECTURE.md`

**Prompt for developer:**

> Add a `## Launcher Mode` section next to the other subsystem sections, following the Immersive VR shape: one intro paragraph, then bold-lead paragraphs for entry and gating, the flavor seam, the desktop model, the grid, gadgets, and the taskbar with its tray. Close with a Related-specs line pointing at the class catalog sector. Describe mechanism only - name no individual gadget and no tray indicator, per section 4.

**Why:**

The flavor gate and the HOME-role constraint are load-bearing for anyone planning launcher work, and both are currently recoverable only by reading the build file and the role manager - a cost every launcher ticket pays again.

**Verification:**

- `Grep` - `## Launcher Mode` matches once in `docs/ARCHITECTURE.md`.
- `Grep` - `SUPPORT_LAUNCHER`, `launcherEnabled` and `LauncherModeContract` each present in the section.
- `Grep` - no individual gadget class name (`WeatherGadget`, `ClockGadget`) appears in the section.

**Status:** `[x]` done

---

#### Step 01.2 - Correct the moveCell KDoc so it matches the implementation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt`

**Prompt for developer:**

> Rewrite the `moveCell` KDoc to state what the implementation does: a free target is taken directly, a blocker of equal footprint is swapped, and a blocker of different footprint refuses the move. Keep the existing reasoning about why an unequal swap is refused.

**Why:**

The new section describes the actual swap behaviour, so leaving the interface comment asserting the opposite would create the same class of contradiction this ticket exists to remove.

**Verification:**

- `Grep` - `does NOT swap` returns zero hits in that file.
- `.\a.ps1 fk` - BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase 02 - Route launcher work somewhere real in the document registry

**Status:** ✅ Done
**Depends on:** Phase 01
**Steps done:** 1 / 1

### Steps

#### Step 02.1 - Add the launcher product area

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`

**Prompt for developer:**

> Add `launcher` to the product areas of the `architecture` record, so the mandatory registry loop routes launcher work to the document that now describes it. Regenerate the derived views.

**Why:**

The registry loop is mandatory at task start, and a query for the launcher product area currently returns nothing, so the loop sends launcher work to records that say nothing about it.

**Verification:**

- `query.ps1 -ProductArea "launcher"` returns the `architecture` record.
- `validate.ps1` exit 0; `generate.ps1 -Check` exit 0.

**Status:** `[x]` done

---

## Last Audit (2026-08-07)

**Verdict:** реализовано.

- Секция `## Launcher Mode` добавлена перед «Performance & Resource Optimization», по форме секции
  Immersive VR: вводный абзац плюс шесть абзацев с жирным зачином и строка «Related specs».
- `query.ps1 -ProductArea "launcher"` возвращает запись `architecture` - предикат, с которого тикет и
  начался, выполняется.
- KDoc `moveCell` переписан на три исхода: свободное место, обмен равных, отказ. `.\a.ps1 fk` -
  BUILD SUCCESSFUL.
- `post-change -ScopeToFile` - PASS без единого совета.
- Соседи по записи `architecture` проверены: `FLAVOR_DEVELOPMENT_RULES.md` - документ правил, а не
  перечень швов, правки не требует; `TECH_STACK.md` и `V2_architecture_overview.md` про подсистемы не
  пишут вовсе. Правка признана не нуждающейся в соседях, реестр подтверждён явно.

**Что изменило план по ходу.** Исследование показало, что S1178 в работе прямо сейчас добавляет
гаджеты в тот реестр, который секция описывает, а четыре тикета из квиза правят трей, настройки, группы
ярлыков и клетки. Секция с перечнем сегодняшних гаджетов устарела бы за неделю. Отсюда правило §4 -
описывать механизм и не называть экземпляры. Предикат «ни одного имени гаджета в документе» внесён в
шаги именно поэтому и проверяется механически.

## Критерии приёмки

- Запрос реестра по продуктовой области `launcher` возвращает документ, который действительно описывает
  подсистему.
- Секция называет флейворный гейт и ограничение по роли HOME - две вещи, которые сейчас приходится
  выводить из файла сборки.
- Ни один класс гаджета и ни один индикатор трея не назван поимённо, поэтому пять живых тикетов не
  делают секцию неверной.
- KDoc `moveCell` и его реализация говорят одно и то же.
