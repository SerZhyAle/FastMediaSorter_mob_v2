# Инструкция по публикации в GitHub

## Шаг 1: Создайте репозиторий на GitHub

1. Войдите в свой аккаунт GitHub
2. Нажмите "New repository" (зеленая кнопка)
3. Заполните:
   - **Repository name**: `FastMediaSorter_mob_v2`
   - **Description**: "Android app for fast media file sorting (v2 - Clean Architecture)"
   - **Visibility**: Public или Private (на ваш выбор)
   - **НЕ** устанавливайте галочки "Add README" и "Add .gitignore"
4. Нажмите "Create repository"

## Шаг 2: Подключите локальный репозиторий к GitHub

Скопируйте URL вашего репозитория (например: https://github.com/yourusername/FastMediaSorter_mob_v2.git)

Выполните команды:

```powershell
# Добавьте remote origin
git remote add origin https://github.com/ВАШЕ_ИМЯ/FastMediaSorter_mob_v2.git

# Проверьте, что remote добавлен
git remote -v

# Отправьте код в GitHub
git push -u origin main
```

## Шаг 3: Настройте репозиторий на GitHub

После успешной отправки, на странице репозитория:

### 3.1 Добавьте описание
- Кликните на шестеренку возле "About"
- Добавьте описание: "Android app for fast media file sorting with Clean Architecture"
- Добавьте темы (topics): `android`, `kotlin`, `clean-architecture`, `media-sorting`, `exoplayer`

### 3.2 Настройте .gitignore (уже создан)
Файл `.gitignore` уже включает:
- Gradle build files
- Android Studio files
- Local properties
- Keystore файлы (для безопасности)

## Шаг 4: Создайте первый Release (опционально)

1. Перейдите во вкладку "Releases"
2. Нажмите "Create a new release"
3. Заполните:
   - **Tag version**: `v2.0.0-alpha1`
   - **Release title**: "v2.0.0-alpha1 - Initial v2 Architecture"
   - **Description**:
     ```markdown
     ## 🎉 First Alpha Release of v2

     ### ✨ Features
     - Clean Architecture implementation
     - Java 21 runtime
     - Resource management
     - Media browsing and playback
     - File operations (copy, move, rename, delete)
     - Slideshow mode
     - Write permissions indicator

     ### 📦 Tech Stack
     - Kotlin 1.9.22
     - Hilt 2.50
     - Room 2.6.1
     - ExoPlayer (Media3) 1.2.1
     - Material Design 2

     ### 🔄 Migration from v1
     Complete rewrite with modern Android architecture patterns.
     ```
4. Нажмите "Publish release"

## Шаг 5: Настройте GitHub Actions (опционально)

Можно добавить CI/CD для автоматической сборки и тестирования:

Создайте файл `.github/workflows/android.yml`:

```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    - name: Build with Gradle
      run: ./gradlew build
```

## Текущее состояние репозитория

✅ **3 коммита:**
1. `c176292` - Initial project structure
2. `f27ccaa` - Java 21 upgrade and UI improvements
3. `9b9e3bc` - Comprehensive README

✅ **Файлы готовы к публикации:**
- Весь исходный код app_v2
- Документация (спецификации, TODO)
- README.md
- CHANGELOG_SESSION.md
- V1 референс

⚠️ **Файлы исключены из git (.gitignore):**
- `fastmediasorter.keystore` (НЕ публикуйте keystore!)
- `keystore.properties` (НЕ публикуйте пароли!)
- build/ директории
- .idea/ и .gradle/

## Безопасность

⚠️ **ВАЖНО: НЕ публикуйте:**
- `fastmediasorter.keystore`
- `keystore.properties`
- Любые пароли или секретные ключи

Проверьте `.gitignore` перед push:
```bash
git check-ignore -v fastmediasorter.keystore
git check-ignore -v keystore.properties
```

Если файлы уже в git, удалите их:
```bash
git rm --cached fastmediasorter.keystore
git rm --cached keystore.properties
git commit -m "chore: remove sensitive files from git"
```

## Дальнейшие шаги

После публикации:

1. **Создайте ветки для разработки:**
   ```bash
   git checkout -b develop
   git push -u origin develop
   ```

2. **Настройте GitHub Projects** для управления задачами из TODO_V2.md

3. **Включите GitHub Issues** для отслеживания багов

4. **Добавьте CONTRIBUTING.md** с правилами внесения изменений

5. **Настройте защиту веток** (main должна быть protected)

---

**Готово к публикации! 🚀**
