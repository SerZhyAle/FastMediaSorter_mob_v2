# Разработка и релиз FastMediaSorter v2 - практическое руководство

Документ описывает три рабочих сценария: ежедневная разработка с отладочными сборками, экстренный фикс-релиз и плановый релиз по завершению цикла разработки.

---

## Модель окружения

Два рабочих дерева git существуют одновременно:

| Директория | Ветка | Назначение |
|------------|-------|------------|
| `P:/ANDROID/FastMediaSorter_mob_v2` | `DEBUG-v00N` | Разработка - здесь пишется весь код |
| `P:/ANDROID/FastMediaSorter_release` | `main` | Сборки релизов - сюда никогда не переключаться вручную |

Все команды `.\a …` запускаются из `FastMediaSorter_mob_v2`. Релизные команды (`r`, `vr`, `nl`) автоматически переключаются на `FastMediaSorter_release`, собирают там, копируют артефакты обратно.

Перед началом любой сессии:
```powershell
git branch --show-current   # убедиться что ты на DEBUG-v00N, а не на main
```

---

## Сценарий 1 - Ежедневная разработка и тестирование debug-версий

### Когда применяется

Ты работаешь над кодом, хочешь собрать debug APK, установить на устройство и проверить поведение.

### Шаги

**1. Пишешь код в `FastMediaSorter_mob_v2` на ветке `DEBUG-v00N`.**

**2. Собираешь debug APK:**
```powershell
.\a d          # быстрая сборка + zip в DOWNLOADS
# или
.\a db         # то же самое без zip
# или
.\a dc         # clean + debug (если нужна чистая сборка)
```

После успешной сборки APK лежит в `DOWNLOADS/` и в `app_v2/build/outputs/apk/standard/debug/FastMediaSorter_debug.apk`.

**3. Устанавливаешь на устройство.** `.\a d` также запускает фоновый auto-deploy через ADB, если устройство подключено.

**4. Тестируешь на устройстве.** Если нужно смотреть logcat - фильтруй по тегу приложения.

**5. Фиксируешь изменения:**
```powershell
git add путь/к/файлу.kt
git commit -m "feat: описание"
git push origin DEBUG-v001
```

**6. Логируешь изменение** (обязательно после каждого коммита с кодом):
```powershell
.\scripts\add_to_dev_log.ps1 "путь/к/файлу.kt" "ClassName" "Описание изменения"
```

### Итог

APK в DOWNLOADS, изменения зафиксированы на DEBUG-ветке. Никаких операций с `main`.

---

## Сценарий 2 - Фикс-релиз (исправление уже опубликованной версии)

### Когда применяется

Найден баг в опубликованной версии. Нужно выпустить исправление как можно скорее. В релизе - **только fix**, никакого нового функционала или UI.

Фикс-релиз - единственная законная причина делать коммит напрямую в `main` вне цикла DEBUG → main.

### Предусловия

- Исправление уже закоммичено на `DEBUG-v00N`.
- Спека для этого фикса есть в каталоге (статус `Implemented` или `Verified`).
- Рабочее дерево чистое.

### Шаги

**Один вызов:**
```
/skill-fix-release S0123
```

Скилл выполняет полный пайплайн автономно:

1. Pre-flight: ветка, чистота, наличие worktree.
2. Резолвит спеку `S0123` через каталог - получает название, путь к файлу.
3. Ищет коммиты для этого фикса в трёх фазах:
   - Фаза A: `git log origin/main..HEAD --grep="S0123"` - коммиты с ID в сообщении.
   - Фаза B: коммиты, затронувшие файл спеки.
   - Фаза C (fallback): парсит файл спеки на предмет упомянутых путей, делает `git diff origin/main..HEAD -- <path>` для каждого.
4. Показывает список найденных коммитов и файлов (без паузы).
5. Cherry-pick найденных коммитов в `main` (в release worktree) - **только они**, остальной код DEBUG не трогается.
6. Обновляет `docs/WHATS_NEW.md` - новый блок «Fix Release» становится текущим, старый «Current release» становится «Previous Release».
7. Обновляет `README.md` (и RU/UK зеркала) - версия в заголовке «What's New».
8. Коммит документов прямо в `main`, тег `release/vX.X.XXXX.XXX`, push.
9. Запускает `.\a r` - AAB для Google Play (gitignored-файлы копируются автоматом).
10. Rebase `DEBUG-v00N` на обновлённый `main`, push с `--force-with-lease`.

### Что делать при конфликте cherry-pick

Скилл остановится после шага 5 и покажет конфликтные файлы:
```powershell
cd P:/ANDROID/FastMediaSorter_release
# редактируешь конфликты
git cherry-pick --continue
# дальше шаги 6–10 вручную по инструкции в .claude/commands/skill-fix-release.md
```

### Итог

