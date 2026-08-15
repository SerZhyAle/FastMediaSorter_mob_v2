# S0589 - Streams catalog: conservative dead-link auto-prune

**Ticket:** S0589
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-21
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-21 (по итогам анализа лога fastmediasorter_20260621_160906.log)

> **Scope:** COMPACT (Simple path). Dev-tooling enhancement of one PowerShell maintenance script + its docs. No app code, no Room, no Hilt, no flavor source sets.

---

## Goal (RU)

Существующий `scripts/stream_catalog/check-liveness.ps1` (введён S0570) уже параллельно пробит все ссылки каталога `delivery/stream-catalog/streams.csv` (426 строк) и печатает `dead`-кандидатов, но из CSV их не удаляет - чистка остаётся ручной.. S0589 добавляет в тот же скрипт opt-in авто-прунинг: по флагу удалять из каталога строки, классифицированные как `dead`, консервативно (только однозначно мёртвые: DNS-fail, conn-refused, HTTP 404/410 - никогда `unknown`/гео/таймаут), с dry-run по умолчанию, таймстамп-бэкапом перед записью и точным отчётом об удалённых строках.. Сам шиппинг-каталог в рамках S0589 не мутируется - реальный прунинг это owner-операция обслуживания (гео-точка одной машины не доказывает смерть источника).

**Захвачено (verbatim, 2026-06-21):** «Изучение всех ссылок списка трансляций. каким -то скриптом параллельно. Неживые - удалить.»

**Что уже есть (не переделывать):** чтение CSV, параллельный пробинг (`ForEach-Object -Parallel -ThrottleLimit`), консервативная классификация alive/dead/unknown (HEAD->GET, `ResponseHeadersRead` чтобы не качать бесконечный Icecast-боди, RTSP TCP-connect), отчёт `temp/stream-catalog-liveness.csv`, печать dead-кандидатов.

**Gap (это и делаем):** «Неживые - удалить» - удаление dead-строк из CSV; сейчас скрипт только сообщает.

**Non-goals:**

- Клиентская/runtime liveness-проверка в приложении (отложена S0570 §6.8).
- Удаление `unknown` (auth/geo/rate/timeout - не доказательство смерти).
- Авто-публикация/перезаливка release-asset каталога.
- Любая правка app-кода, Room-схемы, Hilt, flavor source-sets.
- Двух-вантажная/повторная авто-верификация dead перед удалением (v1 - один проход; повторный прогон рекомендуется в docs).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0570 (owns the catalog + the liveness script; scoped "выбросить мёртвые" but shipped report-only), S0565 (base Streams feature), S0581 (runtime per-stream remove-from-list - distinct from this batch tooling), S0582/S0583 (catalog CSV parser/import).
- **Scope = dev-tooling + delivery data/docs only.** No `src/main`, no UI, no flavor, no API-level surface - owner UI/flavor/data-compat inputs not applicable.

---

## Phases

### Phase 1 - Add conservative opt-in prune mode to check-liveness.ps1

Goal: extend the existing script so it can delete dead rows from the catalog CSV, off by default.

Steps:
1. Add param `[switch] $Prune` (absent = dry-run) and `[string[]] $PruneStatuses = @('dead')` to the `param()` block.
2. After `$results` is computed, build `$pruneUrls` = set of `url` whose `status` is in `$PruneStatuses`.
3. Dry-run branch (no `-Prune`): print `Would prune N row(s) (run with -Prune to apply)` plus the affected `[category] name (note) url` list; write nothing back to `$CsvPath`.
4. Prune branch (`-Prune` present, `$pruneUrls` non-empty):
   - Write a timestamped backup of `$CsvPath` to `temp/` (e.g. `temp/streams.csv.<yyyyMMdd-HHmmss>.bak`) BEFORE any write; abort the write if the backup fails.
   - Keep only `$rows` whose `url` is NOT in `$pruneUrls`, preserving original row order and the original 17-column order.
   - Serialize survivors back to `$CsvPath` via `Export-Csv -NoTypeInformation -Encoding utf8` (or `ConvertTo-Csv`) so quoted fields (commas/quotes in notes/license_note) round-trip correctly - never delete lines by string manipulation.
   - Print `Pruned N row(s); backup: <path>; catalog now M row(s)`.
5. Empty-prune guard: `$pruneUrls` empty -> print `Nothing to prune`, no backup, no write, in both branches.
6. Update the `param()` defaults so the existing report-only behaviour is byte-unchanged when `-Prune` is absent.

