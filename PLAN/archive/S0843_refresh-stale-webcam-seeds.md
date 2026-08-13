# Стратегическая спецификация: S0843 - Обновить протухшие webcam-сиды сборщика каталога

**Ticket:** S0843
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-01
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - парковка находки при работе над S0805

> **Scope:** STRATEGIC skeleton (`/spec-draft`). Сырой захват находки. Доработать через `/spec` или `/spec-update`.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01 (side-finding при реализации S0805)

**Симптом:**

Вшитый в сборщик `scripts/streams/collect-stream-candidates.ps1` seed-список webcam-оси (`Get-WebcamSeeds`) протух. Все три сида отдают плейлист `200`, но первый сегмент - `404` (класс "заявлен, но не играет"):

- NASA TV Public - `https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8`
- NASA TV Media - `https://ntv2.akamaized.net/hls/live/2013923/NASA-NTV2-HLS/master.m3u8`
- DW Documentary 24/7 - `https://dwamdstream105.akamaized.net/hls/live/2015531/dwstream105/master.m3u8`

**Доказательство:**

Прогон `collect-stream-candidates.ps1 -Axis webcam -PreviewOnly` (после S0805): header-liveness 3/3 alive, deep-signal 0/3 (`segment http 404`, `signal_bytes 0`). Гейт S0805 корректно исключает их, поэтому в каталог они уже не попадут - но seed-список теперь мёртвый груз: ось webcam не даёт ни одной строки.

**Задача (высокоуровнево):**

Найти актуальные рабочие HLS-URL для NASA TV / DW (URL akamai ротируются) либо заменить/расширить seed-список живыми публичными 24/7-фидами; подтвердить глубоким сигналом. Требует ресёрча текущих URL - не однострочник.

**Вложения:**

Вложений нет.

---

## 1. Резолюция (2026-07-02)

`Get-WebcamSeeds` в `scripts/streams/collect-stream-candidates.ps1` перепахан: 3 протухших сида -> 12 живых, каждый подтверждён deep-signal (реальные байты сегмента, не 2xx на плейлисте).

Итог проб (`-CatalogOnly -DeepSignal` по кандидатам, затем `-Axis webcam -PreviewOnly`):

- NASA полностью удалён - публичные akamai-HLS мертвы после ухода на NASA+: старый `ntv1` даёт segment 404, `nasa-i.akamaihd` варианты - 403 (header/geo-gated, app не сможет играть).
- Тема science/space сохранена живыми фидами InWonder (Wonder FAST) и WildEarth (live-сафари вебкамера).
- DW Documentary (`dwstream105`, мёртвый) заменён рабочим DW English (amagi).
- Ось раскладывается по существующему словарю topic: Documentary, News, Science & Space, Outdoor - нового бакета не заводил.

Новый seed-список (все 12 - `alive`, сегмент 16384B):

- Documentary: DW English (DE), CGTN Documentary (CN), Al Arabiya Programs (AE), Asharq Documentary (SA).
- News: France 24 English (FR), Al Jazeera English (QA), CGTN (CN).
- Science & Space: InWonder (NL).
- Outdoor / webcam: WildEarth (ZA), Red Bull TV (AT), AKC TV Puppies 24/7 (US), 30A Darcizzle Offshore (US).

Проверка `-Axis webcam -PreviewOnly` против текущего каталога (2424 строки): 6 сидов новые, 6 уже есть через другие оси; deep-signal 6/6 несут сигнал, 0 pseudo-alive отброшено. Ось webcam снова продуктивна (была 0 строк).

Публикация не выполнялась - `-Publish` в `delivery-so-v1` инициирует владелец. 6 новых живых строк вольются в каталог при следующем прогоне `-WithFavicons -Publish`.

---

## 10. Связи с другими спеками

- S0805 - фильтр живости при сборе каталога (эта находка всплыла при его валидации).
