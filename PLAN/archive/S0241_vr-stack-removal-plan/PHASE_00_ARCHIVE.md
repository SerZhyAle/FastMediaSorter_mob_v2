# S0241 Phase 00 - Archive Current VR Baseline

Ticket: S0241
Phase status: Done
Goal: preserve the last full VR stack before any removal lands in trunk.

## Scope

- Create archive branch `archive/vr-stack-2026-05` from the current HEAD commit.
- Create tag `vr-stack-2026-05-final` on the same commit.
- Push both refs to `origin`.
- Write `dev/archive/VR_LEGACY_SNAPSHOT.md` as the navigation map for future research.
- Record the preservation step in `dev/CHANGELOG.md` through the helper script.

## Checklist

- [x] Local archive branch created from current HEAD.
- [x] Local archive tag created on the same commit.
- [x] Remote refs pushed to `origin`.
- [x] `dev/archive/VR_LEGACY_SNAPSHOT.md` added.
- [x] Dev changelog entry written.
- [x] Validation captured below.

## Validation

- PASS: `git push origin refs/heads/archive/vr-stack-2026-05:refs/heads/archive/vr-stack-2026-05 refs/tags/vr-stack-2026-05-final`
- PASS: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0241 -Status Tactical`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "dev/archive/VR_LEGACY_SNAPSHOT.md" "S0241 Phase 00" "Added VR legacy snapshot and prepared tactical archive phase for stack removal"`
- PASS: readback of `dev/archive/VR_LEGACY_SNAPSHOT.md` headings and content map.

## Notes

- The archive refs must stay immutable after Phase 00.
- The flat-screen stereo path remains in `src/main` and is explicitly outside the removal set.