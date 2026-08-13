# Phase 04 - Structural validation on install + payload size in the offer

**Strategic spec:** [`../S1483_artwork-delivery-without-build-pins.md`](../S1483_artwork-delivery-without-build-pins.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 0 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/PayloadIntegrityVerifier.kt` | Modified | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/delivery/PayloadIntegrityVerifierTest.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamAtlasPromptManager.kt` | Modified | ≤ 140 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3 keys |

---

## Steps

### Step 04.1 - Validate a tile pack structurally instead of by hash

**Files:** `PayloadIntegrityVerifier.kt`, `PayloadIntegrityVerifierTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> For an unpinned `.zip` payload, additionally require that the archive opens, holds at least one
> entry, and that every entry name is a plain decimal integer - the tile-pack container contract.
> Reject otherwise, with the existing failure result. Test a healthy pack, a truncated file, and a
> zip whose entries are named like ordinary files.

**Why:**

Dropping the hash removes the only check that a downloaded pack is what it claims to be; the
strategic spec replaces it with the structural check the catalog import already relies on, so a
truncated download cannot overwrite working artwork.

**Verification:**

- `.\a.ps1 fu --tests "*PayloadIntegrityVerifierTest*"` passes.

**Status:** `[x]` done

---

### Step 04.2 - Name the download size in the offer

**Files:** `StreamAtlasPromptManager.kt`, `strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Append the payload size to the offer dialog's message when the manifest supplies one, formatted in
> whole megabytes. Add the string in EN/RU/UK via `set-android-string.ps1 -Action add`.

**Why:**

The two payloads are 8 MB and 11 MB and currently start downloading with no indication of size,
which the strategic spec calls out as the second thing the manifest makes cheap to fix.

**Verification:**

- `scripts/check_strings_localized.ps1 -KeyPrefix streams_atlas_prompt` exits 0.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` passes; new tests green.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.
