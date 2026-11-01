#!/usr/bin/env bash
# Compila todo o projeto para ./out (requer JDK 17+, sem dependências externas).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir out
javac -encoding UTF-8 -d out $(find src -name '*.java')
echo "Compilado em ./out"
