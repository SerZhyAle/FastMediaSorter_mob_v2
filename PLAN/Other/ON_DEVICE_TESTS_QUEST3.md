# On-Device Test Plan — Quest 3

**Generated:** 2026-04-27
**Scope:** все активные `PLAN/spec_*` спеки, ожидающие ручной проверки на Meta Quest 3
---

## 0. Подготовка (один раз)

1. **Собрать debug APK VR-флейвора:** `/build` → выбрать `vrDebug` (или эквивалент).
2. **Подключить Quest 3 по ADB:**
   - Включить developer mode на гарнитуре, разрешить USB debugging.
   - `adb devices` — гарнитура должна быть в списке.
3. **Установить APK:** `adb install -r app_v2/build/outputs/apk/vr/debug/<apk>.apk`.
4. **Запустить логкат-фильтр в отдельном окне** (понадобится для всех тестов):

   ```
   adb logcat -c
   adb logcat -s VR_PERF:I VR_BOOT:E VrPlayerActivity:I OpenXrSessionManager:I VrStereoSnapshotManager:I VrSaveFrameCommandOverride:I *:S
   ```

---

## 1. [S0014_vr-xr-cold-start/PHASE_02__measurement-run.md](S0014_vr-xr-cold-start/PHASE_02__measurement-run.md) — единственный явный блокер

Блокирует Phase 03. 5 шагов.

