# S0573 - Crash: dialog_link_autodownload_progress inflate (MaterialButton theme)

**Ticket:** S0573
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-21
**Tier:** 3 - Bug (ad-hoc)

> Auto-captured via `/spec-draft` (CLAUDE §3.1) while working on S0570.

---

## 0. Raw capture (verbatim evidence)

Crash log: `logs/fastmediasorter_crash_20260621_023706.log`
Build: `2.60.6210.225-NoLegal-DEBUG (260621022)`, thread main.

```
android.view.InflateException: Binary XML file line #42 in .../layout/dialog_link_autodownload_progress: Error inflating class <unknown>
Caused by: java.lang.reflect.InvocationTargetException
  at com.sza.fastmediasorter.ui.share.LinkAutoDownloadProgressDialog.show(LinkAutoDownloadProgressDialog.kt:40)
  at com.sza.fastmediasorter.ui.share.ReceiveShareActivity.processLinkAutoDownload(ReceiveShareActivity.kt:439)
  at ...ReceiveShareActivity$maybeOfferAuthThenDownload$1.invokeSuspend$lambda$0(ReceiveShareActivity.kt:246)
  at ...AccountSelectionManager.selectAccount(AccountSelectionManager.kt:32)
Caused by: java.lang.UnsupportedOperationException: Failed to resolve attribute at index 37: TypedValue{t=0x2/d=0x7f0401e7 a=18}
  theme = ThemeOverlay.Material3.Button.TextButton -> Theme.FastMediaSorter.App -> ThemeOverlay.Material3.DynamicColors.Dark -> Theme.FastMediaSorter.Transparent -> Theme.AppCompat.Empty -> android Theme.DeviceDefault...
  at android.content.res.TypedArray.getDimensionPixelSize
  at com.google.android.material.button.MaterialButton.<init>
```

---

## 1. Симптом

При шаринге ссылки с авто-загрузкой (`ReceiveShareActivity` -> выбор аккаунта -> `processLinkAutoDownload`) показ диалога прогресса `LinkAutoDownloadProgressDialog` падал на инфляции `dialog_link_autodownload_progress` (строка #42).. Корень - `MaterialButton` не мог разрешить требуемый атрибут размерности под темой инфляции диалога, когда цепочка тем шла через `Theme.FastMediaSorter.Transparent`..

## 2. Причина подтверждена

- `ReceiveShareActivity` работает под translucent-темой `Theme.FastMediaSorter.Transparent`.
- Кнопка Cancel в `dialog_link_autodownload_progress.xml` использовала `Widget.FastMediaSorter.Button.DialogCancel`, а этот стиль задаёт `android:minHeight="?attr/dialogActionButtonMinHeight"`.
- Из-за этого `MaterialButton` в `dialog_link_autodownload_progress.xml` падал на inflate с `UnsupportedOperationException: Failed to resolve attribute`.

## 3. 2026-06-21 correction: previous assumption was wrong

- Логи `fastmediasorter_20260621_031454.log` и `fastmediasorter_crash_20260621_031534.log` показали, что предыдущий wrapper на `Theme.FastMediaSorter.App` **не устранил** падение.
- Crash report из build `2.60.6210.313-NoLegal-DEBUG (260621031)` всё ещё падал в `LinkAutoDownloadProgressDialog.show(LinkAutoDownloadProgressDialog.kt:40)`.
- Значит проблема не в отсутствии wrapper как такового, а в самой зависимости этой конкретной кнопки от theme attr `dialogActionButtonMinHeight`.

## 4. Реализация

- Исправлен не только `dialog_link_autodownload_progress`, а сам общий источник риска: стили
  `Widget.FastMediaSorter.Button.DialogConfirm`, `DialogCancel` и `DialogDestructive`.
- В `app_v2/src/main/res/values/themes.xml` их `android:minHeight` больше не идёт через
  `?attr/dialogActionButtonMinHeight`.
- Вместо этого все три стиля используют прямой `@dimen/dialog_action_button_min_height`.
- Это сохраняет единый visual contract диалоговых кнопок, но убирает runtime-зависимость от
  нестабильной theme-attr chain при inflate MaterialButton.

## 5. Audit sweep for the same risk

- `ReceiveShareActivity`:
  - `LinkAutoDownloadProgressDialog`
  - `MaterialAlertDialogBuilder(dialogContext)` в auth-offer / CCT-unavailable
  - `WebViewAuthDialogFragment` custom layout с `DialogConfirm` + `DialogCancel`
- `ResourceImportActivity`:
  - оба `MaterialAlertDialogBuilder(this)` под `Theme.FastMediaSorter.Transparent`
- `CameraQuickCaptureActivity`:
  - `CameraQuickCaptureLaunchManager.showNameDialog()` использует `MaterialAlertDialogBuilder(activity)`
- Прочие transparent host'ы проверены:
  - `QuickAudioRecorderActivity` - dialog inflate path не найден
  - `CameraLaunchActivity` - dialog inflate path не найден
  - `StandalonePlayerDispatcherActivity` - dialog inflate path не найден

## 6. Шаги воспроизведения

1. Поделиться в приложение ссылкой, попадающей в авто-загрузку (link auto-download).
2. Пройти выбор аккаунта (`AccountSelectionManager.selectAccount`).
3. На показе диалога прогресса - краш.

## 7. Затронутые файлы

- `app_v2/src/main/res/values/themes.xml:216-231`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt:34-40`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt:108-114`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceimport/ResourceImportActivity.kt:63-92`
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt:164-173`
- `app_v2/src/main/res/layout/dialog_webview_auth.xml`
- `app_v2/src/main/res/layout-land/dialog_webview_auth.xml`

## 8. Validation (2026-06-21, NO BUILD)

- expected: no dialog action button should resolve `android:minHeight` through `?attr/dialogActionButtonMinHeight`
- actual: all shared dialog action styles now bind `android:minHeight` directly to `@dimen/dialog_action_button_min_height`; every audited transparent-host dialog path inherits the same safe style family
- evidence: static code audit + crash-log audit only (`Get-Content`, `rg`); per user request no `fk`/`fr`/`fc`/APK build was run

## 9. Follow-up

- `S0573` covers the specific logged crash only and is implemented at code level, but still awaits runtime confirmation because this run was `NO BUILD`.
- Broader hardening for every dialog spawned from `ReceiveShareActivity` is tracked separately in `PLAN/S0571_receive_share_dialog_theme_hardening.md`.

---

## Last Audit

**Date:** 2026-06-21
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

The three shared dialog action-button styles (`Widget.FastMediaSorter.Button.DialogConfirm`/`DialogDestructive`/`DialogCancel`) bind `android:minHeight` directly to `@dimen/dialog_action_button_min_height` (themes.xml:221/227/233), not via `?attr/dialogActionButtonMinHeight`. The remaining `dialogActionButtonMinHeight` references (themes.xml:35,47) are theme-attr value definitions, not button-style minHeight bindings - no custom MaterialButton inflate path depends on the unstable attr chain. `@dimen/dialog_action_button_min_height` is defined. themes.xml compiles/packages (validated incidentally by the standard-flavor build run during S0575).

### Manual / on-device

- [ ] Runtime confirmation pending (§9): spec was implemented NO BUILD. Share a link that triggers auto-download -> account selection -> the progress dialog must show without InflateException. Broader transparent-host dialog hardening is tracked in S0571.
