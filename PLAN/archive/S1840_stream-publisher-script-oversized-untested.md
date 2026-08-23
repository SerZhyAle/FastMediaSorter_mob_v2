# Стратегическая спецификация: S1840 - Издающий скрипт трансляций вырос вдвое сверх потолка и не покрыт тестами

**Ticket:** S1840
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-20
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при разборе конвейера превью для S1831, 2026-08-20
**Tactical spec:** `PLAN/S1840_stream-publisher-script-oversized-untested/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-20

**Текст:**

Offline stream publisher scripts/streams/collect-stream-candidates.ps1 is 3254 lines - more than double CLAUDE.md Rule 2's 1500-LOC ceiling - and has zero test coverage. Seven distinct concerns live in the one file: source discovery, header liveness probing, deep-signal probing, favicon atlas, channel-preview atlas, stream-logo atlas, tile-pack cutting and catalog publish. Evidence: wc -l = 3254; roughly 70 top-level functions between lines 279 and 3254; Glob for scripts/streams/**/*.Tests.ps1 and scripts/**/*streams*Tests*.ps1 both returned zero files, so Invoke-SignalProbe, Invoke-ChannelPreviewCapture, Build-ChannelPreviewAtlas, Build-FaviconAtlas, Build-TilePackFromSheet and Assert-AtlasBudget are all untested. The only mechanical gate touching the script is scripts/quality/assert-stream-asset-revisions.ps1, which checks pinned asset base names and revision strings and never reads byte size, tile count, row count or probe behaviour. Discovered while mapping the preview pipeline for S1831, which edits three functions inside this file. Dedup: spec_catalog search for "collect-stream-candidates" and "stream pipeline split" both returned no records.

**Захвачено во время:** S1831 (миниатюры видеоканалов из прогона живости) - находка вне объёма того тикета.

---

## 1. Проблема

Offline stream publishing is concentrated in one 3,501-line PowerShell 7 script with approximately 70 top-level functions and 64 parameters. The file combines discovery, probing, artwork generation, atlas packing, archive validation and release upload, while no publisher-specific Pester tests protect the pure rules or the publication safety checks. A regression can therefore produce a malformed or mismatched catalog artifact without being caught before an external consumer or an Android import sees it.

---

## 2. Цели

1. Reduce the orchestration script and every extracted module to no more than 1,500 lines without changing the existing command-line contract.
2. Add deterministic Pester coverage for URL/schema rules, prune-status normalization, atlas geometry and publication safety invariants.
3. Preserve the existing discovery, probe, artwork, archive and GitHub publication behavior, including rollback on rejected artifacts.
4. Make the module boundaries explicit so a future change can be tested without invoking network services, ffmpeg, GDI+ or GitHub.

**Non-goals:**

- Changing the stream catalog format, published asset names, revision values or consumer contracts.
- Adding Android or Wear OS production code.
- Replacing PowerShell, Pester, GDI+, ffmpeg or the GitHub CLI.
- Requiring live network calls or a release upload in the unit-test suite.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Keep the current single-command operator workflow and parameter defaults.
2. Keep generated artifacts under `temp/` and preserve the existing backup behavior.

### 3.2 Жёсткие ограничения

- **Flavor:** not applicable; repository PowerShell tooling only.
- **API level:** no Android API dependency; requires PowerShell 7 and the existing Windows imaging/tooling prerequisites.
- **Wear OS:** not affected.
- **Производительность:** preserve existing probe throttles and avoid adding network work to unit tests.
- **Совместимость данных:** preserve the current 19-column CSV and published ZIP/atlas contracts; no Room migration.
- **Локализация:** not applicable; no user-visible Android strings.
- **Доступность:** not applicable; no UI surface.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Validation level:** static PowerShell checks, Pester unit tests and the existing script quality gates; no device test.
- **Owner sign-off:** 2026-08-20 - preserve CLI and external stream-catalog contracts while splitting the implementation.
- **Related tickets:** S1831 (preview pipeline changes in the same script), S1828 (external consumer registry and asset revision guard).

---

## 4. Контекст текущей архитектуры

The stream catalog is produced by offline Windows tooling rather than by the Android application. One PowerShell entry point owns parameter parsing, script-scoped configuration, source adapters, probes, image processing, archive construction and release upload. The published ZIP and the two external atlas families are consumed outside this repository, so the producer is a compatibility boundary even though it is not an Android module.

The current file has grown through several feature additions and keeps behavior together by shared script state. A superficial test-only change would leave the 1,500-line violation and would make extracted behavior difficult to load without executing the entire command. The solution must preserve the entry point while creating loadable, deterministic seams for the rules that can be tested offline.

---

## 5. Предлагаемый подход

Keep one thin parameterized entry script and move cohesive responsibilities into dot-sourced module files loaded in a documented order. Shared state remains owned by the entry context, while pure transformations and contract predicates become callable from isolated Pester fixtures. Network, decoder and release operations remain behind explicit boundaries so unit tests can substitute deterministic inputs.

### 5.1 Основные столпы / модули

1. **Shared contract helpers** - schema, URL classification, topic normalization, CSV/backup helpers and common validation predicates.
2. **Candidate discovery and probing** - source adapters, liveness ladders and provider balancing, preserving existing throttles and verdict semantics.
3. **Artwork and atlas production** - favicon cache, channel-preview, stream-logo and tile-pack builders with their existing geometry and byte limits.
4. **Delivery and orchestration** - catalog maintenance, archive entry checks, publication guards, mode dispatch and the unchanged CLI surface.
5. **Pester tests** - deterministic tests for pure helpers and publication predicates, with no live network, media decoder, GDI+ or GitHub dependency.

### 5.2 Потоки данных и событий

CLI parameters -> entry context -> source/discovery modules -> liveness decisions -> candidate/catalog rows -> artwork modules -> CSV/atlas/tile-pack artifacts -> delivery validation -> optional GitHub upload.

Pester fixture -> dot-sourced contract/test seam -> synthetic rows/files -> assertion of deterministic result. No fixture calls external services.

### 5.3 Точки расширяемости

- New source adapters remain additive and do not change the CSV schema without an explicit contract update.
- New asset builders must expose geometry and budget predicates before publication wiring.
- New modes must reuse the same archive and favicon safety guards rather than adding raw upload paths.

---

## 6. Открытые вопросы / Research items

1. **PowerShell test framework availability**
   - **Вопрос:** Can the repository run publisher tests with its existing tooling without adding a dependency?
   - **Варианты:** Pester 3.4.0 already installed; adding a project dependency is unnecessary.
   - **Нужно выяснить:** Verify the installed module and existing `*.Tests.ps1` convention.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1840_stream-publisher-script-oversized-untested/research/01__publisher-boundaries-and-test-harness.md`

