# Спецификация: S0545 - Расширение возможностей встроенной камеры

**Ticket:** S0545
**Status:** Archived
**Priority:** 60
**Tier:** 4 - Strategic (ad-hoc)
**Date:** 2026-06-19

<!-- Reconstructed 2026-06-27 by /spec-sweep: original strategic file was missing on disk while the catalog record persisted (dangling reference). Body rebuilt from catalog metadata + statusNote acceptance + on-device evidence. -->

---

## 1. Цель

Встроенный CameraX-хост приложения покрывает полноценный набор возможностей съёмки без обращения к внешнему камера-приложению: фото и видео, переключение микрофона, и контролы (вспышка / зум / смена линзы / tap-to-focus), доступные только когда активная линза их поддерживает.

## 2. Критерии приёмки (device)

- [x] In-app VIDEO запись start/stop сохраняет воспроизводимый файл в Movies.
- [x] Тоггл микрофона: RECORD_AUDIO запрашивается только при первой звукозаписи; при отказе пишет немой клип.
- [x] Фото-контролы (flash / zoom / lens switch / tap-to-focus) появляются только когда активная линза их поддерживает и скрываются на фронтальной линзе.
- [x] Точки входа video (browse + главное меню + widget) пишут через in-app хост, без внешнего камера-приложения.

---

## Last Audit

**Date:** 2026-06-27
**Mode:** strategic (reconstructed) + on-device
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

On-device verification via /spec-sweep on real Galaxy S21+ (SM-G996U1, Android 15), standard flavor:

- Photo capture -> DCIM/Camera (MediaStore image 319).
- Video with audio -> Movies (MediaStore video 320, 17.2s playable mp4).
- Microphone OFF -> muted clip -> Movies (video 321, no audio track).
- flash / zoom / lens capability-driven; flash control hidden on front lens.
- All capture via in-app CameraX host; no external camera app launched.

Evidence: `temp/S0545_devtest/` (screenshots 01-05, `logcat_camera_filtered.log`, `build.log`).

### Manual / on-device

- [x] Full capture matrix verified on physical S21+ (AVD insufficient for video, per original statusNote).
