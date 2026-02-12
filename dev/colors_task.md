Вот что происходит:

Формы используют ПРАВИЛЬНЫЙ подход:

?attr/colorSurface - цвет поверхности из темы (8x)
?attr/selectableItemBackgroundBorderless - ripple эффекты (8x)
?attr/colorControlNormal - цвет контролов из темы (7x)
@drawable/... - селекторы состояний
НО есть проблемные места - @android:color (системные цвета):

12 файлов используют системные цвета, которые НЕ адаптируются к темам:

activity_browse.xml: white, holo_red_dark (5x)
activity_main.xml: darker_gray, holo_red_dark, holo_orange_dark (5x)
item_resource.xml, item_destination_button.xml: white (по 1x)
4 файла: holo_red_dark для ошибок
dialog_color_picker.xml: holo_green_light
item_color.xml: holo_blue_light


Player уже исправлен (главная проблема решена)
Большинство форм правильные - используют ?attr/...
Проблемных файлов мало: ~20 использований в 12 файлах
@android:color/transparent - безопасный, можно не трогать
white/black - можно заменить на наши @color/white/@color/black
holo_* - нужно создать semantic цвета (error_color, warning_color, success_color)
Лучше сделать отдельной задачей:

Создать semantic статусные цвета
Заменить holo_red_dark → @color/error_color
Заменить holo_orange_dark → @color/warning_color
Заменить holo_green_light → @color/success_color