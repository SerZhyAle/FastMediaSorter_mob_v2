# S1943 - прогон на устройстве, 2026-08-22

Устройство: Galaxy S21, `RFCR110NBQJ`, Android 15. Сборка: `standard debug`, `versionName=2.60.8221.236-DEBUG`,
установлена и сверена с только что собранной APK. Вердикт: **PASS 4 / FAIL 0 / SKIPPED 0 / UNOBSERVED 0**, падений 0.

## Как воспроизвести

```powershell
pwsh -NoProfile -File ./a.ps1 d
pwsh -NoProfile -File scripts/devtest/adb.ps1 install -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 launch  -DeviceId <id>
# Трансляции -> фильтр «только видео» -> открыть HLS-канал -> дождаться картинки
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "settings put system accelerometer_rotation 0" -DeviceId <id>
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "settings put system user_rotation 1" -DeviceId <id>
# вернуть автоповорот после прогона:
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "settings put system accelerometer_rotation 1" -DeviceId <id>
```

Ожидаемо: изображение остаётся на экране после поворота, воспроизведение не прерывается.
До правки: поверхность оставалась скрытой навсегда, звук продолжался.

## Что наблюдалось

1. **Трансляции, видеоканал (сценарий владельца).** Канал «1+1 Марафон» (`https://dash2.antik.sk/live/1plus1_marathon/playlist.m3u8`).
   До поворота `exo_content_frame` в дереве доступности с границами `0,829..1080,1436`; после поворота - `240,0..2160,1080`,
   картинка на экране, кнопка «Приостановить» - то есть воспроизведение продолжается.
2. **Локальное видео.** `video_large.mp4`, 02:59. Позиция прошла 00:06 -> 00:51 через два поворота без разрыва,
   изображение на экране в обеих ориентациях.
3. **Аудио.** `REC_20260819_005300.m4a`. После поворота `exo_content_frame` в дереве **отсутствует** (для аудио так и надо),
   а на видео - присутствует. Значит перезапустилась именно `configurePlayerViewForMediaType`, а не только флаг видимости.
   Фон с волнами и контролы на месте, чёрной поверхности поверх анимации нет.
4. **Изображение.** `photoView` до и после поворота, `playerView` в дереве отсутствует - восстановление не сработало там,
   где не должно.

## Зонд

`Timber.d("S1943: ...")` сработал 8 раз - по одному на каждый поворот, где поверхность была видима. Первая пара, verbatim:

```text
08-22 12:44:38.901 D/PlayerActivity(10259): S1549: PlayerActivity rebindLayoutForOrientation - layout re-inflated without a recreate
08-22 12:44:39.040 D/PlayerMediaLoaderManager(10259): S1943: player surface restored after rotation re-inflate
```

139 мс между переинфлейтом и восстановлением - ровно ожидаемая последовательность.

## Ошибки в логе

`FATAL EXCEPTION`: 0. В окне поворотов (12:44-12:53) от нашего пакета всего две строки уровня E, обе -
`ImeBackDispatcher: Ime callback not found`, побочный шум от ввода в диалоге фильтра. `ExoPlaybackException` в логе есть,
но все четыре - в 12:41, до тестов, на радио-URL при построении миниатюр экрана трансляций.

## Ловушка, стоившая одного прогона

Первый заход шёл на 3-секундном клипе, который успел доиграть до конца. Дерево доступности показывало `playerView`
видимым, а пиксели были чёрные: на паузе ExoPlayer не отдаёт кадр в новую поверхность. Вердикт по одному дереву,
без снимка экрана, был бы ложным PASS. Проверять этот дефект нужно только на реально идущем воспроизведении.
