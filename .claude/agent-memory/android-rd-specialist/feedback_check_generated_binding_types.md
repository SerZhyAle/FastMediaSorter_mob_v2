---
name: check-generated-binding-types
description: When patching a compatibility-view shim around an existing ViewBinding (e.g. .bind(root) on a stripped revised layout), verify each id's actual field type in the generated *Binding.java before picking the Kotlin class for the hidden placeholder
metadata:
  type: feedback
---

**Rule:** When `Foo.bind(root)` is called on a view tree that does not contain every id Foo expects, every hidden compatibility view you inject MUST use the exact Kotlin class declared in the generated `FooBinding.java` field for that id. `Button` vs `MaterialButton`, `LinearLayout` vs `ConstraintLayout`, `TextView` vs `MaterialTextView` - all are silent traps. Generated binding does an unchecked downcast on every accessor; mismatched type produces `ClassCastException` exactly where `findChildViewById` succeeds.

**Why:** Burned twice on S0125 RevisedGeneralSectionBinder (2026-05-19). First fix added 6 hidden views using uniform `android.widget.Button` for every button id. Build succeeded. On-device first open crashed with `ClassCastException: Button cannot be cast to MaterialButton` at `FragmentSettingsGeneralBinding.bind() line 960`. Two of the six buttons (`btnShowLog`, `btnShowSessionLog`) were declared as `MaterialButton` in the generated binding because the legacy XML used `<com.google.android.material.button.MaterialButton>` tag; the other four were plain `<Button>`. The comment "ClassCastException is just as fatal as NullPointerException" was inserted in the first patch but I did not act on it.

**How to apply:**
- Before inserting hidden views, read `app_v2/build/generated/data_binding_base_class_source_out/<variant>/out/com/sza/fastmediasorter/databinding/Foo*Binding.java` and grep `public final <Type> <id>;` for every id you plan to inject.
- Pick the Kotlin class that matches the generated `<Type>` exactly.
- Cross-check the binding against the revised XML for ids that ARE present in both: for each, grep the XML tag and confirm the tag's class matches (or is a subclass of) the binding's declared type. Safe upcasts (binding declares `View`, XML uses `ConstraintLayout`) are fine. Downcasts (binding declares `MaterialButton`, XML uses `<Button>`) crash.
- One-shot pwsh command to scan all binding-vs-XML mismatches lives in the S0125 second-fix log; rebuild it with the bindingFile + revisedXml paths for any new shim.

See related: [[no-scaffolding-as-done]] (don't claim "fix complete" without on-device proof of the failing flow).
