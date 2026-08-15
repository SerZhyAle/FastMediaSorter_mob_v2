# S1257 - The user guide says the `vr` store build has no immersion; the source suggests otherwise

**Status:** Archived
**Priority:** 45

## 0. Raw capture

Found 2026-07-28 while implementing **S1233**, during the mandatory document-registry pass. Not part of that ticket's scope.

`docs/HOW_TO.md:167` (and its RU/UK twins), in the "Full immersion" walkthrough:

> Full immersion needs the XR/noLegal sideload build .. and a Quest or other OpenXR headset - the Meta Horizon Store / Google Play `vr` build does not have it wired up yet.

The same page's step 2 already restricts immersion to "XR/noLegal sideload build only".

## 1. Why it looks wrong

Three findings from the build files and source, each verified rather than inferred:

- `src/vr/` is the **`vr` flavor's own source set**, mounted by AGP's flavor-name convention. `MediaCapabilitiesModule` exists once per flavor under `src/standard`, `src/lite`, `src/photos`, `src/legacy` and `src/vr`, with no `noLegal` copy - `noLegal` borrows the vr directory explicitly at `build.gradle.kts:611`. So `DiagnosticXrActivity`, `ImmersiveBrowseActivity` and `XrDetectionFacadeImpl` compile into **both** flavors.
- A `vr`-flavor compile succeeds and actually runs `compileVrDebugKotlin` (`check-standard-fast.ps1 -Mode Code -Flavor Vr`), so those classes are not excluded some other way.
- `BuildConfig.SUPPORT_VR_PLAYER` is `false` on `vr` and would be the obvious gate, but **nothing reads it**. The only production references assign it into `MediaCapabilities`; `PlayerPlaybackCallbackImpl:233` carries an explicit comment saying to gate on `supportsVrMediaControls` instead, precisely because `supportsVrPlayer` is not the right signal. The VR entry points are gated on `XrDetectionFacade` state, which is runtime OpenXR detection plus the user's master toggle, with no flavor check.

## 2. What is genuinely unknown

Whether the `vr` store build *works* in a headset, which is a different question from whether the code is present. Candidates for a real remaining gap, none of which this capture has checked:

- the OpenXR native `.so` targets and whether `fms_diagnostic_xr` is built for the `vr` flavor (`build.gradle.kts:514` gates the CMake target);
- the manifest entries the immersive activities need under the `vr` flavor's own manifest;
- whether the Horizon Store build was ever installed and tried.

The doc sentence may therefore be **true for a reason other than the one it implies**, or simply stale since S0250. Both are worth knowing: it is the sentence a headset owner reads before deciding which build to install.

### 2.1 Resolved 2026-08-14

The first two candidates are closed by reading the tree; the third is not answerable from it.

- **Native target - not a gap.** `isXrNativeBuildRequested` (`app_v2/build.gradle.kts:178`) reads `-Pfms.xrNative` and, absent that property, defaults to **true** for any task name containing `nolegal` or `vr`. So `assembleVr*` / `bundleVr*` build `fms_diagnostic_xr` by default; the `if` at line 528 is not an off switch for the store build.
- **Manifest - not a gap.** `app_v2/src/vr/AndroidManifest.xml` declares both `DiagnosticXrActivity` and `ImmersiveBrowseActivity` with the `com.oculus.intent.category.VR` filter, plus the Quest and Android XR `uses-feature` entries. It is the `vr` flavor's own manifest by AGP convention, and `noLegal` borrows it through `manifest.srcFile` at line 692 - so the file serves `vr` first and `noLegal` second, not the other way round.
- **DI - not a gap.** The real `XrModule` lives in `src/vr/java/.../core/xr/di/`, the `NoOpXrModule` in `src/vrStub/`. The `vr` flavor mounts its own source set, so it binds the real facade and the real entry gateway.
- **The stated mechanism is false.** The RU and UK "Available in" lines named the cause outright - `SUPPORT_VR_PLAYER = false`, therefore no immersive view. A grep for `SUPPORT_VR_PLAYER` and `supportsVrPlayer` across `app_v2/src/**/*.kt` returns only the per-flavor `MediaCapabilitiesModule` assignments, the `MediaCapabilities` field declaration, tests, and two comments saying to use `supportsVrMediaControls` instead. Nothing gates an entry point on it. `VR_UI_COMPOSITION_LAYER_ENABLED`, the other flag that differs between `vr` and `noLegal` in `docs/FLAVOR_MATRIX.md`, has no production reader either.
- **Still unknown, and not knowable from the tree:** whether the store build actually runs in a headset. Carried to its own ticket - **Carrier: S1655**.

