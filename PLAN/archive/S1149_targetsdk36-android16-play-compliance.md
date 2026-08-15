# Стратегическая спецификация: S1149 - Переход на targetSdk/compileSdk 36 (Android 16) для соответствия требованиям Google Play

**Ticket:** S1149
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-22
**Tier:** 3 - Moderate
**Roadmap entry:** Play Console mandate - target API level requirement (deadline 2026-08-31)
**Tactical spec:** `PLAN/S1149_targetsdk36-android16-play-compliance/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-22

**Текст (Google Play Console, вербатим):**

> App must target Android 16 (API level 36) or higher.
> To provide users with a safe and secure experience, Google Play requires all apps to meet target API level requirements. From Aug 31, 2026, if your target API level is not within 1 year of the latest Android release, you won't be able to update your app.
> How to fix: Your highest non-compliant target API level is Android 15 (API level 35). 1. Update your app to target Android 16 (API level 36) or higher. 2. Publish a new version of your app to production.
> Action by Aug 31: You won't be able to release app updates.

**Скриншот приложен владельцем к запросу (Policy status -> Issue details).**

**Ключевой вывод research-фазы (полный отчёт: `temp/S1149/research_native_16kb_inventory.md`):**

- Риск 16 КБ страниц памяти - предусловие Play для targetSdk >= 35 - у нас уже фактически закрыт. Все 22 уникальные нативные `.so`, реально попадающие в сборку, выровнены под 16 КБ (`0x4000`) на обеих 64-битных ABI (arm64-v8a, x86_64); Paddle-Lite - под 64 КБ (`0x10000`). Ни одной несовместимой 64-битной библиотеки не найдено.
- Ремедиация 16 КБ уже проведена командой в апреле-июне 2026 (NDK r27c, пересобранный первопартийный FFmpeg-AAR, `jniLibs.useLegacyPackaging=false`, `android.bundle.enableNativeLibraryAlignment=true`).
- Ранний тезис «Media3 1.2.1 устарел под 16 КБ» - неточен: Media3 у нас не поставляет `.so` вовсе; FFmpeg - отдельный первопартийный AAR. Tesseract4Android уже 4.8.0 (первая версия с 16 КБ), Chaquopy уже 17.0.0 (milestone с 16 КБ).
- 32-битные ABI выровнены под 4 КБ (`0x1000`) - это в рамках политики (требование Play 16 КБ распространяется только на 64-битные ABI).

---

## 1. Проблема

- Google Play требует targetSdk >= 36 (Android 16) с 2026-08-31; иначе публикация обновлений блокируется. Текущее значение: `compileSdk = 35`, `targetSdk = 35` (`app_v2/build.gradle.kts:212,223`).
- Поднятие targetSdk - не правка одной строки: приложение соглашается на поведенческие изменения Android 16 (edge-to-edge, predictive back, игнор orientation/resizability на больших экранах и др.), часть из которых может сломать текущий UX без предварительного аудита.
- Отдельное, но связанное требование Play (16 КБ страницы, для targetSdk >= 35) - уже соблюдено, но не защищено механическим гейтом от регрессии при будущих bump'ах зависимостей.
- Массовое заблуждение, которое нужно закрыть в постановке: bump targetSdk НЕ сокращает поддержку старых устройств - за неё отвечает `minSdk`, который не трогается.

---

## 2. Цели

1. Поднять `compileSdk` и `targetSdk` с 35 до 36 на всех флейворах, сохранив сборку и текущий охват устройств.
2. Пройти аудит поведенческих изменений Android 16 (см. §4): по каждому пункту - либо «не применимо», либо закрыто с доказательством, либо дочерний тикет.
3. Подтвердить и (по решению) защитить гейтом соответствие 16 КБ на release-сборке.

**Non-goals:**

- Поднятие `minSdk` (охват устройств неизменен: 26 для standard/lite/photos/noLegal/vr, 23 для legacy - вплоть до Android 6.0).
- Снятие portrait-lock экрана камеры (отклонено S0754/S0924).
- Полная адаптивная переверстка экрана камеры под большие экраны (горизонт targetSdk 37, см. S0934 §5).

---

## 3. Ключевой вывод: 16 КБ уже закрыт

- Инвентаризация (read-only, 2026-07-22) на реально упаковываемых бинарниках: все 22 нативные `.so` 16 КБ-совместимы на 64-битных ABI. Доказательство: `temp/S1149/readelf_raw.txt`, `temp/S1149/readelf_summary.tsv`.
- Пороговые версии зависимостей уже соблюдены: Tesseract4Android 4.8.0, Chaquopy 17.0.0, MLKit translate 17.0.3 / language-id 17.0.6, CameraX 1.5.3, OpenXR loader 1.1.48; первопартийные FFmpeg-AAR и `libfms_diagnostic_xr` собраны с `-Wl,-z,max-page-size=16384` под NDK r27c.
- Остаточный риск - не текущее несоответствие, а регрессия при будущем bump'е любой нативной зависимости без повторной проверки выравнивания (сейчас проверяется только разовыми ручными спайками). Кандидат на механический гейт (см. §7, решение 2).

---

## 4. Аудит поведенческих изменений Android 16 (targetSdk 36)

Источник: developer.android.com/about/versions/16/behavior-changes-16 (сверено 2026-07-22). Релевантность для FMS и предварительный статус:

- **Edge-to-edge принудительно (opt-out удалён)** - касается всех экранов. Статус: частично закрыто (часть предупреждений Play уже снята через Material 1.14.0); требуется верификация всех экранов под 36 на insets.
- **Predictive back по умолчанию** - `onBackPressed()` не вызывается, `KEYCODE_BACK` не диспатчится. Статус: АУДИТ - найти использования `onBackPressed`/`KEYCODE_BACK`, мигрировать на `OnBackInvokedCallback` либо выставить `android:enableOnBackInvokedCallback="false"` точечно.
- **Игнор orientation/resizability/aspect на больших экранах (sw >= 600dp)** - касается portrait-lock камеры и VR. Статус: ЗАКРЫТО opt-out property (S0934), проверено в живом дереве: `src/main/AndroidManifest.xml:266` (Camera), `src/vr/AndroidManifest.xml:74,99` (VR). Требуется device-test на планшете/раскладушке под 36. Горизонт targetSdk 37: property перестанет действовать (S0934 §5).
- **`MediaStore#getVersion()` теперь уникален на приложение** - FMS активно работает с MediaStore. Статус: АУДИТ - не полагаемся ли на формат/межприложенческое сравнение значения версии.
- **Разрешение на локальную сеть (`NEARBY_WIFI_DEVICES`, enforcement 25Q2-26Q2)** - касается стримов / обнаружения устройств (mDNS/SSDP/NsdManager/raw sockets), если используются. Статус: АУДИТ - обращаемся ли к локальной сети; при необходимости декларировать разрешение.
- **`scheduleAtFixedRate()` выполняет максимум одну пропущенную задачу** - касается воркеров/шедулинга. Статус: АУДИТ использования (низкий приоритет).
- **Предвыбор своих медиа в photo picker при частичном доступе** - касается доступа к фото. Статус: проверить UX (низкий).
- **Deprecated elegant fonts; GPU syscall filter (Mali); safer intents (opt-in); Bluetooth bond intents; granular health perms** - низкая/нулевая релевантность для FMS. Статус: отметить, не блокирует.

