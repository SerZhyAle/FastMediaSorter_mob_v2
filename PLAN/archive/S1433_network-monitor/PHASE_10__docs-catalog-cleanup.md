# Phase 10 - Docs and catalog cleanup

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked - requires on-device verification
**Depends on:** all phases
**Blocks:** none - final phase

---

## Objective

Regenerate every derived document the feature touches, record the capability, and align the privacy surfaces with what the code now does.

---

## Prerequisites

- [x] Phases 01 to 09 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FLAVOR_MATRIX.md` | Regenerated | - |
| `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json` | Regenerated / Modified | - |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 10 |
| `docs/PRIVACY*.md` and the Play Data Safety note | Modified | ≤ 40 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended via script | - |

---

## Steps

### Step 10.1 - Regenerate the flavor matrix

**Files:** `docs/FLAVOR_MATRIX.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/docs/generate-flavor-matrix.ps1` and then `pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1`. Never hand-edit the table.

**Why:**

The matrix is generated from `productFlavors` and is the only correct answer to which flavor has the Monitor, and S1392 recorded that a hand-written summary of it went stale and misled four documents.

**Verification:**

- `Grep` - `SUPPORT_NETWORK_MONITOR` row present with `[+]` in the standard and noLegal columns.
- `scripts/quality/assert-flavor-matrix-docs.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 10.2 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the settings manifest and reference, and add the annotation for both new settings. Register the in-section track switch in `SettingsDocScopeCatalog`, not in `SettingsSearchLayoutCatalog`.

**Why:**

CLAUDE.md Rule 22 requires the regeneration for any setting including one hosted outside a settings screen, and S1035/S1313 record that a non-screen surface registered in the search catalog is the wrong catalog.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - exit 0.
- `Grep` - both setting keys present in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done

---

### Step 10.3 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `scripts/all_features/add.ps1` describing the Network Monitor in English, naming the flavors from `docs/FLAVOR_MATRIX.md` rather than from memory. Do not edit `docs/FEATURES*.md`.

**Why:**

Strategic §8 requires the capability record, and CLAUDE.md §11 reserves the public showcase files for `/skill-release`, which generates them from the diff of this inventory.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `Grep` - `docs/FEATURES.md` unchanged by this phase.

**Status:** `[x]` done

---

### Step 10.4 - Align the privacy surfaces

**Files:** `docs/PRIVACY*.md` and the Play Data Safety note
**Depends on:** - start of phase

**Prompt for developer:**

> State in the privacy documents that the app can record a GNSS track on the device when the user turns that setting on, that the track never leaves the device, and that the external-IP check sends a request to a third-party service on explicit user action. Update the Play Data Safety answer for location accordingly, in the same change as the text.

**Why:**

Strategic §11 criterion 10 requires the setting text, the privacy policy and the Play form to say the same thing, and the canon's hard invariant 14 makes a divergence a release blocker rather than a documentation debt.

**Verification:**

- `Grep` - the track and the external-IP sentences present in every locale of the privacy document.
- The Data Safety answer names location as collected-and-not-shared, processed on device.

**Status:** `[x]` done - privacy documents align. The Play Console Data Safety answer has no repository artifact; release-console review is deferred with the device check.

---

### Step 10.5 - Remove the debug probes

**Files:** every `.kt` touched by phases 01 to 09
**Depends on:** - start of phase

**Prompt for developer:**

> If the ticket entered `BlockNeedUserTest` during implementation, the `Timber.d("S1433: ..")` probes were added then. They stay until `/spec-check` sets the ticket `Verified`; at that point delete every one of them in the same change as the status flip.

**Why:**

CLAUDE.md §2 makes the probe tag exist if and only if the ticket is in `BlockNeedUserTest`, and a permanent log line carrying a ticket id fails the ticket-log gate.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-ticket-log.ps1` - exit 0 once the ticket leaves `BlockNeedUserTest`.

**Status:** `[manual - deferred]` temporary S1433 probes remain until the required on-device verification can promote the ticket.

---

### Step 10.6 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once, then set `role` and `status` for the new classes through `dev/CATALOG/scripts/set.ps1`.

**Why:**

The catalog is the project's first lookup step for any Kotlin class, and a feature of this size adds around thirty classes that would otherwise be invisible to every later search.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*NetworkMonitor*"` returns the new classes.

**Status:** `[x]` done

---

### Step 10.7 - Close the ticket mechanically

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 10.1 to 10.6

**Prompt for developer:**

> Run `scripts/post-change.ps1` with the whole changed set and `-ChangeType Mixed`, read its verdict, and only then advance the spec status. A `ChangeType Kotlin` close would skip the doc-pin gate that this feature's regenerated documents depend on.

**Why:**

CLAUDE.md §12 makes the facade the mechanical closure, and its verdict is the evidence the ticket is finished rather than a self-report.

**Verification:**

- `post-change: PASS` printed, exit 0.

**Status:** `[x]` done - `post-change: PASS (Mixed, 104944 ms)`.

---

## Phase Done Criteria

- [ ] Every `Step 10.*` above is `[x] done`.
- [ ] `/spec-check S1433` returns `Verified`.
- [ ] No `S1433:` string remains in any `.kt` file.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. S1440 and S1441 build on what this ticket registered.
Before verification, connect an online device and confirm that disabling the setting removes the Monitor
from Programs, the launch panel and launcher desktop; then review the matching Play Console Data Safety
answer. Keep the temporary `S1433:` Timber probes until that check advances the journal from
`BlockNeedUserTest`.

---

## Rollback Plan

Documentation only - regenerate from source of truth rather than reverting by hand.
