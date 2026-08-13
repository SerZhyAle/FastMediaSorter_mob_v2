# Спецификация (compact): S0974 - Устаревшие упоминания VrStereoRenderer в комментариях main src

**Ticket:** S0974
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-10
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-10 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-07

**Текст:**

Stale "VrStereoRenderer" comment references in main src (out of scope for S0967/S0968 docs-only work, code untouched here). Found while verifying DEV_OPS.md VR class names for S0967: `VrStereoRenderer` does not exist as a class anywhere in app_v2/src (grepped) - it only appears in two stale KDoc/inline comments: `VideoPlayerManager.kt:598` and `PlayerMediaLoaderManager.kt:166`. Needs its own verification pass to find what actually applies per-eye VR stereo crop today and correct the two comments to name the real mechanism instead of a phantom class.

---

## 1. Проблема

Два комментария в `src/main` ссылаются на несуществующий класс `VrStereoRenderer` (удалён при рефакторинге VR-рендера в OpenXR-путь; в коде остались только исторические упоминания в CHANGELOG и эти два комментария). Разработчик, отлаживающий stereo-кроп, ищет фантомный класс.

**Реальный механизм (проверено):** при `vrImmersiveActive` main-плеер пропускает 2D-кроп (`singleEyeEnabled=false` -> `MONO`, полный SBS/OU-кадр без кропа в `PanelStereoCropApplier`), а per-eye кроп выполняет OpenXR-рендер vr-флейвора - `DiagnosticXrRuntime` поверх нативного `xr_session` (per-eye swapchains, проекция + stereo layout). `stereoMode` доходит до него через ViewModel-flow.

---

## 2. Цели

1. Комментарии `VideoPlayerManager.kt:598` и `PlayerMediaLoaderManager.kt:166` называют реальный механизм per-eye VR-кропа, а не фантомный `VrStereoRenderer`.

**Non-goals:**

- Изменение реального поведения stereo-кропа - только исправление комментариев под текущий код.

---

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0967 (источник находки; docs-аналог).

---

## Фазы

### Фаза 01 - исправить два комментария

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt:598` - KDoc `applyStereoEffect`: заменить упоминание `VrStereoRenderer` описанием реального механизма (при VR immersive 2D-кроп пропускается, per-eye кроп у OpenXR-рендера vr-флейвора: `DiagnosticXrRuntime` / нативный `xr_session`). Строки <=120 символов (detekt MaxLineLength).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt:166` - inline-комментарий: `VrStereoRenderer picks it up via stereoMode flow` -> реальный OpenXR-рендер vr-флейвора (`DiagnosticXrRuntime`) забирает `stereoMode` через flow.
- Verification: grep `VrStereoRenderer` по `app_v2/src` не находит ссылок (только CHANGELOG); `assembleStandardDebug`/`fk` компилируется; detekt-гейт зелёный.

---

## 4. Проверка

Только статическая: комментарии не содержат `VrStereoRenderer`, называют реальный механизм; grep чист; сборка standard проходит. Пользовательского поведения нет - device-тест не требуется.

---

## Last Audit

**Date:** 2026-07-10
**Outcome:** Verified
**Method:** static (comment-only change; no runtime behavior).

- `VideoPlayerManager.kt` `applyStereoEffect` KDoc: `VrStereoRenderer` -> vr-flavor OpenXR renderer (DiagnosticXrRuntime / native xr_session per-eye swapchains). Names the real mechanism (verified: under `vrImmersiveActive` the 2D crop is skipped and per-eye crop happens in the OpenXR path).
- `PlayerMediaLoaderManager.kt` inline comment: `VrStereoRenderer picks it up via stereoMode flow` -> DiagnosticXrRuntime (vr flavor) via the stereoMode flow.
- `grep -rn VrStereoRenderer app_v2/src` -> no matches (phantom class fully removed from source comments). expected: 0 | actual: 0.
- `a.ps1 fk` (standard Kotlin compile) -> BUILD SUCCESSFUL. expected: PASS | actual: PASS.

No action items.