---

## 5. Затронутые файлы (предварительно)

- `app_v2/build.gradle.kts` - `compileSdk` 35 -> 36 (строка ~212), `targetSdk` 35 -> 36 (строка ~223).
- Манифесты - уже готовы (S0934), только верификация; правки возможны по итогам аудита predictive back / local network.
- `scripts/quality/assert-16kb-alignment.ps1` - новый гейт (если принято решение 2 из §7).
- `wear/build.gradle.kts` - `targetSdk` 35 -> 36 (если wear включён в объём, решение 1 из §7).
- Документы (по document registry): `dev/TECH_REQUIREMENTS.md` (уже фиксирует факты 16 КБ / targetSdk-35 - строки 247/276/281), `docs/TECH_STACK.md`, `docs/DEV_OPS.md` (таблица версий), `CLAUDE.md` (пины targetSdk).

---

## 6. Критерии готовности

- Все флейворы (standard, lite, photos, legacy, noLegal, vr) собираются на `compileSdk`/`targetSdk` 36.
- Каждый пункт аудита §4 разрешён: «не применимо», закрыто с доказательством, либо запаркован дочерним тикетом.
- Device-test под targetSdk 36 на планшете/раскладушке (sw >= 600dp): экран камеры остаётся portrait, остальные экраны не ломаются в landscape.
- 16 КБ: выравнивание подтверждено на release-сборке; при принятии решения 2 - гейт зелёный в `post-change`/CI.
- Play Console: предупреждение «App must target Android 16» исчезает после публикации; device-reach не сокращается (`minSdk` неизменен - проверить по release-гейту «no coverage regression»).

---

## 7. Решения (утверждено владельцем 2026-07-22)

Все три пункта утверждены: wear включён в объём, механический гейт 16 КБ добавляется, полный аудит поведения Android 16 остаётся внутри S1149.

