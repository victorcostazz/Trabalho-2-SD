#!/usr/bin/env bash
# Uso: ./run-servidor.sh [host=localhost] [porta=1099]
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
exec java -cp out sd.servidor.ServidorMain "$@"
