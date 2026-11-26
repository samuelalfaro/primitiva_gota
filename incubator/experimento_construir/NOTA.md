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
- ~~Y el disco (nodo sin aristas)~~: hecho, ver abajo.

## Segunda medida: el disco (`Gota` de centros coincidentes y radios iguales)

`Disco.java`: `Gota.lineal(p, 100, p, 100, Perfil.recto())` con `Colores.cuatroColores`, frente a la misma gota con los centros
separados 0,5 (imagen `disco_coincidente_vs_separado.png`, coincidente a la izquierda).

- Es un **círculo**: 31 700 píxeles no blancos frente a los 31 416 del círculo ideal (el exceso es el borde suavizado).
- **Orientación por defecto: el eje x**, como decían las reglas de la gota de cuatro arcos: el extremo 1 queda a la izquierda y
  el 2 a la derecha. Dibujada es indistinguible de la de centros separados 0,5; difieren 136 píxeles de borde en la silueta y
  hay diferencias numéricas pequeñas de color, sin efecto visible.
- Por tanto, un nodo sin aristas puede construirse como esa gota sin caso especial en la forma; lo único por decidir es si la
  orientación debe poder darse (por ejemplo, un ángulo opcional en el nodo).
