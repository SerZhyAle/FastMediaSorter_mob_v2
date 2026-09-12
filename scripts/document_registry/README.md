# Document registry - how a page gets announced

`docs/DOCUMENT_REGISTRY.jsonl` is the source of truth for maintained documents and site pages.
`generate.ps1` renders `docs/DOCS_MAP.md` and `sitemap.xml` from it; `validate.ps1` guards it; both are
invoked by the closure facade whenever a registered document changes. Never hand-edit the two
generated files.

## The model (S1803)

Four sentences, and they are the whole contract:

1. **The page declares its own address.** A `permalink:` line in the page's front matter is what puts
   it in the sitemap. Adding a page to a group that already exists needs no registry edit.
2. **The record decides publication for the group.** `published` and `indexable` are properties of the
   group, not of any one page; a record that is not indexable announces nothing.
3. **A page that must not be announced is named in its record, with a reason.** That is the
   `sitemap_exclude` field: a list of `{path, reason}`. The reason is written for someone re-judging it
   a year later - say what the file is and who it is for. "Internal" is not a reason, and `validate.ps1`
   refuses a reason shorter than four words.
4. **A file without a declared address is not a page.** JSON inputs, generated dumps and notes live
   under the same globs as real pages; they are named in `sitemap_exclude` so the record says out loud
   that they were considered and are not pages, rather than leaving them silently absent.

## What the validator enforces

- Every file under an indexable record is one of three things: it declares a permalink, it is named in
  `sitemap_exclude`, or it is the source of an address the record itself declares (the site root is
  backed by `index.html` / `README.md`). Anything else fails with the file named and the fix spelled
  out - that is the check that stops an internal note from reaching a search engine.
- Every address a record declares by hand resolves to a page that exists. An address answering with an
  error is worse than a page nobody announced.
- Every `sitemap_exclude` entry names a file that exists and carries a reason of at least four words.

## The other direction: disk -> registry (S2618)

`validate.ps1` only ever asks whether a record finds a file. It never asks whether a file found a
record, so a directory full of documents that no record names failed nothing and survived - which is
how the missing `.claude/rules/*.md` glob lasted until S2607, while `dev/RULE_AND_SKILL_AUTHORING.md`
called that record exhaustive.

`scripts/quality/assert-document-registry-coverage.ps1` asks the reverse question at release scope
(step 0.4 of `/spec-prerelease`, via `assert-release-scope-gates.ps1`):

- A **document** is a `.md` file under one of the roots the gate declares. The roots are written out
  in the script and are deliberately not derived from git, which answers "is this maintained" wrongly
  in both directions here: `.claude/` is gitignored in full yet holds the largest registered surface
  in the repository, and `node_modules/` is tracked with thousands of files that are nobody's.
- It judges **directories, not files**. The failure it exists for is a whole tree no glob reaches; a
  per-file rule would demand a justification for every one-off design note under `dev/`.
- A directory must be reached by a record glob, or named in
  `scripts/quality/document-registry-coverage-baseline.txt` with a reason of at least four words.
  A parent record does not cover a child directory - that is exactly the shape that was missed.
- A baseline row naming a directory that no longer holds documents fails too, so the list cannot
  quietly accumulate decisions about nothing.

Answering a refusal: register the directory (a new record, or a glob on an existing one), or add a
baseline row saying what the tree is and who it is for. Excusing is the second-best answer - a row
there means the repository agreed to stop maintaining that tree as documentation.

## Adding a page

Drop it under an existing group's globs with a `permalink:` and you are done - the sitemap picks it up
on the next `generate.ps1`. If it should not be announced, add it to that record's `sitemap_exclude`
with a reason. If it belongs to no existing group, add a record.
