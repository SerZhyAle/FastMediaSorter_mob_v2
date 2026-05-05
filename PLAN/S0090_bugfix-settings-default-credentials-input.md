# Стратегическая спецификация: S0090 — Bugfix: надёжный ввод Default User / Default Password в общих настройках

**Ticket:** S0090
**Status:** BlockByOtherTask
**Priority:** 75
**Date:** 2026-05-05
**Tier:** 1 — Quick Win
**Roadmap entry:** Ad-hoc — пользовательский баг-репорт 2026-05-05 по полям `Default User` / `Default Password`.
**Tactical spec:** `PLAN/S0090_bugfix-settings-default-credentials-input/INDEX.md`
**Current blocker:** unrelated unit-test compile failure in `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScannerTest.kt` prevents Phase 03 verification from completing.

> **Scope:** STRATEGIC. Цели, ограничения, UX-решения, риски и критерии готовности. Без детального implementation diff.

---

## 1. Проблема

В разделе `General` настроек, внутри блока `App Data`, поля `Default User` и `Default Password` не дают надёжно ввести текст: пользователь может тапнуть по зоне полей и не получить ни экранную клавиатуру, ни ввод с аппаратной клавиатуры. При этом аналогичный сценарий переименования файла работает корректно, что указывает не на системную проблему IME, а на локальный дефект маршрута фокуса, текстового ввода и/или интерактивной геометрии именно на экране настроек.

Проблема имеет пользовательский эффект, а не только диагностический: дефолтные сетевые учётные данные становятся фактически труднодоступными, а значит базовый сценарий настройки SMB/SFTP/FTP-ресурсов частично ломается. Дополнительный риск в том, что текущий экран сочетает несколько факторов сразу: сворачиваемую секцию `App Data`, activity-wide keyboard contract для surface `SETTINGS`, отличающиеся portrait/landscape-конфигурации поля и отсутствие явного "rename-like" паттерна получения фокуса.

---

## 2. Цели

1. Поля `Default User` и `Default Password` принимают текст после одного прямого взаимодействия с полем как в portrait, так и в landscape.
2. Экранная клавиатура открывается надёжно и предсказуемо, когда пользователь начинает редактирование этих полей.
3. Аппаратная клавиатура и editor-navigation не блокируются общим keyboard-shortcut слоем настроек, пока фокус находится в текстовом поле.
4. Секция `App Data` и строка с дефолтными учётными данными не создают ложного впечатления editable interaction при фактически закрытом или нефокусируемом состоянии.
5. Конфигурация полей в portrait и landscape унифицирована и следует каноническому Material-паттерну для текстового ввода.
6. Появляется регрессионное покрытие, которое ловит повторное нарушение фокуса, IME или keyboard-precedence в этом сценарии.

**Non-goals:**

- Переработка всего экрана `General` или всех секций настроек одной волной.
- Изменение формата хранения default credentials, их шифрования или бизнес-логики применения.
- Редизайн settings search overlay, вкладок настроек или глобальной темы.
- Изменение остальных editable-полей в настройках, если они не затронуты тем же дефектом.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление должно устранять корневую причину, а не маскировать симптом логированием или повторными ретраями IME.
2. Для ввода в settings нужно переиспользовать уже доказавший надёжность паттерн, аналогичный rename-dialog и другим рабочим текстовым сценариям в приложении.
3. После фикса поведение soft keyboard, hardware keyboard и focus navigation должно быть консистентным между portrait и landscape.
4. Если проблема складывается из нескольких локальных причин, спецификация должна закрывать их одним bugfix-пакетом, а не оставлять полуисправленное состояние.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `photos`, `legacy`.
- **API level:** поведение едино для `minSdk 23+`; отдельные API-forks не планируются.
- **Wear OS:** не затрагивается.
- **Производительность:** нельзя ухудшать время первого открытия Settings заметной дополнительной работой на main thread.
- **Совместимость данных:** сохранение значений default credentials остаётся совместимым с текущим форматом настроек.
- **Локализация:** новые видимые строки не требуются; если в ходе реализации появятся, обязателен sync EN/RU/UK.
- **Доступность:** текстовое поле, password toggle и help-иконка должны остаться независимыми accessibility-узлами; touch-target не деградирует.

### 3.3 UI/UX decision table

| Область | Решение |
|---|---|
| Portrait placement | Поля остаются в `General -> App Data` в текущем месте строки default credentials. |
| Landscape placement | Поля остаются в том же разделе и в той же строковой группе; portrait/landscape различаются только размерной политикой, а не смыслом размещения. |
| Overflow / top menu | Никакого переноса действия редактирования в overflow, top menu или отдельный диалог. Редактирование остаётся inline. |
| Visibility | Секция `App Data` остаётся collapsible, но при отсутствии сохранённого user-choice открывается по умолчанию. Сохранённый явный выбор пользователя продолжает уважаться. |
| Hidden vs disabled | Когда секция раскрыта, поля всегда доступны для взаимодействия; состояние "видно, но ввести нельзя" запрещено. |
| Empty state | Пустые значения `Default User` / `Default Password` допустимы и не требуют дополнительного confirmation flow. |
| Save trigger | Значение сохраняется не только на потере фокуса, но и на явном editor completion (`IME action done` / эквивалентный commit-path). |
| Help interaction | Help-иконка рядом с credentials остаётся отдельной clickable-zone и не должна перехватывать тап по editable области. |
| Accessibility | Фокус TalkBack последовательно проходит: label/input -> password toggle -> help. Дублирующихся hint/announcement быть не должно. |

