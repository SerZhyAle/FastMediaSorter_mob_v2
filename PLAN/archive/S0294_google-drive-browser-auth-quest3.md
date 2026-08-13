# Стратегическая спецификация: S0294 — Google Drive browser-auth на Quest 3 через общий cloud OAuth

**Ticket:** S0294
**Status:** Archived
**Priority:** 80
**Date:** 2026-05-23
**Tier:** 4 — Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc — добавление Google Drive ресурса на Quest 3 через общую браузерную авторизацию
**Tactical plan:** `PLAN/S0294_google-drive-browser-auth-quest3/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без лимитов строк, Hilt-модулей, Room-миграций и пошаговой тактики.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation
- **Goal / expected outcome:** Provided by user - довести S0294 до рабочего состояния и закрыть тикет до `Verified`, чтобы Google Drive resource add на Quest 3 шёл через browser-auth без зависимости от GMS.
- **Local anchor:** Provided by user - `S0294`, экран `Add Resource -> Google Drive`, symptom `PlayServicesUnavailable` / abort before account selection on Quest 3.
- **Scope boundaries / forbidden areas:** Provided by user - не трогать глобальную Google Account карточку, backup/restore и прочие Google-поверхности; сохранить текущий Credential Manager path на устройствах с рабочим GMS; не использовать встроенный WebView для Google sign-in.
- **Done / success signal:** Provided by user - выполнить критерии из §11 и завершить тикет в статусе `Verified`.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions.
- **UI decisions / delegation:** Delegated by user - сохранить текущую Add Resource UI-модель; разрешается изменить только auth routing, возврат из браузера и user-visible fallback/error copy в рамках текущих UX patterns.

`Approved` допустим: все обязательные поля заполнены из запроса пользователя или явно делегированы текущему агенту.

---

## 1. Проблема

На Quest 3 добавление Google Drive ресурса сейчас неработоспособно. Пользователь нажимает карточку Google Drive на экране добавления ресурса, единый cloud-auth flow стартует, но Google-ветка уходит в Credential Manager, который требует Google Play Services. На Quest 3 Google Play Store и Google Play Services отсутствуют по дизайну устройства, поэтому flow обрывается ещё до выбора аккаунта.

Подтверждение есть в логе от 2026-05-23 01:08:32..01:08:38:

- `CLICK: GoogleDriveCard (AddResource)`
- `UnifiedCloudAuthManager.startInteractiveSignIn provider=GOOGLE_DRIVE`
- `GoogleDriveAuthPlugin.startInteractiveSignIn`
- `Google Play Store, but it is missing`
- `GmsAvailabilityChecker: Google Play Services unavailable`
- `Sign-in aborted: Google Play Services unavailable on this device`
- `Front A skipped reason=UnknownError`
- `GoogleDriveAuthPlugin: signInPrimary failed: UnknownError`

Итог для пользователя: карточка Google Drive на Quest 3 выглядит доступной, но реального пути довести вход до выбора папки нет. Текущие S0233/S0239 улучшают сценарий `PlayServicesOutdated`, но не решают сценарий `PlayServicesUnavailable`, который для Quest-класса устройств является нормой, а не исключением.

---

## 2. Цели

1. На Quest 3 и других XR-устройствах без Google Play Services добавление Google Drive ресурса должно переходить в браузерный OAuth flow вместо немедленного отказа через Credential Manager.
2. Новый путь должен использовать общий cloud-auth контракт и возвращать в UI тот же единый финальный результат: `Success`, `Cancelled` или `Error`.
3. После успешного входа пользователь должен попадать в выбор папки Google Drive и завершать добавление ресурса без ручных обходных шагов.
4. На обычных Android-устройствах с рабочим GMS текущий путь через Credential Manager должен остаться основным и без регрессий.
5. Google-домены должны проходить авторизацию policy-compliant способом: внешний браузер или Chrome Custom Tabs, без встроенного WebView.

**Non-goals:**

- Полная замена текущего Google identity-domain на всех устройствах.
- Переписывание карточки Google Account в Settings как части этого тикета.
- Перенос backup / restore и других Google-интеграций на новый flow в том же раунде.
- Добавление новых Google-сервисов вне Google Drive.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Для Quest 3 Google Drive должен использовать общую браузерную авторизацию того же класса, что уже применяется у Microsoft и Dropbox.
2. Экран добавления ресурса не должен знать о Quest-специфике глубже, чем выбор auth-маршрута; успех и ошибки продолжают приходить в тот же единый UI-канал.
3. Повторный вход в уже подключённый Drive-аккаунт на Quest не должен требовать ручной низкоуровневой диагностики или сторонних инструкций.

### 3.2 Жёсткие ограничения

- **Flavor scope:** только cloud-enabled варианты приложения. `lite` остаётся вне объёма.
- **Device scope:** новый путь нужен именно для устройств класса Quest / XR без GMS; наличие GMS по-прежнему должно вести в текущий Credential Manager flow.
- **Google OAuth policy:** встроенный WebView запрещён для Google sign-in. Разрешён только внешний browser / CCT путь.
- **Security:** токены и refresh-данные после браузерного входа должны храниться не слабее текущих cloud-учётных данных.
- **XR lifecycle:** round-trip через браузер не должен плодить дубликаты panel/task инстансов и не должен ломать возврат обратно в добавление ресурса.
- **Compatibility:** уже существующие Drive-ресурсы и текущий путь на телефонах/планшетах не должны терять работоспособность.
- **Localization:** любые новые user-visible строки на этапе реализации обязательны в EN/RU/UK.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** все cloud-enabled варианты, кроме `lite`; основной device target - Quest / XR без GMS, при этом текущий GMS path на обычных Android-устройствах сохраняется.
- **Auth route contract:** `Credential Manager` остаётся primary path при рабочем GMS; при `PlayServicesUnavailable` Google Drive уходит в browser OAuth и возвращает в UI тот же финальный контракт `Success / Cancelled / Error`.
- **Browser / return contract:** Google auth идёт только через policy-compliant browser surface; redirect URI - `com.sza.fastmediasorter:/oauth2redirect`; возврат обязан восстанавливать текущий add-resource flow без дубликатов panel/task.
- **Credential model:** browser-auth может хранить отдельные Drive credentials в encrypted storage и регистрировать accountId в общей cloud-account модели, но не переводит глобальную Settings Google Account поверхность на новый backend в рамках этого тикета.
- **Validation level:** обязательны targeted compile/build, статическая проверка tactical критериев и manual acceptance для Quest happy path / cancel path / no-browser fallback; на устройстве с рабочим GMS текущий sign-in не должен регрессировать.
- **Owner sign-off:** 2026-05-24.
- **Related tickets:** S0200, S0233, S0239, S0243, S0267, S0281.

---

## 4. Контекст текущей архитектуры

Сейчас Google Drive в потоке добавления ресурса использует тот же единый cloud-auth оркестратор, что и другие облачные провайдеры, но внутренняя реализация Google-ветки делегирует интерактивный вход в identity-domain, где единственным backend'ом является Credential Manager. Этот backend проектировался вокруг primary Google account binding и хорошо работает там, где у устройства есть Google Play Services нужной версии.

Для Quest-класса устройств картина другая:

- глобальное предупреждение о GMS на XR специально подавлено как нерелевантное для устройства;
- live guard перед sign-in всё равно честно перепроверяет наличие GMS и видит `unavailable`;
- repair-flow из S0233 срабатывает только для `PlayServicesOutdated`, но не для полного отсутствия GMS;
- итоговая причина деградирует до `UnknownError`, а альтернативного browser-auth маршрута для Drive нет.

При этом в проекте уже существует отдельная Google-domain browser plumbing для других auth-сценариев: Google-домены маршрутизируются в browser / CCT, а не в WebView. Dropbox и OneDrive уже живут в модели внешнего интерактивного flow с возвратом результата в общий cloud-auth контракт. То есть недостаёт не «общего механизма браузерного входа как класса», а именно Google Drive-ветки, которая умеет пользоваться этим классом flow на GMS-less устройствах.

---

## 5. Предлагаемый подход

### 5.1 Маршрутизация по возможностям устройства

Для Google Drive вводится двухконтурная стратегия:

- устройства с рабочим GMS продолжают использовать текущий путь через Credential Manager;
- устройства без GMS, где Credential Manager заведомо не может завершить вход, переключаются на браузерный OAuth flow.

Решение о маршруте принимается до запуска интерактивного sign-in, чтобы пользователь на Quest не видел ложный старт заведомо неработающего сценария.

### 5.2 Общий browser-auth контур для cloud-провайдеров

Google Drive на Quest должен входить не через отдельный ad-hoc callback, а через тот же общий orchestration pattern, в котором уже живут внешние auth-flow других cloud-провайдеров. Для UI это остаётся тем же процессом:

- нажали карточку провайдера;
- дождались одного финального результата;
- по успеху открыли folder-picker;
- по cancel/error остались на экране без двойной навигации.

### 5.3 Scope первого релиза

Первый релиз намеренно ограничивается сценарием **добавления Google Drive ресурса на Quest / XR**. Глобальная карточка Google Account, backup и другие Google-поверхности не переводятся автоматически на новый backend в этом тикете. Это сокращает риск смешения двух разных моделей учётной записи: device-wide primary binding и resource-oriented browser OAuth.

### 5.4 Browser surface и возврат в приложение

Google sign-in на Quest должен идти через policy-compliant browser surface. Возврат после consent обязан приводить пользователя обратно в текущий сценарий добавления ресурса, а не в дублирующий task/panel. Если на конкретной Quest-конфигурации Chrome Custom Tabs недоступны, flow всё равно обязан иметь поддержанный fallback к внешнему браузеру с корректным app return.

### 5.5 Связность с уже сохранёнными ресурсами

Результат браузерного входа должен давать не только одноразовый success для текущего folder-picker, но и устойчивую привязку аккаунта, пригодную для последующих reopen, re-auth и silent restore на этом же устройстве. При этом новый Quest-путь не должен ломать существующие Drive-ресурсы, созданные на обычных Android-устройствах через текущий backend.

---

## 6. Открытые вопросы / Research items

1. **Граница scope первого релиза**
   - Должен ли browser-auth на Quest покрывать только `Add Resource -> Google Drive`, или сразу и глобальную карточку Google Account в Settings?
   - Рекомендация по умолчанию: только Add Resource в первом релизе.

2. **Модель учётной записи на XR**
   - Успешный browser-auth на Quest должен создавать только Drive-ресурсную привязку или также считаться глобальным Google account binding для приложения?
   - Если оставить его ресурсным, как должен вести себя экран Settings на Quest?

3. **Browser surface на Quest**
   - Что считать основным каналом: CCT-first с fallback во внешний браузер, или сразу внешний браузер как основной путь?
   - Нужно подтвердить фактическую доступность CCT-capable browser на Quest-таргете.

4. **Redirect и back-stack contract**
   - Какой именно return path гарантирует возврат в добавление ресурса без дублирования panel/task и без сломанного следующего запуска?
   - Нужно выбрать один XR-safe контракт и закрепить его тактически.

5. **Совместимость хранения и silent restore**
   - Можно ли для Quest-пути хранить Drive browser credentials в той же общей модели cloud-auth хранения, не смешивая их с primary-account state от текущего backend?
   - Нужен единый ответ, чтобы не получить два конфликтующих источника истины для одного и того же Drive-аккаунта.

6. **Поведение при отсутствии браузера**
   - Какое user-visible сообщение и какой fallback должен быть, если на устройстве нет пригодного browser / CCT-провайдера?
   - `UnknownError` здесь недопустим как финальный UX.

---

## 7. Риски

- **Высокий риск:** у одного провайдера появляется два auth-backend'а, и их нельзя оставить несогласованными по хранению токенов, повторному входу и sign-out semantics.
- **Высокий риск:** некорректный возврат из браузера на Quest может создавать дубли панели, ломать back-stack и делать следующий auth-launch нестабильным.
- **Средний риск:** Google OAuth registration и redirect contract могут потребовать дополнительной настройки для Quest/XR distribution path, даже если остальные cloud-enabled flavors уже зарегистрированы.
- **Средний риск:** слишком широкий scope первого релиза может смешать в одном change-set resource add, Settings, backup и global account semantics, что резко поднимет стоимость и риск регрессий.
- **Средний риск:** если на headset нет поддержанного CCT/browser surface, новый flow останется теоретическим без отдельного fallback UX.
- **Низкий риск:** UI экрана добавления ресурса получит новую ветку ошибок; без унификации сообщений легко вернуться к очередному generic `UnknownError`.

---

## 8. Влияние на пользователя (docs/FEATURES)

Сейчас это bug-fix существующей cloud-возможности на Quest-классе устройств, а не новая продуктовая фича. В `docs/FEATURES.md` запись добавляется только после реализации и только если владелец решит явно документировать поддержку Google Drive add-resource на Quest / XR как отдельный capability note.

На стадии стратегической спеки публичные feature-доки не меняются.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Не заменять текущий Google backend глобально**

- **Решение:** Credential Manager остаётся основным путём там, где устройство его поддерживает. Browser-auth вводится как capability-based маршрут для Quest / XR без GMS.
- **Альтернативы:** глобально перевести все устройства на browser OAuth; оставить всё как есть и считать Quest unsupported.
- **Почему:** первый вариант избыточен и рискован для стабильных Android-устройств; второй оставляет фактически сломанную карточку Google Drive на Quest.

**ADR-2: Первый релиз ограничить Add Resource сценарием**

- **Решение:** новый flow в первой итерации закрывает только добавление Google Drive ресурса и последующий folder-picker путь.
- **Альтернативы:** сразу перевести на него всю Google-поверхность приложения.
- **Почему:** задача пользователя сформулирована именно как «добавить Google Drive ресурс на Quest 3», а не как полная замена identity-domain. Узкий scope снижает риск и ускоряет проверку на реальном устройстве.

**ADR-3: Переиспользовать общий cloud-auth orchestration pattern**

- **Решение:** Google Drive browser-auth возвращает результат через общий cloud-auth flow, а не через уникальную Drive-only ветку.
- **Альтернативы:** отдельный ad-hoc callback только для Quest.
- **Почему:** ad-hoc путь создаст ещё один уникальный контракт рядом с уже существующим S0243-слоем и быстро станет новым техническим долгом.

**ADR-4: Для Google-доменов только policy-compliant browser surface**

- **Решение:** встроенный WebView не рассматривается.
- **Альтернативы:** временный WebView fallback ради скорости.
- **Почему:** это конфликтует с уже принятыми решениями по Google auth и создаст отдельный риск блокировки/нестабильности, особенно на XR.

---

## 10. Связи с другими спеками

- **S0200** — базовый Google account binding и Credential Manager migration. Новый тикет не отменяет S0200, а добавляет GMS-less маршрут для Quest-класса устройств.
- **S0233** — Play Services repair-flow. Полезен для `PlayServicesOutdated`, но не закрывает `PlayServicesUnavailable` на Quest.
- **S0239** — min-version guard для Credential Manager. На Quest именно этот guard честно доказывает, что текущий backend там непригоден.
- **S0243** — единый асинхронный канал результата cloud-аутентификации. Новый Google Drive browser-auth должен жить поверх этого контракта, а не рядом с ним.
- **S0267** — исследование общего хранения cloud-авторизаций. Если Quest-путь вводит отдельные Drive browser credentials, решение по хранению должно оставаться совместимым с этим исследованием.
- **S0281** — для Google-доменов уже принят policy-compliant browser routing без WebView. Новый тикет должен придерживаться той же линии.

Внешних блокеров пока нет, кроме решений из §6, которые должны быть подтверждены до Tactical.

---

## 11. Критерии готовности (strategic-level)

1. На Quest 3 нажатие `Google Drive` в `Add Resource` больше не заканчивается мгновенным generic error из-за отсутствия GMS.
2. Вместо этого стартует браузерный Google sign-in flow, понятный пользователю и соответствующий policy для Google-доменов.
3. После успешного consent пользователь возвращается в приложение и видит выбор папки Google Drive, а не пустой экран добавления ресурса.
4. После выбора папки ресурс реально появляется в списке ресурсов и открывается на том же устройстве.
5. Отмена входа возвращает пользователя обратно без двойной навигации, дубликатов panel/task и без ложного состояния `connected`.
6. На обычном Android-устройстве с рабочим GMS текущий Google Drive sign-in не меняет поведение.
7. Если пригодного browser surface на устройстве нет, пользователь получает конкретное объяснение и следующий шаг, а не `UnknownError`.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0294` — тактическая декомпозиция по маршрутизации, browser OAuth, XR return-path и хранению учётных данных.

## Last Audit

**Date:** 2026-06-01
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 20 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 1

### Manual / on-device

- [ ] Quest / XR without GMS: complete add-resource happy path through browser sign-in, folder picker, and resource reopen now that the Add Resource error surface receives the curated S0294 strings.
- [ ] GMS-capable Android device: confirm Google Drive still prefers Credential Manager and that backup/restore does not auto-adopt the browser-backed Drive session.
- [ ] Target compile retry after clearing the current generated-output / kapt cache contention in this workspace.
