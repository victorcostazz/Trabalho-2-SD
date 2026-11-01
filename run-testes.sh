#!/usr/bin/env bash
# Compila e executa os testes de integração (servidor + clientes RMI na mesma JVM).
cd "$(dirname "$0")"
./build.sh && exec java -cp out sd.teste.TesteIntegracao
