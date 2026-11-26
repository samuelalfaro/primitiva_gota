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

## Criterio adoptado (2026-11-26) y cambio en esta rama

Decisión de Samuel: `d` (longitud de arco) por defecto y la t disponible como opción para los estudios donde no se quiere
suavizar los problemas (pliegues, solapes). Cambios:

- `Tira.POR_DEFECTO` pasa a ser `new PorLongitud(new Pesos(1, 2, 1))` (v lineal en la longitud de arco). Nueva `Tira.EN_T`
  (`Pesos(1, 2, 1)`): lo que era el valor por defecto.
- Los tres rellenos que leían t sin pasar por v (`Colores.dosColores`, `Colores.conGamma` y el mapeo en disco de
  `Texturas`) toman en `para(g, op)` la fracción de longitud de arco cuando la tira lo pide (`argb(t, rho)` directo sigue por t).
- `Contorno` y `Trazos` (zonas para el contorno y el alambre, no para el color) siguen con `Tira.EN_T`, como antes.
- Las gotas rectas (y las rígidas de 4 arcos) no cambian: `fraccion(t) = t`.

Con ello, un `Grafo` de una arista curva coincide a 0 píxeles con su gota (`Pintada.de(gota, dosColores)`), con `GLOBAL` y
con `POR_GOTA` (prueba `GrafoPruebas.unaAristaCurva`). Con `Tira.EN_T` el reparto vuelve a diferir (17182 px, máx 28/255 en
la curva con tiradores; 14465 px, máx 19 en la suave).

Pruebas: 19 suites, 0 fallos. Dos pruebas se actualizaron porque daban por hecho «tira por defecto = t»
(`GotaCadena`: v del cuerpo y `Tira.porLongitud`; `GrafoPruebas`: cortes de `POR_GOTA`).

## Pendiente

- Impacto visual en los ejemplos con gotas curvas y color/textura (cambian ligeramente: máx 28/255 en las medidas).
- Revisar si algún estudio (`incubator`, ejemplos de pliegues) debe fijar `Tira.EN_T` de forma explícita.