2. **Compatibility-preserving extraction strategy**
   - **Вопрос:** Can the script be split without introducing a second CLI or changing script-scoped contracts?
   - **Варианты:** Dot-sourced modules loaded by the current entry script; separate executables are rejected for this ticket.
   - **Нужно выяснить:** Map shared state and responsibility boundaries before writing phases.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1840_stream-publisher-script-oversized-untested/research/01__publisher-boundaries-and-test-harness.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Dot-sourcing order changes a shared function or variable contract | Средняя | A mode fails only at runtime | Load modules in dependency order and add smoke tests for every dispatch mode. |
| Extraction changes a published artifact invariant | Средняя | External consumer receives a mismatched catalog or atlas | Reuse existing guards and add deterministic tests for ZIP order, atlas budgets and index pairing. |
| Pester 3 syntax differs from newer examples | Низкая | Tests do not run in the maintained environment | Use the installed Pester 3.4 API and run the repository test command in the build phase. |
| Large mechanical move leaves duplicate or orphaned functions | Средняя | Ambiguous behavior or dead code ships | Enforce one declaration per moved function, per-file line budgets and an invocation smoke check. |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.»>

---

## 9. Архитектурные решения (ADR)

**ADR-1: Preserve one CLI and split by dot-sourced modules**

- **Решение:** Keep `collect-stream-candidates.ps1` as the sole operator entry point and dot-source cohesive module files from it.
- **Альтернативы:** Rewrite as a new executable, add a PowerShell class library, or only add tests around the oversized file.
- **Почему:** Dot-sourcing preserves parameter binding and script-scoped state, while the other options increase compatibility risk or leave the line-budget problem unresolved.

