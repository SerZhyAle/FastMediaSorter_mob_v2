# S0811 - Короткая метка места сохранения в камере (только имя папки)

**Ticket:** S0811
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-30
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

<!-- auto-approved by /spec-all - 2026-06-30 -->

> **Scope:** Compact (Simple path). Цель и фазы реализации в одном файле.

---

## Цель

Оверлей камеры показывает место сохранения как «Сохраняет в: Pictures» (формат `camera_save_destination` = «Сохраняет в: %1$s»). Префикс-обёртка избыточна - достаточно самого имени папки назначения («Pictures»). Нужно выводить только имя назначения, а ставший ненужным строковый ключ удалить (гигиена мёртвого кода, Rule 20).

## Затронутая область

- `CameraCaptureActivity.refreshSaveDestinationLabel()` - единственное место, где метка формируется и присваивается `cameraSaveDestination`.
- Строковый ключ `camera_save_destination` (EN/RU/UK) - используется только этой точкой.

**Non-goals:**

- Не меняем логику разрешения имени назначения (`resolveSaveDestinationName()`), видимость/поворот оверлея, поведение захвата.
- Не трогаем другие метки камеры.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI scope:** видимое изменение метки оверлея камеры, запрошено владельцем 2026-06-29; показываем только имя папки, без вводного текста.
- **Localization:** EN/RU/UK - строковый ключ удаляется во всех трёх локалях в lockstep, паритет сохраняется (ключа нет нигде).

---

## Phases

### Phase 1 - Show only the destination folder name

Display the resolved destination name verbatim and drop the localized "Saves to:" wrapper.

Steps:

1. In `CameraCaptureActivity.refreshSaveDestinationLabel()` set `binding.cameraSaveDestination.text = destinationName` instead of `getString(R.string.camera_save_destination, destinationName)`. `destinationName` is already guaranteed non-blank on this branch.
2. Remove the now-unused string key `camera_save_destination` from all three locales via `set-android-string.ps1 -Action remove -Key camera_save_destination`.

Verification:

- `Grep camera_save_destination` over `app_v2/src/` returns no `.kt` reference and no `<string name="camera_save_destination">` definition in any locale.
- `a.ps1 fc` (Kotlin compile + resources) passes.

---

## Критерии готовности

1. Оверлей камеры показывает только имя папки назначения (например «Pictures»), без префикса «Сохраняет в:».
2. Строковый ключ `camera_save_destination` отсутствует во всех локалях и не имеет ссылок.
3. Логика разрешения имени назначения и поведение камеры не изменены.

---

## Last Audit

**Date:** 2026-06-30
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

`camera_save_destination` removed from EN/RU/UK with zero remaining `.kt` / `@string` references. `refreshSaveDestinationLabel()` now sets `cameraSaveDestination.text = destinationName` (folder name only, no "Saves to:" wrapper); resolution logic and capture behavior unchanged. `a.ps1 fc` (Kotlin compile + resources) BUILD SUCCESSFUL. FEATURES exempt - label refinement, not a new capability.

### Manual / on-device

- [ ] Optional, low-risk: open the camera for a scenario, confirm the destination label reads just the folder name (e.g. «Pictures»). Static proof covers it (TextView bound directly to the resolved name).

