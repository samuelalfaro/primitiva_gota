# Primitiva Gota: implementación de referencia en Java

Implementación de referencia (raster, píxel a píxel sobre un `BufferedImage` ARGB) de la primitiva y de la composición.
Todo está en el paquete por defecto.

## Organización

```
src/main/java      la biblioteca
  Gota.java              la primitiva: círculos/elipses a lo largo de una Bézier cúbica; geometría, SDF y raster
  Coordenadas.java       coordenadas intrínsecas de un píxel de la gota (Muestra, Uv, semidisco de las tapas)
  Costurador.java        modos de costura de la tira (Gota.Costura: ORTOGONAL, ESPIGA por defecto, SUAVIZADO)
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
Gota ──genera──> Coordenadas ──> Colores   ──┐
                      └───────> Texturas ────┼─> Gota.Relleno ──> color ARGB
                                  Mezcla <───┘  (Colores y Texturas interpolan con Mezcla)
ComposicionColor combina dos rellenos cualquiera; Composicion une gotas y las pinta con esos rellenos.
```

## Modelo de color y textura en la tira

Un solo mapeo: el cuerpo es una tira (u a lo ancho, v a lo largo) y las tapas son semidiscos. El color usa seis colores
(contra, centro y normal de cada círculo; la punta es la media de normal y contra) y la textura se coloca plana sobre el
semidisco. Ver `Coordenadas.discoTapa` y `Colores.Lateral`.

## Modos de costura

`Gota.Costura` decide cómo se reparte la tira entre cuerpo y tapas, igual para colores y texturas
(`gota.conCostura(...)`; por defecto ESPIGA): ORTOGONAL (líneas de t constante normales al camino, tapas semidiscos),
ESPIGA (cada mitad es el radio al punto de tangencia, tapas polares) y SUAVIZADO (como ESPIGA, con Hermites C1 en el eje).
Solo para círculos con solape PLANO; con elipses o TRAZO se mantiene por ahora el cálculo anterior.
