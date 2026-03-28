# Specification Writer

Write a detailed implementation specification for a roadmap item in this project.

## Usage

```
/spec <roadmap-id> <short-name>
```

Examples:
- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`

The `short-name` becomes the filename: `PLAN/spec_<short-name>.md`

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse arguments.**
Extract the roadmap ID (e.g. `X.11`) and short name (e.g. `background-thumbnail-preload`).
Output filename: `PLAN/spec_<short-name>.md`

**Step 2 — Read context.**
- Read `PLAN/IMPROVEMENT_ROADMAP.md` to find the roadmap entry for the given ID (tier, description, risk factor).
- Read `dev/PROJECT_OPERATIONS_INDEX.md` to identify which modules and paths are relevant.
- Read `docs/ARCHITECTURE.md` for data-flow and layer context.
- Read `app_v2/build.gradle.kts` to identify which product flavors are affected and what `BuildConfig` flags apply.
- Read all source files relevant to the feature (use Grep/Glob to find them). Be thorough — the "Current Architecture" section must be accurate.
- If the feature touches the player, read `ui/player/PlayerActivity.kt` and all files in `ui/player/helpers/`.
- If the feature touches settings, read the relevant fragment in `ui/settings/fragments/`.
- If the feature may affect Wear OS, check `wear/src/main/java/com/sza/fastmediasorter/wear/`.

**Step 3 — Determine the Tier label.**
Map the roadmap tier to the spec header string:
- TIER 0 → `0 — Security/Compliance (urgent)`
- TIER 1 → `1 — Quick Win (1–2h, zero risk)`
- TIER 2 → `2 — Easy (2–4h, low risk)`
- TIER 3 → `3 — Moderate (4–8h, medium risk)`
- TIER 4 → `4 — Strategic (8h+, high risk)`

**Step 4 — Write the spec file** to `PLAN/spec_<short-name>.md` using the exact template below.

**Step 5 — Run the dev log command** (mandatory after every file change):
```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec" "Add specification for <roadmap-id>"
```

---

## Spec Template

Use this exact structure. Do not skip sections. Fill every section with real content derived from the code you read — no placeholders.

```markdown
# Specification: <ID> — <Feature Name>

**Status:** Draft
**Date:** <today's date YYYY-MM-DD>
**Tier:** <tier label from Step 3>
**Roadmap entry:** <exact description text from IMPROVEMENT_ROADMAP.md>

---

## 1. Problem Statement

<2–4 sentences. What is broken or missing? What is the user impact? Reference specific classes or files where the gap exists.>

---

## 2. Goals

<Numbered list of concrete deliverables. Each item = one testable outcome.>

Non-goals for this spec: <list things explicitly out of scope>

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ / ❌ | |
| `lite`     | ✅ / ❌ | |
| `photos`   | ✅ / ❌ | |
| `legacy`   | ✅ / ❌ | |

<If the feature is flavor-specific, name the `BuildConfig` flag that gates it (e.g. `BuildConfig.FEATURE_AUDIO`). If no existing flag applies, propose the new flag and where to declare it in `build.gradle.kts`.>

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | <note if legacy flavor needs different handling> |
| 26+ (standard minSdk) | <default path> |
| 29 (Android 10) | <scoped storage, RecoverableSecurityException, etc. if relevant> |
| 30+ (Android 11) | <MediaStore batch ops, package visibility, etc. if relevant> |
| 31+ (Android 12) | <SplashScreen, exact alarms, notification permission if relevant> |
| 34+ (Android 14) | <photo picker, predictive back, etc. if relevant> |

<Remove rows not relevant to this feature. Add rows for other API-gated behaviour.>

### 3.3 Wear OS Impact

<One sentence: does this change require any update to the `wear/` module? If yes, explain what. If no, state "No Wear OS changes required.">

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `ClassName` | `path/to/File.kt` | What it does today |
...

<Add 1–2 sentences describing the key limitation or gap in the current architecture.>

---

## 5. Proposed Architecture

### 5.1 <Main structural change>

<Describe the change. Use code snippets (Kotlin) for new classes/interfaces/data classes where helpful.>

### 5.2 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `NewClass.kt` | `path/to/` | ≤ N |
...

