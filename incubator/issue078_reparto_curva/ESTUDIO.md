# Issue #78: `GLOBAL` frente a `POR_GOTA` en un `Grafo` de una arista curva

Rama: `issue078_reparto_curva`. Se ejecuta con `Reparto078.java` (solo API pública; necesita las clases de `src/main/java`):
`java -cp <clases> Reparto078 global_vs_por_gota.png`.

## Resultado

**No es un defecto del `Grafo`: es la diferencia entre dos tiras.** Con una arista, los dos repartos dan los mismos cortes de v
(c1 = 1/4, c3 = 3/4); lo que cambia es el indicador `Cortes.porLongitud`:

| Reparto | Tira que usa | `Cortes` | v dentro del cuerpo |
|---|---|---|---|
| `POR_GOTA` | `Tira.POR_DEFECTO` (`Pesos(1,2,1)`) | `Cortes(1/4, 3/4)` → `porLongitud = false` | lineal en **t** |
| `GLOBAL` | `Tira.fija(c1, c3)` (vía `cortesGlobales`) | `Cortes(1/4, 3/4, true)` | lineal en la **longitud de arco** |

En recta t y la longitud de arco avanzan a la par y no hay diferencia. En curva, t no es proporcional a la longitud de arco
(la curva «acelera» en t), así que el color avanza distinto.

## Medidas (400×300, nodos `0xFFD02020` y `0xFF2040D0`, `Mezcla.RGB`, silueta idéntica en todos los casos)

Pintando la gota suelta con el **mismo relleno lateral que usa el `Grafo`** (`Colores.cadenaLateral`) y cambiando solo la tira:

| Comparación | recta | curva con tiradores | curva suave |
|---|---|---|---|
| `Grafo POR_GOTA` frente a gota con `Tira.POR_DEFECTO` | 0 | **0** | **0** |
| `Grafo GLOBAL` frente a gota con `Tira.fija(1/4, 3/4)` | 0 | **0** | **0** |
| `Grafo GLOBAL` frente a gota con `Tira.POR_DEFECTO` | 0 | 17182 px (máx 28) | 14465 px (máx 19) |
| `Grafo POR_GOTA` frente a gota con `Tira.fija(1/4, 3/4)` | 0 | 17182 px (máx 28) | 14465 px (máx 19) |

Cada reparto del `Grafo` es **exactamente** una gota con su tira. `global_vs_por_gota.png`: `GLOBAL`, `POR_GOTA` y la diferencia ×8.

## Por qué `dosColores` confundía

`Colores.dosColores(a, b)` mezcla por `p.muestra().t()`: no usa v ni los cortes, así que no distingue entre las dos tiras
(`Tira.porLongitud(...)` da lo mismo que la tira por defecto). Coincide con `POR_GOTA` porque ambas son lineales en t; por eso
la comparación del experimento `experimento_construir` salía bien en `POR_GOTA` y mal en `GLOBAL`, sin que el relleno fuese distinto.

## Qué hay que decidir (no es un arreglo)

`GLOBAL` por longitud de arco es intencionado: la textura no se estira donde la curva acelera y continúa entre gotas
(documentado en `Tira.porLongitud` y `Grafo.cortes`). La gota suelta, por defecto, va lineal en t. Para que
«un `Grafo` de una arista» y «su gota» pinten igual hay tres salidas:

1. **Dejarlo y documentarlo**: la equivalencia vale con `Tira.fija(c1, c3)` (o `Tira.porLongitud(Tira.POR_DEFECTO)`) en las `Opciones`.
2. **`construir()` devuelve la gota** con la tira de longitud de arco (necesita que la geometría pueda llevar su tira, hoy solo la llevan las `Opciones` de `plot`).
3. **Cambiar el valor por defecto de la gota a longitud de arco** (`Tira.POR_DEFECTO`): afecta a todos los rellenos y texturas de gota suelta y a las referencias de las suites de color.

Recomendación: 1 por ahora; 2 cuando exista el `construir()` de `Geometria`, porque decide la tira en el mismo sitio donde se decide el tipo.

## Una comprobación que sale de paso

`Tira.porLongitud(Tira.POR_DEFECTO)` y `Tira.fija(1/4, 3/4)` son el mismo `Cortes` (`Cortes(1/4, 3/4, true)`), de modo que esa tira
reproduce `GLOBAL` con una arista; con varias, `GLOBAL` reparte la banda [c1, c3] por la s acumulada de cada gota.
