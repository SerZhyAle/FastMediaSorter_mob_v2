# ProGuard/R8 rules specific to the noLegal flavor.
# Wired only via the noLegal productFlavor proguardFiles — never applied to
# standard/lite/photos/legacy/vr, which do not depend on NewPipeExtractor.

# NewPipeExtractor (com.github.TeamNewPipe:NewPipeExtractor) pulls Rhino (Mozilla
# Rhino JS engine) and jsoup. Both reference optional JVM classes that are absent
# on Android:
#   - Rhino's JavaToJSONConverters reflects over java.beans.* (no Beans API on Android)
#   - jsoup's Re2jRegex optionally delegates to com.google.re2j.* (not bundled)
#   - Rhino registers a javax.script.ScriptEngineFactory service we do not use
# These code paths are never exercised at runtime, so suppressing the missing-class
# warnings is safe.
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
