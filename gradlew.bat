@echo off
setlocal
set DIR=%~dp0
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  java -Xmx64m -cp "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  echo The Gradle wrapper jar is missing. Please run 'gradle wrapper' to generate it.
  exit /b 1
)