## 3. Not decided here

This is an inbox capture. Resolving it needs the native-build gate read end to end and, most likely, a `vr` build installed on a headset - neither belongs in S1233.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1233 (тикет, при закрытии которого находка захвачена), S0250 (вероятный источник устаревшей фразы), S1655 (несёт оставшуюся проверку в шлеме)
- **Communication policy:** формулировка правится в `docs/HOW_TO.md` и обеих переводных копиях; заявляется только то, что доказано исходниками, без утверждений о результатах на устройстве

## 4. Related

- **S1233** - the ticket during which this surfaced; it recorded the two-flavor source-set fact.
- **S0250** - archived `vrUnlicensed` and moved the sideload VR surface to `noLegal`; the likeliest origin of the sentence.
- **S0386** - native attach on the `vr` flavor, adjacent territory.
- **S1655** - carries the one question this ticket could not answer: does the store build run in a headset.

---

## Last Audit

**Дата:** 2026-08-14. **Вердикт:** Verified.

**Что изменено.** Три утверждения в каждой из трёх локалей `docs/HOW_TO*.md`, все в блоке «OpenXR VR Immersive Cinema»:

- строка «Available in» больше не говорит «только `noLegal`» и не называет ложную причину (`SUPPORT_VR_PLAYER = false`); теперь названы обе сборки и настоящее условие входа - обнаруженный OpenXR-шлем плюс общий переключатель VR;
- шаг 2 быстрого пути: «(только сборка XR/noLegal sideload)» -> «(сборка `vr` или XR/noLegal)»;
- пункт разбора сценария больше не утверждает, что в магазинной сборке `vr` это «пока не подключено».

**Чего намеренно НЕ сделано.** Ни одна из новых формулировок не утверждает, что сборка `vr` проверена в шлеме. Это не проверялось и проверено быть не могло - см. S1655. Текст говорит только то, что доказано исходниками: иммерсивный вид в сборке есть, и вход в него не зависит от флейвора.

**Доказательства.**

- `app_v2/build.gradle.kts:178` - `isXrNativeBuildRequested` по умолчанию true для задач с `vr` в имени; нативная цель собирается.
- `app_v2/src/vr/AndroidManifest.xml` - обе иммерсивные Activity объявлены; `build.gradle.kts:692` показывает, что этот же файл одалживает `noLegal`.
- `app_v2/src/vr/java/.../core/xr/di/XrModule.kt` против `app_v2/src/vrStub/java/.../core/xr/di/NoOpXrModule.kt` - реальная привязка достаётся флейвору `vr`.
- grep `SUPPORT_VR_PLAYER|supportsVrPlayer` по `app_v2/src/**/*.kt` - ни одного производственного потребителя, кроме присваивания в `MediaCapabilities`; `PlayerPlaybackCallbackImpl.kt:233` прямо говорит гейтить по `supportsVrMediaControls`.
- `docs/FLAVOR_MATRIX.md:41,45` - сгенерированная сетка подтверждает, что `SUPPORT_VR_PLAYER` и `VR_UI_COMPOSITION_LAYER_ENABLED` различаются между `vr` и `noLegal`; ни один из флагов не читается кодом, поэтому различие не функционально.
- Закрытие: `post-change.ps1 -ChangeType Doc -ScopeToFile` - PASS.
