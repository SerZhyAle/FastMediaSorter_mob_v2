---
name: compose-stable-field-in-reflection-tests
description: A test that walks declaredFields in this project must skip static fields - the Compose compiler adds a $stable static to classes, and it fails any "every field is annotated" assertion
metadata:
  type: feedback
---

**Rule.** Any test that reflects over `SomeClass::class.java.declaredFields` here must filter out **static** fields, not just synthetic ones: `.filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }`.

**Why.** The Compose compiler plugin adds a static `$stable` field to classes it processes - including plain `data class` domain models that have nothing to do with Compose. It is not synthetic, so `isSynthetic` does not remove it. On 2026-08-14 (S1660) the first run of a brand-new "every persisted field pins its wire name via @SerializedName" guard failed with `expected:<[]> but was:<[$stable]>` on `FileAttributes`, a two-field model. The test was wrong, not the code.

The filter is also the semantically right one, which is what makes it safe rather than a workaround: Gson skips static fields when serializing, so a serialization guard that judged them would be asserting something Gson never looks at.

**How to apply.**

- Writing a Gson `@SerializedName` guard (the S1638 / S1657 / S1660 family, and the next ticket of that class): copy the static filter, not just the synthetic one.
- The same trap waits for any reflection-based assertion over fields - counting them, checking visibility, matching a schema.
- Symptom to recognise instantly: an assertion diff whose only unexpected entry is `$stable`.
