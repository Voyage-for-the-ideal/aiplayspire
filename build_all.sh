#!/bin/bash
set -e
cd "$(dirname "$0")"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$(cygpath -w "$PWD/battle_engine/build.ps1")" ;;
  *) test -s battle_engine/bin/battle_jni.dll || { echo "For the Windows mod bundle, supply the Windows x64 DLL or build on Windows. For the Linux worker see docs/BATTLE_ENGINE.md." >&2; exit 1; } ;;
esac
echo "=== Building STSStateSaver ===" && (cd STSStateSaver && mvn package -q)
echo "=== Building LudicrousSpeed ===" && (cd LudicrousSpeed && mvn package -q)
echo "=== Building StSCommunicationMod ===" && (cd StSCommunicationMod && mvn package -q)
echo "=== Building scumthespire ===" && (cd scumthespire && mvn package -q)
echo "=== All 4 mods built ==="
