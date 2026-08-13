# Стратегическая спецификация: S1003 - Унификация типов медиа и профиля ресурса (создание/редакция)

**Ticket:** S1003
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-11
**Tactical spec:** `PLAN/S1003_resource-media-types-profile-unify/` (будет создан через `/spec-tech`)

<!-- auto-approved by /spec-all - 2026-07-11: поведение задано владельцем в §0 детально; референс-реализация (create-flow) существует в коде; owner-вопросов нет. -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

Окно редакции ресурса (в моём случае - SMB, но проверить для остальных) - есть сворачиваемая группа "Типы медиа", в которой нет тогглеров для всех типов (пропали?) и только выбор классификатора из профиля быстрой настройки. Там стоит "произвольный". Если меняю на "Аудитека" - меняется иконка. Хорошо.  Но если меняю на "файловый менеджер", - пропадает вся сворачиваемая группа из интерфейса. А ведь там должны быть все тогглеры про типы фалов, по аналогии с группой "типы файлов" при создании нового ресурса.

Кстати и при создании ресурса и при редакции к значению профиля быстрой снастройки нужно подойти обинаково - это должен быть один диалог выбора профиля ресурса и заголовок: "Профиль ресурса". Сейчас они реализовны различно. Должны работать одинаково для всех типов ресурсов и при создании и при редакции - унифицировать

---

## 1. Проблема

- В редакторе ресурса выбор профиля «Файловый менеджер» (ALL_FILES) прячет ВСЮ сворачиваемую группу «Типы медиа» - пользователь теряет и тогглеры типов, и сам селектор профиля (вернуться назад нельзя без пересоздания).
- Выбор профиля реализован в create- и edit-потоках по-разному: в создании - только для SMB и SFTP (две копии ручной пресет-логики), для LOCAL/FTP/облаков селектора нет вовсе; заголовок диалога «Профиль быстрой настройки» вместо ожидаемого «Профиль ресурса».
- Эффект: непоследовательный UX, «пропадающая» группа воспринимается как баг, пресет-логика продублирована и может разъезжаться.

---

## 2. Цели

1. В редакторе группа «Типы медиа» видима всегда (для типов ресурсов, чья схема включает типы медиа), включая профиль «Файловый менеджер»; тогглеры типов присутствуют по аналогии с группой создания (при allFiles - отключены, но видимы).
2. Один общий диалог выбора профиля с заголовком «Профиль ресурса» (EN/RU/UK) - в создании и в редакции.
3. Селектор профиля унифицирован во всех create-формах, где есть блок типов медиа (SMB и общая SFTP/FTP-секция); выбор профиля выставляет те же типы, что и в редакции.
4. Применение пресета - единая точка (`ProfileMediaPreset`/`applyProfile`, S1002), без ручных копий на форму.

**Non-goals:**

- Изменение состава/семантики самих профилей (NONE..ALL_FILES) и их влияния на флаги.
- Редизайн диалога сверх заголовка/унификации (стиль остаётся Material single-choice).
- Wear OS и cloud-специфичные формы без блока типов медиа.
- Добавление блока «типы медиа»/профиля в create-форму LOCAL: там блока типов нет вовсе (типы сужаются после создания в редакторе) - добавление целой секции выходит за рамки унификации; редактор после фикса даёт полный путь.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Тогглеры типов в редакции - «по аналогии с группой "типы файлов" при создании».
2. Один диалог, один заголовок: «Профиль ресурса».
3. Одинаково для всех типов ресурсов и в создании, и в редакции.

### 3.2 Жёсткие ограничения

- **Flavor:** все (ресурс-формы в `src/main`).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично (диалог/видимость).
- **Совместимость данных:** модель не меняется; только UI/поведение.
- **Локализация:** заголовок диалога и возможные новые строки - EN/RU/UK.
- **Доступность:** пункты диалога и тогглеры остаются фокусируемыми (D-pad), без изменений контрастов.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1002 (`ProfileMediaPreset`/`applyProfile` - единая точка пресетов, переиспользуется).

---

## 4. Контекст текущей архитектуры

