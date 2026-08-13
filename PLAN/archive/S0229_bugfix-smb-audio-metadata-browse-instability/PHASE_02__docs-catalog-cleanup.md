# Phase 02 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0229_bugfix-smb-audio-metadata-browse-instability.md`](../S0229_bugfix-smb-audio-metadata-browse-instability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 3 / 4
**Started:** —
**Completed:** —

---

## Objective

Post-code sweep: catalog sync, changelog, functionality log, and device-side acceptance verification.

`docs/FEATURES*.md` is NOT touched — strategic §8 mandates "Без изменений".

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Project compiles for at least `standardDebug`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | (auto) |
| `dev/CATALOG/app_v2.md` | Regenerated | (auto) |
| `dev/CHANGELOG.md` | Appended | (auto via script) |
| `dev/FUNCTIONALITY.log` | Appended | (auto via script) |

---

## Steps

### Step 02.1 — Catalog regeneration

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Action:**

> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> `AudioMetadataLoader` is an existing class — no `set.ps1` call needed unless `role` was previously unset.

**Verification:**

- `scan.ps1` and `render.ps1` both exit 0.
- expected: exit 0 | actual: scan 1329 records, render 1329 records. PASS.

**Status:** `[x]` done — 2026-05-16.

---

### Step 02.2 — Dev changelog

**File:** `dev/CHANGELOG.md`

**Action:**

> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt" "AudioMetadataLoader" "S0229: reduce semaphore 3→2, downgrade EOFException/IOException to debug, wrap future.get() with runInterruptible"
> ```

**Verification:**

- `Grep -Pattern "S0229"` in `dev/CHANGELOG.md` → at least one entry.
- expected: S0229 present | actual: 6 entries in CHANGELOG.md. PASS.

**Status:** `[x]` done — 2026-05-16.

---

### Step 02.3 — Functionality log

**File:** `dev/FUNCTIONALITY.log`

**Action:**

> ```powershell
> .\scripts\add_to_functionality_log.ps1 -Id S0229 -Op FIX -Description "Eliminate Handler-on-dead-thread and EOFException warning noise in browse-side SMB audio metadata path; reduce concurrent fetch semaphore 3→2."
> ```

**Verification:**

- `Grep -Pattern "S0229"` in `dev/FUNCTIONALITY.log` → at least one `FIX` entry.
- expected: S0229 FIX present | actual: present at line 52. PASS.

**Status:** `[x]` done — 2026-05-16.

---

### Step 02.4 — Device acceptance (MANUAL)

**Files:** —

**Action:**

> On a device/emulator connected to an SMB share with audio files:
>
> 1. Open browse → navigate to SMB folder containing MP3/FLAC files.
> 2. Scroll through the list, then stop (idle).
> 3. Wait 5–10 seconds for background metadata enrichment.
> 4. Open `logs/current.log` or `adb logcat`.
>
> Verify:
> - NO `Handler on a dead thread` lines in the metadata path during normal browse.
> - NO `AudioMetadataLoader: Media3 MetadataRetriever failed on 65536 bytes` at warning level (if EOFException is the cause, it should be at debug level now).
> - Frame timing stays below 100 ms during scroll-idle metadata load burst.
> - artist/title still appear in visible rows (metadata enrichment still works).

**Verification:**

- `Grep` in `logs/current.log` — `Handler on a dead thread` ABSENT during browse-metadata enrichment sequence.
- `Grep` — `AudioMetadataLoader: Media3 MetadataRetriever failed` (warning) lines for EOFException/IOException: ABSENT or significantly reduced.
- Record: `expected: no Handler on dead thread / no EOFException warning | actual: <value from device run>`.

**Status:** `[manual — deferred to human]` — requires device with SMB audio folder.

---

## Phase Done Criteria

- [ ] Steps 02.1..02.3 all `[x] done`.
- [ ] Step 02.4 is `[manual — deferred to human]` — device run deferred to `BlockNeedUserTest` operator test.

---

## Revision History

- **2026-05-16** — Initial phase authored by `/spec-all`.
