# Phase 08 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

> **Impl notes (2026-06-21, `/spec-all`).** Catalog synced; roles/status set for all new public
> classes (FullStreamProtocolSupport carries `-NoFlavors lite,photos`). ProgressiveOnlyStreamProtocolSupport
> is not catalogued because the scan only includes the `streamingEnabled` bucket (the `streamingDisabled`
> twin would create duplicate same-named records) - by design, not a gap. ALL_FEATURES record added
> (`streaming.internet_streams`, validate green; also fixed a pre-existing malformed s0562 record that
> was failing validate). Gates green: neuroslop, settings-doc-sync. Four `Timber.d("S0565: ..")` probes
> inserted (screen open, stream playback start, inline-audio start, .m3u import done). Full standard
> debug APK built. Spec -> BlockNeedUserTest.

---

## Objective

Close the change set: regenerate the class catalog, record the delivered capability in `docs/ALL_FEATURES.jsonl`, run the mechanical quality gates, and transition the spec into device testing.

---

## Prerequisites

- [ ] Phases 01-07 ✅ Done.
- [ ] Working tree builds for standard, lite, and photos.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ +2 |

> `dev/CATALOG/*` are gitignored local indexes - regenerate, do not commit. No `docs/FEATURES*.md` edit (strategic §8 = no change until shipped; `/skill-release` populates the showcase).

---

## Steps

### Step 08.1 - Catalog sync + set roles for new public classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the new public classes via `dev/CATALOG/scripts/set.ps1`: `StreamSourceEntity`, `StreamSourceDao`, `StreamSourceRepository`, the five `*UseCase`s, `M3uPlaylistParser`, `StreamProtocolSupport`, `HttpStreamPlaybackHelper`, `StreamsActivity`, `StreamsViewModel`, `StreamSourceAdapter`, `StreamInlineAudioManager`. For the flavor-only impls, hint isolation: `FullStreamProtocolSupport` -> `set.ps1 -NoFlavors "lite,photos"`; `ProgressiveOnlyStreamProtocolSupport` -> `set.ps1 -NoFlavors "standard,legacy,noLegal,vr"`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*StreamSource*"` returns the new classes.
- `query.ps1 -ClassMatches "FullStreamProtocolSupport"` shows the noFlavors hint applied.

**Status:** `[x]` done (2026-06-21)

---

### Step 08.2 - Record capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 08.1

**Prompt for developer:**

> Add one EN-only record via `scripts/all_features/add.ps1` describing the delivered "Internet Streams (Трансляции)" capability: separate screen, manual URL add + remote `.m3u` import, local pin-to-top, inline radio with ICY metadata, fullscreen video, RTSP (TCP) on streaming flavors, progressive-only on lite, absent on photos. Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - a record mentioning "stream" / "Трансляции" present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done (2026-06-21)

---

### Step 08.3 - Mechanical quality gates

**Files:** (no edit - runs gates over the whole change set)
**Depends on:** Step 08.2

**Prompt for developer:**

> Run the bundled closure facade `pwsh -NoProfile -File scripts/post-change.ps1` over the change set (or run the individual gates): neuroslop gate, deprecated-PM-flags gate, settings-doc-sync gate, no-ticket-logs gate. Fix any violation in the touched files (no hex in layouts, no empty catch, no `Log.d`, no `Sxxxx` in persistent logs).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done (2026-06-21)

---

### Step 08.4 - Insert debug tags + transition to BlockNeedUserTest

**Files:** changed-flow entry points (playback dispatch, screen launch, import) + spec catalog
**Depends on:** Step 08.3

**Prompt for developer:**

> Per CLAUDE.md Debug Verification Tags, insert one `Timber.d("S0565: <entry description>")` at each changed-flow entry: (a) `StreamsActivity` open, (b) `playHttpStreamVideo` start (http vs rtsp), (c) inline-audio start, (d) `.m3u` import completion with inserted count. One tag per flow entry, not per line. Then build a full debug APK (`.\a.ps1 d`) and transition the spec: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0565 -Status BlockNeedUserTest -StatusNote 'Device-test on a streaming flavor (standard) and lite: open Трансляции, add a manual https radio URL (SomaFM) - inline audio + ICY metadata; add an HLS VOD .m3u8 - fullscreen video; add an rtsp:// URL - RTSP plays on standard, shows unsupported message on lite; import a remote .m3u; pin-to-top reorders; public http:// radio plays (cleartext); Back from video restores list. Confirm photos has no Трансляции entry.'`

**Verification:**

- `Grep` - `Timber.d("S0565:` matches exactly at the intended flow entries (one per entry, 4 total).
- `.\a.ps1 d` exits 0 (full debug APK).
- `select.ps1 -Id S0565 -Format json` shows `status: BlockNeedUserTest`.

**Status:** `[x]` done (2026-06-21)

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] Catalog regenerated for app_v2; new classes carry role/status (+ flavor hints).
- [ ] `docs/ALL_FEATURES.jsonl` records the capability; `validate.ps1` exits 0.
- [ ] All mechanical gates exit 0.
- [ ] Spec at `BlockNeedUserTest` with a device-test note; debug tags present (one per changed flow).
- [ ] Dev log entries added for the whole change set.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After device verification, `/spec-check S0565` removes the `S0565:` debug tags and advances the spec to `Verified`; `/skill-release` later pulls the ALL_FEATURES record into the public FEATURES showcase.

---

## Rollback Plan

Catalog regeneration and ALL_FEATURES are idempotent/append - no rollback needed. If device test fails, the spec stays `BlockNeedUserTest`; fix in the owning phase and rebuild.
