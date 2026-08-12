---
name: drift-check-false-positive-on-commit-mention
description: drift-check.ps1 reports DRIFT from a commit-message mention alone; read its "code markers" count before believing the verdict
metadata:
  type: feedback
---

`scripts/spec_catalog/drift-check.ps1` exit 1 (`DRIFT`) does **not** mean the ticket is implemented. It
ORs two signals, and prints them separately:

- `git commits with Sxxxx marker: N` - matches a commit *message*, so an unrelated ticket that merely
  named this id in its message trips it.
- `code markers (Sxxxx:): N in M file(s)` - actual `// Sxxxx:` / `Timber.d("Sxxxx:` markers in
  `app_v2/src`.

A verdict with commits >= 1 and **code markers 0** is the false-positive shape. Read both lines before
acting on the verdict, then confirm against the working tree.

**Why:** on 2026-08-08, S1206 (live contact data for launcher cells) reported `DRIFT` from one commit
dated four days before the ticket's own work, with `code markers: 0 in 0 files`. `/spec-all`'s Stage 0a
maps `DRIFT` + no `## Last Audit` to "switch to review mode, write an audit block, jump to F5 or set
`BlockNeedUserTest`" - which would have declared a ticket with literally zero implementation finished.
Reading the live code took one file: `ContactSnapshotDataSource` reads only at pick time and nothing in
`app_v2/src` performed a live `ContactsContract` read at all.

**How to apply:** on any `DRIFT` verdict, check the `code markers` count first. Zero code markers means
the evidence is a commit message, not code - verify against the working tree (CLAUDE.md: working tree is
truth, git history misleads with many tickets per file) and continue with the normal Resume Map instead of
review mode. Only route to review mode / F5 when markers actually exist in source. Related:
[[feedback_verify_spec_id_before_pipeline]], [[feedback_old_capture_may_be_superseded]].
