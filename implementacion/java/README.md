# Primitiva Gota: implementación de referencia en Java

Implementación de referencia (raster, píxel a píxel sobre un `BufferedImage` ARGB) de la primitiva y de la composición.
Está repartida en paquetes por capas: cada paquete solo depende de los anteriores (lo comprueba `common.ArquitecturaPruebas`).

## Organización

```
src/main/java      la biblioteca, de abajo a arriba
  forma/             la primitiva y su forma
    Gota.java            la primitiva: contenedor de parámetros (centros, radios, perfil y espina Bézier cúbica); implementa Camino
    Camino.java          consulta de solo lectura del camino de una gota (centros, radios, tangente, mascara()); lo implementa Gota
    Mascara.java         pertenencia de un punto a la gota (contiene, cobertura, hebras); la da Camino.mascara()
    Silueta.java         silueta de dos tapas y conector (elíptico, biarco o recta); Silueta.Envolvente es la máscara actual de Gota
    SiluetaCurva.java    silueta doblada sobre una espina curva (clase de apoyo a las pruebas; pendiente de integrar en Gota)
    Perfil.java          perfil del radio a lo largo del camino; resuelve(r1, r2) da los valores efectivos que usa Gota
  coordenadas/       el píxel visto desde la gota
    Coordenadas.java     coordenadas intrínsecas de un píxel de la gota (Muestra, Uv, semidisco de las tapas)
    Punto.java           el píxel visto desde la gota: muestra() y uv() se calculan solo si el relleno las pide
    Costura.java         modos de costura de la tira: ORTOGONAL, ESPIGA (por defecto) y SUAVIZADO
    Zonas.java           reparto de la tira en banda y tapas según la costura (lo usa Coordenadas; solo conoce Camino)
    Opciones.java        record (costura, solape, tira) que se pasa a los plotters y a las geometrías; por defecto ESPIGA, PLANO y la tira 1 : 2 : 1
    Tira.java            reparto del eje v entre tapas y cuerpo: pesos(a, b, c), proporcional() o POR_DEFECTO; produce los Cortes
    Cortes.java          record (c1, c2, c3) con los cortes de v de la tira
    Solape.java          PLANO o TRAZO: cómo se resuelven los solapes; produce las capas (cobertura + Punto), independiente de Costura
    Contorno.java        el contorno de una gota: borde real (la silueta) y envolvente oculta (lazos, cuellos, pliegues)
  color/             de un Punto a un color
    Relleno.java         interfaz de un relleno: de un Punto a un color ARGB (las fábricas están en Colores y Texturas)
    Colores.java         colores sobre las coordenadas (cadena axial, color lateral, tapas de semidisco)
    Texturas.java        texturas: imagen, textura de referencia 1 x 2 y mapeos (tira, disco) sobre las coordenadas
    Degradado.java       degradados
    Mezcla.java          interpolación entre dos colores (RGB, RGB lineal, HSL, Oklch)
    Combinacion.java     composición de rellenos al estilo glTexEnv (color x textura)
  geometria/         lo que se puede pintar
    Geometria.java       contrato: bounds, silueta, label, altura, capas(x, y, opciones, salida); acepta(Visitante), caminos(), recorre, esHoja, nHijos
    Tinta.java           una capa de un píxel: color ARGB, cobertura y altura
    Visitante.java       recorrido de la estructura: hoja, entra(Grupo), sale(Grupo)
    Grupo.java           varias geometrías sin topología; las capas de sus hijos se ordenan por altura (estable)
    composicion/       las geometrías concretas
      Pintada.java         una gota con su relleno, como Geometria
      Grafo.java           composición topológica: varias gotas con nodos compartidos (cadena, ciclo, estrella, ocho); también textura(...)
  pintado/           plotters: solo saben pintar o dibujar Geometria
    Plotter.java         Plotter.raster(imagen).plot(geometria, opciones); también plot(gota, relleno, opciones)
    PlotterAlambre.java  dibuja el alambre de una gota o de una geometría (Graphics2D o imagen)
    PlotterSvg.java      dibuja el alambre como un documento SVG (se escribe al cerrar); los Grupo salen como <g id=label>
    Trazos.java          los trazos del modo alambre (rol + Shape): la ayuda común de PlotterAlambre y PlotterSvg
  utils/             transversal: funciones numéricas auxiliares (Utils), sin dependencias del proyecto
src/test/java      las suites de pruebas (cada una es un programa con main; terminan con código 1 si algo falla)
  common/Todas.java  las ejecuta, cada una en su JVM; niveles: unitarias, regresion y estudio
src/test/resources textura base que usan algunas suites
src/ejemplos/java  dibujos y demos (Flor, Hoja, Figura8, ...); se compilan con las pruebas para que sigan compilando
```

## Uso

Maven es una ayuda de compilación, no un requisito: solo hace falta un JDK 17 o posterior (también valen versiones
posteriores) y todo funciona sin entorno gráfico.

```
mvn test                 # compila todo y ejecuta los niveles rápidos: unitarias + regresion (los segundos)
mvn test -Pestudio       # añade las matrices de estudio (el render completo; ~9 min en hardware modesto)
mvn test -Dsuites.niveles=estudio    # solo un nivel: unitarias, regresion o estudio
./ejemplos.sh Flor       # ejecuta un ejemplo; la imagen queda en target/imagenes
```

