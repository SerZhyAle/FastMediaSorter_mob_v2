# Phase 03 - Registry population: register all receivers + bind handlers

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Populate the S0452 registry: contribute all ten `ShareTarget` declarations (`@IntoSet`) with balanced defaults (ADR-7) and type-applicability (research 02), and bind one `ShareTargetHandler` per target (`@IntoMap` keyed by id). Existing receivers' handlers wrap existing send code via `ShareableContent`; new ones come from Phase 02. After this phase the settings group auto-populates.

---

## Prerequisites

- [ ] Phase 01 ✅ (model + handler contract), Phase 02 ✅ (new handlers).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetHandlerModule.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/SystemShareTargetHandler.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/OpenInShareTargetHandler.kt` | New | ≤ 80 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> Telegram / Lens / Keep-text / Keep-drawing / Print handlers wrap existing send logic (`PlayerShareManager`, `DrawKeepExportHelper`, `TextViewerManager`, `DocumentPrintManager`); place each new wrapper under `core/share/handlers/`. Print and Keep are content-driven via `ShareableContent` (text for Keep-text, uri+mediaType for Print).

---

## Steps

### Step 03.1 - Add receiver title strings (trilingual)

**Files:** `res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add receiver title keys missing today (`share_target_title_system_share`, `..._open_in`, `..._email`, `..._whatsapp`, `..._instagram`; reuse existing Keep/Telegram/Lens/Print titles where present). Use one lockstep call: `scripts/utils/set-android-string.ps1 -Action add -Key <k> -En -Ru -Uk`. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (label formula) and §6 (tone checklist).

**Verification:**

- `scripts/utils/set-android-string.ps1 -Action get -Key share_target_title_email` exits 0 (present EN/RU/UK).
- `scripts/check_strings_localized.ps1 -KeyPrefix share_target_title` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. Added 5 keys (system_share/open_in/email/whatsapp/instagram) EN/RU/UK via set-android-string.ps1 add; `check_strings_localized.ps1 -KeyPrefix share_target_title` exit 0 (5/5 OK); Cyrillic integrity confirmed by Grep (no mojibake). Reusing existing titles for telegram/lens/print/open-in-system/keep at registration. COMMUNICATION_POLICY §6: concise noun labels. Files: values/strings.xml + values-ru + values-uk.

---

### Step 03.2 - Wrapper handlers for existing-code receivers

**Files:** `core/share/handlers/SystemShareTargetHandler.kt`, `OpenInShareTargetHandler.kt`, plus Telegram/Lens/Keep-text/Keep-drawing/Print wrappers
**Depends on:** Step 03.1

**Prompt for developer:**

> Create one `ShareTargetHandler` per existing receiver, each `send(activity, content)` delegating to the existing invoker with data taken from `content` only: System Share → `SystemShareInvoker.invokeFiles`; Open-in → `ACTION_VIEW` chooser (mirror `FileInfoLaunchManager`); Telegram → `invokeFiles(preferredPackage = TelegramShareTargets.firstInstalled)`; Lens → existing Lens intent (image); Keep-text → `content.text` to Keep package; Keep-drawing → image uri to Keep; Print → hand `content` uri+mediaType to the print path. Each declares its `targetId`. Do not duplicate send logic - call the existing invokers/managers.

**Verification:**

- `Glob` - `SystemShareTargetHandler.kt` and `OpenInShareTargetHandler.kt` exist.
- `Grep` - `: ShareTargetHandler` matches once per wrapper file.
- `Grep` - `class .*ShareTargetHandler` count ≥ 7 across `core/share/handlers/` (5 existing wrappers + 2 named here; Email/WhatsApp/Instagram from Phase 02 also present).

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification PASS: 10 handlers across 10 files (system_share/open_in/telegram/keep_text/keep_drawing/print/lens + email/whatsapp/instagram). Owner-confirmed design (SharePrintHost, +mediaFile, menu-side prep). `GoogleLensShare` relocated ui/player/helpers -> core/share (+shareImageUri Uri entry) to keep core off the UI layer; 2 standalone FQN refs updated, old file removed, zero stale refs. Print handler dispatches via SharePrintHost (host wires in Phase 05). Files: core/share/GoogleLensShare.kt, core/share/handlers/LensShareTargetHandler.kt + 5 wrappers, ui/player/standalone/PhotoVideoStandaloneActivity.kt, DocumentStandaloneActivity.kt.

---

### Step 03.3 - Register ten ShareTargets with defaults + applicability

