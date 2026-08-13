# Strategic spec: S0584 - ALL_FEATURES records use spec-id as area prefix

**Ticket:** S0584
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-21
**Tier:** 1 - Trivial (ad-hoc)

---

## 0. Captured material (inbox)

**Captured:** 2026-06-21 (parked by `/spec-dev` during S0577 finalization - out-of-scope finding).

**Symptom:** `scripts/all_features/validate.ps1` fails on `docs/ALL_FEATURES.jsonl` with:

- `L367: id 's0575.streams-feature-master-toggle-with-per-device-profile' uses a spec id as area prefix; use the area slug`
- `L368: id 's0559.take-a-screenshot-from-the-app-operations-settings' uses a spec id as area prefix; use the area slug`

**Evidence:** the two records carry a correct `area` ("Streams", "Screen Capture") but an `id` whose prefix is the spec id (`s0575`, `s0559`) instead of the area slug (`streams`, `screen-capture`). The validator requires `<area-slug>.<feature>`.

**Fix direction:** rename each id prefix to the area slug derived from its `area` field, keeping the feature segment, and confirm uniqueness. Re-run `scripts/all_features/validate.ps1` to green. No app code change.

**Note:** pre-existing in the inventory before S0577; S0577's own record (`streams.background-playback-and-exit`) is well-formed. The validate gate is red only because of these two legacy rows.

**Attachments:** none.

---

## Goal

Привести два legacy-записи в `docs/ALL_FEATURES.jsonl` к схеме `<area-slug>.<feature>`, переименовав id-префикс из spec-id в area-slug. Цель - зелёный `scripts/all_features/validate.ps1` без изменения кода приложения, area/name/description/flavors/spec остаются нетронутыми. Это снимает красный гейт `validate.ps1`, унаследованный от старого `add.ps1`.

---

## Phases

### Phase 01 - Rename malformed id prefixes in ALL_FEATURES.jsonl

- Edit `docs/ALL_FEATURES.jsonl` L367: change `id` prefix `s0575` -> `streams`, yielding `streams.streams-feature-master-toggle-with-per-device-profile`. Keep the feature segment and all other fields byte-identical.
- Edit `docs/ALL_FEATURES.jsonl` L368: change `id` prefix `s0559` -> `screen-capture`, yielding `screen-capture.take-a-screenshot-from-the-app-operations-settings`. Keep the feature segment and all other fields byte-identical.
- Derivation rule: area-slug is the lowercase, hyphen-joined slug of the `area` field (`Streams` -> `streams`, `Screen Capture` -> `screen-capture`), matching the convention of sibling records (e.g. `streams.background-playback-and-exit`).
- **Verification:** `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0 (PASS), and `Grep "id":"(streams|screen-capture)\.` shows both renamed ids with unique feature segments (no duplicate-id error).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0575, S0559 (the two malformed records), S0577 (parking context)

<!-- auto-approved by /spec-all - 2026-06-21 -->

---

## Last Audit

**Date:** 2026-06-21 - status `Verified` via `/spec-all` (Simple path).

**Result:** Verified. Both malformed id prefixes renamed to area slugs in `docs/ALL_FEATURES.jsonl`:

- L367: `s0575.streams-feature-master-toggle-with-per-device-profile` -> `streams.streams-feature-master-toggle-with-per-device-profile`
- L368: `s0559.take-a-screenshot-from-the-app-operations-settings` -> `screen-capture.take-a-screenshot-from-the-app-operations-settings`

**Verification:** `scripts/all_features/validate.ps1` exits 0 (`PASS: 370 record(s)`); previously failed with 2 spec-id-prefix errors. Both renamed ids are unique (no duplicate-id error); feature segments and all sibling fields (`area`/`name`/`description`/`flavors`/`spec`/`status`) unchanged.

**Residual gaps:** none. Docs-only change, no app code touched, no build required.
