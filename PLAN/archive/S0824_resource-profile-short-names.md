# S0824 - Короткие названия профилей ресурсов без префикса «Быстрая настройка»

**Ticket:** S0824
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-30
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-30

<!-- auto-approved by /spec-all - 2026-06-30 -->

> **Scope:** Compact (Simple path). Цель и фазы реализации в одном файле.

---

## Цель

Названия пресет-профилей ресурсов дублируют контекст: каждый пункт начинается с «Быстрая настройка: » («Быстрая настройка: Аудиотека», «Быстрая настройка: Видеотека» и пр.). Префикс избыточен - диалог выбора профиля уже озаглавлен «Профиль быстрой настройки». Нужно убрать префикс из всех шести названий профилей во всех трёх локалях, оставив только содержательную часть («Аудиотека», «Видеотека» и пр.). Заголовок диалога не трогаем - он несёт контекст «быстрой настройки».

## Затронутая область

- Строковые ресурсы `label_profile_*` (6 ключей) в `values/`, `values-ru/`, `values-uk/`.
- Потребители метки - пикер пресет-профиля в редакторе ресурса и в форме добавления ресурса; оба выводят метку как есть, без программного префикса.

**Non-goals:**

- Не меняем заголовок диалога `title_select_profile` (несёт контекст «быстрой настройки»).
- Не трогаем enum `ResourceProfile`, логику применения пресетов и имена строковых ключей.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0799
- **UI scope:** видимое изменение названий профилей, запрошено владельцем 2026-06-30; контекст «быстрой настройки» сохраняется в заголовке диалога.
- **Localization:** EN/RU/UK - все три локали правятся в lockstep, парность обязательна.

---

## Phases

### Phase 1 - Strip the "Quick Setup" prefix from profile labels

Remove the locale prefix from the six `label_profile_*` string values; keep the key names and the dialog title unchanged.

Target values (key -> EN / RU / UK):

| Key | EN | RU | UK |
|-----|----|----|----|
| label_profile_none | Custom | Пользовательский | Довільний |
| label_profile_audio_library | Audio Library | Аудиотека | Аудіотека |
| label_profile_video_library | Video Library | Видеотека | Відеотека |
| label_profile_photo_storage | Photo Storage | Фотохранилище | Фотосховище |
| label_profile_documents | Documents | Документы | Документи |
| label_profile_all_files | File Manager Mode | Файловый менеджер | Файловий менеджер |

Steps:

1. For each of the six keys, set the EN value in `app_v2/src/main/res/values/strings.xml` to the table value (strip leading `Quick Setup: `) via `set-android-string.ps1 -Action set -Locale en` with `-ExpectedOldValue`.
2. Repeat for RU (`values-ru`, strip `Быстрая настройка: `) and UK (`values-uk`, strip `Швидке налаштування: `).
3. Leave `title_select_profile` unchanged in every locale.

Verification:

- `check_strings_localized.ps1 -KeyPrefix "label_profile_"` exits 0 (EN/RU/UK parity, no missing keys).
- Grep `Быстрая настройка|Quick Setup:|Швидке налаштування` over `app_v2/src/main/res/**/strings.xml` returns no `label_profile_*` line.
- `a.ps1 fr` (resources/manifest gate) passes.

---

## Критерии готовности

1. Пикер профиля показывает «Пользовательский», «Аудиотека», «Видеотека», «Фотохранилище», «Документы», «Файловый менеджер» (и эквиваленты EN/UK) без префикса.
2. Заголовок диалога остаётся «Профиль быстрой настройки».
3. Парность локалей EN/RU/UK сохранена, имена ключей и логика пресетов не изменены.

---

## Last Audit

**Date:** 2026-06-30
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Profile labels (`label_profile_*`, 6 keys × EN/RU/UK) carry no «Quick Setup» / «Быстрая настройка» / «Швидке налаштування» prefix; values match the Phase 1 table. `title_select_profile` unchanged in all locales. Consumers (`ResourceEditorFragment`, `AddResourceFormManager`) render the label verbatim via `getString` - no `.kt` change. Locale parity OK (`check_strings_localized` exit 0); `processStandardDebugResources` BUILD SUCCESSFUL. FEATURES exempt - label refinement, not a new capability.

### Manual / on-device

- [ ] Optional, low-risk: open the resource-profile picker, confirm items read «Аудиотека / Видеотека / …» without prefix while the dialog title still reads «Профиль быстрой настройки». Static proof already covers this (labels display verbatim).

