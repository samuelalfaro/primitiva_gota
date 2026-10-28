#!/bin/sh
# Uso: ./ejemplos.sh Flor   (o Hoja, Figura8, EstrellaTapas, ...). Las imágenes quedan en target/imagenes.
set -e
cd "$(dirname "$0")"
[ -d target/test-classes ] || mvn -q test-compile
mkdir -p target/imagenes
cd target/imagenes
exec java -Djava.awt.headless=true -cp ../classes:../test-classes "${1:?falta el nombre del ejemplo}"
