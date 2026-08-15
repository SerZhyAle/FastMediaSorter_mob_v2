# S0892 - WebViewAuthDialogFragment: unsafe WebView teardown, no lifecycle forwarding, leaked account dialog

**Ticket:** S0892
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

<!-- discovered by /spec-all S0877 - 2026-07-02 (out-of-scope finding, CLAUDE.md 3.1) -->
<!-- rescoped by /spec-all against live code - 2026-07-03 (see §0) -->

## 0. Захваченный материал (inbox) + live rescope

**Захвачено:** 2026-07-02 (при S0877); дополнено 2026-07-03 (P2-appendix аудита). **Пересмотрено против живого кода 2026-07-03:**

- **Finding 1 (нет onDestroyView / поля не отнуляются) - УЖЕ ИСПРАВЛЕНО.** `WebViewAuthDialogFragment` теперь имеет `onDestroyView()` (строки 327-332): `webView?.destroy(); webView = null; saveButton = null`. Действий не требуется.
- **Finding 4 (destroy() при attached) - ТЕПЕРЬ РЕАЛЬНА** (draft верно пометил как «сверить»). Добавленный `onDestroyView` зовёт `webView.destroy()` пока WebView ещё в дереве и до `super.onDestroyView()`, без `stopLoading()`/`removeView`/quiesce - destroy() на прикреплённом/загружающемся WebView может уронить нативный рендерер или течь.
- **Finding 2 (нет onPause/onResume forwarding) - реальна.** JS-страница (таймеры, playback, сеть) продолжает работать при уходе хоста в фон.
- **Finding 3 (account-name AlertDialog не lifecycle-managed, строка 218) - реальна.** `MaterialAlertDialogBuilder(..).show()` в `harvestAndDismiss()` не удерживается и не закрывается на teardown -> WindowLeaked при config change.

## 1. Goal (RU)

Привести teardown/lifecycle WebView-фрагмента в порядок: (a) корректный порядок разрушения WebView (detach -> quiesce -> destroy); (b) проброс onPause/onResume в WebView; (c) закрытие account-name диалога на onDestroyView. Оба режима (login + harvest) не регрессируют.

## 2. Constraints

- Do not touch `configureWebView` / harvest-intercept / cookie logic - only lifecycle/teardown.
- Keep the existing S0877 `isAdded && !isDetached` guards.
- Input-restoration of the account dialog across config change is out of scope (needs a retained DialogFragment); this ticket only stops the window leak.

## 3. Phases

### Phase 1 - Safe WebView teardown order (finding 4)

- Step 1.1: In `onDestroyView`, before `destroy()`: `stopLoading()`, `loadUrl("about:blank")` (halt JS/media/network), detach via `(webView?.parent as? ViewGroup)?.removeView(webView)`, `removeAllViews()`, then `destroy()`. Keep the field null-out. `ViewGroup` is already imported.
  - Verification: grep - `onDestroyView` removes the WebView from its parent and quiesces before `destroy()`; `destroy()` is no longer the first call on an attached WebView.

### Phase 2 - Forward onPause/onResume to the WebView (finding 2)

- Step 2.1: Override `onPause` -> `webView?.onPause()` and `onResume` -> `webView?.onResume()` (each after `super`), so a JS-enabled media page suspends timers/playback/rendering while the host is backgrounded and resumes on return.
  - Verification: grep - `onPause`/`onResume` overrides forward to the WebView.

### Phase 3 - Lifecycle-manage the account-name dialog (finding 3)

- Step 3.1: Add `private var accountNameDialog: androidx.appcompat.app.AlertDialog? = null`; assign it from the `MaterialAlertDialogBuilder(..).show()` in `harvestAndDismiss()`; in `onDestroyView` `accountNameDialog?.dismiss(); accountNameDialog = null`.
  - Verification: grep - the account dialog is held and dismissed in `onDestroyView`; no bare `.show()` without a retained reference.

### Phase 4 - Build gate

- Step 4.1: `standard debug` compiles (`a.ps1 fk`). Detekt-clean on `WebViewAuthDialogFragment.kt`.
  - Verification: BUILD SUCCESSFUL; no new detekt findings.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0877 (dismiss guard on detached fragment - added the current `onDestroyView`), S0749 (cookie-clear-before-load login flow - do not regress).

