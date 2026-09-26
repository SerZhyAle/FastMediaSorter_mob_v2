---
page_id: programs.storage-and-media-statistics
title: Як переглянути розподіл сховища та статистику медіа
description: Практичний рецепт для перевірки використання диска, дублікатів файлів і розподілу сховища.
category: Програми та інструменти
category_slug: programs
ticket: S2961
flavor: Усі редакції
recipe_number: "03"
canonical_url: documentation/sample-program-recipe-uk.html
ingredients:
  - Встановлений FastMediaSorter v2.
  - Надані дозволи на сховище для сканування медіатек.
steps:
  - number: 1
    id: step-1
    title: Відкрийте меню програм і запустіть Статистику
    text: З головного екрана відкрийте бічну панель свайпом і виберіть **Програми та інструменти** → **Статистика медіа**.
  - number: 2
    id: step-2
    title: Досліджуйте розподіл сховища за типом і розміром
    text: Інтерактивна діаграма показує загальний обсяг використаного місця за категоріями Відео, Зображення, Аудіо, Документи та Інше з можливістю миттєвої деталізації.
snippets:
  - title: Приклад дерева використання сховища
    path: docs/content/snippets/storage-breakdown-sample.txt
    language: text
next_recipes:
  - title: Відтворення та впорядкування музичних файлів
    url: sample-recipe.html
    badge: Аудіо
    badge_type: music
    description: Посібник з керування аудіо- та відеофайлами.
  - title: Налаштування параметрів відтворення та сортування за замовчуванням
    url: sample-settings-recipe.html
    badge: Налаштування
    badge_type: settings
    description: Налаштуйте обмеження сканування та індексації диска.
  - title: Діагностика пристрою та журнали
    url: programs/device-diagnostics-and-logs.html
    badge: Система
    badge_type: docs
    description: Експортуйте діагностичні звіти та журнали.
---

Швидко проаналізуйте, які папки, типи файлів і великі відеофайли займають місце на вашому пристрої, SD-карті чи мережевих ресурсах.
