# Research 01 - CI triggers & cost map

**Spec:** S0752
**Date:** 2026-06-27
**Status:** Resolved
**Question (§6.1):** Which workflows spend GitHub Actions minutes, on which triggers, and is a push to `DEBUG-v0NN` provably free?

## Method

Read all three workflow files under `.github/workflows/` and the branch list.

## Findings

### Workflow triggers

- **android-ci.yml** (Android Lint, Unit Tests, Build matrix of 4 flavors, R8/ProGuard release check, status notify):
  - `push: branches: [ main, develop ]`
  - `pull_request: branches: [ main ]`
  - `workflow_dispatch`
- **maestro-tests.yml** (Android emulator E2E, `ubuntu-latest`, ~35 min):
  - `pull_request: branches: [ main ]`
  - `workflow_dispatch`
- **jekyll-gh-pages.yml** (site deploy):
  - `push: branches: ["main"]`
  - `workflow_dispatch`

### Branch model (observed)

- Current dev branch: `DEBUG-v020`. Branches `DEBUG-v001..v020` exist locally + on origin.
- `main` exists. No `develop` branch exists (local or remote).

## Conclusions

1. **A push to any `DEBUG-v0NN` branch triggers zero workflows.** Every trigger is keyed to `main` (push) or PR targeting `main`. Therefore a test "сборка" committed/pushed via `.\a.ps1 c` to a DEBUG branch costs 0 Actions minutes.
2. **Paid CI fires only at the `main` boundary** - i.e. inside `/skill-release` when DEBUG is merged into `main` (android-ci push + jekyll-gh-pages push), and on any PR targeting `main` (android-ci + maestro).
3. **The `develop` trigger in android-ci.yml is dead** - no such branch exists. Harmless but misleading; removal candidate (spec goal 5).

## Implication for spec

- Goal 2 ("за тестовую сборку не платим") is already guaranteed by the trigger design - the spec documents and protects it rather than building it.
- The only real exposure is an **accidental direct push to `main`** from the dev working copy, which would fire android-ci + jekyll. That is exactly what the guard (§6.2) must intercept.
