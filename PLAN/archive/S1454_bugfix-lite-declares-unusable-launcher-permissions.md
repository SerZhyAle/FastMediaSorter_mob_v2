# Спецификация (compact bugfix): S1454 - lite и photos объявляют разрешения, которыми не могут пользоваться

<!-- 2026-08-07: сюда влит S1460 (permission-parity-test-red-on-lite-and-photos) по решению владельца.
     Имя файла оставлено прежним намеренно: его цитируют PLAN/RELEASE_QUEUE.md и журнал каталога,
     а переименование дало бы расхождение ради косметики. Область тикета - оба флейвора. -->

**Ticket:** S1454
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1450

**Текст:**

`PermissionRegistryManifestParityTest` fails on `lite` the first time it has ever been able to run there. S1436 added this per-variant parity gate and `docs/RELEASE_READINESS_STANDARD.md` names it a release blocker, prescribing `:app_v2:testLiteDebugUnitTest --tests "*PermissionRegistryManifestParityTest"` - but `lite` unit tests did not compile until S1450, so the gate could never fire on the very flavor whose permission composition differs most. S1450 made the suite runnable (3157 tests now execute on lite) and the gate immediately reported three divergences.

Observed 2026-08-07 in `app_v2/build/test-results/testLiteDebugUnitTest`, on the fixed tree:

```
PermissionRegistryManifestParityTest > every declared permission is a registry row or a named exemption FAILED
    java.lang.AssertionError: Declared with no registry row and no exemption:
    [android.permission.READ_CONTACTS, android.permission.POST_NOTIFICATIONS]

PermissionRegistryManifestParityTest > no exemption outlives the divergence it excuses FAILED
    java.lang.AssertionError: Exemptions for rows that no longer exist:
    [android.permission.BIND_NOTIFICATION_LISTENER_SERVICE]

PermissionRegistryRepositoryImplTest > S1335 registers the read-contacts permission in registry, onboarding and groups FAILED
    java.lang.AssertionError: read_contacts must be registered
PermissionRegistryRepositoryImplTest > getGroups returns only groups that have applicable entries FAILED
```

Likely shape, to be confirmed rather than assumed: `READ_CONTACTS` backs the launcher contact shortcuts (S1176 / S1206 / S1319), and `docs/FLAVOR_MATRIX.md` records `SUPPORT_LAUNCHER` as `[-]*` on `lite`, so the registry row is gated off while `app_v2/src/main/AndroidManifest.xml` declares the permission for every flavor. That is the same shape as S1442 (`RECORD_AUDIO` declared on lite/photos with `SUPPORT_MIC_RECORDING=false`), so the two should probably be decided together rather than fixed twice in different directions.

Why it matters beyond a red test: canon hard invariant 13 requires declaring only permissions the runtime actually uses, each with a user-visible justification, and `READ_CONTACTS` is exactly the sensitive kind Play scrutinises in a data-safety review. The parity gate exists to catch this; it has been unable to run on lite since it was written.

The decision is not mechanical - for each of the three findings the fix is either removing the declaration in the flavor (`tools:node="remove"`), adding the registry row, or adding a named exemption with a written reason; each choice needs a check that no runtime path in that build reaches the permission, and a release note if a shipped flavor's declared permission set changes.

---

## 1. Проблема / симптом

Четыре отказа на варианте `lite`, воспроизведены заново 2026-08-07 (`check-standard-fast.ps1 -Mode Unit -Flavor Lite -Tests "*PermissionRegistry*"` - 12 тестов, 4 упали). Это не одна поломка, а четыре разных, которые просто впервые смогли выполниться на этом флейворе.

- `READ_CONTACTS` объявлен, строки реестра нет, исключения нет.
- `POST_NOTIFICATIONS` объявлен, строки реестра нет, исключения нет.
- Исключение для `BIND_NOTIFICATION_LISTENER_SERVICE` выглядит протухшим.
- Два теста `PermissionRegistryRepositoryImplTest` требуют то, чего у `lite` законно нет.

