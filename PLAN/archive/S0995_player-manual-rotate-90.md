# Стратегическая спецификация: S0995 - Ручной поворот на 90 в проигрывателях

**Ticket:** S0995
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-11
**Tactical spec:** `PLAN/S0995_player-manual-rotate-90/` (будет создан через `/spec-tech`)

<!-- auto-approved by /spec-all - 2026-07-11: поведение задано владельцем детально; подход резолвится из кода. -->
<!-- tactical + research done 2026-07-15 (research/01): механизм видео - CODE-DETERMINABLE (не device-эксперимент, см. обновлённый §6); импл выполнима автономно, device нужен только для финальной верификации (BlockNeedUserTest). -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

При проигрывании видео и изображений для внутреннего проигрывателя и для standaLONE проигрывателей добавить новую команду с низким приоритетом (окажется в ниспадающем меню) "повернуть на 90" с соотвествующей иконкой.
1. Эта функция не редактирт изображение и не зависит от датчика устройста и текущих настроек по поворачиваннию экрана
2. Эта функция накопительная - два раза - 180 , три - -270, 4 - сброс (360)
3. это состояние поворота сохраняется пока открыт проигрыватель (напрмер при переходе к следующим (другим) видео и изображениям), но при выходе из проигрывателя не сохраняется
4. Только изображение / видео поворачивается (по часовой стрелке) , сохраняя пропорции, всё. Для случаев, когда контролы поворачивать не хочется, лезть в настройки не хочется, а хочется досмотреть фильм лёжа

---

## 1. Проблема

- Нет способа быстро повернуть только сам кадр (видео/изображение) в проигрывателе, не редактируя файл и не трогая датчик/настройки поворота экрана.
- Юзкейс: досмотреть контент «лёжа», когда автоповорот выключен, а контролы поворачивать не нужно.

---

## 2. Цели

1. Новая низкоприоритетная команда «Повернуть на 90» (в ниспадающем/overflow меню) с иконкой - во внутреннем и во всех standalone проигрывателях, для видео и изображений.
2. Поворот только визуальный (по часовой), сохраняет пропорции, не редактирует файл, не зависит от датчика/настроек экрана.
3. Накопительно: 90 -> 180 -> 270 -> 360 (сброс).
4. Состояние поворота живёт, пока открыт проигрыватель (переносится на следующий файл), сбрасывается при выходе.

**Non-goals:**

- Деструктивное вращение файла (это уже `RotateImageUseCase` в редакторе изображений - другой путь).
- Экранный поворот по датчику (`toggleRotationSensor`, S0162/S0390 - другой путь).
- Поворот контролов проигрывателя.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Команда низкоприоритетная (в overflow), не загромождает основную панель.

### 3.2 Жёсткие ограничения

- **Flavor:** все, где есть видео/изображения (`VIDEO`/`IMAGES` capability).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** визуальный transform без перекодирования; без аллокаций на кадр.
- **Совместимость данных:** файл не меняется; состояние не персистится.
- **Локализация:** новая строка команды + contentDescription - EN/RU/UK.
- **Доступность:** пункт меню фокусируемый, contentDescription для иконки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0162/S0390 (экранный поворот по датчику - разграничить), S0393 (image-edit rotate - разграничить).

---

## 4. Контекст текущей архитектуры

- Команды проигрывателя - строковые `CommandId` (домен input), резолвятся в действия в обработчике команд плеера; overflow/command-panel строит низкоприоритетные пункты. Константа `CommandId.ROTATE = "view.rotate"` уже объявлена, но к повороту контента НЕ подключена.
- Видео рендерится через `VideoPlayerManager` + Media3 `PlayerView` (в `activity_player_unified` и standalone-хостах); изображения - через image/PhotoView.
- Существующее «вращение» - иное: `RotateImageUseCase` (деструктивный edit) и `toggleRotationSensor` (экранный датчик, `btnEditRotate`, скрыт без акселерометра). Ни то, ни другое не даёт «визуальный поворот только кадра».
- Проигрыватели - «семья»: общий движок + отзеркаливание на каждый хост вручную (внутренний `PlayerActivity` + `*StandaloneActivity`). Новую команду и применение поворота надо провести по всем хостам.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- **Session rotation state:** одно значение «накопленный угол (0/90/180/270)» на сессию проигрывателя, переживает смену файла, сбрасывается на выходе.
- **Apply-слой:** визуальный transform к media-view (не к битмапу, не к экрану): изображение - поворот view + масштаб под контейнер; видео - поворот video-surface с aspect-fit.
- **Команда/меню:** низкоприоритетный `CommandId.ROTATE` -> инкремент угла; иконка + строка; зеркалируется на все хосты семьи.

### 5.2 Потоки данных и событий

- Тап пункта меню / команда -> инкремент session-угла (mod 360) -> применить transform к текущему media-view.
- Переход к следующему файлу -> переприменить текущий угол к новому media-view.
- Выход из проигрывателя -> состояние отбрасывается.

### 5.3 Точки расширяемости

