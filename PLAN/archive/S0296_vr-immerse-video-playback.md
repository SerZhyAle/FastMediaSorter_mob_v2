---
ticket: S0296
status: Partial
priority: 80
date: 2026-05-25
tier: 3
---

# Стратегическая спецификация: S0296 — Воспроизведение VIDEO в immerse-режиме

**Ticket:** S0296
**Status:** Archived
**Priority:** 80
**Date:** 2026-05-25
**Tier:** 3 — Strategic
**Tactical plan:** `PLAN/S0296_vr-immerse-video-playback/INDEX.md`
**Roadmap entry:** `S0240 §10.3` — первый VIDEO follow-up после `S0295` generic immerse playback contract. Тикет снимает ограничение `IMAGE only`: пользователь, который видит VR-бейдж в плоском плеере, должен попадать в иммерс с настоящим видео, а не в `Unavailable(NotYetSupported)`.

**Depends on:**

- `S0295` Archived (superseded; was Verified) — generic immerse playback contract.
- `S0291` BlockNeedUserTest — diagnostic OpenXR lifecycle (passthrough exit + HUD re-entry) must pass Quest verification before final S0296 acceptance.
- `S0292` BlockNeedUserTest — player entry + return-path snapshot restoration; S0296 Phase 04 owns the shared return-path fix, superseding S0292's round-trip warnings.

**Blocks:** последующие тикеты `S0240 §10.3`:

- Авто-детект стерео-формата для видео.
- Поддержка VR180 / VR360 / SBS / OU per-format.
- GIF/animated в иммерсе.
- Один долгоживущий XR-host без cold-start на каждый файл.
- Zero-copy compositor-layer backend на `XR_KHR_android_surface_swapchain`.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user — довести стратегическую спецификацию S0296 до готового состояния после research-прохода.
- **Goal / expected outcome:** Provided by user — пользователь нажимает VR-бейдж на видео, входит в immersive cinema playback и возвращается в плоский плеер без потери позиции и базового состояния воспроизведения.
- **Local anchor:** Provided by user — `S0296`, существующий VR-бейдж из `S0292`, generic launch contract из `S0295`, диагностический OpenXR/video path текущего VR runtime.
- **Scope boundaries / forbidden areas:** Delegated by user — без stereo auto-detect, корректного SBS/OU/VR180/VR360, GIF, новых UI-сурфейсов, browse-level entry, long-lived XR host и DRM-гарантий.
- **Done / success signal:** Provided by user — локальное или уже подготовленное flat mono video открывается в immerse cinema mode со звуком, стартует с запрошенной позиции, выходит с обновлённым playback snapshot и больше не возвращает `Unavailable(NotYetSupported)` для поддержанного VIDEO input.
- **Autonomy rule:** Delegated by user — агент выбирает менее рискованную архитектуру с явными допущениями.
- **UI decisions / delegation:** N/A — UI placement принадлежит `S0292`; этот тикет меняет только поведение после активации уже существующего VR entry.

### Approved Scope Decisions

- **Cinema baseline:** S0296 renders VIDEO as a mono flat cinema surface inside the existing immersive scene. Per-format stereo/spherical correctness is deferred.
- **Playback ownership:** MVP использует XR-owned ExoPlayer внутри immerse host. Плоский плеер передаёт launch snapshot; immerse player стартует с него, владеет воспроизведением в XR и возвращает обновлённый snapshot.
- **Same-instance handoff deferred:** общий flat ExoPlayer instance для flat/immerse откладывается до long-lived-host / runtime-toggle тикета. Он конфликтует с текущей моделью возврата player task и не является самым безопасным первым VIDEO milestone.
- **Rendering backend:** MVP переиспользует существующий native `SurfaceTexture` / `GL_TEXTURE_EXTERNAL_OES` video path и external-video shader. `XR_KHR_android_surface_swapchain` фиксируется как future backend, а не первая реализация.
- **Input scope:** MVP принимает local file URI и уже подготовленные readable cache URI. Network/cloud live streaming и политика копирования больших `content://` файлов остаются follow-up, если тактический план явно не подключит существующий safe pre-cache path.
- **Audio:** audio remains in the standard Android audio pipeline. No OpenXR audio API work is required in this ticket.
- **Subtitles:** subtitle rendering inside immerse is out of MVP. Selected subtitle state may be restored on flat return if the flat player already tracks it.

---

## 1. Проблема

