# S0218 — build-warnings-safe-cleanup

**Ticket:** S0218
**Status:** Verified
**Priority:** 30
**Date:** 2026-05-16
**Tier:** 1 — Quick Win (ad-hoc)

---

## Problem

Каждая отладочная сборка (`.\a d`, `.\a nd`) печатает три устранимых предупреждения, которые шумят в логе наряду с AGP-deprecation каскадом и мешают вычленять реальные проблемы при чтении вывода глазами или через `/log-reader`. Все три варнинга не зависят от kapt→KSP миграции (S0042 Archived, заблокирована Glide+okhttp3) и фиксятся локально без архитектурных решений.

## Approach

- **`app_v2/src/vr/AndroidManifest.xml`** — три активити (`MainActivity`, `SettingsActivity`, `WelcomeActivity`) объявлены с `tools:replace="android:configChanges,android:resizeableActivity"`. В main-манифесте `android:resizeableActivity` отсутствует, поэтому `tools:replace` для этого атрибута бьёт пустоту → варнинг `was tagged at AndroidManifest.xml:0 to replace other declarations but no other declaration present`. Убрать токен `android:resizeableActivity` из `tools:replace` во всех трёх местах; атрибут `android:resizeableActivity="true"` overlay по-прежнему **добавляет** в смерженный манифест (main атрибут отсутствует — конфликта нет). Затрагивает flavor: `vr`, `vrUnlicensed`, `noLegal`.
- **`app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt:389`** — `overridePendingTransition(0, 0)` deprecated с API 34. Вызов уже сидит внутри `if (SDK_INT < TIRAMISU)` (API < 33); замена `overrideActivityTransition` появилась только в API 34, то есть на этой ветке недостижима. Локальный `@Suppress("DEPRECATION")` на функции `onWelcomeLanguageSelected` с однострочным комментарием о причине.
- **`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawEditorPrefs.kt:105`** — `displayMetrics.scaledDensity` deprecated с API 34. Заменить `sp * ctx.resources.displayMetrics.scaledDensity` на `TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.resources.displayMetrics)`. Семантика идентична: `applyDimension` для `COMPLEX_UNIT_SP` возвращает ту же формулу `sp * scaledDensity`, но без прямого обращения к deprecated-полю.

**Out of scope:** AGP-каскад (`android.builtInKotlin=false`, `android.newDsl=false`, obsolete `applicationVariants`/`testVariants`/`unitTestVariants`, `Project.android(BaseAppModuleExtension)`) — упёртый в S0042 (Archived, заблокирован несовместимостью `glide:ksp` 4.16.0 с `okhttp3-integration`); снять только полной kapt→KSP миграцией. Также KAPT options not recognized — внутренний шум Hilt-процессора.

## Done criteria

- `assembleStandardDebug` проходит, и в его выводе **отсутствуют** строки `'fun overridePendingTransition` и `'field scaledDensity`.
- `assembleNoLegalDebug` проходит, и в его выводе **отсутствуют** три варнинга вида `activity#…@android:resizeableActivity was tagged at AndroidManifest.xml:0 to replace other declarations but no other declaration present` для `MainActivity`/`SettingsActivity`/`WelcomeActivity`.
- AGP-каскад (`builtInKotlin`, `newDsl`, obsolete variant API, `Project.android` deprecation, итоговое `Deprecated Gradle features were used`) остаётся — это документировано в §Approach как out of scope.

---

## Last Audit

**Date:** 2026-05-16
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [x] `assembleStandardDebug` — BUILD SUCCESSFUL, grep `scaledDensity`/`overridePendingTransition`: 0 hits (validated during implementation).
- [x] `assembleNoLegalDebug` — BUILD SUCCESSFUL, grep `was tagged at AndroidManifest.xml:0`: 0 hits (validated during implementation).
