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
| 2 | Costura entre dos gotas de borde común con N = 1, 2, 3, 4 (conflation) | pendiente |
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