**Из влитого S1460 - вторая половина, на `photos`.** Тот же парити-тест красен и там, теми же двумя утверждениями, и краснел ещё до S1442. Причина, по которой этого никто не видел, названа в S1460 и подтверждена чтением: `docs/RELEASE_READINESS_STANDARD.md` перечисляет прогоны `standardRelease`, `liteDebug` и `noLegalDebug` - `photos` в списке нет вовсе. При этом состав разрешений у `photos` самый узкий из всех (нет лаунчера, видео, документов, микрофона), то есть это как раз тот вариант, где объявленное-но-недостижимое разрешение вероятнее всего.

---

## 2. Корневая причина

Решающее правило уже выбрано не здесь: **ADR-1 из S1442** - разрешение снимается только там, где путь к нему закрыт архитектурно, а не там, где выключен флаг функции. Приравнивание флага к факту использования и было исходной ошибкой того тикета. Ниже каждая находка разобрана этим правилом, а не по имени флага.

- **`READ_CONTACTS` - путь закрыт архитектурно.** Единственный шов к `ContactsContract` во всём дереве - `ContactSnapshotDataSource`; его инжектит только `PickContactShortcutUseCase`, а того - только `LauncherHomeViewModel` и `LauncherHomeDependencies` из `src/launcherEnabled`. Этот набор исходников монтируется лишь в `standard` и `noLegal` (`build.gradle.kts:640-641`, `671-672`) и в `lite` отсутствует. Панель быстрого запуска, которая в `lite` есть, к этим командам не обращается вовсе. То есть в `lite` разрешение объявлено и недостижимо.
- **`POST_NOTIFICATIONS` - путь открыт.** `ScheduledOperationsWorker` безусловно создаёт канал и публикует уведомление, а его выключатель `ENABLE_SCHEDULED_OPERATIONS` задан `true` в обоих типах сборки без флейворных переопределений. Плюс онбординг: у строки стоит `shownInWelcomeDespiteGates`, и `PermissionEntry.kt:47-54` прямо называет `POST_NOTIFICATIONS` тем случаем, ради которого флаг введён. Строки в реестре нет не потому, что разрешение лишнее, а потому что её единственный гейт - `ENABLE_PERSISTENT_AUDIO_PLAYBACK` - называет одно из применений и не называет второе. Это ровно та же форма, что S1459 уже ведёт для `record_audio`.
- **Исключение `BIND_NOTIFICATION_LISTENER_SERVICE` не протухло - неверна проверка.** Тест сверяет ключи `rowWithoutDeclaration` с `entriesForBuild`, то есть с набором, уже отфильтрованным по гейтам флейвора. Строка `notification_listener` гейтится `SUPPORT_LAUNCHER`, в `lite` выключена, и проверка объявляет исключение мёртвым. Но исключение описывает определение реестра, а не подмножество конкретного флейвора: в `lite` последовательно отсутствуют обе стороны - и строка, и `<service>`, - то есть расхождения нет вовсе.
- **Два теста репозитория закрепляют допущения `standard`.** «S1335 registers the read-contacts permission» требует строку `read_contacts` без оглядки на `SUPPORT_LAUNCHER`, хотя предыдущий пункт показал, что её отсутствие в `lite` архитектурно правильно. «getGroups returns only groups that have applicable entries» требует равенства множеств, которого сама реализация не обещает: KDoc `getGroups()` намеренно отдаёт заголовок группы, чью единственную строку загейтили. Проверено, что это безопасно: `BuildPermissionRowsUseCase.kt:33` пропускает группу с пустым составом (`if (groupEntries.isEmpty()) return@forEach`), поэтому пустого заголовка «Контакты» в `lite` не появляется. Равенство держалось на `standard` лишь потому, что там у каждой группы остаётся хотя бы одна проходящая гейт строка.

---

## 3. Исправление

По одной мере на находку, каждая - следствие §2, а не выбор из вкуса.