**Files:** `core/share/di/ShareTargetModule.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `ShareTargetModule`, add a `@Provides @IntoSet ShareTarget` for each of the ten receivers. Set `defaultEnabled` per ADR-7 (System Share / Print / Open-in = `ALWAYS_ON`; Keep-text / Keep-drawing = `ON_IF_GOOGLE`; Email = `ON_IF_INTERNET`; Lens / Telegram / WhatsApp / Instagram = `ALWAYS_OFF`). Set `availability` (`PACKAGE_INSTALLED` for package targets, `REQUIRES_GOOGLE` for Keep/Lens as today, `ALWAYS` for System Share/Open-in, `REQUIRES_INTERNET` for Email). Set `applicableTypes` per research 02 (System Share/Telegram/Email/Open-in/WhatsApp = empty; Print = IMAGE,GIF,PDF,TEXT,OFFICE_DOCUMENT; Lens = IMAGE,GIF; Keep-text = TEXT; Keep-drawing = IMAGE; Instagram = IMAGE,VIDEO,GIF). Set `titleRes` to the Step 03.1 keys and `iconRes` to a neutral `?attr`-tinted glyph for logical targets.

**Verification:**

- `Grep` - `@IntoSet` count ≥ 10 in `ShareTargetModule.kt`.
- `Grep` - `ShareTargetDefault.ALWAYS_ON` and `ShareTargetDefault.ON_IF_GOOGLE` and `ShareTargetDefault.ON_IF_INTERNET` and `ShareTargetDefault.ALWAYS_OFF` all present.
- `Grep` - `applicableTypes` referenced ≥ 5 times (restricted receivers).

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification PASS (25 hits = 10 @IntoSet + 10 ShareTargetDefault.* + 5 applicableTypes=setOf). 10 receivers registered: defaults per ADR-7 (Share/Print/Open-in ALWAYS_ON; Keep×2 ON_IF_GOOGLE; Email ON_IF_INTERNET; Lens/Telegram/WhatsApp/Instagram ALWAYS_OFF); applicableTypes per research 02 (Print img/gif/pdf/text/office; Lens img/gif; Keep-text text; Keep-drawing img; Instagram img/video/gif). Lens availability REQUIRES_GOOGLE (no <queries> dependency, no regression). titleRes reuse: telegram/lens/print/keep via existing strings. Files: core/share/di/ShareTargetModule.kt.

---

### Step 03.4 - Bind handlers @IntoMap by id

**Files:** `core/share/di/ShareTargetHandlerModule.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Create `ShareTargetHandlerModule` (`@Module @InstallIn(SingletonComponent::class)`) binding each `ShareTargetHandler` `@IntoMap @StringKey(<targetId>)` into a `Map<String, ShareTargetHandler>`. Use a `@MapKey` annotation for the string key. This map is what the menu controller injects to dispatch a selected receiver. Every `@IntoSet` ShareTarget id from Step 03.3 must have a matching map entry.

**Verification:**

- `Glob` - `core/share/di/ShareTargetHandlerModule.kt` exists.
- `Grep` - `@IntoMap` count ≥ 10.
- `Grep` - `Map<String, ShareTargetHandler>` or `@StringKey` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification PASS (30 hits = 10 @IntoMap + 10 @StringKey + 10 ShareTargetHandler return types). Every registered ShareTarget id has exactly one @Binds @IntoMap @StringKey entry into Map<String, ShareTargetHandler>. Files: core/share/di/ShareTargetHandlerModule.kt.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL in 38s; kapt resolved the Hilt multibinding graph (10 `@IntoSet` + 10 `@IntoMap`, every target id has a handler). `verifyNoPlatformNames` passes after the messenger-label compliance fix.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [~] Settings group «Команды отправить файл в..» renders ten rows: registry is non-empty so `cardSendCommands.isVisible` is now true - visual ten-row confirm is a device-test item (deferred to the eventual `BlockNeedUserTest`).
- [x] `scripts/check_strings_localized.ps1 -KeyPrefix share_target_title` exits 0 (4 keys EN/RU/UK; messenger brand strings dropped for dynamic labels).
- [x] Dev log + `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Registry now exposes ten gated targets; the settings group auto-populates (existing `PlaybackSettingsFragment.buildSendCommandRows`). The `Map<String, ShareTargetHandler>` dispatch is ready for the menu. No menu UI exists yet - receivers are configurable but not invocable from a file surface.

---

## Rollback Plan

Revert phase commit(s) - registry returns to empty, settings group hides itself again (`cardSendCommands.isVisible = false`); handlers/strings dangle harmlessly.
