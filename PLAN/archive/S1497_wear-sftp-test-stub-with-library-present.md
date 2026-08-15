# Стратегическая спецификация: S1497 - Проверка SFTP-подключения на Wear заглушена, хотя библиотека в модуле есть

**Ticket:** S1497
**Status:** Archived
**Priority:** 35
**Date:** 2026-08-07
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - найдено при работе над S1489 2026-08-07
**Tactical spec:** фазы встроены ниже (§12) - отдельная тактическая папка не создаётся, Tier 2

---

<!-- Draft only (/spec-draft): keep this section. /spec omits it entirely. -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1489

**Текст:** текста владельца нет - находка агента, вскрытая новым гейтом отставленных имён.

**Симптом:** `SftpConnectionTest` на Wear всегда возвращает отказ, а объяснение в его KDoc было
фактически неверным.

**Что установлено 2026-08-07:**

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpConnectionTest.kt` возвращает
  `Result.failure(UnsupportedOperationException(..))` всегда, безусловно.
- KDoc объяснял это тем, что «SSHJ (the SFTP library) is not bundled as a direct Wear dependency».
  Оба утверждения неверны: библиотека называется JSch, и она **является** прямой зависимостью модуля
  `wear` - `com.github.mwiede:jsch:0.2.17` (`wear/build.gradle.kts:196`).
- Комментарий поправлен в рамках S1489 на проверяемую формулировку - заглушка описана как незавершённая
  проводка, а не как отсутствие зависимости. Сама заглушка не тронута: менять поведение под
  документарным тикетом было бы подменой объёма.

**Почему это стоит тикета:** ложное обоснование скрывало вопрос, который теперь открыт. Библиотека на
classpath, значит препятствия, названного в комментарии, не существует. Остаётся выяснить, была ли
заглушка осознанным решением по батарее или размеру, либо просто недоделанной проводкой - и в
зависимости от ответа либо реализовать проверку, либо записать настоящую причину.

---

## 1. Проблема

Кнопка «Проверить подключение» при заведении SFTP-источника на часах всегда сообщает, что проверка
недоступна, - и это неправда: тот же источник тут же успешно открывается на просмотр, потому что
просмотр по SFTP на Wear давно работает на той же самой библиотеке. Пользователь остаётся без
единственного способа отличить опечатку в адресе или пароле от рабочей конфигурации: ошибку он увидит
только позже, уже на экране просмотра, и без объяснения причины. Область - сетевые источники модуля
`wear`.

---

## 2. Цели

1. Проверка SFTP-подключения на часах действительно подключается к серверу и возвращает результат этой
   попытки, а не заранее заготовленный отказ.
2. Неверные адрес, порт, логин, пароль или ключ дают отказ с причиной от сервера, а не текст
   «проверка недоступна».
3. Проверяется не только вход на сервер, но и достижимость базового пути источника, потому что
   источник с недостижимым путём настроен неверно, даже если вход прошёл.
4. Проверка не удерживает соединение: сессия закрывается при любом исходе, включая исключение.

**Non-goals:**

- Не расширять сетевые возможности Wear сверх проверки подключения.
- Не менять разрешительную позицию по ключу хоста - это отдельный тикет S1555.
- Не трогать заглушку FTP рядом - отдельный тикет S1554.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<не высказаны - находка агента>

### 3.2 Жёсткие ограничения

- **Ресурсы:** Wear - устройство с жёстким бюджетом батареи и памяти; сетевая проверка обязана иметь
  таймаут и не удерживать соединение.
- **Совместимость:** модуль пинит `jsch:0.2.17`, а не версию из `app_v2`; поведение обязано
  проверяться против той версии, которая в модуле, - см. S1496 про сам разъезд.
- **Flavor:** модуль `wear` собирается одним вариантом, флейворной развилки нет.
- **API level:** без API-специфики - JSch работает через обычные сокеты.
- **Wear OS:** затрагивается только модуль `wear`.
- **Локализация:** новых строк не вводится, поэтому обязательство EN/RU/UK выполняется существующими
  ключами.

### 3.3 Owner inputs (Approval gate)

Владелец по этому тикету не высказывался - находка агента. Значения ниже выведены при
авто-одобрении из кода и §3.2, а не получены от владельца; любое из них он вправе переопределить.

- **Wear OS:** да, целиком и только модуль `wear`.
- **Performance budget:** одна попытка подключения с таймаутом 10 секунд, сессия закрывается сразу
  после ответа; фоновых соединений не остаётся.
- **Validation level:** компиляция модуля `wear` плюс его юнит-тесты; проверка на живом сервере
  остаётся за владельцем и не блокирует закрытие.
- **Related tickets:** S1489 - поправил ложный комментарий и вскрыл вопрос; S1496 - разъезд версий
  `jsch` между `app_v2` и `wear`; S0111 - тикет, к фазе 04 которого привязан пин `jsch` в `wear`;
  S1554 - та же заглушка для FTP; S1555 - разрешительная позиция по ключу хоста на часах.

---

## 4. Контекст текущей архитектуры

Проверка подключения на часах идёт с экрана заведения источника через вью-модель в репозиторий
источников, который разводит вызов по типу источника: SMB честно подключается и отключается, а FTP и
SFTP делегируют одноимённым классам-проверкам, каждый из которых безусловно возвращает отказ. При этом
в том же пакете, что и SFTP-заглушка, лежит рабочий источник данных просмотра: он на JSch поднимает
сессию, открывает канал и закрывает оба в `finally`. То есть препятствие не техническое - проводка
проверки просто не была доведена, и класс-заглушка занял её место.

Уточнение к §0 по состоянию на 2026-08-10: в модуле пин уже не `0.2.17`, а `0.2.26`, выровненный с
`app_v2` в рамках S1496. На существо находки это не влияет - зависимость как была прямой, так и
осталась.

---

## 5. Предлагаемый подход

Заменить тело заглушки настоящей попыткой подключения, повторяющей уже работающий на часах путь
просмотра: та же библиотека, та же схема аутентификации по паролю или ключу, то же закрытие ресурсов
в `finally`. От пути просмотра проверка отличается двумя вещами: она не читает содержимое каталога, а
только убеждается, что базовый путь существует, и берёт более короткий таймаут, потому что за
результатом ждёт человек.

### 5.1 Основные столпы / модули

- **Слой проверки подключения (`wear`, data).** Единственная точка изменения: класс-проверка получает
  реальное тело вместо отказа-заглушки. Контракт метода и способ его внедрения не меняются, поэтому
  ни репозиторий, ни вью-модель, ни экран не трогаются.
- **Юнит-покрытие того же слоя.** Существующий тест закрепляет поведение заглушки как контракт и
  обязан быть переписан вместе с ней, иначе он превратится в тест, охраняющий дефект.

### 5.2 Потоки данных и событий

Экран заведения источника → вью-модель → репозиторий источников → слой проверки подключения → сеть.
Обратно поднимается `Result`: успех, либо отказ с причиной от библиотеки. Вью-модель уже умеет
показывать причину отказа - отдельная ветка «проверка недоступна» просто перестанет достигаться для
SFTP.

### 5.3 Точки расширяемости

Точка, куда позже придёт S1555: место, где сейчас безусловно отключается проверка ключа хоста, должно
остаться одним выражением, чтобы политику ключа можно было заменить, не переписывая подключение.

---

## 6. Открытые вопросы / Research items

1. **Заглушка осознанная или недоделанная**
   - **Вопрос:** есть ли причина не проверять подключение на Wear, кроме отсутствия проводки?
   - **Нужно выяснить:** историю тикета S0111 и то, работает ли на Wear само подключение по SFTP при
     просмотре, - если работает, то проверка подключения тем более реализуема.
   - **Статус:** Resolved - недоделанная проводка. В том же пакете лежит рабочий источник данных
     просмотра на той же библиотеке, он поднимает сессию и канал и читает каталог, а вью-модель
     просмотра им пользуется. Значит подключение по SFTP на часах работает уже сегодня, и осознанного
     решения по батарее или размеру за заглушкой нет - иначе оно запретило бы и просмотр.

2. **Что показывать пользователю**
   - **Вопрос:** если проверка остаётся заглушкой, менять ли текст отказа - сейчас он говорит
     «недоступно», не объясняя, что источник всё равно можно сохранить и использовать.
   - **Статус:** Resolved - вопрос снимается вместе с заглушкой. Вью-модель показывает «проверка
     недоступна» только когда отказ пришёл с `UnsupportedOperationException`; настоящая проверка этим
     исключением не отказывает, поэтому для SFTP начнёт показываться существующая строка с причиной
     отказа. Новых строк не вводится, существующие не меняются.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Реализованная проверка держит соединение на часах | Средняя | Расход батареи на устройстве, где он критичен | Жёсткий таймаут и закрытие сессии в `finally` |
| Проверка успешна, а просмотр падает на базовом пути | Средняя | Зелёная проверка вводит в заблуждение сильнее, чем честный отказ | Проверять достижимость базового пути, а не только вход на сервер |
| Долгая проверка выглядит как зависание на часах | Средняя | Пользователь уходит с экрана, не дождавшись ответа | Таймаут короче, чем у пути просмотра |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая наблюдаемая способность: на часах проверка SFTP-источника теперь действительно проверяет
подключение и называет причину отказа, вместо сообщения о недоступности проверки.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Проверка повторяет позицию пути просмотра по ключу хоста, а не позицию телефона**

- **Решение:** проверка подключения принимает ключ хоста так же разрешительно, как это делает уже
  работающий на часах путь просмотра.
- **Альтернативы:** сразу перенести на часы механизм пиновки ключа с телефона.
- **Почему:** проверка обязана предсказывать исход просмотра. Если она строже просмотра, она будет
  отказывать на источниках, которые откроются, - худший исход, чем отсутствие проверки. Ужесточать
  надо оба пути сразу и вместе с местом хранения ожидаемого ключа, которого в модели источника на
  часах нет; это отдельная работа, заведённая как S1555.

**ADR-2: Проверяется достижимость базового пути, а не только вход на сервер**

- **Решение:** после подключения проверка запрашивает атрибуты базового пути источника и только тогда
  отвечает успехом.
- **Альтернативы:** ограничиться подключением; либо читать листинг каталога, как это делает просмотр.
- **Почему:** вход на сервер не отвечает на вопрос, доберётся ли пользователь до своих файлов, а
  запрос атрибутов даёт тот же ответ, что и листинг, не таща содержимое каталога по радиоканалу часов.
- **Оговорка, вскрытая аудитом 2026-08-10:** просмотр на часах базовый путь сегодня не читает вообще -
  `BrowseViewModel` держит стартовый путь константой `/`. Значит проверка строже просмотра ровно на
  источниках, приехавших с телефона с непустым путём: форма заведения на самих часах всегда пишет `/`,
  и для них расхождения нет. Выбрано именно так, потому что источник с недостижимым настроенным путём
  сломан по существу, и молчать об этом хуже, чем сообщить. Само расхождение заведено как S1556.

**ADR-3: Таймаут проверки короче таймаута просмотра**

- **Решение:** проверка берёт таймаут подключения 10 секунд против 30 секунд у пути просмотра.
- **Альтернативы:** взять те же 30 секунд ради единообразия.
- **Почему:** за результатом проверки ждёт человек, глядя на экран часов, а просмотр может позволить
  себе дольше договариваться о соединении. Значение совпадает с тем, что телефон уже использует для
  той же операции.

---

## 10. Связи с другими спеками

- S1489 - поправил ложное обоснование в KDoc и вскрыл вопрос.
- S1496 - разъезд версий `jsch` между модулями.
- S1554 - та же незавершённая проводка для FTP; отдельный объём.
- S1555 - разрешительная позиция по ключу хоста на часах; отдельный объём, см. ADR-1.
- S1556 - просмотр на часах игнорирует базовый путь источника; отдельный объём, см. оговорку в ADR-2.

---

## 11. Критерии готовности (strategic-level)

1. Проверка SFTP-источника с верными данными завершается успехом.
2. Проверка с неверным паролем, логином, адресом или портом завершается отказом, причина которого
   пришла от библиотеки, а не текстом «проверка недоступна».
3. Проверка источника с несуществующим базовым путём завершается отказом.
4. Ни один исход не оставляет открытой сессии.
5. Юнит-тест модуля больше не утверждает, что проверка недоступна.

---

## 12. Фазы реализации

Tier 2 - одна фаза, отдельная тактическая папка не заводится.

### Phase 01 - Real SFTP connection test on Wear

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Steps done:** 2 / 2

#### Objective

Replace the unconditional-failure body of the Wear SFTP connection test with a real connect-and-probe
against the source, and retarget its unit test at the new contract.

#### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpConnectionTest.kt` | Modified | ≤ 90 |
| `wear/src/test/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpConnectionTestTest.kt` | Modified | ≤ 90 |

