# Documentation Update

Review the current change and update **all** documentation files that are affected.

## Usage

```
/doc-update [optional: brief description of what was just implemented]
```

Example:
- `/doc-update Chromecast cast integration`
- `/doc-update background thumbnail preload worker`
- `/doc-update` (no argument — infer scope from recent git diff)

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Determine scope.**
- If `$ARGUMENTS` is provided, use it as the feature/change description.
- Otherwise run `git diff --name-only HEAD~1 HEAD` to identify what changed.
- Read the changed files briefly to understand what was modified.

**Step 2 — Work through the checklist below in order.**
For each documentation point, decide: *affected / not affected*. Apply every affected update. Skip with a reason for every non-affected item.

**Step 3 — Run the dev log command for every file you modify** (mandatory):
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<class_or_target>" "<short_description>"
```

**Step 4 — Output a summary table** of every document: `Updated / Skipped (reason)`.

---

## Documentation Checklist

### A — Always Required (every code change)

#### A1. `dev/CHANGELOG.md`
**When:** After every code or config change — no exceptions.
**How:** Run the script (do NOT edit the file directly):
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<ClassName or target>" "<one-line description>"
```
Call the script once per modified file. Use the file's package-relative path (e.g. `app_v2/src/main/java/...`).

---

### B — User-Facing Features (when a new capability is visible to the user)

A change is "user-facing" if it adds, removes, or materially alters something the end user can see or do.

#### B1. `docs/FEATURES.md` (English)
**When:** Any new or changed user-facing feature.
**How:** Add or update a bullet inside the relevant numbered section (1–22). Match the existing bullet style: start with `**Feature name**:` followed by a plain-English description of what it does and why it is useful. Do not add a new top-level section unless a genuinely new feature area is introduced; extend an existing section instead.

#### B2. `docs/FEATURES_RU.md` (Russian)
**When:** Same trigger as B1.
**How:** Mirror the B1 change in Russian. Keep section numbers and bullet layout identical to the EN file.

#### B3. `docs/FEATURES_UK.md` (Ukrainian)
**When:** Same trigger as B1.
**How:** Mirror the B1 change in Ukrainian. Keep section numbers and bullet layout identical to the EN file.

---

### C — String Resources (when new UI text is added)

#### C1. `app_v2/src/main/res/values/strings.xml` (English)
**When:** Any new string key referenced from code or layouts.
**How:** Add the `<string name="key">value</string>` entry in alphabetical or logical grouping order. Never hard-code UI strings in Kotlin/XML.

#### C2. `app_v2/src/main/res/values-ru/strings.xml` (Russian)
**When:** Same trigger as C1.
**How:** Add the same key with the Russian translation.

#### C3. `app_v2/src/main/res/values-uk/strings.xml` (Ukrainian)
**When:** Same trigger as C1.
**How:** Add the same key with the Ukrainian translation.

---

### D — Architecture & Tech Docs (when structure or dependencies change)

#### D1. `docs/ARCHITECTURE.md`
**When:** A new layer, module, major class, or data-flow path is introduced (e.g. new UseCase chain, new Repository, new background service). Minor fixes or internal refactors do not require this update.
**How:** Update the relevant diagram or section. Keep the C4 level of detail — components and their responsibilities, not method signatures.

#### D2. `docs/TECH_STACK.md`
**When:** A new third-party library is added or an existing one is upgraded to a new major version.
**How:** Add or update the library entry: name, version, purpose, and any integration notes.

#### D3. `dev/TECH_REQUIREMENTS.md`
**When:** Same trigger as D2, or when platform constraints change (minSdk, compileSdk, Java version, Kotlin version).
**How:** Update the relevant table row or constraint note. Keep the "last updated" date current.

#### D4. `dev/PROJECT_OPERATIONS_INDEX.md`
**When:** A new module is created, a new top-level feature path is established, or a new fast-command is added.
**How:** Add or update the relevant section (Workspace Topology, Source Layout, Fast Commands, Feature-to-Path Map). Keep entries concise — one line per item.

#### D5. `app_v2/build.gradle.kts`
**When:** Adding dependencies, changing SDK versions, adding/modifying product flavors, or adding new `BuildConfig` fields.
**How:** Edit the relevant block. Increment Room's `schemaVersion` (see D6) whenever the DB schema changes. Confirm the new dependency appears in `gradle/libs.versions.toml` first; add it there if absent.

#### D6. `app_v2/src/main/java/com/sza/fastmediasorter/data/db/AppDatabase.kt` (schema version)
**When:** Any Room entity is added, removed, or has a column change.
**How:** Increment the `version` number in `@Database`. Add a corresponding `Migration` object. Do not rename the old migration — append a new one.

---

### E — Spec Files (before or during implementation)

#### E1. `PLAN/spec_<name>.md`
**When:** Before implementing any non-trivial feature (Tier 2+). The spec must exist and be in `Status: Approved` before code is written.
**How:** Use the `/spec` command — never write spec files manually. After implementation, update the spec's `Status` field from `Approved` → `Implemented` and note the completion date.

---

### F — User Help Docs (when the feature changes what the user sees or how they interact)

Update only the sections directly relevant to the change. Do not rewrite entire files.

#### F1. `docs/HOW_TO.md` + `docs/HOW_TO_RU.md` + `docs/HOW_TO_UK.md`
**When:** A new multi-step workflow is available to the user (e.g. "how to cast to Chromecast", "how to set up scheduled sync").
**How:** Add a new numbered how-to section in the same style as existing entries. Add it to all three language files.

