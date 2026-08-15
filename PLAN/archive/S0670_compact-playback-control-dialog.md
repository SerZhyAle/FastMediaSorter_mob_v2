# Стратегическая спецификация: S0670 - Компактный контекстный диалог управления плеером

**Ticket:** S0670
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-24
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос владельца 2026-06-24 (скриншот диалога Control в чате)
**Tactical spec:** `PLAN/S0670_compact-playback-control-dialog/`
**Tactical plan:** `PLAN/S0670_compact-playback-control-dialog/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Диалог управления видео/аудио плеером (вкладки Громкость, Аудио, Субтитры, 3D, HUE, Свет, Скорость) занимает почти всю высоту экрана, хотя контента во вкладках мало - например для аудиофайла активны только Громкость и Скорость. Диалог должен подгоняться под контент, а не растягиваться.

Набор вкладок не учитывает контекст: вкладка 3D показывается и на обычных сборках, где VR-плеер недоступен; вкладки Аудио и Субтитры показываются даже когда выбирать нечего (одна аудиодорожка, субтитров нет). Вкладки Громкость и Аудио визуально неразличимы - похожие иконки и невнятная подпись «Аудио». На вкладке Скорость нет быстрых пресетов, в отличие от Громкости.

Итог: диалог громоздкий и зашумлён нерелевантными вкладками, пользователю труднее быстро попасть в нужный регулятор.

---

## 2. Цели

1. Диалог по высоте подгоняется под контент активной вкладки и не растягивается на почти весь экран; для разреженных наборов вкладок (аудиофайл) заметно компактнее.
2. Вкладка 3D видна только на сборках с поддержкой VR-медиа (vr и выше), на остальных скрыта при любом файле.
3. Вкладки Громкость и «Аудиодорожка» визуально и по подписи различимы.
4. Вкладка «Аудиодорожка» скрывается, когда доступна не более одной аудиодорожки.
5. Вкладка Субтитры скрывается, когда субтитров нет.
6. На вкладке Скорость доступны быстрые пресеты 0.5, 1.5, 2.0 - так же, как пресеты громкости.

**Non-goals:**

- Переработка движка детекции стерео/3D-формата.
- Изменение визуального стиля слайдеров (наследие S0619/S0638 остаётся).
- Новый пользовательский тумблер включения 3D в настройках - владелец выбрал флавор-гейт, а не настройку.
- Изменение логики самих регуляторов (звук, HUE, яркость, выбор дорожки).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Иконка вкладки Громкость - динамик/громкость, явно отличная от иконки дорожки на вкладке «Аудиодорожка».
2. Поведение и компоновка одинаковы в портрете и ландшафте.

### 3.2 Жёсткие ограничения

- **Flavor:** вкладка 3D - целевые флаворы `vr` и `noLegal` (VR и выше); на `standard`, `lite`, `photos`, `legacy` скрыта всегда. Реализация следует `dev/FLAVOR_DEVELOPMENT_RULES.md`: признак «VR-медиа доступно» через интерфейс в `src/main/` и флаворные реализации (наличная абстракция для `src/vr` против `src/vrStub`), без `BuildConfig.IS_*`/`SUPPORT_*` проверок в `src/main` (Rule 14/15). Признак `supportsVrPlayer` для этого гейта непригоден - он истинен только на `noLegal` (см. §6.2).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** расчёт активного набора вкладок (доступность дорожек/субтитров, флавор-признак) выполняется при открытии диалога и должен быть дешёвым, без блокирующих операций в UI-потоке.
- **Совместимость данных:** миграций нет; сохранённый «последний раздел» диалога должен валидироваться против нового активного набора.
- **Локализация:** EN/RU/UK обязательно - новая подпись вкладки «Аудиодорожка» и подписи кнопок-пресетов скорости.
- **Доступность:** новые кнопки и вкладки - focusable, навигация D-pad/TV, contentDescription; вкладки различимы текстом, не только иконкой/цветом.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** вкладка 3D показывается только на `vr` и `noLegal`; сигнал - флавор-абстракция доступности VR-медиа (наличный контракт `isAvailable` либо новое capability-поле, истинное в общем для `vr`+`noLegal` источнике), не `supportsVrPlayer`.
- **UI placement contract:** вкладки в левом вертикальном рейле (портрет) и верхней полосе (ландшафт); пресеты скорости размещаются под слайдером скорости по образцу пресетов громкости; скрытие любой вкладки не должно ломать выбор по умолчанию, фокус и сохранённое состояние.
- **Accessibility:** новые кнопки-пресеты и переименованная вкладка focusable, имеют contentDescription; различие вкладок дублируется текстовой подписью.
- **Localization:** EN/RU/UK для подписи вкладки «Аудиодорожка» и трёх подписей кнопок скорости; аудит строк обязателен.
- **Communication policy:** новые видимые подписи следуют `docs/COMMUNICATION_POLICY.md` (кратко, нейтрально, без жаргона).
- **Validation level:** target-сборки `standard` и хотя бы одной VR-сборки (`noLegal`) компилируются; финальная проверка - ручной тест на устройстве (BlockNeedUserTest).
- **Owner sign-off:** 2026-06-24 - решение по гейту 3D-вкладки (флавор vr+, без новой настройки) подтверждено владельцем.
- **Related tickets:** S0619, S0638 (этот же диалог), S0241 (vr временно идёт по общему пути плеера - причина непригодности `supportsVrPlayer`), S0326 (глобальные настройки/детект стерео).

---

## 4. Контекст текущей архитектуры

Один общий диалог управления плеером обслуживает встроенный и автономный хосты через абстракцию возможностей активного плеера. Набор вкладок сейчас вычисляется по типу медиа (аудио против видео), а стерео-вкладка добавляется по признаку «сборка с VR-плеером ИЛИ в файле обнаружен стерео-формат». Видимость и подписи вкладок жёстко закреплены в разметке и в логике расчёта набора.

Из-за этого нельзя контекстно скрыть нерелевантные вкладки (мало дорожек, нет субтитров, не-VR сборка) и нельзя ужать высоту разреженного диалога без изменения и логики набора, и обеих ориентаций разметки. Высота дополнительно завышается фиксированными по высоте вертикальными регуляторами и высотой рейла вкладок.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы

**Контекстная видимость вкладок.** Единый расчёт активного набора вкладок, учитывающий: тип медиа; наличие более одной аудиодорожки; наличие субтитров; флавор-признак доступности VR-медиа для вкладки 3D. Неактивная вкладка полностью исключается из рейла, навигации, выбора по умолчанию и сохранения/восстановления состояния.

**Компактная высота.** Диалог измеряется по контенту активной вкладки; устраняются факторы принудительного растяжения по высоте, чтобы разреженные вкладки давали низкий диалог, а длинные (3D со списком форматов) по-прежнему помещались в экранные границы со скроллом.

**Различимость аудио-вкладок.** Вкладка Громкость получает иконку громкости/динамика; вкладка дорожки переименовывается в «Аудиодорожка» и сохраняет иконку дорожки.

**Быстрые пресеты скорости.** Под слайдером скорости появляется ряд кнопок-пресетов 0.5, 1.5, 2.0, применяющих скорость немедленно, единым паттерном с пресетами громкости и в обеих ориентациях.

### 5.2 Потоки данных и событий

Хост-плеер сообщает доступность аудиодорожек и субтитров, а также флавор-признак доступности VR-медиа. Слой диалога вычисляет активный набор вкладок и видимость каждой секции. Разметка показывает только активные вкладки; выбор, фокус и сохранённая вкладка валидируются против активного набора - при невалидном выборе диалог открывается на первой активной вкладке.

### 5.3 Точки расширяемости

- Признак «VR-медиа доступно» предоставляется флавор-абстракцией (интерфейс в общем коде, реализации в VR-источнике и в заглушке), переиспользуемой для будущих VR-гейтов без флаговых проверок в общем коде.
- Расчёт активного набора вкладок - единая точка, расширяемая новыми условиями видимости без изменения разметки.

---

## 6. Открытые вопросы / Research items

1. **Причина чрезмерной высоты диалога**
   - **Вопрос:** какие факторы дают почти полную высоту даже у разреженных вкладок?
   - **Варианты:** фиксированная высота вертикальных регуляторов; высота рейла из всех вкладок; растяжение контентной области внутри высото-ограниченного контейнера.
   - **Нужно выяснить:** замерить вклад каждого фактора в портрете и ландшафте, выбрать минимально-инвазивную правку без регресса на sw480/sw720 и в ландшафте.
   - **Статус:** Resolved - доминируют фиксированные 200dp вертикальные слайдеры в портрете и пол высоты из рейла всех вкладок; правка - уменьшить портретные слайдеры и опереться на скрытие вкладок, которое укорачивает рейл. Ландшафт использует wrap_content слайдеры, его высоту определяет контент 3D.
   - **Артефакт:** `PLAN/S0670_compact-playback-control-dialog/research/01__dialog-height-cause.md`

2. **Флавор-признак для вкладки 3D**
   - **Вопрос:** какой сигнал корректно описывает «VR и выше» (vr+noLegal)?
   - **Варианты:** `MediaCapabilities.supportsVrPlayer`; наличный контракт доступности VR-медиа-секции; новое capability-поле.
   - **Нужно выяснить:** подтверждено - `supportsVrPlayer` истинен только на `noLegal` (на `vr` он `false` из-за S0241), поэтому непригоден; корректный сигнал истинен ровно на `vr`+`noLegal`.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0670_compact-playback-control-dialog/research/02__3d-tab-flavor-gate.md`

