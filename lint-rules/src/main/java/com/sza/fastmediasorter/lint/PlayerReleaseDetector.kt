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
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Reports a class that constructs a media player and never releases it.
 *
 * S1195: the rule used to match the substring "Player" against every field type, including the
 * synthetic INSTANCE / Companion fields that carry the enclosing class's own FQN - so a class
 * merely named `*Player*` flagged itself with no player anywhere. It now keys on OWNERSHIP:
 * a class is accountable for a player only when its own body constructs one.
 */
class PlayerReleaseDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            // A Kotlin file facade - the synthetic `<File>Kt` class carrying a file's top-level
            // functions - holds no state, so a player one of them builds is stored on an extension
            // receiver, which is a different class entirely. This is the file-scope twin of the
            // nested-class defect handled below.
            if (node.isFileFacade()) return

            val constructions = mutableListOf<Pair<UCallExpression, String?>>()
            val releasedNames = mutableSetOf<String>()
            var releasesPlayerTypedReceiver = false

            node.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(call: UCallExpression): Boolean {
                    // A nested class is visited on its own; attributing its calls to the outer
                    // class is what made a nested DTO answer for its owner's player.
                    if (enclosingClass(call) === node) {
                        if (isPlayerConstruction(call) && !isHandedToCaller(call)) {
                            constructions += call to assignedName(call)
                        }
                        if (isReleaseCall(call)) {
                            releaseTargetOf(call)?.let { releasedNames += it }
                            if (releasesOwnedPlayer(call)) releasesPlayerTypedReceiver = true
                        }
                    }
                    return super.visitCallExpression(call)
                }
            })

            if (releasesPlayerTypedReceiver) return

            for ((call, name) in constructions) {
                // An unassigned instance is not retained by this class, so it has nothing to free.
                if (name == null || name in releasedNames) continue
                context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "This class constructs a media player (`$name`) but never calls release() on it. " +
                        "Whoever constructs a player owns it and must release it on teardown."
                )
            }
        }
    }

    /**
     * True for the synthetic class Kotlin generates for a file's top-level declarations. Recognised by
     * every method being static, which an `object` (instance methods on INSTANCE) and an ordinary class
     * both fail.
     *
     * Compile-time constants do not count as state: a `private const val` is inlined at every use site
     * and cannot hold a player, so a file of extension functions plus its tuning constants is still a
     * facade. A top-level `val` holding a real object is not constant, so a file that parks a player in
     * one keeps its ownership.
     */
    private fun UClass.isFileFacade(): Boolean =
        methods.isNotEmpty() &&
            methods.all { it.javaPsi.hasModifierProperty(PsiModifier.STATIC) } &&
            fields.all { it.computeConstantValue() != null }

    private fun enclosingClass(element: UElement): UClass? {
        var current: UElement? = element.uastParent
        while (current != null) {
            if (current is UClass) return current
            current = current.uastParent
        }
        return null
    }

    /**
     * True for `MediaPlayer()`, `ExoPlayer.Builder(..).build()` and
     * `MediaController.Builder(..).buildAsync()` - the three ways this codebase creates a player.
     * Receiving one as a parameter, wrapping it in `Lazy`, or holding it in a DTO is borrowing.
     */
    private fun isPlayerConstruction(call: UCallExpression): Boolean {
        if (call.kind == UastCallKind.CONSTRUCTOR_CALL) {
            return isOwnedPlayerType(call.returnType)
        }
        if (call.methodName !in BUILDER_METHODS) return false
        val receiverType = call.receiverType?.canonicalText ?: return false
        return OWNED_PLAYER_TYPES.any { receiverType == "$it.Builder" }
    }

    /** Matches the owned types or any subclass of them - never a raw string comparison. */
    private fun isOwnedPlayerType(type: PsiType?): Boolean =
        (type as? PsiClassType)?.resolve()?.matchesOwnedType() == true

    private fun PsiClass.matchesOwnedType(): Boolean {
        if (qualifiedName in OWNED_PLAYER_TYPES) return true
        return supers.any { it.matchesOwnedType() }
    }

    /** The property or local the constructed player is stored in, or null when it escapes. */
    private fun assignedName(call: UCallExpression): String? {
        var current: UElement? = call
        var depth = 0
        while (current != null && depth < MAX_ASSIGNMENT_WALK) {
            when (val parent = current.uastParent) {
                is UVariable -> return parent.name
                is UReturnExpression -> return null
                is UBinaryExpression -> return simpleName(parent.leftOperand)
                // Lint replays every test with redundant parentheses inserted; unwrapping them
                // here keeps the verdict identical in both modes.
                is UQualifiedReferenceExpression, is UParenthesizedExpression -> current = parent
                else -> return null
            }
            depth++
        }
        return null
    }

    private fun isReleaseCall(call: UCallExpression): Boolean = call.methodName in RELEASE_METHODS

    /** The receiver or argument a release() call actually frees, so a wakeLock cannot pass for a player. */
    private fun releaseTargetOf(call: UCallExpression): String? {
        call.receiver?.let { return simpleName(it) }
        return call.valueArguments.firstNotNullOfOrNull { simpleName(it) }
    }

    /**
     * Name matching alone breaks when the class releases through a different local than the one it
     * constructed into - `musicPlayer` assigned at build time, freed via a `player` alias. Matching
     * the receiver's TYPE covers that without reopening the wakeLock hole, because a WakeLock is
     * not an owned player type.
     */
    private fun releasesOwnedPlayer(call: UCallExpression): Boolean {
        val receiverType = call.receiver?.getExpressionType()
        if (receiverType != null && isOwnedPlayerType(receiverType)) return true
        return call.valueArguments.any { isOwnedPlayerOrItsFuture(it.getExpressionType()) }
    }

    /**
     * `MediaController.releaseFuture(controllerFuture)` is the sanctioned way to free a controller
     * that was built asynchronously, and it takes the future rather than the player - so the owned
     * type sits in the type argument, not in the argument's own type.
     */
    private fun isOwnedPlayerOrItsFuture(type: PsiType?): Boolean {
        if (isOwnedPlayerType(type)) return true
        return (type as? PsiClassType)?.parameters?.any { isOwnedPlayerType(it) } == true
    }

    /**
     * A factory hands its product to the caller, who becomes the owner. Recognised by the enclosing
     * function declaring an owned player type as its return type - `createPlayer(): ExoPlayer`.
     */
    private fun isHandedToCaller(call: UCallExpression): Boolean {
        var current: UElement? = call.uastParent
        while (current != null) {
            if (current is UMethod) return isOwnedPlayerType(current.returnType)
            current = current.uastParent
        }
        return false
    }

    private fun simpleName(element: UElement?): String? = when (element) {
        is USimpleNameReferenceExpression -> element.identifier
        is UQualifiedReferenceExpression -> simpleName(element.selector)
        is UParenthesizedExpression -> simpleName(element.expression)
        else -> null
    }

    companion object {
        private const val MAX_ASSIGNMENT_WALK = 4

        private val OWNED_PLAYER_TYPES = setOf(
            "androidx.media3.common.Player",
            "androidx.media3.exoplayer.ExoPlayer",
            "android.media.MediaPlayer",
            "androidx.media3.session.MediaController"
        )

        private val BUILDER_METHODS = setOf("build", "buildAsync")

        private val RELEASE_METHODS = setOf("release", "releaseFuture")

        val ISSUE = Issue.create(
            id = "PlayerNotReleased",
            briefDescription = "Media Player is not released",
            explanation = "One owner per player: the class that constructs an ExoPlayer, MediaPlayer or " +
                "MediaController holds hardware and system resources and must call release() on that " +
                "instance when the hosting component is torn down. A class that receives, wraps or " +
                "merely names a player does not own it and has nothing to release.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(PlayerReleaseDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
