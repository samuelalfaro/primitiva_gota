# Estudio: curvar la gota rígida (d <= r1 + r2), sin pliegues

Experimento opt-in: `-Dgota.rigidaCurva=true` (margen `-Dgota.rigidaCurvaMargen`, 0,9). Sin la propiedad no cambia nada.

## Motivo
Una cinta hecha con muchos nodos cercanos (cuerdas <= r1 + r2) se compone de gotas rígidas: espina recta, tiradores
ignorados, contorno facetado con quiebros de hasta 18° en los nodos (trébol de 60 nodos, r = 17: 18 gotas rígidas).
`Grafo.rigidas()` las lista.

## Criterio
Una gota rígida se trata como la envolvente de su curva (la de siempre para d > r1 + r2) solo si no hay pliegue en
todo el camino: kappa * r < margen, |r'| / |c'| < margen, y centros distintos. Si no, sigue siendo rígida.

## Resultados
| caso | rígidas sin experimento | con experimento |
|---|---|---|
| trébol N = 40, r = 17 | 0 de 40 | 0 de 40 |
| trébol N = 60, r = 17 | 18 de 60 | 0 de 60 |
| trébol N = 90 y 120, r = 17 | todas | 0 |
| trébol N = 60, r = 30 | 60 de 60 | 0 de 60 |
| polígono cerrado de 8 nodos, r = 30, cuerdas ~40 (giros cerrados) | 8 de 8 | 8 de 8 (el criterio las mantiene rígidas) |

- Desalineo máximo de tangentes en nodos (trébol N = 60): 17,96° -> 0,002°.
- Suites: GotaRegresion y GotaCadena sin cambios; GrafoPruebas falla 1 con el experimento activo, la que cuenta
  rígidas esperando el comportamiento por defecto (esperado).
- Imágenes: `estudio_rigida_trebol.png` (rígida | curvada), `estudio_rigida_octogono.png` (los dos iguales).

## Abierto (no medido)
- La silueta rígida (cuatro arcos) no es la unión de círculos: no se ha medido cuánto difiere del contorno curvado
  en las gotas donde el criterio las acepta (diferencia de área y distancia máxima).
- El margen 0,9 es empírico.
- Coordenadas y texturas con gotas que pasan de rígidas a curvas a lo largo de una cadena (cambio de régimen entre
  vecinas): solo se ha mirado el color.
- Con el criterio cumplido en todas las gotas, `relajar()`/`paso` y la continuidad G1 vuelven a tener efecto.
