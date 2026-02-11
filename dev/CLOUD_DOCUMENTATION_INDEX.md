# Документация по облачным хранилищам

## Документы

### CLOUD_QUICK_FIX.md
**Для**: QA, техподдержка  
**Когда**: Быстрое решение типовых проблем за 5-10 минут

### CLOUD_INTEGRATION_TROUBLESHOOTING.md
**Для**: Разработчики  
**Когда**: Детальная диагностика и исправление проблем

### CLOUD_DIAGNOSTIC_SCRIPTS.md
**Для**: Разработчики, CI/CD  
**Когда**: Автоматизация проверок

---

## Навигация по проблемам

| Симптом | Документ | Раздел |
|---------|----------|--------|
| "Недоступно" при подключении облака | CLOUD_QUICK_FIX.md | Соответствующий раздел |
| SHA-1 ошибка Firebase | CLOUD_QUICK_FIX.md | Google Drive |
| google-services.json не найден | CLOUD_QUICK_FIX.md | Google Drive |
| Все облака не работают | CLOUD_INTEGRATION_TROUBLESHOOTING.md | Диагностика |

---

## Процесс диагностики

### Первый раз
1. CLOUD_QUICK_FIX.md (5 мин)
2. Если не помогло → CLOUD_INTEGRATION_TROUBLESHOOTING.md
3. Если всё ещё не работает → скрипты из CLOUD_DIAGNOSTIC_SCRIPTS.md

### Повторная проблема
1. CLOUD_QUICK_FIX.md → найти проблему в таблице
2. Следовать рекомендации

---

## Поддерживаемые облака

| Провайдер | Статус | Требования |
|-----------|--------|------------|
| Google Drive | ✓ | google-services.json |
| OneDrive | ✓ | msal_config.json |
| Dropbox | ✓ | App Key в gradle |

---

## Для разных ролей

### Разработчик
- CLOUD_INTEGRATION_TROUBLESHOOTING.md → Технический справочник
- CLOUD_DIAGNOSTIC_SCRIPTS.md → Все скрипты
- Код: `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/`

### QA / Тестировщик
- CLOUD_QUICK_FIX.md → "Как проверить что всё работает"
- CLOUD_QUICK_FIX.md → Таблица ошибок

### DevOps / CI-CD
```yaml
steps:
  - name: Cloud Check
    run: .\scripts\cloud-diagnostic.ps1 -flavor standard
```

### Техподдержка
1. Узнать какое облако не работает
2. CLOUD_QUICK_FIX.md → соответствующий раздел
3. Дать инструкции

---

## Получить помощь

1. Запустить:
```bash
.\scripts\cloud-diagnostic.ps1 -flavor standard
adb logcat > cloud_debug.log
```

2. Создать Issue с:
   - Результатом скриптов
   - Логами
   - Версией (debug/release)
   - Flavor (standard/lite/photos)
   - Провайдером (Google/OneDrive/Dropbox)