## Related

- S0877 (dismiss guard - source of the current onDestroyView).
- S0749 (login-mode cookie flow).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** BlockNeedUserTest.

Findings 2/3/4 implemented (finding 1 was already fixed by S0877's `onDestroyView`); `standard debug` Kotlin compile PASS; detekt-clean on the touched file.

- **Finding 4 (`onDestroyView` teardown order)** - `destroy()` is no longer the first call on an attached WebView: now `stopLoading()` -> `loadUrl("about:blank")` -> `removeView` from parent -> `removeAllViews()` -> `destroy()`. Fields still null-ed; the account dialog is also dismissed here.
- **Finding 2 (`onPause`/`onResume`)** - added; forward to `webView?.onPause()`/`onResume()` so a JS-enabled media page suspends timers/playback/network while the host is backgrounded and resumes on return.
- **Finding 3 (account-name dialog)** - the `MaterialAlertDialogBuilder(..).show()` result is held in `accountNameDialog` and dismissed in `onDestroyView`, closing the `WindowLeaked` on config change. Input-restoration across recreation is out of scope (would need a retained DialogFragment) - noted in §2.

**Device gate.** WebView lifecycle + a potential destroy-while-attached crash in a user-facing browser-login flow; probe `S0892: webview teardown`. Verify via `/spec-sweep` in BOTH modes:
- Login mode: trigger a browser-login for a link provider, then rotate the device and/or dismiss the dialog -> no crash, no `WindowLeaked` logcat, WebView tears down cleanly (logcat `S0892: webview teardown`).
- Harvest mode: open a gated content page (harvest), background the app while a media page plays -> playback/JS suspends (onPause), returns on foreground; dismiss -> clean teardown.
- Open the "name account" dialog (login mode, after cookies present), then rotate -> no `android.view.WindowLeaked` for the dialog.

**Evidence rung:** static + compile + detekt (P2). Teardown order + lifecycle forwarding are the canonical safe WebView pattern; the destroy-while-attached path is device-observable (crash risk) - deferred to `/spec-sweep`.

### Manual device test - 2026-07-10 (emulator-5554, Android 13 x86_64, build 2.60.7092.225-DEBUG)

Entry point: Settings -> General -> "Authorization and accounts" -> "Saved authorizations" -> AuthSessionsActivity -> "+ Add authorization" -> pick a non-Google provider (Instagram) -> WebViewAuthDialogFragment opens in login mode. No real login performed. Evidence: `temp/S0892/dump1-4.txt`.

- **Sub-check 1 (login mode, rotate + dismiss) - PASS.** Instagram login page loaded in the dialog; rotated portrait->landscape (host `AuthSessionsActivity` handles `configChanges` via `onConfigurationChanged`, so the dialog is not recreated on rotation and stays alive); dismissed via Cancel. expected: teardown probe + no `WindowLeaked` | actual: `D/WebViewAuthDialogFragment: S0892: webview teardown` fired on dismiss, zero `WindowLeaked`, zero FATAL.
- **Sub-check 3 (account-name dialog, rotate) - PASS.** Tapped "Save authorization" with cookies present -> "Name this account" `AlertDialog` shown; rotated to landscape. expected: no `WindowLeaked` for that dialog | actual: dialog survived rotation intact, zero `WindowLeaked`, zero FATAL; subsequent Cancel gave a second clean teardown probe.
- **Sub-check 2 (harvest mode, media page onPause/onResume) - INCONCLUSIVE.** No UI entry point launches this fragment in harvest mode: all three `newInstance` call sites (`AuthSessionsListFragment`, `LinkAutoDownloadResultPresenter`, `ReceiveShareActivity`) pass the default `harvestMode=false`; live content harvest uses the separate headless `InvisibleWebViewExtractionStrategy`, not this dialog. The mode-independent `onPause`/`onResume` WebView forwarding was exercised in login mode (HOME background then foreground with the Instagram page live) with zero crash/leak, but the "media page plays -> playback/JS suspends" harvest-specific behavior cannot be reproduced through the dialog UI on this build.

Session-wide: 2 clean `S0892: webview teardown` probes, zero app `WindowLeaked`/`leaked window`, zero app FATAL.
