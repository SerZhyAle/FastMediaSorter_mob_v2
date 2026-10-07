---
layout: default
title: "Огляд архітектури"
permalink: /docs/V2_architecture_overview-uk.html
lang: uk
---

<sub class="doc-stamp">26.10.06 21:33</sub>

<div lang="uk" markdown="1">

# Огляд архітектури

[Технічна специфікація](V2_Specification-uk.html) | [Стек, EN](TECH_STACK.html) | [Вимоги](TECHNICAL_REQUIREMENTS-uk.html)

Застосунок використовує MVVM, Hilt, репозиторії та use case. Потік виконання близький до Clean Architecture, але залежності при компіляції навмисно прагматичні.

## Потік шарів

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

ViewModel надає спостережуваний стан і події. Use case координує операції. Інтерфейси репозиторіїв утворюють межу для тестування; реалізації відповідають за збереження даних і локальні, мережеві чи хмарні джерела. Domain також використовує окремі типи шару data: це не твердження про строгу інверсію всіх залежностей.

## Модулі

- `app_v2`: основний застосунок і вісім варіантів телефона/гарнітури; XML, ViewBinding та наявні поверхні Compose.
- `wear`: застосунок Wear OS на Compose, редакції `standard` і `noLegal`.
- `watchface`: окремий ресурсний пакет Watch Face Format v4 для Wear OS 6 / API 36 і новіших.
- `lint-rules`: перевірки Android lint під час збірки.
- `benchmark`: інструменти вимірювань і baseline profile.

## Ключові принципи реалізації

- Стан представлення належить ViewModel; складна поведінка екранів передається manager/helper-класам, а не додається в Activity.
- Room зберігає стан застосунку. Міграції й відновлення різняться за модулями; відновлення не замінює перевірку міграцій.
- Набори вихідного коду редакцій і впроваджувані контракти обирають необов'язкові інтеграції.
- Телефон і Wear використовують application ID `com.sza.fastmediasorter`, але Wear має окремий namespace. Для Data Layer важливі сумісні ідентифікатори й підписи.
- Циферблат не входить до коду застосунку Wear: його XML відображає операційна система.

### Підсистема Трансляцій

`StreamsActivity` і `StreamsViewModel` відображають список. Імпорт каталогу використовує `ImportStreamCatalogUseCase` та `StreamSourceRepository` із сутностями й DAO Room. `StreamInlineAudioManager` керує аудіо у списку та ICY-метаданими; повноекранне відео використовує наявний шлях плеєра.

Протоколи й доступність екрана залежать від редакції; звіряйтеся зі [згенерованою матрицею](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md). Каталог не гарантує доступність віддаленого джерела чи сумісність кодека.

### Файлові операції та лаунчер

Операції передаються стратегіям відповідного протоколу. Перенесення папки між протоколами виконується поелементно: після скасування записані елементи можуть залишитися, а джерело видаляється лише після підтвердження копії. Транзакційного відкочування всієї папки немає.

У редакціях із лаунчером доступні робочий стіл, панель задач і гаджети. Домашній екран обирає користувач. Портретні й альбомні розкладки зберігаються незалежно.

## Джерела реалізації

- [Список модулів Gradle](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/settings.gradle.kts)
- [Редакції телефона й набори вихідного коду](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/app_v2/build.gradle.kts)
- [Ідентифікатори й пакування Wear](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/wear/build.gradle.kts)
- [Пакет Watch Face Format](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/watchface/build.gradle.kts)

Практичні налаштування описано в [документації користувача](../documentation/index-uk.html).

</div>
