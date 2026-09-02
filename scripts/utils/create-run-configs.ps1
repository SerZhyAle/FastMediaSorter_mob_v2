# Script to create Android Studio run configurations for all flavors
$runConfigsDir = ".idea\runConfigurations"

# Ensure directory exists
if (-not (Test-Path $runConfigsDir)) {
    New-Item -ItemType Directory -Path $runConfigsDir -Force | Out-Null
}

# Function to create run configuration XML
function Create-RunConfig {
    param(
        [string]$FlavorName,
        [string]$BuildType,
        [string]$GradleTask
    )
    
    $configName = "app__${FlavorName}${BuildType}_.xml"
    $displayName = "app [$FlavorName$BuildType]"
    
    $xmlContent = @"
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="$displayName" type="AndroidRunConfigurationType" factoryName="Android App">
    <module name="FastMediaSorter_mob_v2.app_v2.main" />
    <option name="DEPLOY" value="true" />
    <option name="DEPLOY_APK_FROM_BUNDLE" value="false" />
    <option name="DEPLOY_AS_INSTANT" value="false" />
    <option name="ARTIFACT_NAME" value="" />
    <option name="PM_INSTALL_OPTIONS" value="" />
    <option name="ALL_USERS" value="false" />
    <option name="ALWAYS_INSTALL_WITH_PM" value="false" />
    <option name="CLEAR_APP_STORAGE" value="false" />
    <option name="ACTIVITY_EXTRA_FLAGS" value="" />
    <option name="MODE" value="default_activity" />
    <option name="CLEAR_LOGCAT" value="false" />
    <option name="SHOW_LOGCAT_AUTOMATICALLY" value="true" />
    <option name="INSPECTION_WITHOUT_ACTIVITY_RESTART" value="false" />
    <option name="TARGET_SELECTION_MODE" value="SHOW_DIALOG" />
    <option name="DEBUGGER_TYPE" value="Auto" />
    <Auto>
      <option name="USE_JAVA_AWARE_DEBUGGER" value="false" />
      <option name="SHOW_STATIC_VARS" value="true" />
      <option name="WORKING_DIR" value="" />
      <option name="TARGET_LOGGING_CHANNELS" value="lldb process:gdb-remote packets" />
      <option name="SHOW_OPTIMIZED_WARNING" value="true" />
    </Auto>
    <Hybrid>
      <option name="USE_JAVA_AWARE_DEBUGGER" value="false" />
      <option name="SHOW_STATIC_VARS" value="true" />
      <option name="WORKING_DIR" value="" />
      <option name="TARGET_LOGGING_CHANNELS" value="lldb process:gdb-remote packets" />
      <option name="SHOW_OPTIMIZED_WARNING" value="true" />
    </Hybrid>
    <Java />
    <Native>
      <option name="USE_JAVA_AWARE_DEBUGGER" value="false" />
      <option name="SHOW_STATIC_VARS" value="true" />
      <option name="WORKING_DIR" value="" />
      <option name="TARGET_LOGGING_CHANNELS" value="lldb process:gdb-remote packets" />
      <option name="SHOW_OPTIMIZED_WARNING" value="true" />
    </Native>
    <Profilers>
      <option name="ADVANCED_PROFILING_ENABLED" value="false" />
      <option name="STARTUP_PROFILING_ENABLED" value="false" />
      <option name="STARTUP_CPU_PROFILING_ENABLED" value="false" />
      <option name="STARTUP_CPU_PROFILING_CONFIGURATION_NAME" value="Java/Kotlin Method Sample (legacy)" />
      <option name="STARTUP_NATIVE_MEMORY_PROFILING_ENABLED" value="false" />
      <option name="NATIVE_MEMORY_SAMPLE_RATE_BYTES" value="2048" />
    </Profilers>
    <option name="DEEP_LINK" value="" />
    <option name="ACTIVITY_CLASS" value="" />
    <option name="SEARCH_ACTIVITY_IN_GLOBAL_SCOPE" value="false" />
    <option name="SKIP_ACTIVITY_VALIDATION" value="false" />
    <method v="2">
      <option name="Android.Gradle.BeforeRunTask" enabled="true" />
      <option name="Gradle.BeforeRunTask" enabled="true" tasks="$GradleTask" externalProjectPath="`$PROJECT_DIR$" vmOptions="" scriptParameters="" />
    </method>
  </configuration>
</component>
"@
    
    $xmlContent | Out-File -FilePath "$runConfigsDir\$configName" -Encoding utf8 -NoNewline
    Write-Host "Created: $configName"
}

# Create all configurations
Write-Host "Creating Android Studio run configurations..." -ForegroundColor Green

# Standard flavor
Create-RunConfig -FlavorName "standard" -BuildType "Debug" -GradleTask ":app_v2:assembleStandardDebug"
Create-RunConfig -FlavorName "standard" -BuildType "Release" -GradleTask ":app_v2:assembleStandardRelease"

# Lite flavor
Create-RunConfig -FlavorName "lite" -BuildType "Debug" -GradleTask ":app_v2:assembleLiteDebug"
Create-RunConfig -FlavorName "lite" -BuildType "Release" -GradleTask ":app_v2:assembleLiteRelease"

# Photos flavor
Create-RunConfig -FlavorName "photos" -BuildType "Debug" -GradleTask ":app_v2:assemblePhotosDebug"
Create-RunConfig -FlavorName "photos" -BuildType "Release" -GradleTask ":app_v2:assemblePhotosRelease"

# Legacy flavor
Create-RunConfig -FlavorName "legacy" -BuildType "Debug" -GradleTask ":app_v2:assembleLegacyDebug"
Create-RunConfig -FlavorName "legacy" -BuildType "Release" -GradleTask ":app_v2:assembleLegacyRelease"

Write-Host "`n✅ Done! Created 8 run configurations." -ForegroundColor Green
Write-Host "Restart Android Studio to see them in the run configuration dropdown." -ForegroundColor Yellow
