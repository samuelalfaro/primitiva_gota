# Historia de `estudio_pliegues_polar`

Resumen del log de la rama de estudio de la issue #29 (qué píxel manda donde la envolvente se pliega), reconstruido a partir de
sus commits antes de que se perdiera en el rebase único (`estudio_pliegues_polar_rebase`, "Rebase a lo bruto", 2026-11-23).
Los hashes son los de la rama `estudio_pliegues_polar` anterior al rebase, conservada en la etiqueta `estudio_pliegues_polar`
(`2fc956c`); GitHub ofrece el zip de esa instantánea y `git log estudio_pliegues_polar` recorre la historia completa. El detalle de
cada medida está en `ESTUDIO_SOLAPES.md`, en esta misma carpeta.

**Cifras.** 44 commits distintos (39 de Claude, 3 de Sam, 2 de samuelalfaro), del 2026-11-12 al 2026-11-22. El `git log` da 86 sin
merges porque los 42 primeros aparecen dos veces (una copia anterior a un rebase previo y otra posterior) más 5 merges de `main`.
La rama solo toca tres ficheros de la biblioteca (`Zonas`, `Solape`, `Coordenadas`), añade `Pliegue.java`, los `Estudio*.java`, el
informe y las imágenes.

## 1. Medir quién tiene razón: PLANO frente a TRAZO (12–13 nov)

Punto de partida: en los pliegues, PLANO y TRAZO dan resultados distintos y hay que saber en qué zonas discrepan y por qué.

| Fecha | Commit | Qué se hizo |
|---|---|---|
| 11-12 | `f9e7bdd` | `Solape.PONDERADO` (experimental), `EstudioSolapes` y primer `ESTUDIO_SOLAPES.md` |
| 11-12 | `86bc667`, `c01deee` | Clasificación de las discrepancias por hebras y por cociente de rho |
| 11-12 | `dfffe1e` | Producto escalar con la tangente (signo de u) y mapa del signo |
| 11-12 | `34cee5a` | Etiqueta de pliegue por curvatura y AUC de las variables |
| 11-12 | `1b181d8` | Prueba híbrida: PLANO donde hay pliegue, TRAZO donde es legítimo |
| 11-12 | `7944085` | Pista de oclusión (sombra junto al borde de la capa superior de TRAZO) |
| 11-12 | `21591fc` | `PONDERADO_UV`: promediar en uv, como "barniz" |
| 11-12 | `e0c0332` | Delimitar legítimo / compresión / pliegue con J = 1 + κ·y |
| 11-13 | `a75ae87` | Comprobar las clases con la textura (densidad de texels y saltos de uv) |
| 11-13 | `afaf3b8` | Clasificación por conexión en t, mapa de calor de f y corte duro configurable |
| 11-13 | `5828418` | Contraste de f con densidad de texels y salto de pasada (indicador, no causa) |

Lo que dejó: `PONDERADO` no sirve como solución, pero sirve para localizar las zonas problemáticas; el híbrido por curvatura apenas
cambia nada; la idea que sobrevive es mirar si las dos zonas de un solape se conectan de forma continua en t (legítimo) o si es
una sola (pliegue). Ver las secciones *Experimento*, *Clasificación*, *Prueba híbrida*, *Delimitar* y *¿Qué indica f?* del informe.

## 2. Casos básicos, cúspide y Catmull-Rom (14–15 nov)

| Fecha | Commit | Qué se hizo |
|---|---|---|
| 11-14 | `aed5dc5` | Signo de J por hebra y mapas con el esquema superpuesto |
| 11-14 | `cf8b86b` | Imágenes con P1..P4 en ámbar |
| 11-14 | `5dbb6ae` | Caso límite de la cúspide del camino (fuera de los casos básicos) |
| 11-14 | `737046b` | `EstudioCasosBasicos`: alfa, u, omega, codo |
| 11-14 | `1d2527c`, `bcbb09b`, `74a1b89` | (Sam) Catmull-Rom frente a Bézier, t frente a longitud de arco; nota sobre arcos complementarios |
| 11-15 | `7a976a1` | El esquema pasa a `Trazos.Ajustes.ESQUEMA` (desaparece `PlotterEsquema`) |

La nota de Catmull-Rom está en `NOTA_CATMULL_ARCOS.md` (esta misma carpeta).

## 3. La zona polar del pliegue (16 nov)

Idea: donde el contorno interior se cruza, la gota se parametriza con coordenadas polares centradas en el cruce, con zonas
exactas (no mezclas).