Sin Maven, con el `Makefile` (solo `javac` y `java`; mismos directorios `target/` que Maven):

```
make                         # compila la biblioteca (target/classes) y las pruebas y ejemplos (target/test-classes)
make test                    # niveles rápidos: unitarias + regresion
make test NIVELES=estudio    # un nivel concreto, o varios separados por comas; make test-todo ejecuta los tres
make ejemplo EJ=Flor         # ejecuta un ejemplo; la imagen queda en target/imagenes
make clean
make RELEASE=17              # (opcional) exige el nivel 17; con SOURCE=17 si al JDK le falta lib/ct.sym
```

A mano: compilar `src/main/java` en un directorio, `src/test/java` y `src/ejemplos/java` en otro con el primero en el
classpath, copiar `src/test/resources` a este segundo, y lanzar `common.Todas <classpath> [niveles,...]` o cualquier clase.

### Entornos sin pantalla ni fuentes

- **Headless.** Nada abre ventanas: se ejecuta con `-Djava.awt.headless=true` (el `Makefile`, `ejemplos.sh` y `common.Todas`
  ya lo ponen).
- **Fuentes.** El texto solo hace falta para escribir rótulos sobre una imagen: el modo alambre con
  `Trazos.Ajustes.conEtiquetas(true)` (el valor por defecto) pintado en un `Graphics2D`, y las suites de estudio que
  rotulan. Sin fuentes del sistema (sin `fontconfig` o sin `libfreetype`, algo típico de los contenedores) esos casos
  fallan con un mensaje que lo explica, y se arreglan con `conEtiquetas(false)`; las suites lo detectan con
  `common.Fuentes.hay()` y dibujan sin texto.
- **SVG.** `PlotterSvg` escribe las etiquetas como elementos `<text>` y no necesita fuentes del sistema (las pone quien
  abra el SVG). Tampoco las necesita el raster (`Plotter.raster`), ni los ejemplos ni las suites de imagen sin texto.

## Dependencias entre paquetes

```
forma ──> coordenadas ──> color ──> geometria ──> geometria.composicion ──> (nadie)
                                        └──────────────> pintado
utils: lo usan todos; no usa a ninguno
```

Cada paquete solo puede usar los de su izquierda: `forma` no conoce a nadie, `coordenadas` solo a `forma`, y así hasta
`pintado`, que ve todo (la regla la comprueba `ArquitecturaPruebas`). Hoy `geometria.composicion` (Grafo, Pintada) y
`pintado` (plotters) no se usan entre sí: los plotters pintan cualquier `Geometria` y las geometrías no saben pintarse.
`coordenadas` solo conoce la interfaz `Camino`, no la clase `Gota`.

```
Gota ──implementa──> Camino <──lee── Coordenadas ──usa──> Zonas ──lee──> Camino
 │                                      │ (Costura elige el modo de Zonas)
 └─ mascara() ──> Mascara (Silueta.Envolvente)
Coordenadas ──> Colores ──┐
            └─> Texturas ─┼─> Relleno ──> color ARGB     (Colores y Texturas interpolan con Mezcla)
Combinacion combina dos rellenos cualquiera.
Pintada (gota + relleno), Grafo (gotas con nodos compartidos) y Grupo implementan Geometria; Plotter las pinta con capas().
```

## Modelo de color y textura en la tira

Un solo mapeo: el cuerpo es una tira (u a lo ancho, v a lo largo) y las tapas son semidiscos. El color usa seis colores
(contra, centro y normal de cada círculo; la punta es la media de normal y contra) y la textura se coloca plana sobre el
semidisco. Ver `Coordenadas.discoTapa` y `Colores.Lateral`.

## Modos de costura

`Costura` decide cómo se reparte la tira entre cuerpo y tapas, igual para colores y texturas
(`Plotter.raster(dst).plot(gota, relleno, Opciones.POR_DEFECTO.conCostura(...))`; por defecto ESPIGA): ORTOGONAL (líneas de t constante normales al camino, tapas semidiscos),
ESPIGA (cada mitad es el radio al punto de tangencia, tapas polares) y SUAVIZADO (como ESPIGA, con Hermites C1 en el eje).
Valen con solape PLANO y TRAZO. En TRAZO el píxel puede tener varias soluciones (el camino pasa varias veces por él) y manda la de mayor clave: t en la banda, y las tapas como si prolongaran el trazo (la inicial, más cerca del centro es posterior; la final, más lejos del centro es posterior).

`Coordenadas.parametros`, `Coordenadas.uv` y el color del `Plotter` salen de la misma costura (una sola t por píxel). `Plotter.plot(grafo, opciones)` acepta también un `Opciones` (costura y solape), con las mismas por defecto. `Coordenadas.porRho` conserva el criterio de mínimo rho que usa `Grafo` para elegir la gota que manda.

El ejemplo `CosturasDemo` (`./ejemplos.sh CosturasDemo`) genera dos imágenes con los tres modos, con una textura de referencia y con un degradado: `costuras_circulos_texturas` y `costuras_circulos_degradados`.
