#!/bin/sh
# Double-click to play. Opens a Terminal window that starts the game; closing the game closes the run.
cd "$(dirname "$0")"
[ -f dist/FiveNightsAtDiddys.jar ] || ./build.sh
java -jar dist/FiveNightsAtDiddys.jar