Verification:
- `pwsh -NoProfile -Command "{ . then AST }"`: script parses with no errors (`[System.Management.Automation.Language.Parser]::ParseFile` yields 0 errors). expected: 0 errors.
- Running with no new args still prints the `=== Liveness summary ===` report and exits 0 (behaviour preserved). expected: exit 0.
- `-Prune` and `-PruneStatuses` appear in `(Get-Command <script>).Parameters`. expected: both present.

### Phase 2 - Verify prune write-back on a CSV copy (real catalog untouched)

Goal: prove the prune mutation is correct and lossless without mutating the shipped catalog.

Steps:
1. Copy `delivery/stream-catalog/streams.csv` to `temp/streams-prune-test.csv`.
2. Append one synthetic guaranteed-dead row with a non-resolving `.invalid` host (DNS-fail -> classified `dead`) so deletion is deterministic regardless of network/geo.
3. Run `check-liveness.ps1 -CsvPath temp/streams-prune-test.csv -Prune`.
4. Confirm real `delivery/stream-catalog/streams.csv` is unchanged (the script only ever writes the `-CsvPath` it was given).

Verification:
- Survivor count == original test-copy count minus pruned count (the synthetic `.invalid` row is gone). expected: exact match.
- Re-`Import-Csv` of the pruned test copy yields the same 17 columns; a sampled known-good row (e.g. first AUDIO row) is present and field-identical. expected: columns + sample row intact.
- A timestamped backup of the test copy exists under `temp/`. expected: file present.
- `git status --porcelain delivery/stream-catalog/streams.csv` is empty. expected: no change to shipped catalog.

### Phase 3 - Document the prune workflow + close

Goal: make the maintenance workflow discoverable and warn about the geo caveat.

Steps:
1. Update the script comment-help (`.DESCRIPTION`/`.EXAMPLE`) to document `-Prune` (dry-run default, `dead`-only, backup-first) and the conservative-deletion warning already present in `.DESCRIPTION`.
2. In `delivery/stream-catalog/README.md`, add a short "Maintenance: pruning dead links" note - run dry-run first, review `temp/stream-catalog-liveness.csv`, re-probe (ideally from a second network vantage) before applying `-Prune`, then re-publish the release-asset.
3. Run mechanical closure: `scripts/post-change.ps1 -ChangeType Script` for the script, dev log for the ticket.

Verification:
- `.EXAMPLE` block in the script mentions `-Prune`. expected: present (Grep).
- README contains the "pruning dead links" maintenance note. expected: present (Grep).
- Dev log has an S0589 entry. expected: present.

---

## Done criteria (strategic-level)

1. The catalog maintainer can, in one command, delete catalog rows classified `dead` by the liveness probe.
2. Prune is opt-in (`-Prune`); default run stays a non-destructive report.
3. Only conservatively-dead rows (DNS-fail/conn-refused/404/410) are eligible; `unknown` is never deleted.
4. A timestamped backup is written before any catalog mutation; CSV quoting round-trips losslessly.
5. The shipped `delivery/stream-catalog/streams.csv` is not mutated by delivering S0589 (real prune is an owner maintenance run).
6. The maintenance workflow + geo caveat are documented.

---

## Last Audit

**2026-06-21 - Verified (Simple path, dev-tooling).**

Implementation: `scripts/stream_catalog/check-liveness.ps1` gained `[switch] $Prune` + `[string[]] $PruneStatuses = @('dead')`; report-only behaviour byte-unchanged when `-Prune` is absent. Docs: script comment-help + `delivery/stream-catalog/README.md` "Pruning dead rows".

Evidence:
- Phase 1: AST parse 0 errors; `-Prune` and `-PruneStatuses` present in `(Get-Command).Parameters`; default run still prints the liveness summary and exits 0.
- Phase 2: ran `-Prune` against `temp/streams-prune-test.csv` (real catalog + 1 synthetic `.invalid` DNS-fail row). Result: 427 -> 426, synthetic row removed, 17 columns + order preserved, sample row field-identical, timestamped backup written to `temp/`. Real `delivery/stream-catalog/streams.csv` not written (synthetic row absent from it; the `M` in git status is unrelated S0570 catalog-WIP, `+48/-6` row additions).
- Baseline full-catalog probe (426 rows): 404 alive / 22 unknown / 0 dead - a real prune today removes nothing, confirming the conservative `dead`-only policy (runtime log failures France24 HTTP 400 / TRT SSL correctly land in `unknown`, not `dead`).

Residual: actually pruning the shipped catalog is a deliberate owner maintenance run (single-vantage geo caveat documented). No app code, no Timber tags, no `ALL_FEATURES` record (maintainer-only tooling, not a user-facing capability).

Follow-up (separate ticket): `last_online` CSV column + app-side "verified green on successful play" + yellow/unknown resolution - captured as a new strategic spec (see Related).
