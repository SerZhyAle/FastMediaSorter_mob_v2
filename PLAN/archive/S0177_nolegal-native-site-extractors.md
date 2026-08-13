# Стратегическая спецификация: S0177 — noLegal: нативные Kotlin extractors (ArtStation, Vimeo, DeviantArt, Dailymotion)

**Ticket:** S0177
**Status:** Verified
**Implemented date:** 2026-05-12
**Priority:** 45
**Date:** 2026-05-12
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — ресёрч S0156, итерация 2026-05-12
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical spec:** `PLAN/S0177_nolegal-native-site-extractors/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Ряд платформ с предсказуемой и стабильной JSON-структурой (ArtStation, Vimeo, DeviantArt, Dailymotion) сейчас обрабатывается через медленный WebView-fallback с 22-секундным таймаутом. Эти сайты не требуют JavaScript-рендеринга — их медиа-URL лежат в `<script id="__NEXT_DATA__">` или в открытом REST API. Нативный Kotlin extractor даёт ответ за < 1 сек и без аллокации WebView. Эти платформы не нуждаются в yt-dlp (S0174) — задача решается 50-100 строками Kotlin на каждый сайт.

---

## 2. Цели

1. ArtStation: single artwork URL → скачивание изображения или видео в максимальном разрешении без WebView.
2. DeviantArt: single deviation URL → оригинальный файл (не watermarked preview). При наличии сессии — скачивание через авторизованный CDN.
3. Vimeo: single video URL → stream без WebView; поддержка password-protected видео при наличии сессии.
4. Dailymotion: single video URL → stream без WebView.
5. Время от получения URL до начала загрузки ≤ 1 сек для каждой платформы (без учёта сетевой задержки).

**Non-goals:**

- Bulk/gallery загрузка (ArtStation profile, DeviantArt gallery) — это отдельная задача, возможно через gallery-dl.
- Instagram, TikTok, Facebook — покрывается S0174.
- Аудио-only форматы.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. DeviantArt: различать «preview» и «original download» — скачивать оригинал при наличии авторизации.
2. Vimeo: если видео требует пароль — предложить ввод, не падать сразу.

### 3.2 Жёсткие ограничения

- **Flavor:** `noLegal` sourceSet (нет GPL зависимостей в этих extractors — могут жить и в standard, но решение принято держать расширенное extraction в noLegal).
- **API level:** minSdk 26.
- **Wear OS:** не затрагивается.
- **APK size:** нулевой overhead — чистый Kotlin, нет новых зависимостей.
- **Лицензии:** Kotlin-native, нет новых библиотек.
- **Совместимость данных:** без изменений Room.
- **Локализация:** нет новых строк (ошибки — переиспользуют существующие).

---

## 4. Контекст текущей архитектуры

Существующая extraction-цепочка в noLegal: site-стратегии → direct → html → WebView-dynamic. Каждая новая платформа добавляется как отдельная site-стратегия через `@Binds @IntoSet` в noLegal DI-модуле. Паттерн для «JSON в HTML» уже реализован (аналоги в ресёрч-документе S0156). Существующий CDN-download компонент используется для финальной загрузки с заголовками (Referer, UA). Авторизация через `EncryptedCookieStore` + `KnownAuthResources` — ArtStation уже в реестре, DeviantArt требует добавления loginUrl.

---

## 5. Предлагаемый подход

Четыре отдельные site-стратегии, каждая в своём файле. Все следуют единому паттерну:
1. `probe(url)` — проверяет host, возвращает `Applicable` / `NotApplicable`.
2. `open(url)` — HTTP GET страницы → парсинг JSON (`__NEXT_DATA__`, API endpoint, oEmbed) → извлечение media URL → CDN download через существующий компонент с нужными заголовками.
3. При необходимости авторизации — `EncryptedCookieStore.loadFor(host)` через `LinkDownloadSessionContext`.

### 5.1 Основные столпы

**ArtStation extractor**
- `probe`: host == `artstation.com`
- `open`: `GET /projects/<hash>.json` (используется внутренний API) → `assets[].image_url` / `video_clip_url` → CDN download.
- Важно: Требуется установка стандартного `User-Agent` (например, Chrome), так как официального публичного API нет.
- Auth: не требуется для публичных работ.

**DeviantArt extractor**
- `probe`: host `deviantart.com`
- `open`: `GET <url>` (с валидным `User-Agent`) → извлечение через Regex блока `<script>` с `window.__INITIAL_STATE__` → парсинг JSON → `deviation.media.baseUri` + `prettyName` → оригинальный файл URL → CDN download.
- Альтернатива/Fallback: использование официального `oEmbed` API (`backend.deviantart.com/oembed`), но он может отдавать только preview.
- Auth: `EncryptedCookieStore` — при наличии сессии deviantart.com → оригинал вместо preview. Добавить DeviantArt в `KnownAuthResources` с loginUrl.

**Vimeo extractor**
- `probe`: host `vimeo.com`
- `open`: `GET player.vimeo.com/video/<id>/config` → парсинг JSON → stream URL (`.mp4` или `.m3u8`).
- Важно: Vimeo блокирует доступ к прямым файлам (403 Forbidden) без проверки источника. Необходимо **обязательно подменять заголовок `Referer`** (например, на `https://vimeo.com/`) при запросе конфигурации и самого файла.
- Password-protected: при ошибке загрузки (или 403) → fallback на WebView-dynamic с pre-filled password (пожелание 3.1.2, возможно вторая итерация).