- `S0295` дал общий immerse launch contract, но VIDEO всё ещё short-circuit-ится в `Unavailable(NotYetSupported)`.
- `S0292` уже даёт пользовательский entry surface. Без S0296 пользователь видит VR-бейдж на видео, но не получает реального immersive playback.
- Диагностический VR runtime уже умеет принимать decoded video frames через native external-OES surface для bundled samples, но этот путь не подключён к пользовательскому VIDEO launch.
- Предыдущая версия спеки предполагала reuse flat ExoPlayer instance. Research pass показал, что это слишком широкий первый шаг: текущий launch flow завершает flat player task, а существующий VR runtime уже содержит более дешёвый XR-owned playback path.

---

## 2. Цели

1. Для supported VIDEO input `StartVrPlaybackUseCase` возвращает launchable input, а не `Unavailable(NotYetSupported)`.
2. Immerse host запускает local/prepared video через XR-owned player и привязывает его к существующей native video surface.
3. Playback стартует с позиции flat-player snapshot и сохраняет запрошенные play/pause, speed и volume.
4. При выходе immerse возвращает обновлённый playback snapshot; плоский плеер восстанавливает тот же файл, позицию, play/pause state, speed и volume.
5. Codec/playback failure маппится в `VrLaunchResult.Unavailable(DecoderFailed)` или `Crashed(reason)` без падения плоского плеера.
6. Diagnostic IMAGE / Test Immersive path не меняется с точки зрения пользователя.
7. MVP остаётся cinema baseline для mono flat video. SBS/OU/VR180/VR360 могут отображаться как flat source frames, но корректный per-format rendering откладывается.

**Non-goals:**

- Same-instance ExoPlayer handoff between flat and immerse.
- Network/cloud live streaming inside immerse without a prepared local/cache URI.
- Correct SBS / OU / VR180 / VR360 rendering.
- Auto-detect stereo/spherical format.
- GIF / animated image playback.
- Subtitle overlay rendering inside immerse.
- DRM / Widevine secure-surface guarantee.
- Long-lived XR host.
- New UI surfaces or copy changes outside `docs/FEATURES`.

---

## 3. Решение

### 3.1. VIDEO Contract Extension

- VIDEO становится поддержанным launch media type, когда runtime доступен, а входной URI локальный или уже подготовлен для безопасного чтения.
- Launch payload несёт данные playback snapshot, достаточные для XR-owned player: URI, playlist context, start position, desired play/pause state, speed и volume.
- Неподдержанные или небезопасные inputs остаются typed failures. Большие неподготовленные `content://`, network и cloud inputs возвращают понятный unavailable result, пока тактическая фаза не подключит безопасный pre-cache route.
- Defensive Activity fallback остаётся: прямые stale-вызовы, запрашивающие VIDEO без валидного prepared URI, возвращают typed unavailable result вместо пустого XR-startup.

### 3.2. XR-Owned Playback Backend

- Flat player перед запуском фиксирует текущее состояние и не считает свой ExoPlayer live renderer на время immersive-интервала.
- Immerse host создаёт собственный player instance, привязывает его к native video surface, уже доступной из VR runtime, применяет launch snapshot и стартует playback.
- Во время immerse изменения seek / pause / resume / speed принадлежат XR player и попадают в return snapshot.
- При выходе XR player очищает video surface и освобождается до teardown native surface. Это сохраняет порядок release, уже проверенный в diagnostic path.
- Flat player потребляет return snapshot и восстанавливает playback с возвращённой позиции. Если return snapshot отсутствует из-за fail-before-playback, flat player откатывается к pre-launch snapshot.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** `S0295` Verified; `S0291` lifecycle readiness; `S0292` player entry and return-state restoration; `S0297` DRM/noLegal capability research as non-blocking related scope.
- **Proceed signal:** user requested to finish S0296 after the research pass on 2026-05-25.
- **Delegated implementation latitude:** tactical planning may choose exact shared model fields, native JNI entry names, player helper boundaries and validation order, while preserving XR-owned playback, external-OES MVP backend and local/prepared URI scope.
- **Blocking conditions:** implementation waits for S0291/S0292 readiness unless a later owner request explicitly carries those gaps as tactical blockers inside S0296.

### 3.4. Cinema Rendering Backend

