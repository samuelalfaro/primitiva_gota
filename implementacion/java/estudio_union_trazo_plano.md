# Estudio abierto: nodos de unión, TRAZO y PLANO

Rama `experimento_union`. **No es una propuesta para la librería**: son experimentos y un resultado aún por confirmar.
El código experimental de `Composicion` (campos `exp*`) está en la rama solo para poder reproducirlo.

## Problema
En un nodo de grado ≥ 3 (trébol: tres hojas que salen y vuelven a M, más el tallo = 7 extremos) el TRAZO deja cuñas y cuerpos
que cruzan el centro. El criterio PLANO da siete sectores radiales limpios (`estudio_union_plano_vs_trazo.png`, izquierda).
Lo deseable es que los siete extremos se unan con el mismo criterio y que fuera del nodo siga mandando el TRAZO.

## Lo probado (impresión visual, salvo que se indique conteo de píxeles)
| Experimento | Resultado | Imagen |
|---|---|---|
| Radio de unión + smoothstep sobre la s de las gotas que vuelven | Las cuñas siguen | |
| Idem a todas las gotas del nodo (entrada, salida, regreso) | Peor: la frontera solo se desplaza | `estudio_union_suavizado_s.png` |
| Desempate por PLANO entre s casi iguales (umbral 40) | Limpia el trébol; en la serpiente cambia ~14 000 px, porque también altera solapes donde el TRAZO es correcto. Umbral = número mágico | `estudio_union_serpiente_desempate.png` |
| Mezcla de rankings: `(1-λ)·(cuerpo + (s-s0)/R) − λ·rho` | Mejora el centro, pero las gotas que vuelven llevan s ≈ s0 + longitud del lazo y ganan hasta λ≈1 | `estudio_union_mezcla_ranking.png` |
| Idem corrigiendo el salto de s con interruptor duro | Peor: aparecen bloques rectangulares | `estudio_union_mezcla_salto.png` |
| **Mezcla de colores**: pintar con PLANO y con TRAZO y mezclar con smoothstep según la distancia a M | Siete sectores limpios en el centro y transición continua hacia TRAZO; deja algo de fantasma en el anillo | `estudio_union_mezcla_colores.png` |

La mezcla de colores se probó **solo como mezcla de dos renders** (`estudio_union_mezcla_imagen.py`), con centro y radios estimados a ojo. No está implementada en la librería.

## Observaciones
- La serpiente enroscada no tiene nodos de grado ≥ 3 (salvo la lengua): sus escamas del solape no dependen de este estudio. Hipótesis sin comprobar: pliegues de la propia gota en el lado interior de las vueltas.
- Ochos: la mezcla de rankings cambia ~1 450 px; no se ha revisado.

## Abierto
- Implementar la mezcla de colores en la evaluación (dos criterios por píxel solo cerca de nodos) con un único radio de unión.
- Ligar radio interior/exterior al radio del nodo o dejarlos configurables.
- Salto de s en el cierre (las gotas que vuelven llegan con la s del final del ciclo).
- Comprobar la hipótesis de los pliegues de la serpiente con una sola gota.
