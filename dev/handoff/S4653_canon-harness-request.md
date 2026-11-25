<sub class="doc-stamp">26.11.15 15:36</sub>

# S4653 - canon harness change request (supersedes S4368)

Input for the canon update; hand it to the `sza-unified-rules` session that owns `tools/harness/document_registry/`.

- Harness version read: `sza` plugin `2026.1115.4`, `tools/harness/`.
- Consumer: FastMediaSorter_mob_v2. Every local path is a generated forwarder, so none of these changes touches a call site here.
- Supersedes `dev/handoff/S4368_canon-harness-request.md` (harness `2026.1017.2`, canon ticket S0008): the shipped harness still does not answer it.
- Why a request and not an edit: harness bodies are overwritten by the next plugin update and never reach the other consumers.

## 1. A second, local document registry path

- `DOC-INTERNAL-QUALITY` (amendment of 2026-10-17, item K) lets the internal corpus be local and gitignored, with its own registry.
- This repository keeps it at `dev/DOCUMENT_REGISTRY.jsonl`; the tracked `docs/DOCUMENT_REGISTRY.jsonl` indexes tracked files only.
- External: `tools/harness/_profile.ps1:101` (plugin `sza`) declares one key, `documentRegistry = 'docs/DOCUMENT_REGISTRY.jsonl'`; no second key exists under `tools/harness/**`, so `validate.ps1` and `query.ps1` never read the local registry.
- Asked: an optional profile key for a local registry path (for example `paths.localDocumentRegistry`), read by `validate.ps1` (registry -> disk) and `query.ps1` (facets) when the file exists, silent when it does not, because a clone does not carry it.

## 2. The item W fields

- `DOC-INTERNAL-QUALITY` 0.12 item W asks a render to name its generator and its drift check in the one place the gate reads.
- This repository now carries them as two record fields, `generator` and `check` (a command line each; `check` = `none` while no drift check exists).
- Asked: `validate.ps1` accepts both fields and, for a record with `generated` true, refuses a missing `generator` or `check` and a command whose `.ps1` does not exist.

## 3. Until then

- `scripts/quality/assert-document-registry-coverage.ps1` reads the local registry when present (its globs name files, and each must match one) and checks the item W fields of both registries.
- It runs in the release scope (`scripts/quality/assert-release-scope-gates.ps1`).
