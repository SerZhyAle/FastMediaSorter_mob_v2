package com.sza.fastmediasorter.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField

/**
 * Reports a UI Context, Activity, Fragment or View retained by a genuinely long-lived object.
 *
 * S1195: this rule used to admit any class whose name merely ended in "Manager", which collides
 * head-on with CLAUDE.md Rule 3 - `NounVerbManager` is this codebase's mandated name for the
 * Activity-scoped helper delegates under `ui/<feature>/helpers`, which are constructed by and die
 * with their Activity. Holding a View is their whole job. Simultaneously the rule was blind to the
 * leaks it exists to catch, because it compared type names literally: `.endsWith(".View")` never
 * matched TextView or RecyclerView, and `.endsWith(".Activity")` never matched AppCompatActivity.
 * Lifetime now comes from `@Singleton` / ViewModel only, and types resolve through their
 * supertypes.
 */
class UiContextLeakDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (!isLongLived(node)) return

            for (field in node.fields) {
                // Synthetic INSTANCE / Companion fields carry the enclosing class's own type and
                // have no source to annotate, so they can never be a real retention decision.
                if (field.sourcePsi == null) continue
                if (!isUiType(field.type)) continue
                if (hasSafeQualifier(node, field)) continue

                context.report(
                    ISSUE,
                    field,
                    context.getLocation(field as UElement),
                    "Do not store a UI Context (Activity, Fragment, View) in a long-lived ViewModel " +
                        "or Singleton - it outlives the UI and leaks it. Inject @ApplicationContext, " +
                        "or hold a WeakReference."
                )
            }
        }
    }

    /**
     * A name suffix is not a lifetime. Only a Hilt scope annotation or a ViewModel supertype
     * proves the object outlives the UI it references.
     */
    private fun isLongLived(node: UClass): Boolean {
        val isSingleton = node.annotations.any { it.qualifiedName?.endsWith("Singleton") == true }
        val isViewModel = node.name?.endsWith("ViewModel") == true ||
            node.uastSuperTypes.any {
                it.type.canonicalText.endsWith("ViewModel") || it.asSourceString().contains("ViewModel")
            }
        return isSingleton || isViewModel
    }

    /**
     * Matches the four UI roots or any subclass. Subclass resolution is what closes the rule's
     * false negative - a TextView in a ViewModel was never reported before.
     *
     * A WeakReference needs no special case: it resolves to java.lang.ref.WeakReference, which is
     * not a UI root, so a wrapped Activity is structurally clean rather than clean by exception.
     */
    private fun isUiType(type: PsiType?): Boolean {
        val resolved = (type as? PsiClassType)?.resolve() ?: return false
        return resolved.matchesUiRoot()
    }

    private fun PsiClass.matchesUiRoot(): Boolean {
        // Pruned before the root check: Application and Service reach Context through
        // ContextWrapper, so without this an Application subclass would look like a UI Context.
        if (qualifiedName in APPLICATION_SCOPED_TYPES) return false
        if (qualifiedName in UI_CONTEXT_ROOTS) return true
        return supers.any { it.matchesUiRoot() }
    }

    /**
     * The guarantee that a Context is safe is the Hilt qualifier, never the spelling of the field.
     * Kotlin puts an annotation applicable to both parameter and field on the constructor
     * PARAMETER by default, so `field.annotations` alone misses `@ApplicationContext private val
     * context: Context` entirely - hence the second lookup through the constructor parameters,
     * which also covers the explicit `@param:` spelling. `@ActivityContext` is a deliberate,
     * annotated opt-in and likewise not reported.
     */
    private fun hasSafeQualifier(node: UClass, field: UField): Boolean {
        if (field.annotations.any { it.qualifiedName in SAFE_CONTEXT_QUALIFIERS }) return true
        // Resolved through UAST rather than by annotation short name: Kotlin import aliases can
        // rename the identifier at the use site, so only the qualified name is trustworthy.
        return node.methods
            .filter { it.isConstructor }
            .flatMap { it.uastParameters }
            .filter { it.name == field.name }
            .any { parameter -> parameter.uAnnotations.any { it.qualifiedName in SAFE_CONTEXT_QUALIFIERS } }
    }

    companion object {
        private val UI_CONTEXT_ROOTS = setOf(
            "android.content.Context",
            "android.app.Activity",
            "androidx.fragment.app.Fragment",
            "android.view.View"
        )

        // Application and Service ARE Contexts but their lifetime is the process, so retaining one
        // in a singleton is correct rather than a leak.
        private val APPLICATION_SCOPED_TYPES = setOf(
            "android.app.Application",
            "android.app.Service"
        )

        private val SAFE_CONTEXT_QUALIFIERS = setOf(
            "dagger.hilt.android.qualifiers.ApplicationContext",
            "dagger.hilt.android.qualifiers.ActivityContext"
        )

        val ISSUE = Issue.create(
            id = "UiContextLeak",
            briefDescription = "UI Context stored in long-lived component",
            explanation = "Storing a UI Context (Activity, Fragment, View, or any subclass such as " +
                "AppCompatActivity or TextView) in a ViewModel or an @Singleton keeps the whole view " +
                "hierarchy alive after the UI is destroyed. Inject @ApplicationContext for " +
                "process-lifetime work, annotate a deliberate activity scope with @ActivityContext, " +
                "or hold a WeakReference. Activity-scoped helpers are not long-lived and are not " +
                "reported.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(UiContextLeakDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
