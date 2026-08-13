# Phase 04 — Initial Population

**Strategic spec:** [`../S0128_activity-catalog.md`](../S0128_activity-catalog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 (query-render-set)
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Run `scan.ps1` for both modules to produce the initial JSONL files, fill all manual fields (`role`, `roleRu`, `tags`, `status`) for every Activity via `set.ps1`, and generate the committed Markdown renders.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] All three scripts (`scan.ps1`, `query.ps1`, `render.ps1`, `set.ps1`) verified working.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/ACTIVITY_CATALOG/app_v2.jsonl` | New | generated |
| `dev/ACTIVITY_CATALOG/wear.jsonl` | New | generated |
| `dev/ACTIVITY_CATALOG/app_v2.md` | New | generated |
| `dev/ACTIVITY_CATALOG/wear.md` | New | generated |

---

## Steps

### Step 04.1 — Run initial scan for both modules

**Files:** `dev/ACTIVITY_CATALOG/app_v2.jsonl`, `dev/ACTIVITY_CATALOG/wear.jsonl`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the following two commands from the project root:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module wear
> ```
> Confirm both complete without errors. Do not edit the JSONL files manually.

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/app_v2.jsonl` exists and is non-empty.
- `Glob` — `dev/ACTIVITY_CATALOG/wear.jsonl` exists and is non-empty.
- `Grep` — `"class":"MainActivity"` present in `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- `Grep` — `"class":"PlayerActivity"` present in `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- `Grep` — `"class":"VrPlayerActivity"` present in `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- `Grep` — `"class":"MainActivity"` present in `dev/ACTIVITY_CATALOG/wear.jsonl`.

**Status:** `[ ]` not done

---

### Step 04.2 — Fill manual fields for all Activities

**Files:** `dev/ACTIVITY_CATALOG/app_v2.jsonl`, `dev/ACTIVITY_CATALOG/wear.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Use `set.ps1` to fill `role`, `roleRu`, `tags`, and `status` for every Activity. Run each command with `-NoRender` to batch the calls; render once at the end in Step 04.3.
>
> **app_v2 Activities — reference values:**
>
> ```powershell
> # MainActivity
> set.ps1 -Module app_v2 -Class "MainActivity" -NoRender `
>   -Role "Primary entry point; hosts resource list and top-level navigation" `
>   -RoleRu "Главный экран; список источников и навигация по разделам" `
>   -Tags "main,launcher,leanback,tv,navigation" -Status tested
>
> # BrowseActivity
> set.ps1 -Module app_v2 -Class "BrowseActivity" -NoRender `
>   -Role "File browser for a single resource; handles sorting, filtering, selection" `
>   -RoleRu "Браузер файлов одного источника; сортировка, фильтр, выделение" `
>   -Tags "browse,files,sort,filter,select" -Status tested
>
> # DuplicatesActivity
> set.ps1 -Module app_v2 -Class "DuplicatesActivity" -NoRender `
>   -Role "Duplicate file detection and cleanup tool" `
>   -RoleRu "Поиск и удаление дубликатов файлов" `
>   -Tags "duplicates,cleanup,detection" -Status tested
>
> # PlayerActivity
> set.ps1 -Module app_v2 -Class "PlayerActivity" -NoRender `
>   -Role "Internal media player; video, audio, images, docs; supports PiP and fullscreen" `
>   -RoleRu "Внутренний плеер: видео, аудио, изображения, документы; PiP и полный экран" `
>   -Tags "player,fullscreen,pip,portrait,landscape,video,audio,image,pdf,epub" -Status tested
>
> # SettingsActivity
> set.ps1 -Module app_v2 -Class "SettingsActivity" -NoRender `
>   -Role "App settings host; delegates to preference fragments" `
>   -RoleRu "Настройки приложения; контейнер для фрагментов настроек" `
>   -Tags "settings,preferences,config" -Status tested
>
> # KeybindingRemapActivity
> set.ps1 -Module app_v2 -Class "KeybindingRemapActivity" -NoRender `
>   -Role "Key binding remapping screen for physical keyboard and remote controls" `
>   -RoleRu "Переназначение клавиш физической клавиатуры и пультов" `
>   -Tags "keybinding,keyboard,remote,remap,settings" -Status tested
>
> # AuthSessionsActivity
> set.ps1 -Module app_v2 -Class "AuthSessionsActivity" -NoRender `
>   -Role "Saved cloud authentication sessions management screen" `
>   -RoleRu "Управление сохранёнными сессиями авторизации в облаке" `
>   -Tags "auth,sessions,cloud,settings,google-drive,dropbox,onedrive" -Status tested
>
> # AddResourceActivity
> set.ps1 -Module app_v2 -Class "AddResourceActivity" -NoRender `
>   -Role "Wizard for adding a new resource (local folder, network share, cloud)" `
>   -RoleRu "Мастер добавления нового источника (папка, сеть, облако)" `
>   -Tags "add,resource,setup,wizard,smb,ftp,sftp,cloud" -Status tested
>
> # ResourceEditorActivity
> set.ps1 -Module app_v2 -Class "ResourceEditorActivity" -NoRender `
>   -Role "Edit settings of an existing resource (name, type, credentials, display options)" `
>   -RoleRu "Редактирование настроек существующего источника" `
>   -Tags "edit,resource,settings,credentials" -Status tested
>
> # WelcomeActivity
> set.ps1 -Module app_v2 -Class "WelcomeActivity" -NoRender `
>   -Role "First-launch onboarding screen; permission requests and initial setup" `
>   -RoleRu "Первый запуск: запрос разрешений и начальная настройка" `
>   -Tags "welcome,onboarding,first-launch,permissions" -Status tested
>
> # GoogleDriveFolderPickerActivity
> set.ps1 -Module app_v2 -Class "GoogleDriveFolderPickerActivity" -NoRender `
>   -Role "Google Drive folder picker for resource setup" `
>   -RoleRu "Выбор папки Google Drive при настройке источника" `
>   -Tags "google-drive,cloud,picker,folder" -Status tested
>
> # DropboxFolderPickerActivity
> set.ps1 -Module app_v2 -Class "DropboxFolderPickerActivity" -NoRender `
>   -Role "Dropbox folder picker for resource setup" `
>   -RoleRu "Выбор папки Dropbox при настройке источника" `
>   -Tags "dropbox,cloud,picker,folder" -Status tested
>
> # OneDriveFolderPickerActivity
> set.ps1 -Module app_v2 -Class "OneDriveFolderPickerActivity" -NoRender `
>   -Role "OneDrive folder picker for resource setup" `
>   -RoleRu "Выбор папки OneDrive при настройке источника" `
>   -Tags "onedrive,cloud,picker,folder,msal" -Status tested
>
> # ResourceLaunchWidgetConfigActivity
> set.ps1 -Module app_v2 -Class "ResourceLaunchWidgetConfigActivity" -NoRender `
>   -Role "Configuration activity for the Resource Launch home-screen widget" `
>   -RoleRu "Настройка виджета быстрого запуска источника на рабочем столе" `
>   -Tags "widget,config,launcher,homescreen" -Status tested
>
> # StandalonePlayerActivity
> set.ps1 -Module app_v2 -Class "StandalonePlayerActivity" -NoRender `
>   -Role "Exported player for external intents (VIEW from file managers); supports PiP" `
>   -RoleRu "Внешний плеер для интентов из файловых менеджеров; поддерживает PiP" `
>   -Tags "player,external,intent,view,fullscreen,pip,standalone" -Status tested
>
> # ReceiveShareActivity
> set.ps1 -Module app_v2 -Class "ReceiveShareActivity" -NoRender `
>   -Role "Handles ACTION_SEND share intents; shows Copy-to dialog" `
>   -RoleRu "Получение файлов через Share; диалог копирования в источник" `
>   -Tags "share,receive,send,copy-to,transparent" -Status tested
>
> # VrPlayerActivity
> set.ps1 -Module app_v2 -Class "VrPlayerActivity" -NoRender `
>   -Role "Immersive VR player for Meta Quest (OpenXR / Horizon OS); landscape singleTask" `
>   -RoleRu "Иммерсивный VR-плеер для Meta Quest (OpenXR); ландшафт singleTask" `
>   -Tags "vr,player,xr,meta,quest,immersive,landscape,openxr" -Status tested
>
> # VrPhoneFallbackActivity
> set.ps1 -Module app_v2 -Class "VrPhoneFallbackActivity" -NoRender `
>   -Role "Fallback shown on non-XR devices when a VR entry-point is invoked" `
>   -RoleRu "Заглушка для не-XR устройств при попытке запустить VR-точку входа" `
>   -Tags "vr,fallback,phone,xr" -Status tested
> ```
>
> **wear Activities:**
> ```powershell
> set.ps1 -Module wear -Class "MainActivity" -NoRender `
>   -Role "Wear OS companion entry point; shows resource list synced from the phone" `
>   -RoleRu "Главный экран Wear OS; список источников, синхронизированных с телефоном" `
>   -Tags "wear,main,launcher,companion,sync" -Status tested
> ```

