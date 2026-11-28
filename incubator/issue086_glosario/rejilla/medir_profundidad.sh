#!/bin/bash
# Mide cuántas capas con cobertura > 0 recibe cada píxel tocado en los ejemplos (profundidad de aportes).
# Uso: medir_profundidad.sh Ejemplo [Ejemplo...]   (desde la raíz del repo; ejemplos de implementacion/java/src/ejemplos/java)
# La clase sombra Plotter (rejilla/sombra) se pone ANTES que main en el classpath; los ejemplos no se tocan.
set -e
RAIZ=$(git rev-parse --show-toplevel); SRC=$RAIZ/implementacion/java/src; T=$(mktemp -d)
mkdir -p $T/main $T/sombra $T/ej
javac -nowarn -d $T/main $(find $SRC/main -name "*.java") 2>&1 | grep -v "^Picked\|^Note" || true
javac -nowarn -cp $T/main -d $T/sombra $RAIZ/incubator/issue086_glosario/rejilla/sombra/pintado/Plotter.java 2>&1 | grep -v "^Picked\|^Note" || true
for c in "$@"; do
  javac -nowarn -sourcepath $SRC/ejemplos/java -cp $T/main -d $T/ej $SRC/ejemplos/java/$c.java 2>&1 | grep -v "^Picked\|^Note" || true
  mkdir -p $T/run/$c; (cd $T/run/$c && java -Djava.awt.headless=true -cp $T/sombra:$T/main:$T/ej $c 2>&1 | grep -a PROFUNDIDAD | sed "s/^/$c: /")
done
