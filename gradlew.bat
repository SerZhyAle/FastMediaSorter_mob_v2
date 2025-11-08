@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
@rem ### START MODIFICATION - SZA ###
@rem Run PowerShell pre-build script to update version.
powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand JABiAHUAaQBsAGQARwByAGEAZABsAGUAUABhAHQAaAAgAD0AIAAnAGEAcABwAF8AdgAyAFwAYgB1AGkAbABkAC4AZwByAGEAZABsAGUALgBrAHQAcwAnADsADQAKACQAYgBhAGMAawB1AHAAUABhAHQAaAAgAD0AIAAkAGIAdQBpAGwAZABHAHIAYQBkAGwAZQBQAGEAdABoACAAKwAgACcALgBiAGEAYwBrAHUAcAAnADsADQAKAGkAZgAgACgAVABlAHMAdAAtAFAAYQB0AGgAIAAtAEwAaQB0AGUAcgBhAGwAUABhAHQAaAAgACQAYgB1AGkAbABkAEcAcgBhAGQAbABlAFAAYQB0AGgAKQAgAHsADQAKACAAIABpAGYAIAAoAC0AbgBvAHQAIAAoAFQAZQBzAHQALQBQAGEAdABoACAALQBMAGkAdABlAHIAYQBsAFAAYQB0AGgAIAAkAGIAYQBjAGsAdQBwAFAAYQB0AGgAKQApACAAewANAAoAIAAgACAAIAAgAEMAbwBwAHkALQBJAHQAZQBtACAALQBMAGkAdABlAHIAYQBsAFAAYQB0AGgAIAAkAGIAdQBpAGwAZABHAHIAYQBkAGwAZQBQAGEAdABoACAALQBEAGUAcwB0AGkAbgBhAHQAaQBvAG4AIAAkAGIAYQBjAGsAdQBwAFAAYQB0AGgAIAAtAEYAbwByAGMAZQA7AA0ACgAgACAAfQANAAoAIAAgACQAbgBvAHcAIAA9ACAARwBlAHQALQBEAGEAdABlADsADQAKACAAIAAkAHYAZQByAHMAaQBvAG4AQwBvAGQAZQBJAG4AdAAgAD0AIABbAGkAbgB0AF0AJABuAG8AdwAuAFQAbwBTAHQAcgBpAG4AZwAoACcATQBNAGQAZABIAEgAbQBtACcAKQA7AA0ACgAgACAAJAB2AGUAcgBzAGkAbwBuAE4AYQBtAGUAIAA9ACAAJwAyAC4AMAAuADAALQBiAHUAaQBsAGQAJwAgACsAIAAkAG4AbwB3AC4AVABvAFMAdAByAGkAbgBnACgAJwB5AHkATQBNAGQAZABIAEgAbQBtACcAKQA7AA0ACgAgACAAJABjAG8AbgB0AGUAbgB0ACAAPQAgAEcAZQB0AC0AQwBvAG4AdABlAG4AdAAgAC0ATABpAHQAZQByAGEAbABQAGEAdABoACAAJABiAHUAaQBsAGQARwByAGEAZABsAGUAUABhAHQAaAAgAC0AUgBhAHcAOwANAAoAIAAgACQAYwBvAG4AdABlAG4AdAAgAD0AIAAkAGMAbwBuAHQAZQBuAHQAIAAtAHIAZQBwAGwAYQBjAGUAIAAnACgAdgBlAHIAcwBpAG8AbgBDAG8AZABlAFwAcwAqAD0AXABzACoAKQBcAGQAKwAnACwAIAAoACcAJAB7ADEAfQAnACAAKwAgACQAdgBlAHIAcwBpAG8AbgBDAG8AZABlAEkAbgB0ACkAOwANAAoAIAAgACQAYwBvAG4AdABlAG4AdAAgAD0AIAAkAGMAbwBuAHQAZQBuAHQAIAAtAHIAZQBwAGwAYQBjAGUAIAAnACgAdgBlAHIAcwBpAG8AbgBOAGEAbQBlAFwAcwAqAD0AXABzACoAKQAiAFsAXgAiAF0AKgAiACcALAAgACgAJwAkAHsAMQB9ACIAJwAgACsAIAAkAHYAZQByAHMAaQBvAG4ATgBhAG0AZQAgACsAIAAnACIAJwApADsADQAKACAAIAAgAFMAZQB0AC0AQwBvAG4AdABlAG4AdAAgAC0ATABpAHQAZQByAGEAbABQAGEAdABoACAAJABiAHUAaQBsAGQARwByAGEAZABsAGUAUABhAHQAaAAgAC0AVgBhAGwAdQBlACAAJABjAG8AbgB0AGUAbgB0ACAALQBOAG8ATgBlAHcAbABpAG4AZQA7AA0ACgAgACAAZQBjAGgAbwAgACgAJwBbAFYBQwBTAF0AIABVAHAAZABhAHQAZQBkACAAZgBpAGwAZAAuAGcAcgBhAGQAbABlAC4AawB0AHMAIAB3AGkAdABoACAAdgBlAHIAcwBpAG8AbgA6ACAAJwAgACsAIAAkAHYAZQByAHMAaQBvAG4ATgBhAG0AZQApADsADQAKAH0A
@rem ### END MODIFICATION - SZA ###

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

@rem ### START MODIFICATION - SZA ###
@rem Check build result and handle backup.
if %ERRORLEVEL% equ 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand ZQBjAGgAbwAgACcAWwBWAEMAUwBdAIABQgBpAGwAZAAgAHMAdQBjAGMAZQBzAHMAZgB1AGwALgAgAEsAZQBwAHQAIAB1AHAAZABhAHQAZQBkACAAZgBpAGwAZQBzAC4AJwA=
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand JABiAHUAaQBsAGQARwByAGEAZABsAGUAUABhAHQAaAAgAD0AIAAnAGEAcABwAF8AdgAyAFwAYgB1AGkAbABkAC4AZwByAGEAZABsAGUALgBrAHQAcwAnADsAIAAkAGIAYQBjAGsAdQBwAFAAYQB0AGgAIAA9ACAAKAAkAGIAdQBpAGwAZABHAHIAYQBkAGwAZQBQAGEAdABoACAAKwAgACcALgBiAGEAYwBrAHUAcAAnACkAOwAgAGkAZgAgACgAVABlAHMAdAAtAFAAYQB0AGgAIAAtAEwAaQB0AGUAcgBhAGwAUABhAHQAaAAgACQAYgBhAGMAawB1AHAAUABhAHQAaAApACAAewAgAE0AbwB2AGUALQBJAHQAZQBtACAALQBMAGkAdABlAHIAYQBsAFAAYQB0AGgAIAAkAGIAYQBjAGsAdQBwAFAAYQB0AGgAIAAtAEQAZQBzAHQAaQBuAGEAdABpAG8AbgAgACQAYgB1AGkAbABkAEcAcgBhAGQAbABlAFAAYQB0AGgAIAAtAEYAbwByAGMAZQA7ACAAZQBjAGgAbwAgACcAWwBWAEMAUwBdAIABQgBpAGwAZAAgAGYAYQBpAGwAZQBkAC4gAFIAZQBzAHQAbwByAGUAZAAgAGIAYQB0ACAAZgByAG8AbQAgAGIAYQBjAGsAdQBwAC4AJwA7ACAAfQ==
)
@rem ### END MODIFICATION - SZA ###

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
