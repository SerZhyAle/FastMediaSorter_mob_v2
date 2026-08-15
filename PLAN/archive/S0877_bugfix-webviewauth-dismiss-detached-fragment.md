# Спецификация (compact bugfix): S0877 - WebViewAuthDialogFragment - dismiss отсоединённого фрагмента после config change

**Ticket:** S0877
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: Defect REAL per 2/2 skeptics but severity DOWNGRADED to P2 (not P0).

- **[P0] app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt:297** - ViewModel save callback dismisses a detached fragment after config change - IllegalStateException crash on main thread
  - Evidence: Lines 234-247 pass a fragment-capturing lambda into the VM: `viewModel.saveSessionFromWebView(host = targetHost, ... ) { savedAccountId -> scrubWebViewState(); ...; emitResultAndDismiss(saved = savedAccountId != null, accountId = savedAccountId) }`. WebViewAuthViewModel.kt lines 44-47 run it from a scope that survives configuration change: `viewModelScope.launch { val id = repository.saveSessionFromWebView(host, displayName, cookies, userAgent); onSaved(id) }` (fragment-scoped VM via `by viewModels()` line 34 is retained across recreation, so the coroutine is NOT cancelled). In `emitResultAndDismiss` (lines 285-298) only `parentFragmentManager.setFragmentResult(...)` is inside `runCatching {}` (286-296); `dismissAllowingStateLoss()` on line 297 is OUTSIDE it. Runtime path: user taps Save in the account-name dialog -> Room write suspends on IO -> rotation/multi-window resize destroys the old fragment instance (onDestroyView nulls mDialog, onDetach nulls mFragmentManager; mDismissed stays false because it was never dismissed) -> coroutine resumes on Main and invokes the OLD instance's lambda -> `dismissAllowingStateLoss()` -> DialogFragment.dismissInternal (fragment-ktx 1.6.2, app_v2/build.gradle.kts line 1193) skips the mDialog branch (null), mBackStackId=-1, and unconditionally calls `getParentFragmentManager()` which throws `IllegalStateException: Fragment ... not associated with a fragment manager` - uncaught, main-thread crash. Contract item 7 violation: this failure/exit path has no isAdded guard, unlike the harvest path (lines 156-160) and the cookie callback (line 110) which do guard.
  - Fix hint: Guard the callback body with `if (!isAdded) return@saveSessionFromWebView` (matching the harvest-path guard), or emit the saved accountId through VM state collected lifecycle-aware instead of a fragment-capturing lambda.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

WebViewAuthDialogFragment - dismiss отсоединённого фрагмента после config change. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`viewModel.saveSessionFromWebView(..) { savedAccountId -> .. }` передаёт во VM лямбду, захватившую инстанс фрагмента. `viewModelScope` переживает config change (VM удерживается через `by viewModels()`), поэтому корутина резюмится и зовёт лямбду СТАРОГО (уничтоженного) инстанса. В `emitResultAndDismiss` только `setFragmentResult` обёрнут в `runCatching`; `dismissAllowingStateLoss()` (:297) - нет, и на отсоединённом инстансе `dismissInternal -> getParentFragmentManager()` бросает `IllegalStateException` на main. Harvest-путь (:157) и cookie-колбэк (:110) такой guard имеют - save-колбэк был единственным незащищённым.

---

## 3. Исправление

- Save-колбэк: `scrubWebViewState()` + `Timber.i` оставлены безусловными (глобальный CookieManager + null-safe вызовы - независимы от attach-состояния; куки-гигиена S0749 сохранена), UI-эпилог `emitResultAndDismiss(..)` закрыт guard'ом `if (isAdded && !isDetached)` - тот же паттерн, что и на harvest-пути.
- Сессия к моменту колбэка уже сохранена репозиторием - при detach теряется только UI-эпилог (dismiss + fragment result); диалог после пересоздания закрывается пользователем, аккаунт на месте.
- Глубокая альтернатива (результат через VM state, lifecycle-aware collect) отклонена как несоразмерная P2: один незащищённый путь, паттерн guard'а уже принят в файле.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` - BUILD SUCCESSFUL (2026-07-02, exit 0).
- Девайс-репро нецелесообразен: окно = ротация точно во время Room-записи сессии; доказательство - статическое (guard в единственной уязвимой точке, паттерн зеркалит существующие guard'ы файла).

---

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

- Fix: `WebViewAuthDialogFragment.kt` save-callback - `emitResultAndDismiss` guarded by `isAdded && !isDetached`; cookie scrub + log stay unconditional (fragment-independent, preserves S0749 hygiene).
- All other `emitResultAndDismiss` callsites reviewed: cancel button, name-dialog negative/cancel (user-interaction while attached), harvest path (already guarded) - no other VM-scope-survivor path exists.
- Validation: `.\a.ps1 fk` - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (exit 0).
- Out-of-scope finding parked: S0892 (WebView teardown / onDestroyView missing in the same fragment).

