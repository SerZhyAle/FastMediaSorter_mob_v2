# Phase 03 - Scenario page: connect the watch to a network share

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Publish a step-by-step guide that connects the watch directly to a NAS or PC share and browses media on it, in English, Russian and Ukrainian, listed in both entrances of the step-by-step guide index.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] S1781 is `Verified` - the Wear main screen this guide opens on is final.
- [x] A reachable SMB or SFTP share exists on the same network as the watch surface used for the walkthrough.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/howto/scenario-watch-network.md` | New | ≤ 200 |
| `docs/howto/scenario-watch-network-ru.md` | New | ≤ 200 |
| `docs/howto/scenario-watch-network-uk.md` | New | ≤ 200 |
| `docs/howto/index.md` | Modified | ≤ 4 added lines |
| `docs/howto/index-ru.md` | Modified | ≤ 4 added lines |
| `docs/howto/index-uk.md` | Modified | ≤ 4 added lines |

---

## Steps

### Step 03.1 - Trace the network flow on the Wear surface

**Files:** `evidence/watch-network-walkthrough.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Drive the Wear surface through adding a network source, testing the connection, pinning the host key where the source type asks for one, opening the source at its configured base path, and filtering the listing by media type. Record every screen, every field label as the interface spells it, and every failure message the flow can produce, into `evidence/watch-network-walkthrough.md`.

**Why:**

Strategic §7 names "сценарий описан по плану, а не по работающему приложению" as a central risk, and network configuration on the watch has several branches - voice input versus keypad, key pinning, test connection outcomes - that must match what the watch actually displays rather than what the mobile app does.

**Verification:**

- `Glob` - `evidence/watch-network-walkthrough.md` exists.
- `Grep` - the file records at least one connection-failure message verbatim.

**Status:** `[x]` done

---

### Step 03.2 - Write the English scenario page

**Files:** `docs/howto/scenario-watch-network.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Write the page in the same genre shape Phase 02 used, with `permalink: /docs/howto/scenario-watch-network.html` and a language-switch line linking its `-ru` and `-uk` siblings. Cross-link the phone-side SMB guide `scenario-smb-setup.md` as the foundation for readers who have not set up a share yet, and cross-link the Wear music guide from Phase 02. Leave `<!-- TODO screenshot: .. -->` placeholders where wording alone is ambiguous. Check the copy against `docs/COMMUNICATION_POLICY.md` §2 and §6 and take interface terms from its §7 glossary. Write any navigation route as one route per line using ASCII `>`, never the arrow character.

**Why:**

Strategic ADR-1 puts Wear guides inside the existing genre precisely so a Wear page can lean on the phone-side guides that already explain a share, and §3.2 binds user-visible copy to the communication policy so the page names controls the way the interface does.

**Verification:**

- `Glob` - `docs/howto/scenario-watch-network.md` exists.
- `Grep` - `permalink: /docs/howto/scenario-watch-network.html` matches exactly once.
- `Grep` - `scenario-smb-setup.md` matches at least once.
- `Grep` - the arrow character U+2192 returns zero hits in the file.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 03.3 - Write the Russian and Ukrainian siblings

**Files:** `docs/howto/scenario-watch-network-ru.md`, `docs/howto/scenario-watch-network-uk.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Write both siblings in one edit, matching the English page step for step, placeholder for placeholder and troubleshooting row for row, with each file carrying its own `permalink` and its own language-switch line. Adapt wording naturally per `docs/COMMUNICATION_POLICY.md` §5.

**Why:**

Strategic §2.3 requires a working language switcher on every page produced by this ticket, and §7 names locale divergence in step composition as a risk mitigated by writing the three locales in a single change.

**Verification:**

- `Glob` - both files exist.
- The count of `## Step` headings is equal across the three locale files.
- The count of `<!-- TODO screenshot:` placeholders is equal across the three locale files.

**Status:** `[x]` done

---

### Step 03.4 - List the guide in both index entrances and re-run the recipe gate

**Files:** `docs/howto/index.md`, `docs/howto/index-ru.md`, `docs/howto/index-uk.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add the guide to each index file twice, as a question line and as an "All Guides" table row, keeping the entry in the same position in all three files and beside the Phase 02 entry. Then run `pwsh -NoProfile -File scripts/quality/assert-howto-settings-paths.ps1` over the whole guide set, because the gate counts recipes across every guide group and a new page shifts the positional signature it compares between locales.

**Why:**

Strategic §11.2 requires both index lists to carry the Wear guides, and §3.2 binds navigation routes to a gate that enforces positional parity across the three locales - a parity that is computed over all guides at once, so it can only be confirmed after the second page is in place.

**Verification:**

- `Grep` - `scenario-watch-network.md` matches exactly twice in `docs/howto/index.md`.
- `Grep` - `scenario-watch-network-ru.md` matches exactly twice in `docs/howto/index-ru.md`.
- `Grep` - `scenario-watch-network-uk.md` matches exactly twice in `docs/howto/index-uk.md`.
- `scripts/quality/assert-howto-settings-paths.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable: no source file is touched in this phase.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both Wear guides exist on three locales with equal placeholder counts per guide, and the index files carry four Wear entries in total. Phase 04 now has the complete set of screenshot requirements to specify and capture; Phase 05 has two pages to point the landing at.

---

## Rollback Plan

Delete the three new pages and revert the index change. No generated artifact and no source file is involved.
