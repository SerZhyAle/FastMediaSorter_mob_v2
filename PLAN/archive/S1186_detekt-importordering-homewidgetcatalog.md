# S1186 - detekt ImportOrdering не проходит на HomeWidgetCatalog даже при ASCII-порядке

**Status:** Archived
**Priority:** 45
**Created:** 2026-07-24

## 0. Исходный материал (verbatim)

Обнаружено при реализации S1165 (правка иконки калькулятора в `HomeWidgetCatalog`), 2026-07-24.

Свежий `:app_v2:detekt --rerun-tasks` (в т.ч. `--no-daemon`) устойчиво выдаёт по `HomeWidgetCatalog.kt`:

```
ImportOrdering - [HomeWidgetCatalog.kt] .. :3:1
```

Находка незаглушена baseline. Правка S1165 импорты не трогала (менялась только `iconRes` строки калькулятора), то есть находка пред-существующая.

## 1. Симптом

Блок импортов `HomeWidgetCatalog.kt` внешне отсортирован по ASCII (case-sensitive), но detekt-formatting (ktlint, `formatting.android = true`, `autoCorrect = false`) всё равно флагует `ImportOrdering` на первой строке импортов. Ручная перестановка не помогла:

- `com.sza.fastmediasorter.R` до `..domain..` (case-sensitive, R=82 < d=100) - FAIL;
- `..domain..` до `com.sza.fastmediasorter.R` (case-insensitive) - тоже FAIL;
- исправление явных сбоев (`RandomMusic` до `RandomPhotoFrame`, `javax.*` до `kotlinx.*`) - находка сохраняется.

`--no-daemon` не меняет вердикт, то есть это не устаревший кэш демона.

## 2. Почему это отдельный тикет

- Вне контракта S1165: тот про видимость иконки калькулятора в пикере; импорты - инфраструктура качества.
- Точный ожидаемый layout ktlint неочевиден, а `autoCorrect = false`, поэтому нет быстрого пути «дать форматтеру починить».
- Вероятно затрагивает не один файл: если ожидаемый `ij_kotlin_imports_layout` расходится с фактическим порядком, находка может быть у многих классов, импортирующих `com.sza.fastmediasorter.R` рядом с `domain`/`widget`.

## 3. Что предстоит выяснить

- Какой именно layout ждёт ktlint при `formatting.android = true` (нет `.editorconfig` в корне - берётся дефолт ruleset'а): точный `ij_kotlin_imports_layout` и правило сравнения (регистр, положение `R`).
- Даёт ли разовый прогон detekt с `autoCorrect = true` (или ktlint-CLI) канонический порядок, который можно применить и заморозить.
- Сколько ещё файлов в том же состоянии - сделать выборку по отчёту detekt (`ImportOrdering`), чтобы чинить пачкой, а не по одному.
- Стоит ли зафиксировать `ij_kotlin_imports_layout` в `.editorconfig`/detekt-конфиге, чтобы порядок стал детерминированным и воспроизводимым в IDE.

## 4. Статус проверки

**Проверено 2026-08-06: находка не воспроизводится. Правка не требуется.**

Доказательства, в порядке получения:

1. `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles "app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt"` -> `assert-detekt: PASS [scoped] - 3 file(s) with new findings project-wide, none among changed files`, выход 0. По файлу находок нет.
2. Находка не заглушена baseline, то есть «PASS» не означает «спрятано»: `HomeWidgetCatalog` не встречается ни в `config/detekt/baseline-app_v2.xml`, ни где-либо ещё в `config/detekt/`.
3. Ручное сравнение блока импортов с правилом ktlint (`formatting.android: true`, `autoCorrect: false` в `config/detekt/detekt.yml`) нарушений не нашло: группировка `*, java.**, javax.**, kotlin.**, ^` соблюдена (`kotlinx.*` относится к первой группе и стоит выше `javax.*`), внутри группы порядок посимвольный, регистрозависимый - `R` выше `domain`, `Cal` < `Cam` < `Cap`, `RandomMusic` < `RandomPhotoFrame`.
4. Независимое подтверждение раскладки на свежем коде: файлы `data/sensors/*ReadingSource.kt`, написанные в тот же день по S1179, располагают `kotlinx.*` перед `javax.*` и проходят гейт detekt. Значит гипотеза из §3 о «плоской ASCII-сортировке без группировки» неверна, а порядок в `HomeWidgetCatalog` соответствует действующему правилу.

Причина расхождения с §1 не устанавливалась: между 2026-07-24 и сегодня блок импортов мог быть приведён в порядок попутной правкой, либо изменилась версия ktlint в составе detekt. Для закрытия тикета это не важно - предметом был конкретный сбой на конкретном файле, и его нет.

Вопросы из §3 при этом сохраняют смысл как отдельная тема (зафиксировать `ij_kotlin_imports_layout` в `.editorconfig`, чтобы порядок был детерминирован и в IDE), но это уже не дефект, а улучшение, и оно шире одного файла.

Исходный контекст: тикет заведён как parking из реализации S1165 (правка иконки закрыта, эта находка считалась пред-существующим инфраструктурным долгом).

## 5. Решение владельца

### Quiz decisions (2026-08-10)

- Что делать с тикетом, раз дефект не воспроизводится, а идея из §3 осталась открытой? → Закрыть S1186 как исчезнувший дефект и завести отдельный тикет на улучшение (слаг и предмет этой спеки - один файл, а фиксация раскладки импортов общепроектная; правило 12 запрещает переименовывать префикс, поэтому перепрофилирование оставило бы имя врать о содержании).

Идея из §3 продолжена в **S1561** - `PLAN/S1561_pin-kotlin-imports-layout-editorconfig.md`.

Собранные при квизе доказательства, что улучшение действительно открыто и шире одного файла:

- Собственного `.editorconfig` в репозитории нет ни в корне, ни в модулях - совпадения только внутри `scripts/mcp/*/node_modules/`.
- `config/detekt/baseline-app_v2.xml` содержит 273 заглушённых находки `ImportOrdering` из 12253 записей baseline.
