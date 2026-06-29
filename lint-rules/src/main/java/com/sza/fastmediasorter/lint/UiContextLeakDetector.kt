package com.sza.fastmediasorter.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class UiContextLeakDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val isSingleton = node.annotations.any { it.qualifiedName?.endsWith("Singleton") == true } ||
                node.name?.endsWith("Manager") == true ||
                node.supers.any { it.qualifiedName == "kotlin.jvm.internal.Lambda" }
            
            val isViewModel = node.name?.endsWith("ViewModel") == true ||
                node.uastSuperTypes.any { it.type.canonicalText.endsWith("ViewModel") || it.asSourceString().contains("ViewModel") }

            if (!isSingleton && !isViewModel) return

            for (field in node.fields) {
                val typeName = field.type.canonicalText
                val fieldName = field.name

                val isContextType = typeName.contains("android.content.Context") || typeName.endsWith(".Context") || typeName == "Context" ||
                    typeName.contains("android.app.Activity") || typeName.endsWith(".Activity") || typeName == "Activity" ||
                    typeName.contains("androidx.fragment.app.Fragment") || typeName.endsWith(".Fragment") || typeName == "Fragment" ||
                    typeName.contains("android.view.View") || typeName.endsWith(".View") || typeName == "View"

                if (isContextType) {
                    val isAppCtx = fieldName.equals("appContext", ignoreCase = true) || 
                        fieldName.equals("applicationContext", ignoreCase = true)
                    
                    val isWeakRef = typeName.contains("java.lang.ref.WeakReference")

                    if (!isAppCtx && !isWeakRef) {
                        context.report(
                            ISSUE,
                            field,
                            context.getLocation(field as UElement),
                            "Do not store UI Context (Activity, Fragment, View) in long-lived ViewModels or Singletons. This leads to severe memory leaks."
                        )
                    }
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UiContextLeak",
            briefDescription = "UI Context stored in long-lived component",
            explanation = "Storing UI Contexts (Activity, Fragment, View) inside ViewModels, Singletons, or long-lived managers causes memory leaks when the UI is destroyed. Use ApplicationContext or wrap in WeakReference.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(UiContextLeakDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
