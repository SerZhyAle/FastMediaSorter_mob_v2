# Индекс спецификаций разработчика

Этот документ содержит список всех спецификаций, созданных из файла `todo.md`. Каждая спецификация представляет собой отдельную задачу с подробным пошаговым описанием реализации.

## Обзор

Всего создано: **14 спецификаций**

## Категории

### UI и Визуальные элементы

1. [**SPEC_01: Иконки для debug/lite версий**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_01_DEBUG_LITE_ICONS.md)
   - Создание уникальных иконок для разных build variants
   - Сложность: 5/10

2. [**SPEC_02: Цветные фоны Welcome экранов**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_02_WELCOME_BACKGROUNDS.md)
   - Разные неяркие цвета фона для каждой страницы приветствия
   - Сложность: 4/10

7. [**SPEC_07: Цветные фоны миниатюр**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_07_COLORED_THUMBNAILS.md)
   - Генерация миниатюр с цветными фонами по типу файла
   - Сложность: 6/10

8. [**SPEC_08: Placeholder миниатюры при загрузке**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_08_PLACEHOLDER_THUMBNAILS.md)
   - Показ placeholder во время загрузки реальных миниатюр
   - Сложность: 7/10

### Права доступа и разрешения

3. [**SPEC_03: Запрос медиа-прав в Welcome Flow**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_03_MEDIA_PERMISSIONS_WELCOME.md)
   - Добавление запроса разрешений в процесс приветствия
   - Сложность: 6/10

4. [**SPEC_04: Улучшения управления правами**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_04_PERMISSION_IMPROVEMENTS.md)
   - Предупреждения без прав, адаптивные кнопки, переход в настройки
   - Сложность: 7/10

### Настройки и функциональность

5. [**SPEC_05: Группировка Debug настроек**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_05_DEBUG_SETTINGS_GROUP.md)
   - Вынос debug настроек в отдельную группу (скрыта в release)
   - Сложность: 5/10

6. [**SPEC_06: Поведение аудио-ресурсов**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_06_AUDIO_ONLY_BEHAVIOR.md)
   - Специальное отображение для ресурсов только с аудио
   - Сложность: 6/10

12. [**SPEC_12: Toast "Выбрать все"**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_12_SELECT_ALL_TOAST.md)
   - Сообщение с количеством выбранных файлов
   - Сложность: 4/10

### Взаимодействие и Touch Zones

10. [**SPEC_10: Touch зоны в полноэкранном режиме**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_10_FULLSCREEN_TOUCH_ZONES.md)
   - Исправление неработающих тач-зон для изображений
   - Сложность: 8/10

11. [**SPEC_11: Touch зоны после PDF**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_11_TOUCH_ZONES_PDF_TRANSITION.md)
   - Исправление зон при переходе PDF → изображение
   - Сложность: 8/10

### Сеть и SMB

9. [**SPEC_09: Прогресс копирования сетевых файлов**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_09_NETWORK_SHARE_PROGRESS.md)
   - Показ прогресса при share файлов с FTP/SMB
   - Сложность: 7/10

13. [**SPEC_13: Таймаут при неверном пароле SMB**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_13_SMB_PASSWORD_TIMEOUT.md)
   - Немедленная ошибка вместо долгого ожидания
   - Сложность: 7/10

14. [**SPEC_14: SMB Connection Reset**](file:///C:/Users/serzh/.gemini/antigravity/brain/fccfd699-d6cf-455e-b0e8-413bf6a67af2/SPEC_14_SMB_CONNECTION_RESET.md)
   - Исправление функции сброса SMB соединений
   - Сложность: 9/10

## Статистика

### По сложности
- Низкая (1-3): 0 задач
- Средняя (4-6): 7 задач
- Высокая (7-10): 7 задач

### По категориям
- UI и визуальные элементы: 4 задачи
- Права доступа: 2 задачи
- Настройки и функциональность: 3 задачи
- Взаимодействие: 2 задачи
- Сеть и SMB: 3 задачи

## Рекомендации по приоритизации

### Высокий приоритет (критичные для UX)
1. SPEC_04 - Управление правами
2. SPEC_10 - Touch зоны fullscreen
3. SPEC_11 - Touch зоны PDF
4. SPEC_13 - SMB таймаут
5. SPEC_14 - SMB reset

### Средний приоритет (улучшения)
1. SPEC_03 - Права в Welcome
2. SPEC_06 - Аудио-ресурсы
3. SPEC_08 - Placeholder миниатюры
4. SPEC_09 - Прогресс копирования

### Низкий приоритет (визуальные улучшения)
1. SPEC_01 - Иконки
2. SPEC_02 - Welcome цвета
3. SPEC_05 - Debug группа
4. SPEC_07 - Цветные миниатюры
5. SPEC_12 - Toast сообщение

## Примечания

- Все спецификации содержат пошаговые инструкции
- Указаны файлы для изменения
- Описаны критерии приемки
- Включены сценарии тестирования
- Добавлены примеры кода на Kotlin

## Зависимости между задачами

- SPEC_03 ↔ SPEC_04 (разрешения связаны)
- SPEC_07 → SPEC_08 (цветные фоны используются в placeholder)
- SPEC_10 ↔ SPEC_11 (обе про touch zones)
- SPEC_13 ↔ SPEC_14 (обе про SMB)
