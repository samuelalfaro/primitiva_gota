# Primitiva Gota: implementación de referencia en Java

Implementación de referencia (raster, píxel a píxel sobre un `BufferedImage` ARGB) de la primitiva y de la composición.
Todo está en el paquete por defecto.

## Organización

```
src/main/java      la biblioteca
  Gota.java              la primitiva: círculos/elipses a lo largo de una Bézier cúbica; geometría, SDF y raster
  Relleno.java           interfaz de un relleno: de un Punto a un color ARGB (las fábricas están en Colores y Texturas)
  Punto.java             el píxel visto desde la gota: muestra() y uv() se calculan solo si el relleno las pide
  Coordenadas.java       coordenadas intrínsecas de un píxel de la gota (Muestra, Uv, semidisco de las tapas)
  Costura.java           modos de costura de la tira: ORTOGONAL, ESPIGA (por defecto) y SUAVIZADO
  Zonas.java             reparto de la tira en banda y tapas según la costura (lo usa Coordenadas; solo conoce Geometria)
  Geometria.java         consulta de solo lectura de la geometría de una gota (la implementa Gota)
  Solape.java            PLANO o TRAZO: cómo se resuelven los solapes; produce las capas (cobertura + Punto) que pinta el render, independiente de Costura
  Utils.java             funciones numéricas auxiliares
  Colores.java           colores sobre las coordenadas (cadena axial, color lateral, tapas de semidisco)
  Texturas.java          texturas: imagen, textura de referencia 1 x 2 y mapeos (tira, disco) sobre las coordenadas
  Mezcla.java            interpolación entre dos colores (RGB, RGB lineal, HSL, Oklch)
  ComposicionColor.java  composición de rellenos al estilo glTexEnv (color x textura)
  Composicion.java       composición topológica: varias gotas con nodos compartidos (cadena, ciclo, estrella, ocho)
src/test/java      las suites de pruebas (cada una es un programa con main; terminan con código 1 si algo falla)
  Todas.java         las ejecuta todas, cada una en su JVM
src/test/resources textura base que usan algunas suites
src/ejemplos/java  dibujos y demos (Flor, Hoja, Figura8, ...); se compilan con las pruebas para que sigan compilando
```

## Uso

```
mvn test                 # compila todo y ejecuta las suites (las imágenes quedan en target/imagenes)
./ejemplos.sh Flor       # ejecuta un ejemplo; la imagen queda en target/imagenes
```

Sin Maven: compilar `src/main/java` en un directorio, `src/test/java` y `src/ejemplos/java` en otro con el primero en el
classpath, copiar `src/test/resources` a este segundo, y lanzar `Todas <classpath>` o cualquier clase.

## Dependencias entre clases

```
Gota ──implementa──> Geometria <──lee── Coordenadas ──usa──> Zonas ──lee──> Geometria
 │                                         │ (Costura elige el modo de Zonas)
 └─ render/uv/parametros ──> Coordenadas ──> Colores   ──┐
                                  └───────> Texturas ────┼─> Relleno ──> color ARGB
                                              Mezcla <───┘  (Colores y Texturas interpolan con Mezcla)
ComposicionColor combina dos rellenos cualquiera; Composicion une gotas y las pinta con esos rellenos.
Coordenadas y Zonas solo conocen la interfaz Geometria, no la clase Gota.
```

## Modelo de color y textura en la tira

Un solo mapeo: el cuerpo es una tira (u a lo ancho, v a lo largo) y las tapas son semidiscos. El color usa seis colores
(contra, centro y normal de cada círculo; la punta es la media de normal y contra) y la textura se coloca plana sobre el
semidisco. Ver `Coordenadas.discoTapa` y `Colores.Lateral`.

## Modos de costura

`Costura` decide cómo se reparte la tira entre cuerpo y tapas, igual para colores y texturas
(`gota.conCostura(...)`; por defecto ESPIGA): ORTOGONAL (líneas de t constante normales al camino, tapas semidiscos),
ESPIGA (cada mitad es el radio al punto de tangencia, tapas polares) y SUAVIZADO (como ESPIGA, con Hermites C1 en el eje).
Valen para círculos y elipses, con solape PLANO y TRAZO (con elipses, las costillas van a los puntos de contacto con el contorno y las tapas se muestrean con la norma elíptica). En TRAZO el píxel puede tener varias soluciones (el camino pasa varias veces por él) y manda la de mayor clave: t en la banda, y las tapas como si prolongaran el trazo (la inicial, más cerca del centro es posterior; la final, más lejos del centro es posterior).

`Gota.parametros`, `Gota.uv` y el color del render salen de la misma costura (una sola t por píxel). `Gota.porRho` (package-private) conserva el criterio de mínimo rho que usa `Composicion` para elegir la gota que manda.

El ejemplo `CosturasDemo` (`./ejemplos.sh CosturasDemo`) genera cuatro imágenes con los tres modos sobre gotas de círculos y de elipses, con una textura de referencia y con un degradado: `costuras_circulos_texturas`, `costuras_circulos_degradados`, `costuras_elipses_texturas` y `costuras_elipses_degradados`.
