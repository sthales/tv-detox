#!/bin/sh
# Runs the plain-Java checks for Config.java. Needs only a JDK.
#   sh ci/unit-test.sh
set -e
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -nowarn -encoding UTF-8 -d "$out" src/com/ansub/tvhome/Config.java ci/ConfigTest.java
java -ea -cp "$out" com.ansub.tvhome.ConfigTest
