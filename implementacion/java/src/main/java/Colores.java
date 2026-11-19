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
 * RGB_LINEAL, HSL y LCH (Oklab), o cualquier lambda propia. Si solo se da una Mezcla, se usa para las dos.
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
    public static Relleno dosColores(int a, int b) { return dosColores(a, b, Mezcla.RGB); }

    public static Relleno dosColores(int a, int b, Mezcla m) {
        Objects.requireNonNull(m, "mezcla");
        return p -> m.mezclar(a, b, clamp01(p.muestra().t()));
    }

    /**
     * Configuración 2: dos colores centrales con ajuste de transición entre ellos. Con ajustes
     * extremos (0 o 1) hay que distinguir las tapas, y eso se hace con la coordenada v de la tira:
     * usa la cadena y por tanto solo admite argb(Muestra, Uv), no argb(t, rho).
     */
    /** @deprecated Se sustituirá por un {@link Degradado}; el ajuste es solo una deformación de x. */
    @Deprecated
    public static Relleno dosColores(int a, int b, double ajusteCC) { return dosColores(a, b, ajusteCC, Mezcla.RGB); }

    /** @deprecated Se sustituirá por un {@link Degradado}; el ajuste es solo una deformación de x. */
    @Deprecated
    public static Relleno dosColores(int a, int b, double ajusteCC, Mezcla m) {
        // extremo1 = centro1 y extremo2 = centro2: las tapas quedan de color constante
        // y el único tramo con efecto es el central.
        return cadena(a, a, b, b, 0.5, ajusteCC, 0.5, m);
    }

    /** Configuración 3: cuatro colores axiales (extremo1, centro1, centro2, extremo2). */
    public static Relleno cadena(int extremo1, int centro1, int centro2, int extremo2) {
        return cadena(extremo1, centro1, centro2, extremo2, Mezcla.RGB);
    }

    public static Relleno cadena(int extremo1, int centro1, int centro2, int extremo2, Mezcla m) {
        return cadena(extremo1, centro1, centro2, extremo2, 0.5, 0.5, 0.5, m);
    }

    /** Configuración 4: la genérica, con los tres ajustes de transición. */
    @Deprecated
    public static Relleno cadena(int extremo1, int centro1, int centro2, int extremo2,
                                      double ajusteEC1, double ajusteCC, double ajusteCE2) {
        return cadena(extremo1, centro1, centro2, extremo2, ajusteEC1, ajusteCC, ajusteCE2, Mezcla.RGB);
    }

    /**
     * Cadena cuyas anclas son los cortes de la tira de la gota que la pinta (por defecto 1/4 y 3/4):
     * el color usa la misma coordenada v que las texturas, así que ambos alinean siempre.
     */
    @Deprecated
    public static Relleno cadena(int extremo1, int centro1, int centro2, int extremo2,
                                      double ajusteEC1, double ajusteCC, double ajusteCE2, Mezcla m) {
        return new Cadena(extremo1, centro1, centro2, extremo2, ajusteEC1, ajusteCC, ajusteCE2, m, 0.25, 0.75);
    }

    private static final class Cadena implements Relleno {
        private final int e1, c1, c2, e2;
        private final double sEC1, sCC, sCE2;
        private final Mezcla m;
        private final double a1, a3;
        private final Degradado dEC1, dCC, dCE2;

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
            this.dEC1 = Degradado.entre(e1, c1, m).ajustado(sEC1);
            this.dCC = Degradado.entre(c1, c2, m).ajustado(sCC);
            this.dCE2 = Degradado.entre(c2, e2, m).ajustado(sCE2);
        }

        @Override public Relleno para(Gota g, Opciones op) {
            Cortes c = op.tira().cortes(g);
            return new Cadena(e1, c1, c2, e2, sEC1, sCC, sCE2, m, c.c1(), c.c3());
        }

        @Override public int argb(Punto p) {
            double f = p.uv().v();
            if (f <= a1) return dEC1.en(tramoLocal(f, 0, a1));
            if (f >= a3) return dCE2.en(tramoLocal(f, a3, 1));
            return dCC.en(tramoLocal(f, a1, a3));
        }
    }

    // ---------- Funciones: el esquema (•~~•) ----------

    /**
     * Las cinco funciones de color de una gota, sobre el esquema
     * <pre>
     *   \  /
     *  (•~~•)
     *   /  \
     * </pre>
     * Los cuatro radios van del centro (x = 0) al punto de contorno (x = 1), y la espina del centro 1 (x = 0) al
     * centro 2 (x = 1). Deben coincidir en los centros (tolerancia de 1/255 por canal).
     * La validación solo comprueba esos valores en los centros, a modo orientativo: que las funciones sean continuas
     * cerca de ellos (y que haya o no saltos duros cerca de los bordes) es responsabilidad de quien las escribe.
     */
    public record Funciones(Degradado normal1, Degradado contra1, Degradado espina,
                            Degradado normal2, Degradado contra2) {
        public Funciones {
            Objects.requireNonNull(normal1, "normal1");
            Objects.requireNonNull(contra1, "contra1");
            Objects.requireNonNull(espina, "espina");
            Objects.requireNonNull(normal2, "normal2");
            Objects.requireNonNull(contra2, "contra2");
            int c1 = espina.en(0), c2 = espina.en(1);
            coincide("centro 1", "normal1(0)", normal1.en(0), "espina(0)", c1);
            coincide("centro 1", "contra1(0)", contra1.en(0), "espina(0)", c1);
            coincide("centro 2", "normal2(0)", normal2.en(0), "espina(1)", c2);
            coincide("centro 2", "contra2(0)", contra2.en(0), "espina(1)", c2);
        }

        /** Nivel 1: dos colores, uno por centro; todo es un degradado entre ellos. */
        public static Funciones de(int centro1, int centro2, Mezcla m) {
            return de(centro1, centro1, centro1, centro2, centro2, centro2, m);
        }

        /** Nivel 2: seis colores, los centros y los cuatro puntos de contorno; cada función es lineal con la mezcla dada. */
        public static Funciones de(int centro1, int normal1, int contra1,
                                   int centro2, int normal2, int contra2, Mezcla m) {
            Objects.requireNonNull(m, "mezcla");
            return new Funciones(Degradado.entre(centro1, normal1, m), Degradado.entre(centro1, contra1, m),
                                 Degradado.entre(centro1, centro2, m),
                                 Degradado.entre(centro2, normal2, m), Degradado.entre(centro2, contra2, m));
        }

        private static void coincide(String donde, String a, int ca, String b, int cb) {
            for (int sh = 0; sh <= 24; sh += 8)
                if (Math.abs(((ca >>> sh) & 255) - ((cb >>> sh) & 255)) > 1)
                    throw new IllegalArgumentException(String.format(
                        "%s: %s = #%08X no coincide con %s = #%08X", donde, a, ca, b, cb));
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
         *
         * @deprecated Se sustituirá por un {@link Degradado}.
         */
        @Deprecated
        public Lateral conAjuste(double s) { return new Lateral(contra, centro, normal, s, ajusteArco); }

        /**
         * Misma terna con otro ajuste de la mezcla polar normal -> contra en las tapas (a lo largo del arco, de 0 a pi):
         * 0.5 es lineal y la punta es la media; por debajo de 0.5 el punto medio de la mezcla se adelanta hacia el lado
         * normal (la punta queda más cerca del contra), por encima se retrasa. El cuerpo no usa este ajuste.
         *
         * @deprecated Se sustituirá por un {@link Degradado}.
         */
        @Deprecated
        public Lateral conAjusteArco(double s) { return new Lateral(contra, centro, normal, ajuste, s); }

        /** Degradado del centro hacia un lado (x = 0 en el centro, 1 en el borde), con el ajuste de la transición. */
        private Degradado haciaLado(int lado, Mezcla m) { return Degradado.entre(centro, lado, m).ajustado(ajuste); }

        int en(double u, Mezcla m) {
            return u <= 0.5 ? haciaLado(contra, m).en(1 - 2 * u) : haciaLado(normal, m).en(2 * u - 1);
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
            int arco = Degradado.entre(normal, contra, m).ajustado(ajusteArco).en(th / Math.PI);
            return haciaLado(arco, m).en(s);
        }
    }

    /**
     * Cadena de dos nodos con color lateral. El cuerpo mezcla primero a lo ancho (en u) en cada nodo y después a lo
     * largo (en v), como un cuadrilátero; las tapas son semidiscos con el centro como origen y la punta es la media de
     * normal y contra (no hay color de extremo aparte). Ver {@link Coordenadas#discoTapa}.
     */
    public static Relleno cadenaLateral(Lateral nodo1, Lateral nodo2, Mezcla m) {
        return cadenaLateral(nodo1, nodo2, 0.5, m);
    }

    /** Igual, con el ajuste de la transición entre los dos nodos (0.5 = lineal; ver {@link #transicion}). */
    /** @deprecated Se sustituirá por un {@link Degradado}; el ajuste es solo una deformación de x. */
    @Deprecated
    public static Relleno cadenaLateral(Lateral nodo1, Lateral nodo2, double ajusteCC, Mezcla m) {
        return new CadenaLateral(nodo1, nodo2, ajusteCC, m, 0.25, 0.75);
    }

    private static final class CadenaLateral implements Relleno {
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

        @Override public Relleno para(Gota g, Opciones op) {
            Cortes c = op.tira().cortes(g);
            return new CadenaLateral(n1, n2, sCC, m, c.c1(), c.c3());
        }

        @Override public int argb(Punto p) {
            Coordenadas.Uv uv = p.uv();
            double[] d = Coordenadas.discoTapa(uv, a1, a3);
            if (d != null) return (d[2] == 0 ? n1 : n2).enDisco(d[0], d[1], m);
            double u = clamp01(uv.u());
            return Degradado.entre(n1.en(u, m), n2.en(u, m), m).ajustado(sCC).en(tramoLocal(uv.v(), a1, a3));
        }
    }

    // ---------- Cadena con las cinco funciones ----------

    /** Con las cinco funciones y la mezcla RGB. Ver {@link #cadenaLateral(Funciones, Mezcla)}. */
    public static Relleno cadenaLateral(Funciones f) { return cadenaLateral(f, Mezcla.RGB); }

    /**
     * Relleno de dos nodos a partir de las cinco funciones. Cada mitad del cuerpo (contra y normal) es un cuadrilátero
     * (x de la espina al contorno, v de un nodo al otro) con tres lados dados (los dos radios y la espina) y el borde
     * exterior deducido como la mezcla lineal de sus dos esquinas; se rellena con un parche de Coons. Como el borde
     * deducido es lineal, el parche se reduce a la mezcla de los dos radios a lo largo de v más una corrección en la
     * espina, que es nula cuando la espina es la mezcla lineal de los centros. Las tapas mezclan normal(s) y contra(s)
     * por el ángulo. La mezcla {@code m} rige la combinación entre radios y entre normal y contra; la corrección de la
     * espina se suma en RGB con alfa premultiplicado.
     */
    public static Relleno cadenaLateral(Funciones f, Mezcla m) {
        Objects.requireNonNull(f, "funciones");
        Objects.requireNonNull(m, "mezcla");
        return new CadenaFunciones(f, m, 0.25, 0.75);
    }

    private static final class CadenaFunciones implements Relleno {
        private final Funciones f;
        private final Mezcla m;
        private final double a1, a3;
        private final int centro1, centro2;
        private final boolean espinaLineal;

        CadenaFunciones(Funciones f, Mezcla m, double a1, double a3) {
            if (!(0 < a1 && a1 < a3 && a3 < 1))
                throw new IllegalArgumentException("los cortes deben cumplir 0 < c1 < c3 < 1");
            this.f = f; this.m = m; this.a1 = a1; this.a3 = a3;
            centro1 = f.espina().en(0);
            centro2 = f.espina().en(1);
            boolean lineal = true;
            for (int i = 1; i < 16 && lineal; i++)
                lineal = f.espina().en(i / 16.0) == m.mezclar(centro1, centro2, i / 16.0);
            espinaLineal = lineal;
        }

        @Override public Relleno para(Gota g, Opciones op) {
            Cortes c = op.tira().cortes(g);
            return new CadenaFunciones(f, m, c.c1(), c.c3());
        }

        @Override public int argb(Punto p) {
            Coordenadas.Uv uv = p.uv();
            double[] d = Coordenadas.discoTapa(uv, a1, a3);
            if (d != null) {
                boolean primera = d[2] == 0;
                double s = Math.min(1, Math.hypot(d[0], d[1]));
                double th = Math.atan2(d[1], d[0]);
                Degradado normal = primera ? f.normal1() : f.normal2(), contra = primera ? f.contra1() : f.contra2();
                return m.mezclar(normal.en(s), contra.en(s), th / Math.PI);
            }
            double u = clamp01(uv.u());
            double v = tramoLocal(uv.v(), a1, a3);
            boolean haciaContra = u <= 0.5;
            double x = haciaContra ? 1 - 2 * u : 2 * u - 1;
            int base = m.mezclar((haciaContra ? f.contra1() : f.normal1()).en(x),
                                 (haciaContra ? f.contra2() : f.normal2()).en(x), v);
            if (espinaLineal) return base;
            return conCorreccion(base, f.espina().en(v), m.mezclar(centro1, centro2, v), 1 - x);
        }
    }

    /** base + peso (e - lineal), en RGB con alfa premultiplicado, recortado. */
    private static int conCorreccion(int base, int e, int lineal, double peso) {
        double[] b = premult(base), pe = premult(e), pl = premult(lineal);
        for (int i = 0; i < 4; i++) b[i] = Math.max(0, Math.min(1, b[i] + peso * (pe[i] - pl[i])));
        double a = b[0];
        if (a <= 0) return 0;
        int r = (int) Math.round(Math.min(1, b[1] / a) * 255);
        int g = (int) Math.round(Math.min(1, b[2] / a) * 255);
        int bl = (int) Math.round(Math.min(1, b[3] / a) * 255);
        return (int) Math.round(a * 255) << 24 | r << 16 | g << 8 | bl;
    }

    private static double[] premult(int c) {
        double a = (c >>> 24) / 255.0;
        return new double[] { a, ((c >> 16) & 255) / 255.0 * a, ((c >> 8) & 255) / 255.0 * a, (c & 255) / 255.0 * a };
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

    /** Coordenada local del tramo [fa, fb], recortada a [0, 1]. */
    private static double tramoLocal(double f, double fa, double fb) {
        return clamp01((f - fa) / (fb - fa));
    }

    /** Nivel 2: color de centro y de borde para cada círculo. */
    public static Relleno cuatroColores(int a, int b, int aBorde, int bBorde) {
        return cuatroColores(a, b, aBorde, bBorde, Mezcla.RGB, Mezcla.RGB);
    }

    public static Relleno cuatroColores(int a, int b, int aBorde, int bBorde, Mezcla m) {
        return cuatroColores(a, b, aBorde, bBorde, m, m);
    }

    public static Relleno cuatroColores(int a, int b, int aBorde, int bBorde,
                                             Mezcla entreCirculos, Mezcla centroBorde) {
        return conGamma(a, b, aBorde, bBorde, 1, 1, entreCirculos, centroBorde);
    }

    /**
     * Nivel 3: nivel 2 más una corrección gamma centro→borde en cada círculo.
     *
     * @deprecated Primera forma de mover el punto medio; lo cubre el ajuste de {@link Lateral} (y pronto un {@link Degradado}).
     */
    @Deprecated
    public static Relleno conGamma(int a, int b, int aBorde, int bBorde, double gamma1, double gamma2) {
        return conGamma(a, b, aBorde, bBorde, gamma1, gamma2, Mezcla.RGB, Mezcla.RGB);
    }

    @Deprecated
    public static Relleno conGamma(int a, int b, int aBorde, int bBorde,
                                        double gamma1, double gamma2, Mezcla m) {
        return conGamma(a, b, aBorde, bBorde, gamma1, gamma2, m, m);
    }

    @Deprecated
    public static Relleno conGamma(int a, int b, int aBorde, int bBorde, double gamma1, double gamma2,
                                        Mezcla entreCirculos, Mezcla centroBorde) {
        Objects.requireNonNull(entreCirculos, "entreCirculos");
        Objects.requireNonNull(centroBorde, "centroBorde");
        if (!(gamma1 > 0) || !(gamma2 > 0))
            throw new IllegalArgumentException("gamma debe ser > 0");
        return p -> {
            double tt = clamp01(p.muestra().t());
            double u = clamp01(p.muestra().rho());
            double g = gamma1 + (gamma2 - gamma1) * tt;
            if (g != 1) u = Math.pow(u, g);
            return centroBorde.mezclar(entreCirculos.mezclar(a, b, tt),
                                       entreCirculos.mezclar(aBorde, bBorde, tt), u);
        };
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
