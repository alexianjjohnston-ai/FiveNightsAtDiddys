#!/bin/sh
# Builds dist/FiveNightsAtDiddys.jar (code + assets). Needs a JDK 11 or newer.
set -e
cd "$(dirname "$0")"
rm -rf build dist
mkdir -p build/classes dist
javac --release 11 -encoding UTF-8 -d build/classes src/diddys/*.java
printf 'Main-Class: diddys.Main\n' > build/manifest.txt
jar cfm dist/FiveNightsAtDiddys.jar build/manifest.txt -C build/classes . -C assets .
echo "Built dist/FiveNightsAtDiddys.jar"
