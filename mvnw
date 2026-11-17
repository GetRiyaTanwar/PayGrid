#!/bin/sh
# PayGrid Maven Wrapper
set -e

# Detect Java Home (prefer Java 21/23 if present)
if [ -d "/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

MAVEN_BIN="/Users/riya/.m2/wrapper/dists/apache-maven-3.8.4-bin/52ccbt68d252mdldqsfsn03jlf/apache-maven-3.8.4/bin/mvn"

if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
elif [ -x "$MAVEN_BIN" ]; then
    exec "$MAVEN_BIN" "$@"
else
    echo "Error: Maven not found." >&2
    exit 1
fi
