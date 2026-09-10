---
layout: default
title: "🏗️ Огляд архітектури"
permalink: /docs/V2_architecture_overview_UK.html
---

# 🏗️ Огляд архітектури

FastMediaSorter v2 використовує **Clean Architecture + MVVM + Hilt**.

## Потік шарів

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

## Модулі

- `app_v2/` - основний Android-застосунок
- `wear/` - companion-застосунок для Wear OS

## Ключові принципи реалізації

- UI-шар містить лише presentation-логіку
- Бізнес-правила зосереджені в domain use case
- Data-шар відповідає за network/cloud/local providers
- Складна логіка Activity делегується helper/manager класам

### Підсистема Трансляцій

- Окремий `StreamsActivity` / `StreamsViewModel` із власним `StreamsRepository` і `StreamDataSource`.
- Вбудоване аудіовідтворення через `StreamInlineAudioManager`; міні-панель керування відображає ICY-метадані (станція/трек), не приховуючи список.
- Відео/RTSP відкриваються у повноекранному плеєрі; кнопка «Назад» повертає до списку зі збереженням позиції прокрутки.
- Імпорт каталогу: `ImportStreamCatalogUseCase` завантажує кураторський каталог із швидким тайм-аутом при недоступному хості.
- Матриця варіантів: standard/legacy/noLegal/vr - повний набір (HLS, DASH VOD, RTSP, progressive HTTP/ICY, `SUPPORT_STREAMS=true`); lite/photos - функція відсутня, точки входу немає (`SUPPORT_STREAMS=false`, у lite схована в S0575).

Повна архітектурна документація: [ARCHITECTURE.md](ARCHITECTURE.md).
