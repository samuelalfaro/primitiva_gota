> Recuperada de la rama `estudio_pliegues_polar` anterior al rebase (commit bcd8bde, antes dfa7932..74a1b89). Las rutas son las históricas: la fuente es hoy `implementacion/java/src/test/java/EstudioCatmullRom.java`.

# Catmull-Rom y arcos complementarios

Material de la charla de estudio (issue #29).

## `EstudioCatmullRom`

Fuente: `implementacion/java/src/test/java/EstudioCatmullRom.java`.
Imagen: `catmull_rom.png` (generar con `EstudioCatmullRom`).

Comparación CR vs Bézier cúbica de la espina, con muestreo en t y por longitud de arco.

- t equidistante apiña donde |c′| es bajo; longitud de arco reparte regular.
- Una sola Bézier entre extremos no reproduce un lazo; la CR sí.
- CR ya en `Colibri` (firma + remuestreo por arco) y en `Composicion` (tangentes grado 2).

## Arcos complementarios

Imágenes: `arcos_complementarios_d4.png`, `arcos_complementarios_d5.png`.

Barrido de arcos complementarios de las tapas + intermedios por **longitud de arco** y opacidad variable.
Los complementarios esbozan la lente de solape; en pliegues (S con radios extremos) cortan la espina; en cruces legítimos (ჲ) se cruzan entre sí.

Ver comentario en issue #29 para el detalle y el enlace a la fuente.
