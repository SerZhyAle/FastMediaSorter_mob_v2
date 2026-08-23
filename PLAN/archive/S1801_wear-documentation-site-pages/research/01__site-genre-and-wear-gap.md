# 01 - Жанр страниц сайта и разрыв по Wear

**Дата:** 2026-08-18
**Метод:** чтение файлов рабочего дерева, генератора реестра и sitemap; проб устройств через adb.

## Как устроен сайт

Сайт собирается GitHub Pages через Jekyll (`_config.yml`, тема `jekyll-theme-cayman`). Репозиторный генератор HTML отсутствует. Источников два:

- Корневые `.html`, написанные руками: `index.html`, `index-ru.html`, `index-uk.html`, `nolegal*.html`.
- Файлы `.md` под `docs/` с Jekyll-фронтматтером `layout` / `title` / `permalink`. Jekyll собирает каждый в страницу по объявленному `permalink`. Скомпилированных `.html` в репозитории нет.

`sitemap.xml` генерируется `scripts/document_registry/generate.ps1` из `docs/DOCUMENT_REGISTRY.jsonl`. Правило: один `<url>` на запись реестра с `published && indexable`, либо по одному на локаль, если запись несёт `localized_urls` с альтернативами `hreflang`. Поле `paths` (glob) в sitemap не разворачивается. Всего в файле 19 URL.

## Жанр, который просит владелец, уже существует

`docs/howto/` - зрелый жанр пошаговых сценариев, 29 файлов:

- Девять сценариев в EN, у каждого сиблинги `-ru.md` и `-uk.md` в том же каталоге: `scenario-companion-share`, `scenario-camera-backup`, `scenario-car-music`, `scenario-download-organizer`, `scenario-home-cinema`, `scenario-internet-radio`, `scenario-photo-frame`, `scenario-smb-setup`.
- Индекс `index.md` (+ `-ru`, `-uk`) с `permalink: /docs/howto/`. Две точки входа внутри него: список «Pick Your Guide in 10 Seconds» и таблица «All Guides» с колонками Guide / What you get / Time / Flavor.
- `SCREENSHOTS.md` - мастер-таблица всех требуемых скриншотов: имя файла, сценарий, шаг, что обязано быть видно на экране, источник съёмки. Плюс каталог `screenshots/` с 33 PNG.

Анатомия одной страницы: фронтматтер из трёх ключей, H1 с иконкой из `../icons/doc/ic_*.png`, строка уровня и flavor'ов, строка переключения языка, абзац «Plain English explanation», раздел «What You Will Need», нумерованные шаги со скриншотами или заглушками `<!-- TODO screenshot: .. -->`, финальный раздел с перекрёстными ссылками, раздел «How It Works», таблица Troubleshooting.

## Чего по Wear нет

- Ни одной сценарной страницы про часы. В `docs/howto/index.md` нет строки про Wear.
- В `docs/FEATURES.md` (публичная витрина) нет ни одной записи про часы, при том что в `docs/ALL_FEATURES.jsonl` (инвентарь разработчика) их 14.
- `index.html` - лендинг сайта - не содержит ни одной ссылки на страницы Wear (`grep -c "WEAR" index.html` даёт 0).

## Что по Wear есть

Восемь файлов `docs/WEAR_OS_*.md`, все EN-only, все с Jekyll-фронтматтером, то есть все собираются в публичные URL:

- Для разработчика: `WEAR_OS_QUICK_START` (запуск модуля из Android Studio), `WEAR_OS_SETUP`, `WEAR_OS_BUILD_CONFIG`.
- Внутренние трекеры: `WEAR_OS_STATUS` (чеклист фаз, «Phase 0-4 Completed»), `WEAR_OS_ROADMAP`, `WEAR_OS_IMPLEMENTATION_STEPS`.
- Ближе к пользователю: `WEAR_OS_SMB_SETUP`, `WEAR_OS_SMB_QUICK_REF` (раздел «On Your Watch»).

Реестр покрывает все восемь одной записью `wear-docs`: `paths: ["docs/WEAR_OS_*.md"]`, `audience: "mixed"`, `published: true`, `indexable: true`, `languages: ["en"]`, единственный `url: "/docs/WEAR_OS_QUICK_START.html"`.

## Следствие, определяющее подход

Из-за правила «один URL на запись» сайт отдаёт поисковику ровно одну страницу про часы, и это инструкция по запуску Gradle-модуля в Android Studio. Пользователь часов, пришедший из магазина, не имеет ни входа, ни страницы, написанной для него. При этом добавить недостающее можно без новой машинерии: жанр, конвенция скриншотов, схема локализации и модель реестра уже готовы и обкатаны на девяти сценариях.
