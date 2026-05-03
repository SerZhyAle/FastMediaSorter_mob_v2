# DESIGN: Замена иконок ресурсов (Этап 8.2)

В рамках задачи заменяются 17 SVG-файлов иконок в директории `app_v2/src/main/res/drawable/`. Все новые иконки соответствуют стандарту Material Icons (24x24dp, один слой `path` с `fillColor="#000000"`).

## Маппинг замен:

| Файл | Было (Проблема) | Будет (Новая Material Icon) | Назначение |
|---|---|---|---|
| **ico_02_004.xml** | 5-конечная звезда (WRONG SET) | **movie** (Кинохлопушка) | Видео |
| **ico_02_005.xml** | Штрихкод (WEAK) | **theaters** (Киноплёнка) | Видео |
| **ico_02_006.xml** | Прямоугольник (WEAK) | **tv** (Телевизор) | Видео |
| **ico_02_007.xml** | Зеркалка (WRONG SET) | **video_library** (Видеобиблиотека) | Видео |
| **ico_02_008.xml** | VR-очки (WEAK) | **play_circle_outline** (Кнопка Play в круге) | Видео |
| **ico_03_001.xml** | Дом (WRONG SET) | **photo_library** (Фотогалерея) | Изображения |
| **ico_03_003.xml** | Цветочный паттерн (WEAK) | **collections_bookmark** (Коллекция с закладкой)| Изображения |
| **ico_03_004.xml** | Автомобиль (WRONG SET) | **image** (Пейзаж с горами) | Изображения |
| **ico_03_005.xml** | Лист (WEAK) | **camera** (Объектив-диафрагма) | Изображения |
| **ico_03_009.xml** | Диаграмма (WRONG SET) | **camera_roll** (Фотоплёнка) | Изображения |
| **ico_03_010.xml** | Гео-пин (WRONG SET) | **portrait** (Портрет) | Изображения |
| **ico_04_003.xml** | Папка (WEAK) | **description** (Текстовый документ) | Документы |
| **ico_04_008.xml** | Портфель (WEAK) | **library_books** (Стопка книг) | Документы |
| **ico_04_010.xml** | Шапочка выпускника (WEAK) | **assignment** (Планшет с заданием) | Документы |
| **ico_05_005.xml** | Предупреждение (WEAK) | **apps** (Сетка 3x3) | Абстракция |
| **ico_05_007.xml** | Звезда (DUPLICATE) | **category** (Геометрические фигуры) | Абстракция |
| **ico_05_019.xml** | Граф с тонкими линиями (WEAK) | **extension** (Пазл) | Абстракция |

## Структура файла для внедрения:
Каждый файл будет иметь следующий XML:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorOnSurface">
    <path
        android:fillColor="#000000"
        android:pathData="[УНИКАЛЬНЫЙ_ПУТЬ]" />
</vector>
```

## Правки Markdown-каталога:
- Описание 17 иконок в таблицах будет заменено на новые, статусы 🔴, 🟡, 🟠 изменены на ✅.
- Произведена замена `...` на `..` и исправлены окончания/гласные (ё) согласно правилам проекта.

**Gate 8.2**: Перехожу к этапу 8.3 (Planning) и 8.4 (Implementation).
