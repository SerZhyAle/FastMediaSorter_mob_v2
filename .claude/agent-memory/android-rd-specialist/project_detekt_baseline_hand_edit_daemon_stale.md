---
name: project_detekt_baseline_hand_edit_daemon_stale
description: Hand-editing config/detekt/baseline-<module>.xml to add a suppression is ignored by a warm Gradle daemon until it is stopped
metadata:
  type: project
---
Manually adding an `<ID>` entry to `config/detekt/baseline-app_v2.xml` (or `-wear.xml`) to suppress a known-legitimate, pre-existing finding is NOT picked up by an already-warm Gradle daemon - the scoped detekt gate (`assert-detekt.ps1 -ChangedFiles ...`) keeps reporting the exact same finding as NEW even though the file on disk is correct (verified byte-exact, correct XML section, alphabetically placed). The detekt task genuinely re-executes (fresh `detekt.txt`/`detekt.xml` timestamps) but still applies a stale in-memory baseline. Forcing `--rerun-tasks --no-configuration-cache` is a red herring here - it appears to bypass baseline application entirely (234 issues reported project-wide) and is not diagnostic.

**Why:** discovered 2026-07-03 fixing S0700/S0900 - added two legitimate `InstanceOfCheckForException:...$t is CancellationException` baseline entries (same accepted idiom used unsuppressed in 10+ other files already) after the S0900 ticket's own earlier "detekt-clean" claim turned out to not have re-frozen the baseline. The entries were textually correct but kept failing the scoped gate until the daemon was restarted.

**How to apply:** after hand-editing a detekt baseline XML file, run `.\gradlew.bat --stop` before re-running the detekt gate (scoped or full). If a scoped `assert-detekt.ps1 -ChangedFiles` run still flags a file right after a baseline edit that looks correct, suspect daemon staleness first - restart the daemon before spending more time diffing the XML. Do not use `--rerun-tasks --no-configuration-cache` to "force" a fresh read; it produces a much worse false signal (baseline looks totally unapplied). Distinct from [[feedback_detekt_baseline_signature_resurface]] (signature drift from a code change, fixed with `@Suppress` or plain `--rerun-tasks`) - this is a plain hand-edit-not-reloaded case, fixed with a full daemon stop.
