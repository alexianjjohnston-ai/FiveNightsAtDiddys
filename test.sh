#!/bin/sh
# Compiles the game and the rule checks, then runs them headlessly.
set -e
cd "$(dirname "$0")"
mkdir -p build/test
javac --release 11 -encoding UTF-8 -d build/test src/diddys/*.java test/diddys/*.java
java -Djava.awt.headless=true -cp build/test:assets diddys.SimTest
