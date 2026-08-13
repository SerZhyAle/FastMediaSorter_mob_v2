---
ticket: S0302
status: Implemented
priority: 50
date: 2026-05-30
tier: 4
---

# Стратегическая спецификация: S0302 - file manager mode

**Ticket:** S0302
**Status:** Archived
**Priority:** 50
**Date:** 2026-05-30
**Tier:** 4 - Strategic, ad-hoc
**Roadmap entry:** Ad-hoc - запрос 2026-05-30: объявить режим, в котором ресурс показывает все файлы, режимом файлового менеджера; изучить интерфейс и документацию; переосмыслить Android-позиционирование приложения как файлового менеджера.
**Tactical spec:** [`PLAN/S0302_file-manager-mode/INDEX.md`](PLAN/S0302_file-manager-mode/INDEX.md)
**Tactical plan:** `PLAN/S0302_file-manager-mode/INDEX.md`

> **Scope:** STRATEGIC. Product naming, UX contract, documentation scope, Android system positioning. Без имён классов, путей, лимитов строк, схемы Room и concrete manifest edits.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - режим ресурса, который показывает все файлы, должен стать понятным режимом файлового менеджера; приложение должно легче восприниматься и использоваться как файловый менеджер в Android-среде.
- **Local anchor:** Provided by user - ресурсный режим показа всех файлов.
- **Scope boundaries / forbidden areas:** Provided by user - UI terminology, trilingual documentation, Manifest category APP_FILES registration. No generic directory/file intent receivers or SAF provider in the first phase.
- **Done / success signal:** Provided by user - интерфейс и документация оформляют этот режим как файловый менеджер, а не как медиа-фильтр; Android-интеграция не конфликтует с этим позиционированием.
- **Autonomy rule:** Provided by user - Agent is permitted to make autonomous UX/product decisions (placement, labels, docs wording) using explicit reasonable assumptions.
- **UI decisions / delegation:** Delegated by user - агент должен изучить интерфейс и документацию, предложить места переименования и переосмысления; implementation остаётся заблокированной до явных UI-решений в tactical фазе.

---

## 1. Проблема

В приложении уже есть режим ресурса, который отключает медиа-фильтр и показывает все типы файлов. Сейчас он описан как техническая опция "All files", поэтому пользователь видит частный фильтр, а не полноценный сценарий файлового менеджера.

Документация уже заявляет, что FMS может заменить файловый менеджер, но интерфейс не везде связывает это обещание с конкретным режимом ресурса. Из-за этого сильная сторона продукта выглядит как набор разрозненных возможностей: все файлы, скрытые файлы, папки, операции, системные интенты, внешнее открытие.

Отдельная проблема - Android-позиционирование. Для роли файлового менеджера важно не только показывать все файлы внутри Browse, но и честно объяснять доступ к хранилищу, системные точки входа, Storage Access Framework, ограничения Android и поведение на телефонах, планшетах, ChromeOS, TV и XR-панелях.

---

## 2. Цели

1. Объявить режим показа всех файлов в ресурсе продуктовым **File Manager Mode**.
2. Сохранить существующий смысл режима: обход медиа-фильтра, показ файлов любых типов, доступность файловых операций там, где источник это поддерживает.
3. Развести понятия: **File Manager Mode** управляет видимостью и файловыми операциями, а не обещает встроенное открытие любого формата.
4. Привести интерфейс добавления, редактирования и просмотра ресурсов к одной терминологии.
5. Привести feature-документацию, README, quick start, how-to и FAQ к единому объяснению сценария файлового менеджера.
6. Определить Android-system surface: launcher/category entry, all-files permission rationale, share/open intents, possible SAF provider scope.
7. Не ломать media-first сценарии: фото, видео, аудио и документы остаются профильными режимами, а File Manager Mode становится отдельной понятной меткой.

**Non-goals:**

- Не реализовывать tactical изменения в рамках этого `/spec`.
- Не обещать встроенный просмотр каждого бинарного, архивного или документного формата.
- Не добавлять `QUERY_ALL_PACKAGES` без отдельного подтверждённого сценария.
- Не строить полноценный `DocumentsProvider` без отдельного owner-решения после исследования.
- Не переименовывать внутренние поля данных только ради косметики, если пользовательский контракт можно закрыть UI/docs-слоем.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Режим, где ресурс демонстрирует все файлы, должен называться режимом файлового менеджера.
2. Нужно изучить текущий интерфейс программы, а не менять одну строку.
3. Нужно изучить документацию программы и понять, где добавить или переосмыслить описание.
4. Приложение должно легче ложиться в Android-среду как файловый менеджер.

