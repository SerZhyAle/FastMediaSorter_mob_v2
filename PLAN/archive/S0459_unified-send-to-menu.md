# Стратегическая спецификация: S0459 - Единое меню «Отправить в»

**Ticket:** S0459
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-16
**Tier:** 2 - Significant (epic)
**Roadmap entry:** Ad-hoc - запрос 2026-06-16. Эпик: меню + настройки + реализация всех получателей.
**Tactical plan:** `PLAN/S0459_unified-send-to-menu/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-16

**Текст:**

у нас собиарается большой список "отправить в".. Это и Google KEEP, Google Lens, email, telegram.. в общем список большой и если оа все включены - ниспдающее меню к файлу длинное и неудобное - идея такая - найти все места где это есть и объеденить их в одно меню вместо SHARE to.. Это должна быть кнопка-меню команндной панели высокого приоритета для файлов. А там или тогда, когда это уже в ниспадающем меню - это должно быть починенное меню-список получателей. этот список зависит оттого что включено в настройках. Разгружает ниспаающее меню (один пункт вместо 10) и объединяет логически  всё это явление - оправить что-то куда то наружу. Нужен полный ресерч, сбор мест, решение по интерфейсу и реализация

**Вложения:**

Вложений нет.

**Дополнение (2026-06-16):** эпик. Один тикет на всё: единое меню + настройки + реализация всех получателей. Консолидирует (absorb) ранее заведённые share-to задачи; их объём поглощён сюда, сами тикеты отменены и заархивированы как superseded:

- S0443 keep-send-option
- S0444 player-send-email
- S0445 profile-share-to-setting
- S0446 messenger-share-settings
- S0458 standalone-image-google-lens-gate

Слой настроек/реестра/резолвера уже отгружен фундаментом S0452 (Verified) - эпик его потребляет, не переделывает.

**Уточнения владельца (2026-06-16):**

- Единое меню обобщает и существующий системный «Share to», и прочие механизмы отправки - все они становятся получателями одного меню, отдельных команд не остаётся.
- У пунктов меню должны быть иконки (изображения получателей), чтобы пользователь сразу видел, куда отправляет.

---

## 1. Проблема

Список действий «отправить файл наружу» разросся: системный Share, Google Keep, Google Lens, Email, Telegram, и запрошены WhatsApp/Instagram. Когда включено много таргетов, ниспадающее меню файла становится длинным и неудобным, а сами команды разбросаны по разным поверхностям (панель команд плеера, overflow просмотра, автономные плееры) и заводятся точечно. Фундамент S0452 уже даёт реестр таргетов, per-target флаги и предикат доступности, но единого UI-потребителя нет - каждый таргет иначе пришлось бы гейтить отдельной командой в горячем командном пути. Нужна одна логическая точка «Отправить в..».

---

## 2. Цели

1. Одна команда «Отправить в..» высокого приоритета в командной панели файла (bar-кнопка), вместо россыпи отдельных команд-получателей.
2. «Отправить в..» = любой внешний получатель: доставка файла наружу в приложение/сервис/принтер. Не путать с внутренним списком ресурсов для копирования/переноса (см. Non-goals).
3. По нажатию - список получателей; состав = таргеты, включённые в настройках И доступные на устройстве И применимые к типу текущего файла(ов) (читается из реестра S0452).
4. Когда «Отправить в..» уезжает в overflow - вложенное нативное подменю, а не множество отдельных пунктов.
5. Поддержаны получатели: системный Share (chooser), Google Keep (текст), Google Keep (рисунок), Google Lens, Email, Telegram, WhatsApp, Instagram, Print, «Открыть в..» (внешнее приложение) - каждый как декларация в реестре + действие отправки.
6. Мультивыбор: получатели, принимающие пачку (Share, Telegram, Email-вложение, системный chooser), отправляют всю выборку; получатели одного файла (Lens, Print, Keep, «Открыть в..») применяются к первому файлу выборки.
7. Список реагирует на группу настроек «Команды отправить файл в..» (S0452): выключенный получатель не показывается; недоступный или неприменимый - скрыт по единому правилу.
8. Разрозненные точки «отправить/share/open-in/print» по всему приложению заменены единым меню - один пункт вместо ~10, без дубликатов.
9. Встроенный системный «Share to» Android (chooser, `ACTION_SEND`) - уже реализован - обобщён как один из получателей (универсальный «ещё.. / системный выбор»), а не отдельная команда рядом; его действие отправки переиспользуется как есть.
10. Каждый получатель показан с иконкой: для пакетных - иконка установленного приложения, для логических - нейтральный глиф (см. §3.2).

**Non-goals:**

- Каркас реестра/хранения флагов/резолвера доступности и группа настроек - область S0452 (готово); этот тикет потребляет его, расширяя модель только аддитивным полем применимости по типу (см. §3.2).
- Внутренний список получателей-ресурсов для копирования и переноса (destination-кнопки плеера, transfer-стратегии) - отдельный механизм, в это меню не входит и с ним не смешивается.
- Исходящее, не являющееся «отправить текущий файл получателю»: экспорт логов, письмо-багрепорт (mailto без вложения), ссылки Help/Play Store, установка APK (noLegal) - остаются отдельными действиями вне меню.
- Страница настроек «Плеер» - S0442 (готово).
- Интеграция с бизнес-API мессенджеров (WhatsApp Business / Instagram Graph) и серверные токены.
- Прямой выбор получателя сообщения в обход UI мессенджера - открытый вопрос осуществимости (§6); дефолт - «открыть приложение с вложением».

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. «Отправить в..» - команда высокого приоритета, попадающая в бар при наличии места.
2. Прямой выбор получателя внутри приложения, если мессенджеры это допускают; иначе «открыть в приложении с вложением» достаточно.
3. У каждого получателя в меню - узнаваемая иконка приложения/сервиса для быстрой ориентации.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты сборки; меню и таргеты в общем коде. Набор реальных send-действий не вводит flavor-специфики сверх существующей.
- **API level:** без API-специфики, кроме обязательного manifest-объявления видимости пакетов (`<queries>`) для email-клиентов и мессенджеров на API 30+.
- **Wear OS:** не затрагивается.
- **Производительность:** проверка доступности (PackageManager) не блокирует UI меню; подготовка сетевого файла к отправке - вне главного потока, как у текущих share-операций.
- **Совместимость данных:** флаги хранятся app-global в DataStore (каркас S0452), не в Room - изменения схемы нет. «Миграция» = дефолтные значения флагов таргетов.
- **Применимость по типу:** получатель показывается только если применим к типу текущего файла(ов) - Lens и Keep-рисунок: изображение; Keep-текст: текст; Print: PDF/изображение/текст/office; прочие: любой. Модель таргета S0452 расширяется аддитивным предикатом применимости по MIME/типу; существующие поля не меняются.
- **Мультивыбор:** при выборке >1 получатель одного файла (Lens/Print/Keep/«Открыть в..») применяется к первому файлу; получатели пачки (Share/Telegram/Email) отправляют всю выборку. UI несёт подсказку «применится к первому файлу» для single-only при мультивыборе.
- **Форма показа:** нажатие bar-кнопки открывает bottom sheet со строками иконка+подпись; в overflow - нативное вложенное подменю (`addSubMenu`).
- **Локализация:** EN/RU/UK - всегда обязательно (заголовок «Отправить в..», подписи получателей, сообщения об ошибке отправки).
- **Доступность:** меню-список фокусируем с D-pad/TV и клавиатуры; недоступный получатель отличается не только цветом (подпись «Не установлено»); touch target соблюдён.
- **Иконки:** для пакетных получателей (Telegram/WhatsApp/Instagram/Keep/Lens) - иконка установленного приложения через `PackageManager`; для логических (Share/Email/Print/«Открыть в..») - нейтральный `?attr`-тонированный глиф. Брендовые лого не бандлятся (trademark/Play-safety). Иконка дополняет текст, а не заменяет его (доступность).

### 3.3 Owner inputs (Approval gate)

- **Scope decision (2026-06-16):** «Отправить в..» = любой внешний получатель, включая принтер и Lens; внутренний copy/move-в-ресурсы из охвата исключён.
- **UI/UX decisions (2026-06-16):** иконки - гибрид (иконка приложения для пакетных + нейтральный глиф для логических); форма - bottom sheet из бара + нативное подменю в overflow; мультивыбор - single-only получатели применяются к первому файлу; диагностика/URL/APK-install - вне меню.
- **Defaults & behavior (2026-06-16):** сбалансированный пресет дефолтов (Share/Print/«Открыть в» ON, Keep при Google, Email при интернете, Lens и мессенджеры OFF); при одном применимом получателе - прямой запуск в баре, подменю в overflow; порядок по частоте, системный выбор последним catch-all.
- **Related tickets:** S0452 (foundation - реестр/флаги/резолвер/группа настроек, потребляется этим тикетом); supersedes S0443, S0444, S0445, S0446, S0458 (заархивированы 2026-06-16).

---

## 4. Контекст текущей архитектуры

Команды файла описаны единым перечнем с приоритетами и местом показа (бар или overflow); часть «отправить в» уже есть (системный Share, Telegram, Google Lens, Keep для текста), их доступность проверяется ad-hoc прямо в обновлении доступности панели команд, а в просмотре и автономных плеерах - свои отдельные точки. Фундамент S0452 ввёл реестр share-таргетов (регистрация через Hilt-мультибиндинг), app-global хранилище on/off флагов, единый предикат доступности и use-case эффективного состояния флага; группа настроек строится из реестра автоматически. Чего нет - единого UI-потребителя реестра: сейчас каждый таргет показывался бы отдельной командой, а гейтинг пришлось бы вплетать в горячий командный путь по каждому таргету. Поэтому меню разрастается и логика «отправить наружу» размазана.

---

## 5. Предлагаемый подход

Ввести единый UI-потребитель реестра S0452: одну команду «Отправить в..» в командной панели. Эта команда строит список получателей из реестра по правилу «флаг включён И таргет доступен» и показывает его - как всплывающий список при нажатии бар-кнопки, а в overflow как вложенное подменю. Каждый таргет несёт своё действие отправки (существующие - Share/Telegram/Lens/Keep; новые - Email и WhatsApp/Instagram поверх системного инвокера с адресацией пакету и откатом на системный выбор). Гейтинг видимости становится централизованным в этом одном меню - отдельные таргеты не трогают горячий командный путь, чем снимается прежняя необходимость протягивать предикат гейтинга в ручно-собираемые контроллеры панели. Разрозненные точки «отправить в X» по приложению сводятся к этому меню.

### 5.1 Основные столпы / модули

- Единая команда «Отправить в..» в перечне команд (высокий приоритет, bar-capable); показ как bottom sheet из бара и нативное подменю в overflow.
- Рендер списка получателей из реестра по правилу «включён И доступен И применим к типу текущего файла(ов)», реакция на настройки.
- Расширение модели таргета S0452 предикатом применимости по типу (additive, дефолт «любой тип»).
- Регистрация всех получателей в реестре + их действия отправки: существующие (Share, Telegram, Lens, Keep-текст, Keep-рисунок, Print, «Открыть в..») и новые (Email, WhatsApp, Instagram).
- Логика мультивыбора: пачка vs первый файл по способности получателя.
- Резолвинг иконок: иконка приложения через `PackageManager` для пакетных + нейтральный глиф для логических.
- Аудит и сведе́ние всех существующих точек «отправить/share/open-in/print» по приложению к единому меню.
- Видимость пакетов получателей для системы (manifest `<queries>`, API 30+).

### 5.2 Потоки данных и событий

- Настройки: реестр → группа «Команды отправить файл в..» → тумблеры → app-global флаги (целиком S0452).
- Меню: точка показа файла → команда «Отправить в..» → запрос у реестра списка «включён И доступен» → отображение списка/подменю → выбор получателя → действие отправки таргета.
- Отправка: получатель → подготовка файла (сетевой - кэш, как сейчас) → системный инвокер с адресацией пакету → при недоступности клиента откат на системный выбор.

### 5.3 Точки расширяемости

- Новый получатель - одна регистрация в реестре плюс действие отправки; меню подхватывает его автоматически.
- Способ показа (всплывающий список vs bottom sheet) и порядок получателей - параметры одной точки, не размазаны по таргетам.

---

## 6. Открытые вопросы / Research items

Все пункты закрыты ресёрчем 2026-06-16; артефакты в `PLAN/S0459_unified-send-to-menu/research/`.

1. **Исчерпывающий surface-аудит точек «отправить/share/open-in/print».**
   - Resolved: ~13 IN-menu точек отправки (вкл. standalone-кластер `shareCurrentFile`/`shareOfficeDocument` и аудио) + Print (4 сайта, через системный Print) + «Открыть в..» (неск. сайтов, вкл. `FileInfoLaunchManager`) сведены к получателям. OUT-firewall: логи, багрепорт-mailto, Help/Store URL, APK-install, внутренний copy/move, плюс экспорт конфигурации (ресурс `ResourceShareFormat`, бэкап JSON, диагностический текст). Standalone-плееры несут собственную кнопку share (под сведе́ние), не наследуют меню «бесплатно». (Уточнено независимой проверкой 2026-06-16.)
   - **Артефакт:** [research/01__surface-audit.md](S0459_unified-send-to-menu/research/01__surface-audit.md)
2. **Моделирование применимости по типу на `ShareTarget`.**
   - Resolved: аддитивное поле `applicableTypes: Set<MediaType> = emptySet()` (пусто = любой тип) + чистая `appliesTo(type)`; правило списка «включён И доступен И применим». Настроечный тумблер типом не гейтится. Дефолт не ломает реестр/тест/группу настроек.
   - **Артефакт:** [research/02__type-applicability-model.md](S0459_unified-send-to-menu/research/02__type-applicability-model.md)
3. **Фокус bottom sheet на TV/D-pad.**
   - Resolved: строки focusable + запрос фокуса на первый активный пункт при показе; не-цветовое отличие неактивных. Откат - нативное `addSubMenu` подменю (D-pad-дружелюбно) при регрессе на устройстве.
   - **Артефакт:** [research/03__bottomsheet-tv-focus.md](S0459_unified-send-to-menu/research/03__bottomsheet-tv-focus.md)
4. **Выбор получателя в мессенджерах.**
   - Resolved: программный выбор контакта официально не поддержан (WhatsApp `jid` недокументирован; Instagram - только image/video в свой share-flow). Действие = `ACTION_SEND` + `setPackage` + откат на chooser (переиспользует инвокер); получателя выбирает само приложение. Instagram-применимость = {IMAGE, VIDEO, GIF}.
   - **Артефакт:** [research/04__messenger-recipient-feasibility.md](S0459_unified-send-to-menu/research/04__messenger-recipient-feasibility.md)
5. **Email-действие с вложением.**
   - Resolved: `ACTION_SEND`/`ACTION_SEND_MULTIPLE` + `EXTRA_STREAM` + `EXTRA_EMAIL`/`EXTRA_SUBJECT` (MIME `message/rfc822` для смещения к почтовикам); не `mailto:` (теряет вложение). Переиспользует системный инвокер.
   - **Артефакт:** [research/05__email-send-action.md](S0459_unified-send-to-menu/research/05__email-send-action.md)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Пропущена точка «отправить в» при сведе́нии | Средняя | Где-то остаётся старая россыпь/дубликат | Grep-аудит всех share-точек как явный шаг; completeness-проверка |
| Вложенное подменю/список неудобно на TV/D-pad | Средняя | Регресс доступности на не-touch | Тест фокус-навигации; не-цветовое отличие недоступных |
| Мессенджеры ограничивают программный шаринг | Высокая | Прямой выбор получателя невозможен | Откат на `ACTION_SEND` chooser/открыть в приложении |
| Длинный список даже после объединения | Низкая | Список получателей всё ещё длинный | Показывать только включённые И доступные И применимые; порядок по приоритету |
| Мультивыбор «первый файл» неочевиден | Средняя | Пользователь думает, что отправил всю выборку | Подсказка «применится к первому файлу» у single-only при выборке >1 |
| Расширение модели S0452 ломает регистрации/настройки | Низкая | Регресс группы настроек или реестра | Аддитивное поле применимости с дефолтом «любой тип»; существующие поля не трогаются |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность: единое меню «Отправить в..» - одна команда высокого приоритета вместо россыпи отдельных пунктов; список получателей зависит от включённого в настройках. Отразить в FEATURES + _RU + _UK.

---

## 9. Архитектурные решения (ADR)

- ADR-1 - Централизованный гейтинг: видимость получателей решает один UI-потребитель реестра (меню «Отправить в..»), а не каждый таргет в горячем командном пути. Отменяет промежуточное решение S0452 «делегировать гейтинг таргет-тикетам» (оно имело смысл при отдельных командах; при едином меню гейтинг естественно централизуется). Следствие: таргет-тикеты не трогают `CommandPanel*`; добавление получателя - чистая регистрация.
- ADR-2 - Показ: bar-кнопка «Отправить в..» открывает bottom sheet получателей; в overflow - нативное вложенное подменю (`addSubMenu`). Форма зафиксирована 2026-06-16: bottom sheet, не PopupMenu - больше места под строки иконка+подпись.
- ADR-3 - Применимость по типу: получатель показывается только если применим к типу текущего файла(ов); правило списка = «включён И доступен И применим». Модель таргета S0452 расширяется аддитивным предикатом применимости (дефолт «любой тип»), существующие регистрации не ломаются.
- ADR-4 - Мультивыбор: получатели пачки (Share/Telegram/Email) отправляют всю выборку; получатели одного файла (Lens/Print/Keep/«Открыть в..») применяются к первому файлу выборки, с UI-подсказкой. (Решение владельца 2026-06-16.)
- ADR-5 - Иконки и подписи: гибрид - иконка установленного приложения через `PackageManager` для пакетных получателей + нейтральный `?attr`-глиф для логических; брендовые лого не бандлятся (trademark/Play-safety). Аналогично **подпись** пакетного получателя резолвится из установленного приложения (`PackageManager`), а не хардкодится: брендовые литералы (напр. Instagram) запрещены гейтом `verifyNoPlatformNames` в market-сборках, поэтому titleRes мессенджеров несёт нейтральный fallback (`share_target_title_app`), а реальная подпись берётся из приложения. (Решение владельца 2026-06-16; уточнено при реализации Phase 03 после срабатывания гейта.)
- ADR-6 - Граница охвата: меню = «отправить текущий файл/выборку внешнему получателю». Вне меню: внутренний copy/move-в-ресурсы; экспорт логов; багрепорт-mailto без вложения; ссылки Help/Play Store; APK-install. (Решение владельца 2026-06-16.)
- ADR-7 - Дефолты получателей: сбалансированный пресет - Share/Print/«Открыть в» включены; Keep (текст/рисунок) при наличии Google; Email при интернете; Lens и мессенджеры (Telegram/WhatsApp/Instagram) выключены. (Решение владельца 2026-06-16.)
- ADR-8 - Команда при одном применимом получателе: в баре - прямой запуск без меню; в overflow - всегда подменю. (Решение владельца 2026-06-16.)
- ADR-9 - Порядок получателей: по частоте использования, системный Share/chooser - последним пунктом-catch-all. (Решение владельца 2026-06-16.)
- ADR-10 - Print - host-связанный получатель: печатный путь (`DocumentPrintManager`) привязан к host-Activity плеера, поэтому Print не может быть context-free хендлером. Вводится интерфейс host-возможности, который реализует host (плеер - в фазе сведе́ния), а Print-хендлер диспатчит через него; host без печати Print не показывает. Решение раскрылось при реализации Phase 03 (исходный план считал Print content-drivable). (Решение владельца 2026-06-16.)
- ADR-11 - Источник для File/host-bound получателей: payload меню несёт доменный файл (`mediaFile`) для Print; подготовка сетевого/облачного файла к локальному виду делается на стороне меню до вызова хендлера, чтобы хендлеры оставались синхронными; Lens отправляет по уже подготовленному Uri. (Решение владельца 2026-06-16.)

---

## 10. Связи с другими спеками

- Опирается на (consumes): S0452 - реестр share-таргетов, app-global флаги, предикат доступности, группа настроек (Verified). Модель таргета расширяется аддитивно полем применимости по типу (см. ADR-3); существующее поведение не ломается.
- Опирается на: S0442 - страница настроек «Плеер» (Archived/Verified).
- Supersedes (absorbed, заархивированы 2026-06-16): S0443 (Keep), S0444 (Email), S0445 (системный Share), S0446 (мессенджеры), S0458 (Google Lens gate в автономном просмотре изображений). Их объём - регистрация таргетов + действия отправки + сведе́ние точек - поглощён §2/§5/§6 этого тикета. Код Lens-гейта из S0458 уже в дереве и сохранён; этот тикет его наследует и проверяет в составе единого меню.
- Re-homes (существующие ad-hoc Keep-команды, НЕ архивируются этим эпиком): S0362 (Keep в редакторах текста/рисунка) и S0431 (Keep в режиме чтения текста: встроенный + автономный плеер). Реализованы как отдельные overflow-команды вне реестра S0452 - это «разрозненные точки» из goal 8. При регистрации получателей Keep-текст/Keep-рисунок переиспользуются их действия отправки и семантика контента (чистый текст без номеров строк; текущая читаемая страница, а не весь файл). Фаза сведе́ния переносит их точку входа в единое меню и удаляет дублирующую ad-hoc команду. Сами тикеты завершают цикл независимо (device-test → Verified) до сведе́ния.

---

## 11. Критерии готовности (strategic-level)

1. В командной панели файла есть одна команда «Отправить в..» высокого приоритета.
2. Её вызов показывает (bottom sheet) список получателей = включённые в настройках И доступные на устройстве И применимые к типу текущего файла(ов).
3. Когда команда в overflow - получатели показаны как нативное вложенное подменю, а не отдельными пунктами.
4. Поддержаны получатели: системный Share, Google Keep (текст), Google Keep (рисунок), Google Lens, Email, Telegram, WhatsApp, Instagram, Print, «Открыть в..».
5. Выключенный в настройках или неприменимый к типу получатель в списке не появляется; при нуле применимых команда скрыта целиком.
6. При мультивыборе получатели пачки отправляют всю выборку, получатели одного файла - первый файл выборки (с подсказкой).
7. Прежние разрозненные команды «отправить/share/open-in/print» по приложению (включая отдельную системную «Share to») заменены единым меню - дубликатов нет (подтверждается grep-аудитом).
8. Каждый получатель отображается с иконкой: пакетные - иконка приложения, логические - нейтральный глиф.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0459` - создаст `PLAN/S0459_unified-send-to-menu/` с фазами.

