---
layout: default
title: "🏗️ Architecture Overview"
permalink: /docs/V2_architecture_overview.html
---

# 🏗️ Architecture Overview

FastMediaSorter v2 follows **Clean Architecture + MVVM + Hilt**.

## Layered flow

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

## Modules

- `app_v2/` — main Android application
- `wear/` — Wear OS companion application

## Key implementation notes

- UI layer contains presentation logic only
- Business rules are concentrated in domain use cases
- Data layer handles network/cloud/local providers
- Complex Activity logic is delegated to helper/manager classes

For full architecture details see [ARCHITECTURE.md](ARCHITECTURE.md).