**Verification:**

- `Grep` — `"role":"Primary entry point` (or substring) present in `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- `Grep` — `"roleRu":` present in `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- `Grep` — `"tags":` present in `dev/ACTIVITY_CATALOG/app_v2.jsonl`.
- Running `query.ps1 -Module app_v2 -MissingRole` returns zero rows (all roles filled).

**Status:** `[ ]` not done

---

### Step 04.3 — Generate and commit initial renders

**Files:** `dev/ACTIVITY_CATALOG/app_v2.md`, `dev/ACTIVITY_CATALOG/wear.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the render script for both modules:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module wear
> ```
> Inspect the generated `app_v2.md` — confirm it contains a table with all Activity rows including `VrPlayerActivity` and at least one row where both `Role (EN)` and `Role (RU)` columns are non-empty. Commit `app_v2.jsonl`, `wear.jsonl`, `app_v2.md`, `wear.md` together.

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/app_v2.md` exists.
- `Glob` — `dev/ACTIVITY_CATALOG/wear.md` exists.
- `Grep` — `VrPlayerActivity` present in `dev/ACTIVITY_CATALOG/app_v2.md`.
- `Grep` — `Role (RU)` present in `dev/ACTIVITY_CATALOG/app_v2.md` (column header present).
- `Grep` — `MainActivity` present in `dev/ACTIVITY_CATALOG/wear.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `query.ps1 -Module app_v2 -MissingRole` returns zero rows.
- [ ] `query.ps1 -Module app_v2 -Search "portrait"` returns at least `PlayerActivity` (tagged).
- [ ] Both `.md` files committed alongside `.jsonl` files.
- [ ] Dev log entries added for all four generated files.

---

## Handoff Notes to Next Phase

Phase 04 produces the committed catalog data. Phase 05 updates project navigation docs and closes out the spec.

---

## Rollback Plan

Revert phase commit(s). Generated JSONL and MD files are derived artifacts — safe to delete and regenerate from source.