### 3.2 Жёсткие ограничения

- **Flavor:** all app flavors, with capability wording adjusted per available feature surface.
- **API level:** standard/lite/photos API 26+; legacy API 23+; Android file-manager system category is API 29+ and must be treated as an additive system hint, not a required launch path.
- **Wear OS:** no direct scope unless phone-side terminology is later mirrored into the companion docs.
- **Производительность:** File Manager Mode must not force thumbnails or deep metadata extraction for unsupported binaries.
- **Совместимость данных:** existing resources must keep their current behavior after upgrade; if persistent schema changes become necessary, they require tactical justification.
- **Локализация:** EN/RU/UK required for UI strings and public docs.
- **Доступность:** every renamed or newly surfaced control must preserve keyboard, D-pad, mouse and TalkBack behavior.
- **Storage policy:** all-files access messaging must stay truthful: Android grants broad shared-storage access for file-manager-like use cases, but still excludes protected app-specific directories.
- **System integration:** exported surfaces must be explicit, minimal and safe; file-manager positioning must not broaden external entry points beyond reviewed behavior.
- **UI copy:** future strings must pass `docs/COMMUNICATION_POLICY.md` tone checklist.
- **Docs consistency:** public feature docs must not overpromise noLegal-only or flavor-specific capabilities.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0059, S0082, S0253, S0289, S0293, S0299.
- **Known owner request:** rename and frame the all-files resource mode as File Manager Mode.
- **Known owner request:** review interface and documentation before deciding exact edits.
- **Known owner request:** align product positioning with Android file-manager expectations.
- **Scope boundaries resolved:** UI terminology, documentation, Manifest category APP_FILES registration. No generic directory/file intent receivers or SAF provider.
- **Autonomy rule resolved:** Agent may make autonomous UX/product decisions (labels, placement, docs wording) using explicit reasonable assumptions.

---

## 4. Контекст текущей архитектуры

FMS already has a resource-centric browsing model: the main screen opens configured sources, Browse renders file lists, and the player/viewer route handles supported formats. The current product can manage local, network and cloud files, including copy, move, rename, delete, folders, ZIP operations, widgets, shortcuts and system share/open entry points.

The all-files resource flag already exists as a domain concept. It bypasses the media-type filter and is also referenced by hidden-file visibility, default virtual resources, quick setup presets and document-related actions. The gap is not that the product lacks file-manager mechanics; the gap is that the UI and docs expose those mechanics as fragmented media settings.

The current Android-facing surface is stronger as a viewer/receiver than as a file-manager identity. The app can be launched normally, appears on TV launchers, receives shared files and opens supported media/documents through standalone player aliases. It does not yet have a single product-level file-manager route that ties resource browsing, broad storage permission and system file-app positioning together.

---

## 5. Предлагаемый подход

### 5.1 Product terminology

- Treat **File Manager Mode** as the user-facing name for resources that show all files.
- Keep **All files** only where a compact label is needed and the surrounding context already says File Manager Mode.
- Explain the mode as "show and manage every visible file in this resource", not as "enable every viewer".
- Keep "Show hidden files" as a subordinate option that only makes sense after File Manager Mode is enabled.
- Keep media profiles as first-class presets: Photo, Video, Audio, Documents and File Manager.

### 5.2 UX contract

- In resource creation, File Manager Mode appears as a profile/preset and as the explicit all-files toggle for advanced setup.
- In resource editing, the mode is visible as the governing display mode for the resource.
- In Browse, the current resource state should communicate whether the list is media-filtered or file-manager-style.
- Unsupported files remain useful rows: icon, name, metadata, select, copy, move, rename, delete, share and external-open options where available.
- Empty states should distinguish "no files here" from "files hidden by current filter".
- Error states should avoid implying that an unsupported viewer means the file-manager mode failed.

### 5.3 Documentation contract

