# Prueba de fuego: trenza aleatoria (alisar + altura + relajar)

`TrenzaAleatoria.java`: 16 puntos aleatorios (saltos de 90 a 260, radios 9 a 18, colores aleatorios), una única
composición cerrada, semilla fija. Tres paneles por semilla: A por defecto | B `alisar()` | C `alisar()` + `altura`
(`trenza_aleatoria_42_7.png`, semillas 42 y 7). `java TrenzaAleatoria [semilla]` y `java TrenzaAleatoria semillas 200`.

## Resultados (semillas 1 a 200)
| | resultado |
|---|---|
| excepciones al construir y pintar | 0 |
| cruces de la espina consigo misma | 9,0 de media; todos con paridad correcta (alternar sobre/bajo es posible) |
| aristas rígidas | 0 (los saltos mínimos son 90) |
| salto de curvatura en los nodos, máximo (relativo a la curvatura máxima) | por defecto 111 %; con `alisar()` 1,45 % (2 semillas > 1 %) |
| aristas plegadas (kappa·r >= 1) | por defecto 673 de 3200 (197 semillas); con `alisar()` 1642 (200 semillas) |

- Medir la curvatura con diferencias finitas de paso 1e-3 daba saltos falsos de hasta el 15 % tras `alisar()` en las
  puntas; con 1e-5 salen menos del 0,2 %. Era un fallo de la medida, no de la spline.
- Parametrización de la spline: longitud de cuerda 1 (803 plegadas en 100 semillas), centrípeta 0,5 (872), uniforme
  0 (769), por defecto sin alisar (327). Ninguna evita el aumento; se quedó la longitud de cuerda.

## Lo que muestra
- `alisar()` elimina los quiebros de curvatura, pero en entradas con giros muy cerrados concentra la curvatura y
  multiplica por 2,4 las aristas con la envolvente plegada. La silueta (unión de círculos) no se ve afectada; las
  coordenadas (color y textura por bandas) sí pueden. No se ha medido el efecto en color.
- Un nodo donde la ruta se invierte casi 180° (semilla 2026, nodo 15): con Catmull-Rom el lazo se pasa de largo; con
  la spline se pellizca en una punta. Es una entrada degenerada.
- `altura`: con la altura global suavizada entre cruces, los cruces cercanos o rasantes fallan porque las dos
  cintas igualan su altura dentro de la zona de solape. Mejorado con mesetas del tamaño del solape
  ((r_a + r_b) / sen del ángulo) y transición solo en el hueco libre: la semilla 7 queda limpia salvo algún detalle; la
  42 (cruces muy juntos) conserva defectos, y parte de ellos están también sin `altura` (cortes rectos de TRAZO en
  puntas y giros cerrados, issue #46).

## Abierto
- Decidir qué hace `alisar()` con las puntas: limitar la curvatura, volver a Catmull-Rom en las cadenas donde aumente
  el número de pliegues, o dejarlo documentado.
- Medir el efecto de los pliegues en las coordenadas (color/textura) con `alisar()`.
- Asignación de alturas que tenga en cuenta el solape de forma exacta (hoy es una aproximación por el seno del ángulo
  de cruce).