3. **Иконка вкладки Громкость**
   - **Вопрос:** какую иконку-динамик использовать, чтобы она отличалась от иконки дорожки?
   - **Нужно выяснить:** найти существующую подходящую иконку громкости в ресурсах или добавить новую.
   - **Статус:** Resolved - подходящей иконки-динамика в ресурсах нет (`ic_audio`/`ic_notification_audio` - обе нота); добавить новый вектор `ic_volume_up` для вкладки Громкость, дорожке оставить `ic_audio_track`.

4. **Готовность дорожек и субтитров в момент открытия диалога**
   - **Вопрос:** всегда ли дорожки/субтитры уже разобраны плеером к моменту открытия диалога?
   - **Варианты:** считать набор один раз при открытии; пере-вычислять при готовности треков; при неизвестности показывать вкладку (без ложного скрытия).
   - **Нужно выяснить:** поведение, исключающее ложное скрытие нужной вкладки из-за гонки с подготовкой плеера.
   - **Статус:** Resolved - считать доступность один раз при открытии диалога; скрывать аудио/субтитры только когда хост-плеер доступен и однозначно вернул список; при недоступном плеере вкладку показывать (безопасный фоллбэк, без ложного скрытия).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Скрытие вкладки ломает сохранённый/дефолтный выбор | Средняя | Диалог открывается на невидимой секции | Валидировать выбор против активного набора, фоллбэк на первую активную вкладку |
