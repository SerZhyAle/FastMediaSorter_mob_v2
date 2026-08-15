# STORE_TODO.md — накопительный чек-лист submission-требований

**Источник тикет:** `S0244` (стартовое наполнение) + последующие тикеты эпика.
**Эпик:** `S0240` §6.10, §6.11, §6.13.
**Обновлено:** 2026-05-18 (резолюшны R-06/R-07/R-10/R-11/R-12/R-13).

Живой документ. Пополняется по мере появления store-declarations / permissions / manifest-категорий. Чистится перед каждой submission-итерацией: пункты с `[x]` переезжают в submission-тикет.

Статусы пунктов:
- `[ ]` — открыто, нужно сделать
- `[~]` — в работе
- `[x]` — готово, попало в очередную submission-итерацию
- `[?]` — требует уточнения у владельца
- `[!]` — разрешено R-research, осталось implement в коде

---

## Google Play (Android XR)

### Manifest-декларации

- [!] **`<uses-feature>` для Android XR — РЕЗОЛЮШН R-06.** Минимальный набор (все `required="false"` для single-APK strategy):
  - `android.software.xr.api.openxr` (`required="false"`) — основной маркер OpenXR runtime.
  - `android.hardware.xr.input.controller` (`required="false"`).
  - `android.hardware.xr.input.hand_tracking` (`required="false"`).
  - `android.hardware.xr.input.eye_tracking` (`required="false"`).
  - `<uses-native-library android:name="libopenxr.google.so" android:required="false" />`.
  - Альтернатива для Jetpack XR SDK: `android.software.xr.api.spatial` (`required="false"`).
- [!] **`required="true"` vs `required="false"` — РЕЗОЛЮШН R-10.** Все XR-фичи **`required="false"`** для single-APK / single-track distribution. Google прямо рекомендует этот подход. `required="true"` отфильтрует приложение на phones, tablets, Wear, Auto, TV.
- [!] **`<property>`-декларации для immersive — РЕЗОЛЮШН R-06.**
  - `<property android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE" android:value="XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED" />`.
  - `<property android:name="android.window.PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED" android:value="XR_BOUNDARY_TYPE_LARGE" />`.
  - Только в `app_v2/src/vr/AndroidManifest.xml` (не в `main`).
- [!] **Intent-категории — РЕЗОЛЮШН R-06.** Стандартный `MAIN` + `LAUNCHER`; **отдельной XR-категории не существует**, фильтрация идёт по `xr.api.openxr` / `xr.api.spatial`.

### Permissions

- [!] **XR-specific permissions — РЕЗОЛЮШН R-06.** Добавлять только когда соответствующий `XR_ANDROID_*` extension реально используется (`SCENE_UNDERSTANDING_*`, `EYE_TRACKING_*`, `FACE_TRACKING`, `HAND_TRACKING`). Базовый use case media-player не требует ни одной.
- [x] **Cloud-permissions inherit-from-standard — РЕЗОЛЮШН `S0240 §11 Q3` 2026-05-18.** Все cloud-провайдеры (Google Drive, Dropbox, MSAL, SMB, SFTP, WebDAV, FTP) автоматом через `standard` ⊂ `vr`.

### Data Safety

- [x] **Декларация data collection — РЕЗОЛЮШН `S0240 §11 Q4` 2026-05-18:** «No data collected». Inherit-from-standard, никаких telemetry-библиотек в VR-сборке.
- [!] **Privacy policy URL — РЕЗОЛЮШН R-12.** Хостинг через **GitHub Pages** в публичном репо `fastmediasorter-legal` (или текущем `FastMediaSorter` public repo) → `https://<owner>.github.io/fastmediasorter-legal/privacy.html`. HTTPS обязателен. Trilingual: `docs/PRIVACY_EN.md`, `PRIVACY_RU.md`, `PRIVACY_UK.md`. Содержимое: «No data collected» + перечень категорий данных, которые не собираются + локальный SMB/SFTP/cloud-доступ как «processed only on-device, not transmitted to developer».

### Content Rating

- [ ] Content rating questionnaire — после определения, что попадает в production-сборку.

### Store assets

- [!] **Capture pipeline — РЕЗОЛЮШН R-13.** Те же assets для Quest и Android XR пока (контент one-eye 2560×1440 идентичен; Google Play XR требования к screenshots = общим Play требованиям).
- [!] **Screenshots (5 шт):** **2560×1440 PNG**, 16:9, через MQDH Cast 2.0 → Cinematic 4K → crop. Safe area: top 20% / bottom 30% свободны от текста.
- [ ] Описание приложения EN (минимум) — на стадии первой submission.
- [ ] Описание RU/UK — после `S0240 §11` (locale scope).
- [!] **Promo video (30..120s):** 1080p MP4 H.264/AAC, MQDH Widescreen 16:9 → монтаж → export.

---

## Meta Store / Horizon Store (Quest 3)

### Manifest-декларации

