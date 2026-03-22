#!/usr/bin/env sh
##############################################################################
## Gradle start up script for UN*X
##############################################################################
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"
MAX_FD="maximum"
warn () { echo "$*"; }
die () { echo; echo "ERROR: $*"; echo; exit 1; }
if [ "$APP_HOME" ] ; then
    case "$APP_HOME" in
        */) APP_HOME=`expr "$APP_HOME" : '\(.*[^/]\)' \| "."` ;;
    esac
fi
JAVA_EXE="java"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVA_EXE"  -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
