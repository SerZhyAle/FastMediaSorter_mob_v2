# 01 - Архитектура и масштаб экрана настроек

**Ticket:** S0474
**§6 item:** 1
**Метод:** чтение исходников `ui/settings/**`, layouts `res/xml`/`res/layout`, catalog query.

## Тип реализации

Экран настроек - кастомная реализация на `ViewPager2`, без `androidx.preference.PreferenceFragmentCompat` и без preferences-XML. Каждый таб - обычный `Fragment` с вертикальным `LinearLayout` внутри `NestedScrollView`.

- Хост: `SettingsActivity` (595 LOC) - `BaseActivity<ActivitySettingsBinding>`, держит `ViewPager2` + `TabLayout` + оверлей глобального поиска по настройкам.
- Адаптер: `SettingsPagerAdapter` (47 LOC) - `FragmentStateAdapter`, 4 статических таба + дополнительные через `Set<SettingsTabExtension>` (VR-флейвор).

## Табы и размеры

- General - `GeneralSettingsFragment` (290 LOC) + 13 helper-классов (вынесенная логика, крупнейший `GeneralSettingsViewSetupHelper` 623 LOC).
- Media - `MediaSettingsFragment` (196 LOC, контейнер) → программно attach-ит 5-6 дочерних фрагментов: Images (175), Video (197), Audio (392), Documents (133), OtherMedia (449), опц. VrSettingsBlock.
- Playback - `PlaybackSettingsFragment` (439 LOC).
- Operations - `OperationsSettingsFragment` (**1165 LOC**, вплотную к лимиту 1500).

## Масштаб

- Layout XML суммарно ~2691 строк; крупнейший `fragment_settings_destinations.xml` (776), затем `fragment_settings_general.xml` (536).
- В системе поиска `SettingsSearchLayoutCatalog` зарегистрировано 9 layouts как источников настроек.
- Оценка по числу listeners/binding: ~100-130 отдельных управляющих элементов настроек на всех табах (Operations один даёт ~40-50).

## Существующая декомпозиция

- General-таб уже разнесён на 13 helper-классов (хороший прецедент паттерна).
- Media-таб двухуровневый: контейнер + дочерние фрагменты по типам медиа.
- Часть разделов уже вынесена в отдельные экраны: Permissions (`PermissionsManagementFragment`, полноэкранная замена `android.R.id.content`), Auth Sessions (`AuthSessionsActivity`, отдельная Activity), Downloadable Extensions (`ExtensionsManagerFragment`, полноэкранный оверлей).

## Вывод

«Огромное число настроек в одном Activity» - подтверждается: единый хост-Activity с ~100-130 настройками. Но это не один монолитный файл - есть разбиение на табы/фрагменты/helpers. Реальные болевые точки - не «всё в одном классе», а синхронная стоимость загрузки при открытии и один переросший фрагмент (Operations). Детали - в артефактах 02-03.
