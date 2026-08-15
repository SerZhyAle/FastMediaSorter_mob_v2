# S1032 - Reduce `!!` non-null-assertion usage + ratchet gate

**Status:** Archived
**Priority:** 45
**Date:** 2026-07-15
**Complexity:** Simple (script gate + mechanical burn-down; single concern)

## Goal

`!!` (оператор not-null assertion) - канонический Kotlin-антипаттерн: превращает nullable в
гарантированный `NullPointerException` без восстановления. Аудит S1030 насчитал ~139 таких мест в
`src/main`, но механического запрета на рост не было. Ставим ratchet-гейт, который замораживает
текущее число и блокирует новые `!!`, и сжигаем нулевого-риска подмножество (избыточные guard-идиомы).

## 0. Raw finding (auto-parked from S1030 audit, 2026-07-13)

`docs/CODE_AUDIT_PROTOCOL.md` already lists `!!` as a P3 review item, but there was NO mechanical
enforcement, and detekt's `UnsafeCallOnNullableType` rule is not enabled in `config/detekt/detekt.yml`.
Top offenders included `data/network/datasource/SftpDataSource.kt` (the redundant-guard shape
`if (channel == null || !channel!!.isConnected)`), `ui/player/helpers/VideoPlayerErrorHandler.kt`,
`data/cloud/CloudFileOperationHandler.kt`, `data/network/glide/NetworkFileModelLoader.kt`.

## 1. Decision (approval gate resolved from codebase)

Two routes were on the table (§2 of the parked draft): enable detekt `UnsafeCallOnNullableType`
with a baseline, OR add a custom counting ratchet `assert-*.ps1`.

- **Rejected: the detekt route.** `UnsafeCallOnNullableType` **requires type resolution**, but this
  project's detekt gate runs LEXICALLY - `config/detekt/detekt.yml` states "Do NOT enable rules that
  require type resolution (this gate runs lexically)", and `build.gradle.kts` applies the plain
  `detekt` task with no analysis classpath. Enabling the rule would either be a silent no-op or force
  a full type-resolution detekt reconfiguration (heavy, risky, against the config's stated intent).
- **Chosen: a custom lexical ratchet** `scripts/quality/assert-non-null-assertion.ps1`, cloned from
  the sibling `assert-em-dash.ps1` and registered under the neuroslop umbrella. Matches the existing
  gate family, the "runs lexically / do not over-tune" philosophy, and the fast-gates batch.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1030 (archived parent audit umbrella); sibling ratchet `assert-em-dash.ps1`.

## 2. Phases (all done)

### Phase 01 - ratchet gate `[x]`

- Add `scripts/quality/assert-non-null-assertion.ps1`: Report / `-Gate` / `-UpdateBaseline` / `-List`
  / `-ChangedFiles` delta modes; regex `!!` over `app_v2/src/main/**/*.kt`; single-int baseline in
  `scripts/quality/non-null-assertion-baseline.txt`; ratchet-DOWN-only.
  - Verification: `-Gate` exits 0 at baseline; a fabricated extra `!!` in a changed file makes
    `-Gate -ChangedFiles` exit 1. **PASS.**

### Phase 02 - register under neuroslop `[x]`

- Add the gate to `scripts/quality/assert-neuroslop.ps1` `$children` + KDoc list, so `post-change.ps1`
  and `a.ps1 fg` run it with the other lexical ratchets.
  - Verification: `assert-neuroslop.ps1 -Gate` lists the child and stays green. **PASS.**

### Phase 03 - burn down the zero-risk redundant-guard idiom `[x]`

- `SftpDataSource.kt`: capture the nullable `channel` var into a local `val` at both open/retry
  sites so the null-guard smart-casts; removes 4 `!!` (2 guard `!channel!!.isConnected` + 2
  `attemptOpen(channel!!)` call sites). Behaviour identical.
  - Verification: no `== null || .. !!` idiom remains in the file; `a.ps1 fk` compiles. **PASS.**

### Phase 04 - seed baseline + document `[x]`

- Seed `non-null-assertion-baseline.txt` at the post-burn-down count (141).
- `docs/CODE_AUDIT_PROTOCOL.md`: note the P3 `!!` item is now mechanically ratcheted.
  - Verification: gate delta 0; doc greps for the gate path. **PASS.**

## 3. Done criteria

1. `assert-non-null-assertion.ps1` exists with a seeded, ratchet-down-only baseline. `[x]`
2. Gate blocks a NEW `!!` in a changed file (delta mode) and passes at baseline. `[x]`
3. Gate runs inside the neuroslop umbrella (hence `post-change.ps1` + `fg`). `[x]`
4. The `SftpDataSource` redundant-guard `!!` idioms are gone; the file compiles. `[x]`
5. `docs/CODE_AUDIT_PROTOCOL.md` records the mechanical enforcement. `[x]`
6. No new string keys, no runtime behaviour change. `[x]`

## 4. Notes

- Remaining `!!` (baseline 141) burn down opportunistically per-site; the gate guarantees the count
  only shrinks. `SftpDataSource.kt:175` (`inputStream!!`) is a genuine post-open invariant left in place.
- Parent audit: S1030 (archived umbrella).

## Last Audit

**Date:** 2026-07-15
**Mode:** full (Simple path S4)
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0

New lexical ratchet `scripts/quality/assert-non-null-assertion.ps1` (regex `!!` over `app_v2/src/main/**/*.kt`,
single-int baseline `non-null-assertion-baseline.txt` = 141, ratchet-DOWN-only, Report/`-Gate`/`-UpdateBaseline`/
`-List`/`-ChangedFiles` modes), registered under the neuroslop umbrella so `post-change.ps1` + `a.ps1 fg` run it.
The detekt route was rejected: `UnsafeCallOnNullableType` needs type resolution but this project's detekt gate is
lexical (config header forbids type-resolution rules). Fail-closed proven adversarially (baseline 140 -> exit 1;
141 -> exit 0). Redundant-guard `!!` burned down in `SftpDataSource.kt` via local-val capture (4 `!!` removed,
behaviour identical); `a.ps1 fk` BUILD SUCCESSFUL; scoped detekt clean on the changed file. `docs/CODE_AUDIT_PROTOCOL.md`
records the enforcement. No new string keys, no runtime behaviour change.
