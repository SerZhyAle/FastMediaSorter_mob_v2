package com.sza.fastmediasorter.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Reports blocking file I/O reachable on the main thread from a UI or ViewModel class.
 *
 * S1195: the rule recognised only a literal enclosing `withContext(..)` whose argument's rendered
 * text contained the letters "IO", and its analysis stopped at the enclosing function body - so a
 * private helper called exclusively from `withContext(Dispatchers.IO) { .. }` was reported as if it
 * ran on the main thread. It now understands the other confinement forms this codebase uses and
 * resolves the dispatcher instead of string-matching it.
 */
class MainThreadIoDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "readText", "writeText", "readBytes", "writeBytes", "readLines",
        "copyTo", "createNewFile", "delete", "deleteRecursively"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val owner = method.containingClass?.qualifiedName
        val isFileIo = owner?.startsWith("kotlin.io") == true || owner == "java.io.File"
        if (!isFileIo) return

        if (!isInUiOrViewModel(node)) return
        if (isConfined(node)) return
        if (isConfinedByEveryCaller(context, node)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Blocking I/O operation reachable on the main thread in a UI or ViewModel class. " +
                "Wrap it in withContext(Dispatchers.IO)."
        )
    }

    private fun isInUiOrViewModel(node: UCallExpression): Boolean {
        var current: UElement? = node
        while (current != null) {
            if (current is UClass) {
                val className = current.name ?: ""
                val isViewModel = className.endsWith("ViewModel") ||
                    current.uastSuperTypes.any {
                        it.type.canonicalText.endsWith("ViewModel") || it.asSourceString().contains("ViewModel")
                    }
                val isUi = className.endsWith("Activity") || className.endsWith("Fragment") ||
                    className.endsWith("View")
                if (isViewModel || isUi) return true
            }
            current = current.uastParent
        }
        return false
    }

    /**
     * Walks outward to the enclosing function only - confinement established by an outer class is
     * not this call's confinement. A suspend function is never assumed to be on the main thread.
     */
    private fun isConfined(start: UElement): Boolean {
        var current: UElement? = start
        while (current != null) {
            if (current is UCallExpression && isDispatcherSwitch(current)) return true
            if (current is UMethod) return isBackgroundMethod(current)
            current = current.uastParent
        }
        return false
    }

    private fun isDispatcherSwitch(call: UCallExpression): Boolean {
        if (call.methodName !in COROUTINE_CONFINERS) return false
        return call.valueArguments.any { isBackgroundDispatcher(it) }
    }

    /**
     * Resolves the argument to the actual Dispatchers field. The old `asRenderString().contains("IO")`
     * would accept `Dispatchers.Main` passed through a variable whose name merely contained "IO",
     * and Kotlin import aliases can rename the identifier at the use site, so only the resolved
     * qualified name is trustworthy.
     */
    private fun isBackgroundDispatcher(expression: UExpression): Boolean {
        val unwrapped = expression.skipParenthesizedExprDown()
        val target = if (unwrapped is UQualifiedReferenceExpression) unwrapped.selector else unwrapped
        // A Kotlin `val` on an object resolves to either the backing field or its getter,
        // depending on the light-class shape - accept both rather than betting on one.
        val (owner, name) = when (val resolved = (target as? UResolvable)?.resolve()) {
            is PsiField -> resolved.containingClass?.qualifiedName to resolved.name
            is PsiMethod -> resolved.containingClass?.qualifiedName to resolved.name.removePrefix("get")
            else -> return false
        }
        return owner == DISPATCHERS_FQN && name in BACKGROUND_DISPATCHERS
    }

    private fun isBackgroundMethod(method: UMethod): Boolean {
        if (method.uAnnotations.any { it.qualifiedName == WORKER_THREAD_FQN }) return true
        // A suspend function carries a trailing Continuation parameter in its light signature.
        return method.javaPsi.parameterList.parameters.lastOrNull()
            ?.type?.canonicalText?.startsWith(CONTINUATION_FQN) == true
    }

    /**
     * The intra-procedural escape hatch, kept deliberately narrow: a PRIVATE function in the SAME
     * file whose every call site is already confined cannot run on the main thread. Anything wider
     * would need a call graph, which this rule has no business building.
     */
    private fun isConfinedByEveryCaller(context: JavaContext, node: UCallExpression): Boolean {
        val enclosing = enclosingMethod(node) ?: return false
        if (!enclosing.javaPsi.hasModifierProperty(PsiModifier.PRIVATE)) return false
        val file = context.uastFile ?: return false

        val target = enclosing.javaPsi
        val callSites = mutableListOf<UCallExpression>()
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(call: UCallExpression): Boolean {
                if (call.resolve() == target) callSites += call
                return super.visitCallExpression(call)
            }
        })
        // No call site in this file means the helper is reached from somewhere unanalysed - report.
        return callSites.isNotEmpty() && callSites.all { isConfined(it) }
    }

    private fun enclosingMethod(node: UElement): UMethod? {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }

    companion object {
        private const val DISPATCHERS_FQN = "kotlinx.coroutines.Dispatchers"
        private const val WORKER_THREAD_FQN = "androidx.annotation.WorkerThread"
        private const val CONTINUATION_FQN = "kotlin.coroutines.Continuation"

        private val COROUTINE_CONFINERS = setOf("withContext", "launch", "async")
        private val BACKGROUND_DISPATCHERS = setOf("IO", "Default")

        val ISSUE = Issue.create(
            id = "MainThreadIo",
            briefDescription = "Blocking I/O operation on main thread",
            explanation = "Calling blocking File I/O directly on the main thread in a ViewModel or UI " +
                "class causes jank and ANRs. Confine it with withContext(Dispatchers.IO), a coroutine " +
                "builder with an IO or Default dispatcher, a suspend function, or a @WorkerThread " +
                "function.",
            category = Category.PERFORMANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(MainThreadIoDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
