import java.util.Objects;

/**
 * Rellenos de color para Gota, en coordenadas (t, rho):
 *   t   = posición a lo largo del camino (0 en el centro 1, 1 en el centro 2)
 *   rho = distancia al centro de ese círculo, normalizada por su radio (0 centro, 1 borde)
 *
 * Modelo común:
 *   centro(t) = entreCirculos(a, b, t)            color en el centro de cada círculo
 *   borde(t)  = entreCirculos(aBorde, bBorde, t)  color en el borde exterior de cada círculo
 *   u         = rho ^ gamma(t),  gamma(t) = gamma1 + (gamma2 - gamma1) * t
 *   color     = centroBorde(centro(t), borde(t), u)
 * gamma > 1 conserva más tiempo el color del centro; gamma < 1 adelanta el del borde.
 *
 * Las dos interpolaciones son funciones intercambiables (Mezcla). Por defecto RGB; también
 * RGB_LINEAL, HSL y LCH (Oklch) con sentido de tono configurable, o cualquier lambda propia.
 * Si solo se da una Mezcla, se usa para las dos. Con tonos HORARIO / ANTIHORARIO / LARGO conviene
 * dar una segunda Mezcla de sentido CORTO para centroBorde: un sentido forzado en esa mezcla
 * puede dar vueltas completas por la rueda de color.
 *
 * Aparte del modelo radial, la cadena axial (extremo1 -> centro1 -> centro2 -> extremo2 sobre la
 * coordenada de tira f) colorea a lo largo de la gota con un ajuste de transición por tramo.
 */
public final class Rellenos {

    private Rellenos() {}

    /** Interpolación entre dos colores ARGB: u = 0 devuelve c0, u = 1 devuelve c1. */
    @FunctionalInterface
    public interface Mezcla {
        int mezclar(int c0, int c1, double u);
    }

    /**
     * Sentido en que el tono recorre la rueda de color (HSL y LCH).
     * HORARIO = tono creciente (rojo → amarillo → verde → azul), ANTIHORARIO = decreciente.
     */
    public enum Tono {
        /** El arco más corto entre los dos tonos (valor por defecto). */
        CORTO,
        /** El arco más largo. Con dos tonos iguales da una vuelta completa (igual que CSS). */
        LARGO,
        /** Tono creciente. */
        HORARIO,
        /** Tono decreciente. */
        ANTIHORARIO
    }

    /** sRGB directo, alfa premultiplicado. Es el valor por defecto. */
    public static final Mezcla RGB = Rellenos::mezclaRgb;
    /** Interpola en luz lineal (sin la curva sRGB), alfa premultiplicado. */
    public static final Mezcla RGB_LINEAL = Rellenos::mezclaRgbLineal;
    /** Tono, saturación, luminosidad; arco más corto del tono. */
    public static final Mezcla HSL = hsl(Tono.CORTO);
    /** Luminosidad, croma, tono en Oklab (variante perceptual de LCH); arco más corto del tono. */
    public static final Mezcla LCH = lch(Tono.CORTO);

    public static Mezcla hsl(Tono tono) {
        Objects.requireNonNull(tono, "tono");
        return (c0, c1, u) -> mezclaHsl(c0, c1, u, tono);
    }

    public static Mezcla lch(Tono tono) {
        Objects.requireNonNull(tono, "tono");
        return (c0, c1, u) -> mezclaOklch(c0, c1, u, tono);
    }

    // ---------- Fábricas ----------

    // ---------- Cadena axial de color ----------
    //
    // Cuatro anclas en la coordenada de tira f: extremo1 (f = 0) -> centro1 (f = corte1)
    // -> centro2 (f = corte3) -> extremo2 (f = 1), con un ajuste normalizado s en [0, 1]
    // por tramo (neutro 0.5): s < 0.5 adelanta el color de destino, s > 0.5 lo retrasa,
    // y s = 0 / s = 1 concentran la transicion en el inicio / final del tramo (saltos
    // deliberadamente abruptos). El ajuste dice CUANDO se avanza entre dos colores;
    // la Mezcla dice COMO se calcula el intermedio. Las configuraciones 1-3 son casos
    // particulares de la 4: una unica cadena interna resuelve todas.

    /**
     * Configuración 1: un color en el centro de cada círculo. Solo depende de t, así que admite
     * argb(t, rho) y es idéntico a la cadena con los tres ajustes neutros.
     */
    public static Gota.Relleno dosColores(int a, int b) { return dosColores(a, b, RGB); }

    public static Gota.Relleno dosColores(int a, int b, Mezcla m) {
        Objects.requireNonNull(m, "mezcla");
        return (t, rho) -> m.mezclar(a, b, clamp01(t));
    }

    /**
     * Configuración 2: dos colores centrales con ajuste de transición entre ellos. Con ajustes
     * extremos (0 o 1) hay que distinguir las tapas, y eso se hace con la coordenada v de la tira:
     * usa la cadena y por tanto solo admite argb(Muestra, Uv), no argb(t, rho).
     */
    public static Gota.Relleno dosColores(int a, int b, double ajusteCC) { return dosColores(a, b, ajusteCC, RGB); }