#### Steps

##### Step 01.1 - Implement the connection test against JSch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpConnectionTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the stub body of `testSftp` with a real attempt, mirroring `SftpDataSource.listDirectory`
> in the same package: on `Dispatchers.IO`, add the private key as an identity when
> `source.sshPrivateKey` is non-blank, open a session for `source.username@source.server:source.port`,
> set the password, disable strict host-key checking in one expression, and connect with a 10-second
> timeout held in a `private const val` companion field. Open the `sftp` channel, connect it, and call
> `stat` on `source.basePath` to confirm the path is reachable, then return `Result.success(true)`.
> Wrap the whole attempt so any exception becomes `Result.failure(e)` rather than propagating, and
> disconnect the channel and the session in a `finally` block using `runCatching` for each. Rewrite the
> KDoc: it now describes a real test, and it must not claim the operation is unavailable. Keep the
> class shape - `@Inject constructor()`, same method signature - so no DI or caller changes are needed.

**Why:**

Without a real body the button lies to the user: §1 records that the source it refuses to test opens
successfully for browsing moments later, on the same library and the same credentials. The 10-second
timeout and the `finally` teardown are the mitigations §7 names for the battery risk and the
perceived-hang risk on a watch, and the `stat` on the base path is ADR-2's guarantee that a green
check predicts a working browse.

