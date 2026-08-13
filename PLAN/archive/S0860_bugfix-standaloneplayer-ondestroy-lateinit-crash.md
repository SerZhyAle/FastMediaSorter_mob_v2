# Спецификация (compact bugfix): S0860 - StandalonePlayerActivity - onDestroy на lateinit viewManager без guard

**Ticket:** S0860
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED (2026-07-02, dedicated skeptic). Severity: P0 per strict taxonomy (deterministic crash on an exported component), P1 by practical reachability (no in-app flow launches this deprecated fallback host - explicit component targeting only). Confirmed mechanics: viewManager lateinit (:185) assigned only at :266 in setupViews(); probe short-circuit :259-263 finish()+return runs BEFORE assignment; setupViews() executes from BaseActivity binding.root.post{} (BaseActivity.kt:145-155), i.e. after onCreate returned - finish() there unconditionally reaches onDestroy; onDestroy (:445-457) guards lifecycleManager via ::isInitialized (:446) but calls viewManager.release() UNGUARDED at :455, while the ::viewManager.isInitialized pattern exists at :211/:408/:467/:1068-1092 in the same file (omission, not design). Manifest :540 exported=true without permission; DefaultPlayerProbe.isProbe matches bare substring "default_player_probe" (DefaultPlayerProbe.kt:23-24) - reproducible via am start with such a URI. Second trigger: BaseActivity post{}-guard (destroyed within first frame) also leaves viewManager unassigned while onDestroy still runs.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt:455** - onDestroy calls viewManager.release() on uninitialized lateinit - deterministic crash on the probe early-exit path (contract item 7)
  - alt wording: onDestroy() calls lateinit viewManager.release() without isInitialized guard - guaranteed UninitializedPropertyAccessException on early-exit teardown paths
  - Evidence: setupViews() returns BEFORE viewManager is assigned when a default-player probe URI arrives: lines 259-263 `if (isDefaultPlayerProbe) { Timber.d("StandalonePlayer: short-circuiting default-player probe before standalone init"); finish(); return }` precede line 266 `viewManager = StandaloneViewManager(...)`. onDestroy() (lines 445-457) guards the neighbours - line 446 `if (::lifecycleManager.isInitialized) lifecycleManager.onDestroy()`, line 447 `pipManager?.release()` - but line 455 is unguarded: `viewManager.release()` on `private lateinit var viewManager` (line 185). finish() from setupViews still runs the full teardown, so onDestroy throws UninitializedPropertyAccessException. The activity is live and externally reachable: AndroidManifest.xml:540 declares it `android:exported="true"` (no permission), and DefaultPlayerProbe.isProbe (DefaultPlayerProbe.kt:23-24) matches ANY uri whose string contains "default_player_probe". Concrete trigger: explicit component launch (any third-party app or `am start -n .../.ui.player.StandalonePlayerActivity -d file:///x/default_player_probe/a.mp4`) -> setupViews early-return -> onDestroy -> crash. Second trigger for the same line: BaseActivity defers setupViews via `binding.root.post { if (_binding == null || isDestroyed) return@post ... setupViews() }` (BaseActivity.kt:145-155), so an activity destroyed within the first frame never assigns viewManager and onDestroy crashes identically. Crash per taxonomy; graded P1 not P0 because no in-app flow launches this deprecated host (grep: only the manifest references it; probes from DefaultPlayerHelper's chooser resolve to the per-type aliases) - reachability requires explicit component targeting.
  - Fix hint: Guard the call: `if (::viewManager.isInitialized) viewManager.release()` in onDestroy, mirroring the existing `::lifecycleManager.isInitialized` and `::viewManager.isInitialized` guards already used at lines 446 and 211.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

StandalonePlayerActivity - onDestroy на lateinit viewManager без guard. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

- `viewManager` - `private lateinit var` (:185), присваивается только в `setupViews()` (:266). Пробный short-circuit (`isDefaultPlayerProbe -> finish(); return`, :259-263) выполняется ДО присвоения.
- `setupViews()` вызывается из `BaseActivity` через `binding.root.post { .. }` (после возврата onCreate), поэтому `finish()` оттуда безусловно доходит до `onDestroy`.
- `onDestroy()` (:445-457) гвардит `lifecycleManager` через `::isInitialized` (:446), но `viewManager.release()` (:455) - без guard -> `UninitializedPropertyAccessException` на early-exit teardown. Паттерн `::viewManager.isInitialized` уже используется в этом же файле (:211/:408/:467) - это упущение, не дизайн.
- Reachability: Activity `exported=true` без permission (manifest :540); `DefaultPlayerProbe.isProbe` матчит любой URI с подстрокой `default_player_probe` -> воспроизводимо через `am start`. Второй триггер: destroy в первом кадре (post-guard `BaseActivity`) тоже оставляет `viewManager` неприсвоенным.

---

## 3. Исправление

One-line guard in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`, `onDestroy()`.

1. Guard the release: `if (::viewManager.isInitialized) viewManager.release()` (was unguarded at :455), mirroring the adjacent `::lifecycleManager.isInitialized` guard and the `::viewManager.isInitialized` checks already used at :211/:408/:467.
   - Verification: the probe short-circuit / first-frame destroy path reaches `onDestroy` with `viewManager` unassigned and no longer throws `UninitializedPropertyAccessException`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` (standard Kotlin compile) - PASS.
- Static gates `.\a.ps1 fg` (neuroslop, pm-flags, listener, flavor, ticket-log) - PASS.
- Optional on-device (deferred, not a merge gate): `am start -n <pkg>/.ui.player.StandalonePlayerActivity -d file:///x/default_player_probe/a.mp4`; confirm the probe finishes cleanly with no `UninitializedPropertyAccessException` in logcat.

---

## Last Audit

**Date:** 2026-07-02
**Verdict:** Verified
**Method:** static - `compileStandardDebugKotlin` + scoped gates + lifecycle inspection (CODE_AUDIT_PROTOCOL teardown-path trigger). On-device probe-crash regression optional, not a merge gate.

- Fix present: `onDestroy()` now calls `viewManager.release()` only under `if (::viewManager.isInitialized)`, matching the sibling `lifecycleManager` guard.
- Reasoning: on the probe early-exit (`finish()` before `setupViews()` assigns `viewManager` at :266) and first-frame destroy, `onDestroy` previously dereferenced an uninitialized `lateinit`; the guard makes it a no-op. Normal teardown (viewManager assigned) is unaffected.
- No behavior change on the happy path; `standaloneViewManager()` and other `::viewManager.isInitialized` call sites are untouched.