| Что | Результат |
|-----|-----------|
| `main` | содержит только коммиты этого фикса |
| Тег | `release/vX.X.XXXX.XXX` с меткой Fix Release |
| Документы | `WHATS_NEW.md`, `README.md` обновлены прямо в `main` |
| `DEBUG-v00N` | перебазирован, фикс не задублируется при следующем merge |
| Артефакт | AAB в `DOWNLOADS/` - готов к публикации |

Публикуешь артефакт из `DOWNLOADS/` вручную. Для VR дополнительно: `.\a vr`.

### Проверочный список

- [ ] Исправление закоммичено на DEBUG с понятным сообщением (желательно содержит `S0123`)
- [ ] Спека `S0123` в каталоге в статусе `Implemented` или `Verified`
- [ ] В релизе только fix - никакого нового функционала
- [ ] DEBUG-ветки перебазированы (скилл делает это автоматически)

---

## Сценарий 3 - Плановый релиз текущего DEBUG-бранча (`/skill-release`)

### Когда применяется

`DEBUG-v001` стабилен: ключевые спеки в статусе Implemented / Verified, билд не падает, ручное тестирование пройдено. Ты готов признать бранч готовым и выпустить полноценный релиз.

### Предусловия

- Ты на `DEBUG-v00N` (не на `main`).
- Рабочее дерево чистое (`git status` показывает nothing to commit).
- `P:/ANDROID/FastMediaSorter_release` существует (`git worktree list` должен показать оба дерева).

### Шаги

**Один вызов:**
```
/skill-release
```

Скилл выполняет полный пайплайн автономно:

1. Pre-flight: проверяет ветку, чистоту дерева, наличие worktree.
2. Генерирует версию по формату `Y.YM.MDDH.Hmm`.
3. Анализирует `git log <prev-tag>..HEAD` - разбивает коммиты на «What's New» (`feat:`) и «What's Fixed» (`fix:`).
4. Обновляет `docs/WHATS_NEW.md` - старый «Current release» становится «Previous Release», сверху вставляется новый блок.
5. Обновляет `README.md` (и зеркала `README_RU.md`, `README_UK.md`) - раздел «What's New» с новой версией.
6. Коммитит документы на DEBUG-ветке, пушит.
7. Мёрджит DEBUG → main в release worktree (`--no-ff`), ставит тег, пушит `main` и тег.
8. Переходит на следующий DEBUG-бранч:
   - Если `DEBUG-v002` уже существует (был «future»-бранчем) → `git checkout DEBUG-v002`.
   - Если нет → создаёт `DEBUG-v002` от свежего `main`, пушит с трекингом.
9. Запускает `.\a r` - релизный билд AAB в worktree, артефакты в `DOWNLOADS/`.

### Что делать при конфликте мёрджа

Если шаг 7 упал с конфликтом, скилл остановится и покажет список конфликтных файлов. Дальше вручную:

```powershell
cd P:/ANDROID/FastMediaSorter_release
# редактируешь конфликтные файлы
git add .
git merge --continue
# продолжаешь с шага 9 вручную:
git tag release/v$NEW_VERSION
git push origin main
git push origin release/v$NEW_VERSION
```

### Итог после `/skill-release`

| Что | Результат |
|-----|-----------|
| `main` | содержит всё из DEBUG-v00N |
| Тег | `release/v$NEW_VERSION` в истории git |
| Документы | `WHATS_NEW.md`, `README.md` обновлены и закоммичены |
| Dev-директория | переключена на `DEBUG-v002` (следующий цикл) |
| Артефакт | AAB в `DOWNLOADS/` - готов к публикации в Google Play |

Публикуешь AAB из `DOWNLOADS/` вручную в Google Play Console. Для VR после этого запускаешь `.\a vr` отдельно.

---

## Быстрый справочник команд

| Задача | Команда |
|--------|---------|
| Debug APK | `.\a d` |
| Debug APK без zip | `.\a db` |
| Clean + debug APK | `.\a dc` |
| AAB для Google Play (release) | `.\a r` |
| APK для VR/Meta (release) | `.\a vr` |
| APK noLegal (release) | `.\a nl` |
| Плановый релиз (всё автоматом) | `/skill-release` |
| Текущая ветка | `git branch --show-current` |
| Все рабочие деревья | `git worktree list` |
| Последние теги релизов | `git tag --list "release/*" --sort=-version:refname` |

---

## Связанные документы

- [`.claude/commands/git.md`](../.claude/commands/git.md) - полный git-справочник: ветки, worktree, fix-release, push
- [`.claude/commands/skill-release.md`](../.claude/commands/skill-release.md) - детальный алгоритм `/skill-release`
- [`scripts/release-worktree-sync.txt`](../scripts/release-worktree-sync.txt) - список gitignored-файлов, синхронизируемых в worktree перед каждым release-билдом
- [`docs/WHATS_NEW.md`](WHATS_NEW.md) - история релизов
