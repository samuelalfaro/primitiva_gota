import java.util.Objects;

/**
 * Colores de relleno para Gota, en coordenadas (t, rho):
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
 * Las dos interpolaciones son funciones intercambiables (Mezcla). Por defecto Mezcla.RGB; también
 * RGB_LINEAL, HSL y LCH (Oklch) con sentido de tono configurable, o cualquier lambda propia.
 * Si solo se da una Mezcla, se usa para las dos. Con tonos HORARIO / ANTIHORARIO / LARGO conviene
 * dar una segunda Mezcla de sentido CORTO para centroBorde: un sentido forzado en esa mezcla
 * puede dar vueltas completas por la rueda de color.
 *
 * Aparte del modelo radial, la cadena axial (extremo1 -> centro1 -> centro2 -> extremo2 sobre la
 * coordenada de tira f) colorea a lo largo de la gota con un ajuste de transición por tramo.
 */
public final class Colores {

    private Colores() {}

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
    public static Gota.Relleno dosColores(int a, int b) { return dosColores(a, b, Mezcla.RGB); }

    public static Gota.Relleno dosColores(int a, int b, Mezcla m) {
        Objects.requireNonNull(m, "mezcla");
        return (t, rho) -> m.mezclar(a, b, clamp01(t));
    }

    /**
     * Configuración 2: dos colores centrales con ajuste de transición entre ellos. Con ajustes
     * extremos (0 o 1) hay que distinguir las tapas, y eso se hace con la coordenada v de la tira:
     * usa la cadena y por tanto solo admite argb(Muestra, Uv), no argb(t, rho).
     */
    public static Gota.Relleno dosColores(int a, int b, double ajusteCC) { return dosColores(a, b, ajusteCC, Mezcla.RGB); }

    public static Gota.Relleno dosColores(int a, int b, double ajusteCC, Mezcla m) {
        // extremo1 = centro1 y extremo2 = centro2: las tapas quedan de color constante
        // y el único tramo con efecto es el central.
        return cadena(a, a, b, b, 0.5, ajusteCC, 0.5, m);
    }

    /** Configuración 3: cuatro colores axiales (extremo1, centro1, centro2, extremo2). */
    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2) {
        return cadena(extremo1, centro1, centro2, extremo2, Mezcla.RGB);
    }

    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2, Mezcla m) {
        return cadena(extremo1, centro1, centro2, extremo2, 0.5, 0.5, 0.5, m);
    }

    /** Configuración 4: la genérica, con los tres ajustes de transición. */
    public static Gota.Relleno cadena(int extremo1, int centro1, int centro2, int extremo2,
                                      double ajusteEC1, double ajusteCC, double ajusteCE2) {
        return cadena(extremo1, centro1, centro2, extremo2, ajusteEC1, ajusteCC, ajusteCE2, Mezcla.RGB);
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

        @Override public int argb(Coordenadas.Muestra mu, Coordenadas.Uv uv) {
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
     * Cadena de dos nodos con color lateral. El cuerpo mezcla primero a lo ancho (en u) en cada nodo y después a lo
     * largo (en v), como un cuadrilátero; las tapas son semidiscos con el centro como origen y la punta es la media de
     * normal y contra (no hay color de extremo aparte). Ver {@link Coordenadas#discoTapa}.
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

        @Override public int argb(Coordenadas.Muestra mu, Coordenadas.Uv uv) {
            double[] d = Coordenadas.discoTapa(uv, a1, a3);
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
        return cuatroColores(a, b, aBorde, bBorde, Mezcla.RGB, Mezcla.RGB);
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
        return conGamma(a, b, aBorde, bBorde, gamma1, gamma2, Mezcla.RGB, Mezcla.RGB);
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

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
