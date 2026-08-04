---
name: insert-ps1-file-validation
description: spec_catalog/insert.ps1 -File rejects the skill-doc "PLAN/<placeholder>"; must pass a real ^PLAN/S\d{4}_<slug>.md path, so call next-id.ps1 first
metadata:
  type: project
---

`scripts/spec_catalog/insert.ps1 -File <path>` validates the path against `^PLAN/S\d{4}_(?!spec_)` and **exits 1** on anything else - including the literal `PLAN/placeholder` / `PLAN/<placeholder>` that the `/spec` skill doc tells you to pass ("harmless because step 5 overwrites it"). That documented placeholder flow is broken.

**Why:** the journal `file` field is format-guarded at insert time, not just at update time. The skill doc predates (or ignores) that guard.

**How to apply:** before `insert.ps1`, run `scripts/spec_catalog/next-id.ps1` to get the next `Sxxxx`, build the real `PLAN/<Sxxxx>_<slug>.md` path, and pass THAT to `insert.ps1 -File`. next-id and insert agree on the same next id, so no mismatch. Confirms the returned id equals the next-id token. Related: [[spec-catalog-exit-code-contract]].
