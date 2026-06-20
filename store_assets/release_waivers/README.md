# Release waivers

Storage convention for standard production release waivers (S0553 §3.3, ADR-2).

- One file per release named `<versionName>.md` (the `versionName` of the published `standardRelease` AAB).
- Each file lists the approved deviations for that release, one block per the `TEMPLATE.md` shape.
- A waiver is a recorded, owner-approved acceptance of a **waiver-eligible** loss (coverage losses §5.4 and temporary subsystem limits). Hard-stop classes (flavor-surface §5.2, release-only technical §5.3, operational/policy §5.5) are **not** waiver-eligible.
- A sufficient waiver record requires: loss-class reference, author, and date.
- Only the **owner** may approve a waiver.
- `scripts/release/standard-release-gate.ps1` reads the per-release waiver file to decide whether a waiver-eligible gap resolves to WAIVED rather than FAIL.
