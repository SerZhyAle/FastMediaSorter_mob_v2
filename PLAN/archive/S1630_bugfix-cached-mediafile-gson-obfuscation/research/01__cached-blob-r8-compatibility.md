# S1630 research 01 - Cached blob and R8 compatibility

## Question

Can a cached Gson payload from a previous release populate an invalid media-file object after R8 changes field names?

## Evidence

- The cached file-list persistence boundary serializes a list of media-file objects directly with Gson, compresses it, and stores the bytes in one Room row.
- The media-file model contains required Kotlin properties, including `path`, but has neither field annotations nor a custom adapter.
- Existing ProGuard rules retain field names for other Gson-persisted models specifically to preserve cross-version JSON compatibility, but do not cover this model.
- The captured release stack resolves the null check to path normalization during resource-statistics computation. A Gson result with an absent JSON key can therefore reach a Kotlin non-null property and fail only at its later use.
- The cache reader already treats parsing failures as a cache miss. It does not yet validate a successfully parsed but incomplete object, so this is the missing boundary check.

## Result

Resolved. The failure mechanism is consistent with a mapping-dependent persisted JSON blob. The corrective contract is a narrow field-name keep rule for future snapshots plus validation and deletion of invalid stored snapshots before consumers receive them.