- Тот же session-state пригоден для будущего «отразить»/произвольного угла, если понадобится.

---

## 6. Открытые вопросы / Research items

**RESOLVED (2026-07-15, `research/01__player-family-rotation-map.md`) - все три резолвятся из кода; НЕ owner-gate, device-эксперимент НЕ требуется:**

1. **Механизм поворота ВИДЕО с aspect-fit - RESOLVED (CODE-DETERMINABLE).** Исходная посылка «PlayerView по умолчанию SurfaceView» - неверна для этого проекта: все layout'ы плееров уже `app:surface_type="texture_view"` (4 файла), DRM/secure-пути нет (grep). В обоих движках уже есть production-hardened Media3 `setVideoEffects()` GL-пайплайн (`VideoColorProcessor` + `applyConfiguredVideoEffects()` / `StandaloneViewManager`), а `media3-effect:1.2.1` (с `ScaleAndRotateTransformation`) уже в зависимостях. Механизм - **вариант (3): добавить rotation-эффект в существующий список**, с сохранением 3 задокументированных обходов багов Media3 1.2.1 и ручной компенсацией aspect-fit на 90/270. Не A/B-выбор на устройстве.
2. **Дом session-состояния - RESOLVED.** Два семейства НЕ делят ViewModel -> два владельца: `PlayerViewModel.PlayerState.sessionRotationAngle` (внутренний) и `StandalonePlayerViewModel.StandalonePlayerState.sessionRotationAngle` (standalone, потребляет только `PhotoVideoStandaloneActivity`). Apply-слой - расширить общий `VideoPlayerHandle` (зеркало hue).
3. **Место пункта + иконка - RESOLVED.** Внутренний: новый low-priority overflow-only `PlayerCommand` (паттерн `DRAW_OVERLAY`) с `iconResId`. Standalone: `<item>` в `overflow_menu_standalone_player.xml` (text-only конвенция) + `isVisible`-гейт. Нужна новая drawable `ic_rotate_90` (существующие `ic_rotation_*` - датчик-падлоки, семантически чужие) и строки EN/RU/UK с новым ключом (не `big_btn_short_rotation` - занят датчиком).

**Остаётся только device-ВЕРИФИКАЦИЯ (для `/spec-test-device`, не research):** резёрфейс крэшей effect-пайплайна с 4-м эффектом; чистый aspect-fit кадр-к-кадру на 90/270; взаимодействие с PiP/fullscreen/cast; направление поворота (по часовой) на экране.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Поворот `SurfaceView` для видео невозможен простым transform | Высокая | видео не крутится / чёрный кадр | заранее выбрать `TextureView`/контейнер-подход (§6.1), проверить на устройстве |
| Blast radius «семьи плееров» - регресс рендера на одном из хостов | Средняя | сломанный плеер | зеркалировать и проверять каждый хост (внутренний + standalone), device-verify |
| Aspect-fit при 90 (ширина<->высота) искажает кадр | Средняя | обрезка/растяжение | масштаб под контейнер с сохранением пропорций |

---

## 8. Влияние на пользователя (docs/FEATURES)

- Новая видимая команда проигрывателя. Записать в `docs/ALL_FEATURES.jsonl` при реализации; строка FEATURES - на этапе `/skill-release`.

---

## 9. Архитектурные решения (ADR)

- **ADR-1: визуальный transform, не деструктивное вращение и не экранный датчик.** Разграничение с `RotateImageUseCase` и `toggleRotationSensor` - три разных «поворота» не смешивать.
- **ADR-2: session-scoped состояние, без персиста.** По требованию владельца (п.3 захвата).

---

## 10. Связи с другими спеками

- S0162 / S0390 - экранный поворот по датчику (разграничить, не пересекать).
- S0393 - image-edit (деструктивный rotate) - разграничить.

---

## 11. Критерии готовности (strategic-level)

1. В overflow-меню внутреннего и standalone проигрывателей есть «Повернуть на 90» (видео и изображения).
2. Повторные вызовы дают 90/180/270/360(сброс); крутится только кадр по часовой, пропорции сохранены, контролы не крутятся.
3. Угол переносится на следующий файл в рамках сессии, сбрасывается при выходе.
4. Файл не изменяется; датчик/настройки экрана не затрагиваются.

---

## Last Audit

**Date:** 2026-07-15
**Mode:** full (F2 tactical + F3 impl + F4 build gate)
**Flags:** -
**Outcome:** BlockNeedUserTest (impl complete, green build; runtime device-verification pending)
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 0

Реализовано по 4-фазному тактическому плану, обе семьи плееров; `assembleStandardDebug` - BUILD SUCCESSFUL (независимо перепроверено: 48 задач up-to-date). detekt-clean по всем 16 затронутым файлам; string-audit `rotate_content_90` exit 0 (EN/RU/UK).

