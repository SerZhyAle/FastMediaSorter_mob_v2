# Дизайн решения: Резервная доставка перевода (ML Kit) для store/debug сборок

## 1. Изменение структуры Source Set
* Вместо динамического подключения исходных кодов перевода через DFM, исходники `src/translationMlKit/java` будут монтироваться в хост-модуль `app_v2` для всех флейворов (включая `standard` и `legacy`).
* Это устраняет необходимость рефлексивного фабричного создания `DynamicTextTranslationFacadeFactory` и предотвращает ошибки `ClassNotFoundException`.
* Модуль `:translate_feature` DFM становится безкодовым (code-less), содержащим только `.so` библиотеки для Play Store сборок.

### gradle.properties / app_v2/build.gradle.kts
* Переносим зависимости ML Kit из DFM-модуля напрямую в `app_v2/build.gradle.kts` как flavor-зависимости для `standard` и `legacy`:
  ```kotlin
  "standardImplementation"("com.google.mlkit:translate:17.0.3")
  "standardImplementation"("com.google.mlkit:language-id:17.0.6")
  "legacyImplementation"("com.google.mlkit:translate:17.0.3")
  "legacyImplementation"("com.google.mlkit:language-id:17.0.6")
  ```
* Исключаем упаковку `.so` файлов в базовый APK для `standard` и `legacy` в `jniLibs.excludes`:
  ```kotlin
  excludes += "**/libtranslate_jni.so"
  excludes += "**/liblanguage_id_l2c_jni.so"
  ```

## 2. Добавление дескриптора в каталог
В [DeliverableDescriptorCatalog.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt) добавляется поддержка `DeliverableSet.TRANSLATION` со всеми SHA-256 хешами и размерами под 4 архитектуры ABI:
* `libtranslate_jni.so`
* `liblanguage_id_l2c_jni.so`

Имя удалённого ассета формируется по схеме: `<abi>-libtranslate_jni-v1.so` и `<abi>-liblanguage_id_l2c_jni-v1.so`.

## 3. Резервный флоу скачивания (RealDeliverableSetDownloader)
В методе `download(set: DeliverableSet)` при запросе `DeliverableSet.TRANSLATION` алгоритм действует следующим образом:
1. Пытается вызвать `downloadDfm("translate_feature")` (первичный канал Google Play).
2. Если возвращается ошибка `DownloadProgress.Failed` (например, DFM недоступен из-за sideload-установки с кодом `-15` `APP_NOT_OWNED`), флоу перехватывает ошибку, логирует предупреждение и выполняет переключение на `downloadFromSources(set)` (резервный HTTP/GitHub канал).

## 4. Монтирование библиотек
Класс `DeliveredNativeLibraryLoader` расширяется для поддержки ручного подключения `DeliverableSet.TRANSLATION`:
* Сплайсит `filesDir/delivery/TRANSLATION/` в пути поиска `BaseDexClassLoader`.
* Загружает `liblanguage_id_l2c_jni.so` и `libtranslate_jni.so` с помощью `System.load`.
* Благодаря этому, последующие вызовы `System.loadLibrary` от Google ML Kit SDK успешно разрешают зависимости.