- Первый backend — существующий GLES scene path: native `SurfaceTexture` получает decoded frames, `updateTexImage()` обновляет external OES texture, а текущий video shader рисует её на flat projection quad.
- Shader сохраняет существующий texture transform и gamma handling из diagnostic video work.
- `XR_KHR_android_surface_swapchain` документируется как более поздний optimization path. Он может уменьшить копии и напрямую связать decoded video с OpenXR composition layer, но имеет отдельные lifecycle rules и не должен смешиваться с первым пользовательским VIDEO milestone.
- Cinema size и distance фиксированы для MVP. User-adjustable size/distance — polish follow-up.

### 3.5. State Handoff

- Pre-launch snapshot is caller-owned and stored before dispatch.
- Launch snapshot provides the XR player with start position, play/pause state, speed and volume.
- Return snapshot is immerse-owned and reports final position, play/pause state, speed, volume and terminal outcome.
- Flat return потребляет return snapshot до повторного показа controls. Если S0292 snapshot restoration остаётся частичным, реализацию S0296 нельзя принимать.

### 3.6. Lifecycle And Memory Pressure

- OpenXR session remains cold-start per launch. Long-lived host is not part of this ticket.
- XR player lifecycle is nested inside the immersive session and releases before native video surface destruction.
- Native surface objects remain owned by the VR runtime; the player never serializes or transports Surface through intent extras.
- Audio focus stays in the Android media pipeline. Tactical planning may add an explicit focus request if device testing shows audio loss on Quest.
- Memory pressure should fail closed: if prepared video cannot be opened or the decoder cannot attach to the native surface, return a typed failure and restore flat state.

### 3.7. Flavor Isolation

- Shared transport models stay in the common contract layer.
- Real VIDEO playback implementation stays in VR-capable source sets.
- Non-VR flavors keep no-op behavior and must not gain new `BuildConfig.SUPPORT_*` checks in main-source implementation.

### 3.8. docs/FEATURES

- Cinema-video capability ships folded into the consolidated VR Edition bullet in `docs/FEATURES.md` ("virtual cinema screen for flat files"), `docs/FEATURES_RU.md` ("воспроизведение 2D-файлов на гигантском виртуальном экране"), and `docs/FEATURES_UK.md` ("відтворення 2D-файлів на гігантському віртуальному екрані"), not as a standalone bullet — avoids redundancy with the existing VR Edition entry.
- S0296's contribution to that bullet is cinema playback for local or prepared flat video; per-format stereo claims (SBS/OU/VR180/VR360) in the same bullet belong to other VR tickets, not S0296.
- The consolidated bullet must keep cinema playback for flat files readable in all three locales.
- `docs/FEATURES_noLegal*.md` stays unchanged.

---

## 4. Открытые вопросы / Research items

1. **S0291 lifecycle readiness**
   - **Вопрос:** закрыты ли passthrough exit и HUD re-entry defects из S0291?
   - **Статус:** Open blocker before implementation.

2. **S0292 snapshot restoration**
   - **Вопрос:** плоский player return path уже восстанавливает video position/speed/photo state, или всё ещё только command panel/fullscreen?
   - **Статус:** Open blocker before acceptance.

3. **Prepared URI policy**
   - **Вопрос:** какие existing pre-cache/local-copy маршруты можно безопасно переиспользовать для network/cloud video before immerse launch?
   - **Статус:** Deferred unless tactical phase explicitly includes safe pre-cache wiring.

4. **Cinema size**
   - **Вопрос:** достаточно ли фиксированного cinema quad размера для MVP?
   - **Статус:** Resolved for MVP — fixed size. User-adjustable distance/size deferred.

5. **DRM secure output**
   - **Вопрос:** принимает ли Quest/Android XR decoder native VR surface for protected content?
   - **Статус:** Deferred to S0297 / DRM follow-up.

6. **Surface-swapchain backend**
   - **Вопрос:** когда стоит заменить external-OES path на `XR_KHR_android_surface_swapchain`?
   - **Статус:** Deferred optimization after MVP video works on hardware.

---

## 5. Риски