---

## 4. Контекст текущей архитектуры

Экран настроек построен как tab-host activity с отдельными fragments и helper-слоем, который инициализирует UI-элементы по секциям. Блок `App Data` в `General` управляется как сворачиваемая секция с независимо сохранённым expanded-state. Поля default credentials живут внутри этой секции и завязаны на focus/blur lifecycle для показа IME и сохранения значения.

Параллельно у surface `SETTINGS` существует activity-wide keyboard contract для navigation и shortcuts. На других поверхностях приложения уже есть рабочий образец надёжного текстового ввода: отдельный rename-dialog, где фокус поля и открытие IME инициируются явно, а keyboard-layer не претендует на текстовые клавиши при активном `EditText`.

Следовательно, проблема не изолируется только layout'ом или только сохранением значения. Это cross-cutting bug между четырьмя слоями UI-поведения:

1. discoverability и default-state секции `App Data`,
2. интерактивная зона самой строки default credentials,
3. focus/IME acquisition contract,
4. precedence между text editor и surface-wide shortcuts.

---

## 5. Предлагаемый подход

Исправление вводится как единый bugfix для "text editing inside settings surface", но scoped только на строку default credentials в `General` и на правила keyboard-precedence для активного settings text field.

### 5.1 Основные столпы / модули

**A. Нормализация interaction contract у credentials row**

- editable area должна гарантированно приводить к фокусу соответствующего поля;
- визуальная и интерактивная геометрия строки не должна путать header секции, help-иконку и собственно текстовое поле;
- portrait и landscape используют одинаковый semantic contract для фокусируемости и hint-поведения.

**B. Явный focus + IME path**

- вход в редактирование переводится на explicit request-focus path, аналогичный рабочим диалоговым сценариям;
- открытие IME не зависит только от случайного focus change, а становится частью гарантированного activation flow;
- commit-path поддерживает как focus-loss, так и editor completion.

**C. Editor-first keyboard precedence в Settings**

- пока активен settings text field, printable input, delete/backspace, caret navigation, editor enter/done и стандартные text-editing keys принадлежат редактору;
- surface-wide shortcuts не должны открывать settings search, переключать вкладки или активировать посторонние control'ы во время набора текста;
- глобальные escape/help semantics допускаются только в объёме, не ломающем редактирование.

**D. Discoverability и first-entry state секции**

- `App Data` не должен открываться пользователю в состоянии, при котором он визуально ожидает editable controls, но фактически взаимодействует только с заголовком секции;
- на первом посещении секция открыта по умолчанию; далее сохраняется явный выбор пользователя.

**E. Regression coverage**

- unit-тесты фиксируют keyboard precedence для surface `SETTINGS` при активном text field;
- UI-level тесты фиксируют tap-to-focus, ввод текста и commit-path в portrait и landscape;
- тесты должны ловить регрессию даже если IME itself flaky на эмуляторе, то есть основным oracle считается не только факт показа клавиатуры, но и успешное изменение текста/commit state.

### 5.2 Потоки данных и событий

```
User tap on Default User / Default Password
    -> section is visible and editable
    -> target field becomes focused explicitly
    -> IME opens on the focused field
    -> text input is routed to the field, not to settings shortcuts
    -> commit happens on IME done or on focus loss
    -> SettingsViewModel receives updated value
```

### 5.3 Точки расширяемости

- Правило editor-first precedence должно быть переиспользуемым для других editable settings fields.
- Паттерн explicit inline text-edit activation в settings может стать каноническим для всех будущих полей с вводом на surface `SETTINGS`.

---

## 6. Закрытые решения после review

1. **Форма UI-регрессии в автотесте**
   - **Решение:** использовать смешанную схему `unit + androidTest`.
   - **Выбранный путь:** unit-тесты покрывают precedence keyboard-layer для surface `SETTINGS`, а один узкий instrumentation test покрывает inline flow `tap -> focus -> text mutation -> commit` для `Default User` / `Default Password`.
   - **Почему:** JVM/unit даёт быстрый и стабильный сигнал для routing-логики, а реальный inline text-input на settings surface требует хотя бы одного device/emulator уровня. Robolectric остаётся допустимым вспомогательным инструментом, но не единственным oracle для этого дефекта.
   - **Статус:** Resolved on 2026-05-05

