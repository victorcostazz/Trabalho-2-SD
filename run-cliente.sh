#!/usr/bin/env bash
# Uso: ./run-cliente.sh [hostServidor=localhost] [portaServidor=1099] [hostLocal=localhost] [portaLocal=1100]
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
exec java -cp out sd.cliente.ClienteMain "$@"
