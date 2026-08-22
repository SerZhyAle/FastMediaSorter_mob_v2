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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Reports blocking network I/O calls that are not explicitly confined to a background dispatcher.
 *
 * S1810: Network data sources perform blocking socket I/O (smbj, commons-net, jsch) and must
 * explicitly switch to a background dispatcher (e.g. `withContext(Dispatchers.IO)`) rather than
 * running on the caller's coroutine dispatcher.
 *
 * Crucially: unlike MainThreadIoDetector, a suspend function without an enclosing dispatcher
 * switch is NOT treated as confined, because suspend functions execute on the caller's dispatcher.
 */
class NetworkDataSourceDispatcherDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val psiMethod = node.resolve() ?: return
            val owner = psiMethod.containingClass?.qualifiedName ?: return
            if (!isNetworkLibraryOwner(owner)) return

            if (isConfined(node)) return
            if (isConfinedByEveryCaller(context, node)) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Network I/O operation calling `$owner.${psiMethod.name}` without background dispatcher " +
                    "confinement. Wrap in withContext(Dispatchers.IO)."
            )
        }
    }

    private fun isNetworkLibraryOwner(owner: String): Boolean {
        return owner.startsWith("com.hierynomus.") ||
            owner.startsWith("org.apache.commons.net.") ||
            owner.startsWith("com.jcraft.jsch.")
    }

    private fun isConfined(start: UElement): Boolean {
        var current: UElement? = start
        while (current != null) {
            if (current is UCallExpression && isDispatcherSwitch(current)) return true
            if (current is UMethod && hasWorkerThreadAnnotation(current)) return true
            current = current.uastParent
        }
        return false
    }

    private fun isDispatcherSwitch(call: UCallExpression): Boolean {
        if (call.methodName !in COROUTINE_CONFINERS) return false
        return call.valueArguments.any { isBackgroundDispatcher(it) }
    }

    private fun isBackgroundDispatcher(expression: UExpression): Boolean {
        val unwrapped = expression.skipParenthesizedExprDown()
        val target = if (unwrapped is UQualifiedReferenceExpression) unwrapped.selector else unwrapped
        val resolved = (target as? UResolvable)?.resolve() ?: return false
        if (isDispatchersMember(resolved)) return true

        if (resolved is PsiField) {
            val uField = UastFacade.findPlugin(resolved)?.convertElementWithParent(resolved, null) as? UField
            val initializer = uField?.uastInitializer
            if (initializer != null && isBackgroundDispatcher(initializer)) {
                return true
            }
        }
        return false
    }

    private fun isDispatchersMember(resolved: com.intellij.psi.PsiElement): Boolean {
        val (owner, name) = when (resolved) {
            is PsiField -> resolved.containingClass?.qualifiedName to resolved.name
            is PsiMethod -> resolved.containingClass?.qualifiedName to resolved.name.removePrefix("get")
            else -> return false
        }
        return owner == DISPATCHERS_FQN && name in BACKGROUND_DISPATCHERS
    }

    private fun hasWorkerThreadAnnotation(method: UMethod): Boolean {
        return method.uAnnotations.any { it.qualifiedName == WORKER_THREAD_FQN }
    }

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

        private val COROUTINE_CONFINERS = setOf("withContext", "launch", "async")
        private val BACKGROUND_DISPATCHERS = setOf("IO", "Default")

        val ISSUE = Issue.create(
            id = "NetworkDataSourceDispatcher",
            briefDescription = "Unconfined blocking network I/O call",
            explanation = "Network data source operations perform blocking socket I/O and must explicitly switch " +
                "to a background dispatcher (e.g. withContext(Dispatchers.IO)) rather than running on the " +
                "caller's coroutine dispatcher.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(NetworkDataSourceDispatcherDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
