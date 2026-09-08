# Curated stream collections - the two source files

S2669. A collection is a named, ordered group of streams from `../streams.csv`. One stream may belong
to any number of collections; nothing is duplicated in the bank to express that.

Nothing here is edited by a script. The two files below are the curator's; the third file,
`../collections.json`, is generated from them and must never be hand-edited.

## `rules.json` - the declarations

An array. One object per collection, in no particular file order (the `order` field decides the order
the user sees).

| Field | Meaning |
| --- | --- |
| `id` | stable kebab-case identifier, unique across the file, never reused for a different collection |
| `order` | integer, ascending; decides the position of the collection in the strip on the phone and in the list on the watch |
| `names` | map of BCP-47 language tag to display name; `en`, `ru` and `uk` are all mandatory, any further locale is optional and reaches the user without an app release |
| `rule` | optional object; its fields `country`, `category`, `topic` and `language` each hold an array of values |

A bank row joins the collection when **every field present in `rule` matches at least one of its
values**, compared case-insensitively against the normalized column of the same name. An absent field
is not a constraint. The `language` column of the bank holds a comma-separated list, so a row matches
when any of its languages is in the array.

A `rule` that is absent or empty selects nothing - such a collection is built entirely from the
overlay.

## `overlay.json` - the manual layer

A map from collection `id` to an object with two arrays of stream URLs:

```json
{ "tv-africa": { "include": ["https://.."], "exclude": ["https://.."] } }
```

- `include` adds a URL the rule did not select.
- `exclude` removes a URL the rule did select.
- **`exclude` is applied after `include`**, so an explicit removal always wins, even over an explicit
  addition of the same URL.

An overlay entry naming an id that `rules.json` does not declare is a refusal, not a silent no-op.

## Why two layers

A rule alone cannot express two of the three collections this feature was asked for. "Radio of the
former USSR" is a list of fifteen countries and "Television of Africa" is a continent; neither concept
is a value in the bank. A manual list alone does not scale to nineteen thousand rows and goes stale on
every collection run. The two layers together give both the coverage and the precision.

## Generating and publishing

```powershell
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -BuildCollections
```

writes `../collections.json` and validates it. `-Publish` implies `-BuildCollections`, so a publish
always regenerates from these two files rather than shipping a stale committed artifact.

Never package the archive by hand - see the publishing section of `../README.md`.

## Adding a collection

Add a declaration to `rules.json`, fill the three mandatory locales, regenerate, publish. No app
change and no app release is involved: the name, the order and the membership all travel in the data.
