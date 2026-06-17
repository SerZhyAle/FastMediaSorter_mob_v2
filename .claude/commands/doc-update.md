# Documentation Update

Review the current change and update all affected documentation files.

## Usage

```text
/doc-update [optional: brief description of what was implemented]
```

- `/doc-update Chromecast cast integration`
- `/doc-update` - infer scope from `git diff --name-only HEAD~1 HEAD`

---

## Process

**1 - Determine scope.** Use `$ARGUMENTS` as the change description, or run `git diff --name-only HEAD~1 HEAD` and read changed files briefly.

**2 - Work the checklist below in order.** Decide per item *affected* / *not affected*. Apply every affected update; skip non-affected silently. If a required repo doc/helper script is broken or insufficient, fix the script first instead of working around it.

**3 - Run dev log for every file modified** (mandatory):

```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<class_or_target>" "<short_description>"
```

**Chat output:** table of every document - `Updated` or `Skipped (reason)`.

---

## Documentation Checklist

### A - Always Required

#### A1. `dev/CHANGELOG.md`

**When:** after every code or config change - no exceptions.
**How:** run the script once per modified file. Never edit directly.

```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<ClassName or target>" "<one-line description>"
```

---

### B - User-Facing Features

"User-facing" = adds, removes, or materially alters something the end user can see or do.

**Two artifacts, two roles (S0489):** `docs/ALL_FEATURES.jsonl` is the EN-only developer inventory of every implemented capability (one record per capability, written via `scripts/all_features/add.ps1`); `docs/FEATURES*` is the curated public showcase published to the site. Record every shipped capability in `ALL_FEATURES`. Do NOT add per-feature bullets to `FEATURES*` ad hoc - that showcase is populated only by `/skill-release` from the inventory diff since the previous release. The B1-B3 steps below apply when `/skill-release` (or an explicit owner request) promotes a standout capability into the showcase.

**noLegal exception:** `noLegal`-exclusive features go into `docs/FEATURES_noLegal.md` + `_RU` + `_UK` (gitignored). NEVER added to public `docs/FEATURES*.md`; public entries are never copied into `_noLegal`. Routing: `BuildConfig.IS_NO_LEGAL_FLAVOR` the sole gate → use B-noLegal; otherwise B1–B3.

#### B1. `docs/FEATURES.md` (English)
Add/update a bullet in the relevant numbered section. Style: `**Feature name**: plain-English description.`

#### B2. `docs/FEATURES_RU.md` (Russian) · B3. `docs/FEATURES_UK.md` (Ukrainian)
Mirror the B1 change. Identical section numbers and bullet layout.

#### B-noLegal. `docs/FEATURES_noLegal.md` + `_RU` + `_UK` (noLegal sideload-only)
**When:** new/changed capability gated exclusively by `BuildConfig.IS_NO_LEGAL_FLAVOR == true`.
**How:** add a section under `## noLegal-Only Features` using the file template. Mirror in all three locale files. Never touch B1–B3 for this class of change.

---

### C - String Resources

#### C0. `docs/COMMUNICATION_POLICY.md` - tone check (user-visible strings)
**When:** any new/updated user-visible string (toast, dialog, error, empty state, progress, CTA, confirmation).
**How:** verify §2 (message-type formula) + §6 (tone checklist) before committing. Fails any item → rewrite first.

#### C1. `app_v2/src/main/res/values/strings.xml` (English)
Single-locale value updates: prefer `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "<key>" -Value "<text>"`. Manual XML edits only for `plurals`, `string-array`, comments, regrouping, bulk rewrites.

#### C2. `values-ru/strings.xml` (Russian) · C3. `values-uk/strings.xml` (Ukrainian)
Mirror the same key with locale-specific values. All three files mandatory on every new string. Brand-new key: prefer one lockstep call `scripts/utils/set-android-string.ps1 -Action add -Key "<key>" -En "<text>" -Ru "<text>" -Uk "<text>"` (writes all three locales, parity-enforced) over editing each by hand. Per-locale wording fixes: `-Action set` once per locale.

---

### D - Architecture & Tech Docs

#### D1. `docs/ARCHITECTURE.md`
**When:** new layer, module, major class, or data-flow path. Not for minor fixes/internal refactors.
**How:** update relevant diagram/section at C4 level - components + responsibilities, not signatures.

