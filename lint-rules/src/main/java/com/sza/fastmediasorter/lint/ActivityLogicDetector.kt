package com.sza.fastmediasorter.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class ActivityLogicDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val isActivity = node.name?.endsWith("Activity") == true || 
                node.uastSuperTypes.any { it.type.canonicalText.endsWith("Activity") || it.asSourceString().contains("Activity") }
            
            if (!isActivity) return

            for (field in node.fields) {
                val typeName = field.type.canonicalText
                val sourcePsi = field.sourcePsi
                val kotlinOrigin = (sourcePsi as? org.jetbrains.kotlin.asJava.elements.KtLightElement<*, *>)?.kotlinOrigin ?: sourcePsi
                val hasInject = field.annotations.any { it.qualifiedName?.endsWith("Inject") == true } ||
                    (kotlinOrigin as? org.jetbrains.kotlin.psi.KtModifierListOwner)?.annotationEntries?.any {
                        it.shortName?.asString() == "Inject"
                    } == true
                
                val isForbiddenDependency = typeName.contains("Repository") ||
                    typeName.contains("UseCase") ||
                    typeName.contains("DataSource") ||
                    typeName.contains("Dao") ||
                    typeName.contains("Database")

                if (hasInject && isForbiddenDependency) {
                    context.report(
                        ISSUE,
                        field,
                        context.getLocation(field as UElement),
                        "Activities must not contain business logic or direct references to repositories/usecases/data sources/databases. Delegate to a ViewModel or Manager."
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ActivityLogicViolation",
            briefDescription = "Direct dependency injection of business layer in Activity",
            explanation = "Activity classes must act as thin UI hosts and delegate all business, network, or database logic to ViewModels, use cases, or managers.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(ActivityLogicDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