- **S0291 lifecycle остаётся нестабильным.** Реализация может компилироваться, но провалить user verification. Митигация: не начинать S0296 implementation до S0291 verification или явно внести S0291 как pre-implementation blocker в tactical plan.
- **S0292 return path неполный.** Видео может играть в immerse, но flat player вернётся на stale position. Митигация: сначала закрыть S0292 snapshot restoration warnings или сделать их исправление первой фазой S0296.
- **Prepared URI gap.** Network/cloud файлы могут быть небезопасны для чтения XR-owned player. Митигация: MVP принимает только local/prepared URI и возвращает typed unavailable для неподготовленных remote inputs.
- **Decoder/surface mismatch.** Some codecs may refuse the native video surface. Mitigation: map failure to `DecoderFailed`, keep flat player recoverable, and validate H.264/HEVC local samples on Quest 3.
- **Subtitle expectation mismatch.** Users may expect subtitles because flat player supports them. Mitigation: keep subtitles out of S0296 feature text and defer immersive subtitle overlay.
- **Cold-start budget miss.** XR session + player prepare can exceed target on first run. Mitigation: measure local 1080p first and move long-lived host / preload to follow-up if needed.
- **Feature text overclaims.** Documentation could imply full VR180/360 support. Mitigation: feature bullet says cinema playback only.

---

## 6. Влияние на пользователя (docs/FEATURES)

Delivered via the consolidated VR Edition bullet (cinema playback for flat files in all three locales), not a standalone bullet. Canonical user-facing intent:

- **EN:** Watch local or prepared video in immersive VR cinema mode on a flat screen inside the VR scene.
- **RU:** Просмотр локального или подготовленного видео в режиме VR-кинозала на плоском экране внутри immersive-сцены.
- **UK:** Перегляд локального або підготовленого відео в режимі VR-кінотеатру на плоскому екрані всередині immersive-сцени.

---

## 7. Архитектурные решения (ADR)

**ADR-1: Existing external-OES backend first**

- **Решение:** MVP переиспользует текущий native `SurfaceTexture` / external-OES video path.
- **Альтернативы:** implement `XR_KHR_android_surface_swapchain` immediately.
- **Почему:** local code already has a working diagnostic video path; surface-swapchain adds native lifecycle scope that is not needed to prove the first user VIDEO milestone.

**ADR-2: XR-owned ExoPlayer baseline**

- **Решение:** immerse owns a player instance for the XR session and returns updated state to the flat player.
- **Альтернативы:** share the flat ExoPlayer instance across flat and immerse.
- **Почему:** текущий flat launch path и task-return model превращают same-instance sharing в более широкий lifecycle-проект. XR-owned playback — самый короткий мост от existing diagnostic video к user video.

**ADR-3: Snapshot return contract instead of live Surface bridge**

- **Решение:** state moves through typed snapshots; Surface stays native-runtime-owned and never crosses intent boundaries.
- **Альтернативы:** Hilt singleton Surface bridge, intent extras, direct reuse of flat player.
- **Почему:** snapshots match the existing typed launch/result contract and keep native Surface lifecycle local to the immersive host.

**ADR-4: Local/prepared input scope**

- **Решение:** S0296 accepts only local or already prepared readable video URIs.
- **Альтернативы:** make all network/cloud/content URIs work in the same ticket.
- **Почему:** large video copy/streaming policy is separate product risk. MVP should prove immersive video playback without turning into a transfer/cache project.

---

## 8. Связи с другими спеками

- **S0240 §10.3** — parent roadmap group for VR video follow-ups.
- **S0295** — generic immerse playback contract, already Verified.
- **S0291** — lifecycle and diagnostic stability blocker.
- **S0292** — user entry surface and flat-player return path; snapshot restoration warnings must be resolved before S0296 acceptance.
- **S0290 / S0249** — origin of the current diagnostic OpenXR video rendering path.
- **S0297** — related noLegal/DRM/capability research; DRM is not part of S0296 MVP.

---

## 9. Критерии готовности (strategic-level)

1. On Quest 3, a local or already prepared mono flat video launched from the VR badge opens in immersive cinema view with audio.
2. Immerse playback starts within ±200 ms of the launch snapshot position.
3. Exit returns the flat player to the same file at the final immersive playback position within ±200 ms.
4. Play/pause, speed и volume восстанавливаются из return snapshot.
5. Unsupported unprepared remote/content inputs fail with a typed unavailable result and leave flat playback recoverable.
6. `StartVrPlaybackUseCase` no longer returns `Unavailable(NotYetSupported)` for supported VIDEO inputs.
7. Codec/playback failure returns `DecoderFailed` or `Crashed(reason)` without crashing the flat player.
8. Diagnostic IMAGE / Test Immersive flow does not regress.
9. Local 1080p H.264 cinema playback is stable at headset refresh rate without visible dropped-frame bursts.
10. Cold start from badge tap to visible first video frame is measured on Quest 3 and recorded in the tactical closure notes.
11. `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` contain the new cinema-video bullet without overclaiming VR180/360/stereo/streaming/DRM/subtitles.

