import java.awt.image.BufferedImage;
import java.util.Objects;

/** Texturas para los rellenos de Gota: muestreo en coordenadas normalizadas (u, v) con (0,0) arriba a la izquierda. */
public final class Texturas {

    private Texturas() {}

    /** Devuelve un color ARGB para unas coordenadas de textura. u y v en [0,1] cubren la textura entera. */
    @FunctionalInterface
    public interface Textura {
        int muestrea(double u, double v);

        /**
         * Proporción ancho/alto de la imagen que describe la textura (1 por defecto: cuadrada). Las coordenadas
         * (u, v) siguen yendo de 0 a 1 en los dos ejes; el aspecto solo lo usa el mapeo en disco, para que un
         * círculo de la textura siga siendo un círculo y no una elipse.
         */
        default double aspecto() { return 1; }
    }

    /** Qué hacer con coordenadas fuera de [0,1]. */
    public enum Borde { SUJETAR, REPETIR, ESPEJO }

    public static Textura deImagen(BufferedImage img) { return deImagen(img, Borde.SUJETAR); }

    /** Textura bilineal (alfa premultiplicado) a partir de una imagen. La imagen se copia. */
    public static Textura deImagen(BufferedImage img, Borde borde) {
        final int w = img.getWidth(), h = img.getHeight();
        final int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        return (u, v) -> {
            double x = u * w - 0.5, y = v * h - 0.5;       // los centros de texel están en medio
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = x - x0, fy = y - y0;
            int a = Mezcla.RGB.mezclar(texel(px, w, h, x0, y0, borde), texel(px, w, h, x0 + 1, y0, borde), fx);
            int b = Mezcla.RGB.mezclar(texel(px, w, h, x0, y0 + 1, borde), texel(px, w, h, x0 + 1, y0 + 1, borde), fx);
            return Mezcla.RGB.mezclar(a, b, fy);
        };
    }

    // ---------- Textura de referencia ----------

    /**
     * Textura de referencia de proporciones 1 x 2 (ancho x alto), dibujada como una cápsula repartida en cuatro
     * cuartos a lo largo de v, que coinciden con los cortes por defecto de la tira (1/4, 1/2, 3/4):
     * <ol>
     *   <li>medio círculo con circunferencias concéntricas (la tapa de arriba),</li>
     *   <li>rectángulo de líneas paralelas sobre un color de fondo,</li>
     *   <li>rectángulo de líneas paralelas sobre otro color de fondo,</li>
     *   <li>medio círculo opuesto con circunferencias concéntricas (la tapa de abajo).</li>
     * </ol>
     * Las líneas son los contornos concéntricos de la cápsula (distancia constante a su eje), de modo que las
     * circunferencias de los semicírculos continúan, sin cortes, en las rectas de los rectángulos. Fuera de la
     * cápsula (las esquinas de los cuartos 1 y 4) el color es transparente.
     *
     * <p>Una gota recta de radios iguales r con sus centros a 2r de distancia es exactamente esta cápsula: el
     * mapeo en disco ({@link #discoCapsula}) la reproduce idéntica, y la tira lo hace en el cuerpo.
     */
    public static Textura referencia() {
        return referencia(5, new int[] { 0xFFFFE0B2, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFF8BBD0 }, 0xFF37474F, 0.024);
    }

    /**
     * @param anillos número de líneas concéntricas (>= 1), repartidas a distancias (k + 1/2)/anillos de la mitad del ancho
     * @param fondos  color de fondo de cada cuarto (4 valores)
     * @param linea   color de las líneas
     * @param grosor  grosor de las líneas, en unidades del ancho de la textura
     */
    public static Textura referencia(int anillos, int[] fondos, int linea, double grosor) {
        if (anillos < 1) throw new IllegalArgumentException("anillos >= 1");
        if (fondos == null || fondos.length != 4) throw new IllegalArgumentException("hacen falta 4 fondos");
        if (!(grosor > 0 && grosor < 0.5 / anillos)) throw new IllegalArgumentException("grosor fuera de rango");
        return new Referencia(anillos, fondos.clone(), linea, grosor);
    }

    /**
     * Igual que {@link #referencia()}, con un fondo distinto a cada lado del eje: los de la mitad u < 1/2 son los de
     * {@code izquierda} y los de u >= 1/2 los de {@code derecha}. Así se ve en la gota qué lado es el normal (u = 1) y
     * cuál el contra (u = 0), y cómo atraviesa el eje cada mapeo.
     */
    public static Textura referenciaLados() {
        return referenciaLados(5, new int[] { 0xFFFFE0B2, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFF8BBD0 },
                new int[] { 0xFFFF9E80, 0xFF80D8FF, 0xFFA5D6A7, 0xFFCE93D8 }, 0xFF37474F, 0.024);
    }

