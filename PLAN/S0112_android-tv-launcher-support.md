# Стратегическая спецификация: S0112 — Android TV Launcher Visibility + D-pad Focus

**Ticket:** S0112
**Status:** Approved
**Priority:** 55
**Date:** 2026-05-08
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — запрос 2026-05-08
**Tactical plan:** `PLAN/S0112_android-tv-launcher-support/INDEX.md`

<!-- auto-approved by /spec-all — 2026-05-08 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

После публикации в Google Play приложение не появляется в списке приложений на Panasonic Google TV (Android 11). Причина — три отсутствующих компонента:

- В `AndroidManifest.xml` нет категории `LEANBACK_LAUNCHER` в `intent-filter` главной Activity: TV-лаунчер не знает, что приложение умеет запускаться на телевизоре.
- Отсутствует `android:banner` — TV-лаунчер не может отобразить иконку приложения в сетке, поэтому приложение скрыто даже после установки.
- Шесть item-layout'ов RecyclerView не имеют `android:focusable="true"`, что делает соответствующие экраны неиспользуемыми с пультом (фокус системы «проваливается» сквозь элементы).

S0081 (Verified) уже добавил `android.software.leanback required="false"` и базовое key-mapping. Данная задача — недостающее продолжение: видимость в TV-лаунчере и навигабельность пультом в оставшихся экранах.

---

## 2. Цели

- Приложение отображается в лаунчере Google TV / Android TV сразу после установки.
- TV-баннер показывается в лаунчере (XML-placeholder с фирменным цветом + иконкой; дизайнерский PNG — отдельная задача).
- Пульт (D-pad) позволяет навигировать по экранам дубликатов, epub, планировщика операций и переименования файлов без «провалов» фокуса.
- Кнопки управления плеером в кастомном controller layout остаются фокусируемыми (Media3 PlayerView обрабатывает DPAD автоматически при наличии focusable-кнопок).
- Телефонное и VR-поведение не изменяется: `LEANBACK_LAUNCHER` аддитивен, phone-лаунчер продолжает использовать `LAUNCHER`.

**Non-goals:**

- Дизайнерский TV banner PNG (отдельный арт-актив).
- Leanback-компоненты или 10-foot UI.
- TV-специфичный flavor или package id.
- Адаптация шрифтов/отступов под телевизионный экран.

---

## 3. Ограничения

- Изменения применяются ко всем flavors через общий `AndroidManifest.xml`; flavor-специфичные манифесты не затрагиваются.
- `android:focusableInTouchMode` не устанавливается — нарушает touch UX на телефонах.
- Landscape-counterparts item-layout'ов (если существуют) получают те же атрибуты в том же коммите.

---

## 4. Решение (summary)

- Добавить `android.intent.category.LEANBACK_LAUNCHER` в intent-filter MainActivity.
- Добавить `android:banner="@drawable/tv_banner"` в тег `<application>`.
- Создать `res/drawable/tv_banner.xml` — layer-list с фоном `#1A1A2E` и центрированной иконкой `@mipmap/ic_launcher`.
- Добавить `android:focusable="true"` к root-view в: `item_duplicate_file`, `item_epub_search_result`, `item_epub_toc`, `item_duplicate_group`, `item_rename_file`, `item_scheduled_operation`.
- Проверить `custom_player_controls.xml` и `custom_player_controls_large.xml` — все интерактивные кнопки должны иметь `android:focusable="true"`.

---

## 5. Открытые вопросы

Нет.

---

## Last Audit

**Date:** 2026-05-08  
**Result:** Verified ✅  
**Build:** standard debug — PASS (32s)

**Verified predicates:**
- `LEANBACK_LAUNCHER` present in MainActivity intent-filter at line 93 ✓
- `android:banner="@drawable/tv_banner"` in `<application>` at line 70 ✓
- `res/drawable/tv_banner.xml` created (620 bytes, layer-list dark navy + ic_launcher) ✓
- `tv_banner_bg` color added to `colors.xml` ✓
- `android:focusable="true"` on root view in 5/5 targeted item layouts ✓
- `item_rename_file.xml` skipped — root is TextInputLayout; inner TextInputEditText handles focus natively ✓
- Player controls: all ImageButton elements use `Widget.AppCompat.Button.Borderless` (focusable by style); no changes needed ✓
- `Timber.d("S0112: ...")` tag present in FastMediaSorterApp.kt ✓
- No landscape counterparts existed for the 6 item layouts ✓

**Manual items:**
- On-device verification on Panasonic Google TV — install APK and confirm app appears in launcher
- Google Play Console: verify Android TV distribution is enabled for this app
- Designer asset: replace `res/drawable/tv_banner.xml` with a proper 320×180 px PNG when available
