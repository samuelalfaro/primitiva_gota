# Issue #86: glosario, `Aporte` y Rejilla

Material de estudio de la [#86](https://github.com/samuelalfaro/primitiva_gota/issues/86). Todo lo de esta carpeta es **propuesta y medida**, nada cambia la API.

- [`GLOSARIO.md`](GLOSARIO.md): glosario de conceptos y homónimos de Vibe (PR #85, movido aquí desde `implementacion/java/`).
- [`rejilla/`](rejilla/): estudio de la Rejilla como componente real (aportes por píxel, prensado, sobremuestreo N×N).

## Decisiones provisionales (de la discusión en la #86)

1. `Tinta` → `Aporte` y `Geometria.capas(...)` → `aporta(...)`: la unidad previa al prensado, venga de una `Gota`, un `Grafo` o un `Grupo`.
2. La **Rejilla** deja de ser metáfora: guarda los aportes de las geometrías y el **prensado** la convierte en imagen. La consulta por píxel se conserva (la usan los mapas y `Coordenadas.parametros`).
3. **Franjas** de `h` filas para acotar memoria (el recorrido se repite por franja; franjas disjuntas, paralelizables).
4. **Sobremuestreo N×N** para antialiasing y subpíxel. `N×N` y el filtro de prensado pertenecen a la **Rejilla**, no al `Aporte` (llevarlo al `Aporte` ahora es optimización prematura).
5. Lo que **funde** (nodos compartidos, `smin`, color continuo en los nodos) es un `Grafo` conexo y entrega un aporte resuelto; lo que no funde es independiente y se apila en la Rejilla.

## Estudio

Preguntas por medir, en orden:

| # | Pregunta | Estado |
|---|---|---|
| 1 | Profundidad de aportes por píxel en los ejemplos (dimensiona la memoria) | primera medida, abajo |
| 2 | Costura entre dos gotas de borde común con N = 1, 2, 3, 4 (conflation) | medida, abajo |
| 3 | ¿Promediar N² muestras al final equivale a no sobremuestrear? Dónde difieren (bordes, texturas, gamma, orden por altura) | pendiente |
| 4 | Qué N iguala la calidad actual con cobertura binaria por muestra (con `common.Compara`) | pendiente |
| 5 | ¿Prensado parcial (seguir añadiendo tras prensar) o solo al final? | abierta |

### 1. Profundidad de aportes

Método: una clase `Plotter` «sombra» (`rejilla/sombra/pintado/Plotter.java`, copia de la de la biblioteca con un contador) se pone antes en el classpath y cuenta, por píxel y por imagen, las capas con cobertura > 0 que reciben todas las llamadas de `plot`. Los ejemplos no se modifican: `rejilla/medir_profundidad.sh Ejemplo...`.

Medida del 2026-11-28 sobre `main` (ee98871); porcentaje de píxeles tocados por número de capas:

| Ejemplo | píxeles tocados | 1 | 2 | 3 | 4 | 5 | ≥ 6 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Abecedario | 162 877 | 99,93 % | 0,07 % | – | – | – | – |
| PezAbisal | 54 958 | 94,81 % | 4,57 % | 0,49 % | 0,13 % | – | – |
| Escarabajo_by_Gemini | 22 771 | 92,08 % | 7,92 % | – | – | – | – |
| Pulpo_by_Grok | 67 288 | 85,84 % | 6,77 % | 3,56 % | 1,56 % | 1,04 % | 1,22 % |
| Betta | 426 400 | 73,82 % | 13,97 % | 9,83 % | 1,62 % | 0,39 % | 0,38 % |
| Monarca_by_Claude | 574 000 | 58,04 % | 6,44 % | 5,92 % | 6,54 % | 6,55 % | 16,50 % |

Lectura provisional:
- Las láminas de contornos finos (abecedario, pez, escarabajo) están casi siempre en 1 capa: una Rejilla dispersa o por franjas sobra.
- **Cautela:** la cuenta incluye los fondos que se pintan con una `Gota` grande (Betta, Monarca, Escarabajo…), que suman una capa a casi todos los píxeles; la profundidad real de las geometrías es una menos en esos casos. Habría que separar el fondo para afinar.
- Monarca (alas y manchas superpuestas) es el caso exigente: un 16 % de píxeles con 6 o más capas. Es el ejemplo a mirar para dimensionar la memoria por píxel.
- La cuenta es de **capas emitidas**, no de aportes ordenados con cobertura parcial; para el borde (pregunta 2) hará falta otra medida.

### 2. Costura entre dos geometrías de borde común

Método (`rejilla/Costura086.java`): dos cápsulas (gotas de radios iguales con los centros separados: borde recto) se apilan con el borde común en `y = 50 + f`, con `f` fraccionario; la fila de píxeles que cruza el borde recibe la cobertura `f` de la roja y `1 − f` de la azul. El sobremuestreo se simula pintando la escena a escala N con el plotter actual y promediando cajas N×N (no es todavía una Rejilla). El error es la mayor diferencia de canal (0..255) entre la fila del borde y el color ideal `f·rojo + (1 − f)·azul`; el contraste entre los dos colores es de 176 niveles.

Cobertura **analítica** (la actual, parcial por píxel), composición «over»:

| N \ f | 0,10 | 0,25 | 0,50 | 0,75 | 0,90 |
|---:|---:|---:|---:|---:|---:|
| 1 | 18,6 | 39,0 | 52,0 | 39,0 | 19,4 |
| 2 | 17,2 | 26,0 | 0,0 | 26,0 | 16,8 |
| 3 | 14,6 | 13,0 | 17,0 | 13,0 | 14,8 |
| 4 | 12,6 | 0,0 | 0,0 | 0,0 | 12,8 |

Cobertura **binaria** por muestra (umbral 0,5 en la clase sombra, `-Dgota.binaria=true`), que emula muestras puntuales:

| N \ f | 0,10 | 0,25 | 0,50 | 0,75 | 0,90 |
|---:|---:|---:|---:|---:|---:|
| 1 | 17,6 | 44,0 | 191,0 | 44,0 | 17,6 |
| 2 | 17,6 | 96,0 | 0,0 | 96,0 | 17,6 |
| 3 | 17,6 | 15,0 | 44,0 | 15,0 | 17,6 |
| 4 | 17,6 | 0,0 | 0,0 | 0,0 | 17,6 |

Lectura provisional:
- **Con N = 1 la costura existe**: el fondo se cuela hasta 52 niveles (en el peor caso `f = 0,5` el fondo aporta `f·(1 − f)` = 25 %).
- **Con cobertura analítica, sobremuestrear la reduce en proporción a 1/N**, no la elimina: cada muestra sigue teniendo cobertura parcial y el mismo efecto se repite a escala menor. Es exacta solo cuando `N·f` es entero (N = 4, f = 0,25).
- **Con cobertura binaria no hay colado de fondo**: queda solo el error de cuantización, como mucho `|A − B| / (2N)` (≈ 22 con N = 4). Los casos grandes (191 con N = 1 y f = 0,5, 96 con N = 2) son empates: el borde cae justo en el centro de la muestra y el umbral la da por cubierta a las dos formas, de modo que gana la última. Una regla de desempate (fraccionaria, o regla de llenado) en la Rejilla evitaría ese salto.
- Para elegir entre las dos hay que pesar: la analítica no tiene empates ni saltos de cuantización pero conserva la costura residual; la binaria la elimina pero necesita N ≥ 3–4 y una regla de empate.
- Limitaciones: es una escena sintética de bordes rectos y color sólido, con promedio en sRGB (no en luz lineal). Falta repetirlo con bordes curvos, degradados y las capas ordenadas por altura al prensar.