---

## 10. Что делать с этим документом

1. Keep S0296 `Approved`.
2. Не начинать implementation, пока S0291 lifecycle readiness и S0292 snapshot restoration не закрыты или явно не внесены в tactical blockers.
3. Run `/spec-tech S0296` when tactical planning is requested.
4. Tactical phases должны покрыть: VIDEO launch contract extension, snapshot-return contract, XR-owned player binding to existing external-OES surface, flat return restoration, docs update и Quest 3 validation.
5. After S0296 is Verified, continue with auto-detect stereo format and per-format rendering tickets.

---

## Research Update (2026-05-25)

### External API findings

- `XR_KHR_android_surface_swapchain` exists for Android decoded video / image stream use cases: runtime creates an `android.view.Surface` producer-end and `XrSwapchain` that can be used as `XrSwapchainSubImage`.
- `XR_KHR_android_surface_swapchain` has strict lifecycle rules: only `xrDestroySwapchain` is valid for that swapchain; `xrAcquireSwapchainImage`, `xrWaitSwapchainImage`, and `xrReleaseSwapchainImage` are invalid. Before `xrEndSession`, the app must ensure the Surface is no longer receiving frames.
- Android XR official extension list includes `XR_KHR_android_surface_swapchain`; Khronos runtime inventory shows support on Meta Quest 3 / 3S / Pro mobile runtimes.
- Media3 / ExoPlayer supports `setVideoSurface(Surface)` for arbitrary Surface output, but Android docs recommend `SurfaceView` / `TextureView` helpers when caller owns their lifecycle. Native-owned Surface lifecycle must be closed manually.
- Android `SurfaceTexture` remains a valid baseline: a `Surface` created from `SurfaceTexture` can be a `MediaCodec` / media playback output destination, and `updateTexImage()` moves the latest decoded frame into a GLES external texture.

### Local architecture findings

- Native VR runtime already contains GL-backed video path: `SurfaceTexture` + `GL_TEXTURE_EXTERNAL_OES`, JNI surface access, external-video shader, texture transform, and manual gamma decode.
- Immerse Activity already creates an owned player for bundled diagnostic video and binds it to the native video Surface, but user VIDEO launch is currently blocked by use-case and Activity fallback gates.
- Player VR launch path currently uses legacy direct dispatch and finishes the flat player task after immersive start. This is not compatible with same-instance ExoPlayer reuse as an MVP assumption.
- S0292 audit уже зафиксировал round-trip gap: snapshot fields захватываются, но не полностью восстанавливаются на return. S0296 acceptance зависит от исправления этого path.

### Recommended baseline

- S0296 MVP использует XR-owned ExoPlayer внутри immerse Activity плюс существующий native `SurfaceTexture` / external-OES pipeline.
- Flat player passes URI / position / playback parameters in the launch snapshot. Immerse owns audio/video during XR and returns an updated snapshot on exit.
- Same-instance ExoPlayer handoff moves to a later long-lived-host / panel-toggle ticket.
- `XR_KHR_android_surface_swapchain` is tracked as a future zero-copy compositor-layer backend.

### Sources

- Khronos OpenXR specification: `XR_KHR_android_surface_swapchain`, `xrCreateSwapchainAndroidSurfaceKHR` — https://registry.khronos.org/OpenXR/specs/1.0-khr/html/xrspec.html#XR_KHR_android_surface_swapchain
- Android XR supported OpenXR extensions — https://developer.android.com/develop/xr/openxr/extensions
- Khronos OpenXR runtime extension inventory — https://github.khronos.org/OpenXR-Inventory/runtime_extension_support.html
- Android Media3 surface docs — https://developer.android.com/media/media3/ui/surface
- Media3 `SimpleExoPlayer.setVideoSurface` API reference — https://developer.android.com/reference/androidx/media3/exoplayer/SimpleExoPlayer#setVideoSurface(android.view.Surface)
- Android `SurfaceTexture` API reference — https://developer.android.com/reference/android/graphics/SurfaceTexture

## Implementation Closure Handoff (2026-05-30)

