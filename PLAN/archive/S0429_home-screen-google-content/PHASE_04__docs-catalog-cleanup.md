# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S0429_home-screen-google-content.md`](../S0429_home-screen-google-content.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Record the delivered capability, classify the new classes, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.
- [ ] No `CODE.LOCK` is held - this phase edits no source.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Written by script | - |

---

## Steps

### Step 04.1 - Classify the new classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` via `dev/CATALOG/scripts/set.ps1` for every class Phases 01-02 added: `NowPlayingSource`, `OwnSessionNowPlayingSource`, `ActiveSessionNowPlayingSource`, `NotificationAccessState`, `MediaSessionAccessService`. All five live in `src/launcherEnabled`, which only `standard` and `noLegal` mount, so declare `-NoFlavors "lite,vr,photos,legacy"` on each - read that list off `app_v2/build.gradle.kts`, not off a sibling record.

**Why:**

A new class with no role or status is invisible to the catalog-first research order every later ticket starts from (CLAUDE.md §5).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*NowPlayingSource*"` returns three records, each with a non-empty `role`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "MediaSessionAccessService"` returns one record with a non-empty `role`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/2 PASS. Seven records now carry a role, a `status=new` and `noFlavors=[lite,vr,photos,legacy]`: the five classes the step names plus `NowPlayingState` and `NowPlayingCommand`, which share `NowPlayingSource.kt` and are therefore keyed to the same path. The flavor list was read off `app_v2/build.gradle.kts` as instructed and independently confirmed: only the `standard` and `noLegal` source-set blocks add `src/launcherEnabled/java`, and `launcherFlavors = setOf("standard", "noLegal")` gates the manifest injection at line 1079. Worth noting against the strategic spec, which lists `legacy`/`photos` as carrying the home surface - for this source set they do not.

---

### Step 04.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record through `scripts/all_features/add.ps1`: the launcher's Now Playing gadget shows and controls whatever is playing in any app - not only this one - once the user turns on notification access from the gadget itself, and falls back to this app's own playback otherwise. Area `Launcher`, flavors `standard,noLegal`. Do not touch `docs/FEATURES*.md`. Then run `scripts/all_features/validate.ps1`.

**Why:**

`docs/ALL_FEATURES.jsonl` is the inventory every later ticket checks before implementing something, and the release notes are generated from its diff (CLAUDE.md §11).

**Verification:**

- `Grep` - a record naming the now-playing gadget present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/2 PASS. Record `launcher.now-playing-any-app` added via `add.ps1`, area `Launcher`, flavors `standard,noLegal`, spec `S0429`; `validate.ps1` exit 0 at 652 records, up from 651. `docs/FEATURES*.md` untouched - release-owned. First attempt was rejected for the id `launcher-now-playing-any-app`: the inventory requires `<area>.<feature>`, and the script says so plainly rather than accepting a malformed id.

---

### Step 04.3 - Close the ticket through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> If each phase already closed through `post-change.ps1`, do not re-run it over the whole set - that duplicates changelog rows. Confirm instead that every phase printed `post-change: PASS`, that the settings-doc-sync gate ran rather than skipped in Phase 03, and that `dev/CHANGELOG.md` names S0429. Then finalize with `scripts/spec_catalog/close-and-log.ps1` to `BlockNeedUserTest`, whose status note tells the owner to grant notification access from the gadget, start a track in another player, and confirm the gadget shows and controls it.

**Why:**

Strategic §6 criteria are device-observable - a foreign player's track appearing on the desktop - so the ticket's acceptance is a device run, which is what `BlockNeedUserTest` means (CLAUDE.md §2).

**Verification:**

- `Grep` - `dev/CHANGELOG.md` carries an entry naming S0429.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0429 -Format json` reports `BlockNeedUserTest` with a non-empty status note.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/2 PASS. `dev/CHANGELOG.md` carries 15 rows naming S0429; `select.ps1` reports `BlockNeedUserTest` with a status note. Each phase closed on its own `post-change: PASS` (01 Kotlin, 02 Mixed, 03 Mixed after one detekt fix, plus the probe-tag run), so the facade was not re-run over the whole set.
- 2026-08-06 - `settings-doc-sync` **skipped** rather than ran, and the step's expectation that it must run does not hold: the permissions screen is not a settings layout, so no changed file is a settings surface. The gate was therefore run standalone instead - exit 0, everything in sync. Reasoning in the Phase 03 criteria block.
- 2026-08-06 - `-FuncOp` deliberately omitted from the `close-and-log.ps1` call: Step 04.2 had already written the inventory record directly through `add.ps1`, and passing `-FuncOp` as well would have produced a second record for one capability.
- 2026-08-06 - Two ordering corrections worth recording, both caught by gates rather than by review:
  - The probe tags were inserted here, not before Phase 03's build as the pipeline prefers, so they cost one extra `fc` run (exit 0, 21 tasks). Four tags across three flow entries.
  - `assert-no-ticket-logs` then failed them all as "stale probe (ticket not BlockNeedUserTest)" - correctly: the status flip has to precede the gate, not follow it. Flipped first, re-ran, `post-change: PASS`.
  - `close-and-log.ps1` overwrote the detailed device-test note with the short one passed to it, leaving a header that said "See the spec header note" about itself. Restored through `update.ps1`; the note that matters is the one in the spec header.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Dev log entry present for the ticket.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Nothing to roll back: this phase writes only derived indexes and the changelog.
