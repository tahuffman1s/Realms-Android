@rem Minimal Gradle wrapper launcher for Windows.
@echo off
set DIR=%~dp0
set JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
if not exist "%JAR%" (
    echo gradle-wrapper.jar missing.
    echo Run "gradle wrapper" in this directory, or open in Android Studio.
    exit /b 1
)
java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