- Редактор: fragment-форма с per-type схемой видимости полей; профиль применяется через доменный `applyProfile` (S1002), но видимость карты «Типы медиа» завязана на флаг allFiles - профиль ALL_FILES прячет карту целиком.
- Создание: одна activity с per-protocol секциями; пресет-кнопка есть только у SMB и SFTP, каждая со своей копией «профиль -> чекбоксы» мимо доменного пресета; LOCAL/FTP селектора не имеют.
- Заголовок диалога общий (`title_select_profile` = «Профиль быстрой настройки»), но сами диалоги - две независимые реализации.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- **Общий диалог профиля:** один переиспользуемый хелпер «выбор профиля ресурса» (single-choice, заголовок «Профиль ресурса», единый маппинг лейблов), вызываемый и create-, и edit-формой.
- **Единая пресет-логика:** create-формы применяют выбранный профиль через доменный пресет (S1002), убирая ручные per-protocol копии.
- **Видимость группы в редакторе:** карта «Типы медиа» видима по схеме типа ресурса независимо от allFiles; тогглеры при allFiles - disabled (как сейчас), не скрыты.
- **Покрытие create-форм:** SMB и общая SFTP/FTP-секция (обе имеют блок типов) идут через общий диалог и доменный пресет; LOCAL - non-goal (блока типов в create нет).

### 5.2 Потоки данных и событий

- Тап селектора -> общий диалог -> выбранный профиль -> доменный пресет -> обновление чекбоксов/флагов формы -> (редактор) авто-иконка как сейчас.

### 5.3 Точки расширяемости

- Новый профиль добавляется в enum + доменный пресет + маппинг лейбла - обе формы подхватывают автоматически.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет - референс-поведение (create-flow) и формулировки заданы владельцем в §0.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Регресс схемной видимости (типы ресурсов без блока типов медиа) | Средняя | лишняя пустая группа | видимость по схеме сохраняется; убирается только связь с allFiles |
| Расхождение чекбокс-состояний при переходе на доменный пресет в create | Средняя | иной набор типов после выбора профиля | пресет S1002 - тот же источник, что в редакции; сверка по каждому профилю на устройстве |
| layout-land рассинхрон | Низкая | сломанный лендскейп | правки в обоих бакетах, где есть counterpart |

---

## 8. Влияние на пользователя (docs/FEATURES)

- Поведенческая унификация + фикс исчезающей группы. Записать в `docs/ALL_FEATURES.jsonl` (FIX/CHANGE) при реализации.

---

## 9. Архитектурные решения (ADR)

- **ADR-1: один источник пресетов - доменный (S1002 `ProfileMediaPreset`).** UI-формы не держат собственные копии «профиль -> типы».
- **ADR-2: allFiles не управляет видимостью группы, только enabled-состоянием тогглеров.** Скрытие группы лишало пользователя пути назад.

---

## 10. Связи с другими спеками

- S1002 - доменный пресет профиля (переиспользуется, не меняется).

---

## 11. Критерии готовности (strategic-level)

1. Редактор: профиль «Файловый менеджер» НЕ прячет группу «Типы медиа»; тогглеры видимы (disabled при allFiles), селектор профиля доступен, возврат на другой профиль возможен. **PASS** (emulator-5556, SFTP «Home ApeFlac»: до фикса группа исчезала; после - кнопка «Файловый менеджер», тогглеры видимы disabled, скриншот 22:06).
2. Диалог выбора профиля в создании и редакции - один и тот же, заголовок «Профиль ресурса» (EN/RU/UK). **PASS** (редактор 22:05 и SMB-create 22:08 - один `ResourceProfileDialog`, заголовок «Профиль ресурса»; string audit EN/RU/UK OK).
3. Выбор профиля в create выставляет те же типы, что и в редакции (единый доменный пресет). **PASS** (SMB-create: «Аудиотека» -> только Audio checked, кнопка обновилась; SFTP/FTP - тот же код-путь `showProfilePresetDialog`).
4. Лендскейп-каунтерпарты затронутых layout-ов синхронны. **PASS** - counterpart-ов у `activity_add_resource`/`fragment_resource_editor` нет (layout/ только), XML не менялся.

---

## Last Audit

