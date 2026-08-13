# Спецификация (compact bugfix): S1197 - Диалог разрешения на хранилище течёт окном при пересоздании MainActivity

**Ticket:** S1197
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-26
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-26 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-26

**Захвачено во время:** S0484 (`/spec-prerelease` sweep)

**Текст:**

Storage-permission dialog leaks its window when MainActivity is recreated (WindowLeaked). Found during /spec-prerelease sweep 2026-07-26 on emulator-5554 (API 35), run log temp/S0484/run_20260726_004338.log line 282.

Symptom: logcat E WindowManager: android.view.WindowLeaked: Activity com.sza.fastmediasorter.ui.main.MainActivity has leaked window com.android.internal.policy.DecorView{82fcc07}[MainActivity] that was originally added here.

Trigger observed: the sweep applied the per-app locale (ru) via `cmd locale set-app-locales` and relaunched; MainActivity was recreated while the first-run storage-permission AlertDialog was on screen. Any configuration-driven recreate (language change, rotation) during that dialog should reproduce it.

Stack (app process pid 4531):
  at android.app.Dialog.show(Dialog.java:352)
  at androidx.appcompat.app.AlertDialog$Builder.show(AlertDialog.java:1008)
  at com.sza.fastmediasorter.ui.main.helpers.MainStoragePermissionsHelper.showStoragePermissionRequestDialog(MainStoragePermissionsHelper.kt:73)
  at com.sza.fastmediasorter.ui.main.helpers.MainStoragePermissionsHelper.checkLocalPermissionsOnStartup(MainStoragePermissionsHelper.kt:46)
  at com.sza.fastmediasorter.ui.main.MainActivity.onResumeWithViews(MainActivity.kt:592)
  at com.sza.fastmediasorter.core.ui.BaseActivity.onCreate$lambda$10(BaseActivity.kt:167)

Root cause (read from working tree): MainStoragePermissionsHelper.showStoragePermissionRequestDialog() builds a MaterialAlertDialogBuilder and calls .show() without retaining the returned AlertDialog. The existing `if (activity.isFinishing || activity.isDestroyed) return` guard at line 61 only covers the creation instant - nothing dismisses the dialog when the Activity is later destroyed, so the DecorView window token outlives the Activity and retains it.

Severity: P1 per docs/CODE_AUDIT_PROTOCOL.md (retained Activity / leaked window). Occurred once in the run; no crash, no user-facing toast (toastCount=0).

Suggested direction (not yet decided): keep the AlertDialog reference in the helper and dismiss it from the Activity's onDestroy, or host the rationale as a DialogFragment so the framework owns its lifecycle.

Evidence: temp/S0484/run_20260726_004338.log (line 282 + 19-frame stack), temp/S0484/log_audit.json. Dedup checked via scripts/spec_catalog/search.ps1 on "WindowLeaked", "leaked window", "MainStoragePermissionsHelper" - no existing records.

---

## 1. Проблема / симптом

Диалог-обоснование запроса доступа к хранилищу показывается на первом запуске из `onResumeWithViews`. Если в этот момент Activity пересоздаётся (смена языка, поворот, любое конфигурационное пересоздание), окно диалога переживает Activity.

Система пишет `E WindowManager: android.view.WindowLeaked ... [MainActivity]` и удерживает ссылку на уничтоженную Activity через токен окна.

Наблюдалось на эмуляторе `emulator-5554` (API 35, standard-debug) во время sweep S0484: применение per-app локали `ru` с последующим перезапуском. Один случай за прогон, без краша и без пользовательского тоста.

Severity P1 по `docs/CODE_AUDIT_PROTOCOL.md`: удержанная Activity.

---

## 2. Корневая причина

`MainStoragePermissionsHelper.showStoragePermissionRequestDialog()` вызывает `.show()` и выбрасывает возвращённый `AlertDialog`.

Никто не владеет диалогом: у helper нет ссылки, значит его некому закрыть при уничтожении Activity.

Гард `if (activity.isFinishing || activity.isDestroyed) return` проверяет только момент создания и ничего не говорит о том, что произойдёт через секунду.

Дополнительно: `onSettingsResult()` сбрасывает `permissionCheckDoneThisSession` и повторно вызывает `checkLocalPermissionsOnStartup()`, поэтому без ссылки на текущий диалог возможен показ второго поверх первого.

---

## 3. Исправление

Владение диалогом переносится в helper по домашнему паттерну проекта (`CompanionConfigImportActivity`: nullable-поле плюс `dismiss()` в `onDestroy`).

- Helper хранит текущий диалог в поле и обнуляет его по `setOnDismissListener`.
- Helper получает публичный метод закрытия, который вызывается хостом из `onDestroy`.
- Повторный вызов при уже показанном диалоге не создаёт второй.