**ADR-2: Test deterministic boundaries, not external services**

- **Решение:** Unit tests cover pure transformations and artifact predicates with synthetic inputs; live APIs, ffmpeg, GDI+ and GitHub remain integration/manual operations.
- **Альтернативы:** Network-backed tests or a full end-to-end publication test.
- **Почему:** External tests are slow, flaky and can mutate a release; they cannot provide reliable regression evidence for local rules.

---

## 10. Связи с другими спеками

- S1831 - тикет, при разборе которого находка обнаружена; правит `Invoke-SignalProbe`, `Invoke-ChannelPreviewCapture` и `Build-ChannelPreviewAtlas` внутри этого же файла. Не блокер: S1831 не ждёт этого тикета.
- S1828 - реестр внешних потребителей; `scripts/quality/assert-stream-asset-revisions.ps1` - единственный гейт, который вообще читает этот скрипт.

---

## 11. Критерии готовности (strategic-level)

1. The entry script and every extracted module are at or below 1,500 lines.
2. Existing parameter names, defaults, mode dispatch and published artifact names remain unchanged.
3. Publisher Pester tests run without network access and cover the agreed deterministic contract predicates.
4. Existing stream asset revision and quality gates pass.
5. A standard debug build is not applicable; this ticket is tooling-only and uses PowerShell/Pester validation.


## Last Audit

**Date:** 2026-08-20
**Mode:** full (strategic + 4 phases)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 1

### Evidence

- Criterion 1 - line budget: entry point 514, Artwork 1279, Probes 772, Discovery 450, Delivery 360,
  Common 210. The 3,501-line file recorded in the previous audit is gone; the largest survivor is 1279.
- Criterion 2 - unchanged surface: `Get-Command` on the entry point exposes 69 parameters; all eight mode
  switches (`-CatalogOnly`, `-PreviewOnly`, `-WithFavicons`, `-WithChannelPreviews`, `-WithStreamLogos`,
  `-WithTilePacks`, `-Publish`, `-PruneDead`) are present, and all seven published artifact names still
  appear in the source.
- Criterion 3 - `Invoke-Pester -Path scripts/streams.tests -PassThru`: 15 passed, 0 failed, 0 skipped, no
  network. Covers atlas geometry and byte ceiling, CSV schema order, URL/topic/prune rules, ZIP entry
  contract, provider interleave and loss thresholds.
- Criterion 4 - `assert-stream-asset-revisions.ps1`: PASS, 4 pinned assets still published, 2 frozen and
  untouched. Document registry: `validate.ps1` PASS (36 records), `generate.ps1 -Check` reports current.
- Parse check: all 10 publisher `.ps1` files parse clean.
- Criterion 5 - EXEMPT by its own wording: tooling-only, no APK build applicable.

### Independent end-to-end evidence

The split is additionally exercised beyond its own unit tests. S1841 ran the real `Build-StreamLogoAtlas`
out of the extracted `StreamPublisher.Artwork.ps1` against the live artwork cache, twice: 4 148 tiles
packed, encoded through ffmpeg, sidecar written, and a byte-identical sheet both times
(`PLAN/S1841_stream-logo-sheet-silently-truncates/evidence/`). A refactor that had broken the artwork
seam would not have produced a correct sheet, so this is behaviour evidence the Pester suite cannot give.

### Manual / on-device

- None. Tooling-only ticket; nothing ships in an APK.
