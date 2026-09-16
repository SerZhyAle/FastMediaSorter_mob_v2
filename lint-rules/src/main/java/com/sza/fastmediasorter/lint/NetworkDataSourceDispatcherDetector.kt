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
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULambdaExpression
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
 *
 * S3156: three narrowings, each measured against the 449 findings the first shape produced.
 * The trigger set excludes library types and members that own no socket; a `@WorkerThread`
 * scoping function confines everything inside its lambda; a reference whose declared type is
 * `CoroutineDispatcher` counts as a background dispatcher. Scope stays `JAVA_FILE_SCOPE`, so a
 * call confined only by a caller in another file is still reported and carried in the baselines.
 */
class NetworkDataSourceDispatcherDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val psiMethod = node.resolve() ?: return
            val owner = psiMethod.containingClass?.qualifiedName ?: return
            if (!isBlockingNetworkCall(owner, psiMethod.name)) return

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

    /**
     * S3156: the three package prefixes alone matched 268 of 449 findings against members that
     * can never reach a socket, so the owner type and the member name both have to pass.
     */
    private fun isBlockingNetworkCall(owner: String, member: String): Boolean {
        if (!isNetworkLibraryOwner(owner)) return false
        if (LOCAL_OWNER_PREFIXES.any { owner == it || owner.startsWith("$it.") }) return false
        return !isLocalStateMember(member)
    }

    private fun isNetworkLibraryOwner(owner: String): Boolean {
        return owner.startsWith("com.hierynomus.") ||
            owner.startsWith("org.apache.commons.net.") ||
            owner.startsWith("com.jcraft.jsch.")
    }

    /** Reads a field the previous exchange already filled, or configures a client before it connects. */
    private fun isLocalStateMember(member: String): Boolean {
        return member in LOCAL_STATE_MEMBERS ||
            member.startsWith("set") ||
            member.startsWith("enterLocal")
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
        if (isWorkerThreadScopingCall(call)) return true
        if (call.methodName !in COROUTINE_CONFINERS) return false
        return call.valueArguments.any { isBackgroundDispatcher(it) }
    }

    /**
     * S3156: this project confines a whole client family in one scoping function
     * (`SmbConnectionManager.withConnection`, `SftpConnectionPool.withConnection`) rather than at
     * each call site. A `@WorkerThread` on such a function declares that its lambda runs on a
     * background dispatcher, so every call inside that lambda is confined.
     */
    private fun isWorkerThreadScopingCall(call: UCallExpression): Boolean {
        if (call.valueArguments.none { it is ULambdaExpression }) return false
        val resolved = call.resolve() ?: return false
        return resolved.annotations.any { it.qualifiedName == WORKER_THREAD_FQN }
    }

    private fun isBackgroundDispatcher(expression: UExpression): Boolean {
        val unwrapped = expression.skipParenthesizedExprDown()
        val target = if (unwrapped is UQualifiedReferenceExpression) unwrapped.selector else unwrapped
        val resolved = (target as? UResolvable)?.resolve() ?: return false
        if (isDispatchersMember(resolved)) return true
        // Dispatchers.Main and Dispatchers.Unconfined are CoroutineDispatcher too - stop before the
        // declared-type check below would accept them.
        if (isForegroundDispatchersMember(resolved)) return false

        if (resolved is PsiField) {
            val uField = UastFacade.findPlugin(resolved)?.convertElementWithParent(resolved, null) as? UField
            val initializer = uField?.uastInitializer
            if (initializer != null && isBackgroundDispatcher(initializer)) {
                return true
            }
        }
        return isInjectedDispatcher(resolved)
    }

    /**
     * S3156: `SmbDirectoryScanner` and its siblings take the dispatcher as a constructor property
     * (`withContext(smbDispatcher)`), which resolves neither to a `Dispatchers` member nor to a
     * field with an initializer, so the switch was invisible.
     */
    private fun isInjectedDispatcher(resolved: com.intellij.psi.PsiElement): Boolean {
        val type = when (resolved) {
            is PsiField -> resolved.type
            is PsiMethod -> resolved.returnType
            is PsiVariable -> resolved.type
            else -> null
        } ?: return false
        return InheritanceUtil.isInheritor(type, COROUTINE_DISPATCHER_FQN)
    }

    private fun isForegroundDispatchersMember(resolved: com.intellij.psi.PsiElement): Boolean {
        val owner = when (resolved) {
            is PsiField -> resolved.containingClass?.qualifiedName
            is PsiMethod -> resolved.containingClass?.qualifiedName
            else -> null
        }
        return owner == DISPATCHERS_FQN
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
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.resolve() == target) callSites += node
                return super.visitCallExpression(node)
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
        private const val COROUTINE_DISPATCHER_FQN = "kotlinx.coroutines.CoroutineDispatcher"
        private const val WORKER_THREAD_FQN = "androidx.annotation.WorkerThread"

        private val COROUTINE_CONFINERS = setOf("withContext", "launch", "async")
        private val BACKGROUND_DISPATCHERS = setOf("IO", "Default")

        /** Value objects, builders and credential holders - none of them owns a connection. */
        private val LOCAL_OWNER_PREFIXES = setOf(
            "com.hierynomus.msfscc.fileinformation",
            "com.hierynomus.msdtyp",
            "com.hierynomus.smbj.SmbConfig",
            "com.hierynomus.smbj.auth.AuthenticationContext",
            "com.jcraft.jsch.SftpATTRS",
            "com.jcraft.jsch.ChannelSftp.LsEntry",
            "org.apache.commons.net.ftp.FTPFile",
            "org.apache.commons.net.ftp.FTPReply"
        )

        /** Members that read a field the last exchange filled, or hand out a handle without using it. */
        private val LOCAL_STATE_MEMBERS = setOf(
            "isConnected",
            "getReplyCode",
            "getReplyString",
            "getConnection",
            "getSession",
            "addIdentity"
        )

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
