# Стратегическая спецификация: S0934 - Готовность к Android 16/17: opt-out от игнорирования portrait-lock экрана камеры на больших экранах

**Ticket:** S0934
**Status:** Archived
**Priority:** 20
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - Play Console recommendation (release 2.60.7031.316), forward-looking (триггерится только при targetSdk 36)
**Tactical spec:** `PLAN/S0934_targetsdk36-orientation-restriction-optout/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

**Текст (Play Console, вербатим):**

> Remove resizability and orientation restrictions in your app to support large screen devices.
> From Android 16, Android will ignore resizability and orientation restrictions for large screen devices, such as foldables and tablets. This may lead to layout and usability issues for your users.
> We detected the following resizability and orientation restrictions in your app:
> `<activity android:name="com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity" android:screenOrientation="PORTRAIT" />`
> To improve the user experience for your app, remove these restrictions and check that your app layouts work on various screen sizes and orientations by testing on Android 16 and below.
> Category: User experience. Release name: 2.60.7031.316.

**Находки агента при разборе (контекст для research-фазы):**

- Буквальную рекомендацию Play («remove these restrictions») выполнять НЕЛЬЗЯ: portrait-lock экрана камеры - намеренное решение. ADR в S0754 явно рассмотрел и отклонил раздельные portrait/landscape раскладки для всего экрана камеры; S0918 усилил lock ради Play device-reach; S0924 (создан 2026-07-04) заново подтвердил, что владелец отклонил снятие lock (вариант B) - видоискатель и контролы остаются portrait-locked.
- Текущая сборка НЕ затронута: `targetSdk = 35` (`app_v2/build.gradle.kts`). Поведение «игнорировать orientation/resizability restrictions на дисплеях sw >= 600dp» включается только для приложений с `targetSdk = 36` (Android 16). Play предупреждает проактивно.
- Device-reach уже защищён отдельно (S0918): `android.hardware.screen.portrait` и `.landscape` объявлены `required="false"` в `src/main/AndroidManifest.xml`. Это ортогонально данному тикету (device-reach filter != поведение lock на большом экране).
- Точный механизм opt-out (подтверждено на developer.android.com, 2026-07-04): для `targetSdk = 36` объявить манифест-property, чтобы сохранить прежнее (portrait-locked) поведение на больших экранах -
  `<property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true" />`
  Можно на уровне `<activity>` (только `CameraCaptureActivity`) или `<application>`. Значение `true` = opt-out (compat mode, старое поведение).
- Срок жизни opt-out ограничен: на `targetSdk = 37` (Android 17) opt-out удаляется, restrictions ВСЕГДА игнорируются на sw >= 600dp. Тогда portrait-lock экрана камеры на планшетах/раскладушках станет неотменяемым системно - потребуется либо принять landscape (пересмотр S0754), либо иное решение. Это за горизонтом данного тикета, но зафиксировать нужно.

**Предварительный объём (уточнить в §1-§11 при `/spec-update` или `/spec-tech`):**

- Тикет активируется при следующем поднятии `targetSdk` до 36. Пока `targetSdk = 35` - работы нет, тикет висит как памятка/gate на targetSdk-bump.
- При bump до 36: добавить `<property PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY value=true>` на `CameraCaptureActivity` (минимально-точечно, не на всё приложение - остальные экраны адаптивны и должны выиграть от нового поведения). Device-test на планшете/раскладушке (sw >= 600dp): убедиться, что видоискатель остаётся portrait, layout не ломается.
- `src/vr/AndroidManifest.xml` landscape-lock отдельной активности - проверить, нужен ли симметричный opt-out для VR/noLegal (аналогично тому, как S0918 пришлось делать симметричный `screen.landscape`).
- Дальний горизонт (targetSdk 37): отдельный research - принять ли landscape для экрана камеры или иное. Не в объёме S0934.
- Связанные тикеты: S0754 (ADR portrait-lock), S0918 (device-reach implied feature), S0924 (поворот только диалога настроек камеры).

---

## 1. Проблема