- **Снять `READ_CONTACTS` в `lite`** оверлеем манифеста с `tools:node="remove"`, тем же приёмом, каким там уже снят `ACCESS_LOCAL_NETWORK`, а в `photos` - `RECORD_AUDIO`. Исключение после этого не нужно: расхождения не остаётся.
- **Добавить именованное исключение для `POST_NOTIFICATIONS`** в `declaredWithoutRow`, по образцу соседней записи `RECORD_AUDIO`: разрешение объявлено и используется, а строка спрятана слишком узким гейтом. Сам гейт здесь не расширяется - это отдельная правка того же класса, что S1459, и делать её заодно значит менять состав экрана разрешений в тикете про манифест.
- **Починить третью проверку парити-теста:** сверять ключи `rowWithoutDeclaration` с полным набором строк реестра, а не с отфильтрованным по гейтам. Для этого реестр открывает тестам ещё один аксессор рядом с существующими `@get:VisibleForTesting`.
- **Привязать два теста репозитория к способности:** проверку `read_contacts` выполнять при `SUPPORT_LAUNCHER`, а при выключенном гейте утверждать обратное - что строки нет; равенство групп заменить на то, что реализация действительно обещает.

- **Снять `READ_CONTACTS` и в `photos`** тем же оверлеем: замыкание там идентичное - `src/launcherEnabled` этот флейвор тоже не монтирует. Остальные три меры общие для всех флейворов по построению (таблица исключений и два тестовых класса лежат в общем коде), поэтому отдельной правки под `photos` не требуют.
- **Добавить `photos` в релиз-чеклист** `docs/RELEASE_READINESS_STANDARD.md`. Без этого гейт остаётся слепым ровно к тому варианту, где расхождение вероятнее всего, и находка вернётся.

Охват сборок не сужается (канон, инвариант 2): в `lite` и `photos` снимается разрешение, которого сборка не могла использовать, а список устройств разрешениями не задаётся.

### 3.3 Owner inputs (Approval gate)

- **Продуктового решения не требуется.** Каждая из четырёх мер выведена по ADR-1 из S1442 и по прочитанному коду, а не выбрана.
- **Related tickets:** S1436 (добавил гейт и владеет контрактом разрешений), S1442 (тот же класс дефекта для `RECORD_AUDIO`; его ADR-1 - решающее правило здесь), S1450 (сделал набор запускаемым на lite, чем и проявил находку), S1335 (регистрация read-contacts), S1459 (узкий гейт строки - та же форма, что у `post_notifications`), S1460 (**влит сюда** 2026-08-07 по решению владельца - описывал то же падение парити-теста плюс его половину на `photos`; архивирован)

---

## 4. Проверка

- `check-standard-fast.ps1 -Mode Unit -Flavor Lite -Tests "*PermissionRegistry*"` - ни одного отказа.
- То же на `standard` - правки тестов не должны ослабить проверку там, где способность есть.
- `.\a.ps1 fk` - компиляция standard проходит.

---

## 5. Фазы

### Phase 01 - Развести объявления, исключения и допущения тестов

**Objective:** `lite` объявляет только те разрешения, до которых у него есть путь, а парити-тест отличает флейворную вариативность от настоящего расхождения.

#### Step 01.1 - Снять READ_CONTACTS в lite

**Files:** `app_v2/src/lite/AndroidManifest.xml`
**Depends on:** - начало фазы

**Prompt for developer:**

> Add a `uses-permission` entry for `android.permission.READ_CONTACTS` with `tools:node="remove"`, next to the existing `ACCESS_LOCAL_NETWORK` removal. The comment must name the ticket and the reason: the only `ContactsContract` seam is reached exclusively from `src/launcherEnabled`, which `lite` does not mount.

**Why:**

Разрешение объявлено и в этой сборке недостижимо, а канон инвариантом 13 требует объявлять только то, чем среда выполнения действительно пользуется; `READ_CONTACTS` - именно тот чувствительный вид, который Play разбирает при проверке data safety.

