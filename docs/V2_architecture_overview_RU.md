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

Полная архитектурная документация: [ARCHITECTURE.md](ARCHITECTURE.md).
