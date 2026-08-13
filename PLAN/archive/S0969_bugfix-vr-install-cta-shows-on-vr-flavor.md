# Спецификация (compact bugfix): S0969 - VR install CTA всё ещё показывается на самом `vr` флейворе

**Ticket:** S0969
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-06
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст:**

Found while executing S0965 (VR docs reconciliation), out of that ticket's scope (docs-only).

`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` around line 211-227 (`onStereoDetected`) has this gate:

```kotlin
// S0264: VR install CTA must not appear on VR-capable builds (vr / noLegal).
// Those builds already include VR functionality - prompting the user to install
// "the VR edition" while they are running the VR edition is meaningless noise.
if (mediaCapabilities.supportsVrPlayer) return
```

`mediaCapabilities.supportsVrPlayer` (`BuildConfig.SUPPORT_VR_PLAYER`) is `true` only on `noLegal` today - the `vr` flavor sets it `false` (`app_v2/build.gradle.kts` line ~543, comment: "S0241: keep the VR visual shell/source-set overlay buildable while routing the shared runtime through the same player path as standard until the rewrite lands").

Because the guard checks `supportsVrPlayer` (not `supportsVrMediaControls`, which is the correct "vr + noLegal" signal per project memory `project_supportsvrplayer_nolegal_only`), the early-return at line 218 does NOT trigger on the `vr` flavor. Net effect: today, opening 3D content on the `vr` flavor itself shows a CTA suggesting the user "install the VR edition" - while they are already running it. This is exactly the nonsensical case the S0264 comment says must not happen; it happens today because the code still assumes the pre-S0241 world where `supportsVrPlayer` was true for `vr`.

Not confirmed on-device (found via static code read only) - worth a quick logcat/device check before fixing, but the code logic is unambiguous: `supportsVrPlayer=false` on `vr` + guard checks `supportsVrPlayer` = CTA fires.

Please scaffold a Draft spec capturing this verbatim so it can be picked up later.

---

## 1. Проблема / симптом

На флейворе `vr`, при обнаружении 3D-стерео контента (SBS/OU) в обычном плеере, показывается CTA-диалог «установите VR-редакцию», хотя пользователь уже находится в VR-редакции. Причина - `PlayerPlaybackCallbackImpl.onStereoDetected` гейтит показ CTA по `mediaCapabilities.supportsVrPlayer`, который для `vr`-флейвора сегодня `false` (S0241), а не по `supportsVrMediaControls` (истинный признак «vr или noLegal»).

---

## 2. Корневая причина

`BuildConfig.SUPPORT_VR_PLAYER` не равен true для `vr` начиная с S0241 (временное состояние до иммерсивного rewrite, эпик S0773), но гейт CTA в `PlayerPlaybackCallbackImpl.kt:218` не был обновлён вместе с этим изменением и всё ещё читает `supportsVrPlayer`.

---

## 3. Исправление

Реализовано: в `PlayerPlaybackCallbackImpl.onStereoDetected` (строка 219) гейт заменён с `mediaCapabilities.supportsVrPlayer` на `mediaCapabilities.supportsVrMediaControls` (комментарий S0264/S0969). `supportsVrMediaControls` задаётся литеральным `true` в `src/vr/.../di/MediaCapabilitiesModule.kt` (модуль обслуживает и vr, и noLegal), а на standard/lite/photos/legacy отсутствует -> дефолт `false` (`MediaCapabilities.kt`). Итог: CTA «установить VR-редакцию» скрывается на vr и noLegal, но остаётся на не-VR билдах.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0965 (docs-vr-drift-reconcile-quickpath - источник находки), S0241 (изменил supportsVrPlayer для vr), S0670 (добавил supportsVrMediaControls как правильный сигнал)

---

## 4. Проверка

Статический аудит по всем flavor source-set'ам (working tree): гейт читает `supportsVrMediaControls`; значение `true` только в `src/vr` модуле (vr+noLegal), `false` по дефолту на остальных. Логика однозначна - CTA не может сработать на vr/noLegal и сохраняется на standard/lite/photos/legacy. On-device подтверждение (сборка vr, открытие SBS/OU) - необязательная низкоценная проверка однозначной one-flag правки; не блокирует.

---

## Last Audit

**Дата:** 2026-07-09
**Статус:** Verified (статический аудит кода + flavor-wiring)

- Фикс присутствует: `PlayerPlaybackCallbackImpl.kt:219` гейтит `onStereoDetected` по `mediaCapabilities.supportsVrMediaControls` (не `supportsVrPlayer`).
- Flavor-wiring подтверждён: `supportsVrMediaControls = true` только в `src/vr/.../MediaCapabilitiesModule.kt` (обслуживает vr и noLegal); на standard/lite/photos/legacy поле отсутствует -> дефолт `false` в `MediaCapabilities.kt`.
- Следствие: на vr и noLegal CTA скрыт (bug устранён); на не-VR билдах CTA сохранён (желаемое). Регрессий по другим флейворам нет.
- Probe-тегов `S0969:` в коде нет (0). Правка - одна строка + комментарий, отдельная сборка не требуется (входит в текущие зелёные сборки).
