---
layout: default
title: "🏗️ Обзор архитектуры"
permalink: /docs/V2_architecture_overview_RU.html
---

# 🏗️ Обзор архитектуры

FastMediaSorter v2 использует **Clean Architecture + MVVM + Hilt**.

## Поток слоёв

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

## Модули

- `app_v2/` - основное Android-приложение
- `wear/` - companion-приложение для Wear OS

## Ключевые принципы реализации

- UI-слой содержит только presentation-логику
- Бизнес-правила сосредоточены в domain use case
- Data-слой отвечает за network/cloud/local providers
- Сложная логика Activity делегируется helper/manager классам

### Подсистема Трансляций

- Отдельный `StreamsActivity` / `StreamsViewModel` со своим `StreamsRepository` и `StreamDataSource`.
- Встроенное аудиовоспроизведение через `StreamInlineAudioManager`; мини-панель управления отображает ICY-метаданные (станция/трек), не скрывая список.
- Видео/RTSP открываются в полноэкранном плеере; кнопка «Назад» возвращает к списку с сохранением позиции прокрутки.
- Импорт каталога: `ImportStreamCatalogUseCase` загружает кураторский каталог с быстрым тайм-аутом при недоступном хосте.
- Матрица вариантов: standard/legacy/noLegal/vr - полный набор (HLS, DASH VOD, RTSP, progressive HTTP/ICY, `SUPPORT_STREAMS=true`); lite/photos - функция отсутствует, точки входа нет (`SUPPORT_STREAMS=false`, в lite скрыта в S0575).

Полная архитектурная документация: [ARCHITECTURE.md](ARCHITECTURE.md).
