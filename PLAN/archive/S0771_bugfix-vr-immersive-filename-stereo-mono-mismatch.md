# S0771 - Immersive XR filename parser misreads stereo VR180 SBS film as MONO

**Status:** Archived

## Goal

Иммерсивный XR-рендерер теряет стерео-3D для VR-видео, имя которого `StereoDetector` (панельный плеер) уже корректно распознаёт как side-by-side. Причина - в `src/vr` живёт второй, наивный парсер имени `parseFilenameConfig`, который не знает токены `3dh`/`3dv`/`tab`/`hou` и потому скатывает явно стереоскопический файл в `MONO`. Цель: убрать расхождение двух парсеров, сделав `StereoDetector` единым источником истины для иммерсивного рендера, сохранив текущее поведение для всех имён, которые детектор не классифицирует однозначно.

## 0. Raw capture / evidence

Device: Oculus Quest 3 (eureka), Android 14, app 2.60.6281.708-NoLegal-DEBUG.
File: `/storage/emulated/0/Movies/18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4`
(7168x3584, HEVC, 60fps; filename tokens: `vr`, `180`, `180x180`, `3dh` = 3D horizontal / side-by-side).

Panel side (`StereoDetector`) - correct:
```
StereoDetector: filename match -> VR180_FISHEYE_SBS
PlayerStereoModeCoordinator: effective=VR180_FISHEYE_SBS reason=auto-detect
```

Immersive XR side (`parseFilenameConfig`) - wrong:
```
parseFilenameConfig: 18VR_..._180x180_3dh.mp4 -> projection=HEMISPHERE_180, layout=MONO
HUD ... layout=HEMISPHERE_180/MONO
```

Reproduced across multiple sessions:
- `logs/fastmediasorter_20260628_172525.log:3472`, `:3946`
- `logs/fastmediasorter_20260628_180102.log:732-733`

## 1. Root cause (researched 2026-06-28)

- Two independent filename classifiers. [StereoDetector.detectFromFilename](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt#L155) (src/main) recognises the SBS family `sbs`, `3dh`, `lr`, `rl`, `fullsbs` and the OU family `ou`, `tb`, `3dv`, `hou`, `tab` with word-boundary token matching.
- [DiagnosticXrActivity.parseFilenameConfig](app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt#L477) (src/vr) re-implements detection with naive `String.contains` and a smaller vocabulary. Its SBS branch lacks `3dh` and its OU branch lacks `3dv`/`tab`/`hou`, so a `_3dh` name falls through to `else -> StereoLayout.MONO`.
- The immersive renderer therefore renders a side-by-side stereo film flat (mono); the 2D panel renders it correctly.

Secondary (out of scope here): the XR projection enum has no fisheye type, so `VR180_FISHEYE_SBS` content is rendered on `HEMISPHERE_180` (equirect hemisphere). This is pre-existing and unchanged by this fix - only the stereo layout is corrected.

## 2. Fix

Make `StereoDetector` the single source of truth for the immersive path, with the existing naive parse retained only as the `UNKNOWN` fallback so no current behaviour regresses.

### Phase 1 - Route parseFilenameConfig through StereoDetector

1. In `DiagnosticXrActivity.parseFilenameConfig`, call `StereoDetector().detectFromFilename(filename)` first.
2. Map the resulting `StereoMode` to `(ProjectionType, StereoLayout)`:
   - `VR180_FISHEYE_SBS`, `EQUIRECT_180_SBS` -> `HEMISPHERE_180`, `SIDE_BY_SIDE`
   - `EQUIRECT_180_MONO`, `CYLINDER_180` -> `HEMISPHERE_180`, `MONO`
   - `EQUIRECT_360_SBS` -> `SPHERE_360`, `SIDE_BY_SIDE`
   - `EQUIRECT_360_OU` -> `SPHERE_360`, `TOP_BOTTOM`
   - `EQUIRECT_360_MONO` -> `SPHERE_360`, `MONO`
   - `SBS_FULL`, `SBS_HALF` -> `FLAT`, `SIDE_BY_SIDE`
   - `OU` -> `FLAT`, `TOP_BOTTOM`
   - `MONO` -> `FLAT`, `MONO`
   - `UNKNOWN` -> fall through to the existing naive `when` blocks (behaviour preserved).
3. Keep the existing `parseFilenameConfig` Timber.d log line so the HUD/log audit trail is unchanged.
   - Verification: a `_3dh` name resolves to `layout=SIDE_BY_SIDE`; a plain `_stereo` name (no 180/360/sbs/ou) still resolves via the naive fallback exactly as before.

### Phase 2 - Build gate

1. `standard debug` compiles (`a.ps1 dq`).
2. `vr debug` compiles (file is in `src/vr`).
   - Verification: both builds PASS.

### Phase 3 - Device verification (deferred, device-gated)

1. On Quest 3, immerse the `18VR_..180x180_3dh.mp4` film and confirm the scene is stereo 3D (per-eye), not flat mono; HUD shows the SBS layout.
   - Verification: device test via `/spec-test-device` / `/spec-sweep` when a headset is online.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0772 (same file, OOM crash - separate)
- **Flavor scope:** VR-only change (`src/vr`), noLegal-gated immersive renderer; no standard-flavor behaviour change.

## 4. Notes

- Prior related (all Archived): S0012 vr-stereo-formats, S0013 vr-stereo-projection-mapping, S0041 debug-vr180-fisheye-quality-regression.

<!-- auto-approved by /spec-all - 2026-06-28 -->
