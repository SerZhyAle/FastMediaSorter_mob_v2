---
name: feedback_check_generated_binding_types
description: When patching a compatibility-view shim around an existing ViewBinding (e.g. .bind(root) on a stripped revised layout), verify each id's actual field type in the generated *Binding.java before picking the Kotlin class for the hidden placeholder
metadata:
  type: feedback
---

**Rule:** When `Foo.bind(root)` is called on a view tree that does not contain every id Foo expects, every hidden compatibility view you inject MUST use the exact Kotlin class declared in the generated `FooBinding.java` field for that id. `Button` vs `MaterialButton`, `LinearLayout` vs `ConstraintLayout`, `TextView` vs `MaterialTextView` - all are silent traps. Generated binding does an unchecked downcast on every accessor; mismatched type produces `ClassCastException` exactly where `findChildViewById` succeeds.

**Why:** Burned twice on S0125 RevisedGeneralSectionBinder (2026-05-19). First fix added 6 hidden views using uniform `android.widget.Button` for every button id. Build succeeded. On-device first open crashed with `ClassCastException: Button cannot be cast to MaterialButton` at `FragmentSettingsGeneralBinding.bind() line 960`. Two of the six buttons (`btnShowLog`, `btnShowSessionLog`) were declared as `MaterialButton` in the generated binding because the legacy XML used `<com.google.android.material.button.MaterialButton>` tag; the other four were plain `<Button>`. The comment "ClassCastException is just as fatal as NullPointerException" was inserted in the first patch but I did not act on it.

**How to apply:**
- When implementing a compat shim that injects hidden views before a `.bind(root)` call (so the binding's expected ids are present even though the visible layout has been trimmed), open `app_v2/build/generated/data_binding_base_class_source_out/<variant>/out/com/sza/fastmediasorter/databinding/Foo*Binding.java` and grep `public final <Type> <id>;` for every id being injected. Pick the Kotlin class that exactly matches the generated `<Type>` - not the closest `widget.*` superclass.
- For ids present in BOTH the revised XML and the generated binding, grep the XML tag and confirm the tag's class equals (or extends) the binding's declared type. Safe upcasts (binding declares `View`, XML uses `ConstraintLayout`) are fine. Downcasts (binding declares `MaterialButton`, XML uses `<Button>`) crash on inflate.
- After any `.bind(root)` call in a `*Fragment` / `*Activity` / `*View`, before flipping the step to done, build the actual variant and inspect the generated binding's field types - the build succeeds for any plausible cast, the crash happens at first inflate.
- A one-shot pwsh script to scan all binding-vs-XML mismatches lives in the S0125 second-fix log; reuse it (rebuild with bindingFile + revisedXml paths) for any new shim instead of eyeballing.

See related: [[feedback_no_scaffolding_as_done]] (don't claim "fix complete" without on-device proof of the failing flow).