**Verification:**

- `Grep` - `UnsupportedOperationException` returns zero hits in that file.
- `Grep` - `session.connect(` present in that file.
- `Grep` - `stat(` present in that file.
- `Grep` - `finally` present in that file.
- `.\a.ps1 fw` - module `wear` compiles.

**Status:** `[x]` done - `.\a.ps1 fw` exit 0 (2026-08-10).

##### Step 01.2 - Retarget the unit test at the new contract

**Files:** `wear/src/test/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpConnectionTestTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the test asserting that `testSftp` fails with `UnsupportedOperationException` and the literal
> message, and replace it with one that points the source at `127.0.0.1` on a port nothing listens on,
> then asserts the result is a failure whose exception is NOT `UnsupportedOperationException`. Keep the
> existing `makeSource` helper and give it parameters for server and port so both the refused-connection
> case and any later case can reuse it. The test must not reach the network beyond loopback, so it stays
> deterministic and fast on a build machine with no server.

**Why:**

§11 criterion 5 requires the module's tests to stop asserting that the check is unavailable: the
existing test pins the stub's exact failure as the contract, so leaving it in place would turn it into
a test guarding the defect this ticket removes.

**Verification:**

- `Grep` - `UnsupportedOperationException` appears in that file only inside a negative assertion.
- `Grep` - `127.0.0.1` present in that file.
- Module unit tests pass.

**Status:** `[x]` done - `check-standard-fast.ps1 -Module wear -Mode Unit` exit 0, result XML reports
1 test, 0 failures, 0.106 s, so the refusal is immediate and the test never waits on a network.

#### Phase Done Criteria

- [x] Both steps above are `[x] done`.
- [x] Module `wear` compiles.
- [x] `wear` unit tests pass.
- [x] Dev log entry added for both touched files.
- [x] Catalog re-synced for module `wear`.

#### Rollback Plan

Revert the phase commit - no data migration, no persisted state, no user-visible surface beyond the
result of a button that previously always failed.

---

## Last Audit

**Date:** 2026-08-10
**Mode:** strategic (phases inline, no tactical folder)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 3

### Corrected during this audit

- ADR-2 and §2 goal 3 asserted that browsing on the watch starts at the source base path. It does not:
  `BrowseViewModel:132` holds the start path as the constant `/` and never reads `source.basePath`.
  Both statements were rewritten to the verifiable form, the deliberate divergence is now recorded in
  ADR-2, and the underlying inconsistency is parked as S1556. No code change followed - the probe on
  the configured base path is the intended behaviour, and only its stated justification was wrong.

### Manual / on-device

- [ ] §11.1 - a correct SFTP source on a live server reports success. Needs a reachable server and a
      watch or Wear emulator; no device was attached during this session.
- [ ] §11.3 - a source whose base path does not exist reports failure. Same prerequisite. Statically
      the path is covered: `channel.stat` raises `SftpException`, which the second catch turns into a
      failed Result.