1. **wear/ в объёме S1149 или отдельный тикет?** Рекомендация: включить отдельной фазой в S1149 - тот же дедлайн Play, модуль native-free (риск низкий), координированный bump проще отслеживать.
2. **Механический гейт 16 КБ в этом тикете?** Рекомендация: да - обобщить рабочую проверку из `scripts/builders/build-ffmpeg-dts.sh:500-589` в `scripts/quality/assert-16kb-alignment.ps1`, чтобы будущие bump'ы Tesseract/MLKit/CameraX/OpenXR/Chaquopy аудировались автоматически, а не разовыми спайками (соответствует Rule 19/20).
3. **S1149 владеет полным behavior-audit Android 16 или только version-bump?** Рекомендация: S1149 владеет аудитом §4 - безопасно поднять targetSdk без него нельзя; найденные нетривиальные дефекты парковать дочерними тикетами.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0934 (large-screen orientation opt-out, реализован), S0386 / S0923 / S0971 (нативная доставка и 16 КБ-ремедиация), S0918 / S0754 (device-reach implied screen.* и ADR portrait-lock камеры).
- **Owner decisions (2026-07-22):** wear включён в объём S1149; механический гейт 16 КБ добавляется; полный аудит поведения Android 16 остаётся внутри S1149.
- **Scope:** модули app_v2 и wear; minSdk не меняется (26 для standard/lite/photos/noLegal/vr, 23 для legacy); компиляция против установленной платформы android-36.1, targetSdk 36.
- **Non-goals confirmed:** без поднятия minSdk, без снятия portrait-lock экрана камеры, без полной адаптивной переверстки камеры (горизонт targetSdk 37).

---

## 8. Связанные тикеты

- S0934 - opt-out от игнорирования orientation/resizability на больших экранах (реализовано; манифест `main:266`, `vr:74,99`).
- S0386 / S0923 / S0971 - нативная доставка, 16 КБ-ремедиация, упаковка `.so` под Play.
- S0918 / S0754 - device-reach implied `screen.portrait/landscape`, ADR portrait-lock камеры (контекст non-goal).

---

## 9. Сроки (Google Play Console)

- 16 КБ страницы (упаковка, для targetSdk >= 35): 2026-05-31 (с возможностью продления) - уже соблюдён.
- targetSdk 36: 2026-08-31 («Action by Aug 31») - основной дедлайн тикета. Запас > 1 года, но нативную/поведенческую верификацию нельзя откладывать на последний момент.

---

## 10. Ход реализации (2026-07-22)

Статус: Implemented. Код/конфиг/сборка/гейт/доки готовы и проверены; на Verified остаётся только device-test поведения Android 16 (онлайн-устройства на момент реализации не было).

Сделано:

- Пины: app_v2 `compileSdk`/`targetSdk` 35 -> 36 (`build.gradle.kts:212,223`); wear 35 -> 36 (`wear/build.gradle.kts:36,43`); benchmark `compileSdk` 36. `minSdk` не тронут (26 / 23 legacy) - охват устройств прежний.
- Сборка на 36: standard fast Kotlin compile PASS (1m43s); noLegal debug APK собран и упакован с полным нативным набором (`v2.60.7221.313`); wear `:wear:compileDebugKotlin` PASS (15s).
- Гейт 16 КБ: `scripts/quality/assert-16kb-alignment.ps1` (обобщение проверки из `build-ffmpeg-dts.sh`); прогон на собранных noLegal `.so` - 40/40 уникальных 64-бит `.so` выровнены, PASS. Вписан hard-stop сигналом в `scripts/release/standard-release-gate.ps1`.
- Аудит поведения Android 16: predictive back - готово (`enableOnBackInvokedCallback=true` + `OnBackPressedCallback` повсеместно); `MediaStore#getVersion()` - не используется (N/A); orientation на больших экранах - opt-out уже в дереве (S0934: `main` manifest:266, `vr`:74/99); edge-to-edge - частично, device-verify; local network - запаркован дочерним **S1150** (opt-in фаза, не блокер).
- Doc-sync: CLAUDE.md + TECH_STACK managed-блок регенерированы (`generate-toolchain-pins -Write`); `dev/TECH_REQUIREMENTS.md` compileSdk/targetSdk -> 36; `check-doc-vs-gradle` fail 0; `assert-doc-pin-drift` PASS. Все fast static gates PASS (exit-contract, ticket-logs, neuroslop, orientation-implied-feature).

Остаётся для Verified (device-test):

- Планшет/раскладушка sw >= 600dp под targetSdk 36: экран камеры остаётся portrait, остальные экраны не ломаются в landscape.
- Predictive back: системный жест «назад» с анимацией на реальном устройстве.
- Edge-to-edge: пройтись по экранам под 36 на insets.
- wear: установка и smoke на Wear-устройстве/эмуляторе.
