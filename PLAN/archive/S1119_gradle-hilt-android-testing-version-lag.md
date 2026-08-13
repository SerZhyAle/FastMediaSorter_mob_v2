# S1119 - Gradle: hilt-android-testing lags main Hilt version (2.57.2 vs 2.59)

**Status:** Archived
**Priority:** 40
**Date:** 2026-07-19
**Source:** parked from S1075 implementation (doc-vs-gradle pin sync)

## 0. Raw capture

Found while syncing `dev/TECH_REQUIREMENTS.md` to actual Gradle pins for S1075.

**Symptom:** the Dagger-Hilt dependency group in `app_v2/build.gradle.kts` is internally inconsistent: the runtime/compiler/gradle-plugin are on `2.59`, but the AndroidTest artifact is still on `2.57.2`.

**Evidence (`app_v2/build.gradle.kts`):**
- `implementation("com.google.dagger:hilt-android:2.59")`
- `kapt("com.google.dagger:hilt-android-compiler:2.59")`
- `kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.59")`
- `androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")` <- lags
- `build.gradle.kts`: `classpath("com.google.dagger:hilt-android-gradle-plugin:2.59")`

**Why its own ticket:** it is a build-config change (not the doc-drift S1075 targets), and bumping a Hilt test artifact should be proven on a real instrumented/unit build before landing - not folded into a docs-only ticket. Likely just an oversight during the 2.57.2 -> 2.59 bump.

## 1. Next step

Confirm 2.59 has the matching `hilt-android-testing` artifact, bump `androidTestImplementation("com.google.dagger:hilt-android-testing:2.59")`, build to verify, then update the `hilt-android-testing` row in `dev/TECH_REQUIREMENTS.md` (currently documented at the current Gradle truth 2.57.2).

## 2. Plan

**Goal (RU):** Выровнять версию тестового артефакта Hilt с основной группой (2.59), убрав внутреннюю рассогласованность Dagger-Hilt в сборке, и синхронизировать строку в `dev/TECH_REQUIREMENTS.md`. Все Hilt-артефакты Dagger релизятся в lockstep, поэтому `hilt-android-testing:2.59` заведомо существует (2.59 hilt-android/compiler уже резолвятся в текущей сборке).

### Phase 1 - Version alignment

1. Bump `androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")` -> `2.59` in `app_v2/build.gradle.kts`.
   - Verification: grep shows `hilt-android-testing:2.59`; no `hilt-android-testing:2.57.2` remains in `app_v2/build.gradle.kts`.
2. Update `dev/TECH_REQUIREMENTS.md` row `hilt-android-testing | 2.57.2` -> `2.59`.
   - Verification: `pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1` exits 0.
3. Prove resolution + compile of the AndroidTest source set (no device needed - compile proves artifact resolves and APIs match; running instrumented tests would need a device but is out of scope here).
   - Verification: `./gradlew :app_v2:compileStandardDebugAndroidTestKotlin` -> BUILD SUCCESSFUL.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1075 (doc-vs-gradle pin sync, parent of this parked finding).

## Last Audit

**Дата:** 2026-07-19 (`/spec-all` Simple path через `/spec-next`). **Вердикт:** Verified.

- Step 1: `app_v2/build.gradle.kts:1501` -> `hilt-android-testing:2.59`; `2.57.2` в файле не осталось (grep). Вся Dagger-Hilt группа теперь на 2.59 (runtime/compiler/kaptAndroidTest/gradle-plugin/testing).
- Step 2: `dev/TECH_REQUIREMENTS.md:231` -> `2.59`; `assert-doc-pin-drift.ps1` PASS (total 90, fail 0, inconsistent 0).
- Step 3: `./gradlew :app_v2:compileStandardDebugAndroidTestKotlin` -> BUILD SUCCESSFUL in 29s. `kaptStandardDebugAndroidTestKotlin` (генерация Hilt test-компонентов) и compile androidTest прошли с 2.59; ошибок нет (единственный warning - предсуществующий LeakCanary `watch` deprecation, к Hilt отношения не имеет). Резолв артефакта и API-совместимость доказаны без устройства.

Пользовательской способности не добавляет (внутреннее выравнивание тестовой зависимости) - записи в `docs/ALL_FEATURES.jsonl` не требует.
