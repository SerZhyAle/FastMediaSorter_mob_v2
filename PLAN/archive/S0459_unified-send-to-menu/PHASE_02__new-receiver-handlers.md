# Phase 02 - New-receiver send handlers + package visibility

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Implement the send actions for the three receivers with no existing code - Email, WhatsApp, Instagram - as `ShareTargetHandler`s over the existing `SystemShareInvoker`, and declare the manifest package visibility they need on API 30+.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`ShareTargetHandler`, `ShareableContent` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/EmailShareTargetHandler.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/WhatsAppShareTargetHandler.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/InstagramShareTargetHandler.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/SystemShareInvoker.kt` | Modified | ≤ 200 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | - |

---

## Steps

### Step 02.1 - Add an email-extras overload to SystemShareInvoker

**Files:** `core/share/SystemShareInvoker.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an overload (or optional params) to `invokeFiles` accepting `emailAddresses: Array<String>? = null` and `subject: String? = null`; when present, put `Intent.EXTRA_EMAIL` / `Intent.EXTRA_SUBJECT` on the built intent. Do not change existing call sites' behaviour (defaults null). The attachment path stays `ACTION_SEND`/`ACTION_SEND_MULTIPLE` with `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION` - never `mailto:` (loses attachment), per research 05.

**Verification:**

- `Grep` - `EXTRA_EMAIL` present in `SystemShareInvoker.kt`.
- `Grep` - `Intent.ACTION_SENDTO` NOT introduced in `SystemShareInvoker.kt` (zero hits).
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (`EXTRA_EMAIL` line 74, zero `ACTION_SENDTO`, zero `Log.d`). Additive params default null; existing call sites unchanged. Files: core/share/SystemShareInvoker.kt. Dev log recorded.

---

### Step 02.2 - EmailShareTargetHandler

**Files:** `core/share/handlers/EmailShareTargetHandler.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class EmailShareTargetHandler @Inject constructor(...)` implementing `ShareTargetHandler` with `targetId = "email"`. `send` calls the Step 02.1 invoker with the content uris, MIME `message/rfc822` (bias toward mail apps), empty `EXTRA_EMAIL`, and a subject from `displayName`. Falls back to the system chooser when no mail app resolves. Applicability is any type (declared at registration in Phase 03).

**Verification:**

- `Glob` - `core/share/handlers/EmailShareTargetHandler.kt` exists.
- `Grep` - `class EmailShareTargetHandler` and `targetId` = `"email"` present.
- `Grep` - `message/rfc822` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (`class EmailShareTargetHandler` line 14, `"email"` line 29, `message/rfc822` line 30). Files: core/share/handlers/EmailShareTargetHandler.kt. Dev log + module gates batched with 02.3.

---

### Step 02.3 - WhatsApp + Instagram handlers

**Files:** `core/share/handlers/WhatsAppShareTargetHandler.kt`, `core/share/handlers/InstagramShareTargetHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `WhatsAppShareTargetHandler` (`targetId = "whatsapp"`, packages `com.whatsapp`, `com.whatsapp.w4b`) and `InstagramShareTargetHandler` (`targetId = "instagram"`, package `com.instagram.android`). Each implements `ShareTargetHandler.send` by calling `SystemShareInvoker.invokeFiles(..., preferredPackage = <firstInstalled>)` with chooser fallback. No programmatic recipient selection (no `jid` extra) - the app handles recipient, per research 04. Instagram only image/video/gif (gated at registration). Mirror `TelegramShareTargets.firstInstalledPackage` for package selection.

**Verification:**

- `Glob` - both handler files exist.
- `Grep` - `com.whatsapp` and `com.instagram.android` present in the respective files.
- `Grep` - `jid` NOT present (zero hits) in either file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (`com.whatsapp`+`com.whatsapp.w4b` in WhatsApp handler line 42, `com.instagram.android` in Instagram handler line 38, zero `jid` after comment reword). Files: core/share/handlers/WhatsAppShareTargetHandler.kt, InstagramShareTargetHandler.kt. Dev log recorded; module gates ran via batched post-change.

---

### Step 02.4 - Declare package visibility in the manifest

**Files:** `src/main/AndroidManifest.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> In the existing `<queries>` block (or add one), declare visibility for the new receivers on API 30+: `<package android:name="com.whatsapp"/>`, `com.whatsapp.w4b`, `com.instagram.android`, and an `<intent>` for `ACTION_SENDTO` `mailto` so mail clients resolve. Keep the existing Keep/Telegram entries intact.

**Verification:**

- `Grep` - `com.instagram.android` present in `src/main/AndroidManifest.xml`.
- `Grep` - `com.whatsapp` present in the manifest.
- `Grep` - `<queries>` present exactly once in the manifest.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (`<queries>` single at line 70, `com.whatsapp` line 118, `com.instagram.android` line 120, mailto scheme line 124). Justified extension: also added Telegram package visibility (5 ids) - the plan assumed pre-existing Telegram `<queries>` entries that did not exist, and Phase 03 `PACKAGE_INSTALLED` availability for Telegram requires them on API 30+. Files: src/main/AndroidManifest.xml. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` (code + resources, covers Kotlin handlers + manifest merge) BUILD SUCCESSFUL in 56s after killing two Android Studio daemons (no kapt recovery needed; incremental from the clean Phase 01 cache).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Existing `SystemShareInvoker` call sites unchanged in behaviour - new params default null and the build is green.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Three new handlers exist but are not yet bound or registered. Phase 03 binds them `@IntoMap` and registers their `ShareTarget` declarations with applicability + balanced defaults (ADR-7).

---

## Rollback Plan

Revert phase commit(s) - new handler files + additive invoker params + manifest `<queries>` entries; nothing consumes them yet.
