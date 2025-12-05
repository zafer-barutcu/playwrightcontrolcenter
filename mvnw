#!/bin/sh
set -e
BASEDIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$BASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Wrapper jar yok: $WRAPPER_JAR" >&2
  exit 1
fi

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME tanimli degil" >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" -cp "$WRAPPER_JAR" $WRAPPER_LAUNCHER "$@"