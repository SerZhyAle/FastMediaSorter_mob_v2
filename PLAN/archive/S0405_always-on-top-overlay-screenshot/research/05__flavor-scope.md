# 05 - Набор flavor и изоляция способности

**Research item:** §6.5
**Дата:** 2026-06-11
**Статус:** Resolved (рекомендация; финальный набор - sign-off владельца)

## Вопрос

На какие flavor выкатывать способность и как изолировать.

## Факты по `app_v2/build.gradle.kts`

- `SUPPORT_IMAGES=true` во всех flavor (standard, lite, photos, legacy, noLegal/VR) - сохранять снимок есть куда везде.
- minSdk: standard/lite/photos = 26; legacy = 23; noLegal (VR) = 26.
- lite позиционируется как лёгкий: `SUPPORT_CLOUD=false`, `SUPPORTS_DEFAULT_PLAYER=false`, анимации off - системный оверлей+захват противоречат идее «минимальный/быстрый».
- photos - image-центричный (`SUPPORT_DEFAULT_PLAYER=true`, cloud on): сильный продуктовый фит для «снять экран → в фото-ресурс».
- noLegal - sideload-VR (Quest): always-on-top оверлей поверх других приложений в панельном VR-контексте смысла не имеет.

## Рекомендация (уточнена 2026-06-11 - фазовый rollout)

- **Первый таргет - `noLegal`** (sideload-only, не проходит ревью Google Play). По конфигу noLegal несёт полную standard-поверхность, а VR-контролы рантайм-гейтятся `XrRuntimeAvailability`, то есть на обычном телефоне noLegal ведёт себя как standard. Это идеальная площадка обкатать чувствительные пермишены (overlay + MediaProjection) без policy-гейта Play. Фича позиционируется как телефонная; на реальном XR-устройстве гейтится тем же XR-механизмом, что и прочие phone-only сценарии.
- **Потом Play-таргеты: `standard` + `photos`** - после обкатки на noLegal, с корректным раскрытием и consent-моделью (см. §6.4).
- Исключить: `lite` (противоречит лёгкости), `vr` (Store-VR shell - не телефонная парадигма).
- `legacy` (API 23..25): тихий `takeScreenshot` недоступен; MediaProjection работает, но это старые устройства. По умолчанию - вне первой итерации; пересмотреть при спросе.

## Почему ранний «исключить noLegal» был неверен

- Ошибочно считалось, что noLegal = VR-only и оверлей поверх приложений там не имеет сценария. На деле noLegal - sideload-сборка полной поверхности, штатно ставится на телефоны; VR лишь рантайм-гейтится. Поэтому noLegal - не исключение, а наоборот лучший первый таргет.

## Изоляция (обязательно)

- Интерфейс способности - в `src/main/`; реализация (оверлей-сервис, захват, маршрутизация) - в `src/<flavor>/java/`; отдельный flavor-Hilt-модуль (`dev/FLAVOR_DEVELOPMENT_RULES.md`).
- Запрещены `BuildConfig.IS_*`/`SUPPORT_*`-проверки способности внутри `src/main/` (CLAUDE.md Rule 15). Flavor без способности компилируется с no-op/отсутствием реализации.
- Возможен новый capability-флаг (например `SUPPORT_SCREEN_OVERLAY_CAPTURE`) для гейтинга UI-входа и манифестных компонентов на фазе `/spec-tech`.

## Источники

- `app_v2/build.gradle.kts` (flavor `buildConfigField`-блоки, minSdk).
- `dev/FLAVOR_DEVELOPMENT_RULES.md`.