#### F2. `docs/FAQ.md` + `docs/FAQ_RU.md` + `docs/FAQ_UK.md`
**When:** The feature is likely to generate user questions ("Why does X happen?", "Where is setting Y?").
**How:** Add one or more Q&A pairs in all three files. Keep answers concise and link to HOW_TO if a full walkthrough exists.

#### F3. `docs/TROUBLESHOOTING.md` + `docs/TROUBLESHOOTING_RU.md` + `docs/TROUBLESHOOTING_UK.md`
**When:** A new known failure mode is introduced or resolved; or a bug fix addresses a previously documented issue.
**How:** Add the symptom, cause, and resolution as a bullet or numbered item. Remove or mark resolved any entries that no longer apply.

#### F4. `docs/LIMITATIONS.md` + `docs/LIMITATIONS_RU.md` + `docs/LIMITATIONS_UK.md`
**When:** The feature has a known constraint the user should be aware of (e.g. "cast only works on Standard flavor", "max 500 files per batch").
**How:** Add a concise limitation bullet in all three files under the most relevant section.

#### F5. `docs/QUICK_START.md` + `docs/QUICK_START_RU.md` + `docs/QUICK_START_UK.md`
**When:** The initial setup or onboarding flow changes (new required step, removed step, changed screen).
**How:** Update the affected numbered step or add a new one. Keep entries short — Quick Start is not a full manual.

#### F6. `README.md` (root, GitHub-facing)
**When:** A major new feature is added that changes the app's top-level value proposition, or a key capability is removed.
**How:** Update the feature list or description section. This file is what developers/contributors see first on GitHub — keep it accurate and concise.

#### F7. `docs/README.md` + `docs/README_RU.md` + `docs/README_UK.md`
**When:** Same trigger as F6, or when the user-facing overview needs updating (download links, screenshots referenced, supported versions).
**How:** Add a one-line entry to the feature summary list. Mirror the change in all three language files.

---

### G — Website / Landing Pages (when a significant user-visible feature is added or removed)

The root HTML files are the public GitHub Pages website. They contain a key features list, usage scenarios, and download links visible to all visitors.

#### G1. `index.html` (English landing page)
**When:** A new major feature is added that belongs in the "Key Features" `<ul>` list, or an existing feature is removed/renamed; or a new usage scenario card is warranted.
**How:** Add a `<li>` entry to the features list inside `<div class="feature-list">`, or add/update a scenario `<div class="card">`. Keep wording short (≤ 10 words for list items). Do not change layout, CSS, or SEO meta tags unless the product's name or description fundamentally changes.

#### G2. `index-ru.html` (Russian landing page)
**When:** Same trigger as G1.
**How:** Mirror the G1 change in Russian inside the same HTML structure.

#### G3. `index-uk.html` (Ukrainian landing page)
**When:** Same trigger as G1.
**How:** Mirror the G1 change in Ukrainian inside the same HTML structure.

---

### H — Wear OS Companion (when wear/ module is touched)

#### H1. `docs/WEAR_OS_STATUS.md`
**When:** Any change to the `wear/` module.
**How:** Update the validation status row for the affected feature area.

#### H2. `docs/WEAR_OS_ROADMAP.md`
**When:** A planned Wear OS feature is implemented or a new one is planned.
**How:** Move the item from "Planned" to "Implemented" (or add a new planned item).

---

### I — Documentation Map (when new doc files are created)

#### I1. `docs/DOCS_MAP.md`
**When:** Any new `.md` documentation file is created anywhere in the project.
**How:** Add a row to the appropriate table section with: link, one-line description, and today's date. If no existing section fits, add a new section.

---

## Decision Matrix (quick reference)

| What changed | Updates required |
|---|---|
| Bug fix, no UI/feature change | A1 only |
| Internal refactor (same behavior) | A1 only |
| New dependency added | A1, D2, D3, D5 |
| SDK / platform version change | A1, D3, D4, D5 |
| Room schema change | A1, D5, D6 |
| New string resource | A1, C1, C2, C3 |
| New user-facing feature | A1, B1–B3, F6, F7 + relevant F docs + G1–G3 |
| New user-facing feature + new strings | A1, B1–B3, C1–C3, F6, F7 + relevant F docs + G1–G3 |
| New architectural component | A1, D1, D4 |
| New spec created | E1 (via `/spec`) |
| Spec implemented | E1 (status → Implemented) |
| Wear module change | A1, H1, H2 |
| New doc file created | I1 |
| Initial setup flow changes | A1, F5 (all 3 languages) |
| New workflow for users | A1, F1 (all 3 languages) |
| Known limitation discovered | A1, F4 (all 3 languages) |
| Root README or website outdated | F6, F7, G1–G3 |

---

## Quality Rules

- Never update `CHANGELOG.md` manually — always use the script.
- Never skip RU/UK when updating EN user-facing docs (B, C, F groups). Trilingual consistency is mandatory.
- Do not rewrite documentation sections you are not updating — only touch the affected parts.
- Do not create new documentation files outside `docs/` or `dev/` unless explicitly instructed.
- When in doubt whether a change is "user-facing", apply the rule: *if a user would notice the difference in normal use, it is user-facing.*
- If a file in group F does not yet have a relevant section, add one rather than skipping the update.
- Keep `docs/DOCS_MAP.md` in sync — any new file added in this session must appear there.