2. **Escape / Back semantics во время редактирования**
   - **Решение:** `Escape` работает по editor-first модели, если активен settings text field: сначала снимает фокус и скрывает IME, и только следующий `Escape` без активного редактора возвращает обычное поведение surface exit. `Back` сохраняет platform-native semantics и не переопределяется этим bugfix.
   - **Почему:** это согласуется с уже существующим паттерном `EditText`-guard в dialog input flow и минимизирует риск регрессии для обычной Android back-navigation.
   - **Статус:** Resolved on 2026-05-05

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|---|:-----------:|-------------|-----------|
| Снятие приоритета у settings shortcuts в режиме редактирования сломает keyboard-navigation вне полей | Средняя | Регрессия TV/keyboard UX в Settings | Явный editor-only guard, unit coverage на non-editor сценарии |
| Авто-раскрытие `App Data` на первом посещении изменит привычную компактность экрана | Средняя | Визуальная регрессия для части пользователей | Ограничить новым default-state только отсутствие сохранённого user-choice |
| Forward tap-to-focus на credentials row заденет help-иконку или password toggle | Средняя | Неверная clickable geometry и случайные action'ы | Раздельные hit zones и UI regression tests |
| Унификация portrait/landscape поломает существующую ширину или обрезку helper/content | Низкая | Частичный layout regression | Проверка обеих ориентаций как обязательный DoD и UI snapshot/manual pass |
| UI-тесты для IME окажутся flaky | Средняя | Ненадёжный CI signal | Делать primary assertion по focus/text mutation/commit, а не только по видимости клавиатуры |

---

## 8. Влияние на пользователя (docs/FEATURES)

Отдельное обновление `docs/FEATURES.md` не требуется: это bugfix существующего сценария, а не новая пользовательская возможность. Если в тактической фазе будет добавлен новый явный affordance или отдельная UI-подсказка, решение по FEATURES принимается отдельно.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Settings text fields use editor-first key precedence**

- **Решение:** при активном текстовом поле на surface `SETTINGS` редактор получает приоритет над navigation/search shortcuts.
- **Альтернативы:** оставить общий keyboard layer без изменений; патчить только конкретные поля локальными listener'ами.
- **Почему:** дефект лежит на уровне surface contract, локальный патч только в одном поле не защищает от повторения на соседних editable settings fields.

**ADR-2: Default credentials remain inline, not dialog-based**

- **Решение:** редактирование остаётся прямо в `General -> App Data`, без переноса в отдельный dialog.
- **Альтернативы:** открыть отдельный edit-dialog для credentials; перенести редактирование в отдельный экран.
- **Почему:** отдельный dialog действительно обходит часть проблем, но лечит симптом ценой лишнего UX-излома и расхождения с существующей моделью settings form.

**ADR-3: First-entry `App Data` should be expanded**

- **Решение:** при отсутствии сохранённого состояния секция открыта по умолчанию.
- **Альтернативы:** оставить текущий collapsed default; убрать collapsible behavior совсем.
- **Почему:** это минимальный change set, который снижает путаницу и не отменяет пользовательскую настройку секции после первого явного выбора.

**ADR-4: Material text field contract must be portrait/landscape-consistent**

- **Решение:** одна semantic model для wrapper hint, editable content, width policy и focus affordance в обеих ориентациях.
- **Альтернативы:** точечный fix только в portrait; точечный fix только в helper-коде без приведения layout'ов.
- **Почему:** текущий дефект пользовательски проявляется на интеракции, а разные layout contracts между orientation states увеличивают шанс скрытых регрессий.

---

## 10. Связи с другими спеками

- **S0044** — settings layout compactness. Текущий bugfix должен не противоречить ранее введённым layout-правилам экранов настроек.
- **S0081** — TV remote key coverage. Изменения keyboard precedence должны сохранять совместимость с общей стратегией keyboard/remote navigation.

---

## 11. Критерии готовности (strategic-level)

1. На первом прямом тапе по `Default User` и `Default Password` пользователь может начать ввод текста в portrait и landscape.
2. Экранная клавиатура открывается предсказуемо при входе в редактирование этих полей.
3. При активном поле текстовый ввод, удаление символов и editor navigation не перехватываются surface-wide shortcuts настроек.
4. `Ctrl+F` и другие settings shortcuts не крадут фокус у credentials field во время редактирования.
5. `App Data` на первом посещении раскрыта по умолчанию; при наличии сохранённого выбора секции уважается пользовательское состояние.
6. Строка default credentials и её layout contract консистентны между portrait и landscape: без дублирующихся hint'ов, без ложных hit-area и с корректной доступностью.
7. Сохранение новых значений происходит и при явном завершении редактирования, и при потере фокуса.
8. Есть автопроверка, которая ловит регрессию focus/keyboard-precedence хотя бы на уровне unit + UI coverage.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: реализация по фазам из `PLAN/S0090_bugfix-settings-default-credentials-input/INDEX.md` с обязательной верификацией portrait/landscape и regression coverage.
