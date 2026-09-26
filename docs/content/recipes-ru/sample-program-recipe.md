---
page_id: programs.storage-and-media-statistics
title: Как просмотреть структуру накопителя и медиастатистику
description: Практический рецепт по анализу использования диска, поиску дубликатов и распределению категорий файлов.
category: Программы, статистика и диагностика
category_slug: programs
ticket: S2961
flavor: Все редакции
recipe_number: "03"
canonical_url: documentation/sample-program-recipe-ru.html
ingredients:
  - Установленный FastMediaSorter v2.
  - Предоставленные разрешения на доступ к хранилищу для сканирования медиабиблиотек.
steps:
  - number: 1
    id: step-1
    title: Откройте меню программ и запустите Статистику
    text: На главном экране смахните для открытия боковой панели и выберите **Программы и инструменты** -> **Статистика медиа**.
  - number: 2
    id: step-2
    title: Изучите распределение памяти по типам и размерам
    text: Интерактивная диаграмма наглядно отображает общий объем занятого пространства по категориям «Видео», «Изображения», «Аудио», «Документы» и «Прочее» с возможностью детализации по каждому разделу.
snippets:
  - title: Пример дерева использования хранилища
    path: docs/content/snippets/storage-breakdown-sample.txt
    language: text
next_recipes:
  - title: Воспроизведение и упорядочивание музыки
    url: sample-recipe.html
    badge: Audio
    badge_type: music
    description: Руководство по управлению аудио и видео файлами.
  - title: Настройка параметров воспроизведения и сортировки
    url: sample-settings-recipe.html
    badge: Settings
    badge_type: settings
    description: Настройка ограничений сканирования и индексации диска.
  - title: Сведения о системе и журнал отладки
    url: programs/device-diagnostics-and-logs.html
    badge: System
    badge_type: docs
    description: Экспорт диагностических отчетов и журналов отладки.
---

Быстрый анализ того, какие папки, типы файлов и тяжелые видеоролики занимают место на вашем устройстве, карте памяти или сетевых ресурсах.
