# CLAUDE.md

Guidance for Claude Code in this repo. Load-bearing rules only — detailed
references live in `dev/` and `docs/`.

## Communication

- **Language**: RUSSIAN in chat, ENGLISH in code/docs/logs/commits.
- **Tone**: professional, dry, concise. Ask if ambiguous — do not guess paths or values.

## Author Style (all user-facing text, docs, UI strings)

- Ellipsis: `..` (two dots), never `...`.
- Always use `ё`/`Ё` in Russian where grammatically correct (e.g. `всё`, `ещё`, `приём`).

Non-negotiable — not typos.

## Caveman Mode (optional)

- Trigger only on explicit user request: `caveman`, `less tokens`, `be brief`, `ultra-terse`, `/caveman`.
- Keep Russian in chat. Keep code/docs/logs/commits in English.
- Drop filler, pleasantries, and hedging. Keep technical terms exact. Default level: `full`. `lite` keeps full sentences. `ultra` compresses harder.
- Repo workflow and safety rules override brevity. Mandatory skill routing still applies.
- Do not use caveman compression for security warnings, destructive/irreversible confirmations, or multi-step sequences where compression creates ambiguity.
- `stop caveman` or `normal mode` disables it.

## Spec Writing Style

Applied by all `/spec*` skills when writing `.md` artefacts. Reader is a senior developer — convey the idea, not the explanation of the idea.

- **Lists over tables.** Use `- item` for requirements, steps, decisions. Tables only where data has 3+ parallel columns (Modules table, Flavors table, stack pins).
- **No pseudographics.** No ASCII arrows, boxes, or flow diagrams in spec text.
- **No self-evident links.** Skip "ViewModel observes Repository", "§5 feeds §6" — reader knows Clean/MVVM.
- **One idea per bullet.** No elaboration paragraphs inside list items. If it needs WHY, it belongs in ADR, not in the list.
- **No section summaries.** Don't close sections with "this ensures X" or "together these achieve Y".

## Debug Verification Tags (code specs)

Invariant: a `Timber.d("Sxxxx: ...")` tag exists in `.kt` code **if and only if** spec `Sxxxx` is currently in status `BlockNeedUserTest`. The tag lifecycle is bound to that status — nothing else.

```kotlin
Timber.d("Sxxxx: <short description of exercised path>")
```

- **On transition INTO `BlockNeedUserTest`** (by `/spec`, `/spec-tech`, `/spec-dev`, `/spec-all`, or a manual `update.ps1 -Status BlockNeedUserTest`): insert one tag at the entry point of each changed flow — not on every modified line. The skill that moves the ticket into the status owns the insertion.
- During on-device testing: the tag appearing in logcat proves the code path was exercised → the spec may leave `BlockNeedUserTest` (normally `→ Verified` via `/spec-check`).
- **On transition OUT of `BlockNeedUserTest`** (to `Verified` via `/spec-check`; back to `Tactical`/`Approved`/`Draft`/`In Progress` via `/spec-update`; to `Implemented` on `/spec-all` resume; to any other `Block*`; to `Archived`; or a manual `update.ps1 -Status …`): grep for `Timber.d("Sxxxx:` across all `.kt` files and delete every matching line. The skill that moves the ticket out of the status owns the removal; commit the removal together with the status change. A manual status change must be paired with the same grep-and-delete.
- A tag whose `Sxxxx` is **not** currently `BlockNeedUserTest` is stale. Any `/spec-fix`, `/spec-check`, or `/spec-arc` run that notices one removes it.
- Never remove a tag while its spec is still `BlockNeedUserTest` — the tag is the operator's logcat probe for that round of device testing. Removal is a side effect of the status leaving `BlockNeedUserTest`, never a standalone "cleanup".
- Tags are never present in `Verified`, `Implemented`, `Partial`, `Broken`, `Block*` other than `BlockNeedUserTest`, or `Archived` code.

## Mandatory Skills (auto-trigger, do not handle manually)

| Situation | Skill |
|-----------|-------|
| **Очень незначительная** правка (коррекция дизайна, опечатка, цвет/отступ, одна строка ресурса) | `/quick` (без спеки/доков/билда; только `dev/CHANGELOG.md`) |
| Creating/updating `PLAN/Sxxxx_*.md` | `/spec` |
| Full spec pipeline (idea → verified implementation, unattended) | `/spec-all` |
| UI/UX change touching layout, menus, visibility, orientation, empty/error states, overflow | `/ui-clarify` (blocks impl until ambiguities resolved) |
| Editing `docs/FEATURES*.md` or other feature docs | `/doc-update` (EN/RU/UK mirrors) |
| Analysing `logs/current.log` or logcat | `/log-reader` |
| Build questions or triggering a build | `/build` (do NOT invoke gradle directly) |
| Git questions (commit/stage/push/diff/history) | `/git` |
| "Where does X happen?" / "find file Foo.kt" / auditing code / planning a refactor / adding a class | `/catalog` (query first, update after) |

