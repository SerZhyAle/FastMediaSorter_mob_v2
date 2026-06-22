# SOLUTION DESIGN (Дизайн решения): S0588 - replenish-streams-catalog

**Билет:** S0588  
**Название:** replenish-streams-catalog  
**Дата:** 2026-06-21  

---

## 1. Структура новых записей каталога

Все новые записи будут добавлены в `delivery/stream-catalog/streams.csv` с сохранением заголовков и формата RFC-4180. Ниже приведена таблица предлагаемых изменений с их классификацией.

### 1.1 Новые источники Live TV (Видео-трансляции)
| category | topic | name | url | media_kind | protocol | format | bitrate | is_live | https | language | country | homepage | source_kind | license_note | notes | confidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Live TV | News | Al Jazeera English | https://live-hls-web-aje.getaj.net/AJE/index.m3u8 | VIDEO | HLS | m3u8 | | true | true | english | QA | https://www.aljazeera.com/ | PUBLIC_BROADCASTER | "Al Jazeera, publicly funded international news channel" | Al Jazeera English web live feed | medium |
| Live TV | News | Bloomberg TV US | https://www.bloomberg.com/media-manifest/streams/us.m3u8 | VIDEO | HLS | m3u8 | | true | true | english | US | https://www.bloomberg.com/ | PUBLIC_BROADCASTER | "Bloomberg TV, free news stream" | Bloomberg US business live stream | high |
| Live TV | News | Bloomberg TV Europe | https://www.bloomberg.com/media-manifest/streams/eu.m3u8 | VIDEO | HLS | m3u8 | | true | true | english | US | https://www.bloomberg.com/ | PUBLIC_BROADCASTER | "Bloomberg TV, free news stream" | Bloomberg Europe business live stream | high |
| Live TV | News | Bloomberg TV Asia | https://www.bloomberg.com/media-manifest/streams/asia.m3u8 | VIDEO | HLS | m3u8 | | true | true | english | US | https://www.bloomberg.com/ | PUBLIC_BROADCASTER | "Bloomberg TV, free news stream" | Bloomberg Asia business live stream | high |
| Live TV | General | Bloomberg TV Originals | https://www.bloomberg.com/media-manifest/streams/originals.m3u8 | VIDEO | HLS | m3u8 | | true | true | english | US | https://www.bloomberg.com/ | PUBLIC_BROADCASTER | "Bloomberg TV, free news stream" | Bloomberg Originals global live stream | high |
| Live TV | News | Euronews English | https://cdn-euronews.akamaized.net/live/eds/euronews-en/25080/euronews-en.m3u8 | VIDEO | HLS | m3u8 | | true | true | english | FR | https://www.euronews.com/ | PUBLIC_BROADCASTER | "Euronews, free news stream" | Euronews English live feed on Akamai CDN | medium |