#### D2. `docs/TECH_STACK.md`
**When:** new third-party library or major version bump.
**How:** add/update entry - name, version, purpose, integration notes.

#### D3. `dev/TECH_REQUIREMENTS.md`
**When:** same as D2, or platform constraints change (minSdk, compileSdk, Java, Kotlin).

#### D4. `dev/PROJECT_OPERATIONS_INDEX.md`
**When:** new module, new feature path, or new fast-command.
**How:** add/update relevant section. One line per item.

#### D5. `app_v2/build.gradle.kts`
**When:** adding dependencies, SDK version changes, new flavors, new `BuildConfig` fields.
**How:** edit relevant block. New dependencies must appear in `gradle/libs.versions.toml` first.

#### D6. `AppDatabase.kt` (Room schema version)
**When:** any Room entity added/removed or column changed.
**How:** increment `version` in `@Database`. Add `Migration` object. Never rename old migrations.

---

### E - Spec Files

#### E1. `PLAN/Sxxxx_<slug>.md`
**When:** before implementing any non-trivial feature (Tier 2+).
**How:** use `/spec` - never write manually. After implementation, advance `Status` to `Implemented`.

---

### F - User Help Docs

Update only sections directly relevant to the change.

#### F1. `docs/HOW_TO.md` + `_RU` + `_UK`
**When:** new multi-step workflow available to the user.

#### F2. `docs/FAQ.md` + `_RU` + `_UK`
**When:** feature likely to generate user questions.

#### F3. `docs/TROUBLESHOOTING.md` + `_RU` + `_UK`
**When:** new known failure mode introduced, or bug fix addresses a previously documented issue.

#### F4. `docs/LIMITATIONS.md` + `_RU` + `_UK`
**When:** feature has a known user-facing constraint.

#### F5. `docs/QUICK_START.md` + `_RU` + `_UK`
**When:** initial setup or onboarding flow changes.

#### F6. `README.md` (root)
**When:** major new feature changes top-level value proposition, or key capability removed.

#### F7. `docs/README.md` + `_RU` + `_UK`
**When:** same as F6, or user-facing overview needs updating.

---

### G - Website / Landing Pages

#### G1. `index.html` · G2. `index-ru.html` · G3. `index-uk.html`
**When:** new major feature added to "Key Features" list, or existing removed/renamed.
**How:** add `<li>` entry (≤10 words). Mirror in all three files. Do not change layout or SEO meta.

---

### H - Wear OS

#### H1. `docs/WEAR_OS_STATUS.md`
**When:** any change to the `wear/` module. Update validation status row.

#### H2. `docs/WEAR_OS_ROADMAP.md`
**When:** planned Wear feature implemented or new one planned.

---

### I - Documentation Map

#### I1. `docs/DOCS_MAP.md`
**When:** any new `.md` documentation file created. Add row: link, one-line description, date.

---

## Decision Matrix

| What changed | Updates required |
| --- | --- |
| Bug fix, no UI/feature change | A1 |
| Internal refactor | A1 |
| New dependency | A1, D2, D3, D5 |
| SDK / platform version change | A1, D3, D4, D5 |
| Room schema change | A1, D5, D6 |
| New string resource | A1, C1, C2, C3 |
| New user-facing feature | A1, B1–B3, F6, F7 + relevant F + G1–G3 |
| New feature + new strings | A1, B1–B3, C1–C3, F6, F7 + relevant F + G1–G3 |
| New `noLegal`-only feature | A1, B-noLegal (all 3 locale files) - do NOT touch B1–B3, G |
| New `noLegal` feature + new strings | A1, B-noLegal (all 3), C1–C3 - do NOT touch B1–B3, G |
| New architectural component | A1, D1, D4 |
| Wear module change | A1, H1, H2 |
| New doc file created | I1 |
| Initial setup flow changes | A1, F5 (all 3) |
| New user workflow | A1, F1 (all 3) |
| Known limitation discovered | A1, F4 (all 3) |

---

## Constraints

- Never edit `CHANGELOG.md` manually - always use the script.
- Never skip RU/UK when updating EN user-facing docs (B, C, F groups).
- Do not rewrite sections you are not updating - only touch affected parts.
- Do not create new doc files outside `docs/` or `dev/` unless explicitly instructed.
- "User-facing" rule: if a user would notice the difference in normal use, it is user-facing.
- Keep `docs/DOCS_MAP.md` in sync - any new file added must appear there.