- §11.1 команда «Повернуть на 90» - в overflow обеих семей: внутренний `PlayerCommand.ROTATE_CONTENT` (overflow-only, prio 660, гейт `isImage || (isVideo && !isAudio)`), standalone text-item `menu_rotate_content_standalone` (`isVisible = isImage||isVideo`). PASS (статически).
- §11.2 накопительность - `rotateSession90()` = `(angle+90)%360` в обеих ViewModel; изображение через `photoView.setRotationTo`/`imageView` scale-fit; видео через `ScaleAndRotateTransformation` (CW = `360-angle`). PASS (статически); визуальная корректность на 90/270 - device.
- §11.3 перенос угла по сессии - `sessionRotationAngle` в `PlayerState`/`StandalonePlayerState` (не персистится, живёт до destroy VM); re-apply после каждой загрузки. PASS (статически).
- §11.4 файл/датчик не трогаются - `RotateImageUseCase`/`toggleRotationSensor` не вызываются (grep). PASS.
- Video-механизм = вариант (3) research/01: rotation-эффект добавлен в существующий `setVideoEffects()` список в обоих движках; 3 обхода багов Media3 1.2.1 сохранены. PASS (статически).
- Debug-tag инвариант: 2 probe `Timber.d("S0995: …")` (по одному на семью) - присутствуют, статус BlockNeedUserTest -> корректно.
- ALL_FEATURES ADD - ОТЛОЖЕНО до Verified (не заявлять непроверенную на устройстве capability).

### Manual / on-device (device-verification gate - drain via /spec-test-device)

- [x] Видео: поворот effect-пайплайном не роняет `TexturePool.freeTexture` / `Presentation.createForWidthAndHeight`.
- [ ] Aspect-fit на 90/270 чистый кадр-к-кадру (без мерцания/обрезки/растяжения); направление - по часовой.
- [ ] PiP / fullscreen / cast с активным rotate-эффектом.
- [ ] Изображение (photoView и plain imageView в слайдшоу) крутится с сохранением пропорций в обеих семьях; контролы не крутятся.

#### Device-test run 2026-07-15 (emulator-5554, Android 17 / SDK 37, standard-debug v2.60.7151.516)

Verdict: INCONCLUSIVE - crash-half PASS; visible-rotation-half not confirmable on this emulator (Media3 GL effect pipeline does not visibly render here).

Внутренний плеер (`PlayerActivity`), видео `video_sample.mp4` (576x1024). Overflow достигается: fullscreen -> tap-зона COMMAND_PANEL (REG-975 row2/col0) -> `btnOverflowMenu` (⋯) -> пункт «Rotate 90°» (content-desc «Rotate the frame 90 degrees clockwise»). Присутствует. PASS §11.1.

- No-crash (primary regression concern): PASS. Rotate-tap логируется `S0995: internal rotate90 tap -> 90` затем `-> 180` (модульная аккумуляция подтверждена). Effect-список наращивался чисто: rotation only (`effects=1`) -> `hue=142.0 + rotation` (`effects=2`) -> `hue=142.0 + brightness=0.8 + rotation` (`effects=3`). За весь прогон НЕТ `TexturePool.freeTexture`, НЕТ `Presentation.createForWidthAndHeight`, НЕТ FATAL/effect-exception; приложение всё время живо в `PlayerActivity`. Дословный литерал «4-й одновременный эффект» не достигнут (4-й = stereo-crop, только в stereo/VR-режиме); 3 одновременных эффекта чисто - сильное свидетельство стабильности пайплайна. expected: no effect-pipeline crash | actual: no crash at 1/2/3 concurrent effects.
- Visible rotation: НЕ подтверждено на эмуляторе. При активном воспроизведении на угле 90 и 180 кадр остаётся строго вертикальным (не «на боку», не «вверх ногами»). Дискриминирующая проверка: одновременный `hue=142` + `brightness=0.8` тоже не дал явного видимого сдвига цвета - т.е. вывод всего Media3 `setVideoEffects` GL-пайплайна визуально не отражается на GLES этого эмулятора (известное ограничение эмуляторного `DefaultVideoFrameProcessor`), а не баг именно поворота. Обработчик и композиция эффекта корректны по логам. expected: frame rotates CW 90/180/270 | actual: frame stays upright on emulator (GL pipeline not rendered) - requires real-device (Galaxy S21+) confirmation.
- PiP / cast: не проверялось в этом прогоне.
- Изображение (обе семьи): не проверялось - в тест-наборе только 1 видео, изображений нет. Image-путь - view-transform (не GL), должен рендериться и на эмуляторе; отдельный прогон с засеянным изображением.

Evidence: `temp/S0995/` (01 overflow-меню с Rotate 90°; 02 видео angle=180 вертикально при воспроизведении; 03 hue142+rotation effects=2; `logcat_effects_S0995.txt`).

Рекомендация: перегнать video-half на реальном Galaxy S21+ (визуальный поворот + направление CW + aspect-fit 90/270 + PiP/cast) и отдельно image-half с засеянным изображением, прежде чем снимать `BlockNeedUserTest`.

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 3 times: `internal rotate90 tap -> N`.
- Not covered: the remaining criterion is that the frame VISIBLY rotates, which no log line can show. Ticket stays blocked on a real-device visual check.