### 1.2 Новые источники Radio (Аудио-трансляции)
| category | topic | name | url | media_kind | protocol | format | bitrate | is_live | https | language | country | homepage | source_kind | license_note | notes | confidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Radio | News | Українське Радіо (UR 1) | http://radio.ukr.radio:8000/ur1-mp3 | AUDIO | ICECAST | mp3 | 192 | true | false | ukrainian | UA | http://www.nrcu.gov.ua/ | PUBLIC_BROADCASTER | "Suspilne public broadcaster, free live stream" | UA public radio channel 1 | high |
| Radio | Pop | Радіо Промінь (UR 2) | http://radio.ukr.radio:8000/ur2-mp3 | AUDIO | ICECAST | mp3 | 192 | true | false | ukrainian | UA | http://www.nrcu.gov.ua/ | PUBLIC_BROADCASTER | "Suspilne public broadcaster, free live stream" | UA public radio channel 2 | high |
| Radio | Classical | Радіо Культура (UR 3) | http://radio.ukr.radio:8000/ur3-mp3 | AUDIO | ICECAST | mp3 | 192 | true | false | ukrainian | UA | http://www.nrcu.gov.ua/ | PUBLIC_BROADCASTER | "Suspilne public broadcaster, free live stream" | UA public radio channel 3 | high |
| Radio | News | Радіо NV | https://online-radio.nv.ua/radionv.mp3 | AUDIO | ICECAST | mp3 | 128 | true | true | ukrainian | UA | https://radio.nv.ua/ | COMMUNITY | "Radio NV, public news and talk radio stream" | Ukrainian talk/news radio | high |
| Radio | News | Громадське Радіо | http://91.218.212.67:8000/stream | AUDIO | ICECAST | mp3 | 192 | true | false | ukrainian | UA | https://hromadskeradio.org/ | COMMUNITY | "Hromadske Radio, non-profit talk radio stream" | Ukrainian independent talk radio | high |
| Radio | News | Єдині Новини (Radio News) | https://online-news.radioplayer.ua/RadioNews | AUDIO | ICECAST | mp3 | 64 | true | true | ukrainian | UA | https://radioplayer.ua/ | PUBLIC_BROADCASTER | "Radioplayer UA, official national news stream" | UA United News radio stream | high |
| Radio | Electronic | Kiss FM (Ukraine) | http://online.kissfm.ua/KissFM | AUDIO | ICECAST | mp3 | 128 | true | false | ukrainian | UA | https://www.kissfm.ua/ | COMMUNITY | "Kiss FM Ukraine, free web radio stream" | Popular dance/electronic station | high |
| Radio | Rock | Radio ROKS | http://online.radioroks.ua/RadioROKS_HD | AUDIO | ICECAST | mp3 | 320 | true | false | ukrainian | UA | https://www.radioroks.ua/ | COMMUNITY | "Radio ROKS, free rock music stream" | Main rock station, 320k | high |
| Radio | Rock | Radio ROKS Ballads | http://online.radioroks.ua/RadioROKS_Ballads_HD | AUDIO | ICECAST | mp3 | 320 | true | false | ukrainian | UA | https://www.radioroks.ua/ | COMMUNITY | "Radio ROKS, free rock music stream" | Rock ballads channel, 320k | high |
| Radio | Rock | Radio ROKS Hard'n'Heavy | http://online.radioroks.ua/RadioROKS_HardnHeavy_HD | AUDIO | ICECAST | mp3 | 320 | true | false | ukrainian | UA | https://www.radioroks.ua/ | COMMUNITY | "Radio ROKS, free rock music stream" | Hard'n'Heavy channel, 320k | high |
| Radio | Pop | Мелодія FM | http://online.melodiafm.ua/MelodiaFM | AUDIO | ICECAST | mp3 | 128 | true | false | ukrainian | UA | https://www.melodiafm.ua/ | COMMUNITY | "Melodia FM, free pop music stream" | Adult contemporary/pop music | high |
| Radio | Pop | Наше Радіо | http://online.nasheradio.ua/NasheRadio | AUDIO | ICECAST | mp3 | 128 | true | false | ukrainian | UA | https://www.nasheradio.ua/ | COMMUNITY | "Nashe Radio UA, free pop music stream" | Ukrainian pop music | high |
| Radio | Ambient | Радіо Relax | http://online.radiorelax.ua/RadioRelax | AUDIO | ICECAST | mp3 | 128 | true | false | ukrainian | UA | https://www.radiorelax.ua/ | COMMUNITY | "Radio Relax UA, free relax music stream" | Light pop and ambient music | high |
| Radio | Pop | Люкс FM | http://icecastdc.luxnet.ua/lux_mp3_128 | AUDIO | ICECAST | mp3 | 128 | true | false | ukrainian | UA | https://lux.fm/ | COMMUNITY | "Lux FM, free pop music stream" | Popular hits station | high |
| Radio | Pop | Радіо Максимум | http://icecastdc.luxnet.ua/maximum_mp3_128 | AUDIO | ICECAST | mp3 | 128 | true | false | ukrainian | UA | https://maximum.fm/ | COMMUNITY | "Radio Maximum UA, free pop music stream" | Modern pop and rock hits | high |
| Radio | Eclectic | Серебряный Дождь | https://silverrain.hostingradio.ru/silver128.mp3 | AUDIO | ICECAST | mp3 | 128 | true | true | russian | RU | https://www.silver.ru/ | COMMUNITY | "Silver Rain Radio, free eclectic radio stream" | Russian independent talk/music radio | high |
| Radio | Electronic | DFM Дискач 90-х | https://dfm-disc90.hostingradio.ru/disc9096.aacp | AUDIO | ICECAST | aac+ | 96 | true | true | russian | RU | https://dfm.ru/ | COMMUNITY | "DFM, free dance music stream" | 90s eurodance channel | high |
| Radio | Oldies | Ретро FM | http://retroserver.streamr.ru:8043/retro256.mp3 | AUDIO | ICECAST | mp3 | 256 | true | false | russian | RU | http://retrofm.ru/ | COMMUNITY | "Retro FM, free retro hits stream" | 70s-90s retro music | high |
| Radio | Rock | Наше Радио (RU) | http://nashe1.hostingradio.ru/nashe-128.mp3 | AUDIO | ICECAST | mp3 | 128 | true | false | russian | RU | http://www.nashe.ru/ | COMMUNITY | "Nashe Radio RU, free rock music stream" | Russian rock music station | high |

## 2. Верификация решений
После добавления записей в `streams.csv` мы выполним следующие шаги проверки:
1. **Проверка liveness:** Запуск `check-liveness.ps1` для подтверждения доступности всех добавленных потоков.
2. **Сборка проекта:** Будет запущен `./a.ps1 fk` для проверки корректности Kotlin кода.
3. **Парсинг CSV:** Мы проверим, что наши изменения в CSV-файле не ломают парсер `StreamCatalogCsvParser`, запустив существующие модульные тесты (`StreamCatalogCsvParserTest.kt`), созданные в рамках билета S0582.
