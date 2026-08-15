# Спецификация: S1641 - Стилизация субтитров в панельном плеере не применяется никогда

**Ticket:** S1641
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-14
**Tier:** bugfix

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14 (авто-захват по CLAUDE.md 3.1 в ходе S1618)

**Симптом:**

В панельном плеере шрифт субтитров из настроек (`ocrDefaultFontSize`, `ocrDefaultFontFamily`) не применяется никогда.

**Доказательство:**

- `PlayerSettingsManager.applyPlayerSettings()` вызывает `applySubtitleStyling()` только под условием `playerSettings.showSubtitles`.
- `playerSettings` - сессионные значения по умолчанию, где `showSubtitles = false`. До S1618 единственным, что могло их изменить, был диалог настроек плеера, у которого не было ни одной точки вызова; S1618 этот диалог удалил. То есть условие ложно всегда и было ложным всё время существования кода.
- Автономный плеер стилизацию применяет - `StandalonePlayerSettingsManager` вызывает `trackSelectionManager.applySubtitleStyle` без такого условия. Расхождение между двумя плеерами видно только здесь.

**Что выяснить:** от чего стилизация должна зависеть в панельном плеере - применять её безусловно при появлении дорожки субтитров, либо привязать к включению субтитров во вкладке субтитров диалога управления воспроизведением. Выбор влияет на то, где живёт вызов, поэтому нужен разбор пути включения субтитров в `PlaybackControlDialogFragment`.

**Границы:** не трогать автономный плеер - там путь рабочий.

---

## 1. Цель

Вернуть панельному плееру применение пользовательского шрифта субтитров. Стилизация должна ставиться на `SubtitleView` безусловно при готовности воспроизведения, а не под условием сессионного флага, который никогда не бывает истинным.

Разбор пути включения субтитров (вопрос из §0) выполнен по коду и закрыт: привязывать вызов к вкладке субтитров не нужно и было бы неполно. Субтитры в панельном плеере включаются тремя независимыми путями, и ни один из них не проходит через `playerSettings.showSubtitles`:

- `VideoTrackSelectionManager.applyTrackSelection` включает их по `channelPreference.subtitlesEnabled` (запомненный выбор канала, S1144) - это ветка `preference?.subtitlesEnabled ?: settings.showSubtitles`.
- Вкладка субтитров `PlaybackControlDialogFragment.setupSubtitleTab()` вызывает `handle.selectSubtitleTrack(..)` напрямую, минуя `PlayerSettingsManager`.
- Второй диалог выбора дорожки, `PlayerDialogHelper`, делает то же самое.

Стиль - свойство самого `SubtitleView`, а не события включения дорожки: `setStyle` держится до следующей установки и на невидимой вьюхе безвреден. Поэтому одна безусловная установка в точке готовности покрывает все три пути, тогда как привязка к одной вкладке покрыла бы один.

## 2. Область

- В области: `PlayerSettingsManager.applyPlayerSettings()` - снятие условия.
- Вне области: автономный плеер (`StandalonePlayerSettingsManager`), VR/XR-путь (`HudTrackController`), сама модель `PlayerSettings`.

## 3. Решение

Убрать условие `if (playerSettings.showSubtitles)` вокруг `applySubtitleStyling()`. Вызов остаётся в `applyPlayerSettings()`, которую `PlayerPlaybackCallbackImpl.onPlaybackReady()` вызывает на каждый новый файл - к этому моменту `PlayerView` уже присоединён, и `VideoTrackSelectionManager.applySubtitleStyle` находит `subtitleView`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1618 (удалил диалог настроек плеера, из-за чего условие стало заведомо мёртвым), S1144 (ввёл `channelPreference.subtitlesEnabled` - путь включения субтитров в обход `showSubtitles`)
- **UI scope:** n/a - видимых элементов управления не добавляется и не переносится; меняется только начертание уже существующих субтитров, приводясь к тому, что задано в настройках
- **Flavor scope:** n/a - код в `src/main` и не имеет флейворных ветвлений; наблюдаем он там, где по `docs/FLAVOR_MATRIX.md` включён `SUPPORT_VIDEO` - `standard`, `noLegal`, `lite`, `legacy`, `vr`, но не `photos`

---

## Phase 01 - Apply subtitle styling unconditionally

**Status:** ✅ Done
**Depends on:** none - single-phase fix
**Steps done:** 1 / 1

### Objective

Remove the dead `showSubtitles` guard so the panel player applies the user's subtitle font size and family on every playback-ready.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt` | Modified | ≤ 120 |

### Steps

#### Step 01.1 - Drop the `showSubtitles` guard in `applyPlayerSettings()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `applyPlayerSettings()`, call `applySubtitleStyling()` unconditionally instead of inside `if (playerSettings.showSubtitles)`. Replace the existing `// Apply subtitle styling from saved font settings when subtitles are enabled` comment with one stating why the call is unguarded: the subtitle style is a property of the `SubtitleView` and is inert while no cues render, so applying it on every playback-ready covers every path that turns subtitles on, none of which routes through `playerSettings`.

**Why:**

`playerSettings.showSubtitles` is a session default that nothing sets to true after S1618 removed the only dialog that edited it, so the guarded branch never runs and the font settings never reach the panel player; the three real subtitle-enabling paths named in §1 all bypass that flag.

**Verification:**

- `Grep` - `showSubtitles` returns zero hits in `PlayerSettingsManager.kt`.
- `Grep` - `applySubtitleStyling()` present in `applyPlayerSettings()` at statement level.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

### Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, then `.\a.ps1 d` BUILD SUCCESSFUL (APK v2.60.8112.319-DEBUG).
- [x] Dev log entry added via `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Rollback Plan

Revert the single-file change - no data migration, no user-visible surface added.

---

## Last Audit

**Дата:** 2026-08-14
**Вердикт:** Verified

**Что в коде:** `PlayerSettingsManager.applyPlayerSettings()` вызывает `applySubtitleStyling()` без условия; `showSubtitles` в файле не встречается.

**Доказательство на устройстве** (RFCR110NBQJ, SM-G996U1, Android 15, standard debug v2.60.8112.319):

- Открыт панельный плеер (`PlayerActivity`) на локальном видео из `virtual://all_video`.
- Logcat той же секунды: `VideoTrackSelectionManager: Subtitles disabled` - то есть `showSubtitles` ложен, ровно та ветка, которая раньше отменяла стилизацию.
- Следом `VideoTrackSelectionManager: Applied subtitle style - fontSize=AUTO, fontFamily=DEFAULT` и временная проба `S1641: panel player subtitle style applied size=AUTO family=DEFAULT`. До правки обе строки не появлялись бы вовсе.
- Предупреждения `No subtitle view available` нет - `PlayerView` к моменту `onPlaybackReady()` уже присоединён, как и предполагала §3.

Проба `Timber.d("S1641: ..")` удалена при выходе из `BlockNeedUserTest`.

**Не покрыто тестом:** отрисовка нестандартного значения (например `LARGE`) на дорожке субтитров. Значения читаются тем же выражением `TranslationFontSize.valueOf(settings.ocrDefaultFontSize)`, что и в рабочем автономном плеере, и проба напечатала именно прочитанное из репозитория настроек.