- **Date:** 2026-07-12
- **Mode:** manual re-audit (owner-reported regression after Archived; not a full `/spec-check` static run)
- **Outcome:** Broken
- **Owner report:** on a debug APK built 2026-07-12 21:55 (after the 2026-07-11 fix), the SMB resource editor's «Типы медиа» group still disappears entirely when selecting the «Файловый менеджер» profile - same symptom as the original §0/§1 report, unchanged from before the fix.
- **Code-level re-check (no smoking gun found):**
  - `ResourceEditorFragment.updateMediaTypesSectionVisibility()` (~L671) still gates on `hasMediaTypesBySchema` only, set from schema `visibleKeys` (~L757), not from `allFiles`/profile - matches the intended fix.
  - `observeUiState()` calls `renderFieldSchema()` (sets `hasMediaTypesBySchema`) before `renderFormData()` (consumes it) - no stale-read ordering bug.
  - `ResourceFormViewModel.fieldSchema` is recomputed from `resourceEditorUseCase.fieldSchema(type)`, keyed only by resource `type`, independent of profile/`allFiles`.
  - `SmbResourceStrategy.fieldSchema()` (and Sftp/Ftp/Local/Cloud) register `MEDIA_TYPES` with default `visible = true` unconditionally - `ResourceFieldSchema.visible` has no other writer.
  - Conclusion: static inspection says the group should stay visible; the runtime symptom is unexplained from source alone.
- **Not yet checked:** whether the owner's edit session actually reaches `ResourceEditorFragment` (vs. some other/legacy edit entry point); live logcat during repro; layout XML actually packaged into the tested APK.
- **Residual:** prior `Verified` evidence (`temp/scratch/emulator-5556_202607112*.png`) is gone - `temp/scratch` was rotated since 2026-07-11, so the original on-device claim can't be cross-checked either.

### Action items

1. **[MANUAL]** Reproduce live: SMB resource editor -> profile «Файловый менеджер» -> confirm group disappearance, capture screenshot + logcat, confirm which Fragment/Activity is on screen.
2. **[MANUAL]** Repeat for SFTP/FTP/LOCAL/CLOUD editors to check scope (original report only covers SMB, "but проверить для остальных").
3. **[DONE 2026-07-13]** Diagnostic probes added (status -> BlockNeedUserTest). Second static pass confirmed the prior audit: `applyProfile(ALL_FILES)` changes only `profile/supportedMediaTypes/allFiles/rememberFileList` (NOT `type`/`path`), SMB strategy registers `MEDIA_TYPES` unconditionally, virtual-path branch cannot fire on `smb://` - so the card must stay visible statically. Symptom is runtime-only; probes capture the real values.

### Instrumentation (2026-07-13, active while BlockNeedUserTest)

`Timber.d("S1003: ...")` probes at four flow points:
- `ResourceFormViewModel.onProfileSelected` - logs `profile`, resulting `type`, `allFiles`, `path`.
- `ResourceEditorFragment.renderFieldSchema` (after MEDIA_TYPES key computation) - logs `type`, `hasMediaTypesBySchema`, full `visibleKeys`.
- `ResourceEditorFragment` virtual-path branch entry - logs when it fires + `currentPath`.
- `ResourceEditorFragment.updateMediaTypesSectionVisibility` entry - logs `hasMediaTypesBySchema` + final `show`.

On device: open the SMB resource editor, switch profile to «Файловый менеджер», capture `adb logcat | grep S1003`. Diagnosis:
- If NO `S1003:` line fires on profile change -> the edit surface is not `ResourceEditorFragment` (wrong fragment / legacy path) - the prior audit's top unchecked hypothesis.
- If `renderFieldSchema` logs `hasMediaTypesBySchema=false` -> the schema drops MEDIA_TYPES at runtime (unexpected vs static) - inspect `visibleKeys`/`type` logged.
- If `virtual-path branch HIT` fires -> `applyProfile` or state made the path virtual - inspect `currentPath`.
- If all show `hasMediaTypesBySchema=true`/`show=true` yet the card is gone -> a visibility writer outside these paths (collapsible section / layout) - widen the probe next pass.
