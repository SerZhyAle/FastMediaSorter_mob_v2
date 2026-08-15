# S0399 — Тактическая спецификация: страница профиля устройства

**Status:** Tactical
**Strategic:** `PLAN/S0399_welcome-profile-page.md`
**Research:** `PLAN/S0395_welcome-screens-redesign-research/research/03__page1-device-profiles.md`

## Зафиксированные решения (закрывают research open questions)

- Позиция страницы — индекс 1 (после hero, перед networks). Итог порядка пейджера: hero(0) → profile(1) → networks(2) → functionality(3) → permissions(4) → default-player(5, cond).
- Плитки — переиспользуем `DeviceProfileTileAdapter` + `item_device_profile_tile.xml` как есть (ПОЛНЫЕ плитки). «Минимальные отступы» = уменьшенный page-padding, без нового layout плитки.
- Колонки — правило диалога: sw≥720→3, sw≥480→2, иначе 1.
- Порядок «малые экраны вверху» — page-локальный transform над `selectableProfiles`; глобальный `DeviceProfileUi.displayOrder` НЕ трогаем (иначе переедет диалог настроек). Порядок: PERSONAL_SMARTPHONE, PHOTO_FRAME, EBOOK_READER, AUDIO_PLAYER, VIDEO_PLAYER, MEDIA_PLAYER, HOME_TABLET, TV_MEDIA_BOX, CAR_HEAD_UNIT, VR_HEADSET, OTHER.
- Рекомендованный — предвыбран, бейдж (существующий), автоскролл к его позиции после bind.
- D-pad — грид поглощает LEFT/RIGHT на краях строки (без листания страницы). Спец-кейс в `WelcomeActivity.handleSliderHorizontal`.
- Refresh-хук — холдер страницы кэширует ссылку на внутренний `DeviceProfileTileAdapter`, прямой `setSelected` из `observeData` (обход ненадёжного rebind ViewPager2).
- Skip — вне scope (кнопки Skip нет; S0398 её удалил). Семантику `saveDeviceProfile` не трогаем.
- Диалог `DeviceProfilePickerDialogFragment` остаётся для настроек; welcome-обвязка удаляется.

## Контракт файлов (для параллельной реализации)

NEW (disjoint, владелец — агент A):
- `res/layout/page_welcome_profiles.xml` + `res/layout-land/page_welcome_profiles.xml` → биндинг `PageWelcomeProfilesBinding`.
- `ui/welcome/holders/ProfilesPageViewHolder.kt` (грид + autoscroll + кэш адаптера).

SHARED (центрально, владелец — оркестратор):
- `WelcomePagerAdapter.kt` — VIEW_TYPE_PROFILES, dispatch, поля `WelcomePage` (isProfilesPage, recommended/selected, onProfileSelected). Удалить page-0 profile card блок + `profileSelectorBinding`/`refreshSelectedProfile`/`bindSelectedProfileCard`.
- `WelcomeActivity.kt` — вставка `pagesList.add` на индекс 1; удалить `showProfilePicker`, result-listener, page-0 mutation; D-pad спец-кейс.
- `page_welcome_enhanced.xml` (+land) — удалить `layoutProfileSelector` блок.
- strings — page title/subtitle (переиспользуем `welcome_profile_selector_title` как заголовок; +1 подзаголовок).

## Фазы

1. Skeleton: VIEW_TYPE_PROFILES + stub холдер/layout, поле страницы, вставка в pagesList(1). Build green.
2. Удаление page-0 обвязки профиля (адаптер + Activity + enhanced layout оба ориентации).
3. Грид-холдер: адаптер, колонки, page-локальный порядок, автоскролл, кэш-рефреш.
4. D-pad edge absorption для грида.
5. Cleanup dead-weight (Rule 20), build все флейворы.

## Валидация
- assembleStandardDebug + assembleLiteDebug green.
- Catalog sync app_v2.
