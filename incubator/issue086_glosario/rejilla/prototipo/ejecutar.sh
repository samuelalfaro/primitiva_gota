#!/bin/bash
# Compila la biblioteca y el prototipo de Rejilla y ejecuta PruebaRejilla, Nube_by_Mistral y Llama_by_Mistral (desde cualquier carpeta del repo). Tarda ~3 min (N=3 cuesta ~N² veces el Plotter).
set -e
RAIZ=$(git rev-parse --show-toplevel); SRC=$RAIZ/implementacion/java/src; P=$RAIZ/incubator/issue086_glosario/rejilla/prototipo; T=$(mktemp -d)
javac -nowarn -d $T $(find $SRC/main -name "*.java") 2>&1 | grep -v "^Picked\|^Note" || true
javac -nowarn -cp $T -d $T $P/Rejilla.java $P/Sol_by_Claude.java $P/PruebaRejilla.java $P/Nube_by_Mistral.java $P/Llama_by_Mistral.java 2>&1 | grep -v "^Picked\|^Note" || true
java -Djava.awt.headless=true -cp $T PruebaRejilla "${1:-$T/png}" 2>&1 | grep -v Picked
java -Djava.awt.headless=true -cp $T Nube_by_Mistral "${1:-$T/png}" 2>&1 | grep -v Picked
java -Djava.awt.headless=true -cp $T Llama_by_Mistral "${1:-$T/png}" 2>&1 | grep -v Picked
