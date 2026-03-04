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

- `app_v2/` — основний Android-застосунок
- `wear/` — companion-застосунок для Wear OS

## Ключові принципи реалізації

- UI-шар містить лише presentation-логіку
- Бізнес-правила зосереджені в domain use case
- Data-шар відповідає за network/cloud/local providers
- Складна логіка Activity делегується helper/manager класам

Повна архітектурна документація: [ARCHITECTURE.md](ARCHITECTURE.md).
