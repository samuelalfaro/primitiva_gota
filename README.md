# Primitiva Gota

*Primitiva geométrica 2D definida por la envolvente de una familia de secciones elípticas a lo largo de una curva 
Bézier cúbica.*

## 📌 Descripción General

La **primitiva Gota** es una forma vectorial 2D definida como la envolvente de una familia de elipses (o círculos) cuyo
centro recorre una curva Bézier cúbica y cuyo radio varía según una función de perfil. Esta primitiva permite crear
formas orgánicas y suaves que combinan las propiedades de las elipses con la flexibilidad de las curvas paramétricas.

![](./.README_files/figura-1.svg)

**Características principales:**
- Forma definida por 2 puntos extremos (centros de las secciones) y 2 puntos de control (para la Bézier).
- Radio variable a lo largo del camino con control de suavizado.
- Soporte para elipses (con relación de aspecto configurable) y círculos.
- Sistema de coordenadas intrínsecas para mapeo de texturas y colores.
- Composición topológica: cadenas, ciclos, estrellas y figuras complejas.

## 🔷 Especificación Geométrica

### Definición Matemática

Una **gota** es la envolvente de una familia de secciones elípticas (o circulares, como caso particular) cuyos centros
recorren una curva Bézier cúbica y cuyos radios varían según un perfil.

Sea $t \in [0,1]$ el parámetro del camino. Cada sección tiene:

- Centro $c(t)$, sobre una curva Bézier cúbica.
- Radio normal $b(t)$.
- Relación de aspecto $\lambda(t)$.
- Radio tangente $a(t)=\lambda(t)b(t)$.

En los puntos regulares, la orientación de cada elipse viene dada por la tangente al camino.

La curva central es:

$$
c(t)=(1-t)^3P_0+3(1-t)^2tP_1
+3(1-t)t^2P_2+t^3P_3
$$

Donde:

- $(P_0,P_3)$: centros de las secciones extremas.
- $(P_1,P_2)$: puntos de control de la Bézier.

### Geometría de las Elipses

Para un punto $p$ y una sección de parámetro $t$, definimos:

$$
d=p-c(t)
$$

La tangente unitaria y la normal unitaria son:

$$
T(t)=\frac{c'(t)}{|c'(t)|}
$$

$$
N(t)=(T_y,-T_x)
$$

Las proyecciones del vector sobre los ejes de la elipse son:

$$
d_T=d\cdot T
$$

$$
d_N=d\cdot N
$$

La condición de pertenencia a la sección elíptica es:

$$
\frac{d_T^2}{a(t)^2}
+\frac{d_N^2}{b(t)^2}
\leq 1
$$

Y, puesto que $a(t)=\lambda(t)b(t)$, también puede expresarse como:

$$
d_T^2+\lambda(t)^2d_N^2
-\lambda(t)^2b(t)^2\leq 0
$$

La gota queda definida por la unión de todas las secciones:

$$
G=\bigcup_{t\in[0,1]}E(t)
$$

Donde $E(t)$ es la sección elíptica correspondiente a cada parámetro.

**Nota sobre la implementación:** en el algoritmo se utiliza una dirección $D(t)$ no necesariamente normalizada, derivada de la curva y regularizada para evitar degeneraciones. La condición de pertenencia se expresa entonces mediante:

$$
(d\cdot D)^2
+\lambda(t)^2(d\cdot D^\perp)^2
-\lambda(t)^2b(t)^2|D|^2
\leq 0
$$

Esta expresión es equivalente a la anterior cuando $D$ representa la dirección tangente, sin necesidad de normalizarla. La regularización permite mantener una dirección válida en los casos degenerados.

### Perfil del Radio

El radio normal $b(t)$ varía a lo largo del camino mediante un perfil cuadrático definido por:

- $r_1$: radio inicial.
- $r_2$: radio final.
- $r_m$: radio en la posición $t_m$.
- $s$: parámetro de suavizado, entre 0 y 1.

Por defecto, $t_m=0.5$.

Con suavizado 1, se utiliza una única curva cuadrática de Bézier siempre que su punto de control no produzca radios negativos:

$$
b(t)=(1-t)^2r_1+2(1-t)tq+t^2r_2
$$

El control $q$ se calcula para cumplir $b(t_m)=r_m$:

$$
q=
\frac{r_m-(1-t_m)^2r_1-t_m^2r_2}
{2t_m(1-t_m)}
$$

Si el control no es válido, o el suavizado es inferior a 1, el perfil se construye mediante dos cuadráticas unidas en $t_m$, interpolando sus pendientes entre el perfil lineal por tramos y el perfil cuadrático suave.

