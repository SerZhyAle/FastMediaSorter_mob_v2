# Спецификация (compact bugfix): S0970 - permission flavor-gate ссылается на несуществующие BuildConfig-поля (standard release)

**Ticket:** S0970
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-06
**Tier:** 2 - Easy

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Источник:** remote-лог внешнего тестера (`/newlog`), сессия `logs/fastmediasorter_20260706_113335.log`. Билд **2.60.7042.357, standard, release**; POCO/Xiaomi (mt6768), Android 15/API 35.

**Эвиденс (вербатим):**

```
2026-07-06 11:33:36.703 E/App: Permission flavor-gate references unknown BuildConfig field: SUPPORT_AUDIO
2026-07-06 11:33:36.704 E/App: Permission flavor-gate references unknown BuildConfig field: ENABLE_PERSISTENT_AUDIO_PLAYBACK
2026-07-06 11:35:16.724 E/UserAction: Permission flavor-gate references unknown BuildConfig field: SUPPORT_AUDIO
2026-07-06 11:35:16.728 E/App: Permission flavor-gate references unknown BuildConfig field: ENABLE_PERSISTENT_AUDIO_PLAYBACK
```

Горит на старте и повторно на user-action.

**Корень (по коду):** `PermissionRegistryRepositoryImpl.kt:211` - при `NoSuchFieldException` (gate пермишена ссылается на отсутствующее BuildConfig-поле) логирует `Timber.e(...)` и возвращает `false` (safe-disabled default, без краша). Значит на standard-release пермишен(ы), чей flavor-gate завязан на `SUPPORT_AUDIO` / `ENABLE_PERSISTENT_AUDIO_PLAYBACK`, тихо резолвятся как отключённые.

**Пробел в тесте:** `PermissionRegistryRepositoryImplTest.kt:77` (`assertNotNull("Permission flavor-gate references unknown BuildConfig field: ...")`) обязан ловить это, но на standard-флейворе поле отсутствует, а тест не поймал -> вероятно тест гоняется против BuildConfig одного флейвора (не standard) либо declaredFlavorGateFields расходятся с реальными именами полей.

**Вложения:** нет.

---

## 1. Проблема / симптом

На standard release пермишен-реестр не может разрешить flavor-gate двух пермишен-групп: BuildConfig-поля `SUPPORT_AUDIO` и `ENABLE_PERSISTENT_AUDIO_PLAYBACK` не существуют (в standard BuildConfig, судя по всему, иные имена). Gate падает в safe-disabled -> соответствующие пермишены тихо отключены, E-лог на каждом старте + user-action. Крашей нет, но поведение пермишенов деградировано на релизе, и юнит-гейт это пропустил.

---

## 2. Корневая причина

Расхождение имён: declaredFlavorGatefields (в permission-реестре) ссылаются на `SUPPORT_AUDIO`/`ENABLE_PERSISTENT_AUDIO_PLAYBACK`, которых нет в BuildConfig standard-флейвора. Плюс тест не покрывает standard BuildConfig. Точную декларацию найти при реализации.

---

## 3. Исправление

Реализовано (корень оказался не в отсутствии полей - они есть на всех флейворах, а в **reflection**): `PermissionRegistryRepositoryImpl.resolveFlavorGate` (строки 208-225) заменил reflection-лукап `BuildConfig::class.java.getField(name)` на compile-time `when`-map с прямыми ссылками (`"SUPPORT_AUDIO" -> BuildConfig.SUPPORT_AUDIO`, `"SUPPORT_LOCAL_NETWORK"`, `"ENABLE_PERSISTENT_AUDIO_PLAYBACK"`). R8 константо-фолдит `public static final boolean` BuildConfig-поля и стрипает декларации, из-за чего reflection кидал `NoSuchFieldException` на minified release -> старый код логировал «unknown BuildConfig field» и тихо отключал пермишен. Прямые ссылки переживают R8 с корректным inlined-значением. `else` (неотображённое имя = ошибка разработчика) логирует и держит safe-default.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none (дедуп по «flavor-gate»/«BuildConfig field»/«SUPPORT_AUDIO»/«permission gate» - пусто).

---

## 4. Проверка

Статический аудит (working tree): reflection удалён -> `NoSuchFieldException` невозможен, поэтому E-строка «references unknown BuildConfig field» больше не может возникнуть из mapped-полей (else теперь пишет «unmapped» и только для незадекларированного gate). Все 3 объявленных gate (`declaredFlavorGateFields`: SUPPORT_LOCAL_NETWORK / SUPPORT_AUDIO / ENABLE_PERSISTENT_AUDIO_PLAYBACK, строки 85/119/130) имеют arm в `when`. Тест `every declared flavor-gate names an existing boolean BuildConfig field` гарантирует, что имена gate соответствуют реальным boolean BuildConfig-полям. Прямые `BuildConfig.*` ссылки inherently R8-safe (const-fold) - отдельная release-сборка не требуется, т.к. источник бага (reflection) устранён.

---

## Last Audit

**Дата:** 2026-07-09
**Статус:** Verified (статический аудит кода + тест-гард)

- Фикс присутствует: `PermissionRegistryRepositoryImpl.resolveFlavorGate` использует compile-time `when` с прямыми `BuildConfig.SUPPORT_AUDIO` / `SUPPORT_LOCAL_NETWORK` / `ENABLE_PERSISTENT_AUDIO_PLAYBACK`, без reflection; `else` -> log + safe default.
- Полнота: все `declaredFlavorGateFields` (3 имени) имеют arm; BuildConfig-поля определены на всех флейворах (`build.gradle.kts`).
- Тест-гард: `PermissionRegistryRepositoryImplTest.every declared flavor-gate names an existing boolean BuildConfig field` (reflection по test-variant BuildConfig ловит typo/removed-имя).
- R8-safety: замена reflection -> прямых const-folded ссылок устраняет корневую причину `NoSuchFieldException` на minified release; отдельная release-проверка не нужна.
- Probe-тегов `S0970:` нет. Правка входит в текущие зелёные сборки (standard fc/d).
