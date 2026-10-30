import java.util.Objects;

/**
 * Interpolación entre dos colores ARGB: u = 0 devuelve c0, u = 1 devuelve c1. Las implementaciones listas para usar
 * son RGB (por defecto), RGB_LINEAL, HSL y LCH (Oklch), estas dos con sentido de tono configurable ({@link Tono}), o
 * cualquier lambda propia. Si solo se da una Mezcla, se usa para todas las interpolaciones. Con tonos HORARIO /
 * ANTIHORARIO / LARGO conviene dar una segunda Mezcla de sentido CORTO para centroBorde: un sentido forzado en esa
 * mezcla puede dar vueltas completas por la rueda de color.
 */
@FunctionalInterface
public interface Mezcla {
    int mezclar(int c0, int c1, double u);

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
    public static final Mezcla RGB = Mezcla::mezclaRgb;
    /** Interpola en luz lineal (sin la curva sRGB), alfa premultiplicado. */
    public static final Mezcla RGB_LINEAL = Mezcla::mezclaRgbLineal;
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
        double p0 = TablaLineal.LIN[s0 & 0xFF] * a0, p1 = TablaLineal.LIN[s1 & 0xFF] * a1;
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
        double r = TablaLineal.LIN[(c >> 16) & 0xFF], g = TablaLineal.LIN[(c >> 8) & 0xFF], b = TablaLineal.LIN[c & 0xFF];
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

/** Tabla sRGB -> luz lineal de Mezcla (no es parte de la API: una interfaz no puede tener estáticos privados). */
final class TablaLineal {
    private TablaLineal() {}

    static final double[] LIN = new double[256];
    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            TablaLineal.LIN[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }
}
