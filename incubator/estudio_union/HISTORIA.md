# Historia de `experimento_union`

Resumen del log de la rama original `experimento_union` (20–22 nov 2026), reconstruido al llevarla al diseño por paquetes
(`forma` → `coordenadas` → `color` → `geometria` → `geometria.composicion` → `pintado`). La rama original se conserva en el tag
`experimento_union`; los hashes de abajo son los de esa rama (los commits aparecen duplicados por un merge intermedio con `main`,
entre paréntesis el segundo hash).

## Línea temporal

| Fecha | Commit | Qué se hizo |
|---|---|---|
| 20 nov | `8903e67` (`f23d72e`) | **Cambio de librería.** En los nodos de grado ≥ 3 el TRAZO decide como PLANO dentro de un disco de 1.6 radios del nodo: ahí convergen varias gotas con la misma s y no hay orden de encima/debajo. Quita las cuñas oscuras del trébol (issue #47) salvo una pequeña muesca. El margen 1.6 es empírico (con 1.0 quedan puntas fuera del disco). |
| 20 nov | `58474e3` (`a6ec9cc`) | **Estudio abierto** `estudio_union_trazo_plano.md`: seis experimentos para unir mejor los nodos de cruce (radio de unión con smoothstep, desempate por PLANO, mezcla de rankings, salto de s, mezcla de colores de dos renders). Solo la mezcla de colores da un resultado limpio, y solo como postproceso de imágenes (`estudio_union_mezcla_imagen.py`). El código experimental (campos `exp*` de `Composicion`) iba en la rama «solo para reproducir». |
| 20 nov | `5a683f6` (`9bd9c6c`) | Ejemplo `Nudo`: el trébol de tres cruces como una sola cinta cerrada. Muestra el límite del TRAZO (*lo que va más tarde queda encima*): no puede alternar encima/debajo. Ese límite es el que motivó `Arista.altura` y `NudoAltura`. |
| 22 nov | `d80b73f`, `37368c9`, `3d6ba1c` | Merges con `main`/`Refactor01` y «Correcciones tras merge» (`Composicion` → `Grafo` en `Nudo`). |

## Qué se llevó al nuevo diseño

- `Grafo.enNodoDeCruce` (`geometria.composicion`): la regla de grado ≥ 3, con su constante `MARGEN_NODO_CRUCE`.
- `ejemplos/Nudo.java`, adaptado a `Plotter.raster(im).plot(grafo, opciones)` (ya no existe `Grafo.render`).
- En `incubator/estudio_union/`: `estudio_union_trazo_plano.md`, `estudio_union_mezcla_imagen.py` y las 8 imágenes `estudio_union_*.png`.

## Qué no se llevó

- Los campos `exp*` de `Composicion` y sus ramas en `sEfectiva` y en la decisión del TRAZO (`expRadioUnion`, `expSuavizado`,
  `expNodoPlano`, `expEmpate`, `expTodos`, `expMezcla`, `expCuerpo`, `expSalto`): son estado estático experimental que no
  encaja en la librería. Para reproducir los experimentos hay que descargar el tag y mirar el commit `58474e3`.
- Los cambios de `Grafo` de la rama que ya estaban en `main` por otro camino (la parte del diff que se ve contra `main` es solo
  el cambio de paquetes).

## Verificación

Tras el port: compilan `main`, `test` y `ejemplos`; las 19 suites pasan (0 fallos). El trébol con TRAZO sigue mostrando la
pequeña muesca descrita en el estudio y `Nudo` produce el mismo resultado que en la rama original.

## Abierto (heredado del estudio)

Ver la sección «Abierto» de `estudio_union_trazo_plano.md`: mezcla de colores con un único radio de unión, radio interior/exterior
ligados al nodo, salto de s en el cierre y la hipótesis de los pliegues en la serpiente enroscada.