**Verification:**

- `Grep` - `READ_CONTACTS` присутствует в файле вместе с `tools:node="remove"`.

**Status:** `[x]` done

#### Step 01.2 - Именованное исключение для POST_NOTIFICATIONS

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionManifestExemptions.kt`
**Depends on:** - независим от 01.1

**Prompt for developer:**

> Add `android.permission.POST_NOTIFICATIONS` to `declaredWithoutRow`, mirroring the `RECORD_AUDIO` entry's shape. The reason must state that the permission is declared and used (scheduled operations post a notification, and onboarding asks for it via `shownInWelcomeDespiteGates`), that the row is hidden because its only gate names persistent audio playback, and that widening the gate is tracked separately.

**Why:**

Снимать объявление здесь означало бы повторить ровно ту ошибку, которую называет ADR-1 из S1442 - судить по выключенному флагу функции, а не по достижимости пути, - и сломать уведомление фонового сервиса запланированных операций.

**Verification:**

- `Grep` - `POST_NOTIFICATIONS` присутствует в `declaredWithoutRow`.

**Status:** `[x]` done

#### Step 01.3 - Полный набор строк для проверки исключений

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** - независим

**Prompt for developer:**

> Add a `@get:VisibleForTesting` property exposing every registry row's manifest name regardless of build gate and SDK window, alongside the existing `entriesForBuild` / `declaredBuildGateFields`. KDoc must say it exists so a staleness check can tell a deleted row from a gated-off one.

**Why:**

Третья проверка парити-теста сегодня сверяется с набором, отфильтрованным по гейтам флейвора, и потому не может отличить удалённую строку от выключенной в этой сборке.

**Verification:**

- `Grep` - новое свойство присутствует и помечено `@get:VisibleForTesting`.

**Status:** `[x]` done

#### Step 01.4 - Починить третью проверку парити-теста

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryManifestParityTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Compare `rowWithoutDeclaration` keys against the new all-rows accessor instead of `entriesForBuild`, and extend the failure message to say that a gated-off row is not staleness.

**Why:**

Исключение описывает определение реестра, а не состав конкретного флейвора, поэтому выключенный гейт не должен читаться как исчезнувшая строка.

**Verification:**

- `Grep` - `entriesForBuild` больше не используется в тесте `no exemption outlives the divergence it excuses`.

**Status:** `[x]` done

#### Step 01.5 - Привязать тесты репозитория к способности

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt`
**Depends on:** - независим

**Prompt for developer:**

> In the `read_contacts` test assert the full registration only when `BuildConfig.SUPPORT_LAUNCHER` is true, and assert the row is absent when it is false, so the gate stays covered in both directions. In the groups test replace the set equality with what `getGroups()` actually promises: every group with an applicable entry has a header, and headers never leave declaration order.

**Why:**

Оба теста закрепляют состав `standard` как универсальный, хотя отсутствие строки `read_contacts` в `lite` архитектурно правильно, а равенство групп сама реализация в KDoc обещает не соблюдать.

**Verification:**

- `Grep` - `SUPPORT_LAUNCHER` присутствует в файле теста.

**Status:** `[x]` done

#### Phase Done Criteria

- [x] Все шаги `[x] done`.
- [x] Прогон `-Flavor Lite -Tests "*PermissionRegistry*"` без отказов.
- [ ] Прогон на `standard` без отказов - **не достигнут по причине вне этого тикета**, см. Last Audit.

---

## Last Audit

**Дата:** 2026-08-07. **Проведён:** `/spec-all`, Simple path, S4.

Эвиденс:

- `check-standard-fast.ps1 -Mode Unit -Flavor Lite -Tests "*PermissionRegistry*"` - exit 0. XML от 13:54:05: `PermissionRegistryManifestParityTest` 3/0, `PermissionRegistryRepositoryImplTest` 9/0. Было 4 отказа из 12, стало 0.
- `post-change.ps1 -ScopeToFile -ChangeType Mixed` - exit 0, `post-change: PASS`, без advisories.

