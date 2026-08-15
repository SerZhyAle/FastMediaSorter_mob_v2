---
ticket: S0371
status: Partial
priority: 50
date: 2026-06-06
tier: 3
---

# Стратегическая спецификация: S0371 - Запись видео в ресурс

**Ticket:** S0371
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-06
**Tier:** 3 - Moderate, cross-surface capture parity
**Roadmap entry:** Ad-hoc - запрос 2026-06-06: «Запись видео» - исследовать все участки, где сейчас сделан photo-to-resource (настройки, права, вызов, виджет), и определить аналогичную feature surface для video recording в текущий ресурс; без post-capture editing, с возможным open-in-player.
**Tactical spec:** `PLAN/S0371_video-recording-to-resource/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, Room-миграций и Hilt-деталей.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - создать стратегическую спецификацию для записи видео в текущий ресурс по аналогии с photo-to-resource после аудита всех связанных поверхностей: настройки, права, вызов, widget, post-capture outcome.
- **Local anchor:** Provided by user - существующий photo-to-resource feature surface: settings, permissions, invoke flow, widget.
- **Scope boundaries / forbidden areas:** Provided by user - в этой задаче создать только specification; реализация, билд и user-facing правки вне текущего объёма.
- **Done / success signal:** Provided by user - создан новый specification task, который фиксирует исследование текущего photo-to-resource контракта и описывает целевую стратегию для video recording.
- **Autonomy rule:** agent may decide with explicit assumptions (granted by owner via /goal directive 2026-06-06).
- **UI decisions / delegation:** Resolved by owner (2026-06-06) - выбрана FULL реализация parity по всем surfaces (settings, permissions, browse command, widget, resource naming, post-capture); capture использует существующую системную ветку `ACTION_VIDEO_CAPTURE` (Path A); после записи редактор не открывается; open-in-player предлагается опционально и управляется отдельной настройкой (по умолчанию выключено); точные placement, ordering и widget shape делегированы тактической фазе в рамках выбранного scope.

Owner gate закрыт: все строки заполнены, `MISSING - requires owner input` не осталось.

---

## 1. Проблема

Photo-to-resource в продукте уже существует как полноценный feature surface: у него есть настройки, permission-copy, browse-side вызов, home-screen entry points и понятный post-capture результат. Видео в текущий ресурс при этом остаётся неполным и слабо определённым сценарием.

Локальный аудит показывает, что запись видео уже присутствует как частичный runtime branch внутри capture flow, но она не оформлена как самостоятельная пользовательская функция. В результате вокруг неё нет полного продуктового контракта: не закреплены настройки, не определено итоговое поведение после записи, нет явной widget-story, а naming и discoverability всё ещё опираются на photo-centric модель.

Дополнительная проблема в том, что существующий camera resource уже охватывает материалы камеры шире, чем это видно из названий и entry points. Это создаёт расхождение между фактическим содержимым camera surface и тем, как он описан пользователю.

Эффект для UX:

1. Пользователь может получить запись видео в ресурс только как побочный сценарий текущей capture logic, а не как явно описанную возможность.
2. Настройки и permission expectations для photo capture не образуют завершённую аналогию для video recording.
3. Widget и resource naming закрепляют модель «фото», хотя стратегически речь уже идёт о camera media.
4. Финальный handoff после записи видео не определён: редактирование не требуется, но policy для быстрого открытия в player пока не зафиксирована.

---

## 2. Цели

1. Зафиксировать полный аудит всех user-visible поверхностей, где сегодня живёт photo-to-resource.
2. Описать `Запись видео в ресурс` как явную пользовательскую возможность, а не как скрытый побочный branch.
3. Определить, какие surface areas обязаны получить parity для video recording: settings, permissions, browse command, widgets, resource naming, post-capture behaviour.
4. Зафиксировать правило, что после записи видео не открывается редактор.
5. Подготовить решение для optional player handoff после успешной записи без навязывания этого поведения по умолчанию, пока owner не утвердит policy.
6. Синхронизировать будущую video feature surface с текущей перегруппировкой camera/microphone settings, чтобы не породить вторую параллельную IA.

**Non-goals:**

- реализация capture flow в рамках текущего тикета;
- добавление встроенного видеоредактора или любой post-capture editing surface;
- redesign Camera OCR, drawing editor или microphone recording вне общих точек пересечения;
- изменение Wear OS;
- изменение Room schema, Hilt wiring или low-level camera stack в стратегическом документе.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Нужно исследовать все места, где photo-to-resource уже оформлен как feature surface.
2. Для video recording нужно создать аналогичную стратегию, а не точечный runtime patch.
3. После записи видео не нужен edit flow в конце.
4. Допускается вариант, при котором пользователь может захотеть открыть записанное видео в проигрывателе.

### 3.2 Жёсткие ограничения

- **Spec only:** текущая задача заканчивается созданием Draft-спеки, без тактической декомпозиции и без кода.
- **Parity audit:** тактика не может ограничиться только browse launch. Нужно покрыть все product surfaces, где photo capture уже представлен пользователю.
- **No editor handoff:** никакой автоматический переход в drawing/editor surface для video recording недопустим.
- **Copy policy:** все новые или изменённые пользовательские строки обязаны соответствовать `docs/COMMUNICATION_POLICY.md` и пройти EN/RU/UK parity.
- **Accessibility and orientation:** если future implementation затронет settings, widgets или browse UI, portrait/landscape, D-pad, keyboard и mouse parity должны остаться согласованными.
- **Photo stability:** существующие photo-to-resource, Camera OCR и microphone-related сценарии не должны ухудшиться из-за введения video parity.

### 3.3 Owner inputs (Approval gate)

- **Implementation scope:** FULL parity по всем photo-to-resource surfaces - settings, permissions, browse command, widget, resource naming, post-capture outcome; частичная реализация одного surface не считается завершением.
- **Capture mechanism:** Path A - переиспользовать существующую системную ветку `ACTION_VIDEO_CAPTURE`; не вводить отдельный кастомный video capture stack в рамках этой работы.
- **Post-capture contract:** после записи видео editor handoff запрещён; open-in-player реализуется как optional flow за отдельной настройкой, по умолчанию выключенной, без принудительного auto-open.
- **Settings IA dependency:** video settings размещаются внутри обновлённой playback-side capture IA из S0367; реализация зависит от утверждения этой IA либо объединяет оба settings-решения в один тактический пакет, чтобы не породить вторую параллельную архитектуру.
- **Permission contract:** video recording оформляется с понятной пользователю permission-story (camera, плюс microphone-ожидание для записи со звуком); конкретная формулировка проходит product + platform review в тактической фазе.
- **Resource naming:** тактическая фаза явно решает, переиспользуется ли существующий camera resource, переименовывается ли он в более широкое camera-media понятие, или создаётся отдельная video surface; photo-only naming не сохраняется молча при добавлении video support.
- **Accessibility and orientation:** затронутые settings, widget и browse UI сохраняют portrait/landscape, touch, keyboard, D-pad и mouse parity.
- **Communication policy:** все новые или изменённые строки (action label, settings, widget label, permission copy) проходят `docs/COMMUNICATION_POLICY.md` (§6) и EN/RU/UK parity.
- **Validation level:** целевые варианты сборки компилируются; затронутые unit-тесты проходят; ручная проверка на устройстве для записи видео в выбранный ресурс, отсутствия editor handoff и опционального player-open.
- **Related tickets:** S0367 (settings IA, на которую опирается video capture grouping). Зависимостей-блокеров нет, но settings-часть синхронизируется с S0367.

---

## 4. Контекст текущей архитектуры

Текущее состояние, установленное локальным аудитом, выглядит так:

1. Photo-to-resource уже закреплён как отдельный пользовательский блок настроек с управлением видимостью команды, rename-поведения и post-capture handoff.
2. Runtime capture flow уже умеет отличать photo capture от video recording в зависимости от характера текущего ресурса, но эта развилка не оформлена как полноценный продуктовый контракт.
3. Permission story сегодня разделена: camera capture имеет собственную permission-copy и runtime запрос, а microphone flows живут как отдельная область. Для video recording единый продуктовый policy ещё не сформулирован.
4. Существуют home-screen entry points, завязанные на camera resource и photo-centric naming, включая отдельный widget и комбинированную capture/OCR surface.
5. Camera resource уже охватывает материалы камеры шире, чем это следует из его видимого названия: в описании ресурса речь идёт не только о фото.
6. В параллельной работе уже существует задача на regrouping camera/microphone settings, поэтому новая video story не должна проектироваться мимо этой IA.

Из этого следует, что задача про video recording не сводится к одной browse-команде. Это cross-surface feature definition, который должен привести в согласованное состояние naming, settings, widgets, permissions и completion behaviour.

---

## 5. Предлагаемый подход

### 5.1 Считать video recording first-class sibling feature

Запись видео в текущий ресурс должна рассматриваться как отдельная пользовательская возможность того же уровня, что и photo-to-resource, а не как скрытый branch существующего capture flow.

### 5.2 Использовать photo-to-resource как reference contract

Будущая тактика должна сначала перечислить все photo-centric surfaces, а затем для каждой из них явно решить одно из трёх состояний: parity required, parity not needed, shared surface with renamed semantics.

### 5.3 Принять явное решение по resource identity и naming

Нельзя одновременно оставлять photo-only названия и ожидать, что пользователь поймёт video support. Тактика должна зафиксировать, переиспользуется ли существующий camera resource, переименовывается ли он в более широкое camera-media понятие, или создаётся отдельная video-specific surface.

### 5.4 Развести post-capture outcomes для photo и video

У фото уже есть понятная story с rename и возможным handoff в editor. У видео должен быть собственный outcome contract: сохранить, показать в текущем ресурсе, и опционально дать быстрый переход в player без обязательного auto-open.

### 5.5 Рассматривать widgets как часть feature, а не как позднее дополнение

Если photo-to-resource уже имеет отдельные home-screen точки входа, видео нужно с самого начала оценивать на наличие dedicated widget, расширения текущей capture-panel surface или осознанного отказа от widget parity.

### 5.6 Сцепить задачу с финальной IA capture settings

Video settings нельзя проектировать в отрыве от текущего regrouping camera/microphone sections. Либо новая спека зависит от утверждения этой IA, либо future tactical plan объединяет оба изменения в один согласованный settings contract.

---

## 6. Открытые вопросы / Research items

1. **Resource model**
   - **Вопрос:** video recording использует существующий camera resource, требует его переименования, или нуждается в отдельной resource surface?
   - **Почему важно:** текущее photo-centric naming уже расходится с фактическим содержимым camera resource.
   - **Статус:** Open - owner/product decision required.

2. **Settings IA**
   - **Вопрос:** video recording добавляется в ту же capture section, что и фото, или получает отдельную подгруппу внутри обновлённого playback-side settings блока?
   - **Почему важно:** параллельная regrouping-спека уже меняет расположение camera/microphone settings.
   - **Статус:** Open - depends on S0367 owner gate.

3. **Browse visibility rule**
   - **Вопрос:** команду записи видео нужно показывать только для video-only resources или для любого ресурса, который допускает видео?
   - **Почему важно:** текущая runtime логика уже содержит implicit rule, но продуктовая формулировка этого правила отсутствует.
   - **Статус:** Open - tactical UX decision required.

4. **Permission contract**
   - **Вопрос:** какой permission story должна быть у video recording: только camera expectation, camera + optional microphone expectation, или иная формулировка?
   - **Почему важно:** photo и microphone permission surfaces сегодня разведены, а видео естественно затрагивает обе пользовательские ментальные модели.
   - **Статус:** Open - product + platform review required.

5. **Post-capture player handoff**
   - **Вопрос:** после успешной записи видео нужно ли предлагать открыть файл в player, и если да, то как именно: auto-open, optional toggle, snackbar action, dialog, one-shot prompt?
   - **Почему важно:** пользователь явно исключил editor flow, но оставил пространство для player-open сценария.
   - **Статус:** Open - owner decision required.

6. **Widget strategy**
   - **Вопрос:** нужна ли dedicated home-screen widget surface для video recording, расширение существующей capture-panel widget, или осознанный отказ от widget parity?
   - **Почему важно:** photo-to-resource уже имеет widget entry points, и отсутствие решения создаст asymmetry feature discovery.
   - **Статус:** Open - owner/product decision required.

7. **Naming across locales**
   - **Вопрос:** как именно должны называться video recording action, settings section, widget label и camera resource в EN/RU/UK, если продукт уйдёт от photo-only модели?
   - **Почему важно:** текущие photo-centric labels уже влияют на discoverability.
   - **Статус:** Open - tactical copy decision required.

8. **Filename policy**
   - **Вопрос:** нужна ли для видео та же rename-before-save модель, что и у фото, отдельный toggle, или фиксированное автоимя?
   - **Почему важно:** video capture может требовать иной баланс между speed and confirmation.
   - **Статус:** Open - tactical settings decision required.

---

## 7. Риски

- Если future implementation ограничится только browse runtime branch, видео останется скрытой capability без discoverability и завершённой UX-story.
- Если продукт сохранит photo-only naming для mixed camera media, пользовательская модель станет ещё менее понятной.
- Если video recording привяжут к settings IA до завершения regrouping capture sections, возникнет повторная миграция настроек.
- Если player-open policy останется неявной, post-capture outcome будет отличаться между устройствами и ожиданиями пользователя.
- Если widget story не будет решена явно, photo и video получат несбалансированные точки входа.
- Если permission contract будет сформулирован поверхностно, появится конфликт между ожиданием записи видео со звуком и фактическим поведением на разных устройствах.

---

## 8. Влияние на пользователя (docs/FEATURES)

Это потенциально новый end-user capability. Если tactical implementation утвердит и доставит явную video-recording surface, потребуется обновление `docs/FEATURES*.md` с описанием записи видео в текущий ресурс и post-capture поведения. Пока в рамках Draft-спеки изменения в feature inventory не выполняются.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Video recording to resource is an explicit feature, not a hidden branch**

- **Решение:** future work должна оформить запись видео как полноценную feature surface.
- **Альтернативы:** оставить всё как implicit runtime branch без parity по surface areas.
- **Почему:** без этого продукт продолжит расходиться между фактической возможностью и видимой моделью.

**ADR-2: Video completion never routes to editing**

- **Решение:** после записи видео не допускается editor handoff.
- **Альтернативы:** переиспользовать photo editing path или добавить отдельный editor.
- **Почему:** owner explicit request исключает edit flow для видео.

**ADR-3: Player handoff is optional policy, not mandatory default**

- **Решение:** возможный переход в player рассматривается как отдельное продуктовое решение, а не как жёсткий auto-open.
- **Альтернативы:** всегда открывать player; никогда не предлагать player.
- **Почему:** пользователь сформулировал этот сценарий как возможное желание, а не как обязательное поведение.

**ADR-4: Shared surfaces must be audited end-to-end before implementation**

- **Решение:** settings, permissions, browse invoke, widgets, resource naming и post-capture outcome рассматриваются как единый feature contract.
- **Альтернативы:** внедрять video parity по одному surface за раз без общей модели.
- **Почему:** photo-to-resource уже распределён по нескольким поверхностям, и частичная parity только усилит несогласованность.

---

## 10. Связи с другими спеками

- Напрямую связано с [S0367] как минимум по settings IA: новая video story не должна проектироваться мимо regrouping camera/microphone sections.
- Если S0367 останется в `Draft`, `/spec-tech` для S0371 должен либо зафиксировать зависимость, либо объединить settings decisions в один тактический пакет.
- По предметной области связано с существующим photo-to-resource и Camera OCR feature family, даже если для них нет активной strategic-спеки в текущем журнале.

---

## 11. Критерии готовности (strategic-level)

1. Тактическая фаза перечисляет все photo-to-resource product surfaces, которые являются reference contract для video parity: settings, permissions, browse command, resource naming, widgets, post-capture outcome.
2. `Запись видео в ресурс` определена как явная пользовательская возможность, а не скрытая реализация.
3. Утверждён resource model: reuse existing camera resource, rename to broader camera-media concept, или separate video surface.
4. Утверждён settings contract для video recording без конфликтов с capture regrouping work.
5. Утверждён permission contract с понятной пользовательской формулировкой ожидаемых доступов.
6. После записи видео нет editor handoff.
7. Player-open behaviour либо утверждён как explicit optional flow, либо явно отклонён; двусмысленность не остаётся.
8. Widget strategy для video recording либо утверждена, либо сознательно исключена с явным решением в тактической спеки.
9. Photo-to-resource, Camera OCR и связанные capture surfaces не деградируют из-за введения video parity.
10. EN/RU/UK naming и help copy проходят parity и соответствуют communication policy.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация будет создана через `/spec-tech S0371` после закрытия owner gate.

---

## Last Audit

- **Date:** 2026-06-06
- **Auditor:** Copilot via `/spec-all S0371`
- **Verdict:** Partial - implementation surface is present and the remaining local naming drift was fixed, but `standardDebug` verification is still blocked by a worktree-wide Android resource merge failure outside the edited `S0371` slice.
- **Confirmed against the spec:**
   - Browse exposes a dedicated `Record video` command and routes it through `ACTION_VIDEO_CAPTURE` with camera-permission gating.
   - Video completion reloads the resource, scrolls to the new file, never opens the drawing editor, and optionally opens the file in the player when the opt-in setting is enabled.
   - Playback settings contain a dedicated video-recording master toggle plus the opt-in `open in player` child option.
   - Home-screen quick-capture widget supports photo/video mode selection per instance and persists that mode in widget prefs.
- **Audit fixes applied in this session:**
   - Removed the temporary `Timber.d("S0371: ..")` debug probe while resuming from `BlockNeedUserTest` to `Implemented`.
   - Renamed the quick-capture widget copy in EN/RU/UK from photo-only wording to photo-or-video wording.
   - Aligned `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` with the new quick-capture widget label.
- **Validation log:**
   - `grep Timber\.d\("S0371:` -> PASS (no remaining debug verification tags)
   - `get_errors` on touched Kotlin/XML/docs -> PASS
   - `./gradlew.bat assembleStandardDebug` -> FAIL (`:app_v2:hiltJavaCompileStandardDebug`, missing `FastMediaSorterApp_ComponentTreeDeps` class file)
   - `./gradlew.bat clean :app_v2:assembleStandardDebug` -> FAIL (`:app_v2:mergeStandardDebugResources`, `NoSuchFileException` / file-lock churn in `app_v2/build/intermediates/...`)
   - `scripts/builders/clean-gradle-caches.ps1` + `./gradlew.bat --no-daemon --no-build-cache :app_v2:assembleStandardDebug` -> FAIL (`:app_v2:mergeStandardDebugResources`, missing generated `merged.dir/values*.xml` paths)
- **Open blocker:** `standardDebug` cannot currently complete because the branch/worktree is in an unstable Android resource-merge state unrelated to the touched `S0371` files. The edited files themselves report no IDE errors.
- **Next step to reach `Verified`:** stabilize the shared `standardDebug` resource merge/build environment, rerun the build gate, then rerun `/spec-check S0371`.

---

## Revision History

- **2026-06-06** - created by Copilot via `/spec`
  - Added strategic draft for video recording to current resource based on the audited photo-to-resource surfaces: settings, permissions, browse invocation, widgets, resource naming, and post-capture behaviour.