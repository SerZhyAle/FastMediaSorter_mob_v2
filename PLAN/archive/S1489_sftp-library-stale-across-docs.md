# S1489 - Документы называют SSHJ библиотекой SFTP, включая политику приватности

**Status:** Archived
**Priority:** 60
**Tier:** 3 - Moderate (ad-hoc)
**Date:** 2026-08-07

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07 при `/spec-quiz S1421 S1422 S1428 S1441 S1440`. Текста владельца нет -
находка агента при исследовании сетевого слоя для S1440.

**Симптом:** приложение работает по SFTP через JSch, а документы утверждают, что через SSHJ. Три из
них - опубликованная политика приватности на трёх языках, которую пользователь открывает прямо из
настроек приложения.

## 1. Цель

Привести утверждения о библиотеке SFTP в соответствие с тем, что действительно собирается, во всех
поверхностях и локалях одной правкой, убрать из того же абзаца два числа, не соответствующих ни одной
зависимости, и завести механическую проверку, которая не даст этому классу расхождений вернуться.

Расхождение пережило два тикета миграции (S0207 перевёл проект с SSHJ на JSch, S0046 доделал переход в
проверке host key) и не всплыло ни разу - значит одной ручной правки недостаточно.

## 2. Что установлено кодом (2026-08-07)

- Зависимость: `com.github.mwiede:jsch:0.2.26` (`app_v2/build.gradle.kts:1495`), комментарий строкой
  выше: «JSch for Android - better KEX support than SSHJ». SSHJ отвергнут осознанно.
- `data/remote/sftp/SftpClient.kt` импортирует `com.jcraft.jsch.*`.
- `wear` пинит свой `jsch:0.2.17` (`wear/build.gradle.kts:196`) - другая версия, это факт, а не
  опечатка.
- `docs/TECH_STACK.md:134,149` уже говорит правду.
- **EdDSA как зависимости не существует.** Поиск `eddsa`/`i2p` по `app_v2/build.gradle.kts` даёт ноль
  совпадений. Утверждение «SFTP: SSHJ 0.37.0 with EdDSA 0.3.0» ложно целиком - и библиотека, и версия,
  и вторая библиотека. Ed25519 поддерживает сам форк mwiede, отдельным артефактом это не поставляется.
- **BouncyCastle не прямая зависимость и версии 1.78.1 не имеет.** Единственный блок, который её
  фиксировал, закомментирован (`app_v2/build.gradle.kts:1625-1630`, форсил 1.72), а комментарий на
  `:1491` говорит, что SMB тянет BouncyCastle транзитивно. Число 1.78.1 в четырёх README не
  соответствует ни включённому, ни выключенному состоянию.
- Политика приватности открывается из настроек по опубликованному адресу
  (`ui/settings/helpers/GeneralSettingsViewSetupHelper.kt:611-613`), то есть это пользовательская
  поверхность, а не внутренний документ.

## 3. Объём и ограничения

### 3.1 Поверхности

Устаревшее имя библиотеки:

- `docs/PRIVACY_POLICY.md:225`, `docs/PRIVACY_POLICY.ru.md:225`, `docs/PRIVACY_POLICY.uk.md:225`
- `docs/README.md:432`, `docs/README_RU.md:452`, `docs/README_UK.md:451`
- `README.md:481` - корневой README, отдельный файл от `docs/README.md`
- `docs/WEAR_OS_SETUP.md:152`
- `dev/TECH_REQUIREMENTS.md:292`
- `store_assets/post.md:54`, `post_habr.md:77,194`, `post_hackernews.md:25`,
  `post_reddit_homelab.md:35`, `post_reddit_selfhosted.md:24`
- `dev/NETWORK_SPECS.md:5` - исправлено на месте при обнаружении, в объём фаз не входит

Соседние числа в том же перечне зависимостей, проверяемые против сборки:

- «EdDSA 0.3.0» - четыре README, зависимости не существует.
- «BouncyCastle 1.78.1» - четыре README, версия не соответствует сборке.
- `README.md:432` (корневой) - «Glide 4.15.1» против пина 4.16.0.
- `README.md:3` (корневой) - бейдж Kotlin 1.9.0 против пина 2.2.10.

### 3.2 Ограничения

- Политика приватности и README ведутся на EN/RU/UK; правка ложится во все локали одним изменением.
- Страница приватности, список разрешений и форма Data safety в Play обязаны говорить одно и то же -
  список сторонних компонентов входит в это требование.
- Корневой `README.md` и `docs/README.md` - два разных файла, оба правятся.
- Изменений кода нет, поэтому сборка не запускается: закрытие идёт через `post-change.ps1` с
  `-ChangeType Doc`, а новый гейт - через `-ChangeType Script`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0207 - перевод SSHJ → JSch; S0046 - закрепление перехода в проверке host key;
  S1440 - тикет, при исследовании которого найдено расхождение.
