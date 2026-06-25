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
# дальше шаги 6-10 вручную по инструкции в .claude/commands/skill-fix-release.md
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
- `P:/ANDROID/FastMediaSorter_release` существует (`git worktree list` должен показать оба дерева).
- Standard production readiness gate пройден: `docs/RELEASE_READINESS_STANDARD.md` (вердикт `scripts/release/standard-release-gate.ps1` -> PASS или WAIVED).

Грязное рабочее дерево **не** блокер: пайплайн сам коммитит и пушит WIP через `.\a c` (`commit-push.ps1`) перед мёрджем. Жёсткие блокеры - только: запуск с `main` вместо DEBUG-ветки, отсутствие release-worktree, провал commit/push, конфликт мёрджа.

### Запуск

```
/skill-release                 # только standard (Google Play AAB + канонический GitHub-asset)
/skill-release vr noLegal      # standard + перечисленные редакции на GitHub
/skill-release all             # весь спектр: standard, vr, lite, photos, legacy, noLegal, wear
```

Без аргумента собирается и публикуется только `standard`. Аргументы расширяют **только** спектр GitHub-релиза; Google Play AAB (standard) собирается всегда, независимо от аргументов.

### Что делает скилл автономно

1. Pre-flight: ветка, наличие worktree. Грязный tree -> авто-коммит + push через `.\a c`.
2. Версия и versionCode из одного timestamp по формату `Y.YM.MDDH.Hmm` / `YYMMDDHHm`. Оба значения **ПИНятся** в билд (`-VersionName -VersionCode`), чтобы тег = заголовок `WHATS_NEW` = AAB = APK без расхождений.
3. Анализ изменений по **диффу инвентаря возможностей**, а не по git-логу: `scripts/all_features/diff.ps1 -From <prev-tag>`. Коммиты неконвенциональны (голые числа/таймстемпы), `PLAN/` в gitignore, поэтому `feat:`/`fix:` из лога не вытащить. Записи `[ADD]`/`[CHANGE]` раскладываются на «Что нового» и «Что исправлено».
4. Обновляет `docs/WHATS_NEW.md` + **обязательно** зеркала `WHATS_NEW_RU.md` и `WHATS_NEW_UK.md`. Из них генерятся локализованные fastlane-changelog'и; если зеркала не обновить - в Play/IzzyOnDroid уедут заметки прошлого релиза.
5. Обновляет `README.md` (+ `README_RU.md`/`README_UK.md`, если там есть блок версии).
6. Dev-log + коммит документов на DEBUG-ветке, push.
7. Мёрджит DEBUG -> main в release worktree (`--no-ff`), ставит тег `release/v<version>`, пушит `main` и тег.
8. Переходит на следующий DEBUG-бранч: берёт существующий `DEBUG-v0NN+1` (был «future»-бранчем) или создаёт его от свежего `main` и пушит с трекингом.
9. Сборка: `.\a r -VersionName <v> -VersionCode <c>` - standard AAB (Google Play) + APK + зеркало в Google Drive (запароленный ZIP) + fastlane-changelog'и. Доп. редакции из `$FLAVORS` собираются `build-release-spectrum.ps1 -ReuseVersion` на той же версии (без расхождений с Play-AAB).
10. Публикация по каналам (см. ниже): GitHub Release (`publish-github-release.ps1`, сперва `-DryRun`), Google Play (`publish-play-release.ps1`, трек `production`, статус `completed`). Затем коммит сгенерированных fastlane-changelog'ов в `main` и сброс version-stamp в `build.gradle.kts` (иначе следующий релиз упрётся в грязный tree при мёрдже).
11. Showcase: из диффа `ALL_FEATURES` яркие возможности добавляются в `docs/FEATURES.md` (+ RU/UK в lockstep), коммит на новом DEBUG-бранче (на сайт попадёт при следующем мёрдже в `main`).

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
| `main` | содержит всё из DEBUG-v00N + fastlane-changelog'и релиза |
| Тег | `release/v$NEW_VERSION` запушен |
| Документы | `WHATS_NEW.md` (+ RU/UK), `README.md` обновлены до мёрджа; `FEATURES.md` (+ RU/UK) обновлён на новом DEBUG |
| Dev-директория | переключена на следующий `DEBUG-v0NN+1` |
| Google Play | standard AAB опубликован на `production` (статус `completed`) - автоматически |
| GitHub Release | assets `$FLAVORS` опубликованы под тегом `v$NEW_VERSION` - автоматически |
| Google Drive | запароленный ZIP standard синхронизирован внутри `.\a r` |