- $s=1$: perfil cuadrático suave, cuando sus controles son válidos.
- $s=0$: interpolación lineal entre los tres valores, con un codo en $t_m$.
- Valores intermedios: transición entre ambos comportamientos.

La construcción garantiza radios no negativos.

### Relación de Aspecto (Elipses)

La relación de aspecto se define como:

$$
\lambda(t)=\frac{a(t)}{b(t)}
$$

Donde:

- $a(t)$: radio tangente al camino.
- $b(t)$: radio normal al camino.

La relación de aspecto varía linealmente entre los valores configurados en los extremos:

$$
\lambda(t)=(1-t)\lambda_1+t\lambda_2
$$

Por tanto:

$$
a(t)=\lambda(t)b(t)
$$

- $\lambda=1$: círculo.
- $\lambda>1$: elipse alargada en dirección tangente.
- $\lambda<1$: elipse alargada en dirección normal.

Dominio de la relación de aspecto:

$$
0.05\leq\lambda\leq20
$$

El círculo es un caso particular de la misma primitiva, no una implementación geométrica diferente desde el punto de vista de su definición.

## 🎨 Modelos de Color y Textura

### 📐 Sistema de Coordenadas Intrínsecas

Cada píxel de la gota se describe mediante una muestra:

$$
Muestra(t,\rho,axial,lateral)
$$

Las coordenadas se calculan respecto a la sección seleccionada para representar el píxel:

- $t$: posición a lo largo del camino.
- $axial$: proyección normalizada sobre la tangente.
- $lateral$: proyección normalizada sobre la normal.
- $\rho$: distancia radial normalizada respecto a la sección.

Con $d=p-c(t)$, tangente unitaria $T$, normal unitaria $N$, radio tangente $a(t)$ y radio normal $b(t)$:

$$
axial=\frac{d\cdot T}{a(t)}
$$

$$
lateral=\frac{d\cdot N}{b(t)}
$$

$$
\rho=\sqrt{axial^2+lateral^2}
$$

Se cumple:

$$
\rho^2=axial^2+lateral^2
$$

La pertenencia a la sección viene determinada por:

$$
\rho\leq1
$$

En los círculos, donde $a=b=r$, se recupera el sistema de coordenadas circular original. Se utiliza el mismo sistema de coordenadas para ambas geometrías.

### Coordenadas de Textura (Tira)

![](./.README_files/figura-2.svg)

**Textura de referencia (cápsula 1×2):**

![](./.README_files/figura-3.png)

La tira utiliza dos coordenadas normalizadas:

$$
UV=(u,v),\qquad u,v\in[0,1]
$$

- $u$: posición transversal, de un lado al otro de la gota.
- $v$: posición longitudinal, desde la primera tapa hasta la segunda.

En el cuerpo, $u=0.5$ corresponde al camino central. Las coordenadas $u=0$ y $u=1$ representan los lados opuestos. La orientación concreta de esos lados depende del sentido de avance del camino.

La coordenada longitudinal se distribuye en cuatro regiones:

- $[0,v_1]$: tapa inicial.
- $[v_1,v_2]$: primera mitad del cuerpo.
- $[v_2,v_3]$: segunda mitad del cuerpo.
- $[v_3,1]$: tapa final.

El corte central del cuerpo corresponde a $t=t_m$.

Las tapas se representan como semidiscos. Para convertir las coordenadas de la tira en coordenadas del semidisco unidad:

$$
l=2u-1
$$

$$
h=w\sqrt{1-l^2}
$$

Donde $w$ es la profundidad normalizada de la tapa: vale 0 en la costura con el cuerpo y 1 en el arco exterior.

Esta transformación permite utilizar una misma tira de coordenadas para aplicar tanto colores como texturas, independientemente de que la gota esté formada por círculos o elipses.

### Modelos de Color

La primitiva soporta múltiples modelos de interpolación de color:

```
┌─────────────────────────────────────────────────────────────┐
│                    MODELOS DE INTERPOLACIÓN                 │
├─────────────────────────────────────────────────────────────┤
│  RGB          │  Interpolación lineal en espacio RGB        │
│  RGB_LINEAL   │  Interpolación lineal gamma-correcta        │
│  HSL          │  Interpolación en espacio HSL               │
│  OKLCH        │  Interpolación en espacio Oklch (perceptual)│
│  Personalizada│  Cualquier función lambda propia            │
└─────────────────────────────────────────────────────────────┘
```