- **UI scope:** пользовательских экранов не меняется. Меняется текст опубликованной страницы
  приватности, которую приложение открывает из настроек.
- **Flavor scope:** без изменений. `jsch` подключён безусловным `implementation`, то есть входит во
  все сборки.
- **Data / permissions scope:** без изменений. Ни одного нового разрешения, форма Data safety в Play
  не меняется - меняется только название библиотеки в перечне сторонних компонентов.

## 4. Решения, принятые при написании спеки

- **Маркетинговые посты правятся.** Они уже опубликованы на Reddit, HN и Habr, и правка в репозитории
  тех копий не меняет. Но файл в репозитории, утверждающий неправду, остаётся ложным независимо от
  этого, а текст поста переиспользуется при следующей публикации - тогда ошибка поедет дальше.
- **`THIRD_PARTY_LICENSES.md` не трогаем.** Он честно ограничен бинарными ассетами собственным текстом
  на строке 5. Его проблема - ссылка на несуществующий инструмент и полнота OSS-уведомлений - другой
  дефект и отдельный тикет (см. §7).
- **Гейт нужен новый, в `pins.psd1` он не ложится.** Существующая запись
  `lib.com.github.mwiede:jsch` в `scripts/doc-drift/pins.psd1:352-361` сравнивает **версии** через
  группу захвата `(?<v>..)`. Утверждение «имя SSHJ не должно встречаться» - другая форма проверки, и
  запись про версию её выразить не может. Отсюда отдельный `assert-*.ps1` про отставленные имена.
- **Версии BouncyCastle не восстанавливаем.** Вернуть закомментированный `resolutionStrategy` - это
  правка сборки с собственной проверкой, а не правка текста. Числа из README убираются, вопрос о
  пиннинге вынесен в отдельный тикет (§7).

## 5. Найдено попутно, вынесено отдельно

- Полнота OSS-уведомлений: `docs/OPEN_SOURCE.md` перечисляет 2 библиотеки из примерно 20 и не
  содержит GPL-зависимости NewPipeExtractor, а `THIRD_PARTY_LICENSES.md:5` ссылается на «OSS license
  aggregator in release-prep tooling», которого в проекте нет.
- Пробелы в пиннинге зависимостей: закомментированный `resolutionStrategy` BouncyCastle и разъезд
  версий `jsch` между `app_v2` и `wear`, невидимый гейту - `GradleParser.ps1` читает координаты
  библиотек только из `app_v2/build.gradle.kts`.

---

## Phase 01 - Correct the SFTP library name across every documentation surface

**Status:** ✅ Done
**Steps done:** 4 / 4

### Objective

Every maintained document names JSch instead of SSHJ, with all three locales landing in one change.

### Steps

#### Step 01.1 - Correct the published privacy policy in all three locales

**Files:** `docs/PRIVACY_POLICY.md`, `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`

**Prompt for developer:**

> In each locale, replace the `**SSHJ**` bullet in the open-source library list with `**JSch**`, keeping the surrounding bullet format and the locale's own wording for "SFTP protocol". Change nothing else in the list.

**Why:**

The privacy policy is opened directly from in-app Settings as a published page, so it names a third-party component to the user that the app does not ship - and the privacy page, the permission list and the store data-safety form are required to say the same thing.

**Verification:**

- `Grep` - `SSHJ` returns zero hits across the three privacy policy files.
- `Grep` - `JSch` matches exactly once in each of the three files.

**Status:** `[x]` done

---

#### Step 01.2 - Correct the four README variants

**Files:** `README.md`, `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md`

**Prompt for developer:**

> In the "Network Protocols" bullet block of each file, replace the SFTP line so it names JSch and the `com.github.mwiede` fork. Keep the neighbouring SMB and FTP lines untouched in this step.

**Why:**

The root README and the docs README are two separate files that drifted independently, and both state a library the build rejected on purpose - a reader picking a transport from the README would research the wrong project.

**Verification:**

- `Grep` - `SSHJ` returns zero hits across the four README files.
- `Grep` - `JSch` present in each of the four files.

**Status:** `[x]` done

---

#### Step 01.3 - Correct the Wear setup guide and the tech requirements table

**Files:** `docs/WEAR_OS_SETUP.md`, `dev/TECH_REQUIREMENTS.md`

**Prompt for developer:**

> In `docs/WEAR_OS_SETUP.md` replace `SSHJ` with `JSch` in the network-protocols row. In `dev/TECH_REQUIREMENTS.md` change the SFTP coroutine-check row to say JSch callbacks. Leave the already-correct `jsch` version row in the same file alone - it is gated by `pins.psd1` and matches the build.