## Spec Catalog (Sxxxx tickets)

Each specification carries a stable ticket id `Sxxxx` (four digits, zero-padded). The id never changes, never gets reused, and is the canonical reference token in chat / commits / `dev/CHANGELOG.md`.

- **Token rule:** any reference of the form `S\d{4}` is a ticket id. Resolve via:
  `pwsh -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`
- **Filenames:** every spec artefact is `PLAN/Sxxxx_<slug>.md` (no `_spec_` segment — the id already identifies the artefact). Tactical folder: `PLAN/Sxxxx_<slug>/`. **No audit / fix files are written** — `/spec-check` and `/spec-fix` record findings inside the ticket file's `## Last Audit` block (overwritten on each run) and in the journal `updated` timestamp.
- **Journal:** `PLAN/spec-catalog.jsonl` is the source of truth. Schema: `scripts/spec_catalog/SCHEMA.md`.
- **Required fields:** `id`, `name`, `status`, `priority` (0..100), `file`, `created`, `updated`. Optional: `tier`.
- **Priority guide:** 90..100 build/release blocker · 70..89 critical · 40..69 standard (default 50) · 10..39 polish · 0..9 wishlist.
- **Statuses:** active — `Draft`, `Approved`, `Tactical`, `In Progress`, `Implemented`, `Verified`, `Partial`, `Broken`. Block — `BlockByOtherTask`, `BlockNeedUserTest`, `BlockQuestions`, `BlockExternal`. Terminal — `Archived` (soft delete; ids never reused).
- **Stale signal:** `a.ps1 ss` flags any active spec with `updated` ≥ 14 days (`!`) or ≥ 30 days (`!!`); consider `/spec-update <Sxxxx>`.
- **CLI — primitives:** `insert.ps1`, `update.ps1`, `select.ps1`, `delete.ps1`, `validate.ps1` under `scripts/spec_catalog/`. **Never edit `PLAN/spec-catalog.jsonl` by hand.**
- **CLI — operator facade:** `next-id.ps1`, `search.ps1`, `close.ps1`, `stats.ps1`, `bulk-update.ps1`, `complete.ps1`, `archive.ps1` — prefer these for id allocation, lookup, finalization, summary, batch changes, one-shot completion, and archiving (move to `temp/done/` + set Archived).
- **Lifecycle hooks:** `/spec` calls `insert`; `/spec-tech` flips status to `Tactical`; `/spec-dev` flips to `In Progress` then `Implemented`; `/spec-check` flips to `Verified` / `Partial` / `Broken` (writes summary into ticket's `## Last Audit`); `/spec-fix` touches `updated`. Block-states are set explicitly via `update.ps1 -Status Block...`.
- **Soft delete only:** `delete.ps1` sets status `Archived`; record stays in the journal forever.

## Research Order (before changes)

1. `dev/PROJECT_OPERATIONS_INDEX.md` — workspace routing + **Feature-to-Path Map** (use before any global search).
2. For any `Sxxxx`-tagged question — run `scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` first to get current status / file path; do not infer from filename alone.
3. **`dev/CATALOG/<module>.md` (or `query.ps1`) — MANDATORY first stop for any class/file lookup.**
   - Run `query.ps1` before any `Grep`, `Glob`, or shell `find`. These are fallbacks only when the catalogue yields nothing.
   - Locating a `.kt` file by name? → `-ClassMatches "*Name*"`. Finding what touches a feature? → `-PathMatches` or `-Role`. Who injects a type? → `-Injected <Type>`.
   - **Never use `find`/`Glob` to locate a Kotlin class — the catalogue already knows the path.**
4. Domain doc per task type:
   - Architecture → `docs/ARCHITECTURE.md`
   - Build/flags → `docs/DEV_OPS.md` + `app_v2/build.gradle.kts`
   - Dependencies → `docs/TECH_STACK.md` + `dev/TECH_REQUIREMENTS.md`
   - Network → `dev/NETWORK_SPECS.md`
5. Implementation files.

**Multi-step tasks**: read `dev/AGENT_WORKFLOW.md` BEFORE execution (mandatory 5-step process).

## Modules

| Module | Root | Purpose |
|--------|------|---------|
| `app_v2/` | `app_v2/src/main/java/com/sza/fastmediasorter/` | Main Android app |
| `wear/` | `wear/src/main/java/com/sza/fastmediasorter/wear/` | Wear OS companion |

**Architecture**: Clean + MVVM. Flow: `UI → ViewModel → UseCase → Repository → DataSource`.
Layers: `ui/` (zero business logic — delegate to `ui/<feature>/helpers/*Manager.kt`), `domain/`, `data/`, `di/`, `core/`, `utils/`, `worker/`, `widget/`.

## Product Flavors

| Flavor | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM |
|--------|:-----:|:-----:|:------:|:-----:|:----:|:----:|
| `standard` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `lite` | ✓ | — | ✓ | — | — | — |
| `photos` | — | — | ✓ | — | — | ✓ |
| `legacy` | ✓ | ✓ | ✓ | — | — | ✓ |

Gated via `BuildConfig` fields in `app_v2/build.gradle.kts`.

## Tech Stack Pins

Kotlin 1.9+ / Java 17 / `compileSdk 35` / `minSdk 26` (standard), `minSdk 23` (legacy).
Hilt · Room v6 (bump version + migration on every schema change) · ExoPlayer Media3 1.2.1 · Glide 4.15.1 · SMBJ/SSHJ/Apache Commons Net · Google Drive/MSAL/Dropbox SDKs.
**Logging: Timber only** — `Log.d()` is prohibited.

## Strict Rules

1. No writes to project root — use `temp/` for logs, artifacts, backups.
2. File size limit 1500 LOC — extract to `helpers/*Manager.kt`.
3. Activity logic prohibited — delegate to Manager/Helper classes.
4. Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
5. Backup rule: file >500 LOC → timestamped backup in `temp/` before edit.
6. Naming: `VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`.
7. Lint: resolve warnings in files you touch.
8. Ignore `*.backup` files unless user asks for historical comparison.
9. Before editing, read existing inline comments/KDoc in the affected area — treat as requirements.
10. When changing logic, add WHY-comments only when not obvious; remove stale comments.
11. UI ambiguity gate: see `/ui-clarify` — implementation blocked until all placement/visibility/fallback decisions are explicit.
12. Layout orientation: editing any `res/layout/*.xml` → ALWAYS check `res/layout-land/*.xml` counterpart. If it exists, apply the equivalent change in the same step. If it should exist but doesn't, create it or add a blocker. **Never silently leave portrait-only edits in a layout that has a landscape counterpart.**
13. Spec ticket discipline: never edit `PLAN/spec-catalog.jsonl` directly; never rename a spec file out of its `Sxxxx_` prefix; never re-introduce a `_spec_` segment in PLAN paths; new specs must allocate an id via `scripts/spec_catalog/insert.ps1` **before** the strategic `.md` is written to disk.
14. Internal script ownership: do not work around broken or insufficient repo scripts when the current task depends on them. If a project script is buggy, outdated, or can be materially improved to complete the task safely, fix the script itself and then use it.

## Feature Inventory

`docs/FEATURES.md` is canonical (21 feature areas). Read before implementing anything to avoid duplication. Mirrors: `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

## UI Communication Policy

`docs/COMMUNICATION_POLICY.md` is the canonical source for tone, message formulas, and feedback-channel routing. Mirrors: `docs/COMMUNICATION_POLICY_RU.md`, `docs/COMMUNICATION_POLICY_UK.md`. Origin: S0118.

- **Read before writing or modifying any user-visible string** — applies to toasts, dialogs, empty states, errors, progress, confirmations, and next-step CTAs.
- **Tone checklist** (§6 of the policy) must pass before any string batch is committed.
- **Exceptions:** legal texts, Terms of Service, machine-readable artifacts — keep formal neutral style, do not apply friendly rewrite.
- **Deviations** from the policy are allowed only for the exempted categories above; any other deviation must be justified and noted in the spec or commit message.

## Post-Change Steps (mandatory, all agents)

1. **Dev Changelog** after every code/config change — run
   `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"`
   (never edit `dev/CHANGELOG.md` directly).
2. **Feature docs** after any new user-facing feature — update `docs/FEATURES.md` + `_RU` + `_UK` with a concise bullet.
3. **String locale audit** after adding/removing any `strings.xml` keys — run
   `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "<key_prefix>"`
   to verify EN/RU/UK parity. Exit code 1 = missing keys, must fix before commit.
4. **Catalogue sync** — run after **every** `.kt` file change (not only API changes):
   - `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>` — refreshes auto-fields; manual fields are preserved.
   - `pwsh -File dev/CATALOG/scripts/render.ps1 -Module <app_v2|wear>` — regenerates the human-readable `.md`.
   - For new classes, fill `role` + `status` via `set.ps1` (see `dev/CATALOG/README.md`).
   - Commit updated `dev/CATALOG/<module>.jsonl` + `<module>.md` together with the code change.
5. **Spec catalog sync** — run on every spec status transition (Draft → Approved → Tactical → In Progress → Implemented → Verified / Partial / Broken, or to/from any `Block*` state):
   - `pwsh -File scripts/spec_catalog/update.ps1 -Id Sxxxx -Status <new>` (also `-Priority N` when the urgency changes).
   - Skills `/spec`, `/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-update`, `/spec-all`, `/quick` perform this automatically — invoke the CLI yourself only when no skill is in flight.
   - Direct edits to `PLAN/spec-catalog.jsonl` are forbidden.

## Version Format

`Y.YM.MDDH.Hmm` (e.g. `2.60.1102.207` = 2026/01/10 20:07). History: `dev/CHANGELOG.md`.
