package com.sza.fastmediasorter.lint

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class UnsafeFlowCollectDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("collect", "collectLatest")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass?.qualifiedName
        val isFlow = evaluator.isMemberInSubClassOf(method, "kotlinx.coroutines.flow.Flow", false) ||
            containingClass == "kotlinx.coroutines.flow.Flow" || containingClass?.endsWith(".Flow") == true || method.containingClass?.name == "Flow"
        if (!isFlow) {
            return
        }

        var current: UElement? = node
        var isInsideLifecycleScopeLaunch = false
        var hasRepeatOnLifecycle = false
        var hasFlowWithLifecycle = false

        while (current != null) {
            if (current is UCallExpression) {
                val methodName = current.methodName
                val receiver = current.receiver?.asRenderString()
                if (receiver?.contains("lifecycleScope") == true && methodName == "launch") {
                    isInsideLifecycleScopeLaunch = true
                }
                if (methodName == "repeatOnLifecycle") {
                    hasRepeatOnLifecycle = true
                }
                if (methodName == "flowWithLifecycle") {
                    hasFlowWithLifecycle = true
                }
            }
            current = current.uastParent
        }

        if (isInsideLifecycleScopeLaunch && !hasRepeatOnLifecycle && !hasFlowWithLifecycle) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Collecting Flow directly in lifecycleScope.launch is unsafe. Use repeatOnLifecycle or flowWithLifecycle to prevent background resource usage and leaks."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UnsafeFlowCollect",
            briefDescription = "Lifecycle-unsafe Flow collection",
            explanation = "Collecting Kotlin Flows directly inside lifecycleScope.launch without repeatOnLifecycle or flowWithLifecycle will continue collecting in the background, consuming resources and potentially causing crashes. Use repeatOnLifecycle(Lifecycle.State.STARTED).",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(UnsafeFlowCollectDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
