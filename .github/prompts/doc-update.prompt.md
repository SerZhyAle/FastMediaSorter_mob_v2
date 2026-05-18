---
mode: agent
description: "Use when: updating documentation files after implementing a feature, syncing EN/RU/UK doc mirrors, ensuring FEATURES.md/TECH_STACK.md/ARCHITECTURE.md are up to date, or asked to run /doc-update command."
---

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

**1 - Determine scope.**

Use `$ARGUMENTS` as the feature/change description, or run `git diff --name-only HEAD~1 HEAD` and read changed files briefly.

**2 - Work through the checklist below in order.**

Decide per item: *affected* or *not affected*. Apply every affected update. Skip non-affected items silently.

If a repo documentation/helper script required for the update is broken or insufficient, fix the script first instead of working around it.

**3 - Run dev log for every file modified** (mandatory):

```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<class_or_target>" "<short_description>"
```

**Chat output:** table of every document - `Updated` or `Skipped (reason)`.

---

## Documentation Checklist

### A - Always Required

#### A1. `dev/CHANGELOG.md`

**When:** After every code or config change - no exceptions.
**How:** Run the script once per modified file. Never edit directly.

```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<ClassName or target>" "<one-line description>"
```

---

### B - User-Facing Features

A change is "user-facing" if it adds, removes, or materially alters something the end user can see or do.

#### B1. `docs/FEATURES.md` (English)

Add or update a bullet in the relevant numbered section. Style: `**Feature name**: plain-English description.`

#### B2. `docs/FEATURES_RU.md` (Russian) · B3. `docs/FEATURES_UK.md` (Ukrainian)

Mirror the B1 change. Identical section numbers and bullet layout.

---

### C - String Resources

#### C0. `docs/COMMUNICATION_POLICY.md` - tone check (user-visible strings)

**When:** Any new or updated user-visible string (toast, dialog, error, empty state, progress, CTA, confirmation).
**How:** Verify the string satisfies §2 (message-type formula) and §6 (tone checklist) of the policy before committing. If it fails any checklist item, rewrite first.

#### C1. `app_v2/src/main/res/values/strings.xml` (English)

For single-key updates, prefer `pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "<key>" -Value "<text>"`. Use manual XML edits only for `plurals`, `string-array`, comments, regrouping, or bulk rewrites.

#### C2. `values-ru/strings.xml` (Russian) · C3. `values-uk/strings.xml` (Ukrainian)

Mirror the same key with locale-specific values. For single-key updates, prefer `scripts/utils/set-android-string.ps1` once per locale. All three files are mandatory on every new string.

---

### D - Architecture & Tech Docs

#### D1. `docs/ARCHITECTURE.md`

**When:** New layer, module, major class, or data-flow path. Not for minor fixes or internal refactors.
**How:** Update relevant diagram/section at C4 level - components and responsibilities, not signatures.

#### D2. `docs/TECH_STACK.md`

**When:** New third-party library or major version bump.
**How:** Add/update entry: name, version, purpose, integration notes.

#### D3. `dev/TECH_REQUIREMENTS.md`

**When:** Same as D2, or when platform constraints change (minSdk, compileSdk, Java, Kotlin).

#### D4. `dev/PROJECT_OPERATIONS_INDEX.md`

**When:** New module, new feature path, or new fast-command.
**How:** Add/update relevant section. One line per item.

#### D5. `app_v2/build.gradle.kts`

**When:** Adding dependencies, SDK version changes, new flavors, new `BuildConfig` fields.
**How:** Edit relevant block. New dependencies must appear in `gradle/libs.versions.toml` first.

#### D6. `AppDatabase.kt` (Room schema version)

**When:** Any Room entity added, removed, or column changed.
**How:** Increment `version` in `@Database`. Add `Migration` object. Never rename old migrations.

---

### E - Spec Files

#### E1. `PLAN/spec_<name>.md`

**When:** Before implementing any non-trivial feature (Tier 2+).
**How:** Use `/spec` - never write manually. After implementation, advance `Status` to `Implemented`.

---

### F - User Help Docs

Update only sections directly relevant to the change.

#### F1. `docs/HOW_TO.md` + `_RU` + `_UK`

**When:** New multi-step workflow available to the user.

#### F2. `docs/FAQ.md` + `_RU` + `_UK`

**When:** Feature likely to generate user questions.

#### F3. `docs/TROUBLESHOOTING.md` + `_RU` + `_UK`

**When:** New known failure mode introduced, or bug fix addresses a previously documented issue.

#### F4. `docs/LIMITATIONS.md` + `_RU` + `_UK`

**When:** Feature has a known user-facing constraint.

#### F5. `docs/QUICK_START.md` + `_RU` + `_UK`

**When:** Initial setup or onboarding flow changes.

#### F6. `README.md` (root)

**When:** Major new feature changes top-level value proposition, or key capability removed.

#### F7. `docs/README.md` + `_RU` + `_UK`

**When:** Same as F6, or user-facing overview needs updating.

---

### G - Website / Landing Pages

#### G1. `index.html` · G2. `index-ru.html` · G3. `index-uk.html`

**When:** New major feature added to "Key Features" list, or existing removed/renamed.
**How:** Add `<li>` entry (≤10 words). Mirror in all three files. Do not change layout or SEO meta.

---

### H - Wear OS

#### H1. `docs/WEAR_OS_STATUS.md`

**When:** Any change to the `wear/` module. Update validation status row.

#### H2. `docs/WEAR_OS_ROADMAP.md`

**When:** Planned Wear feature implemented or new one planned.

---

### I - Documentation Map

#### I1. `docs/DOCS_MAP.md`

**When:** Any new `.md` documentation file created. Add row: link, one-line description, date.

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