- Буквальная рекомендация Play («remove these restrictions») конфликтует с намеренным portrait-lock экрана камеры (S0754/S0918/S0924).
- Со сборки под `targetSdk = 36` (Android 16) система игнорирует orientation/resizability restrictions на дисплеях sw >= 600dp: окно активности принудительно получает ландшафтную форму, pillarbox не применяется.
- Экран камеры к такому reshape не готов: layout один портретный (`res/layout/activity_camera_capture.xml`, `-land` нет), а UX неподвижных контролов держится на паттерне «крутим только иконки» (S0844, `CameraOrientationManager` + `CameraOverlayRotationManager`), который предполагает портретную форму окна. При ландшафтном окне констрейнты пересчитаются -> кнопки (затвор и пр.) сдвинутся.
- Задача: точечный opt-out при переходе на targetSdk 36, сохраняющий compat (letterbox) поведение, чтобы решение S0754 жило до targetSdk 37 без переделки экрана.

---

## 2. Цели

1. Сохранить portrait-lock и неподвижность контролов экрана камеры на больших экранах под targetSdk 36.
2. Сделать это декларативно и точечно (только затронутые активности), не трогая адаптивные экраны, которые должны выиграть от нового поведения.
3. Не оставлять «памятку на будущее»: изменение вносится сразу, инертно при targetSdk 35, само активируется при bump до 36.

**Non-goals:**

- Снятие portrait-lock с экрана камеры (отклонено S0754/S0924).
- Landscape-раскладка всего экрана камеры (горизонт targetSdk 37, отдельный тикет).

---

## 3. Решение

Декларативный opt-out через манифест-property (подтверждено на developer.android.com, 2026-07-04):

- `res` не трогаем. В `src/main/AndroidManifest.xml` на `<activity CameraCaptureActivity>` добавлен дочерний
  `<property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true" />`.
  `value=true` = opt-out (compat mode, прежнее letterbox-поведение) на sw >= 600dp под targetSdk 36.
- Симметрично в `src/vr/AndroidManifest.xml` на `<activity DiagnosticXrActivity>` (landscape-lock, `resizeableActivity=false`) добавлен тот же property - защитно, по уроку S0918 про зеркальный баг. VR-путь под HorizonOS reshape скорее всего не затрагивает, но property инертна и стоит одну строку; при возражении владельца тривиально снимается.
- Точечно, не на `<application>`: остальные экраны адаптивны и должны получить новое поведение.

Почему не «повёрнутый портрет» вручную сейчас: при targetSdk 35/36 letterbox от compat-режима уже даёт неподвижные кнопки бесплатно, а иконки крутит готовый S0844. Само-letterbox своими руками (фиксированный `layout_constraintDimensionRatio` на контейнере) понадобится только на targetSdk 37, когда opt-out уберут - см. §5.

---

## 4. Затронутые файлы

- `app_v2/src/main/AndroidManifest.xml` - property на `CameraCaptureActivity`.
- `app_v2/src/vr/AndroidManifest.xml` - property на `DiagnosticXrActivity` (симметрично).

---

## 5. Дальний горизонт (targetSdk 37) - не в объёме S0934

- На targetSdk 37 opt-out удаляется, restrictions ВСЕГДА игнорируются на sw >= 600dp; letterbox навязать нельзя.
- Рекомендованный путь тогда - **само-letterbox**: обернуть дерево `activity_camera_capture.xml` в контейнер с фиксированным портретным `app:layout_constraintDimensionRatio`, отцентрованный по гайдлайнам (чёрные поля по бокам). Геометрия внутри рамки идентична портрету, кнопки неподвижны, иконки продолжает крутить готовый S0844. Дешевле и безопаснее полноценного `layout-land`.
- Альтернатива (полноценный `layout-land` с переносом панели на trailing-край) - больше работы и ровно тот риск разъезда кнопок, которого избегает владелец.
- Отдельный research + тикет при bump до 37.

---

## 6. Критерии готовности

- Property с точным именем `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` присутствует на обеих активностях (сверено с официальной докой, не с догадкой).
- `.\a.ps1 fr` (resources/manifest) проходит - манифест собирается, merge не ломается.
- Behavioral device-test отложен: поведение включается только под targetSdk 36 на планшете/раскладушке sw >= 600dp. При следующем bump до 36 - device-test: видоискатель остаётся portrait, layout не ломается, кнопки на местах.

---

## 7. Связанные тикеты

- S0754 - ADR portrait-lock экрана камеры.
- S0918 - device-reach implied `screen.portrait/landscape` (ортогонально: фильтр устройств != поведение lock на большом экране); прецедент симметричной правки.
- S0844 - паттерн «фиксированный layout + вращение иконок» (`CameraOrientationManager`, `CameraOverlayRotationManager`).
- S0924 - поворот только диалога настроек камеры; повторно подтвердил отказ от снятия lock.
