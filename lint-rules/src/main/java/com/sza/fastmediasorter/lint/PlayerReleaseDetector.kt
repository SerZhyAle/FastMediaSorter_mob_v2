package com.sza.fastmediasorter.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PlayerReleaseDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val playerFields = node.fields.filter { field ->
                val typeName = field.type.canonicalText
                typeName.contains("Player") || typeName.contains("ExoPlayer") || typeName.contains("MediaController")
            }

            if (playerFields.isEmpty()) return

            var hasReleaseCall = false
            node.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    if (node.methodName == "release") {
                        hasReleaseCall = true
                    }
                    return super.visitCallExpression(node)
                }
            })

            if (!hasReleaseCall) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node as UElement),
                    "Class declares Player/ExoPlayer/MediaController property but does not call release() to clean up resources."
                )
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "PlayerNotReleased",
            briefDescription = "Media Player is not released",
            explanation = "ExoPlayer and MediaController hold hardware and system resources that must be explicitly released by calling release() when the hosting component is destroyed.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(PlayerReleaseDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