<All new files must respect the 1000-line limit. If estimated lines > 600, plan extraction into a helper Manager from the start — do not wait until the limit is hit.>

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ / ⚠️ | <Manager(s) that will hold the logic> |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ✅ | |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ / ⚠️ | |
| No `Log.d()` — Timber only | ✅ | |
| Room schema version incremented (if DB changes) | ✅ / N/A | <new version number if applicable> |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ / N/A | |
| Hilt DI: new bindings declared in module file | ✅ / N/A | <which `@Module` file> |

### 5.4–5.N <Additional subsections as needed>

<Detail each major component: responsibilities, key methods, state management, lifecycle hooks.>

---

## 6. Data Flow

<ASCII diagram showing the data/event flow through the new architecture. Use → for calls and ←—— for observations/callbacks.>

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `ExistingFile.kt` | What changes and why | N lines |
...

<If any file will exceed 500 lines after the change, add a backup step in section 9.>

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| <risk description> | Low / Med / High | <how to prevent or recover> |
...

---

## 9. Testing Plan

### 9.1 Unit Tests

<List the classes/methods that warrant unit tests. For each, name the test class and the key scenario(s) to cover. If this tier is 1 or 2 and logic is trivial, "No unit tests required" is acceptable — but justify it.>

### 9.2 Manual Test Cases

<Ordered list of manual verification steps. Include: happy path, error states, API-level variants (e.g., "test on Android 10 device for RecoverableSecurityException").>

### 9.3 Maestro E2E (if applicable)

<If the feature has a user-visible flow suitable for automation, describe the Maestro flow file to add in `maestro/smoke/` or `maestro/critical/`. If not applicable, state "No Maestro tests needed.">

---

## 10. Accessibility

<One paragraph. Does this feature add or change UI elements? If yes: are all interactive elements reachable by TalkBack (content descriptions, focusable flags)? Are minimum touch targets 48dp? Any colour-only affordances that need a non-colour alternative? If the feature is purely non-visual (e.g. a background service), state "No accessibility changes.">

---

## 11. User-Facing Feature Update

<If this feature adds or materially changes what the user can do, list the three FEATURES doc entries to add/update:>
- `docs/FEATURES.md` (EN): <bullet text>
- `docs/FEATURES_RU.md` (RU): <bullet text>
- `docs/FEATURES_UK.md` (UK): <bullet text>

<If no user-visible change, state "No FEATURES doc update required.">

---

## 12. Architecture Decision Records (ADRs)

<List any non-obvious architectural choices made in this spec and the reason behind them. Use this format:>

**ADR-1: <Decision title>**
- **Decision:** <What was decided>
- **Alternatives considered:** <What else was evaluated>
- **Reason:** <Why this option was chosen>

<Add one ADR per significant trade-off. If there are no notable decisions, write "No ADRs — implementation follows established patterns.">

---

## 13. Implementation Steps

<Numbered ordered list. Each step = one atomic unit of work (create a file, add a method, modify a layout). Follow dependency order — create data classes before classes that use them. End with the dev log command for each modified file.>

Mandatory step checklist at the end:
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated (if user-facing)
- [ ] Room DB migration added + version incremented (if DB schema changes)
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

<Bullet list of related improvements deliberately deferred to keep this spec focused.>
```

---

## Quality Rules

- Every class reference must match an actual file that exists in the codebase (verify with Grep/Glob before writing).
- Line budgets in section 5.2 must respect the 1000-line file limit from `CLAUDE.md`. If any class will exceed 600 lines, split it proactively using the Manager pattern.
- Section 13 steps must be in dependency order (create data classes before classes that use them).
- String resources must include EN/RU/UK variants — add a step in section 13 for each language file.
- If the feature touches a file > 500 lines, add a step to create a timestamped backup in `temp/` first.
- Do not reference files in read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Sections 3.1 and 3.2 must be filled even if the answer is "all flavors" or "no API-level differences" — state it explicitly.
- Section 5.3 Architecture Compliance table must be filled for every rule — never leave it empty.
- Section 9.2 Manual Test Cases must include at least one error-state scenario.
- Sections 10 (Accessibility) and 11 (User-Facing Feature Update) must not be omitted — write "No changes" if not applicable, but do not skip.
- If the feature adds new Hilt bindings, name the `@Module` file in section 5.3 and include the binding as a step in section 13.
- If the feature has `BuildConfig`-gated code, note the flag name in section 3.1 and in the relevant implementation step.
