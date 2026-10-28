import java.awt.image.BufferedImage;

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
            int a = Rellenos.RGB.mezclar(texel(px, w, h, x0, y0, borde), texel(px, w, h, x0 + 1, y0, borde), fx);
            int b = Rellenos.RGB.mezclar(texel(px, w, h, x0, y0 + 1, borde), texel(px, w, h, x0 + 1, y0 + 1, borde), fx);
            return Rellenos.RGB.mezclar(a, b, fy);
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
     * mapeo en disco ({@link Rellenos#discoCapsula}) la reproduce idéntica, y la tira lo hace en el cuerpo.
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