- [!] **Manifest-категория immersive — РЕЗОЛЮШН R-07.** `<category android:name="com.oculus.intent.category.VR" />` дополнительно к `MAIN` + `LAUNCHER` на launching activity.
- [!] **`<uses-feature>` для Quest — РЕЗОЛЮШН R-07.** **Обязательно:** `<uses-feature android:name="android.hardware.vr.headtracking" android:required="true" android:version="1" />`. Это **Quest-specific** маркер, не работает на Android XR — поэтому только в `src/vr/AndroidManifest.xml` для Quest-сборки. На Android XR — `android.software.xr.api.openxr` (см. выше).
- [!] **`<meta-data>` supported devices — РЕЗОЛЮШН R-07.** `<meta-data android:name="com.oculus.supportedDevices" android:value="quest3|quest3s" />` (на текущий план). Quest 2/Pro можно добавить позже; Quest 1 устарел. Отсутствие `quest3` блокирует ревью.
- [!] **`com.oculus.vr.focusaware` — РЕЗОЛЮШН R-05.** `<meta-data android:name="com.oculus.vr.focusaware" android:value="true" />` на VR-Activity. Без неё OpenXR runtime не активируется на HorizonOS v67+.
- [!] **Activity attributes — РЕЗОЛЮШН R-05/R-07.**
  - `android:taskAffinity="${applicationId}.vr"` — обязательная изоляция.
  - `android:launchMode="singleTask"`, `android:exported="false"`.
  - `android:resizeableActivity="true"`, расширенный `configChanges` (для Android XR Shell).
  - `android:screenOrientation="landscape"`, `android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"`.
  - `android:excludeFromRecents="true"`.
- [x] **App targeting — РЕЗОЛЮШН `S0240 §11 Q2` 2026-05-18:** Quest 2, Quest 3, Quest 3S, Quest Pro. Quest 1 — не поддерживается. На Quest 2 — fallback для Quest 3-specific расширений.
- [!] **minSdk override — РЕЗОЛЮШН R-07.** В `vr`-flavor `defaultConfig` повысить `minSdk = 29` (Quest 3 требует). Phone остаётся на `minSdk = 26`.
- [!] **uses-permission для VR — РЕЗОЛЮШН R-05.**
  - `com.oculus.permission.HAND_TRACKING` — обязательна на HorizonOS v62+ для `xrCreateHandTrackerEXT` (даже если hand tracking опциональный — без permission API возвращает `XR_ERROR_PERMISSION_INSUFFICIENT`).
  - `horizonos.permission.HEADSET_CAMERA` — passthrough-камера (Horizon OS v74+, dangerous).

### Performance / Comfort

- [!] **Refresh rate — РЕЗОЛЮШН R-07.** Запрашивать **90 Hz** по умолчанию через `xrRequestDisplayRefreshRateFB` (расширение `XR_FB_display_refresh_rate`). 120 Hz — опциональный toggle в настройках, требует включения в Headset Settings → Developer.
- [!] **Foveation — РЕЗОЛЮШН R-07.** Через `XR_FB_foveation` + `XR_FB_foveation_configuration` рантайм.
- [!] **SpaceWarp — РЕЗОЛЮШН R-07.** `XR_FB_space_warp` (опционально для 90→45 Hz reprojection).
- [!] **Performance settings — РЕЗОЛЮШН R-07.** `XR_EXT_performance_settings`.
- [ ] Comfort declarations — заполнить в Developer Dashboard на стадии submission.

### Submission tooling

- [!] **CLI tool — РЕЗОЛЮШН R-11.** `ovr-platform-util` (часть MQDH installer). Команда: `ovr-platform-util upload-quest-build --app_id=<ID> --apk=<file>.apk --channel=<channel>`. Документировать в `docs/DEV_OPS.md` после первой успешной загрузки.
- [!] **Signing — РЕЗОЛЮШН R-11.** Обязательна **APK Signature Scheme v2**. JKS или PKCS12 keystore. **Meta НЕ имеет аналога Play App Signing** — разработчик хранит ключ сам; потеря = re-listing нового app entry. Хранить keystore в защищённом backup (Bitwarden Secrets или аналог).
- [!] **Release channels — РЕЗОЛЮШН R-11.** Четыре канала: ALPHA (200/2500 invite-only) → BETA → RC → Production. App Lab merged into Store в 2024. Best practice: dev/CI → ALPHA → BETA → RC → Production за 3+ недели до релиза.
- [!] **SDK requirements — РЕЗОЛЮШН R-11.** `minSdkVersion ≥ 23` (рекомендуется 29 для Quest 3); `targetSdkVersion` 32..36.
- [!] **Submission timing — РЕЗОЛЮШН R-11.** Публичного SLA нет; буфер 2..6 недель перед целевой датой. Verification of developer organization (один раз) — ~48 часов.

### Privacy policy

- [!] **URL — РЕЗОЛЮШН R-12.** Тот же URL, что для Google Play (общий через GitHub Pages). Meta допускает organisational privacy policy если покрывает приложение поимённо.

### Cloud OAuth caveat