- Static implementation phases 01..05 are complete.
- Build command: `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` passed on 2026-05-30.
- Quest 3 acceptance still must verify local/prepared video launch from the flat-player VR badge, immersive cinema video + audio, return snapshot restoration for file, position, play/pause, speed and volume, typed unavailable results for unsupported inputs, and Diagnostic IMAGE / Test Immersive non-regression.
- Cold-start measurement: pending device verification.

## Proposed Structural Changes

### Proposal P-1 - Replace same-instance ExoPlayer handoff with XR-owned ExoPlayer baseline  (proposed 2026-05-25 by GPT-5)

**Status:** Accepted
**Affected:** §0 Approved scope decisions, §2 Goals, §3.2, §3.4, §3.5, §7 ADR-2, §9 criteria #2..#4
**Rationale:** Current player launch path finishes the flat player task after dispatch, and the native VR Activity already owns a working ExoPlayer-to-native-Surface path for diagnostic video. Sharing the flat ExoPlayer instance across activities would require lifecycle and task-return changes outside the smallest VIDEO milestone.
**Applied edit:** S0296 now defines XR-owned ExoPlayer as the MVP baseline and defers same-instance handoff.

### Proposal P-2 - Use existing external-OES pipeline first; defer `XR_KHR_android_surface_swapchain` backend  (proposed 2026-05-25 by GPT-5)

**Status:** Accepted
**Affected:** §3.2, §3.3, §3.5, §5 risks, §7 ADR-1/ADR-3
**Rationale:** External research confirms `XR_KHR_android_surface_swapchain` is a valid Quest 3 / Android XR route, but local code already has `SurfaceTexture` + `GL_TEXTURE_EXTERNAL_OES` + native shader path. Replacing it with a compositor surface-swapchain during the first user VIDEO milestone increases native scope and lifecycle risk.
**Applied edit:** MVP uses the existing external-OES path; surface-swapchain backend is a follow-up.

### Proposal P-3 - Add return snapshot as a normative VIDEO contract  (proposed 2026-05-25 by GPT-5)

**Status:** Accepted
**Affected:** §3.4, §9 criteria, dependency note for S0292
**Rationale:** With XR-owned ExoPlayer, player position, play/pause, speed, volume and track choices can change inside immerse. The previous S0296 text assumed the same ExoPlayer instance survived and therefore said no return payload was needed.
**Applied edit:** S0296 now requires an updated return snapshot and makes flat-player consumption part of acceptance.

### Proposal P-4 - Narrow MVP input scope to local or already prepared readable video URI  (proposed 2026-05-25 by GPT-5)

**Status:** Accepted
**Affected:** §0 Done signal, §2 Goals, §3.1, §4 open questions, §9 criteria
**Rationale:** The current Activity copies `content://` image input to cache, but doing that blindly for large video files is unsafe. Network/cloud live streaming into immerse should not be pulled into this first VIDEO ticket unless the tactical plan explicitly reuses existing player pre-cache / local-copy machinery.
**Applied edit:** MVP scope is local or already prepared readable video URI.

### Proposal P-5 - Update readiness gates with actual S0291 / S0292 state  (proposed 2026-05-25 by GPT-5)

**Status:** Accepted
**Affected:** `Depends on`, §10, §9 criteria
**Rationale:** At research time, S0295 was Verified, S0291 was Tactical, and S0292 was Partial with round-trip restoration warnings. S0296 should not enter implementation as if both upstream lifecycle and player-return state were closed.
**Applied edit:** Dependencies and next-step gates now explicitly require S0291 lifecycle readiness and S0292 snapshot restoration.

## Revision History

- **2026-05-25** - created by android-rd-specialist (focus: VR roadmap continuation, S0240 §10.3 first VIDEO milestone)
  - Идея и scope сформулированы после owner-research-запроса по состоянию VR-фронтов. Зафиксированы CINEMA quad как baseline, ExoPlayer reuse, флавор-изоляция, явные блокеры `S0295` + `S0291` и per-format / auto-detect non-goals.
- **2026-05-25** - by `/spec-update` (`GPT-5`, focus: research, completeness, consistency)
  - Applied: 1 research addendum. Proposed (DISCUSS): 5.
  - Research found: lowest-risk VIDEO baseline is XR-owned ExoPlayer + existing external-OES native surface, not same-instance flat-player handoff.
