# Database Migration Guide

Этот документ описывает процесс управления миграциями базы данных для FastMediaSorter.

## Текущая версия БД: 3

## История миграций

### Version 2 → 3 (MIGRATION_2_3)
**Дата:** Добавлено в рамках поддержки локального хранилища
**Изменения:**
- `ALTER TABLE connection_configs ADD COLUMN type TEXT NOT NULL DEFAULT 'SMB'`
- `ALTER TABLE connection_configs ADD COLUMN localUri TEXT`
- `ALTER TABLE connection_configs ADD COLUMN localDisplayName TEXT`

**Цель:** Добавить поддержку локальных папок наряду с SMB подключениями.

## Добавление новой миграции

### Шаг 1: Обновить версию БД
```kotlin
@Database(entities = [ConnectionConfig::class], version = 4, exportSchema = true)
```

### Шаг 2: Создать миграцию
```kotlin
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Пример: Добавить новую колонку
        database.execSQL("ALTER TABLE connection_configs ADD COLUMN newColumn TEXT")
        
        // Пример: Создать новую таблицу
        database.execSQL("""
            CREATE TABLE new_table (
                id INTEGER PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)
        
        // Пример: Обновить существующие данные
        database.execSQL("UPDATE connection_configs SET newColumn = 'default_value' WHERE newColumn IS NULL")
    }
}
```

### Шаг 3: Добавить в getAllMigrations()
```kotlin
private fun getAllMigrations(): Array<Migration> {
    return arrayOf(
        MIGRATION_2_3,
        MIGRATION_3_4  // Добавить здесь
    )
}
```

### Шаг 4: Обновить Entity (если нужно)
Если добавляете новые поля в `ConnectionConfig`, обновите:
- Data class `ConnectionConfig`
- DAO методы (если нужно)

### Шаг 5: Тестирование
1. Установить версию с предыдущей схемой БД
2. Создать тестовые данные
3. Обновить до новой версии
4. Проверить, что данные сохранились и миграция прошла успешно

## Важные правила

### ✅ МОЖНО:
- Добавлять новые колонки с DEFAULT значениями
- Создавать новые таблицы
- Создавать новые индексы
- Обновлять данные (UPDATE)

### ❌ НЕЛЬЗЯ без осторожности:
- Удалять колонки (может сломать старые версии)
- Переименовывать колонки (нужно CREATE + COPY + DROP)
- Изменять типы данных (может потерять данные)
- Удалять таблицы с пользовательскими данными

### Безопасное удаление колонки:
```sql
-- Создать новую таблицу без ненужной колонки
CREATE TABLE connection_configs_new (
    id INTEGER PRIMARY KEY NOT NULL,
    name TEXT NOT NULL
    -- без старой колонки
);

-- Скопировать данные
INSERT INTO connection_configs_new (id, name)
SELECT id, name FROM connection_configs;

-- Удалить старую таблицу
DROP TABLE connection_configs;

-- Переименовать новую
ALTER TABLE connection_configs_new RENAME TO connection_configs;
```

## Схема экспорта

С `exportSchema = true` схемы БД сохраняются в `app/schemas/`. Это помогает:
- Отслеживать изменения схемы
- Валидировать миграции
- Создавать автотесты

## Обработка ошибок

Без `fallbackToDestructiveMigration()`:
- Приложение крашится с ясным сообщением об отсутствии миграции
- Заставляет разработчиков создать правильную миграцию
- **Пользовательские данные сохраняются**

## Экстренное восстановление

Если миграция критически сломана и нужно экстренно обновить:

1. **Временно** добавить `.fallbackToDestructiveMigration()`
2. Выпустить hotfix
3. **Немедленно** убрать fallback и создать правильную миграцию
4. **Предупредить пользователей** о потере данных

## Версионирование

- **Major version** (1→2): Критические изменения схемы
- **Minor version** (2→3): Добавление функций
- **Patch version** (3→4): Исправления без изменения схемы

Придерживайтесь семантического версионирования для БД.