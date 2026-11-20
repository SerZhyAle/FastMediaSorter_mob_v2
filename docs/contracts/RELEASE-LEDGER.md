# Pointer - `RELEASE-LEDGER`

| | |
| --- | --- |
| **Id** | `RELEASE-LEDGER` |
| **Version** | 0.1, draft. Owner: sza-unified-rules (the canon) |
| **Home** | `release-ledger/README.md` in the shared contracts catalog (`P:\Contracts\`) |
| **Role here** | adopter - the release ledger of this product's packages and channels; the canon ships the only writer |

## What this repository must do to stay conformant

- One ledger folder, `docs/releases/`, tracked in git: `channels.json` (declared at adoption and by hand), `builds.json` and `history.jsonl` (machine-written by the writer), `RELEASES.md` (a render target, never edited).
- `channels.json` declares three packages - `phone`, `wear`, `watchface` (one per installable unit) - and four channels: `github` (repo `SerZhyAle/FastMediaSorter_mob_v2`, the phone release vehicle), `play` (`com.sza.fastmediasorter`, track `production`), `play-wear` (the same application id, track `wear:production`, S1681), `play-watchface` (`com.sza.fastmediasorter.watchface`, track `wear:production`, S4009 ADR-2).
- Every successful build calls `release-ledger build` as its last step, through `scripts/utils/log-release-ledger-build.ps1`; only the last build per package, flavor and kind is kept, and a ledger write never fails the build.
- Every channel step of a release calls `release-ledger publish`, through `scripts/utils/log-release-ledger-publish.ps1` wired inside the two publishers: a published GitHub release records `live` with the tag and the release page, a Play commit records `submitted` - or `pending` while the commit is held. A channel no probe can see is confirmed by hand with `confirm -Url <public page>`; the URL is the evidence.
- `probe` (after every release, and whenever a `submitted` publication waits for its review) and the one-time adoption `import` are read-only: they write nothing for a channel they cannot see, and they never write to a channel.
- Never in the ledger: a token, a key, a seller, tenant or client id, an absolute local path, a machine or user name.

## Where it lives here

- `docs/releases/` - the ledger folder (`channels.json`, `builds.json`, `history.jsonl`, `RELEASES.md`).
- `scripts/utils/log-release-ledger-build.ps1` - the never-failing wrapper every builder under `scripts/builders/` calls as its last step.
- `scripts/utils/log-release-ledger-publish.ps1` - the never-failing wrapper the two publishers call.
- `scripts/release/publish-github-release.ps1`, `scripts/release/publish-play-release.ps1` - the publishers that record their channel steps (S4679).
- Adoption row: the `RELEASE-LEDGER` table of the catalog registry `P:\Contracts\_meta\REGISTRY.md`.