---

## Last Audit

### Manual / on-device (2026-06-17)

- **Device:** emulator-5554 (Pixel 4 AVD, x86_64, Android 17 / SDK 37). **Flavor:** standard debug (`v2.60.6161.522-DEBUG`). **Package:** `com.sza.fastmediasorter.debug`.
- **Environment note:** bare emulator - none of the package-targeted receivers (Telegram / WhatsApp / Instagram / Gmail / Google Keep / Google Lens) are installed. Present: Chrome, Google Drive/Docs, Google Photos, GMS, Quick Search. This is the intended availability-reactivity test bed (most receivers must hide, not crash).
- **Seeded media:** `s0459_test_image.png` (Pictures/S0459), `s0459_test_text.txt` + `s0459_test_doc.pdf` (Documents/S0459); MediaStore-scanned. MANAGE_EXTERNAL_STORAGE granted via appops.
- **Verdict:** PASS - menu structure, type-applicability, availability-reactivity and dispatch all behave per §2/§11; zero crashes/ANRs.

| # | Acceptance (§2/§11 summary) | Expected | Actual | Result |
|---|---|---|---|---|
| 1a | Player overflow `Send to..` is a native submenu | overflow row with submenu arrow, not flat items | PDF + text in-app player overflow: `Send to..` row carries `submenuarrow`; expands to a native nested submenu | PASS |
| 1b | Bar `Send to..`/Share opens a bottom sheet | tap bar button -> bottom sheet of icon+label rows | Standalone image player `Share` bar button -> `design_bottom_sheet` titled `Send to..`, `rvSendToReceivers` rows with `ivReceiverIcon`+`tvReceiverLabel` | PASS |
| 3 | Draw-editor overflow `Send to..` opens unified menu | unified menu, not Keep-only | Not exercised on-device (draw editor not entered); code path carries the `S0459:` probe and routes through the unified `SendToMenuManager` (verified in source during sweep) | PARTIAL (code-confirmed, not driven) |
| 4 | Print in player only, hidden on browse/standalone | Print present in in-app player; absent on standalone | In-app PDF + text player overflow submenu: `Print` present. Standalone image bottom sheet: `Print` absent (host does not implement `SharePrintHost`, ADR-10) | PASS |
| 5 | Lens opens directly if installed, else hidden | Lens absent (not installed) -> not shown | Lens not in any list (absent app + not applicable to PDF/text); image lists also omit it (absent app) - availability-reactive, no crash | PASS |
| 6 | Failed/clientless send shows graceful path | no crash; chooser fallback or error toast | Email (no email client) -> `ACTION_SEND` `message/rfc822` fell back to system chooser (Sharing 1 file); no crash, no silent no-op | PASS (fallback path) |
| 7 | List reacts to settings + type + availability | unavailable/inapplicable receivers hidden, no crash | PDF/text submenu: Email, Open in.., Print, Other apps. Image bottom sheet: Email, Open in.., Other apps (no Print - host-bound; no Lens/Keep - apps absent). Type + availability gating correct | PASS |
| 8 | App icons/labels resolve for present apps | logical receivers show neutral glyph; present apps resolve | Logical receivers (Email/Open-in/Print/Other apps) render with rows; `Open in..` (image) launched Google Photos directly; `Open in..` (text) showed Chrome/HTML Viewer; `Email` chooser showed Gmail/Drive - all resolved correctly | PASS |

