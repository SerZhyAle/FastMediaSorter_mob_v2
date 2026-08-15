# Tactical: S0354 - Camera OCR crop-screen language buttons

**Ticket:** S0354
**Strategic:** `PLAN/S0354_camera-ocr-crop-language-buttons.md`
**Status:** Tactical

## Goal

Показать и дать сменить язык OCR и язык перевода прямо в строке действий экрана кадрирования камеры (до распознавания), а на экране результата оставить только смену языка перевода с повторным переводом уже распознанного текста. Языки - общие настройки программы.

## Key decisions (from strategic §6, owner-confirmed)

- Языковой блок внутри строки действий «Повторить»/«OK»: связка `[флаг+код OCR] → [флаг+код перевода]`.
- Стрелка и кнопка целевого языка скрыты, когда перевод недоступен: `settings.enableTranslation == false` или `settings.cameraOcrOnly == true`. Внутри `CameraOcrTranslateActivity` экран существует только во флейворах с переводом, поэтому новый `BuildConfig`-гейт не нужен (Rule 15).
- Выбор сохраняется в общие настройки (`translationSourceLanguage` / `translationTargetLanguage`).
- На экране результата выбор языка OCR убран; смена языка перевода повторно переводит сохранённый исходный текст без нового OCR.
- Языки в подсказке кадрирования не упоминаются.
- Компактная подпись: флаг плюс код в верхнем регистре, через `TranslationLanguageCatalog`.
- Auto-detect остаётся допустимым языком OCR (метка `🌐 AUTO`). Источник в `recognizeAndTranslate` / `translate` передаётся сырым кодом, а не через `languageCodeToMLKit` (который схлопывал `auto` в английский). При `auto` перевод выполняется ОТ языка, определённого по распознанному тексту, а не от английского. Тот же сырой источник используется в пути пере-перевода на экране результата.

## Phases

- Phase 01 - Crop action row: layout cluster, arrow drawable, content-description strings.
- Phase 02 - Crop wiring: FlowManager exposes languages + availability; Activity renders cluster and opens existing picker; selection persists to settings.
- Phase 03 - Results dialog rework: drop source picker; keep target picker; re-translate existing OCR text on target change.
- Phase 04 - Docs, functionality log, catalog sync.

## Affected areas (role-level)

- Camera OCR UI shell + flow manager (`ui/cameraocr`).
- Reused searchable language picker + translation language catalog (`ui/dialog`, `ui/player/helpers`).
- Translation manager text-translate path (`ui/player/helpers/TranslationManager`).