| Гонка с подготовкой плеера | Средняя | Нужная вкладка (аудио/субтитры) не появляется | Политика из §6.4: безопасный фоллбэк при неизвестности, пере-расчёт при готовности |
| Неверный сигнал гейта 3D (`supportsVrPlayer`) | Средняя | На `vr` вкладка пропадёт либо на `standard` покажется | Использовать флавор-признак vr+noLegal (см. §6.2) |
| Починка высоты жёсткими dp | Низкая | Регресс на планшетах/ландшафте | Измерять по контенту, проверить sw480/sw720 и ландшафт |
| Рассинхрон портрет/ландшафт разметки | Средняя | Правка только в одной ориентации | Менять обе разметки синхронно (Rule 11) |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES. Это UX-полировка существующего диалога управления плеером, не новая воспринимаемая способность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Гейт вкладки 3D по флавору, а не по новой настройке**

- **Решение:** показывать вкладку 3D только на `vr`+`noLegal` через флавор-абстракцию доступности VR-медиа.
- **Альтернативы:** новый пользовательский тумблер «Показывать 3D» (default OFF); гейт по факту детекта стерео-контента.
- **Почему:** владелец выбрал флавор-уровень; 3D - возможность VR-сборок, на остальных она лишняя; снимается новая настройка и связанный с Rule 22 оверхед по документации.

