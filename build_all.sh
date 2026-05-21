#!/bin/bash
set -e
cd "$(dirname "$0")"
echo "=== Building STSStateSaver ===" && (cd STSStateSaver && mvn package -q)
echo "=== Building LudicrousSpeed ===" && (cd LudicrousSpeed && mvn package -q)
echo "=== Building StSCommunicationMod ===" && (cd StSCommunicationMod && mvn package -q)
echo "=== Building scumthespire ===" && (cd scumthespire && mvn package -q)
echo "=== All 4 mods built ==="