    public static Textura referenciaLados(int anillos, int[] izquierda, int[] derecha, int linea, double grosor) {
        if (anillos < 1) throw new IllegalArgumentException("anillos >= 1");
        if (izquierda == null || izquierda.length != 4 || derecha == null || derecha.length != 4)
            throw new IllegalArgumentException("hacen falta 4 fondos por lado");
        if (!(grosor > 0 && grosor < 0.5 / anillos)) throw new IllegalArgumentException("grosor fuera de rango");
        return new Referencia(anillos, izquierda.clone(), derecha.clone(), linea, grosor);
    }

    private static final class Referencia implements Textura {
        private static final double SUAVE = 0.004;      // anchura del borde antialiasado, en unidades del ancho
        private final int anillos, linea;
        private final int[] fondos, fondosDer;           // fondos de la mitad u < 1/2 y de u >= 1/2
        private final double medio;

        Referencia(int anillos, int[] fondos, int linea, double grosor) { this(anillos, fondos, fondos, linea, grosor); }

        Referencia(int anillos, int[] fondos, int[] fondosDer, int linea, double grosor) {
            this.anillos = anillos; this.fondos = fondos; this.fondosDer = fondosDer; this.linea = linea; this.medio = grosor / 2;
        }

        @Override public double aspecto() { return 0.5; }

        @Override public int muestrea(double u, double v) {
            double x = u, y = 2 * v;                          // unidades del ancho: x en [0, 1], y en [0, 2]
            double d;
            int cuarto;
            if (y < 0.5) { d = Math.hypot(x - 0.5, y - 0.5); cuarto = 0; }
            else if (y < 1) { d = Math.abs(x - 0.5); cuarto = 1; }
            else if (y < 1.5) { d = Math.abs(x - 0.5); cuarto = 2; }
            else { d = Math.hypot(x - 0.5, y - 1.5); cuarto = 3; }
            double dentro = clamp(0.5 + (0.5 - d) / SUAVE);
            if (dentro <= 0) return 0;
            double l = 0;
            for (int k = 0; k < anillos; k++) {
                double rho = 0.5 * (k + 0.5) / anillos;
                l = Math.max(l, clamp(0.5 + (medio - Math.abs(d - rho)) / SUAVE));
            }
            int bg = (u < 0.5 ? fondos : fondosDer)[cuarto], rgb = 0;
            for (int s = 16; s >= 0; s -= 8)
                rgb |= (int) Math.round(((bg >> s) & 255) * (1 - l) + ((linea >> s) & 255) * l) << s;
            int a = (int) Math.round(255 * dentro);
            return (a << 24) | rgb;
        }
    }

    /**
     * Mapeo de textura en tira: v corre a lo largo de la gota y u a lo ancho. Es la misma coordenada
     * Uv que usa la cadena de color, así que ambas alinean. La textura se divide en cuatro bandas de alto
     * (por defecto cuartos): tapa del primer círculo, cuerpo hasta tm, cuerpo desde tm y tapa del segundo.
     * Las tapas son semidiscos ({@link Coordenadas#discoTapa}): con la textura de referencia 1 x 2 una gota recta de radios
     * iguales reproduce la textura idéntica.
     */
    public static Gota.Relleno tira(Textura tex) {
        Objects.requireNonNull(tex, "textura");
        return new Tira(tex, 0, 0, 1, 1, null, null);   // sin remapeo: usa los cortes de la gota tal cual
    }

    /**
     * Igual que tira(tex), sobre el subrectángulo (u0, v0)-(u1, v1) de la textura (por ejemplo, un atlas)
     * y con los cortes de las tapas como fracciones de su alto, 0 <= c1 <= c3 <= 1. El corte central sigue
     * a tm: el cuerpo de la gota se reparte entre c1 y c3 en la misma proporción que en la propia gota.
     */
    public static Gota.Relleno tira(Textura tex, double u0, double v0, double u1, double v1,
                                    double corte1, double corte3) {
        Objects.requireNonNull(tex, "textura");
        if (!(0 <= corte1 && corte1 <= corte3 && corte3 <= 1))
            throw new IllegalArgumentException("los cortes deben cumplir 0 <= c1 <= c3 <= 1");
        return new Tira(tex, u0, v0, u1, v1, null, new double[] { corte1, corte3 });
    }

    private static final class Tira implements Gota.Relleno {
        private final Textura tex;
        private final double u0, v0, u1, v1;
        private final double[] desde, hasta;   // cortes de la gota -> cortes de la textura (null sin remapeo)

        Tira(Textura tex, double u0, double v0, double u1, double v1, double[] desde, double[] hasta) {
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

        @Override public int argb(Coordenadas.Muestra m, Coordenadas.Uv uv) {
            if (desde == null)   // sin enlazar con una gota: v tal cual
                return tex.muestrea(u0 + (u1 - u0) * clamp(uv.u()), v0 + (v1 - v0) * clamp(uv.v()));
            double v = uv.v(), f = 1;
            double[] d = Coordenadas.discoTapa(uv, desde[1], desde[3]);   // la tapa es el semidisco: v = c1·(1 - a) o c3 + (1 - c3)·a
            if (d != null) v = d[2] == 0 ? desde[1] * (1 - d[1]) : desde[3] + (1 - desde[3]) * d[1];
            for (int i = 0; i < 4; i++) {
                if (v <= desde[i + 1] || i == 3) {
                    double w = desde[i + 1] - desde[i];
                    f = w <= 0 ? hasta[i + 1] : hasta[i] + (hasta[i + 1] - hasta[i]) * clamp((v - desde[i]) / w);
                    break;
                }
            }
            return tex.muestrea(u0 + (u1 - u0) * clamp(uv.u()), v0 + (v1 - v0) * f);
        }
    }

