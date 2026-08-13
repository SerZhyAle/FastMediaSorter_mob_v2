# Phase 09 - User Documentation (HOW_TO + FAQ, site build)

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Descoped 2026-07-18 - moved to S1102
**Depends on:** Phase 08
**Blocks:** - (no longer blocks Phase 10)
**Steps done:** - (moved)
**Started:** -
**Completed:** -

> **MOVED 2026-07-18 (owner decision).** End-user documentation is extracted into its own ticket **S1102** (`PLAN/S1102_launcher-mode-user-docs.md`) and executed after the launcher refinements (S1087-S1101) and owner UX sign-off. The full scope below is preserved verbatim in S1102 §0. Do not execute this phase from within S0404.

---

## Objective

Owner addition 2026-07-17: full end-user documentation for launcher mode in the docs that feed the public site build - a HOW_TO chapter and FAQ entries, EN/RU/UK. Target audience is non-technical (grandma-and-gym-goer rule): zero jargon, outcome-first, every step a tap.

---

## Prerequisites

- [ ] Phase 08 is ✅ Done (strings and settings paths are frozen).
- [ ] Read `docs/COMMUNICATION_POLICY.md` and an existing HOW_TO chapter for structure/voice before writing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO.md` | Modified | +60 |
| `docs/HOW_TO_RU.md` | Modified | +60 |
| `docs/HOW_TO_UK.md` | Modified | +60 |
| `docs/FAQ.md` + `docs/FAQ_RU.md` + `docs/FAQ_UK.md` | Modified | +15 each |
| `docs/DOCS_MAP.md` | Modified (freshness dates) | +2 |

---

## Steps

### Step 09.1 - HOW_TO chapter (EN, then RU/UK mirrors)

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add chapter "Use the app as your device's home screen (launcher mode)" covering, in this order: what the mode is (one paragraph, Windows-desktop analogy); turning it on (Settings path AND the Welcome toggle); the system Always/Just-once chooser; the desktop - cells, gadgets, taskbar, Start menu; editing the desktop (explicit edit mode, add/move/remove, pin apps); the two independent orientations; grid density; turning the mode off and returning to the previous launcher (both paths: Start menu exit and Settings); a "device won't keep the choice after reboot" note for quirky vendors (strategic risk 1, honest limitation). Every settings navigation line uses the `→` path format matching the exact names in `docs/settings/settings-manifest.json` - the HOW_TO settings-path gate (S0558) validates these against the manifest and requires EN/RU/UK parity per path line. Prose rules: `..` not `...`, plain hyphen, RU with ё. RU/UK are translations of the final EN, not summaries.

**Verification:**

- `Grep` - the new chapter heading present in all three HOW_TO files.
- HOW_TO settings-path gate script (see `scripts/quality/` - the S0558 gate) → exit 0.

**Status:** `[ ]` not done

---

### Step 09.2 - FAQ entries (EN/RU/UK)

**Files:** `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** Step 09.1

**Prompt for developer:**

> Add three Q&A entries mirroring existing FAQ voice: "How do I stop the app being my home screen?" (both exits + system home settings); "Why did my tablet go back to its old home screen after a restart?" (vendor firmware behavior, not an app bug - suggest re-selecting Always); "Can I put my own folders and playlists on the desktop?" (yes - edit mode summary + HOW_TO link). Same trilingual parity discipline.

**Verification:**

- `Grep` - "home screen" matches in all three FAQ files' new entries (RU/UK localized equivalents present).

**Status:** `[ ]` not done

---

### Step 09.3 - DOCS_MAP freshness + doc gates

**Files:** `docs/DOCS_MAP.md`
**Depends on:** Steps 09.1-09.2

**Prompt for developer:**

> Update the last-edit dates for HOW_TO and FAQ rows in `docs/DOCS_MAP.md` (site publish status stays `Current`). Close the phase through `scripts/post-change.ps1 -ChangeType Doc` per file batch so the doc gates (ticket-log, howto-settings-paths) run.

**Verification:**

- `post-change.ps1` → PASS for the doc batch (exit 0, gates green).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 09.*` above is `[x] done`.
- [ ] HOW_TO path gate exit 0; EN/RU/UK parity confirmed.
- [ ] Dev log entries added.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. FEATURES showcase (site "Features" page) is intentionally NOT edited here - `/skill-release` derives it from the Phase 10 ALL_FEATURES record.

---

## Rollback Plan

Revert doc commit(s) - no code impact.
