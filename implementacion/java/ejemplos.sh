#!/bin/sh
# Uso: ./ejemplos.sh Flor   (o Hoja, Figura8, EstrellaTapas, ...). Las imágenes quedan en target/imagenes.
set -e
cd "$(dirname "$0")"
if [ ! -d target/test-classes ]; then
    if command -v mvn >/dev/null 2>&1; then mvn -q test-compile; else make; fi
fi
mkdir -p target/imagenes
cd target/imagenes
exec java -Djava.awt.headless=true -cp ../classes:../test-classes "${1:?falta el nombre del ejemplo}"
