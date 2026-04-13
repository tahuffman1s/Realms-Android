#!/bin/sh
# Minimal Gradle wrapper launcher. If gradle/wrapper/gradle-wrapper.jar is
# missing, bootstrap it by running `gradle wrapper` once (requires Gradle 8.x
# on PATH), or open the project in Android Studio which generates it.
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
    echo "gradle-wrapper.jar missing."
    echo "Run 'gradle wrapper' in this directory to generate it, or open the project in Android Studio."
    exit 1
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
