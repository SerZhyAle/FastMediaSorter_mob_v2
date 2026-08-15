# Стратегическая спецификация: S0492 - prerelease-configure import via file:// always fails

**Ticket:** S0492
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-17
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - parked during /spec-prerelease run 2026-06-17
**Tactical spec:** `PLAN/S0492_bugfix-prerelease-configure-import-uri/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват находки на лету. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-17

**Захвачено во время:** S0484 (/spec-prerelease run на emulator-5554)

**Текст:**

prerelease-configure.ps1 resource import always fails on API 24+ (file:// URI not openable by ResourceImportActivity)

SYMPTOM: During /spec-prerelease (S0484) configure stage, `scripts/devtest/prerelease-configure.ps1` pushes `app_v2/src/main/res/xml/sza_resources.xml` to /sdcard and fires `am start -a android.intent.action.VIEW -d "file:///sdcard/sza_resources.xml" -t application/vnd.fms.resources+xml -n .../ResourceImportActivity`. On device this always shows AlertDialog "Импорт ресурсов из файла" / "Это не подходящий файл ресурсов." (not a suitable resources file). The automated import stage therefore always fails on every supported device.

ROOT CAUSE (research, file:line): The XML schema is NOT the problem - ResourceShareFormat.ROOT_TAG = "media-resources" (ResourceShareFormat.kt:14) matches sza_resources.xml:46, fully compatible. The failure is the URI scheme: on Android 7+/API 24+ (minSdk 26), ContentResolver.openInputStream() cannot open a raw file:// URI delivered via Intent.data from an external caller (FileProvider isolation -> SecurityException or null stream). SzaResourcesImporter.preview() hits the `stream ?: return PreviewResult.Invalid("Cannot open file")` branch (SzaResourcesImporter.kt:96-97); importFromUri has the same flaw (SzaResourcesImporter.kt:73-87). Maps to string resource_share_invalid_file. So this is a TOOLING bug in prerelease-configure.ps1, not an app defect.

EVIDENCE: scripts/devtest/prerelease-configure.ps1:169-176 (file:// intent); SzaResourcesImporter.kt:73-130; ResourceShareFormat.kt:10-16; AndroidManifest.xml:493-494 (intent-filter registers both content+file schemes).

FIX OPTIONS: (a) push file to app external-files dir and serve via FileProvider with content:// URI + FLAG_GRANT_READ_URI_PERMISSION; or (b) drop the file-intent path from configure.ps1 entirely and rely on the OWNER_TRIGGER UI path (SettingsViewModel.importSzaResources reads R.xml.sza_resources from compiled resources directly, no Uri). Note: the /spec-prerelease skill already documents OWNER_TRIGGER as the fallback, and this sweep run used it successfully - so configure.ps1's file-import stage is effectively dead weight that always fails.

SCOPE: out-of-scope of the sweep run itself (it is the sweep's own tooling), non-trivial (FileProvider wiring in PowerShell or a stage redesign), needs its own research+execution. Discovered during /spec-prerelease run on emulator-5554, 2026-06-17.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Тулинг `/spec-prerelease`: стадия импорта ресурсов в `prerelease-configure.ps1` всегда падает. Скрипт толкал XML на `/sdcard` и слал `ACTION_VIEW` с `file://`-URI в `ResourceImportActivity`, но на minSdk 26 внешний `file://`-URI не открывается через `ContentResolver.openInputStream` (изоляция FileProvider), поэтому приложение показывало «Это не подходящий файл ресурсов». Стадия - мёртвый груз: импортёр и так читает встроенный в APK `res/xml/sza_resources.xml`, а толкаемый файл не используется. Эффект - sweep всегда отмечал стадию импорта как FAIL, хотя реальный путь импорта (OWNER_TRIGGER в UI) работает.

---

## 2. Цели

1. `prerelease-configure.ps1` больше не содержит стадии, гарантированно падающей на каждом поддерживаемом устройстве.
2. JSON-контракт скрипта явно сообщает, что импорт делегирован UI (`status=delegated-ui`), а не молча падает.
3. Скилл `/spec-prerelease` (оба зеркала) описывает импорт как единственный рабочий путь OWNER_TRIGGER, без упоминания intent-push как основного.

**Non-goals:**

- Не делаем импорт adb-управляемым (нет триггера импорта в обход UI; добавление debug-ресивера - вне объёма).
- Не трогаем приложение: `ResourceImportActivity` / `SzaResourcesImporter` / intent-filter остаются как есть (нормальный пользовательский путь content://-шаринга рабочий).

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

<Заполняется при переходе Draft → Approved.>

- **Related tickets:** S0484 (/spec-prerelease - тулинг, в котором найден баг)

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

Выбран FIX OPTION (b) из §0: убрать падающую стадию file://-импорта из тулинга, а сам импорт оставить за UI-путём OWNER_TRIGGER, который sweep уже выполняет через mobile-mcp. Option (a) (FileProvider + content://) отвергнут: adb из shell-uid не может выдать grant на content://-URI чужого FileProvider, а MediaStore-URI не несёт кастомный mime intent-filter'а - то есть adb-управляемого рабочего варианта нет.

### 5.1 Основные столпы / модули

- `prerelease-configure.ps1`: стадии reachability + adb-настройки сохранены; стадия импорта заменена на запись делегирования.
- Документация скилла `/spec-prerelease` (`.claude/commands/` + `.github/prompts/` зеркала): импорт описан как OWNER_TRIGGER-путь.

### 5.2 Потоки данных и событий

- Скрипт → JSON-контракт: `settings[].resource-import = delegated-ui`, стадия `import` = SKIP.
- Скилл → UI (mobile-mcp): ввод OWNER_TRIGGER в поле «Default User» → диалог импорта → подтверждение → импортёр читает встроенный `res/xml/sza_resources.xml`.

### 5.3 Точки расширяемости

Если в будущем потребуется adb-управляемый импорт - добавить debug-only триггер (broadcast/команда), не возвращая file://-intent.

---

## 6. Открытые вопросы / Research items

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (внутренний тулинг тестового sweep).

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0484 - /spec-prerelease, тулинг которого содержит баг.

---

## 11. Критерии готовности (strategic-level)

1. `prerelease-configure.ps1` парсится и завершается без стадии, падающей из-за file://-импорта.
2. Запуск скрипта на устройстве с подключённым adb даёт стадию `import` = SKIP (delegated-ui), а не FAIL.
3. Оба зеркала скилла описывают импорт как OWNER_TRIGGER-путь без intent-push как основного.

## Implementation log (2026-06-17)

- `scripts/devtest/prerelease-configure.ps1`: удалены стадии `import-push` / `import-launch` (file://-intent), вместо них запись делегирования `import` = SKIP + `settings[] resource-import = delegated-ui`; удалён ставший мёртвым `$CodePackage`; обновлён `.DESCRIPTION`. Parse OK.
- `.claude/commands/spec-prerelease.md` + `.github/prompts/spec-prerelease.prompt.md`: импорт описан как OWNER_TRIGGER-путь; hard-requirement про `sza.owner.trigger` теперь обязателен; убрано упоминание intent-push как пути по умолчанию.
- Devtest-тулинг изменения - не .kt, debug-теги Timber не применимы; финальная проверка - следующий запуск `/spec-prerelease`.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0492` - создаст `PLAN/S0492_bugfix-prerelease-configure-import-uri/` с фазами.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] §11.2 - next `/spec-prerelease` run on a device confirms the `import` stage reports `SKIP` (delegated-ui), not FAIL. Static path verified (`Add-Stage 'import' 'SKIP'`), live confirmation deferred to the sweep that surfaced the bug.
