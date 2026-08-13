# Phase 01 - Receiver icon assets

**Strategic spec:** [`../S0478_send-to-menu-icons.md`](../S0478_send-to-menu-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Create the six new neutral-named vector drawables used as per-receiver glyphs (three logical, three brand analogs); no code wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_send_email.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_send_note.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_send_note_brush.xml` | New | ≤ 25 |
| `app_v2/src/main/res/drawable/ic_send_plane.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_send_chat.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_send_camera.xml` | New | ≤ 25 |

> Drawables are not layouts - the landscape-parity rule does not apply. No `res/layout*` files touched here.

---

## Steps

### Step 01.1 - Draw the six receiver glyphs

**Files:** `app_v2/src/main/res/drawable/ic_send_email.xml`, `ic_send_note.xml`, `ic_send_note_brush.xml`, `ic_send_plane.xml`, `ic_send_chat.xml`, `ic_send_camera.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Author six `24dp` `<vector>` drawables, each recognizable at menu-row size by silhouette alone. Map: `ic_send_email` = envelope; `ic_send_note` = sticky/lined note (Keep text); `ic_send_note_brush` = note with a pencil/brush stroke (Keep drawing); `ic_send_plane` = paper plane (messenger analog); `ic_send_chat` = rounded speech bubble (messenger analog); `ic_send_camera` = camera in a rounded square (social analog). Match the existing `app_v2/src/main/res/drawable/ic_*.xml` convention for `viewportWidth/Height` and `fillColor` so they tint like sibling icons (read `ic_share.xml` / `ic_print.xml` first and follow the same `fillColor` form - do not hardcode a new colour scheme). Names describe purpose, never a brand: do not introduce `ic_instagram`/`ic_telegram`/`ic_whatsapp` or any denylisted token.

**Verification:**

- `Glob` - all six files exist under `app_v2/src/main/res/drawable/`.
- `Grep` - `<vector` matches once in each of the six files.
- `Grep` - `android:fillColor` present in each of the six files.
- `Grep` (denylist guard) - case-sensitive search for `Instagram`, `Telegram`, `WhatsApp` across the six files returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (6× `<vector`, 6× `fillColor`, denylist guard 0 hits). Files: ic_send_email/note/note_brush/plane/chat/camera.xml (New). Material silhouettes, `?attr/colorControlNormal` tint per ic_share convention.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every new drawable via `.\scripts\add_to_dev_log.ps1`.
- [ ] No public Kotlin API changed - catalog regen deferred to Phase 03.

---

## Handoff Notes to Next Phase

Six drawable resource ids now exist (`R.drawable.ic_send_email`, `ic_send_note`, `ic_send_note_brush`, `ic_send_plane`, `ic_send_chat`, `ic_send_camera`). Phase 02 references them as `iconRes` values; reuse of existing `ic_print` / `ic_google_lens` / `ic_share` / `ic_open_in_browse` needs no new asset.

---

## Rollback Plan

Delete the six new drawables - no code references them yet, no build surface depends on them.
