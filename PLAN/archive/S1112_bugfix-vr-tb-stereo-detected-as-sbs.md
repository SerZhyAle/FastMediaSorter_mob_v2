# Спецификация: S1112 - VR: TB (over-under) стерео детектится как SBS в StereoDetector

**Ticket:** S1112
**Status:** Archived
**Priority:** 70
**Date:** 2026-07-19
**Tier:** 3 - bugfix (ad-hoc)
**Roadmap entry:** Ad-hoc - парковка находки при device-тесте VR на Quest 3 (2026-07-19)

> **Scope:** Draft. Захват симптома и объективных доказательств с устройства. Root cause локализован предварительно; research/tactics - отдельным шагом.

---

## 0. Захваченный материал (verbatim)

Найдено при device-тесте VR self-test на Quest 3 (noLegal debug), сессия 2026-07-19.

Репорты владельца (verbatim):

> diagnostic_360_stereo_tb.jpg неверно - верхниз лево право все не в фокусе, тяжело описать

> такаяже мешанина с 180

> flat-tb видео ок, а 360 и 180 TB видео - все мешанина

Симптом: в VR self-test (и в любом immersive-воспроизведении) top-bottom (TB / over-under) стерео-ассеты рендерятся как side-by-side. Каждый глаз получает не свою половину кадра -> изображение не сходится в фокус («мешанина», двоит верх-низ/лево-право). Подтверждено на 360 и на 180.

---

## 1. Проблема

`StereoDetector.detectFromFilename` для имён вида `*_stereo_tb` возвращает SBS-раскладку вместо TB/OU. Активный immersive-путь берёт вердикт именно этого детектора (`source=stereo-detector`), поэтому TB-контент рендерится как SBS и не сходится в стерео.

## 2. Доказательства (logcat -b all, Quest 3, 2026-07-19)

Полный лог: `temp/scratch/vr_session_20260719/logcat_full.log`.

Матрица diagnostic-ассетов (что выдал детектор):

- `diagnostic_360_mono.jpg` -> EQUIRECT_360_MONO - верно
- `diagnostic_360_stereo_sbs.jpg` -> EQUIRECT_360_SBS - верно
- `diagnostic_360_stereo_tb.jpg` -> EQUIRECT_360_SBS - НЕВЕРНО (ожидается EQUIRECT_360_OU)
- `diagnostic_180_mono.jpg` -> EQUIRECT_180_MONO - верно
- `diagnostic_180_stereo_sbs.jpg` -> EQUIRECT_180_SBS - верно
- `diagnostic_180_stereo_tb.jpg` -> EQUIRECT_180_SBS - НЕВЕРНО (ожидается 180 TB/OU)

Ключевые строки:

- L14898-14900: `StereoDetector: filename match -> EQUIRECT_360_SBS`; `parseFilenameConfig: diagnostic_360_stereo_tb.jpg -> layout=SIDE_BY_SIDE (source=stereo-detector)`
- L18435-18436: `StereoDetector: filename match -> EQUIRECT_180_SBS`; `parseFilenameConfig: diagnostic_180_stereo_tb.jpg -> layout=SIDE_BY_SIDE (source=stereo-detector)`
- Размеры ассета: `Loaded and queued image: diagnostic_360_stereo_tb.jpg at 4320x4320` (AR=1.0 -> по размерам это OU).
- Настройки стерео (dump): `stereoTrustFilename=true`, `stereoTrustAspectRatio=false` -> кривой вердикт по имени перебивает верный по размерам.

## 3. Root cause (предварительно)

- Триггер - слово `stereo` в имени СФЕРИЧЕСКОГО (360/180) контента: сферическая ветка детектора при наличии `stereo` возвращает SBS и НЕ доходит до проверки `_tb`/`_ou`. Доказательство-триада (device 2026-07-19 + юнит-тесты):
  - `clip_tb_360` (есть `tb`+`360`, НЕТ `stereo`) -> EQUIRECT_360_OU (юнит-тест L373-375) - верно;
  - `*_360_stereo_tb` / `*_180_stereo_tb` (есть `stereo`) -> EQUIRECT_360/180_SBS - НЕВЕРНО (device: и картинки, и видео);
  - `*_flat_tb` (плоское, НЕТ `360/180`, НЕТ `stereo`) -> FLAT/TOP_BOTTOM корректно (L20578-20583, L20757; плоская ветка честно читает `tb`), поэтому владелец видит «flat-tb ок».
  Дефект локализован в СФЕРИЧЕСКОЙ ветке при токене `stereo`; плоская ветка корректна. Подтверждено на реальном видео (`video_360_stereo_tb.mp4`, `video_180_stereo_tb.mp4`), не только на diagnostic-картинках.
- Тот же порядок «специфичные маркеры (`_sbs`,`_tb`,`_lr`,`_ou`) ДО generic `_stereo`» уже был исправлен как **S0290**, но в легаси-парсере `DiagnosticXrActivity.parseFilenameConfig` (§ строки ~598-616), а НЕ в общем `StereoDetector`. Активный путь идёт через `StereoDetector` (`source=stereo-detector`), где старый порядок остался - фактически регрессия-переносом.
- `detectFromDimensions(3840,3840)` по юнит-тесту даёт EQUIRECT_360_OU, но `stereoTrustAspectRatio=false` не даёт размеру исправить имя.

Класс: `StereoDetector.detectFromFilename` (shared, `app_v2/src/main/.../ui/player/StereoDetector.kt` - зеркало пути юнит-теста).

## 4. Пробел в тестах

`StereoDetectorTest` покрывает `clip_tb_360` и `vacation_360_ou` (оба -> OU), но НЕ комбинацию `*_stereo_tb` / `*_stereo_ou`, где слово `stereo` соседствует со специфичным маркером. Добавить кейсы.

## 5. Влияние

- Ломается ВЕСЬ TB/over-under стерео-контент в immersive: diagnostic/тест-ассеты (`*_stereo_tb`) и реальные VR-файлы с `stereo`+`tb` в имени. Не только диагностика - пользовательский контент тоже.
- Не воспроизводится в `standard` (VR-плеер только noLegal).

## 6. Не покрыто существующими тикетами

- **S0771** (mono-vs-stereo для `18VR_*_180x180_3dh.mp4`, SBS-файл) - другая ось (mono/stereo), другой файл; SBS-детекция там как раз корректна.
- **S0291** (TFEL - горизонтальное зеркало текста на том же ассете) - другая ось (зеркалка азимута сферы), уже исправлено; текущий симптом - несведение стерео, не зеркало.
- Дедуп по каталогу (`stereo`,`top-bottom`,`over-under`,`tb`,`StereoDetector`,`side-by-side`) - иных совпадений нет.