- Public feature inventory gets one coherent File Manager Mode entry instead of scattered "all files" wording.
- Russian and Ukrainian mirrors keep the same product meaning, not literal translations of an internal flag.
- README and Quick Start should route new users to File Manager Mode when their goal is folder management, NAS browsing, USB/OTG inspection or Downloads cleanup.
- How-To should add a practical scenario: use FMS as an Android file manager across local, SMB/SFTP/FTP and cloud sources.
- FAQ should clarify what File Manager Mode can and cannot open internally.
- Limitations and Privacy docs should keep permission language precise for broad storage access.

### 5.4 Android environment fit

- Review whether the main app should advertise the Android files-app category for API 29+ launch flows.
- Keep all-files access permission as a user-granted capability with clear rationale and fallback behavior.
- Keep share/open intents focused on supported external entry points; do not claim generic file-manager handling until Browse can safely route directory/file intents.
- Treat SAF provider exposure as a future optional layer: useful if FMS resources should appear inside Android's system picker, but too large to assume in this spec.
- Keep ChromeOS, large-screen, TV remote and multi-window behavior part of the acceptance surface because those devices are natural file-manager environments.

### 5.5 Data and event flow

Resource setup stores a display/filter intent. Browse reads that intent, builds the visible file list, applies optional hidden-file rules, then routes item actions through existing file-operation and viewer flows. Documentation and system entry points should describe this as one user story: choose or create a resource, switch it to File Manager Mode, then browse and manage files across supported storage backends.

### 5.6 Точки расширяемости

- Future SAF provider layer can expose selected FMS resources to other apps without changing the Browse mental model.
- Future system shortcuts can jump directly to Downloads, Recent or a specific File Manager Mode resource.
- Future per-resource templates can tune thumbnails, hidden files, folder display and default sort for file-manager usage.
- Future onboarding can ask whether the user wants FMS as media sorter, file manager, NAS client or document hub.

---

## 6. Открытые вопросы / Research items

1. **Scope boundary**
   - **Вопрос:** это только переименование и docs/UX alignment или также Android manifest/system integration in the first implementation?
   - **Варианты:** terminology-only; UI+docs; UI+docs+Android category; UI+docs+SAF provider research.
   - **Нужно выяснить:** владелец должен выбрать допустимую глубину первой реализации.
   - **Статус:** Open

2. **Autonomy rule**
   - **Вопрос:** может ли агент сам принять спорные решения по placement, labels and docs wording with explicit assumptions?
   - **Варианты:** ask on each ambiguity; agent may decide with explicit assumptions.
   - **Нужно выяснить:** owner approval gate.
   - **Статус:** Open

3. **Primary UI placement**
   - **Вопрос:** File Manager Mode should be a resource profile, a toggle label, a badge in Browse, or all of these?
   - **Варианты:** profile-only; toggle-only; profile + toggle; profile + toggle + Browse state badge.
   - **Нужно выяснить:** tactical UI clarification before editing strings/layouts.
   - **Статус:** Open

4. **Unsupported files**
   - **Вопрос:** what is the required tap behavior for files FMS can list but cannot render internally?
   - **Варианты:** external-open chooser; info dialog; operations menu only; configurable default.
   - **Нужно выяснить:** user expectation for file-manager mode.
   - **Статус:** Open

5. **Android files-app category**
   - **Вопрос:** should FMS advertise itself as a files app for Android 10+ launch selectors?
   - **Варианты:** no; add only to main launcher; add a dedicated alias; research device behavior first.
   - **Нужно выяснить:** actual launcher/system behavior on target devices.
   - **Статус:** Open

6. **SAF provider**
   - **Вопрос:** should FMS eventually expose local/network/cloud resources into the Android system picker?
   - **Варианты:** out of scope; separate research spec; future tactical phase after File Manager Mode lands.
   - **Нужно выяснить:** owner appetite for a larger platform integration layer.
   - **Статус:** Open

7. **Docs breadth**
   - **Вопрос:** which public docs must change in the first implementation?
   - **Варианты:** FEATURES only; FEATURES + README; FEATURES + README + QUICK_START + HOW_TO + FAQ + LIMITATIONS/PRIVACY wording audit.
   - **Нужно выяснить:** preferred documentation depth for this product positioning pass.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Название обещает больше, чем делает режим | Средняя | Пользователь ожидает встроенное открытие любого файла | Copy must separate manage/list/open capabilities |
