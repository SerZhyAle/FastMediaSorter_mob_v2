# Спецификация (compact bugfix): S0909 - BrowseInlineAudioManager: stale-generation coroutine can clobber newer track's UI state

**Ticket:** S0909
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, во время работы над S0896 (audio-focus-contract-sweep-p2). Не в рамках S0896 - находка про generation-guard, а не про audio focus, но в том же файле/методе, что и правки S0896.

**Текст:**

При добавлении audio-focus логики в `BrowseInlineAudioManager.inlineStart()` (S0896) обнаружено уже существовавшее до S0896 отдельное состояние-race: два места в этом же методе безусловно перезаписывают `_inlinePlayerState.value = InlinePlayerState()` при ошибке, не проверяя `myGeneration == playGeneration` (в отличие от S0862's generation-guard, который защищает публикацию самого `player`-объекта, но не защищает публикацию `_inlinePlayerState`).

Конкретно:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt` - `inlineStart()`, ветка `if (localPath == null) { ..; _inlinePlayerState.value = InlinePlayerState(); return@launch }` - безусловный сброс состояния.
- Тот же файл, `inlineStart()`, внешний `catch (e: Exception) { ..; _inlinePlayerState.value = InlinePlayerState() }` - тот же безусловный сброс.

**Вложения:** нет.

---

## 1. Проблема / симптом

Сценарий: пользователь быстро переключает треки в Browse inline audio (тап на трек A, затем сразу на трек B до того, как корутина A успела завершиться). `playGeneration` инкрементируется на каждый `inlineStop()`/`inlineStart()`, и генерация B корректно "перегоняет" генерацию A - но если корутина A (для трека A) в это время падает в `localPath == null` или в общий `catch (e: Exception)` (например, сетевой файл A не резолвится), она **безусловно** перезаписывает `_inlinePlayerState.value = InlinePlayerState()` (пустое/idle состояние), даже если к этому моменту генерация B уже опубликовала своё состояние (downloading/playing трек B). Результат - UI на мгновение (или до следующей эмиссии от B) показывает "ничего не играет", хотя трек B реально загружается/играет.

Практический эффект - вероятно кратковременный визуальный "мигание" состояния, не потеря данных и не краш. Гоночное окно узкое (нужен сбой именно у превзойдённой генерации), но не нулевое - особенно у сетевых (SMB) файлов, где `resolveLocalPath()`/`downloadSmbAudioToCache()` могут занимать заметное время.

---

## 2. Корневая причина

`inlineStart()` захватывает `val myGeneration = ++playGeneration` синхронно. В IO-корутине два места безусловно пишут `_inlinePlayerState.value = InlinePlayerState()`:
- ветка `localPath == null` - `releaseFocus()` уже под `if (myGeneration == playGeneration)` (S0896), но сброс состояния - нет;
- внешний `catch (e: Exception)` - та же асимметрия.

Если генерация A превзойдена генерацией B (rapid track switch) и затем A падает (сетевой A не резолвится / общий сбой), безусловный сброс затирает уже опубликованное состояние B (downloading/playing).

Проверено: третьего места нет. Ошибка внутри `buildInlineMediaPlayer` пробрасывается в тот же внешний `catch`; superseded-ветка (`myGeneration != playGeneration`) состояние не пишет, только `newPlayer.release()`.

---

## 3. Исправление

`BrowseInlineAudioManager.inlineStart()` - обернуть каждый из двух `_inlinePlayerState.value = InlinePlayerState()` в `if (myGeneration == playGeneration)`, сгруппировав со стоящим рядом `audioFocusManager.releaseFocus()` (единый guard-блок):

- Ветка `localPath == null`: `if (myGeneration == playGeneration) { audioFocusManager.releaseFocus(); _inlinePlayerState.value = InlinePlayerState() }` перед `return@launch`.
- Внешний `catch (e: Exception)`: тот же guard-блок.

Превзойдённая генерация больше не трогает состояние, опубликованное актуальной генерацией; актуальная сама управляет и focus, и state.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0896 (audio-focus-contract-sweep-p2 - fixed the audio-focus-specific instance of this same generation-guard gap in the same two call sites)

---

## 4. Проверка

- `standard debug` компилируется (`a.ps1 fk`); detekt-clean на `BrowseInlineAudioManager.kt`.
- Grep: оба сброса `_inlinePlayerState.value = InlinePlayerState()` стоят внутри `if (myGeneration == playGeneration)`; superseded-ветка состояние не пишет.
- Evidence rung: static + compile (P2). Гоночное окно узкое и не воспроизводится device-жестом надёжно; структурный guard - тот же shape, что уже проверенный S0862/S0896 в этой же функции. No device gate.