**Why:**

The tech requirements row is the one a developer reads before touching SFTP cancellation, and it names callbacks belonging to a library that is not in the project, which sends the reader to the wrong API surface.

**Verification:**

- `Grep` - `SSHJ` returns zero hits in both files.
- `Grep` - `` `jsch` `` still present in `dev/TECH_REQUIREMENTS.md` on the toolchain-version row carrying `0.2.26`. The row is column-padded, so match the cell contents, not a fixed-width table pattern.

**Status:** `[x]` done

---

#### Step 01.4 - Correct the five marketing posts

**Files:** `store_assets/post.md`, `store_assets/post_habr.md`, `store_assets/post_hackernews.md`, `store_assets/post_reddit_homelab.md`, `store_assets/post_reddit_selfhosted.md`

**Prompt for developer:**

> Replace every `SSHJ` mention with `JSch` across the five files, six occurrences total, keeping each post's own language and formatting.

**Why:**

The posts are reused as the source text for the next publication, so leaving the wrong library name in the repository copy propagates the error to the next platform even though the already-published copies cannot be corrected.

**Verification:**

- `Grep` - `SSHJ` returns zero hits under `store_assets/`.

**Status:** `[x]` done

---

### Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Grep` - `SSHJ` returns zero hits repository-wide outside `dev/CHANGELOG.md` and `PLAN/`, where it is historical record.
- [ ] Dev log entry added.

---

## Phase 02 - Remove the version claims that match no dependency

**Status:** ✅ Done
**Depends on:** Phase 01
**Steps done:** 2 / 2

### Objective

The dependency bullets state only numbers that can be checked against the build files.

### Steps

#### Step 02.1 - Drop the EdDSA and BouncyCastle version claims

**Files:** `README.md`, `docs/README.md`, `docs/README_RU.md`, `docs/README_UK.md`

**Prompt for developer:**

> Remove `with EdDSA 0.3.0` from the SFTP line entirely - no such dependency exists. On the SMB line, keep BouncyCastle named but drop the `1.78.1` version, since it arrives transitively through SMBJ and the block that used to force a version is commented out.

**Why:**

A version number a reader cannot verify against `build.gradle.kts` is worse than no number, because it looks authoritative while pointing at an artifact the build never resolves.

**Verification:**

- `Grep` - `EdDSA` returns zero hits across the four README files.
- `Grep` - `1.78.1` returns zero hits across the four README files.
- `Grep` - `BouncyCastle` still present on the SMB line of each file.

**Status:** `[x]` done

---

#### Step 02.2 - Correct the two stale pins in the root README

**Files:** `README.md`

**Prompt for developer:**

> Set the Glide version to 4.16.0 to match `app_v2/build.gradle.kts:1438`, and set the Kotlin badge to 2.2.10 to match the managed pin block.

**Why:**

Both numbers sit in the same dependency listing being corrected and are verifiably wrong against pins the project already generates mechanically, so leaving them costs a second visit to the same paragraph.

**Verification:**

- `Grep` - `4.15.1` returns zero hits in `README.md`.
- `Grep` - `Kotlin-1.9.0` returns zero hits in `README.md`.
- `Grep` - `4.16.0` and `2.2.10` present in `README.md`.

**Status:** `[x]` done

---

### Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Dev log entry added.

---

## Phase 03 - Gate the class of defect so it cannot return silently

**Status:** ✅ Done
**Depends on:** Phase 02
**Steps done:** 2 / 2

### Objective

A mechanical check fails when a retired dependency name reappears in the maintained documentation.

### Steps

#### Step 03.1 - Add the retired-dependency-name gate

**Files:** `scripts/quality/assert-retired-dependency-names.ps1`

**Prompt for developer:**

> Write a gate that holds a list of retired dependency names with the library that replaced each one, and fails when a retired name appears under `docs/`, `dev/`, `store_assets/` or the root `README.md`. Seed the list with SSHJ, replaced by JSch. Exclude `dev/CHANGELOG.md` and `PLAN/`, where a retired name is a historical record rather than a claim. Follow the exit-code contract in CLAUDE.md section 7: header lists the codes returned, `Write-Error` uses `-ErrorAction Continue` before a non-1 `exit`.

**Why:**

The wrong library name survived two migration tickets and a doc-pin gate without being noticed, so the recurring finding has to become a mechanical gate rather than a second manual sweep.

**Verification:**

- `Glob` - `scripts/quality/assert-retired-dependency-names.ps1` exists.
- Run it - exit 0 on the corrected tree.
- Temporarily reintroduce `SSHJ` into a scratch copy under `temp/scratch/`, confirm the gate is scoped so it does not fire on `temp/`, then confirm it fires on a real doc path before reverting.