Снятие `READ_CONTACTS` доказано не чтением диффа, а самим тестом: `PermissionRegistryManifestParityTest` собирает объявленные разрешения через `packageManager.requestedPermissions`, то есть читает **слитый** манифест варианта. Его первая проверка на `lite` теперь проходит, что и означает отсутствие разрешения в собранном манифесте.

**Прогон на `standard` остаётся красным по отдельному дефекту, не связанному с этим тикетом.** Отказ один: `Declared with no registry row and no exemption: [android.permission.CHANGE_WIFI_STATE]`. Разрешение объявлено в `app_v2/src/networkMonitor/AndroidManifest.xml:13` - набор исходников S1433, монтируемый в `standard`. Ни одна правка этого тикета внести его не может: здесь снималось `READ_CONTACTS` в оверлее `lite`, добавлялось исключение для `POST_NOTIFICATIONS` и правились два тестовых класса. Находка запаркована как **S1472** с воспроизведением и разбором развилки.

Разобранное при аудите:

- Утверждение KDoc `getGroups()`, что пустой заголовок группы безвреден, проверено, а не принято на веру: `BuildPermissionRowsUseCase.kt:33` действительно пропускает группу с пустым составом. Поэтому ослабление теста до вложенности - это приведение проверки к настоящему контракту, а не сокрытие дефекта UI; пустого заголовка «Контакты» в `lite` не появляется.
- Проверка гейта не потеряна: тест `read_contacts` теперь утверждает обе стороны - при `SUPPORT_LAUNCHER` строка обязана быть, без него обязана отсутствовать.
- Записи в `docs/ALL_FEATURES.jsonl` не делается - по прецеденту S1442 §8: снятие разрешения не добавляет и не убирает пользовательскую функцию.

**Вердикт:** `Implemented`. Все четыре исходных отказа устранены и подтверждены прогоном; остаточная краснота на `standard` принадлежит S1472.

---

## Last Audit - добор после влития S1460 (2026-08-07)

Влитие S1460 оказалось не бумажной операцией: оно **нашло незакрытую половину этого же тикета**.

Первый проход снял `READ_CONTACTS` только в оверлее `lite`. Прогон `photos`, которого до объединения не делалось, дал `Declared with no registry row and no exemption: [android.permission.READ_CONTACTS]` - 1 отказ из 12. Три остальные меры сработали и там сразу, потому что живут в общем коде: `photos` уже получил и исключение для `POST_NOTIFICATIONS`, и обе правки тестов (у S1460 на этом флейворе значилось 2 отказа, осталось 1).

Добор:

- `app_v2/src/photos/AndroidManifest.xml` - снят `READ_CONTACTS`. Обоснование по ADR-1 то же, что для `lite`, и оно не переносилось по аналогии, а проверено: единственный шов к `ContactsContract` достижим только из `src/launcherEnabled`, который `photos` не монтирует.
- `docs/RELEASE_READINESS_STANDARD.md` - в перечень прогонов парити-теста добавлен `testPhotosDebugUnitTest`, было три варианта, стало четыре.

Эвиденс добора: `check-standard-fast.ps1 -Mode Unit -Flavor Photos -Tests "*PermissionRegistry*"` - exit 0, XML от 14:25:04, `PermissionRegistryManifestParityTest` 3/0 и `PermissionRegistryRepositoryImplTest` 9/0.

Урок, который стоит того, чтобы быть записанным: правка манифеста через флейворный оверлей закрывает **ровно один** флейвор, тогда как соседние меры того же тикета были общими. Считать тикет закрытым по прогону одного варианта здесь было ошибкой, и поймал её не гейт, а прогон второго варианта - которого в релиз-чеклисте и не было.

**Вердикт после добора:** `Implemented`, область - `lite` и `photos`. S1460 архивирован как влитый сюда.