- [!] **Google OAuth на Quest — РЕЗОЛЮШН R-12.** На Horizon OS **нет** Google Play Services. `GoogleSignIn` SDK / `Credential Manager API` — **не работают**. Используем библиотеку **AppAuth-Android** (`net.openid:appauth:0.11.x`) для web OAuth 2.0 flow. Redirect URI custom scheme: `com.sza.fastmediasorter:/oauth2redirect`. Системный браузер (Meta Browser) выполняет authorization endpoint flow. Для media-apps без клавиатуры Meta рекомендует RFC 8628 **device code flow** как UX-альтернативу.
- [!] **Унификация phone+Quest — РЕЗОЛЮШН R-12.** Использовать **AppAuth для обоих** target'ов (phone и Quest) — это уберёт необходимость в Google Play Services на Quest. Phone (standard) сейчас на `GoogleSignIn`; миграция — отдельная задача рефакторинга.

### Store assets

- [!] **Asset spec — РЕЗОЛЮШН R-13.**
  - Screenshot: **2560 × 1440 px (16:9)**, 24-bit PNG, 5 штук.
  - Promo video: 1080p..2K, 30..120 сек, MP4 H.264 / AAC.
  - Trailer cover: 2560 × 1440 px, PNG.
  - Hero cover: 3000 × 900 px (10:3).
  - Cover square: 1440 × 1440 px (1:1).
  - Icon: 512 × 512 px (1:1).
- [!] **Capture pipeline — РЕЗОЛЮШН R-13.** MQDH Cast 2.0 → Cinematic 4K → manual crop. ADB debug.oculus props для специфических разрешений (`debug.oculus.capture.width/height/bitrate/fps`, `debug.oculus.screenCaptureEye 2` для стерео). Props сбрасываются на reboot.
- [!] **Хранение assets — РЕЗОЛЮШН R-13.** `temp/store_assets/quest/` и `temp/store_assets/android_xr/` (per «не писать в root» policy).
- [ ] Описание приложения — на стадии submission.
- [!] **Promo-видео — РЕЗОЛЮШН R-13.** MQDH в Widescreen 16:9 → DaVinci Resolve / Premiere → H.264. Должно содержать аудио-демо (Meta может отметить как ошибку метаданных без аудио).

### Distribution decision (single-APK vs split)

- [!] **РЕЗОЛЮШН R-10.** **Single-APK / single-track** для Google Play. Опция перейти на App Bundle + dynamic feature module для VR-стека позже, если phone-APK раздувается XR-зависимостями. Asset pack limit XR — 30 GB (vs 4 GB phone) — аргумент в пользу dedicated XR APK если медиа-кэш растёт.

### Flavor split decision (vr / vrUnlicensed)

- [!] **РЕЗОЛЮШН R-09.** Khronos OpenXR loader — Apache 2.0, **нет лицензионных оснований для split**. Meta использует тот же Khronos loader (`org.khronos.openxr:openxr_loader_for_android` ≥ 1.0.34). **Рекомендация:** единый `vr` source set; side-load сборка без Meta App ID — конфигурация **buildType** (debug variant), а не отдельный flavor. `vrUnlicensed` flavor — переоформить как buildType debug в Stage 0 (или сохранить до выработки потребности).

---

## Общие пункты (обе площадки)

- [ ] Версия приложения (`Y.YM.MDDH.Hmm` по схеме проекта) — каждая submission-итерация.
- [!] **App icon — РЕЗОЛЮШН R-07.** Меньшие требования к иконке для Quest 3 формфактора нет: 512×512 PNG (та же что для Play). Иконку adaptive (Android XR) — стандартная Material 3 adaptive icon. Если потребуются разные иконки phone vs VR — переоформить через flavor-specific mipmaps.
- [ ] App name / branding — единое для обеих площадок (`FastMediaSorter VR`? — финализируется отдельно).
- [ ] WHATS_NEW.md содержит запись о текущей submission — каждая итерация.

---

## Android XR — emulator gotchas (R-08)

Не submission-чек-лист, но влияет на test pipeline:

- Эмулятор Android XR — **только в Canary** Android Studio.
- OpenXR в эмуляторе **не работает** — приложения с OpenXR runtime могут падать. Эмулятор пригоден только для smoke-теста Jetpack-XR-UI (spatial panels).
- Все OpenXR-related тесты — только на физическом Quest 3 (через `vr` flavor).
- До выхода Samsung Galaxy XR — Quest 3 единственная VR-таргет-платформа.

---

## История обновлений

| Дата | Тикет | Что добавили / убрали |
|------|-------|------------------------|
| 2026-05-18 | S0244 | Создан стартовый скелет; все пункты привязаны к R-задачам из `S0240 §10.0`. |
| 2026-05-18 | S0240 §11 | Q2 закрыт — Quest 2/3/3S/Pro. Q4 закрыт — Data Safety «No data collected». |
| 2026-05-18 | S0244 | Резолюшны R-06/R-07/R-09/R-10/R-11/R-12/R-13 интегрированы. Manifest-фрагменты для Google Play (XR) и Meta Store (Quest) с явными значениями. Privacy policy hosting → GitHub Pages. Google OAuth → AppAuth для обоих target'ов. Capture pipeline → MQDH Cast 2.0. Single-APK distribution для Play. `vrUnlicensed` flavor split — нет лицензионных оснований, рекомендация: переоформить как buildType debug. |