Публикация в Google Play и GitHub - **автоматическая** внутри пайплайна. Ручная публикация AAB больше не нужна.

### Каналы дистрибуции

Полный релиз доходит до пяти каналов:

1. **Google Play** (standard AAB) - автоматически (`publish-play-release.ps1`). Разовый гейт, блокирующий commit: декларация **Foreground service permissions** в Play Console -> App content. При появлении нового типа `FOREGROUND_SERVICE_*` (например, при добавлении записи через микрофон или захвата экрана) AAB заливается, но commit возвращает HTTP 403, пока декларация не сохранена владельцем. Это **не** жёсткий блокер пайплайна - фиксируется в отчёте как `[PLAY FGS]`, дальше владелец дозаявляет в Console и добавляет бандл из библиотеки.
2. **GitHub Release / Store** (`$FLAVORS`) - автоматически (`build-release-spectrum.ps1` + `publish-github-release.ps1`). Кнопки загрузки на сайте и `docs/DOWNLOADS_*` тянут assets через GitHub API. Редакция, не собранная в этом релизе, остаётся на прошлом asset'е - расширяй `$FLAVORS`, когда нужно обновить не-standard.
3. **Google Drive** - автоматически внутри `.\a r`: standard AAB+APK + запароленный ZIP (пароль `1`) в синхронизируемую папку.
4. **4pda** (форум, RU) - **вручную**. Накапливай «Что нового»/«Что исправлено» с момента ПОСЛЕДНЕГО поста на 4pda (не с прошлого релиза - на 4pda постят реже), три спойлера: что нового, что исправлено, noLegal. Вложения: `FastMediaSorter_standard_release.apk` + свежий `FastMediaSorter_nolegal_debug.apk` (`.\a nd`). noLegal-пункты - из `docs/FEATURES_noLegal*` / `docs/ALL_FEATURES_noLegal.jsonl`, никогда из публичных файлов.
5. **IzzyOnDroid** (standard APK) - разовый RFP (только владелец, нужен аккаунт Codeberg). После принятия IzzyOnDroid сам тянет standard APK из каждого GitHub-релиза - отдельных действий на релиз нет.

> Важно про скрин-захват и краевые жесты: в поставляемом standard Play-билде они **отсутствуют** (committed `gradle.properties` задаёт `fms.screenCapture=off`, гейт S0630). В заметках/showcase/посте на 4pda эти возможности относи к noLegal-сборке, даже если в `ALL_FEATURES.jsonl` у них флавор `standard` (инвентарь отражает возможность при флаге=on, а не поставляемую сборку).

---

## Быстрый справочник команд

| Задача | Команда |
|--------|---------|
| Debug APK | `.\a d` |
| Debug APK без zip | `.\a db` |
| Clean + debug APK | `.\a cd` (или `.\a dc`) |
| Коммит + push WIP | `.\a c "сообщение"` |
| AAB для Google Play (release) | `.\a r` |
| APK для VR/Meta (release) | `.\a vr` |
| APK noLegal release / debug | `.\a nl` / `.\a nd` |
| Плановый релиз (только standard) | `/skill-release` |
| Плановый релиз (весь спектр) | `/skill-release all` |
| Дифф возможностей с тега | `pwsh -NoProfile -File scripts/all_features/diff.ps1 -From <tag>` |
| Текущая ветка | `git branch --show-current` |
| Все рабочие деревья | `git worktree list` |
| Последние теги релизов | `git tag --list "release/*" --sort=-version:refname` |

---

## Связанные документы

- [`.claude/commands/git.md`](../.claude/commands/git.md) - полный git-справочник: ветки, worktree, fix-release, push
- [`.claude/commands/skill-release.md`](../.claude/commands/skill-release.md) - детальный алгоритм `/skill-release`
- [`scripts/release-worktree-sync.txt`](../scripts/release-worktree-sync.txt) - список gitignored-файлов, синхронизируемых в worktree перед каждым release-билдом
- [`docs/WHATS_NEW.md`](WHATS_NEW.md) - история релизов (источник fastlane-changelog'ов; RU/UK зеркала рядом)
- [`docs/ALL_FEATURES.jsonl`](ALL_FEATURES.jsonl) - инвентарь возможностей, из его диффа строятся заметки релиза и showcase
- [`docs/FEATURES.md`](FEATURES.md) - публичный showcase (EN/RU/UK), правится только `/skill-release`