**Dailymotion extractor**
- `probe`: host `dailymotion.com`
- `open`: `GET /embed/video/<id>` → парсинг `<script>` → извлечение конфигурации плеера → HLS (`.m3u8`) stream URL → CDN download.
- Важно: Публичный REST API Dailymotion не отдаёт `stream_url` без Enterprise-ключа, поэтому парсинг embed-страницы обязателен. URL-адреса потоков (HLS) обычно подписаны (`sec2(...)`), привязаны к IP и имеют короткий срок жизни (time-limited) — скачивание должно начинаться немедленно после экстракции.

### 5.2 Потоки данных и событий

```
probe(url) → host match → Applicable
open(url)
    → load cookies from EncryptedCookieStore (если нужны)
    → GET page/API
    → parse JSON → media URL
    → DirectFileExtractionStrategy.open(mediaUrl, extraHeaders)
    → OpenResult.Success
```

### 5.3 Точки расширяемости

Паттерн унифицирован — следующие сайты (Flickr, Pinterest, Reddit video и др.) добавляются по той же схеме без изменения инфраструктуры.

---

## 6. Открытые вопросы / Research items

1. **DeviantArt `__INITIAL_STATE__` структура**
   - **Вопрос:** Структура JSON для оригинального файла vs preview в актуальной версии сайта.
   - **Нужно выяснить:** Проверить на нескольких типах deviation (art, photo, gif, literature).
   - **Статус:** Open (проверяется при реализации).

2. **Vimeo password-protected fallback**
   - **Вопрос:** Реализовывать в первой итерации или отложить?
   - **Варианты:** (a) WebView-fallback при ошибке API; (b) диалог ввода пароля; (c) skip в v1.
   - **Статус:** Open — решение на усмотрение владельца.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Сайт меняет JSON-структуру | Средняя | Extractor перестаёт работать | Логировать parse-failures; fallback на WebView-dynamic если parse не удался |
| ArtStation API rate limit | Низкая | 429 при частых запросах | Не кешировать API-вызовы в первой итерации; добавить retry-after при необходимости |
| DeviantArt требует JS для оригинала | Низкая | `__INITIAL_STATE__` не содержит оригинальный URL без авторизации | Проверить на этапе реализации; fallback на WebView-dynamic |
| Истекание токенов (Vimeo/Dailymotion) | Высокая | Ошибка загрузки 403 Forbidden если ссылка получена, но скачивание отложено | Не кешировать извлечённые URL (stream URL); выполнять extractor строго в момент начала скачивания |

---

## 8. Влияние на пользователя (docs/FEATURES)

**EN:** noLegal flavor: fast native extraction for ArtStation, DeviantArt, Vimeo, and Dailymotion — no browser rendering, results in under 1 second.
**RU:** noLegal: быстрое нативное извлечение для ArtStation, DeviantArt, Vimeo и Dailymotion — без рендеринга браузера, результат менее чем за 1 секунду.
**UK:** noLegal: швидке нативне вилучення для ArtStation, DeviantArt, Vimeo та Dailymotion — без рендерингу браузера, результат менш ніж за 1 секунду.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Нативный Kotlin вместо yt-dlp для этих платформ**

- **Решение:** Четыре лёгких Kotlin-стратегии без Python runtime.
- **Альтернативы:** Делегировать все четыре сайта S0174 (yt-dlp).
- **Почему:** yt-dlp с Chaquopy даёт ~2 сек startup overhead; для простых JSON-API это избыточно. Нативный extractor работает за < 100 мс. Если S0174 реализован — можно оставить yt-dlp как fallback; нативный extractor будет `probe()` первым и завершит задачу быстрее.

---

## 10. Связи с другими спеками

- **S0156** — родительский epic.
- **S0174** (yt-dlp) — complementary; S0177 extractors имеют более высокий приоритет в registry для своих платформ; S0174 остаётся fallback.
- **S0171** — если DeviantArt требует авторизации, cookies должны приходить корректно; S0176 fix дополнительно улучшает надёжность.

---

## 11. Критерии готовности (strategic-level)

1. Ссылка на ArtStation artwork скачивает оригинальный файл (не preview) за < 1 сек.
2. Ссылка на DeviantArt deviation скачивает оригинал (при наличии сессии) или preview (без сессии) за < 1 сек.
3. Ссылка на публичное Vimeo видео скачивается без открытия WebView.
4. Ссылка на Dailymotion видео скачивается без открытия WebView.
5. При parse failure (изменилась структура JSON) — управление передаётся WebView-dynamic fallback без ошибки для пользователя.

---

## 12. Ссылка на тактическую спецификацию

**Tactical plan:** `PLAN/S0177_nolegal-native-site-extractors/INDEX.md`

## Last Audit

**Date:** 2026-05-12
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 46 · WARN 0 · FAIL 0 · MANUAL 5 · EXEMPT 0

**Tooling fix applied:** `dev/CATALOG/scripts/render.ps1` `Resolve-SourceLink` was missing `noLegal/java` and `streamingEnabled/java` in `$candidateRoots` — all noLegal class links in `app_v2.md` were pointing to `src/main/java/...`. Fixed and catalog regenerated. Not an implementation defect.

### Manual / on-device

- [ ] ArtStation artwork URL → original file downloaded in < 1 sec (§11.1).
- [ ] DeviantArt deviation URL → original file (with session) or preview (without) in < 1 sec (§11.2).
- [ ] Public Vimeo video URL → download starts without WebView opening (§11.3).
- [ ] Dailymotion video URL → download starts without WebView opening (§11.4).
- [ ] Parse failure scenario → WebView-dynamic fallback invoked, no user-visible error (§11.5).
