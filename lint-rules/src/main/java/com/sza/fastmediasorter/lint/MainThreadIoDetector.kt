package com.sza.fastmediasorter.lint

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class MainThreadIoDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf(
        "readText", "writeText", "readBytes", "writeBytes", "readLines",
        "copyTo", "createNewFile", "delete", "deleteRecursively"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val isFileIo = method.containingClass?.qualifiedName?.startsWith("kotlin.io") == true ||
            method.containingClass?.qualifiedName == "java.io.File"

        if (!isFileIo) return

        var current: UElement? = node
        var isUiOrViewModel = false
        while (current != null) {
            if (current is UClass) {
                val className = current.name ?: ""
                val isViewModel = current.name?.endsWith("ViewModel") == true ||
                    current.uastSuperTypes.any { it.type.canonicalText.endsWith("ViewModel") || it.asSourceString().contains("ViewModel") }
                val isUi = className.endsWith("Activity") || className.endsWith("Fragment") || className.endsWith("View")
                if (isViewModel || isUi) {
                    isUiOrViewModel = true
                    break
                }
            }
            current = current.uastParent
        }

        if (!isUiOrViewModel) return

        current = node
        var isInsideIoDispatcher = false
        while (current != null) {
            if (current is UCallExpression && current.methodName == "withContext") {
                val arg = current.valueArguments.firstOrNull()?.asRenderString()
                if (arg?.contains("IO") == true) {
                    isInsideIoDispatcher = true
                    break
                }
            }
            current = current.uastParent
        }

        if (!isInsideIoDispatcher) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Blocking I/O operation called on the main thread in a UI or ViewModel class. Wrap it in withContext(Dispatchers.IO)."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "MainThreadIo",
            briefDescription = "Blocking I/O operation on main thread",
            explanation = "Calling blocking File I/O operations directly on the main thread in ViewModels or UI classes can cause UI freezes, lag, and ANR errors. Always execute block I/O on Dispatchers.IO.",
            category = Category.PERFORMANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(MainThreadIoDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