    /** Igual que {@link #tira(Textura)}: hay un solo mapeo de textura en la tira (tapas de semidisco). */
    public static Gota.Relleno textura(Textura tex) { return tira(tex); }

    // ---------- Mapeo en disco ----------
    //
    // Cada círculo (o elipse) de la familia se lleva a un disco de la textura mediante una semejanza:
    //     (u, v) = c(t) + s(t) · R(θ(t)) · (x', y')
    // con (x', y') las coordenadas del píxel dentro de su círculo, normalizadas (el círculo es el disco
    // unidad; en las elipses, cada eje por su propio radio). Los extremos fijan los discos: c1 y c2 son los centros
    // de la textura a los que va el centro de cada círculo, y el punto del borde e1 / e2 fija a la vez la escala
    // s = |e - c| y el ángulo θ = atan2(e - c). Entre ambos se interpolan linealmente c y s, y θ con el sentido
    // de Mezcla.Tono (CORTO por defecto). Como en glTexEnv y la tira, depende solo de (t, axial, lateral).
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
    public static Gota.Relleno disco(Textura tex) {
        return disco(tex, Disco.inscrito(), Disco.inscrito(), Orientacion.TANGENTE, Mezcla.Tono.CORTO);
    }

    public static Gota.Relleno disco(Textura tex, Disco primero, Disco segundo) {
        return disco(tex, primero, segundo, Orientacion.TANGENTE, Mezcla.Tono.CORTO);
    }

    /**
     * Mapeo en disco pensado para la textura de referencia 1 x 2: el primer círculo es la tapa de arriba y el último
     * la de abajo, y los círculos intermedios se deslizan entre ellos. En una gota recta de radios iguales con los
     * centros a dos radios de distancia reproduce la textura idéntica.
     */
    public static Gota.Relleno discoCapsula(Textura tex) {
        return disco(tex, Disco.capsulaInicio(), Disco.capsulaFin(), Orientacion.TANGENTE, Mezcla.Tono.CORTO);
    }

    public static Gota.Relleno disco(Textura tex, Disco primero, Disco segundo, Orientacion orientacion) {
        return disco(tex, primero, segundo, orientacion, Mezcla.Tono.CORTO);
    }

    public static Gota.Relleno disco(Textura tex, Disco primero, Disco segundo,
                                     Orientacion orientacion, Mezcla.Tono giro) {
        Objects.requireNonNull(tex, "textura");
        Objects.requireNonNull(primero, "primero");
        Objects.requireNonNull(segundo, "segundo");
        Objects.requireNonNull(orientacion, "orientacion");
        Objects.requireNonNull(giro, "giro");
        return new DiscoRelleno(tex, primero, segundo, orientacion, giro, null);
    }

    private static final class DiscoRelleno implements Gota.Relleno {
        private final Textura tex;
        private final Disco d1, d2;
        private final Orientacion orientacion;
        private final Mezcla.Tono giro;
        private final Gota gota;
        private final double s1, s2, th1, th2;   // escala (en unidades del ancho) y ángulo (grados) de cada extremo
        private final double al;                  // aspecto ancho/alto de la textura

        DiscoRelleno(Textura tex, Disco d1, Disco d2, Orientacion o, Mezcla.Tono giro, Gota gota) {
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

        @Override public int argb(Coordenadas.Muestra m) {
            double t = clamp(m.t());
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
    static double interpolaAngulo(double a0, double a1, double u, Mezcla.Tono tono) {
        double corto = ((a1 - a0 + 180) % 360 + 360) % 360 - 180;   // [-180, 180)
        double d = switch (tono) {
            case CORTO -> corto;
            case LARGO -> corto > 0 ? corto - 360 : corto + 360;
            case HORARIO -> ((a1 - a0) % 360 + 360) % 360;
            case ANTIHORARIO -> -(((a0 - a1) % 360 + 360) % 360);
        };
        return (((a0 + d * u) % 360) + 360) % 360;
    }

    private static double clamp(double x) { return x < 0 ? 0 : x > 1 ? 1 : x; }

    private static int texel(int[] px, int w, int h, int i, int j, Borde borde) {
        return px[indice(j, h, borde) * w + indice(i, w, borde)];
    }

    private static int indice(int i, int n, Borde borde) {
        return switch (borde) {
            case SUJETAR -> Math.max(0, Math.min(n - 1, i));
            case REPETIR -> Math.floorMod(i, n);
            case ESPEJO -> {
                int k = Math.floorMod(i, 2 * n);
                yield k < n ? k : 2 * n - 1 - k;
            }
        };
    }
}
