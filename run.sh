#!/bin/sh
# Compiles and runs straight from the source tree, reading assets/ directly (handy while swapping art).
# Pass --dev to enable the private development keys.
set -e
cd "$(dirname "$0")"
mkdir -p build/classes
javac --release 11 -encoding UTF-8 -d build/classes src/diddys/*.java
DEV=false
[ "$1" = "--dev" ] && DEV=true && shift
exec java -Ddiddys.dev=$DEV -cp build/classes:assets diddys.Main "$@"