**Configuraciones de color:**

```java
// Construcción lineal
Gota.lineal(centro1, radio1, centro2, radio2, perfil);

// Construcción mediante Bézier cúbica
Gota.curva(centro1, radio1, vec1, centro2, radio2, vec2, perfil);

// Perfil del radio
Gota.Perfil.radioMedio(rm);
Gota.Perfil.radioMedio(rm, tm, suavizado);
Gota.Perfil.central(central);
Gota.Perfil.recto();

// Relación de aspecto
Gota.Perfil.radioMedio(rm).aspecto(lambda);
Gota.Perfil.radioMedio(rm).aspecto(lambda1, lambda2);

// Radios tangentes absolutos
Gota.Perfil.radioMedio(rm).radiosTangentes(a1, a2);
```

**Parámetros de la cadena axial:**
- `extremo1`: color en $v=0$ (inicio de la tapa trasera).
- `centro1`: color en $v=v_1$ (centro del primer círculo).
- `centro2`: color en $v=v_3$ (centro del segundo círculo).
- `extremo2`: color en $v=1$ (final de la tapa delantera).
- `ajuste`: controla cuándo se produce la transición (0 = inicio, 0.5 = centro, 1 = final).

## 🔧 Uso

### Requisitos

- Java 17 o superior.
- Maven 3.6+ (opcional).

### Compilación y Ejecución

```bash
# Compilar todo y ejecutar pruebas
mvn test

# Ejecutar un ejemplo específico
echo "Flor" | mvn exec:java -Dexec.mainClass="Flor"

# O usar el script de ejemplos
./ejemplos.sh Flor
```

Las imágenes generadas se guardan en `target/imagenes/`.

### Ejemplos Disponibles

| Ejemplo | Descripción |
|---------|-------------|
| `GotaDemo` | Demostración básica de una gota |
| `Flor` | Composición de gotas formando una flor |
| `Hoja` | Gota configurada como hoja |
| `EstrellaTapas` | Estrella con tapas de semidisco |
| `Figura8` | Dos gotas formando un ocho |
| `GotaTextura` | Gota con textura aplicada |
| `GotaPerfil` | Demostración de diferentes perfiles |
| `GotaColorDemo` | Demostración de modelos de color |
| `GotaSolape` | Pruebas de solapamiento |
| `ArcoDemo` | Arcos formados con gotas |
| `LadosDemo` | Demostración de lados y bordes |

## 🏗️ Arquitectura

### Estructura del Proyecto

```
primitiva_gota/
├── README.md
├── implementacion/
│   └── java/
│       ├── pom.xml
│       ├── src/
│       │   ├── main/java/
│       │   ├── test/java/
│       │   └── ejemplos/java/
│       └── ejemplos.sh
└── ...
```

### Dependencias entre Clases

```
┌─────────────────────────────────────────────────────────────────┐
│                        FLUJO DE DATOS                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Gota ──[genera]──> Coordenadas ──┬──> Colores ───┐            │
│                                    │               │ (Mezcla)   │
│                                    └──> Texturas ──┘            │
│                                                                 │
│   ComposicionColor (combina 2 rellenos)                         │
│   Composicion (une gotas y las pinta)                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 📊 Especificación Técnica Detallada

### Algoritmo de Renderizado

1. **División recursiva**: la gota se divide en tramos hasta que el polinomio de grado 6 (para círculos) o 12 (para elipses) puede evaluarse directamente.
2. **Evaluación en base de Bernstein**: los polinomios se representan y evalúan en esta base, que facilita la subdivisión recursiva y el control de sus valores en cada intervalo.
3. **Antialiasing**: banda de 0.5 píxeles con evaluación conservadora.
4. **Profundidad máxima**: 24 niveles de subdivisión.

### Tipos de Solapamiento

| Tipo | Descripción |
|------|-------------|
| `PLANO` | Un único color por píxel (mínimo $\rho$) |
| `TRAZO` | Cada tramo es una capa, con antialiasing propio |

## 🔗 Recursos Adicionales

- [Implementación de referencia en Java](implementacion/java/README.md)
- [Documentación de las clases](implementacion/java/src/main/java/)
- [Pruebas y ejemplos](implementacion/java/src/test/java/)

## 📜 Licencia

Este proyecto se distribuye bajo la [WTFPL](LICENSE) (*Do What The Fuck You Want To Public License*).

> Lo puedes usar, modificar o ignorar. Está aquí por si a alguien le sirve tanto como a mí.

---

*Generado con ❤️ para la comunidad de gráficos 2D*

