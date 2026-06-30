---
name: closure-on-dirty-tree
description: Close a change on the always-dirty tree with post-change.ps1 -ScopeToFile (S0826), not a manual dance
type: feedback
---

`post-change.ps1` chains project-wide gates (detekt, listener-symmetry, neuroslop, flavor-flag, deprecated-pm) that grow on other tickets' WIP - which on this repo is always (single dev, many tickets/file, "working tree is truth"). Plain `post-change.ps1` therefore aborts on findings that aren't yours.

**Fix shipped in S0826 (2026-06-30): add `-ScopeToFile`.**
`pwsh -NoProfile -File scripts/post-change.ps1 -File "<path>" -Target ".." -Description ".." -ChangeType Kotlin -Module app_v2 -ScopeToFile`
- detekt is diff-scoped to `-File` (re-judged via the Checkstyle XML report `<module>/build/reports/detekt/detekt.xml`, not cache-flaky stdout) - fails only on findings in your file.
- the project-wide count-ratchet gates (neuroslop / listener-symmetry / flavor-flag / deprecated-pm) downgrade to advisory SKIP (warn, non-fatal) so the facade closes exit 0 when your own files are clean.
- targeted gates (ticket-log-audit, dialog-cancel, etc.) stay fatal.
Omit `-ScopeToFile` for release/CI to get the strict full-project gate.

Companion fast checks (also S0826): `a.ps1 fkn` (noLegal Kotlin compile), `a.ps1 fg` (batch the 5 fast static gates in one process; `-IncludeDetekt` opt-in).

**Why:** across S0822/S0821/S0823 the plain facade failed on other tickets' detekt/listener growth, so each ticket was closed with hand-run steps - exactly the friction `-ScopeToFile` removes.

**How to apply:** verify `-ScopeToFile` still exists in `scripts/post-change.ps1` (grep) before relying on it. Use it as the default per-change closure on this tree. Manual fallback if it is ever removed: `update.ps1 -Status` + `add_to_dev_log.ps1` + `catalog_sync.ps1` (Kotlin only) + grep-filter the detekt log to your files. See also [[detekt-gate-dirty-tree]], [[write-detekt-clean-first-time]], [[dirty-tree-is-normal-wip]].