- **2026-05-25** - by `/spec` + `/spec-update` (`GPT-5`, focus: finish strategic spec)
  - Applied: 5 accepted structural proposals. Status: Draft → Approved.
  - Rewrote S0296 around XR-owned playback, existing external-OES backend, return snapshot contract, local/prepared URI scope and explicit S0291/S0292 readiness gates.

## Last Audit

**Date:** 2026-06-15
**Mode:** full
**Flags:** -
**Outcome:** Partial
**Counts:** PASS 30 · WARN 3 · FAIL 0 · MANUAL 6 · EXEMPT 1 · (2 items resolved in 2026-06-15 F5 fix-pass)

> Re-audit of unchanged committed code (no S0296 `.kt`/`docs` change since 2026-05-30 closure). Static contract predicates for the snapshot model, launch gates, XR-owned playback binding and flat-return restore are all present. 2026-06-15 F5 fix-pass cleared the stale `Depends on` header (#2) and the docs/FEATURES reconciliation (#4); remaining open items are device gates (#1 S0291, #2-residual S0292) and one owner-decision FOLLOW-UP (#3). Final `Verified` requires the Quest 3 on-device sweep below.

### Action items

1. **[WARN §0 Depends on / §4.1 - S0291]** S0291 is `BlockNeedUserTest`, not `Verified` - complete its Quest lifecycle device verification before final S0296 verification. **Open device gate.**
2. **[RESOLVED-PARTIAL §0 Depends on / §4.2 - S0292]** Stale `Depends on` header fixed (S0295 now `Archived`; S0291/S0292 now reflect `BlockNeedUserTest`). Phase 04 supersession of S0292's round-trip warnings recorded in the header. Residual: S0292 itself is `BlockNeedUserTest` - its own device verification is still an upstream gate.
3. **[WARN §2.5 / §9.7 criterion #7 - Phase 03.2/03.3]** VIDEO typed-failure contract not implemented as specified. Null-surface path (`startVideoPlayback` returns `false` at `DiagnosticXrActivity.kt:959,1266`) calls `queueErrorHud(..., "Playback Start Failed")`, and `onPlayerError` (`:856`) does `Timber.e("VR diagnostic playback failed!")` + error HUD + Toast + `releasePlaybackResources()` - neither calls `deliverReturnAndFinish(VrLaunchResult.Unavailable(DecoderFailed))`. Flat player is not crashed (safety half of #7 holds) but receives no typed `DecoderFailed`. **FOLLOW-UP (owner decision + device):** confirm whether the in-VR error HUD is the intended final behavior or the flat player must get a typed `DecoderFailed`; not auto-fixable (design fork, not derivable from code).
4. **[RESOLVED §3.8 / §6 / §8 - Phase 05.1]** Cinema-video capability is documented across all three locales via the consolidated VR Edition bullet (`docs/FEATURES.md` "virtual cinema screen for flat files"; `_RU` "воспроизведение 2D-файлов на гигантском виртуальном экране"; `_UK` "відтворення 2D-файлів на гігантському віртуальному екрані"). §3.8 + §6 realigned to this consolidated delivery instead of a redundant standalone bullet. No docs file change needed.

> **F5 fix-pass (2026-06-15, `/spec-all`).** Tree now clean - applied the two mechanically-fixable spec edits: stale `Depends on` header (#2) and docs/FEATURES reconciliation (#4). Item #3 remains a genuine owner-decision FOLLOW-UP (typed `DecoderFailed` vs in-VR HUD) and is deferred to the human. Items #1 + #2-residual are upstream device gates (S0291/S0292 both `BlockNeedUserTest`). S0296 stays `Partial`, blocked on Quest 3 on-device verification (6 manual items below) + S0291/S0292 device verification + the #3 owner ruling. Re-run `/spec-fix S0296` after the owner rules on #3; final `/spec-check` to `Verified` requires the on-device sweep.

### Manual / on-device

- [ ] Quest 3: local or prepared mono flat video launches from the flat-player VR badge into immersive cinema with audio.
- [ ] Quest 3: launch position and return position are within ±200 ms of the expected snapshots.
- [ ] Quest 3: play/pause, speed and volume restore from the return snapshot.
- [ ] Quest 3: GIF, unprepared `content://`, network and cloud inputs return typed unavailable results without crashing the flat player.
- [ ] Quest 3: Diagnostic IMAGE / Test Immersive flow does not regress.
- [ ] Quest 3: cold-start time from badge tap to first visible video frame is measured and replaces the closure placeholder.
