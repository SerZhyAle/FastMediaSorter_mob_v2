# Спецификация #2: Разные цвета фона для экранов Welcome

## Описание задачи
Реализовать различные цвета фона для каждой страницы Welcome Activity. Цвета должны быть неяркими и создавать приятный визуальный переход между экранами.

## Требования

### Функциональные требования
- Каждая страница Welcome Activity должна иметь свой уникальный цвет фона
- Цвета должны быть неяркими (приглушенными)
- Переходы между страницами должны выглядеть гармонично
- Цвета должны соответствовать общему стилю приложения

### Технические требования
- Использовать палитру Material Design
- Цвета должны быть читаемы как в светлой, так и в темной теме
- Обеспечить достаточную контрастность текста на фоне

## Пошаговая реализация

### Шаг 1: Определение цветовой палитры
1. Определить количество страниц в Welcome Activity
2. Подобрать набор неярких цветов, например:
   - Страница 1: Светло-голубой `#E3F2FD`
   - Страница 2: Светло-зеленый `#E8F5E9`
   - Страница 3: Светло-фиолетовый `#F3E5F5`
   - Страница 4: Светло-оранжевый `#FFF3E0`
   - И т.д.

### Шаг 2: Добавление цветов в ресурсы
1. Открыть `app_v2/src/main/res/values/colors.xml`
2. Добавить цвета для каждой страницы:
   ```xml
   <!-- Welcome screen backgrounds -->
   <color name="welcome_page_1_background">#E3F2FD</color>
   <color name="welcome_page_2_background">#E8F5E9</color>
   <color name="welcome_page_3_background">#F3E5F5</color>
   <color name="welcome_page_4_background">#FFF3E0</color>
   ```

3. Для темной темы создать `values-night/colors.xml`:
   ```xml
   <!-- Welcome screen backgrounds (dark theme) -->
   <color name="welcome_page_1_background">#1A237E</color>
   <color name="welcome_page_2_background">#1B5E20</color>
   <color name="welcome_page_3_background">#4A148C</color>
   <color name="welcome_page_4_background">#E65100</color>
   ```

### Шаг 3: Найти код Welcome Activity
1. Найти файл Welcome Activity (вероятно `WelcomeActivity.kt` или аналогичный)
2. Найти файлы макетов для страниц (например, `fragment_welcome_page.xml`)

### Шаг 4: Применение цветов к страницам
Вариант А: Если используются разные layout файлы для каждой страницы:
1. В каждом layout файле установить соответствующий цвет:
   ```xml
   <androidx.constraintlayout.widget.ConstraintLayout
       android:background="@color/welcome_page_1_background"
       ...>
   ```

Вариант Б: Если используется один layout с ViewPager/ViewPager2:
1. Создать массив цветов в `WelcomeActivity.kt`:
   ```kotlin
   private val pageBackgrounds = intArrayOf(
       R.color.welcome_page_1_background,
       R.color.welcome_page_2_background,
       R.color.welcome_page_3_background,
       R.color.welcome_page_4_background
   )
   ```

2. В адаптере ViewPager применять цвет для каждой позиции:
   ```kotlin
   override fun onBindViewHolder(holder: ViewHolder, position: Int) {
       holder.itemView.setBackgroundResource(pageBackgrounds[position])
       // остальной код
   }
   ```

### Шаг 5: Обеспечение плавных переходов
1. Добавить PageTransformer для плавной смены цветов (опционально):
   ```kotlin
   viewPager.setPageTransformer { page, position ->
       page.alpha = when {
           position < -1 -> 0f
           position <= 1 -> 1f
           else -> 0f
       }
   }
   ```

### Шаг 6: Тестирование
1. Запустить приложение в режиме первого запуска
2. Проверить все страницы Welcome Activity:
   - Каждая страница имеет свой цвет фона
   - Цвета неяркие и приятные для глаз
   - Текст читается хорошо на всех страницах
3. Проверить в светлой и темной теме
4. Проверить на разных размерах экранов

## Критерии приемки
- ✅ Каждая страница Welcome Activity имеет уникальный цвет фона
- ✅ Цвета неяркие (приглушенные)
- ✅ Текст хорошо читается на всех фонах
- ✅ Цвета работают как в светлой, так и в темной теме
- ✅ Переходы между страницами выглядят плавно и гармонично

## Зависимости
- Необходимо знать количество страниц в Welcome Activity
- Может потребоваться утверждение цветовой палитры

## Файлы для изменения
- `app_v2/src/main/res/values/colors.xml`
- `app_v2/src/main/res/values-night/colors.xml`
- Welcome Activity layout files
- Welcome Activity Kotlin файл

## Примечания
- Рассмотреть использование градиентов вместо однотонных цветов для более интересного визуального эффекта
- Убедиться, что цвета соответствуют брендингу приложения
