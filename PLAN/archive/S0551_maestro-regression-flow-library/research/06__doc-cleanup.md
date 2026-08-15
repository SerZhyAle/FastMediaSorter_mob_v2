# 06 - Doc cleanup inventory (resolves strategic §6.6)

Discovery 2026-06-20 (read-only). Top-level `maestro/*.md` / `*.txt`.

## KEEP

- `README.md` - directory map, categories, run commands (update to real flow set).
- `INSTALLATION_WINDOWS.md` - real Windows install caveats.
- `TROUBLESHOOTING.md` - real troubleshooting.
- `WRITING_TESTS.md` - authoring guide (host for the oracle convention).
- `AVD_SETUP_FOR_TESTS.md` - needed for 3D test media.
- `TEST_MEDIA_WORKFLOW_GUIDE.md` - precondition setup for 3d-video flows.
- `EXAMPLES.md` - reusable YAML patterns (verify still accurate).
- `INDEX.md` - navigation (only if kept current; else fold into README).

## DELETE (slop / phantom / stale duplicates)

- `FEATURE_TESTS_CATALOG.md` - phantom: claims 36 tests in non-existent `maestro/features/`, links to a foreign `c:/GIT/..` checkout.
- `FEATURE_TESTS_COMPLETE.md` - completion doc for the phantom tests.
- `IMPLEMENTATION_COMPLETE.md`, `SETUP_COMPLETE.md` - `*_COMPLETE` slop, no actionable content.
- `ISSUE_RESOLVED.md` - one-time fix log.
- `MAESTRO_STATUS.txt`, `QUICK_REFERENCE.txt`, `help.txt` - stale snapshots / CLI dumps.
- `MAESTRO_QUICK_START.md`, `QUICK_START.md`, `MAESTRO_SETUP_GUIDE.md`, `MAESTRO_INTEGRATION.md` - duplicate README/INSTALLATION.
- `WINDOWS_MANUAL_INSTALL.md`, `WINDOWS_QUICK_INSTALL.md` - duplicate INSTALLATION_WINDOWS.
- `PATH_FIX_GUIDE.md`, `FIX_WRONG_PACKAGE.md` - one-time fixes; fold any live tip into TROUBLESHOOTING.

Net: ~8 kept, ~16 deleted. Final README must reflect the real on-disk flow set after the rewrite/drop in Phases 02-05.