1. **Cold start:** force-stop приложения, затем открыть VR-файл из browse → дождаться первого кадра. В логах должны быть `VR_PERF: [xr-thread] egl_create=` и `VR_PERF: [gl-thread] first_frame_ready`.
2. **Warm re-entry:** вернуться в browse без force-stop, открыть VR-файл снова → второй `first_frame_ready` с заметно меньшим `abs_from_init`.
3. **Заполнить таблицу** в [PHASE_02__measurement-run.md](S0014_vr-xr-cold-start/PHASE_02__measurement-run.md#L137) — заменить все `MEASURED: ?ms` реальными значениями.
4. **Записать рекомендацию** в секции `RECOMMENDATION:` — `OPTIMIZE_NOW` (если хоть одна стадия > 200 ms и есть реалистичный low-risk фикс) или `BACKLOG`.
5. **Дев-лог:** `.\scripts\add_to_dev_log.ps1 "PLAN/S0014_vr-xr-cold-start/PHASE_02__measurement-run.md" "Phase 02 measurement" "filled cold/warm timings on Quest 3"`.

---

## 2. [spec_vr-immersive-hud-gl.md](spec_vr-immersive-hud-gl.md) — 7 acceptance + 3 research item

В одном immersive-сеансе видеофайла проверить:

- [ ] **HUD progress bar** всплывает при любой команде, исчезает через ~3 c.
- [ ] **Все индикаторы:** pause, seek (направление + позиция), volume, zoom, file name, recenter, mode, repeat — каждый всплывает на свою команду.
- [ ] **Y-fix:** короткое нажатие Y в immersive **не ставит на паузу**, показывает баннер; A — toggle play/pause работает.
- [ ] **Immersive ↔ phone-layout:** переход в одной сессии — индикаторы не дублируются, состояние сохраняется.
- [ ] **Phone-fallback:** на phone-layout индикаторы — как до спеки.
- [ ] **Idle = no overdraw:** в idle HUD-слой не входит в `xrEndFrame` (проверить логом — нет лишних layer-add записей).

Research items (визуально на гарнитуре):

- [ ] **§6.1 alpha blend:** прозрачные края HUD без чёрного фона/пересвета во всех режимах видеослоя (cinema-quad / cylinder / equirect2). Если артефакты — фолбэк на собственный шейдер (см. ADR-1).
- [ ] **§6.3 swapchain lifecycle:** один цикл immersive → home button (onPause) → re-enter (onResume) — в логе парные `HUD swapchain: ..` / `HUD swapchain destroyed`.
- [ ] **§6.4 placement:** 1.0 м × 0.3 м на 1.5 м с углом −20° — эргономика. Допустима подстройка ±30%.

После этого — `/spec-fix vr-immersive-hud-gl` (закроет status drift), потом `/spec-check vr-immersive-hud-gl` → ожидается `Verified`.

---

## 3. [spec_vr-hand-tracking.md](spec_vr-hand-tracking.md) — 2 сигнала

- [ ] Положить контроллеры → hand tracking активируется автоматически, появляется cursor-точка, pinch = клик, в cheatsheet виден hand-section.
- [ ] **Double pinch** = play/pause; **thumb swipes** = seek/volume; берёшь контроллеры обратно — управление возвращается мгновенно.

---

## 4. [spec_vr-stereo-formats.md](spec_vr-stereo-formats.md) — 5 сигналов

Понадобятся файлы в SMB/локально: `*3dh.mp4`, `*180x180*.mp4`, `*3dv.mp4`, `*OU.mp4`, half-OU.

- [ ] `*3dh.mp4` / `*180x180*.mp4` → корректное VR180 без fisheye-искажений.
- [ ] `*3dv.mp4` / `*OU.mp4` → стереоскопично, не плоское cinema.
- [ ] Переключение SBS ↔ OU **внутри одной сессии** без перезапуска плеера.
- [ ] **GPU Profiler (Meta Quest Developer Hub):** 7K VR180 fisheye ≥ 72 fps устойчиво. Если < 72 — переход на LUT-подход (см. action item §6.3).
- [ ] Half-OU: каждый глаз видит правильную вертикальную половину.

---

## 5. [spec_vr-immersive-controls-panel.md](spec_vr-immersive-controls-panel.md) — 7 сигналов

- [ ] От Touch-контроллера видна слабая ray-линия. *(Известный FAIL — ray-визуал отложен, но проверка нужна для отчётности.)*
- [ ] Нажатие **X** → GL-панель с кнопками видна.
- [ ] Наведение лучом на кнопку → hover-подсветка.
- [ ] Кнопка **Exit** на панели — без выхода из VR.
- [ ] **Drag слайдера seek** перематывает видео.
- [ ] Кнопки Play/Pause, Volume, Track, Format, Exit с панели работают.
- [ ] FPS ≥ 72 при открытой панели + 4K видео.

---

## 6. [spec_vr-stereo-projection-mapping.md](spec_vr-stereo-projection-mapping.md) — 1 сигнал

- [ ] Открыть стерео-файл → войти в immersive → выйти → войти снова. **stereoMode не сбрасывается на MONO**, layer descriptor пересоздаётся с тем же режимом. В логах нет `Timber.w` про sentinel-fallback.

---

## 7. [spec_vr-photo-capture-reliability.md](spec_vr-photo-capture-reliability.md) — 1 сигнал

- [ ] Воспроизвести оригинальный сценарий BUG-05 (фотозахват на стерео-файле). В логкате при отказе должен появиться полный chain: `stage=command` → `stage=request` → `stage=poll` → `stage=compose` → `stage=save` с ровно одним `result=fail reason=<tag>` или `result=ok`. Зафиксировать `reason` — это вход для следующей итерации.

---

## 8. [spec_vr-panel-swapchain-availability.md](spec_vr-panel-swapchain-availability.md) — 1 сигнал

- [ ] Воспроизвести отказ в создании panel swapchain. По логам определить, какая ветка сработала: Kotlin-guard `session not running` vs нативная xr-ошибка. Записать в [spec_vr-panel-swapchain-availability.md](spec_vr-panel-swapchain-availability.md) §6.1 как Resolved.

---

## 9. [spec_vr-auto-immersive-setting.md](spec_vr-auto-immersive-setting.md)

Уже **Verified**. На устройстве проверять не обязательно — единственный пункт `§11.2` (mirror на 3DVR-вкладке `PlaybackControlDialogFragment`) отложен в follow-up как опциональный полиш, не блокер.

---

## После всех сессий

1. **Отметить чекбоксы** в соответствующих audit-файлах (раздел "Manual Acceptance Signals" / "Manual / unresolved").
2. Для каждой спеки: обновить статус в [SPECS_CATALOG.md](SPECS_CATALOG.md) (Partial → Verified, или Draft → следующий шаг).
3. По очереди прогнать:

   ```
   /spec-check vr-xr-cold-start
   /spec-check vr-immersive-hud-gl
   /spec-check vr-hand-tracking
   /spec-check vr-stereo-formats
   /spec-check vr-immersive-controls-panel
   /spec-check vr-stereo-projection-mapping
   /spec-check vr-photo-capture-reliability
   /spec-check vr-panel-swapchain-availability
   ```

4. **Сохранять полный logcat** каждой сессии в `temp/quest3_<spec>_<date>.log` — пригодится при последующих `/spec-fix`.

---

## Минимальный одно-сессионный сценарий

Запустить логкат-фильтр (раздел 0.4) → одно cold-start открытие VR-видео (закрывает п.1, начинает п.2) → переключение между файлами разных стерео-форматов (п.4) → манипуляции HUD/контроллерами/панелью (п.2, 5) → переход immersive↔phone↔immersive (п.6) → попытка фотозахвата (п.7) → положить контроллеры (п.3). 30–40 минут активного теста + ~15 минут на заполнение таблиц и галочек.
