# Спецификация (compact bugfix): S0855 - DiagnosticXrActivity - краш release на неинициализированном lateinit (vr)

**Ticket:** S0855
**Status:** Archived
**Priority:** 80
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: P0 CONFIRMED 3/3 by skeptic panel.

- **[P0] app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:946** - releasePlaybackResources() dereferences lateinit playbackController - every pre-initialization teardown path crashes with UninitializedPropertyAccessException
  - Evidence: Line 167: `private lateinit var playbackController: HudPlaybackController` is initialized at exactly one site - line 259 inside proceedWithInitialization() (verified by grep; the only ::x.isInitialized guards in the file are for surfaceView, lines 1059/1070). releasePlaybackResources() (lines 940-947) ends with the unguarded `playbackController.updatePlayer(null)` (line 946) and is called unconditionally from onPause (line 1106) and onDestroy (line 1112). proceedWithInitialization() only runs after all onCreate gates pass AND the HAND_TRACKING permission flow completes. Concrete crash paths: (a) onCreate early-exit at line 205 (preflight failure), line 216 (`!runtime.isNativeAvailable` - deterministic on any device without the arm64 native lib), or line 220 via prepareLaunchMedia() failures (GIF -> NotYetSupported line 411, invalid URI line 422): each calls deliverReturnAndFinish -> finish() (synchronously at line 1172 for ACTIVITY_RESULT mode, so the framework jumps straight to onDestroy -> line 1112 -> crash; or via scheduleHostFinish's posted finish() line 1272-1276, landing in onPause -> line 1106 -> crash). (b) First launch with com.oculus.permission.HAND_TRACKING not granted: checkHandTrackingPermission (line 224) -> requestPermissions (lines 230-234) shows the system grant dialog, which pauses the host activity before onRequestPermissionsResult can run -> onPause line 1106 -> crash before the user answers. This violates contract item 7: the failure/early-exit teardown path does not release cleanly - the release routine itself throws.
  - Fix hint: Guard the teardown: `if (::playbackController.isInitialized) playbackController.updatePlayer(null)` (or make the field nullable). Same defensive pattern already used for surfaceView at lines 1059/1070.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

DiagnosticXrActivity - краш release на неинициализированном lateinit (vr). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`releasePlaybackResources()` (DiagnosticXrActivity.kt) безусловно дереференсил `lateinit var playbackController`, который инициализируется только внутри `proceedWithInitialization()` - после прохождения всех onCreate-гейтов (preflight, `runtime.isNativeAvailable`, `prepareLaunchMedia()`) и разрешения `HAND_TRACKING`. Любой ранний выход из `onCreate` (через `deliverReturnAndFinish` -> `finish()` либо отложенный `scheduleHostFinish`) приводил к `onPause`/`onDestroy` до инициализации поля - `releasePlaybackResources()` падал с `UninitializedPropertyAccessException`.

---

## 3. Исправление

Добавлен guard по аналогии с существующим паттерном для `surfaceView` (строки 1059/1070): `releasePlaybackResources()` теперь вызывает `playbackController.updatePlayer(null)` только внутри `if (::playbackController.isInitialized)` (DiagnosticXrActivity.kt:946-948). Остальные обращения к `playbackController` (строки 264-275, 935) остаются безусловными - они достижимы только из колбэков `HudInteractionDispatcher` и `startVideoPlayback()`, оба живут исключительно после `proceedWithInitialization()`, где поле уже инициализировано.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- Статический разбор: единственная точка инициализации (`proceedWithInitialization()` line 259) подтверждена grep-ом; единственный дереференс без guard устранён.
- `standard debug` compile - PASS (см. dev-log запись S0855 @ 2026-07-02 15:23:42).
- Verified path: любой ранний `finish()` до инициализации `playbackController` больше не проходит через безусловный `updatePlayer(null)`.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Checks: guard present at DiagnosticXrActivity.kt:946-948 (isInitialized) - PASS. Single init site line 259 - PASS. Other `playbackController` call sites (264-275, 935) confirmed reachable only post-init - PASS. Dev log entry present (S0855 @ 15:23:42) - PASS. Debug-tag invariant (status not BlockNeedUserTest, zero `Timber.d("S0855:` tags) - PASS. §2/§3/§4 filled with root cause/fix/verification (were placeholders, patched inline this audit) - PASS x3. FEATURES trilingual - EXEMPT (internal crash fix, no user-visible capability).

### Manual / on-device

- [ ] None - static fix, static verification sufficient (guard mirrors existing surfaceView pattern).

