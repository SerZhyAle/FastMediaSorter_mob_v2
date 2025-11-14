# FastMediaSorter v2 🚀# FastMediaSorter v2 🚀



## About the Project## О проекте



FastMediaSorter v2 is a powerful Android application for quick and convenient sorting of media files (images, videos, GIFs, audio). It is designed as a single center for managing files from various sources: local device folders, network drives (SMB, SFTP), and cloud storage.FastMediaSorter v2 — это мощное Android-приложение для быстрой и удобной сортировки медиафайлов (изображений, видео, GIF, аудио). Оно спроектировано как единый центр для управления файлами из различных источников: локальные папки устройства, сетевые диски (SMB, SFTP) и облачные хранилища.



The key idea of v2 is to combine viewing, playback, and organization of files in one intuitive interface, eliminating the shortcomings and limitations of the previous version.Ключевая идея v2 — объединить просмотр, воспроизведение и организацию файлов в одном интуитивно понятном интерфейсе, устраняя недостатки и ограничения предыдущей версии.



## Key Features## Ключевые возможности



*   🗂️ **Unified Interface:** View and manage files from all sources in one window.*   🗂️ **Единый интерфейс:** Просмотр и управление файлами из всех источников в одном окне.

*   ⚡ **Fast Sorting:** Copy or move files to pre-configured destination folders with one click.*   ⚡ **Быстрая сортировка:** Копируйте или перемещайте файлы в заранее настроенные папки-получатели одним нажатием.

*   🖥️ **Network and Cloud Support:** Work with files on your network drives (SMB), SFTP servers, and in cloud storage (Google Drive, Dropbox, etc.).*   🖥️ **Поддержка сети и облака:** Работайте с файлами на ваших сетевых дисках (SMB), SFTP-серверах и в облачных хранилищах (Google Drive, Dropbox и др.).

*   🖼️ **Flexible Viewing:** Display files as a customizable grid or detailed list.*   🖼️ **Гибкий просмотр:** Отображение файлов в виде настраиваемой сетки или детального списка.

*   ▶️ **Built-in Player:** Playback of video and audio, viewing images and GIFs without leaving the app. Supports slideshow.*   ▶️ **Встроенный плеер:** Воспроизведение видео и аудио, просмотр изображений и GIF без выхода из приложения. Поддерживает слайд-шоу.

*   🔍 **Sorting and Filtering:** Order files by name, date, size, and duration. Apply filters for quick search.*   🔍 **Сортировка и фильтрация:** Упорядочивайте файлы по имени, дате, размеру и длительности. Применяйте фильтры для быстрого поиска.

*   ↩️ **Undo Operations:** Ability to undo the last action (copy, move, delete).*   ↩️ **Отмена операций:** Возможность отменить последнее действие (копирование, перемещение, удаление).

*   🎨 **Modern Interface:** Support for light and dark themes, intuitive controls.*   🎨 **Современный интерфейс:** Поддержка светлой и темной тем, интуитивно понятное управление.



## Build Instructions## Инструкция по сборке



### Requirements### Требования

*   Android Studio Hedgehog (2023.1.1) or newer*   Android Studio Hedgehog (2023.1.1) или новее

*   JDK 17+*   JDK 17+

*   Android SDK 34*   Android SDK 34

*   Minimum Android version: 9.0 (API 28)*   Минимальная версия Android: 9.0 (API 28)



### Build### Сборка

1.  Clone the repository:1.  Клонируйте репозиторий:

    ```bash    ```bash

    git clone https://github.com/yourusername/FastMediaSorter_mob_v2.git    git clone https://github.com/yourusername/FastMediaSorter_mob_v2.git

    cd FastMediaSorter_mob_v2    cd FastMediaSorter_mob_v2

    ```    ```

2.  Open the project in Android Studio.2.  Откройте проект в Android Studio.

3.  Wait for Gradle synchronization to complete.3.  Дождитесь окончания синхронизации Gradle.

4.  Run the app on an emulator or physical device.4.  Запустите приложение на эмуляторе или физическом устройстве.



## Quick Usage Guide## Краткое руководство по использованию



1.  **Adding a Folder (Resource):**1.  **Добавление папки (ресурса):**

    *   On the main screen, press the button with the "Plus" (+) icon to add a new resource.    *   На главном экране нажмите кнопку с иконкой "Плюс" (+), чтобы добавить новый ресурс.

    *   Select the resource type (e.g., "Local Folder").    *   Выберите тип ресурса (например, "Локальная папка").

    *   Use scanning or add the folder manually. After adding, it will appear in the list on the main screen.    *   Используйте сканирование или добавьте папку вручную. После добавления она появится в списке на главном экране.



2.  **Viewing Files:**2.  **Просмотр файлов:**

    *   Double-tap (or long press) on the added resource in the list.    *   Дважды коснитесь (или сделайте долгое нажатие) на добавленный ресурс в списке.

    *   The browse screen will open, where you will see all media files from this folder as a list or grid.    *   Откроется экран просмотра (`Browse Screen`), где вы увидите все медиафайлы из этой папки в виде списка или сетки.

    *   Use the buttons on the top panel for sorting, filtering, or switching view.    *   Используйте кнопки на верхней панели для сортировки, фильтрации или переключения вида.



3.  **Playback and Sorting:**3.  **Воспроизведение и сортировка:**

    *   Tap on any file to open it in the full-screen player.    *   Нажмите на любой файл, чтобы открыть его в полноэкранном плеере (`Player Screen`).

    *   Use swipes left/right or touch zones for navigation between files.    *   Используйте свайпы влево/вправо или сенсорные зоны для навигации между файлами.

    *   For operations (copy, move), use the corresponding touch zones or buttons on the control panel.    *   Для выполнения операций (копирование, перемещение) используйте соответствующие сенсорные зоны или кнопки на панели управления.



4.  **Configuring Destination Folders (Destinations):**4.  **Настройка папок-получателей (Destinations):**

    *   In settings, on the "Destinations" tab, you can specify up to 10 folders that will be used for quick sorting.    *   В настройках, на вкладке "Destinations", вы можете указать до 10 папок, которые будут использоваться для быстрой сортировки.

    *   After that, buttons for quick copying or moving files to these folders will appear on the player screen.    *   После этого на экране плеера появятся кнопки для быстрого копирования или перемещения файлов в эти папки.



## Technology Stack## Технологический стек



-   **Language**: Kotlin-   **Язык**: Kotlin

-   **Architecture**: Clean Architecture, MVVM-   **Архитектура**: Clean Architecture, MVVM

-   **UI**: Android View System (XML), Material Design-   **UI**: Android View System (XML), Material Design

-   **Asynchrony**: Kotlin Coroutines & Flow-   **Асинхронность**: Kotlin Coroutines & Flow

-   **DI**: Hilt-   **DI**: Hilt

-   **Database**: Room-   **База данных**: Room

-   **Navigation**: AndroidX Navigation Component-   **Навигация**: AndroidX Navigation Component

-   **Media**: ExoPlayer (Media3)-   **Медиа**: ExoPlayer (Media3)

-   **Network**: SMBJ (for SMB), SSHJ (for SFTP)-   **Сеть**: SMBJ (для SMB), SSHJ (для SFTP)



## Project Status## Статус проекта



The project is in active development. Core functionality for working with local files is implemented. Work is underway on integrating network protocols and expanding user settings.Проект находится в активной разработке. Основная функциональность для работы с локальными файлами реализована. Ведется работа над интеграцией сетевых протоколов и расширением пользовательских настроек.



------

*This file was generated based on project documentation.**Этот файл был сгенерирован на основе проектной документации.*