| Системная интеграция расширит attack surface | Средняя | Exported entry points accept unsafe intents | Add only reviewed explicit surfaces |
| Google Play all-files policy выглядит слабее без file-manager framing | Средняя | Permission rationale seems excessive for media sorter | Tie permission copy to user-visible file-management use case |
| Docs drift across EN/RU/UK | Средняя | Different languages promise different capabilities | Update docs in mirrored batches |
| File Manager Mode conflicts with media-first presets | Низкая | Users lose simple photo/video setup mental model | Keep media profiles separate and prominent |
| Unsupported binaries degrade Browse performance | Низкая | Slow thumbnails/metadata on large folders | Use generic icons and avoid heavy probing |
| SAF provider scope grows too large | Средняя | Spec becomes platform rewrite instead of UX alignment | Keep SAF provider as optional future layer |

---

## 8. Влияние на пользователя (docs/FEATURES)

After implementation, `docs/FEATURES.md` + `_RU` + `_UK` should describe **File Manager Mode** as the resource mode for browsing and managing all visible file types across supported local, network and cloud sources, with explicit note that unsupported formats can still be managed even when they cannot be opened internally.

Potential additional docs: README, QUICK_START, HOW_TO, FAQ, LIMITATIONS and PRIVACY wording audit.

---

## 9. Архитектурные решения (ADR)

**ADR-1: File Manager Mode is a product mode, not a new storage backend**

- **Решение:** define File Manager Mode as the user-facing contract for all-files resource browsing.
- **Альтернативы:** create a separate resource type or duplicate Browse.
- **Почему:** existing resource browsing already owns the file-management behavior; a new backend would create duplication.

**ADR-2: Manage capability is separate from viewer capability**

- **Решение:** File Manager Mode promises listing and management, while internal opening remains governed by supported viewers and external handoff.
- **Альтернативы:** promise all file formats are supported.
- **Почему:** honest scope avoids UX failures on archives, binaries and vendor formats.

**ADR-3: Android system fit is incremental**

- **Решение:** first align terminology, UI and docs; then add only reviewed Android entry points.
- **Альтернативы:** immediately implement a full SAF provider or broad generic file intents.
- **Почему:** system surfaces are exported contracts and need separate threat-model and device-behavior validation.

**ADR-4: All-files permission copy must be justified by file-manager use**

- **Решение:** permission explanations should connect broad storage access to File Manager Mode and folder management.
- **Альтернативы:** keep permission framed as media browsing only.
- **Почему:** Android's all-files access is explicitly intended for file-manager-like core use cases; media-only framing makes the permission look oversized.

---

## 10. Связи с другими спеками

- **S0059** - precedent: Recent and Downloads default resources with all-files enabled.
- **S0082** - ChromeOS support and storage-permission behavior on desktop-style Android environments.
- **S0253** - compact overflow menus, relevant for file-manager density.
- **S0289** - TV/keyboard/D-pad navigation coverage, relevant for file-manager UX.
- **S0293** - multi-window discoverability, relevant for desktop-style file management.
- **S0299** - Office document external handoff, relevant for unsupported and externally opened document formats.

---

## 11. Критерии готовности (strategic-level)

1. User-facing UI has a clear File Manager Mode concept for resources that show all files.
2. Users can distinguish File Manager Mode from media-only and document-only resource profiles.
3. Hidden files, folders, unsupported files and external-open behavior are explained without contradictory wording.
4. Documentation mirrors the same File Manager Mode story in EN/RU/UK.
5. Android all-files access rationale matches file-manager positioning and does not overclaim privacy or capability.
6. Any Android system entry point added by implementation has a reviewed safe behavior for phones, tablets, ChromeOS, TV and XR panels.
7. Existing media browsing and standalone player flows remain unchanged unless explicitly included in tactical scope.
8. The implementation can be validated without requiring a full SAF provider unless the owner explicitly approves that scope.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0302` - создаст `PLAN/S0302_file-manager-mode/` с фазами после owner approval gate.

---

## Revision History

- **2026-05-30** - created by Codex via `/spec`
  - Added strategic draft for File Manager Mode naming, UX/docs alignment and Android system-positioning research.