`DialogFragment` отвергнут: он потребовал бы переносить строки, колбэки и `ActivityResultLauncher` в отдельный фрагмент ради одного стартового диалога, тогда как соседние экраны решают ту же задачу трёхстрочным полем.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI scope:** внешний вид, тексты и расположение диалога не меняются - правка только жизненного цикла.
- **Locale scope:** новых строк нет, `strings.xml` не затрагивается.

---

## 4. Проверка

- `.\a.ps1 fk` компилируется.
- На устройстве: чистая установка, дождаться диалога разрешения, сменить язык или повернуть экран, убедиться что `WindowLeaked` в логе отсутствует.
- Probe `Timber.d("S1197: ..")` подтверждает прохождение обеих точек - показа и закрытия.

---

## Phases

### Phase 1 - Own the dialog lifecycle in the helper

- [x] **Step 1.1** - In `MainStoragePermissionsHelper`, add a nullable `AlertDialog` field holding the currently shown rationale dialog.
  - Verification: `grep -n "private var .*AlertDialog" app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt` returns one match.
- [x] **Step 1.2** - In `showStoragePermissionRequestDialog()`, assign the result of `.show()` to the field and clear it from `setOnDismissListener`, so a user-dismissed dialog is not retained.
  - Verification: the method no longer ends in a bare `.show()`; `setOnDismissListener` is present in the same builder chain.
- [x] **Step 1.3** - Return early when a dialog is already showing, so `onSettingsResult()` cannot stack a second rationale over the first.
  - Verification: `isShowing` guard precedes dialog construction.
- [x] **Step 1.4** - Add a public `dismissPendingDialog()` that dismisses the held dialog and nulls the field.
  - Verification: `grep -n "fun dismissPendingDialog" ..MainStoragePermissionsHelper.kt` returns one match.
- [x] **Step 1.5** - Call `permissionsHelper.dismissPendingDialog()` from `MainActivity.onDestroy()` before `super.onDestroy()`, guarding on `::permissionsHelper.isInitialized` because the field is `lateinit` and `onDestroy` can run before `setupViews` on an early finish.
  - Verification: `grep -n "dismissPendingDialog" app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` returns one match inside `onDestroy`.
- [x] **Step 1.6** - Insert the `Timber.d("S1197: ..")` probes at the show and dismiss entry points (tags live only while the ticket is `BlockNeedUserTest`).
  - Verification: two `Timber.d("S1197:` lines present, each at or below 120 characters. Removed again on the transition to `Verified`.

### Phase 2 - Build and on-device verification

- [x] **Step 2.1** - Build `standard debug`.
  - Verification: `BUILD SUCCESSFUL in 1m 11s` (auto-build - PASS).
- [x] **Step 2.2** - Reproduce the original trigger on device: clean install, wait for the rationale dialog, force a configuration recreate, read logcat.
  - Verification: both probes fire and no `WindowLeaked` naming `MainActivity` appears in the run log.

---

## Last Audit

**Дата:** 2026-07-26
**Итог:** Verified
**Устройство:** `emulator-5554`, AVD `Pixel_9`, API 35, standard-debug

### Что изменено

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt` - поле `rationaleDialog`, гард `isShowing`, очистка по `setOnDismissListener`, публичный `dismissPendingDialog()`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` - вызов `dismissPendingDialog()` в начале `onDestroy()` под гардом `::permissionsHelper.isInitialized`.

### Доказательство «до/после»

Один и тот же триггер прогнан дважды на одном эмуляторе: отозвать `MANAGE_EXTERNAL_STORAGE`, запустить, дождаться диалога, сменить локаль приложения (пересоздание Activity).

- До фикса (pid 20143): `WindowLeaked` = 1, стек указывает на `MainStoragePermissionsHelper.kt:73` и `:46` - те же строки, что в исходной находке sweep S0484.
- После фикса (pid 20408): `WindowLeaked` = 0. Probe показа сработал в 01:50:35.526, probe закрытия по уничтожению хоста - в 01:50:50.445.
- `FATAL EXCEPTION` = 0 в обоих прогонах.

Диалог корректно появляется заново в пересозданной Activity (01:50:51.126), потому что разрешение всё ещё не выдано. Поведение для пользователя не изменилось - исчезла только утечка.

### Остаточные замечания

- Probe-теги `Timber.d("S1197:` удалены при переходе из `BlockNeedUserTest`; `assert-no-ticket-logs` - `expected: 0 | actual: 0`. Ссылки на `S1197` в трёх комментариях оставлены намеренно: они объясняют, почему поле и вызов в `onDestroy` существуют.
- Запись в `docs/ALL_FEATURES.jsonl` не добавлена: утечка окна не была наблюдаема пользователем (без краша и без сообщения об ошибке), поэтому новой или изменённой возможности инвентарь не получает.
- Не расширяли объём до аудита всех диалогов проекта: в прогоне sweep S0484 протекло ровно это окно, остальные показы диалогов доказательств утечки не дали.