**Status:** `[x]` done

---

#### Step 03.2 - Register the gate in the fast-gate batch

**Files:** `scripts/quality/assert-fast-gates.ps1`

**Prompt for developer:**

> Add the new gate to the fast static-gates batch so `.\a.ps1 fg` runs it. Match how the neighbouring gates in that file are invoked and how their verdicts are aggregated.

**Why:**

A gate no runner invokes is not a gate, and the fast batch is where the other name-and-shape assertions already live, so this is the one place a developer sees it without asking.

**Verification:**

- `Grep` - `assert-retired-dependency-names` present in `scripts/quality/assert-fast-gates.ps1`.
- Run `pwsh -NoProfile -File ./a.ps1 fg` - exit 0, new gate named in the output.

**Status:** `[x]` done

---

### Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `.\a.ps1 fg` passes with the new gate listed.
- [ ] Dev log entry added.

---

## Last Audit (2026-08-07)

**Verdict:** реализовано; объём вырос против исходного и это зафиксировано ниже.

### Что оказалось шире, чем сказано при парковке

- **Гейт нашёл два места, которые ручной поиск пропустил.** Исходная парковка перечисляла только
  `.md`. Новый гейт сканирует и Kotlin, и вскрыл `core/util/MediaFileIntegrity.kt:10` и
  `wear/data/network/sftp/SftpConnectionTest.kt:12`. Второй комментарий был неверен дважды: не та
  библиотека и ложное обоснование заглушки - он утверждал, что библиотеки нет в модуле `wear`, тогда
  как `wear/build.gradle.kts:196` объявляет её прямой зависимостью. Комментарий переписан на
  проверяемую формулировку, а сам вопрос заглушки вынесен в S1497.
- **Первая правка `dev/NETWORK_SPECS.md` была неполной.** Сделанная при обнаружении, она оставляла
  «SSHJ» в поясняющем придаточном и утверждала «+ EdDSA» - зависимость, которой нет. Гейт сработал на
  собственную правку автора, что и требуется от гейта. Переписано.
- **Реестр документов не покрывал локали юридических документов.** Запись `legal-downloads` называла
  `docs/PRIVACY_POLICY.md` и `docs/TERMS_OF_SERVICE.md` без локалей, хотя у обеих есть RU и UK.
  Агент, правящий политику приватности, не узнавал о двух соседях - ровно тот промах, против которого
  и существует правило «все локали одной правкой». Пути заменены на глобы, реестр перегенерирован.

### Проверки

- `assert-retired-dependency-names` - PASS, 0 попаданий на 3253 файла. Детекция доказана отдельно:
  шаблон временно нацелен на заведомо присутствующее имя, гейт дал 121 попадание и упал, затем
  шаблон возвращён.
- Слепое пятно гейта закрыто после первого прогона закрытия: совет реестра про запись `site-landing`
  показал, что корневой `README.md` делит её с `index*.html`, а те не сканировались вовсе. Шесть
  опубликованных лендингов добавлены поимённо. Сами они оказались чисты - правился гейт, не они.
- `.\a.ps1 fg` - PASS, 16 гейтов, новый в списке.
- `.\a.ps1 fk` - BUILD SUCCESSFUL, 23 с.
- `:wear:compileDebugKotlin` - BUILD SUCCESSFUL.
- `assert-exit-contract` - PASS, 0 недостижимых точек выхода.
- `post-change -ScopeToFile` - PASS WITH ADVISORIES (2), оба разобраны: шпаргалка скриптов
  перегенерирована (`help.ps1 -Generate`), запись реестра `site-landing` проверена - лендинги
  `index*.html` чисты.
- `document_registry/validate.ps1` + `generate.ps1 -Check` - PASS, 27 записей.

### Что вынесено отдельно

- S1495 - полнота OSS-уведомлений, включая отсутствие GPL-зависимости NewPipeExtractor.
- S1496 - отключённый forcing BouncyCastle и невидимый гейту разъезд версий `jsch` между модулями.
- S1497 - заглушка проверки SFTP на Wear при наличии библиотеки в модуле.

## Критерии приёмки

- Ни один сопровождаемый документ не называет SSHJ; исторические записи в `dev/CHANGELOG.md` и `PLAN/`
  сохранены нетронутыми.
- Политика приватности во всех трёх локалях называет ту библиотеку, которая действительно поставляется.
- В перечнях зависимостей не осталось версий, не соответствующих `build.gradle.kts`.
- `.\a.ps1 fg` падает, если отставленное имя библиотеки вернётся в документацию.
