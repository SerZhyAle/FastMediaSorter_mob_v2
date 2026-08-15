# Tactical plan: S0988 - QR-скан импорта companion `.fmscfg`

**Ticket:** S0988
**Strategic spec:** `PLAN/S0988_qr-scan-companion-import.md`
**Status source:** spec-catalog (не редактировать здесь)

Задача: добавить экран сканирования QR камерой, который отдаёт строку payload в существующий companion-импорт. Downstream (парсер, use-case `import(dto)`, шифрование, TOFU) уже готов - трогаем только приём.

## Ключевые решения (из стратегии + research)

- Декодер: **ZXing** `com.google.zxing:core` (pure-JVM), общий `implementation` - `src/main` компилируется на всех флейворах (Rule 14). НЕ `zxing-android-embedded` (тянет camera1).
- Скан: собственный лёгкий CameraX-сеанс `Preview` + `ImageAnalysis` с ZXing-анализатором. Не переиспользуем тяжёлый `CameraCaptureSessionManager` (photo/video).
- Точка входа: кнопка `btnSftpScanCompanionQr` рядом с `btnSftpImportCompanion` на экране add-resource.
- Гейт видимости: `packageManager.hasSystemFeature(FEATURE_CAMERA_ANY)` - скрывает кнопку на Quest (VR, ADR-2) и любом устройстве без камеры. Не флейвор-гард.
- `CAMERA` уже в манифесте (S0359) - нового разрешения нет; запрос гранта в рантайме.
- Downstream-вход: `ImportCompanionConfigUseCase.import(dto)` уже публичный ("QR payload path reuses this directly").

## Фазы

- `PHASE_01_scan_core.md` - зависимость ZXing, анализатор, сеанс, activity + layout + манифест + строки.
- `PHASE_02_wire_entrypoint.md` - payload-путь use-case/coordinator/VM, кнопка + launcher + гейт видимости в AddResource.
- `PHASE_03_finalize.md` - строки EN/RU/UK, capability-запись, debug-теги, сборка, аудит.

## Критерии готовности

Как в стратегии §11: скан plain и compressed QR создаёт ресурс без файла; чужой QR отклонён без краша; камера освобождается; кнопка скрыта на camera-less/VR; сборка standard+noLegal проходит; строки трёхъязычны.