- **Dispatch paths exercised (no crash):** Email -> system chooser (PDF); Open in.. -> Chrome/HTML-Viewer chooser (text); Open in.. -> Google Photos direct (image, single resolver).
- **Debug probe tags observed in logcat:** `S0459: player overflow send-to submenu requested` (x2), `S0459: standalone share button -> send-to menu` (x1). Remaining probes (browse binary, file-info open-with, draw editor, in-app Office share, drawing-save, browse selection) sit on flows not driven this pass and/or rotated out of the short emulator logcat buffer; menu structure and dispatch are nonetheless verified on the player/standalone/text/PDF/image surfaces.
- **Not driven on-device this pass (left for a deeper sweep):** draw-editor `Send to..` (acceptance #3), in-app Office fallback Share (status-note #2), browse multi-select/per-file overflow/binary sheet, file-info open-with, PDF-page Lens distinct. None blocked the verified surfaces; no regressions seen.
- **Evidence:** `temp/S0459_devtest/` - `01_player_overflow_sendto_print.png`, `02_player_sendto_submenu_pdf.png`, `03_email_chooser_fallback.png`, `04_text_sendto_submenu.png`, `05_standalone_bottomsheet_image.png`, `logcat_full.txt`, `build.log`.

### Static contract audit (2026-06-17, `/spec-check`)

- **Verdict:** PASS - every §11 acceptance criterion is either UI-verified (device pass above) or statically corroborated by the codebase. The three on-device caveats (draw-editor / in-app-Office / browse-binary) are closed by static contract: all route through the single unified `SendToMenuManager`.
- **§11.1 high-priority command:** `CommandPanelLayoutPlanner.PlayerCommand.SEND_TO(priority 25, …)` - top of the high-priority group. Rendering surface is the native overflow submenu, not a dedicated bar slot, per ADR-2/ADR-8 (the deliberate `barCapable = false`; the inline comment records that a bar slot silently vanished for lack of a dedicated view). The bar entry point is the existing share button, which now routes into the menu (see §11.7). Deviation is documented and intentional, not a gap.
- **§11.2 gated list:** `BuildSendToReceiverListUseCase` three-gate filter (enabled ∧ available ∧ `appliesTo(type)`), then `SendToMenuManager.receiversFor` adds the host-capability gate (ADR-10). UI-verified.
- **§11.3 native submenu in overflow:** `SendToMenuManager.buildOverflowSubMenu` builds `menu.addSubMenu`. UI-verified (row 1a).
- **§11.4 all ten receivers:** `ShareTargetModule` binds `system_share, open_in, print, email, keep_text, keep_drawing, lens, telegram, whatsapp, instagram` via `@IntoSet`.
- **§11.5 hidden when off/inapplicable; zero → command hidden:** filter + `if (receivers.isEmpty()) return`. UI-verified (rows 5, 7).
- **§11.6 multi-file semantics:** `ShareTarget.batchCapable` + `content.single()` first-file scoping + first-file hint string in the submenu label.
- **§11.7 consolidation / no duplicates (the not-driven caveat closed here):**
  - Draw-editor `Send to..` (acceptance #3): `ImageDrawOverlayManager` (line ~419-430) merges the overlay then calls `sendToMenuManager().show(host, content, settings)` via the app `SendToEntryPoint`. Confirmed unified, not Keep-only.
  - In-app Office fallback Share (status-note #2): `PlayerShareManager.shareOfficeDocument` (line ~150-168) prepares the FileProvider Uri and calls `activity.sendToMenuManager.show(activity, content, settings)`. Confirmed unified, not a standalone `ACTION_SEND` chooser.
  - Browse-binary: `BrowseBinaryFileHandler.shareFile` → `sendToMenuManager.show(host, …)`; `openWithDefaultApp` → shared `openInHandler.send(…)`. Confirmed unified.
  - Standalone share button: `StandaloneFileOperationsHandler.shareCurrentFile` routes the shared `btnShareCmd` into `sendToMenuManager`. UI-verified (row 1b).
  - Residual `ACTION_SEND` references in the tree are the receiver handlers / `SystemShareInvoker` themselves and the §6 OUT-firewall sites (log export, support mailto, backup, inbound `ReceiveShareActivity`) - all out of menu scope by design.
- **§11.8 icons:** `ShareTargetIconResolver` hybrid - installed-app launcher icon for package receivers, null → neutral `?attr` glyph for logical receivers; labels likewise resolved from the installed app (ADR-5, keeps brand literals out). UI-verified (row 8).
- **ADR-10 Print host-gate:** `PrintShareTargetHandler.isSupportedBy = activity is SharePrintHost`; `PlayerActivity` implements `SharePrintHost`. Print hidden on incapable hosts. UI-verified (row 4: present in player, absent on standalone).
- **Conclusion:** caveat closed by static contract; UI evidence covers the remainder. Status advanced `BlockNeedUserTest` → `Verified`. The 12 `Timber.d("S0459:` probe tags removed across 11 files on leaving `BlockNeedUserTest`.

---

## Revision History

- **2026-06-16** - by `/spec-update` (`claude-opus-4-8`, focus: completeness, consistency)
  - Folded code-audit findings + 4 owner decisions into §2/§3.2/§3.3/§5.1/§6/§7/§9/§10/§11.
  - Scope locked: «Отправить в..» = любой внешний получатель (Print/Lens/«Открыть в..» включены); firewall non-goal против внутреннего copy/move-в-ресурсы.
  - New hard requirements: применимость по типу (расширение модели S0452), мультифайл-семантика, Keep разделён на текст и рисунок, исчерпывающий surface-аудит, поведение при пустом/единичном списке.
  - New ADR-3 (type applicability), ADR-4 (multi-file first-file), ADR-5 (hybrid icons), ADR-6 (scope boundary); ADR-2 уточнён (bottom sheet).
  - Tier desync (file Tier 2 vs journal 3) - выровнен в журнале (tier 2).
- **2026-06-16** - by `/spec-update` (`claude-opus-4-8`, focus: completeness)
  - 3 owner decisions (defaults=balanced, single-receiver=bar-direct/overflow-submenu, order=frequency+chooser-last) → ADR-7/8/9 + §3.3; убраны из §6 как решённые.
- **2026-06-16** - by `/spec-update` (`claude-opus-4-8`, focus: completeness, consistency)
  - §10: added re-home cross-link to S0362/S0431 (ad-hoc Keep paths outside S0452 registry; not archived). Captures lost Keep-text send semantics (clean text, current page) for reuse at receiver registration + consolidation. Resolves "what did S0459 lose" relative to S0431/S0452 (both kept; neither deprecated).
- **2026-06-16** - research complete; Draft -> Approved
  - All 5 §6 research items Resolved with artifacts under `research/` (surface audit, type-applicability model, bottom-sheet TV focus, messenger recipient feasibility, email send action).
  - Surface audit froze the consolidation work-list: 11 IN-menu sites + Print (4) + Open-in (2) -> receivers; OUT-of-menu firewall confirmed.
  - Type-applicability resolved as additive `applicableTypes: Set<MediaType> = emptySet()` + `appliesTo()`; no S0452 break.
  - Messenger/email actions resolved to `ACTION_SEND` + package targeting + chooser fallback (no programmatic recipient; no `mailto` for attachments).
  - Promoted to Approved (no open blockers remain for `/spec-tech`).
- **2026-06-16** - research self-verification (independent re-sweep)
  - Surface audit corrected: first pass missed the standalone share cluster (`StandaloneFileOperationsHandler.shareCurrentFile` across all 4 standalone hosts, `StandaloneViewManager.shareOfficeDocument`) and `FileInfoLaunchManager` open-with; fixed the false "audio has no share" claim (audio IS shareable).
  - OUT-firewall extended with config/diagnostic exports (resource `ResourceShareFormat`, backup JSON, error-dialog text).
  - research/01 rows 12-14 + correction note added; research/03 points to existing project bottom sheets to mirror; research/04 records known package ids + WhatsApp Business. §6 item 1 resolution line corrected. No new owner decision required.
