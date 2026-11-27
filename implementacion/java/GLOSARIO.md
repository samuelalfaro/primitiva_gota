# Glosario de la primitiva Gota

Cada concepto que usa el código, definido una vez y sin ambigüedad. Es la referencia común antes de
refactorizar: si un nombre se usa para dos cosas, aparece en [Homónimos declarados](#homónimos-declarados)
con su propuesta de renombrado (pendiente de decidir, nada aplicado aún).

Reglas que el glosario defiende:

- **Un término, un significado** en toda la biblioteca. Si un nombre debe vivir en dos planos distintos,
  se documenta aquí y se marca como homónimo.
- **Sin estado global.** No hay estáticos mutables: lo derivado de una gota vive en su `cache()` por
  instancia, las opciones y los resultados son inmutables (records), y solo la *construcción* de
  `Grafo` y `Grupo` es mutante (y no se toca ya al consultarlas).

---

## Familias de coordenadas

La mayor fuente de confusión son las coordenadas emparentadas. Hay tres familias, cada una con su
dominio y su cliente:

```
camino (forma/)              píxel (coordenadas/)          tira (textura y color)
──────────────────           ─────────────────────         ─────────────────────────
t ∈ [0,1]                    ρ (rho)  ∈ [0,1]              u ∈ [0,1]   transversal
    parámetro de la Bézier       distancia radial al centro   (u = 1/2 en el eje;
    (depende de los tiradores)   del círculo que lo cubre      u = 0 y u = 1, lados opuestos)
d         longitud de arco   axial    = d·T / r(t)         v ∈ [0,1]   longitudinal
fraccion(t) = d/L en [0,1]  lateral  = d·N / r(t)             (0 tapa trasera, 1 tapa delantera;
s (Grafo)  longitud desde       (ρ² = axial² + lateral²)      cortes c1 y c3 del cuerpo)
           el nodo 0 por el
           recorrido en profundidad
```

- `t` es el parámetro de la Bézier: avanza más deprisa donde la curva acelera y depende de la
  longitud de los tiradores, no de la forma.
- `fraccion(t)` (antes llamada aquí `d`) es la fracción de longitud de arco recorrida hasta `t`.
  El nombre `d` queda reservado en los documentos para la **distancia entre centros**.
- `v` se reparte según la [`Tira`](#coordenadas--el-píxel-visto-desde-la-gota) y puede ser lineal
  en `t` (`Cortes.porLongitud == false`) o en `fraccion(t)` (`true`).
- La `u` de la **mezcla** de colores (`Mezcla.mezclar(c0, c1, u)`) y la `u = ρ^γ` del modelo radial
  de `Colores` son **otra cosa**: parámetros de interpolación en [0, 1], sin relación con la `u`
  transversal de la tira. Misma letra, planos distintos; se renombrarán solo si el grupo lo decide.

## Árbol de paquetes

```
src/main/java
├── forma/               la primitiva y su forma: qué puntos pertenecen a la gota
│                          Gota, Camino, Mascara, Silueta, SiluetaCurva, Perfil
├── coordenadas/         el píxel visto desde la gota: Muestra (t, ρ, axial, lateral) y tira (u, v)
│                          Coordenadas, Punto, Costura, Zonas, Tira, Cortes, Solape, Contorno, Opciones
├── color/               de un punto a un color: rellenos, degradados, mezclas y texturas
│                          Relleno, Colores, Degradado, Mezcla, Texturas, Combinacion
├── geometria/           lo que se puede pintar: la estructura de capas
│   │                      Geometria, Tinta, Grupo, Visitante
│   └── composicion/     geometrías concretas
│                          Pintada (gota + relleno), Grafo (topología de nodos y aristas)
├── pintado/             plotters: ensamblan y dibujan; son los únicos que conocen a todos
│                          Plotter (raster), PlotterAlambre, PlotterSvg, Trazos
└── utils/               funciones numéricas, sin dependencias del proyecto
```

Las dependencias van solo hacia abajo: `forma` no conoce a nadie; `coordenadas` solo a `forma`
(vía la interfaz `Camino`, nunca la clase `Gota`); y así hasta `pintado`. La regla la comprueba
`common.ArquitecturaPruebas`. Detalle de compilación y uso en [README.md](README.md).

---

## Conceptos, por capa

### `forma` — la primitiva y su forma

- **Gota** — la primitiva: envolvente de la familia de círculos `(c(t), r(t))`, `t ∈ [0, 1]`.
  Contenedor de parámetros (centros, radios de los extremos, perfil y espina Bézier cúbica);
  implementa [`Camino`](src/main/java/forma/Camino.java). No sabe pintarse ni colorearse.
- **Camino** — consulta de solo lectura de la geometría de una gota: `cx/cy(t)`, `rad(t)`,
  tangente, `mascara()`, `fraccion(t)`, `arcos()`, `costilla(t, lado)`. Es lo único que leen
  coordenadas y zonas. **Espina** — la Bézier cúbica de los centros del camino.
- **Centros / extremos** — los círculos de los extremos (`t = 0` y `t = 1`), con sus radios
  `r1` y `r2`. **Tirador** — vector relativo de un punto de control respecto a su centro
  (`Gota.Vec`). Un tirador no es un punto de control: es el desplazamiento hasta él.
- **Perfil** — cómo varía el radio: cúbica de Bézier monótona entre `r1` y `r2`, definida por las
  pendientes `dr/dt` en los extremos (`lineal()` por defecto, o `tangentes(m1, m2)` recortadas
  para conservar la monotonía). Nunca sale de `[r1, r2]`, así que no hay radios negativos.
- **Máscara** (`Mascara`) — pertenencia de un punto a la gota: `contiene`, `cobertura` y `hebras`.
  Lo primero que se decide de un píxel; las coordenadas intrínsecas van después.
- **Envolvente** — la unión de la familia de círculos, el polinomio de grado 6
  `|p - c(t)|² - r(t)²` evaluado en base de Bernstein (`Silueta.Envolvente`, que implementa
  `Mascara`). Es la máscara de toda gota deformable.
- **Gota rígida / deformable** — con distancia entre centros `d ≤ r1 + r2` no cabe la familia de
  círculos: la gota es rígida, se descarta la curva y la espina pasa a ser la recta de los centros.
  Con `d > r1 + r2` la forma es la envolvente y admite curvas y lazos.
- **Silueta** (construcción) — para caminos rectos: dos tapas circulares unidas por dos
  conectores con continuidad G1. El **conector** es por defecto un arco de elipse de mínima
  excentricidad (círculo exacto si existe; **biarco** solo como respaldo o si se pide con
  `Conector.BIARCO`). La clase es interna de `forma`; lo que ve el exterior es un `Shape`.
- **SiluetaCurva** — la silueta recta resuelta con separación igual a la longitud de arco `L` del
  camino y **doblada** a lo largo de él: `(x, y) → c(x) + y·N(x)`, con `x` la longitud de arco.
  Clase de apoyo (pendiente de integrar en `Gota`). Cuando el semiancho supera el radio de
  curvatura el contorno se **pliega** (`pliegue()` lo mide, `plegada()` lo avisa) y manda la
  envolvente. Un **relajo** (`Relajo.EXTERIOR/INTERIOR/AMBOS`) sustituye un conector doblado por
  una spline para evitar el pliegue.
- **Hebra** (`Camino.Hebra`) — una pasada del camino sobre un punto: su `t` y su distancia con
  signo al borde de esa pasada (de donde sale su cobertura). Un píxel puede tener varias.

### `coordenadas` — el píxel visto desde la gota

- **Muestra** (`Coordenadas.Muestra`) — los parámetros de un píxel respecto al círculo de la
  familia que lo cubre: `(t, ρ, axial, lateral)`, con `ρ² = axial² + lateral²`. La pertenencia es
  `ρ ≤ 1`.
- **Tira** — la banda de coordenadas de textura `(u, v)`: `u` transversal (de lado a lado), `v`
  longitudinal (tapa trasera a tapa delantera). Las tapas se mapean a un **semidisco unidad** con
  `l = 2u - 1` y profundidad normalizada `w` (0 en la costura con el cuerpo, 1 en el arco exterior).
  La **Tira** no cambia la forma: decide qué fracción de `v` ocupa cada zona.
- **Cortes** (`Cortes`) — record `(c1, c3, porLongitud)`: la tira se reparte en tapa trasera
  `[0, c1]`, cuerpo `[c1, c3]` (v lineal, en `t` o en `fraccion(t)`) y tapa delantera `[c3, 1]`.
  El corte central del cuerpo es `t = t_m`.
- **Tira** (reparto) — cómo se eligen esos cortes: `Pesos(w1, wC, w2)` (por defecto `1 : 2 : 1`),
  `Proporcional` (tapas y cuerpo según sus tamaños reales), `Fija(c1, c3)` (cortes dados, la que
  usa el `Grafo` con reparto `GLOBAL`), y el envoltorio `PorLongitud`, que fuerza `porLongitud`.
  Constantes: `Tira.POR_DEFECTO` = `PorLongitud(Pesos(1, 2, 1))` y `Tira.EN_T` = `Pesos(1, 2, 1)`
  (el reparto antiguo, lineal en `t`, para estudios).
- **Costura** — dónde caen las costuras entre zonas y cómo se reparte el interior en líneas de
  `t` constante: `ORTOGONAL` (normales al camino, tapas semidiscos), `ESPIGA` (por defecto; cada
  mitad es el radio al punto de tangencia, tapas polares) y `SUAVIZADO` (Hermites C1 en el eje).
  Es independiente del solape y de la forma.
- **Zonas** — el cálculo: produce `(t, u, v)` de un píxel según el modo de costura. Solo conoce
  `Camino`.
- **Solape** — qué pasa donde el camino se cruza consigo mismo: `PLANO` (por defecto; una capa
  por píxel, la de la solución única de la costura) y `TRAZO` (una capa por cada pasada, de
  abajo arriba; manda la de mayor clave: `t` en la banda, y las tapas como si prolongaran el
  trazo). Produce las **capas** de cada píxel. La diferencia se ve con translúcidos y texturas.
- **Capa** (`Solape.Capa`) — record `(cobertura, Punto)`: una contribución de un píxel antes de
  convertir a color. No confundir con `Tinta`.
- **Contorno** — los tramos de la envolvente que son **borde** real de la gota (`borde`) y los
  que quedan dentro por lazos, cuellos o pliegues (`oculta`). Lo usan la silueta de las
  geometrías y el modo alambre.
- **Opciones** — record `(costura, solape, tira)` que se pasa a plotters y geometrías. Son
  independientes entre sí y nunca cambian la forma, solo qué coordenadas (y color) llega a cada
  píxel. Por defecto: `ESPIGA`, `PLANO`, `Tira.POR_DEFECTO`.

### `color` — de un punto a un color

- **Relleno** — función `Punto → color ARGB`. Calcula de la muestra y de la tira solo lo que pide
  (un color sólido no calcula nada). Fábricas en `Colores` y `Texturas`; `Combinacion` combina dos.
- **Modelo radial** (`Colores`) — color entre un color de **centro** `centro(t)` y otro de
  **borde** `borde(t)` según `u = ρ^γ`, con `γ` interpolada entre los extremos de la gota
  (`γ > 1` conserva el color del centro; `γ < 1` adelanta el del borde).
- **Cadena axial** (`Colores`) — coloración a lo largo de la gota sobre la coordenada `v` de la
  tira (extremo → centro1 → centro2 → extremo), con ajuste de transición por tramo.
- **Colores laterales / sectores** — el color de cada lado de la tira (`u < 1/2`, lado
  **contra-normal**; `u > 1/2`, lado **normal**, el de la normal `N` del camino). En un `Grafo`,
  cada nodo declara colores por **sector** (cada hueco entre dos ramas) y cada gota toma el del
  lado normal y el del contra-normal, de modo que el color del contorno es continuo entre ramas.
- **Degradado** — función `x ∈ [0, 1] → ARGB`, sin resolución ni ajustes propios; el ajuste fino
  es una deformación de `x` aplicada antes (`ajustado`, `deformado`).
- **Mezcla** — interpolación entre dos colores: `RGB` (por defecto), `RGB_LINEAL`, `HSL`, `LCH`
  (Oklab) o una lambda propia. HSL y LCH interpolan el vector (croma·cos, croma·sen) para no
  elegir sentido al tono.
- **Textura** — función `(u, v) → ARGB` con aspecto opcional; muestreo de imagen o texturas de
  referencia, y **mapeo en disco** de las tapas (el semidisco unidad visto como sector del disco).
- **Combinación** (`Combinacion`) — combinación de un relleno de color con otro de textura al
  estilo de `glTexEnv` (`REPLACE`, `MODULATE`, `DECAL`, `BLEND`, `ADD`). No confundir con el
  `Grafo`: esa es la composición topológica de gotas, esta la de rellenos.

### `geometria` — lo que se puede pintar

- **Geometría** (`Geometria`) — contrato de lo pintable: `bounds`, `silueta` (contorno como
  `Shape`), `label`, `altura()`, `capas(x, y, opciones, salida)`, `acepta(Visitante)`,
  `caminos()`.
- **Tinta** — la capa final de un píxel: record `(argb, cobertura, altura)`. Cada geometría
  emite sus tintas ya resueltas y `Plotter` las compone de abajo arriba.
- **Grupo** — geometrías sin relación topológica pintadas juntas. No hay orden de pintado: en
  cada píxel se ordenan las capas de todos los hijos por su `Tinta.altura()` (estable: a igual
  altura, debajo la añadida antes). La altura del grupo se **suma** a la de sus hijos.
- **Visitante** — recorrido de la estructura sin exponer a los hijos: `hoja(g)`,
  `entra(Grupo)`/`sale(Grupo)`.
- **Pintada** — una `Gota` con su `Relleno`, como geometría. La gota sola no se puede pintar.
- **Grafo** — composición topológica: gotas que comparten **nodos**. El **nodo** es una sección
  compartida (centro, radio, colores); la **arista** es la gota que conecta dos nodos. Topologías
  que salen del recorrido en profundidad desde el nodo 0: tira, círculo, Y, Y lobulada y 8; un
  **cierre** (`Zn`) es la arista que termina en un nodo ya recorrido; un **lazo**, la que sale y
  llega al mismo nodo. Un nodo solo, sin aristas, es un disco. `s` — longitud de camino desde el
  nodo 0 en ese recorrido (la que usa el reparto `GLOBAL` para el `v`).
- **Reparto** (`Grafo.Reparto`) — cómo corre el `v` de textura y color lateral entre las gotas
  del grafo: `GLOBAL` (por defecto; `v` recorre toda la composición, con `Tira.fija`) o
  `POR_GOTA` (cada gota usa su propia tira). No afecta a la forma ni a `t`: con una sola arista,
  `GLOBAL` y `POR_GOTA` coinciden (ese fue el cierre del issue #78).

### `pintado` — los plotters

- **Plotter** — ensambla máscara, coordenadas y color para dibujar una geometría:
  `Plotter.raster(imagen).plot(geometria, opciones)` compone las `Tinta` de abajo a arriba con
  alfa y antialiasing.
- **Alambre** — representación vectorial de la estructura, sin relleno: los trazos los calcula
  `Trazos` y los dibuja `PlotterAlambre` (raster) o los escribe `PlotterSvg` (SVG, con los
  `Grupo` como `<g id=label>`). No usan `Relleno` ni `Costura`; sus opciones son
  `Trazos.Ajustes`.
- **Rol** (`Trazos.Rol`) — la clase de cada tramo del alambre: `CONTORNO` (la silueta, el
  elemento principal), `OCULTA` (envolvente interior, a trazos), `COSTILLA` (secciones
  interiores), `EXTREMO`, `CAMINO` (la Bézier de los centros) y `TIRADOR`.
- **Cobertura** — fracción del píxel que cubre la gota, en `[0, 1]` (antialiasing de medio píxel
  a cada lado del borde). Un solo concepto con tres puertas de entrada: `Mascara.cobertura`,
  `Hebra.cobertura` y `Tinta.cobertura`. El semiancho del antialiasing es la constante `BANDA`.
- **Cache** — la memoria por instancia de `Camino.cache()`: cálculos derivados del camino
  (coordenadas de la tira, siluetas...). Claves y valores son de quien la usa; vive y muere con
  el camino. Es lo que mantiene la consulta sin estado global.

---

## Homónimos declarados

Nombres que hoy significan dos cosas. Están documentados, no arreglados: cada renombrado es una
decisión del grupo y va con su prueba y su PR.

### `altura` — el caso mayor

Vive en cuatro sitios con tres planos distintos:

| Dónde | Significado | Plano |
|---|---|---|
| `Arista.altura(f)` | ordena las capas **por píxel** dentro de un `Grafo` (quién tapa a quién en cada cruce) | dentro de un grafo |
| `Tinta.altura()` | la capa emitida; `Grupo` la usa para ordenar **entre geometrías** | entre geometrías |
| `Geometria.altura()` (`Grupo`, `Grafo`, `Pintada`) | **nivel** de la geometría respecto a sus hermanas en un `Grupo`; se suma a las tintas de sus capas | entre geometrías |
| `Plotter` (javadoc) | «en orden de altura»: se refiere a la de `Tinta` | entre geometrías |

El propio javadoc de `Tinta` avisa: «hay dos "alturas" con el mismo nombre que viven en planos
distintos». Resumen: *altura de arista* = orden local por píxel; *altura de geometría* = orden
global entre hermanas. Sugerencia pendiente de decidir: llamar `nivel()` a la de `Geometria`
(y `nivel(f)` a la de `Arista`), dejando `altura()` solo para `Tinta`.

### `silueta` — construcción o contorno

1. `Geometria.silueta()` y `Gota.silueta()`: el **contorno** como `Shape`, para alambre, SVG y
   depuración.
2. `forma.Silueta` / `forma.SiluetaCurva`: la **construcción** interna (tapas + conectores) que
   produce la máscara. Clases de paquete, no visibles fuera.
3. `Contorno.borde`: «la silueta» en el sentido 1, calculada desde la envolvente.

Regla de lectura: *silueta* a secas es el contorno visible; la construcción interna es de `forma`.

### `trazo` — solape o alambre

1. `Solape.TRAZO`: el modo que acumula una capa por cada pasada del camino.
2. `Trazos` y sus roles: los trazos del **modo alambre**.

Contextos distintos (pintado con relleno frente a representación vectorial), pero conviene
decidir si `TRAZO` cambia de nombre o si el modo alambre deja de llamarse así.

### `global` — dos usos, ninguno de estado

1. `Grafo.Reparto.GLOBAL`: el `v` de textura corre por toda la composición en vez de reiniciarse
   en cada gota. Es un reparto, no un estado compartido.
2. «Mínimo global» en `Camino`: el mínimo de una función en `[0, 1]` entera (frente a los mínimos
   locales del muestreo). Uso matemático, no de arquitectura.

En ambos casos no hay variable global mutable en la biblioteca; el aviso de fondo del término
es solo semántico.

### `estado` — función, no estado

`SiluetaCurva.estado(x)` devuelve el marco local en la abscisa de arco `x`: `{c(x), T(x), k(x)}`.
Es una **función** de `x`, no estado mutable compartido; el nombre puede confundir con el
«estado» que aquí se evita. Candidato a renombrar (`marco(x)` o `en(x)`) si se toca esa clase.

### `u` — tres dominios

1. La `u` transversal de la tira (`Coordenadas.Uv.u`).
2. La `u = ρ^γ` del modelo radial de `Colores`.
3. El parámetro `u` de `Mezcla.mezclar(c0, c1, u)`.

Misma letra, planos distintos (geometría del píxel, modelo radial, interpolación). El código los
separa por contexto; el glosario los deja anotados por si un renombrado futuro lo zanja.

### `fraccion(t)` y la familia `t / d / s / v`

No son homónimos, pero sí la familia que más cuesta seguir: `t` (parámetro), `fraccion(t)`
(fracción de longitud de arco), `d` (distancia entre centros, solo en documentos), `s`
(longitud desde el nodo 0 en un `Grafo`) y `v` (coordenada de la tira, lineal en `t` o en
`fraccion(t)` según `Cortes.porLongitud`). Ver [Familias de coordenadas](#familias-de-coordenadas).

---

## Pendientes de decidir (nada aplicado)

- `altura()` → `nivel()` en `Geometria`, `Grupo`, `Grafo`, `Pintada` y `Arista` (y revisar el
  javadoc de `Tinta` y de `Plotter`).
- `SiluetaCurva.estado(x)` → `marco(x)` (o el nombre que se decida) si se toca la clase.
- `Solape.TRAZO` frente al modo alambre (`Trazos`): decidir el reparto de nombres.
- Las tres `u`: dejarlas documentadas o renombrar la del modelo radial (`Colores`) y la de
  `Mezcla`.
- Actualizar el árbol de paquetes de [README.md](README.md) si algún renombrado llega a aplicarse.
