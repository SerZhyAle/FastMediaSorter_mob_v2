# Phase 03 - Localization tooling

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Teach the string tooling and the parity check to read the locale set from `locales_config.xml`, with two levels of strictness: complete for `en`/`ru`/`uk`, best-effort for the rest.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `locales_config.xml` declares all thirteen locales.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/locale-set.ps1` | New | ≤ 80 |
| `scripts/utils/set-android-string.ps1` | Modified | ≤ 1200 |
| `scripts/check_strings_localized.ps1` | Modified | ≤ 400 |

---

## Steps

### Step 03.1 - One reader for the locale set

**Files:** `scripts/utils/locale-set.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small dot-sourceable script exposing two functions: one returning every locale tag declared in `app_v2/src/main/res/xml/locales_config.xml`, one returning the strict subset (`en`, `ru`, `uk`) that must stay complete. Parse the XML, do not restate the list. Declare the exit codes the script returns in its header and use `Write-Error -ErrorAction Continue` before any non-1 exit (CLAUDE.md Rule 7 / S1070).

**Verification:**

- `Glob` - `scripts/utils/locale-set.ps1` exists.
- `pwsh -NoProfile -Command ". ./scripts/utils/locale-set.ps1; (Get-SupportedLocales).Count"` prints 13.

**Status:** `[x] done`

---

### Step 03.2 - Unlock the string tool

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `ValidateSet('en','ru','uk')` on `-Locale` and the hardcoded `$locales` array with values from `locale-set.ps1`, validating the argument against the declared set at runtime instead. `-Action add` must keep enforcing parity across the strict trio (`-En -Ru -Uk` stay mandatory) and additionally accept optional per-locale values for the other ten; a missing optional locale is not an error (strategic ADR-6). Keep the file byte-preserving - this tool exists precisely because hand-editing the XML corrupts it.

**Verification:**

- `Grep` - `ValidateSet('en', 'ru', 'uk')` returns zero hits in the file.
- `Grep` - `locale-set.ps1` is dot-sourced at least once.
- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action list -Key app_name` exits 0.

**Status:** `[x] done`

---

### Step 03.3 - Two-level parity gate

**Files:** `scripts/check_strings_localized.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Make the check read the same locale set and apply two strictness levels: a key missing from `en`, `ru` or `uk` is an error and fails the run; a key missing from any other declared locale is reported as a count and does not fail. Running it with no `-KeyPrefix` must audit every key rather than nothing, so the gate becomes usable from `post-change.ps1` on any string change. Document both exit codes in the header.

**Verification:**

- `Grep` - `locale-set.ps1` is dot-sourced at least once.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` exits 0 or 1 with a summary line naming both strictness levels (0 expected once S1193 closes its 21 gaps; a non-zero here is that ticket's debt, not this one's).

**Status:** `[x] done`

---

## Step Log

- 2026-07-27 - Steps 03.1-03.3 executed. Backup: `temp/S1190/set-android-string_20260727_200824.ps1`.
- 2026-07-27 - 03.1 evidence: `. ./scripts/utils/locale-set.ps1; (Get-SupportedLocales).Count` -> `13`; `Get-LocaleResourceDir -Tag 'zh-Hans'` -> `values-b+zh+Hans` (the `values-zh-Hans` spelling Android rejects).
- 2026-07-27 - 03.2 design note: the tool now carries **two** locale sets rather than one widened list. `$locales` stays the strict trio, because `move`/`add` demand the key in every entry - widening it to thirteen would have made every `move` fail for a language nobody has translated yet. `$optionalLocales` holds the declared languages whose `values-XX` directory exists, and `remove`/`rename`/`get`/`list`/`audit` sweep both so a deleted key leaves no orphan behind. Optional values arrive through `-Translations @{ de = '..' }` - a per-locale switch per language would have restated the list this phase exists to remove.
- 2026-07-27 - 03.2 evidence: `-Action list -Key app_name` exit 0; `-Action get -Key app_name` exit 0 (EN/RU/UK all found); `-Action set -Locale ja ..` throws `Unknown locale 'ja'. Declared locales: en, zh-Hans, hi, ..`; `-Action set -Locale RU .. -DryRun` exit 0, so the case-insensitive resolution reaches `values-ru`.
- 2026-07-27 - 03.2 caught by the smoke test: under `Set-StrictMode -Version Latest`, indexing an empty match with `[0]` raised "Index was outside the bounds of the array" and masked the real "unknown locale" message. Uses `Select-Object -First 1`.
- 2026-07-27 - 03.3 evidence: `check_strings_localized.ps1 -KeyPrefix language` -> `OK: all 9 key(s) present in en/ru/uk`, exit 0; with no prefix -> `Keys matching '*': 4419`, `OK`, exit 0. The strategic note expected 21 strict gaps from S1193 here; `main` has none, so those gaps live in another source set and remain that ticket's business.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1 -ChangeType Script`.
- [x] Phase-boundary audit run - script-layer only, so Layer 1 (readability, exit contract) applies; no runtime layers involved.

---

## Handoff Notes to Next Phase

Adding a locale to `locales_config.xml` now propagates to the string tool and the parity check without editing either. Phase 06 can seed new `values-XX` files through the byte-preserving tool instead of by hand.

---

## Rollback Plan

Revert the phase commit - scripts only, no shipped artifact changes.
