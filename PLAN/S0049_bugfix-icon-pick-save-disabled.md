# S0049 — Bugfix: смена иконки в редакторе ресурса не активирует кнопку «Сохранить»

**Статус:** Verified
**Приоритет:** 70 (critical bugfix)
**Тир:** 1 — Trivial (≤30 мин, риск низкий)
**Создан:** 2026-05-02
**Автор:** user

<!-- auto-approved by /spec-all — 2026-05-02 -->

---

## Goal

В редакторе ресурса (`ResourceEditorFragment` + `ResourceFormViewModel`) выбор пользователем
другой иконки/бейджа через кнопку в тулбаре должен делать форму «грязной» (dirty) и
активировать кнопку «Сохранить» — для всех типов ресурсов (SFTP, SMB, FTP, Cloud, Local)
и в обеих ориентациях (portrait / landscape). Сейчас изменение `formData.iconId` обходит
пересчёт `hasChanges` / `canSave`, поэтому Save остаётся недоступным, и сохранить
изменённую иконку нельзя без редактирования какого-либо другого поля.

**Root cause:** `ResourceFormViewModel.onIconPicked` обновляет `formData.iconId` через
`_uiState.update { current.copy(formData = current.formData.copy(iconId = iconId)) }`,
но **не** вызывает `recalculateState(..)` — поэтому `hasChanges` и `canSave` сохраняют
старые значения. Метод `recalculateState` уже корректно сравнивает `formData` с
`originalSnapshot` (поле `iconId` входит в `data class`, поэтому equals автоматически
учитывает его), нужно только прогнать его после изменения.

---

## Phase 01 — Trigger recalculation on icon pick

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt`

- [x] Step 1: в `onIconPicked(iconId: String)` обернуть обновление состояния в
  `recalculateState(..)`, чтобы `hasChanges`, `isFormValid`, `canSave`, `warnings` и
  `nameSuggestions` пересчитались под новое значение `formData.iconId`.
  - Verification: `grep -n "recalculateState" app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt`
    показывает вызов внутри `onIconPicked`.
  - Verification: `formData != originalSnapshot` после смены iconId → `hasChanges = true` →
    `canSave = isFormValid && hasChanges && !hasNameCollision && !isSaving && !isTestingConnection`
    становится `true`, как только остальные поля валидны.

- [x] Step 2: убедиться, что reset (`onResetChanges`) и save (`onSave` success branch)
  по-прежнему обнуляют состояние корректно — оба пути уже вызывают `recalculateState`,
  изменения не требуются. Подтвердить статически (read-only check).
  - Verification: после Save success `originalSnapshot = currentForm`,
    `fieldStates = emptyMap()`, `recalculateState` пересчитывает `hasChanges = false`.

- [x] Step 3: запустить `/build` → `standard debug`. (auto-build — PASS)

- [x] Step 4: трилингва не затронута (UI-строки не меняются), документация не обновляется.

- [x] Step 5: dev log via `.\scripts\add_to_dev_log.ps1`.

### Manual / on-device

- [ ] EDIT существующего SFTP-ресурса → сменить иконку через кнопку в тулбаре → кнопка
  «Сохранить» становится активной → нажатие сохраняет новую иконку.
- [ ] То же для SMB, FTP, Local, Cloud-ресурсов.
- [ ] Проверить в обеих ориентациях (portrait + landscape) — после поворота состояние
  ViewModel сохраняется, кнопка Save остаётся в правильном положении.
- [ ] Если других изменений нет и iconId совпадает с `originalSnapshot.iconId` (например,
  пользователь открыл picker и выбрал ту же иконку) — `hasChanges = false`, Save остаётся
  неактивным (нет ложных срабатываний).

---

## Out of scope

- Любые изменения дизайна кнопки `btnPickIcon` (S0037 закрывает П5).
- Backfill / автоматическое назначение иконки при смене профиля
  (`onProfileSelected` уже вызывает `recalculateState`).
- AddResourceActivity (CREATE flow) — там нет `originalSnapshot`, dirty-логика не нужна.

---

## Last Audit

**Date:** 2026-05-02
**Mode:** simple (compact spec, /spec-all pipeline)
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 0

### Static checks

- `recalculateState` присутствует в теле `onIconPicked` (line 305 of `ResourceFormViewModel.kt`).
- `ResourceFormData.iconId` входит в `data class` → `equals` различает изменение iconId
  и `hasChanges = it != state.formData` срабатывает.
- `canSave` (line 531) включает `hasChanges` — после смены иконки кнопка активируется при
  валидной форме без других изменений.
- Save success branch обнуляет dirty (line 410-417): `originalSnapshot = currentForm`,
  `fieldStates = emptyMap()`, `recalculateState` пересчитывает `hasChanges = false`.
- Build `assembleStandardDebug` — PASS.

### Manual / on-device

- [ ] EDIT существующего SFTP-ресурса → сменить иконку через кнопку в тулбаре → кнопка
  «Сохранить» становится активной → нажатие сохраняет новую иконку.
- [ ] То же для SMB, FTP, Local, Cloud-ресурсов.
- [ ] Проверить в обеих ориентациях (portrait + landscape).
- [ ] Выбор той же иконки, что уже сохранена → Save остаётся неактивным
  (нет ложных срабатываний).
