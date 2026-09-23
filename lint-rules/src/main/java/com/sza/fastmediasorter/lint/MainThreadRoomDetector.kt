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
import com.intellij.psi.util.InheritanceUtil
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
 * Reports blocking Room database or DAO access reachable on the main thread from a UI or
 * ViewModel class.
 *
 * S3371: the sixth member of the audit protocol's lifecycle detector set. The failure it names is
 * harder than [MainThreadIoDetector]'s - Room refuses a blocking query on the main thread at
 * runtime rather than merely stalling it - but the reachability model is the same one, and so is
 * the dispatcher resolution: the argument is resolved to its declaring `Dispatchers` member
 * instead of string-matched, because a Kotlin import alias can rename the identifier at the use
 * site and a variable whose name merely contains "IO" is not a dispatcher.
 *
 * Main-safe by construction and therefore never reported: a `suspend` DAO method, which Room
 * itself moves off the caller's thread, and a method returning an observable holder - `Flow`,
 * `LiveData`, `PagingSource`, `DataSource.Factory` - whose query runs when the holder is
 * collected, not where it is declared.
 *
 * The trigger set cannot be a list of method names the way file I/O is: a DAO method is named by
 * whoever wrote the DAO, so membership is decided by the Room annotations and by
 * `RoomDatabase` inheritance instead.
 */
class MainThreadRoomDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            // Cheapest filter first: resolving every call expression in a file is the expensive
            // half, and outside a UI or ViewModel class the result is discarded anyway.
            if (!isInUiOrViewModel(node)) return
            val method = node.resolve() ?: return
            if (!isRoomMember(method)) return
            if (isMainSafeShape(method)) return
            if (isConfined(node)) return
            if (isConfinedByEveryCaller(context, node)) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Blocking Room access reachable on the main thread in a UI or ViewModel class. " +
                    "Confine it with withContext(Dispatchers.IO), or make the DAO method suspend."
            )
        }
    }

    private fun isRoomMember(method: PsiMethod): Boolean {
        val owner = method.containingClass
        return method.annotations.any { it.qualifiedName in ROOM_METHOD_ANNOTATIONS } ||
            owner?.annotations?.any { it.qualifiedName == DAO_FQN } == true ||
            (owner != null && InheritanceUtil.isInheritor(owner, ROOM_DATABASE_FQN) &&
                method.name in ROOM_DATABASE_MEMBERS)
    }

    /** A shape Room executes off the caller's thread on its own - reporting it would be noise. */
    private fun isMainSafeShape(method: PsiMethod): Boolean {
        val returnType = method.returnType?.canonicalText ?: ""
        return isSuspend(method) || MAIN_SAFE_RETURN_TYPES.any { returnType.startsWith(it) }
    }

    /** A suspend function carries a trailing Continuation parameter in its light signature. */
    private fun isSuspend(method: PsiMethod): Boolean =
        method.parameterList.parameters.lastOrNull()
            ?.type?.canonicalText?.startsWith(CONTINUATION_FQN) == true

    private fun isInUiOrViewModel(node: UCallExpression): Boolean {
        var current: UElement? = node
        var inside = false
        while (current != null && !inside) {
            if (current is UClass) inside = isUiOrViewModelClass(current)
            current = current.uastParent
        }
        return inside
    }

    private fun isUiOrViewModelClass(node: UClass): Boolean {
        val className = node.name ?: ""
        val isViewModel = className.endsWith("ViewModel") ||
            node.uastSuperTypes.any {
                it.type.canonicalText.endsWith("ViewModel") || it.asSourceString().contains("ViewModel")
            }
        val isUi = className.endsWith("Activity") || className.endsWith("Fragment") ||
            className.endsWith("View")
        return isViewModel || isUi
    }

    /**
     * Walks outward to the enclosing function only - confinement established by an outer class is
     * not this call's confinement. A suspend function is never assumed to be on the main thread.
     */
    private fun isConfined(start: UElement): Boolean {
        var current: UElement? = start
        var confined = false
        while (current != null) {
            if (current is UCallExpression && isDispatcherSwitch(current)) {
                confined = true
                break
            }
            if (current is UMethod) {
                confined = isBackgroundMethod(current)
                break
            }
            current = current.uastParent
        }
        return confined
    }

    private fun isDispatcherSwitch(call: UCallExpression): Boolean =
        call.methodName in COROUTINE_CONFINERS && call.valueArguments.any { isBackgroundDispatcher(it) }

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

    private fun isBackgroundMethod(method: UMethod): Boolean =
        method.uAnnotations.any { it.qualifiedName == WORKER_THREAD_FQN } || isSuspend(method.javaPsi)

    /**
     * The intra-procedural escape hatch, kept deliberately narrow: a PRIVATE function in the SAME
     * file whose every call site is already confined cannot run on the main thread. Anything wider
     * would need a call graph, which this rule has no business building.
     */
    private fun isConfinedByEveryCaller(context: JavaContext, node: UCallExpression): Boolean {
        val enclosing = enclosingMethod(node)
        val file = context.uastFile
        if (enclosing == null || file == null ||
            !enclosing.javaPsi.hasModifierProperty(PsiModifier.PRIVATE)
        ) {
            return false
        }

        val target = enclosing.javaPsi
        val callSites = mutableListOf<UCallExpression>()
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.resolve() == target) callSites += node
                return super.visitCallExpression(node)
            }
        })
        // No call site in this file means the helper is reached from somewhere unanalysed - report.
        return callSites.isNotEmpty() && callSites.all { isConfined(it) }
    }

    private fun enclosingMethod(node: UElement): UMethod? {
        var current: UElement? = node.uastParent
        var found: UMethod? = null
        while (current != null && found == null) {
            if (current is UMethod) found = current
            current = current.uastParent
        }
        return found
    }

    companion object {
        private const val DISPATCHERS_FQN = "kotlinx.coroutines.Dispatchers"
        private const val WORKER_THREAD_FQN = "androidx.annotation.WorkerThread"
        private const val CONTINUATION_FQN = "kotlin.coroutines.Continuation"
        private const val DAO_FQN = "androidx.room.Dao"
        private const val ROOM_DATABASE_FQN = "androidx.room.RoomDatabase"

        private val COROUTINE_CONFINERS = setOf("withContext", "launch", "async")
        private val BACKGROUND_DISPATCHERS = setOf("IO", "Default")

        private val ROOM_METHOD_ANNOTATIONS = setOf(
            "androidx.room.Query",
            "androidx.room.RawQuery",
            "androidx.room.Insert",
            "androidx.room.Update",
            "androidx.room.Upsert",
            "androidx.room.Delete",
            "androidx.room.Transaction"
        )

        /** The `RoomDatabase` members that reach SQLite themselves rather than handing out a DAO. */
        private val ROOM_DATABASE_MEMBERS = setOf(
            "runInTransaction",
            "query",
            "compileStatement",
            "clearAllTables",
            "beginTransaction",
            "endTransaction",
            "setTransactionSuccessful"
        )

        /** Holders whose query runs where they are collected, not where they are obtained. */
        private val MAIN_SAFE_RETURN_TYPES = setOf(
            "kotlinx.coroutines.flow.Flow",
            "androidx.lifecycle.LiveData",
            "androidx.paging.PagingSource",
            "androidx.paging.DataSource",
            "io.reactivex"
        )

        val ISSUE = Issue.create(
            id = "MainThreadRoom",
            briefDescription = "Blocking Room access on main thread",
            explanation = "Calling a blocking Room DAO or database method directly on the main thread in a " +
                "ViewModel or UI class makes Room throw at runtime and freezes the frame that reaches it. " +
                "Confine it with withContext(Dispatchers.IO), a coroutine builder with an IO or Default " +
                "dispatcher, a @WorkerThread function, or declare the DAO method suspend or Flow-returning " +
                "so Room schedules it off the main thread itself.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(MainThreadRoomDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
