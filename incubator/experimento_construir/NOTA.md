# Experimento: ¿`Grafo.construir()` puede devolver una `Geometria` más simple?

Idea (especulativa, de Samuel): que construir el grafo devuelva `Geometria` en lugar de `Grafo`. Con una sola arista, la gota
(con su relleno); sin aristas, un disco (gota de centros coincidentes y radios iguales). Esta carpeta guarda las pruebas, aparte
de la librería. Se ejecuta con `UnaArista` (necesita las clases de `src/main/java`).

## Primera medida: un `Grafo` de una arista frente a `Pintada.de(gota, relleno equivalente)`

| Caso | Silueta distinta | Píxeles distintos | Diferencia máx. por canal |
|---|---|---|---|
| negro, radios iguales, recta | 0 | 0 | 0 |
| dos colores, radios iguales, recta | 0 | 0 | 0 |
| dos colores, radios 50 y 15, recta | 0 | 0 | 0 |
| dos colores, curva con tiradores | 0 | 17182 | 28 |
| curva con `ajustarPendientes(false)` | 0 | 17182 | 28 |

- La **silueta** coincide siempre: la gota que da el `Grafo` es la misma.
- En **recta** el color coincide al píxel con `Colores.dosColores`.
- En **curva** hay una diferencia de color de hasta 28/255 (no es de forma ni de las pendientes del radio: no cambia con
  `ajustarPendientes(false)`). Hipótesis: el `Grafo` reparte el color por longitud de arco y `dosColores` usa otro parámetro;
  por confirmar.

## Siguiente
- Averiguar qué relleno exacto emplea el `Grafo` en una arista (`rellenosPintado()`), para comparar con ese y no con `dosColores`.
- Después, `label`, `altura` y `textura` (propiedades del `Grafo` que habría que trasladar).
- Y el disco (nodo sin aristas): `Gota` de centros coincidentes; comprobar la orientación por defecto.