    public static Gota.Relleno dosColores(int a, int b, double ajusteCC, Mezcla m) {
        // extremo1 = centro1 y extremo2 = centro2: las tapas quedan de color constante
        // y el único tramo con efecto es el central.
        return cadena(a, a, b, b, 0.5, ajusteCC, 0.5, m);
    }

    /** Configuración 3: cuatro colores axiales (extremo1, centro1, centro2, extremo2). */
    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2) {
        return cadena(extremo1, centro1, centro2, extremo2, RGB);
    }

    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2, Mezcla m) {
        return cadena(extremo1, centro1, centro2, extremo2, 0.5, 0.5, 0.5, m);
    }

    /** Configuración 4: la genérica, con los tres ajustes de transición. */
    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2,
                                      double ajusteEC1, double ajusteCC, double ajusteCE2) {
        return cadena(extremo1, centro1, centro2, extremo2, ajusteEC1, ajusteCC, ajusteCE2, RGB);
    }

    /**
     * Cadena cuyas anclas son los cortes de la tira de la gota que la pinta (por defecto 1/4 y 3/4):
     * el color usa la misma coordenada v que las texturas, así que ambos alinean siempre.
     */
    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2,
                                      double ajusteEC1, double ajusteCC, double ajusteCE2, Mezcla m) {
        return new Cadena(extremo1, centro1, centro2, extremo2, ajusteEC1, ajusteCC, ajusteCE2, m, 0.25, 0.75);
    }

    private static final class Cadena implements Gota.Relleno {
        private final int e1, c1, c2, e2;
        private final double sEC1, sCC, sCE2;
        private final Mezcla m;
        private final double a1, a3;

        Cadena(int e1, int c1, int c2, int e2, double sEC1, double sCC, double sCE2, Mezcla m,
               double a1, double a3) {
            Objects.requireNonNull(m, "mezcla");
            checkAjuste(sEC1, "ajusteEC1");
            checkAjuste(sCC, "ajusteCC");
            checkAjuste(sCE2, "ajusteCE2");
            if (!(0 < a1 && a1 < a3 && a3 < 1))
                throw new IllegalArgumentException("los cortes deben cumplir 0 < c1 < c3 < 1");
            this.e1 = e1; this.c1 = c1; this.c2 = c2; this.e2 = e2;
            this.sEC1 = sEC1; this.sCC = sCC; this.sCE2 = sCE2;
            this.m = m; this.a1 = a1; this.a3 = a3;
        }

        @Override public Gota.Relleno para(Gota g) {
            return new Cadena(e1, c1, c2, e2, sEC1, sCC, sCE2, m, g.corte1(), g.corte3());
        }

        @Override public boolean usaUv() { return true; }

        @Override public int argb(double t, double rho) {
            throw new UnsupportedOperationException("la cadena necesita la coordenada v: use argb(Muestra, Uv)");
        }

        @Override public int argb(Gota.Muestra mu, Gota.Uv uv) {
            return colorCadena(e1, c1, c2, e2, sEC1, sCC, sCE2, m, uv.v(), a1, a3);
        }
    }

    // ---------- Cadena con color lateral ----------

    /**
     * Colores de un nodo a lo ancho de la tira: contra (u = 0, lado de -N), centro (u = 1/2) y normal (u = 1,
     * lado de +N, con N = (Ty, -Tx) y T la dirección de avance de la gota). u es la coordenada exacta de la
     * tira: vale 0 y 1 en el contorno.
     */
    public record Lateral(int contra, int centro, int normal, double ajuste, double ajusteArco) {
        public Lateral {
            checkAjuste(ajuste, "ajuste");
            checkAjuste(ajusteArco, "ajusteArco");
        }

        public Lateral(int contra, int centro, int normal, double ajuste) { this(contra, centro, normal, ajuste, 0.5); }

        public Lateral(int contra, int centro, int normal) { this(contra, centro, normal, 0.5, 0.5); }

        public static Lateral uniforme(int c) { return new Lateral(c, c, c); }

        /**
         * Misma terna con otro ajuste de la transición centro -> lado ({@link #transicion}): 0.5 es lineal; por debajo
         * de 0.5 el punto medio de la mezcla queda más cerca del centro (el color del centro se estrecha y la
         * nervadura se marca), por encima se aleja.
         */
        public Lateral conAjuste(double s) { return new Lateral(contra, centro, normal, s, ajusteArco); }

        /**
         * Misma terna con otro ajuste de la mezcla polar normal -> contra en las tapas (a lo largo del arco, de 0 a pi):
         * 0.5 es lineal y la punta es la media; por debajo de 0.5 el punto medio de la mezcla se adelanta hacia el lado
         * normal (la punta queda más cerca del contra), por encima se retrasa. El cuerpo no usa este ajuste.
         */
        public Lateral conAjusteArco(double s) { return new Lateral(contra, centro, normal, ajuste, s); }

        /** Peso del color contra a lo largo del arco (t = th / pi). */
        private double pesoArco(double t) { return ajusteArco == 0.5 ? t : transicion(t, ajusteArco); }

        /** Peso del color del lado a la distancia t (0 en el centro, 1 en el borde) del centro. */
        private double peso(double t) { return ajuste == 0.5 ? t : transicion(t, ajuste); }

        int en(double u, Mezcla m) {
            return u <= 0.5 ? m.mezclar(contra, centro, 1 - peso(1 - 2 * u)) : m.mezclar(centro, normal, peso(2 * u - 1));
        }

        /**
         * Color en el semidisco de una tapa, con coordenadas de disco (l, a): l es el lateral (-1 en el lado contra,
         * 1 en el normal) y a la profundidad hacia la punta, en el disco unidad. Radio s = |(l, a)| y ángulo
         * th = atan2(a, l): 0 en el lado normal, pi/2 en la punta y pi en el lado contra. En el arco (s = 1) el color
         * va de normal a contra por th (con el ajuste lineal la punta es la media) y hacia dentro va al centro linealmente en s.
         * Con a = 0 coincide con en((l + 1) / 2): el cuerpo y la tapa casan en la costura.
         */
        int enDisco(double l, double a, Mezcla m) {
            double s = Math.min(1, Math.hypot(l, a));
            double th = Math.atan2(a, l);
            return m.mezclar(centro, m.mezclar(normal, contra, pesoArco(th / Math.PI)), peso(s));
        }
    }

    /**
     * Las tapas de la tira son semidiscos. Con (u, w) de la tira (w va de 0 en la costura con el cuerpo a 1 en el arco),
     * l = 2u - 1 y a = w·sqrt(1 - l²) dan el punto (l, a) del semidisco unidad (en la gota recta de radios iguales es la
     * posición real del píxel). Como w es una fracción de la profundidad de la tapa, el cuerpo y la tapa casan para
     * cualquier pendiente del radio y para las elipses. La textura se coloca plana sobre ese semidisco (v' = c1·(1 - a))
     * y el color lateral va en polares (s, th): ver {@link Lateral#enDisco}.
     */
    /** {l, a, fin} del punto de tapa de la tira (fin 0 = primera, 1 = segunda), o null si cae en el cuerpo. */
    static double[] discoTapa(Gota.Uv uv, double c1, double c3) {
        double v = uv.v(), w;
        int fin;
        if (v < c1) { w = 1 - v / c1; fin = 0; }
        else if (v > c3) { w = (v - c3) / (1 - c3); fin = 1; }
        else return null;
        double l = 2 * clamp01(uv.u()) - 1;
        return new double[] { l, w * Math.sqrt(Math.max(0, 1 - l * l)), fin };
    }

    /**
     * Cadena de dos nodos con color lateral. El cuerpo mezcla primero a lo ancho (en u) en cada nodo y después a lo
     * largo (en v), como un cuadrilátero; las tapas son semidiscos con el centro como origen y la punta es la media de
     * normal y contra (no hay color de extremo aparte). Ver {@link #discoTapa}.
     */
    public static Gota.Relleno cadenaLateral(Lateral nodo1, Lateral nodo2, Mezcla m) {
        return cadenaLateral(nodo1, nodo2, 0.5, m);
    }

    /** Igual, con el ajuste de la transición entre los dos nodos (0.5 = lineal; ver {@link #transicion}). */
    public static Gota.Relleno cadenaLateral(Lateral nodo1, Lateral nodo2, double ajusteCC, Mezcla m) {
        return new CadenaLateral(nodo1, nodo2, ajusteCC, m, 0.25, 0.75);
    }

    private static final class CadenaLateral implements Gota.Relleno {
        private final Lateral n1, n2;
        private final double sCC;
        private final Mezcla m;
        private final double a1, a3;

        CadenaLateral(Lateral n1, Lateral n2, double sCC, Mezcla m, double a1, double a3) {
            Objects.requireNonNull(m, "mezcla");
            Objects.requireNonNull(n1, "nodo1");
            Objects.requireNonNull(n2, "nodo2");
            checkAjuste(sCC, "ajusteCC");
            if (!(0 < a1 && a1 < a3 && a3 < 1))
                throw new IllegalArgumentException("los cortes deben cumplir 0 < c1 < c3 < 1");
            this.n1 = n1; this.n2 = n2; this.sCC = sCC; this.m = m; this.a1 = a1; this.a3 = a3;
        }

        @Override public Gota.Relleno para(Gota g) { return new CadenaLateral(n1, n2, sCC, m, g.corte1(), g.corte3()); }

        @Override public boolean usaUv() { return true; }

        @Override public int argb(double t, double rho) {
            throw new UnsupportedOperationException("la cadena lateral necesita (u, v): use argb(Muestra, Uv)");
        }

        @Override public int argb(Gota.Muestra mu, Gota.Uv uv) {
            double[] d = discoTapa(uv, a1, a3);
            if (d != null) return (d[2] == 0 ? n1 : n2).enDisco(d[0], d[1], m);
            double u = clamp01(uv.u());
            return m.mezclar(n1.en(u, m), n2.en(u, m), transicion(tramoLocal(uv.v(), a1, a3), sCC));
        }
    }

    // ---------- Transición y cadena (internos; visibles en el paquete para las pruebas) ----------

    /** u(t, s): posición efectiva en el tramo. v0.1; el contrato público es el ajuste s. */
    static double transicion(double t, double s) {
        if (s <= 0) return t > 0 ? 1 : 0;        // salto justo tras el inicio (deliberado)
        if (s >= 1) return t < 1 ? 0 : 1;        // salto justo antes del final (deliberado)
        return s <= 0.5 ? Math.pow(t, 2 * s) : 1 - Math.pow(1 - t, 2 - 2 * s);
    }

    private static void checkAjuste(double s, String nombre) {
        if (!(s >= 0 && s <= 1))
            throw new IllegalArgumentException(nombre + " debe estar en [0, 1]");
    }

    /** Color de la cadena en f, con anclas en 0, ancla1, ancla2 y 1. */
    private static int colorCadena(int e1, int cA, int cB, int e2,
                                   double sEC1, double sCC, double sCE2, Mezcla mez,
                                   double f, double ancla1, double ancla2) {
        if (f <= ancla1)
            return mez.mezclar(e1, cA, transicion(tramoLocal(f, 0, ancla1), sEC1));
        if (f >= ancla2)
            return mez.mezclar(cB, e2, transicion(tramoLocal(f, ancla2, 1), sCE2));
        return mez.mezclar(cA, cB, transicion(tramoLocal(f, ancla1, ancla2), sCC));
    }

    /** Coordenada local del tramo [fa, fb], recortada a [0, 1]. */
    private static double tramoLocal(double f, double fa, double fb) {
        return clamp01((f - fa) / (fb - fa));
    }

    /** Nivel 2: color de centro y de borde para cada círculo. */
    public static Gota.Relleno cuatroColores(int a, int b, int aBorde, int bBorde) {
        return cuatroColores(a, b, aBorde, bBorde, RGB, RGB);
    }

    public static Gota.Relleno cuatroColores(int a, int b, int aBorde, int bBorde, Mezcla m) {
        return cuatroColores(a, b, aBorde, bBorde, m, m);
    }

    public static Gota.Relleno cuatroColores(int a, int b, int aBorde, int bBorde,
                                             Mezcla entreCirculos, Mezcla centroBorde) {
        return conGamma(a, b, aBorde, bBorde, 1, 1, entreCirculos, centroBorde);
    }

    /** Nivel 3: nivel 2 más una corrección gamma centro→borde en cada círculo. */
    public static Gota.Relleno conGamma(int a, int b, int aBorde, int bBorde, double gamma1, double gamma2) {
        return conGamma(a, b, aBorde, bBorde, gamma1, gamma2, RGB, RGB);
    }

    public static Gota.Relleno conGamma(int a, int b, int aBorde, int bBorde,
                                        double gamma1, double gamma2, Mezcla m) {
        return conGamma(a, b, aBorde, bBorde, gamma1, gamma2, m, m);
    }

    public static Gota.Relleno conGamma(int a, int b, int aBorde, int bBorde, double gamma1, double gamma2,
                                        Mezcla entreCirculos, Mezcla centroBorde) {
        Objects.requireNonNull(entreCirculos, "entreCirculos");
        Objects.requireNonNull(centroBorde, "centroBorde");
        if (!(gamma1 > 0) || !(gamma2 > 0))
            throw new IllegalArgumentException("gamma debe ser > 0");
        return (t, rho) -> {
            double tt = clamp01(t);
            double u = clamp01(rho);
            double g = gamma1 + (gamma2 - gamma1) * tt;
            if (g != 1) u = Math.pow(u, g);
            return centroBorde.mezclar(entreCirculos.mezclar(a, b, tt),
                                       entreCirculos.mezclar(aBorde, bBorde, tt), u);
        };
    }

    /**
     * Mapeo de textura en tira: v corre a lo largo de la gota y u a lo ancho. Es la misma coordenada
     * Uv que usa la cadena de color, así que ambas alinean. La textura se divide en cuatro bandas de alto
     * (por defecto cuartos): tapa del primer círculo, cuerpo hasta tm, cuerpo desde tm y tapa del segundo.
     * Las tapas son semidiscos ({@link #discoTapa}): con la textura de referencia 1 x 2 una gota recta de radios
     * iguales reproduce la textura idéntica.
     */
    public static Gota.Relleno tira(Texturas.Textura tex) {
        Objects.requireNonNull(tex, "textura");
        return new Tira(tex, 0, 0, 1, 1, null, null);   // sin remapeo: usa los cortes de la gota tal cual
    }

    /**
     * Igual que tira(tex), sobre el subrectángulo (u0, v0)-(u1, v1) de la textura (por ejemplo, un atlas)
     * y con los cortes de las tapas como fracciones de su alto, 0 <= c1 <= c3 <= 1. El corte central sigue
     * a tm: el cuerpo de la gota se reparte entre c1 y c3 en la misma proporción que en la propia gota.
     */
    public static Gota.Relleno tira(Texturas.Textura tex, double u0, double v0, double u1, double v1,
                                    double corte1, double corte3) {
        Objects.requireNonNull(tex, "textura");
        if (!(0 <= corte1 && corte1 <= corte3 && corte3 <= 1))
            throw new IllegalArgumentException("los cortes deben cumplir 0 <= c1 <= c3 <= 1");
        return new Tira(tex, u0, v0, u1, v1, null, new double[] { corte1, corte3 });
    }

    private static final class Tira implements Gota.Relleno {
        private final Texturas.Textura tex;
        private final double u0, v0, u1, v1;
        private final double[] desde, hasta;   // cortes de la gota -> cortes de la textura (null sin remapeo)

        Tira(Texturas.Textura tex, double u0, double v0, double u1, double v1, double[] desde, double[] hasta) {
            this.tex = tex; this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.desde = desde; this.hasta = hasta;
        }

        @Override public Gota.Relleno para(Gota g) {
            double[] d = { 0, g.corte1(), g.corte2(), g.corte3(), 1 };
            if (hasta == null) return new Tira(tex, u0, v0, u1, v1, d, d);
            double c1 = hasta[0], c3 = hasta[1];
            double den = g.corte3() - g.corte1();
            double c2 = den > 0 ? c1 + (c3 - c1) * (g.corte2() - g.corte1()) / den : c1;
            return new Tira(tex, u0, v0, u1, v1, d, new double[] { 0, c1, c2, c3, 1 });
        }

        @Override public boolean usaUv() { return true; }

        @Override public int argb(double t, double rho) {
            throw new UnsupportedOperationException("la tira necesita las coordenadas Uv: use argb(Muestra, Uv)");
        }

        @Override public int argb(Gota.Muestra m, Gota.Uv uv) {
            if (desde == null)   // sin enlazar con una gota: v tal cual
                return tex.muestrea(u0 + (u1 - u0) * clamp01(uv.u()), v0 + (v1 - v0) * clamp01(uv.v()));
            double v = uv.v(), f = 1;
            double[] d = discoTapa(uv, desde[1], desde[3]);   // la tapa es el semidisco: v = c1·(1 - a) o c3 + (1 - c3)·a
            if (d != null) v = d[2] == 0 ? desde[1] * (1 - d[1]) : desde[3] + (1 - desde[3]) * d[1];
            for (int i = 0; i < 4; i++) {
                if (v <= desde[i + 1] || i == 3) {
                    double w = desde[i + 1] - desde[i];
                    f = w <= 0 ? hasta[i + 1] : hasta[i] + (hasta[i + 1] - hasta[i]) * clamp01((v - desde[i]) / w);
                    break;
                }
            }
            return tex.muestrea(u0 + (u1 - u0) * clamp01(uv.u()), v0 + (v1 - v0) * f);
        }
    }

    /** Igual que {@link #tira(Texturas.Textura)}: hay un solo mapeo de textura en la tira (tapas de semidisco). */
    public static Gota.Relleno textura(Texturas.Textura tex) { return tira(tex); }

    // ---------- Mapeo en disco ----------
    //
    // Cada círculo (o elipse) de la familia se lleva a un disco de la textura mediante una semejanza:
    //     (u, v) = c(t) + s(t) · R(θ(t)) · (x', y')
    // con (x', y') las coordenadas del píxel dentro de su círculo, normalizadas (el círculo es el disco
    // unidad; en las elipses, cada eje por su propio radio). Los extremos fijan los discos: c1 y c2 son los centros
    // de la textura a los que va el centro de cada círculo, y el punto del borde e1 / e2 fija a la vez la escala
    // s = |e - c| y el ángulo θ = atan2(e - c). Entre ambos se interpolan linealmente c y s, y θ con el sentido
    // de Tono (CORTO por defecto). Como en glTexEnv y la tira, depende solo de (t, axial, lateral).
    //
    // Con TANGENTE el sistema local gira con el camino (x' en el sentido de avance, y' hacia la derecha del avance en
    // pantalla): el punto e va a la proa del círculo. Con FIJA el sistema local son los ejes de la pantalla
    // (x' hacia +x, y' hacia +y): la textura no gira aunque el camino lo haga, y e es el punto del borde que mira a +x.
    // Si el camino se curva mucho, los círculos próximos de la parte interior de la curva pueden pisarse y la
    // textura se pliega (es la misma costura dura del solape PLANO).

    /**
     * Disco de la textura: su centro (cu, cv) y un punto de su borde (eu, ev), en coordenadas normalizadas.
     * Si el borde coincide con el centro el radio es 0: ese extremo muestrea siempre el texel (cu, cv) (color plano,
     * con ángulo 0) y la interpolación con el otro extremo sigue definida. No se rechaza: es un degenerado y se
     * resuelve de la forma más simple.
     */
    public record Disco(double cu, double cv, double eu, double ev) {
        public Disco {
            if (!Double.isFinite(cu) || !Double.isFinite(cv) || !Double.isFinite(eu) || !Double.isFinite(ev))
                throw new IllegalArgumentException("disco no finito");
        }

        /** El disco inscrito en la textura: centro (1/2, 1/2), borde a la derecha. */
        public static Disco inscrito() { return new Disco(0.5, 0.5, 1, 0.5); }

        /**
         * Disco de la tapa de arriba de la textura de referencia 1 x 2 ({@link Texturas#referencia()}): su centro está
         * en (1/2, 1/4), radio de medio ancho, y el eje del camino (el borde) apunta hacia +v, hacia el cuerpo.
         */
        public static Disco capsulaInicio() { return new Disco(0.5, 0.25, 0.5, 0.5); }

        /** Disco de la tapa de abajo de la textura de referencia: centro (1/2, 3/4), eje del camino hacia +v. */
        public static Disco capsulaFin() { return new Disco(0.5, 0.75, 0.5, 1.0); }
    }

    /** Orientación del sistema local del círculo al llevarlo a la textura. */
    public enum Orientacion {
        /** Gira con el camino. */
        TANGENTE,
        /** Ejes de la pantalla: la textura no gira. */
        FIJA
    }

    /** Todos los círculos al disco inscrito en la textura, orientado con el camino (una especie de proyección). */
    public static Gota.Relleno disco(Texturas.Textura tex) {
        return disco(tex, Disco.inscrito(), Disco.inscrito(), Orientacion.TANGENTE, Tono.CORTO);
    }

    public static Gota.Relleno disco(Texturas.Textura tex, Disco primero, Disco segundo) {
        return disco(tex, primero, segundo, Orientacion.TANGENTE, Tono.CORTO);
    }

    /**
     * Mapeo en disco pensado para la textura de referencia 1 x 2: el primer círculo es la tapa de arriba y el último
     * la de abajo, y los círculos intermedios se deslizan entre ellos. En una gota recta de radios iguales con los
     * centros a dos radios de distancia reproduce la textura idéntica.
     */
    public static Gota.Relleno discoCapsula(Texturas.Textura tex) {
        return disco(tex, Disco.capsulaInicio(), Disco.capsulaFin(), Orientacion.TANGENTE, Tono.CORTO);
    }

    public static Gota.Relleno disco(Texturas.Textura tex, Disco primero, Disco segundo, Orientacion orientacion) {
        return disco(tex, primero, segundo, orientacion, Tono.CORTO);
    }

    public static Gota.Relleno disco(Texturas.Textura tex, Disco primero, Disco segundo,
                                     Orientacion orientacion, Tono giro) {
        Objects.requireNonNull(tex, "textura");
        Objects.requireNonNull(primero, "primero");
        Objects.requireNonNull(segundo, "segundo");
        Objects.requireNonNull(orientacion, "orientacion");
        Objects.requireNonNull(giro, "giro");
        return new DiscoRelleno(tex, primero, segundo, orientacion, giro, null);
    }

    private static final class DiscoRelleno implements Gota.Relleno {
        private final Texturas.Textura tex;
        private final Disco d1, d2;
        private final Orientacion orientacion;
        private final Tono giro;
        private final Gota gota;
        private final double s1, s2, th1, th2;   // escala (en unidades del ancho) y ángulo (grados) de cada extremo
        private final double al;                  // aspecto ancho/alto de la textura

        DiscoRelleno(Texturas.Textura tex, Disco d1, Disco d2, Orientacion o, Tono giro, Gota gota) {
            this.tex = tex; this.d1 = d1; this.d2 = d2; this.orientacion = o; this.giro = giro; this.gota = gota;
            al = tex.aspecto();
            if (!(al > 0) || !Double.isFinite(al)) throw new IllegalArgumentException("aspecto de la textura no válido");
            // El radio y el ángulo se miden en unidades del ancho (v se divide por el aspecto ancho/alto), así un
            // círculo de la textura sigue siendo un círculo aunque la imagen no sea cuadrada.
            double du1 = d1.eu() - d1.cu(), dv1 = (d1.ev() - d1.cv()) / al;
            double du2 = d2.eu() - d2.cu(), dv2 = (d2.ev() - d2.cv()) / al;
            s1 = Math.hypot(du1, dv1);
            s2 = Math.hypot(du2, dv2);
            th1 = Math.toDegrees(Math.atan2(dv1, du1));
            th2 = Math.toDegrees(Math.atan2(dv2, du2));
        }

        @Override public Gota.Relleno para(Gota g) { return new DiscoRelleno(tex, d1, d2, orientacion, giro, g); }

        @Override public int argb(double t, double rho) {
            throw new UnsupportedOperationException("el mapeo en disco necesita la muestra completa");
        }

        @Override public int argb(Gota.Muestra m) {
            double t = clamp01(m.t());
            double cu = d1.cu() + (d2.cu() - d1.cu()) * t, cv = d1.cv() + (d2.cv() - d1.cv()) * t;
            double s = s1 + (s2 - s1) * t;
            double th = Math.toRadians(interpolaAngulo(th1, th2, t, giro));
            double x, y;
            if (orientacion == Orientacion.TANGENTE) {
                x = m.axial(); y = -m.lateral();             // N' = T girado +90° en pantalla (eje u hacia +y)
            } else {
                if (gota == null) throw new IllegalStateException("la orientación FIJA necesita enlazar el relleno con una gota (render lo hace)");
                double phi = gota.angulo(t), tx = Math.cos(phi), ty = Math.sin(phi);
                x = m.axial() * tx + m.lateral() * ty;       // componentes en pantalla de axial·T + lateral·N
                y = m.axial() * ty - m.lateral() * tx;
            }
            double c = Math.cos(th), sn = Math.sin(th);
            return tex.muestrea(cu + s * (x * c - y * sn), cv + al * s * (x * sn + y * c));
        }
    }

    /** Ángulo interpolado en grados, normalizado a [0, 360), con el sentido pedido (mismas reglas que el tono). */
    static double interpolaAngulo(double a0, double a1, double u, Tono tono) {
        double corto = ((a1 - a0 + 180) % 360 + 360) % 360 - 180;   // [-180, 180)
        double d = switch (tono) {
            case CORTO -> corto;
            case LARGO -> corto > 0 ? corto - 360 : corto + 360;
            case HORARIO -> ((a1 - a0) % 360 + 360) % 360;
            case ANTIHORARIO -> -(((a0 - a1) % 360 + 360) % 360);
        };
        return (((a0 + d * u) % 360) + 360) % 360;
    }

    // ---------- Color x textura (al estilo de glTexEnv) ----------

    /**
     * Cómo se combinan el color de base Cf (el relleno de color) y el texel Ct (el relleno de textura),
     * con las fórmulas de glTexEnv sobre canales no premultiplicados y una textura RGBA:
     *   REPLACE : C = Ct              A = At
     *   MODULATE: C = Cf * Ct         A = Af * At
     *   DECAL   : C = Cf (1 - At) + Ct At    A = Af
     *   BLEND   : C = Cf (1 - Ct) + Ce Ct    A = Af * At     (Ce = color de entorno)
     *   ADD     : C = min(1, Cf + Ct)        A = Af * At
     */
    public enum Modo { REPLACE, MODULATE, DECAL, BLEND, ADD }

    /** Combina un relleno de color con uno de textura (o con cualquier otro relleno). BLEND usa Ce = negro. */
    public static Gota.Relleno combinar(Gota.Relleno color, Gota.Relleno textura, Modo modo) {
        return combinar(color, textura, modo, 0xFF000000);
    }

    /** Igual, con el color de entorno Ce de BLEND (su alfa se ignora, como en OpenGL el de GL_BLEND). */
    public static Gota.Relleno combinar(Gota.Relleno color, Gota.Relleno textura, Modo modo, int entorno) {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(textura, "textura");
        Objects.requireNonNull(modo, "modo");
        return new Combinado(color, textura, modo, entorno);
    }

    /** Atajo: relleno de color multiplicado, reemplazado, etc. por una textura en tira. */
    public static Gota.Relleno conTextura(Gota.Relleno color, Texturas.Textura tex, Modo modo) {
        return combinar(color, textura(tex), modo);
    }

    private static final class Combinado implements Gota.Relleno {
        private final Gota.Relleno color, tex;
        private final Modo modo;
        private final int entorno;

        Combinado(Gota.Relleno color, Gota.Relleno tex, Modo modo, int entorno) {
            this.color = color; this.tex = tex; this.modo = modo; this.entorno = entorno;
        }

        @Override public Gota.Relleno para(Gota g) { return new Combinado(color.para(g), tex.para(g), modo, entorno); }

        @Override public boolean usaUv() { return true; }

        @Override public int argb(double t, double rho) {
            throw new UnsupportedOperationException("el relleno combinado necesita la muestra y las coordenadas Uv");
        }

        @Override public int argb(Gota.Muestra m, Gota.Uv uv) {
            return aplica(modo, color.argb(m, uv), tex.argb(m, uv), entorno);
        }
    }

    /** Fórmulas de glTexEnv; público en el paquete para poder contrastarlas en las pruebas. */
    static int aplica(Modo modo, int cf, int ct, int ce) {
        double af = (cf >>> 24) / 255.0, at = (ct >>> 24) / 255.0, a;
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) {
            int s = 16 - 8 * i;
            double f = ((cf >> s) & 0xFF) / 255.0, t = ((ct >> s) & 0xFF) / 255.0, e = ((ce >> s) & 0xFF) / 255.0;
            r[i] = switch (modo) {
                case REPLACE -> t;
                case MODULATE -> f * t;
                case DECAL -> f * (1 - at) + t * at;
                case BLEND -> f * (1 - t) + e * t;
                case ADD -> Math.min(1, f + t);
            };
        }
        a = switch (modo) {
            case REPLACE -> at;
            case DECAL -> af;
            default -> af * at;
        };
        return q(a) << 24 | q(r[0]) << 16 | q(r[1]) << 8 | q(r[2]);
    }

    private static int q(double v) { return (int) Math.round(clamp01(v) * 255); }

    // ---------- RGB ----------

    private static int mezclaRgb(int c0, int c1, double u) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double a0 = (c0 >>> 24) / 255.0, a1 = (c1 >>> 24) / 255.0;
        double a = a0 + (a1 - a0) * u;
        if (a <= 0) return 0;
        int r = canal(c0 >> 16, c1 >> 16, a0, a1, a, u);
        int g = canal(c0 >> 8, c1 >> 8, a0, a1, a, u);
        int b = canal(c0, c1, a0, a1, a, u);
        return (int) Math.round(a * 255) << 24 | r << 16 | g << 8 | b;
    }

    private static int canal(int s0, int s1, double a0, double a1, double a, double u) {
        double p0 = (s0 & 0xFF) * a0, p1 = (s1 & 0xFF) * a1;
        return (int) Math.round((p0 + (p1 - p0) * u) / a);
    }

    // ---------- RGB lineal ----------

    private static final double[] LIN = new double[256];
    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            LIN[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }

    private static int codifica(double lin) {
        double v = lin <= 0.0031308 ? 12.92 * lin : 1.055 * Math.pow(lin, 1 / 2.4) - 0.055;
        return (int) Math.round(clamp01(v) * 255);
    }

    private static int mezclaRgbLineal(int c0, int c1, double u) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double a0 = (c0 >>> 24) / 255.0, a1 = (c1 >>> 24) / 255.0;
        double a = a0 + (a1 - a0) * u;
        if (a <= 0) return 0;
        int r = canalLin(c0 >> 16, c1 >> 16, a0, a1, a, u);
        int g = canalLin(c0 >> 8, c1 >> 8, a0, a1, a, u);
        int b = canalLin(c0, c1, a0, a1, a, u);
        return (int) Math.round(a * 255) << 24 | r << 16 | g << 8 | b;
    }

    private static int canalLin(int s0, int s1, double a0, double a1, double a, double u) {
        double p0 = LIN[s0 & 0xFF] * a0, p1 = LIN[s1 & 0xFF] * a1;
        return codifica((p0 + (p1 - p0) * u) / a);
    }

    // ---------- HSL ----------

    private static int mezclaHsl(int c0, int c1, double u, Tono tono) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double[] x = rgbAHsl(c0), y = rgbAHsl(c1);
        double h = mezclaTono(x[0], x[1], y[0], y[1], u, 1e-6, tono);
        double s = x[1] + (y[1] - x[1]) * u;
        double l = x[2] + (y[2] - x[2]) * u;
        double a = (c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * u;
        return hslARgb(h, s, l, a);
    }

    private static double[] rgbAHsl(int c) {
        double r = ((c >> 16) & 0xFF) / 255.0, g = ((c >> 8) & 0xFF) / 255.0, b = (c & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, d = max - min, h = 0, s = 0;
        if (d > 0) {
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return new double[] { h, s, l };
    }

    private static int hslARgb(double h, double s, double l, double alfa) {
        double c = (1 - Math.abs(2 * l - 1)) * s, hp = h / 60.0;
        double x = c * (1 - Math.abs(hp % 2 - 1)), m = l - c / 2;
        double r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return empaqueta(alfa, (r + m) * 255, (g + m) * 255, (b + m) * 255);
    }

    // ---------- Oklch ----------

    private static int mezclaOklch(int c0, int c1, double u, Tono tono) {
        if (u <= 0) return c0;
        if (u >= 1) return c1;
        double[] x = rgbAOklch(c0), y = rgbAOklch(c1);
        double h = mezclaTono(x[2], x[1], y[2], y[1], u, 2e-3, tono);
        double cr = x[1] + (y[1] - x[1]) * u;
        double l = x[0] + (y[0] - x[0]) * u;
        double a = (c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * u;
        return oklchARgb(l, cr, h, a);
    }

    private static double[] rgbAOklch(int c) {
        double r = LIN[(c >> 16) & 0xFF], g = LIN[(c >> 8) & 0xFF], b = LIN[c & 0xFF];
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double bigL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        double oa = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        double ob = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
        double h = Math.toDegrees(Math.atan2(ob, oa));
        if (h < 0) h += 360;
        return new double[] { bigL, Math.hypot(oa, ob), h };
    }

    private static int oklchARgb(double bigL, double chroma, double h, double alfa) {
        double hr = Math.toRadians(h), oa = chroma * Math.cos(hr), ob = chroma * Math.sin(hr);
        double l = bigL + 0.3963377774 * oa + 0.2158037573 * ob;
        double m = bigL - 0.1055613458 * oa - 0.0638541728 * ob;
        double s = bigL - 0.0894841775 * oa - 1.2914855480 * ob;
        l = l * l * l; m = m * m * m; s = s * s * s;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        // fuera de gamut sRGB: se recorta cada canal
        return empaqueta(alfa, codifica(r), codifica(g), codifica(b));
    }

    // ---------- Utilidades ----------

    /** Tono interpolado según el sentido pedido; si un extremo es acromático toma el tono del otro. */
    private static double mezclaTono(double h0, double c0, double h1, double c1,
                                     double u, double eps, Tono tono) {
        if (c0 < eps) return h1;
        if (c1 < eps) return h0;
        double corto = ((h1 - h0 + 180) % 360 + 360) % 360 - 180;   // [-180, 180)
        double d = switch (tono) {
            case CORTO -> corto;
            case LARGO -> corto > 0 ? corto - 360 : corto + 360;
            case HORARIO -> ((h1 - h0) % 360 + 360) % 360;
            case ANTIHORARIO -> -(((h0 - h1) % 360 + 360) % 360);
        };
        double h = (h0 + d * u) % 360;
        return h < 0 ? h + 360 : h;
    }

    private static int empaqueta(double a, double r, double g, double b) {
        return clamp255(a) << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
    }

    private static int clamp255(double v) { return (int) Math.round(v < 0 ? 0 : (v > 255 ? 255 : v)); }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
