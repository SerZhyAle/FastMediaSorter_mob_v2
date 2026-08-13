# Phase 01 - Unique labels + descriptions + help buttons for Send-to settings group

**Strategic spec:** [`../S0463_send-to-commands-toggle-labels.md`](../S0463_send-to-commands-toggle-labels.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-06-16
**Completed:** 2026-06-16
**Depends on:** -
**Steps done:** 4 / 4

---

## Objective

Give every toggle in the "Send file to.." settings group:
1. A unique, descriptive label (fixing the "three Send to app" / "two Keep" duplicates).
2. A brief description subtitle below the label (always visible when the target is available).
3. A help button (?) that opens a TooltipDialog with an explanation.

Pattern already implemented on other settings rows via `SettingsToggleRow.setHelp()`.

---

## Files Touched

| File | New / Modified | Notes |
|------|:--------------:|-------|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTarget.kt` | Modified | Add `subtitleRes`, `helpMessageRes` nullable fields |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | Modified | Fix keep_text/keep_drawing titleRes; add subtitleRes + helpMessageRes per target |
| `app_v2/src/main/res/values/strings.xml` | Modified | 18 new string keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | Same 18 keys, Russian |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | Same 18 keys, Ukrainian |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | `setupSendCommandsGroup()`: PM label for package targets + subtitle + help |

---

## Steps

### Step 01.1 - Extend ShareTarget model

**Files:** `core/share/ShareTarget.kt`
**Depends on:** -

**Prompt for developer:**

> Add two nullable `@StringRes` fields to `ShareTarget` data class (after `applicableTypes`):
> - `val subtitleRes: Int? = null` — brief description shown under the toggle label (when available)
> - `val helpMessageRes: Int? = null` — body text for the help TooltipDialog; title is taken from `titleRes`
>
> Both default to `null` for additive safety: existing registrations compile without changes.
> Keep the KDoc on the class up to date: add one-liner for each new field.

**Verification:**

- `Grep` - `subtitleRes: Int? = null` present in `ShareTarget.kt`.
- `Grep` - `helpMessageRes: Int? = null` present in `ShareTarget.kt`.
- Project still compiles (no callers are broken by the additive field).

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. Added `subtitleRes: Int? = null` and `helpMessageRes: Int? = null` fields to `ShareTarget.kt`. Both nullable with defaults — additive, zero callsite breakage. File: `core/share/ShareTarget.kt`.

---

### Step 01.2 - Add string resources (trilingual)

**Files:** `res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** -

**Prompt for developer:**

> Add the following 18 string keys using `set-android-string.ps1 -Action add` (one lockstep call per key):
>
> **New title strings** (disambiguate keep_text vs keep_drawing):
> - `share_target_title_keep_text` | EN: `Keep: text` | RU: `Keep: текст` | UK: `Keep: текст`
> - `share_target_title_keep_drawing` | EN: `Keep: image` | RU: `Keep: изображение` | UK: `Keep: зображення`
>
> **Description subtitles** (brief, shown under each toggle label):
> - `share_target_desc_system_share` | EN: `Share via system picker` | RU: `Поделиться через системный выбор` | UK: `Поділитися через системний вибір`
> - `share_target_desc_open_in` | EN: `Open in another app` | RU: `Открыть в другом приложении` | UK: `Відкрити в іншому застосунку`
> - `share_target_desc_print` | EN: `Print document or image` | RU: `Распечатать документ или изображение` | UK: `Роздрукувати документ або зображення`
> - `share_target_desc_email` | EN: `Email as attachment` | RU: `Отправить как вложение` | UK: `Надіслати як вкладення`
> - `share_target_desc_keep_text` | EN: `Save text to Keep` | RU: `Сохранить текст в Keep` | UK: `Зберегти текст у Keep`
> - `share_target_desc_keep_drawing` | EN: `Save image to Keep` | RU: `Сохранить изображение в Keep` | UK: `Зберегти зображення у Keep`
> - `share_target_desc_lens` | EN: `Analyse image with Lens` | RU: `Анализ изображения через Lens` | UK: `Аналіз зображення через Lens`
> - `share_target_desc_package_app` | EN: `Send via installed app` | RU: `Отправить через установленное приложение` | UK: `Надіслати через встановлений застосунок`
>
> **Help message bodies** (shown in TooltipDialog; title = toggle label):
> - `share_target_help_system_share` | EN: `Opens the system app chooser. Share the file with any installed app.` | RU: `Открывает системное меню выбора приложения. Поделитесь файлом с любым установленным приложением.` | UK: `Відкриває системне меню вибору застосунку. Поділіться файлом з будь-яким встановленим застосунком.`
> - `share_target_help_open_in` | EN: `Opens the file in any compatible app on this device.` | RU: `Открывает файл в любом совместимом приложении на устройстве.` | UK: `Відкриває файл у будь-якому сумісному застосунку на пристрої.`
> - `share_target_help_print` | EN: `Sends the file to a printer. Works with images, PDFs, text and office documents.` | RU: `Отправляет файл на принтер. Работает с изображениями, PDF, текстом и офисными документами.` | UK: `Надсилає файл на принтер. Підтримуються зображення, PDF, текст та офісні документи.`
> - `share_target_help_email` | EN: `Attaches the file to a new email. A mail app must be installed.` | RU: `Прикладывает файл к новому письму. Требуется почтовое приложение.` | UK: `Прикладає файл до нового листа. Потрібен поштовий застосунок.`
> - `share_target_help_keep_text` | EN: `Saves the text content to Keep as a note. Works with text files only.` | RU: `Сохраняет текстовый контент в Keep как заметку. Только для текстовых файлов.` | UK: `Зберігає текстовий вміст у Keep як нотатку. Лише для текстових файлів.`
> - `share_target_help_keep_drawing` | EN: `Saves the image to Keep. Works with image files only.` | RU: `Сохраняет изображение в Keep. Только для файлов изображений.` | UK: `Зберігає зображення у Keep. Лише для файлів зображень.`
> - `share_target_help_lens` | EN: `Opens the image in Lens. Works with image files only.` | RU: `Открывает изображение в Lens. Только для файлов изображений.` | UK: `Відкриває зображення у Lens. Лише для файлів зображень.`
> - `share_target_help_package_app` | EN: `Sends the file via the messaging app. The app must be installed on this device.` | RU: `Отправляет файл через приложение. Приложение должно быть установлено.` | UK: `Надсилає файл через застосунок. Застосунок має бути встановлений на пристрої.`
>
> Use `set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"` for each key. Do NOT include brand names banned by `compliance/platform-name-denylist.txt` (Instagram, etc.) in any string value.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key share_target_title_keep_text` exits 0 (EN/RU/UK present).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix share_target_title_keep` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix share_target_desc_` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix share_target_help_` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. Added 18 string keys (2 titles, 8 descs, 8 help msgs) × 3 locales via set-android-string.ps1. All `check_strings_localized.ps1` checks exit 0. No denylist violations (no banned brand names). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml.

---

### Step 01.3 - Update ShareTargetModule: fix titles + add subtitle/help per target

**Files:** `core/share/di/ShareTargetModule.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Update `ShareTargetModule.kt` companion object. For each of the 10 `@Provides @IntoSet` functions, add `subtitleRes` and `helpMessageRes` arguments. Also fix the two duplicate title strings:
>
> - `systemShareTarget()`: `titleRes = share_target_title_system_share` (unchanged), `subtitleRes = share_target_desc_system_share`, `helpMessageRes = share_target_help_system_share`
> - `openInTarget()`: `titleRes = share_target_title_open_in` (unchanged), `subtitleRes = share_target_desc_open_in`, `helpMessageRes = share_target_help_open_in`
> - `printTarget()`: `titleRes = menu_print` (unchanged), `subtitleRes = share_target_desc_print`, `helpMessageRes = share_target_help_print`
> - `emailTarget()`: `titleRes = share_target_title_email` (unchanged), `subtitleRes = share_target_desc_email`, `helpMessageRes = share_target_help_email`
> - `keepTextTarget()`: **change** `titleRes` from `text_editor_action_send_keep` → `share_target_title_keep_text`; add `subtitleRes = share_target_desc_keep_text`, `helpMessageRes = share_target_help_keep_text`
> - `keepDrawingTarget()`: **change** `titleRes` from `text_editor_action_send_keep` → `share_target_title_keep_drawing`; add `subtitleRes = share_target_desc_keep_drawing`, `helpMessageRes = share_target_help_keep_drawing`
> - `lensTarget()`: `titleRes = google_lens` (unchanged), `subtitleRes = share_target_desc_lens`, `helpMessageRes = share_target_help_lens`
> - `telegramTarget()`: `titleRes = share_target_title_app` (unchanged, PM-resolved in UI), `subtitleRes = share_target_desc_package_app`, `helpMessageRes = share_target_help_package_app`
> - `whatsAppTarget()`: `titleRes = share_target_title_app` (unchanged), `subtitleRes = share_target_desc_package_app`, `helpMessageRes = share_target_help_package_app`
> - `instagramTarget()`: `titleRes = share_target_title_app` (unchanged), `subtitleRes = share_target_desc_package_app`, `helpMessageRes = share_target_help_package_app`

**Verification:**

- `Grep` - `share_target_title_keep_text` and `share_target_title_keep_drawing` each appear exactly once in `ShareTargetModule.kt`.
- `Grep` - `text_editor_action_send_keep` has zero hits in `ShareTargetModule.kt` (replaced by unique titles).
- `Grep` - `share_target_desc_` count ≥ 10 in `ShareTargetModule.kt` (one per target).
- `Grep` - `share_target_help_` count ≥ 10 in `ShareTargetModule.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. Fixed keep_text/keep_drawing titleRes (unique strings per content type). All 10 targets have subtitleRes + helpMessageRes. `text_editor_action_send_keep` = 0 hits in module. File: `core/share/di/ShareTargetModule.kt`.

---

### Step 01.4 - Wire labels + subtitle + help into setupSendCommandsGroup()

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> Update `PlaybackSettingsFragment.setupSendCommandsGroup()`. Change the `targets.forEach` block so each `SettingsToggleRow` gets:
>
> 1. **Title:** for package-backed targets (`target.packages.isNotEmpty()`), resolve the installed app's label from `PackageManager.getApplicationLabel(getApplicationInfo(pkg, 0))` — iterate `target.packages` until the first installed package, fall back to `getString(target.titleRes)` if none resolves. For logical targets use `getString(target.titleRes)` directly. Mirror the pattern already in `SendToBottomSheet.resolveLabel()` (same fragment-private helper; do not extract to a shared utility — out of scope).
>
> 2. **Subtitle:** when the target is available, show `target.subtitleRes?.let { getString(it) }` instead of `null`. When not available, keep `getString(R.string.settings_send_command_unavailable)`. So:
>    ```kotlin
>    val subtitleText = if (available) {
>        target.subtitleRes?.let { getString(it) }
>    } else {
>        getString(R.string.settings_send_command_unavailable)
>    }
>    setSubtitle(subtitleText)
>    ```
>
> 3. **Help button:** after `setSubtitle(...)`, add:
>    ```kotlin
>    val hm = target.helpMessageRes
>    if (hm != null) setHelp(target.titleRes, hm)
>    ```
>
> Keep `setCheckedSilently`, `setOnCheckedChangeListener`, `isEnabled` assignment, and `layoutParams` unchanged.
> Add a fragment-private helper `fun resolveShareTargetLabel(target: ShareTarget): CharSequence` that encapsulates the PM resolution; call it from the `forEach` block.

**Verification:**

- `Grep` - `resolveShareTargetLabel` defined and called in `PlaybackSettingsFragment.kt`.
- `Grep` - `getApplicationLabel` present in `PlaybackSettingsFragment.kt`.
- `Grep` - `helpMessageRes` and `setHelp(` present in `PlaybackSettingsFragment.kt`.
- `Grep` - `subtitleRes` referenced in `PlaybackSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. Added `import ShareTarget`; updated `setupSendCommandsGroup()` to call `resolveShareTargetLabel()` + show `subtitleRes` when available + wire `setHelp()`; added private `resolveShareTargetLabel()` helper (PM resolution for package targets, fallback to `titleRes`). File: `ui/settings/fragments/PlaybackSettingsFragment.kt` (486 LOC).

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [x] Project compiles (`.\a.ps1 fk` exit 0 in 19s; `verifyNoPlatformNames` PASS).
- [x] `scripts/check_strings_localized.ps1 -KeyPrefix share_target` exits 0 (all 18 keys EN/RU/UK).
- [x] `Grep` - `text_editor_action_send_keep` has 0 hits in `ShareTargetModule.kt`.
- [x] Dev log + catalog regenerated (see finalization step).

---

## Handoff Notes

Settings group "Send file to.." now shows:
- Unique labels (PM-resolved for package apps; distinct Keep text/image titles)
- Description subtitle per toggle (always when available; "Not installed" otherwise)
- Help button (?) per toggle showing a TooltipDialog