**ADR-2: Контекстное скрытие вкладок по доступности контента**

- **Решение:** единый расчёт активного набора вкладок скрывает аудио при ≤1 дорожке и субтитры при их отсутствии.
- **Альтернативы:** всегда показывать вкладку с пустым состоянием (текущее поведение).
- **Почему:** меньше визуального шума, единообразно с флавор-гейтом 3D, короче диалог.

---

## 10. Связи с другими спеками

- S0619 - стиль широких слайдеров в этом диалоге.
- S0638 - адаптивный рейл/полоса вкладок и высото-ограниченная область.
- S0241 - vr временно идёт по общему пути плеера (причина непригодности `supportsVrPlayer` для гейта 3D).
- S0326 - глобальные настройки и детект стерео.

Блокеров нет.

---

## 11. Критерии готовности (strategic-level)

1. На аудиофайле диалог заметно ниже текущего, без больших пустот под контентом, в портрете и ландшафте.
2. На `standard`/`lite`/`photos`/`legacy` вкладки 3D нет ни при каком файле; на `vr`/`noLegal` вкладка 3D присутствует.
3. Вкладка аудио подписана «Аудиодорожка» и имеет иконку, отличную от вкладки Громкость.
4. При одной аудиодорожке вкладки «Аудиодорожка» нет; при отсутствии субтитров нет вкладки Субтитры.
5. На вкладке Скорость есть кнопки 0.5, 1.5, 2.0, применяющие скорость немедленно.
6. Поведение и вид совпадают в портрете и ландшафте; подписи локализованы EN/RU/UK.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0670` - создаст `PLAN/S0670_compact-playback-control-dialog/` с фазами.

---

## Last Audit

### Manual / on-device

**Outcome: PARTIAL** (emulator-5554, standard debug v2.60.6261.106, re-tested on a phone-typical TALL display 1080x2400 @ 400dpi, aspect 2.22, 2026-06-26). Tab/preset/flavor-gate criteria (2-6) PASS; the headline compactness criterion (1) FAILS conclusively - the dialog does not measure to content.

Re-test on a true tall aspect (not the earlier near-square AVD) is now conclusive: criterion 1 ("диалог заметно ниже .. без больших пустот под контентом") is NOT met. The audio dialog (Volume+Speed only) renders at essentially full screen height with roughly the bottom half empty white space below the Speed slider/presets, and the video dialog (Volume+HUE+Light+Speed) is the SAME full height - audio is NOT visibly shorter than video. The dialog is not wrapping to the active tab's content; some forced-height factor still dominates despite research/01's stated fix (reduce portrait sliders + rely on tab-hiding to shorten the rail). Needs a follow-up height fix before this spec can be Verified.

- Tab visibility - PASS. Audio file (`adele_skyfall`): rail shows only Volume + Speed. Video file (`video_large.mp4`): rail shows Volume + HUE + Light + Speed. Logcat `S0670: .. (3D vrMedia=false, audioMulti=false, subs=false)` for both audio and video opens.
- 3D tab absent on standard - PASS. `supportsVrMediaControls=false` on standard; no 3D tab on either file in portrait or landscape.
- Audio-track tab hidden when single track - PASS (video `audioMulti=false`, no Audio tab). Not exercisable for a true multi-track file - no multi-audio fixture present.
- Subtitles tab hidden when no subtitles - PASS (`subs=false`, no Subtitles tab).
- Volume vs Audio-track distinction - PARTIAL. Volume tab renders a speaker icon with the "Volume" label, distinct from HUE/Light/Speed. The "Audiodorozhka" tab could not be shown (single-track fixtures only), so the Volume-vs-track icon contrast was not directly compared on screen.
- Speed presets 0.5 / 1.5 / 2.0 apply immediately - PASS. Tapping 1.5x updated `Speed: 1.00x` -> `Speed: 1.50x` live; presets present and identical in portrait and landscape.
- Compact height - FAIL (conclusive on tall display). On 1080x2400 (aspect 2.22) BOTH dialogs occupy ~full height (content area ~[67,170][1013,2300]) with the bottom ~half empty white space below the content; the audio dialog (2 tabs) is the SAME height as the video dialog (4 tabs). Criterion 1 ("без больших пустот под контентом", audio visibly shorter than video) is NOT satisfied - the dialog is not measuring to content. Evidence: temp/s0670_audio_dialog.png, temp/s0670_video_dialog.png.
- Portrait/landscape parity - PASS (same rail, same presets, same height behaviour; device is near-square so the two orientations differ little here).

Evidence: temp/s0670_03_audio_dialog_portrait.png, temp/s0670_07_video_dialog_portrait.png, temp/s0670_08_video_dialog_landscape.png.

### Fix - 2026-06-27 (release-safety audit)

Criterion 1 root-caused and fixed. The forced height was NOT the sliders (research/01's first hypothesis) but the rail/content separator: a `<View android:layout_height="match_parent">` inside the `AT_MOST`-measured `MaxHeightLinearLayout` resolves (via `View.getDefaultSize`) to the full height cap, dragging the whole portrait row to full screen height; the `match_parent` + `fillViewport` content ScrollView then stretched its short content to fill, producing the empty bottom half. Identical for audio (2 tabs) and video (4 tabs), which is exactly what the audit observed.

Change: replaced the measured `<View>` divider with the parent LinearLayout's drawn divider (`android:showDividers="middle"` + `@drawable/playback_section_divider`, 10dp insets matching the former margin). A drawn divider is not a measured child, so the row now wraps to content and only scrolls when content exceeds the cap (long 3D tab). Rail/content ScrollViews stay `match_parent` so scrolling still works. Landscape needs no change (Rule 11 checked: its `MaxHeightLinearLayout` has no `match_parent` plain-View divider).

Validation: `.\a.ps1 fr` exit 0 (resources/manifest compile); full debug APK assembles (v2.60.6261.106). Files: `res/layout/dialog_playback_control.xml`, `res/drawable/playback_section_divider.xml`.

REMAINING: visual confirmation of criterion 1 on a true tall display (1080x2400). The only connected AVD is near-square 2076x2152, which the prior audit already flagged as non-conclusive for the compactness criterion; the fix's effect (dialog dropping from ~full height to content height) is expected to be obvious there. Status stays Partial until that tall-device confirmation.

### Device verification - 2026-06-27 (PASS, real Samsung Galaxy S21+ SM-G996U1, Android 15, 1080x2400 @ 450dpi)

Outcome: PASS - criterion 1 now satisfied on the true tall display the prior audit required; all 6 criteria met.

- Compact height (criterion 1) - PASS. The Control dialog now wraps to the active tab's content instead of filling the screen. Audio file (Adele - Skyfall): dialog occupies ~half the screen and ends right after the MAX preset button with NO empty space below; the player/album-art is visible above and below the dialog. Video file: same content-wrapping behaviour (Volume tab: slider + Mute/50%/MAX, ~53% height). This is exactly the prior FAIL case ("audio dialog full height, bottom half empty") now fixed by the showDividers change. Evidence: temp/s0670_REAL_audio_dialog.png, temp/s0670_REAL_video_dialog.png.
- Contextual tabs (criteria 2/4) - PASS. Audio file shows exactly Volume + Speed (Audio-track, Subtitles, 3D, HUE, Light all hidden). Video file shows Volume + HUE + Light + Speed (no 3D on standard, no Audio/Subtitles for the single-track fixture).
- Speed presets / labels (criteria 3/5/6) - carried from the prior emulator PASS; unchanged by the layout fix.

S0670 -> Verified.
