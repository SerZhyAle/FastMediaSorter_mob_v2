---
layout: default
title: "Обзор архитектуры"
permalink: /docs/V2_architecture_overview_RU.html
lang: ru
---

<div lang="ru" markdown="1">

# Обзор архитектуры

[Техническая спецификация](V2_Specification_RU.html) | [Стек, EN](TECH_STACK.html) | [Требования](TECHNICAL_REQUIREMENTS_RU.html)

Приложение использует MVVM, Hilt, репозитории и use case. Поток выполнения близок к Clean Architecture, но зависимости при компиляции намеренно прагматичны.

## Поток слоёв

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

ViewModel предоставляет наблюдаемое состояние и события. Use case координирует операции. Интерфейсы репозиториев образуют границу для тестирования, реализации отвечают за сохранение данных и локальные, сетевые или облачные источники. Domain также использует отдельные типы слоя data: это не заявление о строгой инверсии всех зависимостей.

## Модули

- `app_v2`: основное приложение и восемь вариантов телефона/гарнитуры; XML, ViewBinding и существующие поверхности Compose.
- `wear`: приложение Wear OS на Compose, редакции `standard` и `noLegal`.
- `watchface`: отдельный ресурсный пакет Watch Face Format v4 для Wear OS 6 / API 36 и новее.
- `lint-rules`: проверки Android lint при сборке.
- `benchmark`: инструменты измерений и baseline profile.

## Ключевые принципы реализации

- Состояние представления хранится в ViewModel; сложное поведение экранов передаётся manager/helper-классам, а не добавляется в Activity.
- Room хранит состояние приложения. Миграции и восстановление различаются по модулям; восстановление не заменяет проверку миграций.
- Наборы исходников редакций и внедряемые контракты выбирают необязательные интеграции.
- Телефон и Wear используют application ID `com.sza.fastmediasorter`, но у Wear отдельный namespace. Для Data Layer важны совместимые идентификаторы и подписи.
- Циферблат не входит в код приложения Wear: его XML отображает операционная система.

### Подсистема Трансляций

`StreamsActivity` и `StreamsViewModel` отображают список. Импорт каталога использует `ImportStreamCatalogUseCase` и `StreamSourceRepository` с сущностями и DAO Room. `StreamInlineAudioManager` управляет аудио в списке и ICY-метаданными; полноэкранное видео использует существующий путь плеера.

Протоколы и доступность экрана зависят от редакции; сверяйтесь со [сгенерированной матрицей](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md). Каталог не гарантирует доступность удалённого источника или совместимость кодека.

### Файловые операции и лаунчер

Операции передаются стратегиям соответствующего протокола. Перенос папки между протоколами выполняется поэлементно: после отмены уже записанные элементы могут остаться, а источник удаляется лишь после подтверждения копии. Транзакционного отката всей папки нет.

В редакциях с лаунчером доступны рабочий стол, панель задач и гаджеты. Домашний экран выбирает сам пользователь. Раскладки портретного и альбомного рабочего стола хранятся независимо.

## Источники реализации

- [Список модулей Gradle](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/settings.gradle.kts)
- [Редакции телефона и наборы исходников](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/app_v2/build.gradle.kts)
- [Идентификаторы и упаковка Wear](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/wear/build.gradle.kts)
- [Пакет Watch Face Format](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/watchface/build.gradle.kts)

Практические настройки описаны в [документации пользователя](../documentation/index-ru.html).

</div>