| Commit | Qué se hizo |
|---|---|
| `0dbfcd3` | `Pliegue`, `Solape.PLIEGUE` y `EstudioPliegue` en la costura ORTOGONAL |
| `a1c8e88` | El cruce se busca en c(t) ± r(t)·N, no en la semianchura de la máscara |
| `eecce3e` | Costura ESPIGA: abanico interior entre las costillas y la espina |
| `635ada7` | ORTOGONAL: medida de continuidad en los bordes y semianchura de primera salida (experimental) |
| `1f013a5` | La zona sale del intervalo donde se dispara la primera salida de la normal |
| `f102f98` | La mitad interior de t en [ta, tb] ya no reclama píxeles de la pierna anterior; sector desenrollado |
| `2139481` | Interruptor `-Dgota.pliegue=envolvente\|envolventeX` para probar la envolvente exacta |
| `862ad68` | ORTOGONAL exacto: centro en el cruce de la envolvente, ta y tb en los pies de X sobre la espina |

## 4. Varios pliegues y cuñas anidadas (17 nov)

| Commit | Qué se hizo |
|---|---|
| `5111772` | Varios pliegues por gota: `Pliegue.todos` recoge todos los cruces que son esquina; `Solape.PLIEGUE` filtra contra la lista |
| `85f4f7f` | Medida del salto de u/v en los bordes de los pliegues (con control dentro de la zona); nota sobre v por longitud de arco |
| `bf5083a` | Prioridad entre zonas que se pisan: manda la mitad interior (`EstudioPliegueSolape`) |
| `17de471`, `f6a8efe` | Búsqueda de casos con tres pliegues (t3a, t3b); los tres lóbulos son casos límite de la cúbica, no curvas "bonitas" |
| `6294f17` | Prueba experimental `-Dgota.v=longitud` (textura uniforme); el anidamiento de ORTOGONAL no cambia |
| `5ae7484` | Referencias sobre pliegues en cintas texturadas y aviso sobre patentes de software |
| `9c1d631` | Diagnóstico de cuñas anidadas ORTOGONAL en t3b (`EstudioPliegueCunas`) |
| `0260c38` | Opción (c): cortar la zona que contiene a otras (`-Dgota.anidadas=no` la desactiva) |
| `d08d792` | Opción (A): solo la mitad interior (`-Dgota.anidadas=interior\|cortar\|no`) |
| `5eace4d` | Modos de estudio `acordeon`, `acordeont` y `espina` para zonas anidadas |

Estado en que quedó: por defecto `-Dgota.anidadas=interior`; la alternativa `espina` da 0 solapes pero la cuña ancha pierde su
mitad exterior. Se documentan como casos muy límite.

## 5. Cierre del estudio y rebase (18–23 nov)

| Fecha | Commit | Qué se hizo |
|---|---|---|
| 11-18 | `6d63857`, `1d2fe8c` | `GuiaAjedrez` como textura de referencia de los estudios (`EstudioUV.Guia`): degradado LCH con el centro gris |
| 11-18 | `943753c` | Saltos de u/v en t3a y t3b, con interior y espina (`EstudioPliegueBordesT3`) |
| 11-20, 11-22 | merges | Se sincroniza con `main` (3 merges) y con `Remerfactor01` |
| 11-22 | `2bbe057`, `2fc956c` | (samuelalfaro) Errores de compilación tras rebase y arreglos tras merge |
| 11-23 | `2a84e2c` | `estudio_pliegues_polar_rebase`: instantánea sobre el nuevo reparto de paquetes, en un solo commit |

El estudio se aparca el 18 de noviembre: nada de esta rama está en las suites de matriz y `Solape.PLIEGUE` es opcional.

## Qué contiene esta carpeta

- `HISTORIA.md`: este resumen.
- `ESTUDIO_SOLAPES.md`: el informe completo del estudio, con sus imágenes en esta carpeta.
- `NOTA_CATMULL_ARCOS.md`: la nota de Catmull-Rom y arcos complementarios (no llegó a la instantánea del rebase y se ha recuperado).
- Las imágenes de los estudios (`estudio_pliegue_*`, `pliegue_*`, mapas y clasificaciones).

## Qué no sobrevivió al rebase

- `NOTA_CATMULL_ARCOS.md` (recuperada aquí).
- `estudio_guia_textura.png`, que estaba en la raíz de `implementacion/java/` en la rama anterior.
- La historia fina: cada commit de arriba se aplanó en uno (queda en la etiqueta `estudio_pliegues_polar`).
